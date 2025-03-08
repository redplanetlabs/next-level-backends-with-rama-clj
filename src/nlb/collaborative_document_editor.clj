(ns nlb.collaborative-document-editor
  (:use [com.rpl.rama]
        [com.rpl.rama.path])
  (:require [com.rpl.rama.aggs :as aggs]
            [com.rpl.rama.ops :as ops]))

(defrecord AddText [content])
(defrecord RemoveText [amount])

(defrecord Edit [id version offset action])

(defn add-action-adjustment [{:keys [action]}]
  (if (instance? AddText action)
    (-> action :content count)
    (-> action :amount -)))

(defn transform-remove-against-add [removes offset action]
  (into []
    (mapcat
      (fn [edit]
        (let [start (:offset edit)
              remove-amount (-> edit :action :amount)
              end (+ start remove-amount)
              add-amt (-> action :content count)]
          (cond (>= offset end) [edit]
                (<= offset start) [(update edit :offset #(+ % add-amt))]
                :else
                (let [remove1 (- offset start)
                      remove2 (- remove-amount remove1)
                      start2 (+ offset add-amt)]
                  [(->Edit (:id edit) (:version edit) start2 (->RemoveText remove2))
                   (->Edit (:id edit) (:version edit) start (->RemoveText remove1))
                   ]))
          )))
     removes))

(defn transform-remove-against-remove [removes offset action]
  (into []
    (mapcat
      (fn [edit]
        (let [start (:offset edit)
              remove-amount (-> edit :action :amount)
              end (+ start remove-amount)
              missed-remove-amount (-> action :amount)]
          (cond (>= offset end)
                [edit]

                (<= offset start)
                (let [overlap (-> (- (+ offset missed-remove-amount) start)
                                  (max 0)
                                  (min remove-amount))]
                  (if (< overlap remove-amount)
                    [(-> edit
                         (update :offset #(-> % (- missed-remove-amount) (+ overlap)))
                         (update-in [:action :amount] #(- % overlap)))]))

                :else
                (let [overlap-end (min end (+ offset missed-remove-amount))
                      overlap (- overlap-end offset)]
                  [(update-in edit [:action :amount] #(- % overlap))])
          )))
     removes)))

(defn transform-edit [edit missed-edits]
  (if (instance? AddText (:action edit))
    (let [new-offset (reduce
                       (fn [offset missed-edit]
                         (if (<= (:offset missed-edit) offset)
                           (+ offset (add-action-adjustment missed-edit))
                           offset))
                       (:offset edit)
                       missed-edits)]
    [(assoc edit :offset new-offset)])
  (reduce
    (fn [removes {:keys [offset action]}]
      (if (instance? AddText action)
        (transform-remove-against-add removes offset action)
        (transform-remove-against-remove removes offset action)))
    [edit]
    missed-edits)))

(defn apply-edits [doc edits]
  (reduce
    (fn [doc {:keys [offset action]}]
      (if (instance? AddText action)
        (setval (srange offset offset) (:content action) doc)
        (setval (srange offset (+ offset (:amount action))) "" doc)))
    doc
    edits))

(defmodule CollaborativeDocumentEditorModule
  [setup topologies]
  (declare-depot setup *edit-depot (hash-by :id))
  (let [topology (stream-topology topologies "core")]
    (declare-pstate
      topology
      $$edits
      {Long (vector-schema Edit {:subindex? true})})
    (declare-pstate
      topology
      $$docs
      {Long String})
    (<<sources topology
      (source> *edit-depot :> {:keys [*id *version] :as *edit})
      (local-select> [(keypath *id) (view count)]
        $$edits :> *latest-version)
      (<<if (= *latest-version *version)
        (vector *edit :> *final-edits)
       (else>)
        (local-select>
          [(keypath *id) (srange *version *latest-version)]
          $$edits :> *missed-edits)
        (transform-edit *edit *missed-edits :> *final-edits))
      (local-select> [(keypath *id) (nil->val "")]
        $$docs :> *latest-doc)
      (apply-edits *latest-doc *final-edits :> *new-doc)
      (local-transform> [(keypath *id) (termval *new-doc)]
        $$docs)
      (local-transform>
        [(keypath *id) END (termval *final-edits)]
        $$edits)
      ))
  (<<query-topology topologies "doc+version"
    [*id :> *ret]
    (|hash *id)
    (local-select> (keypath *id) $$docs :> *doc)
    (local-select> [(keypath *id) (view count)] $$edits :> *version)
    (hash-map :doc *doc :version *version :> *ret)
    (|origin)))
