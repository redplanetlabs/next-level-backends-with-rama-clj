(ns nlb.who-to-follow
  (:use [com.rpl.rama]
        [com.rpl.rama.path])
  (:require [com.rpl.rama.aggs :as aggs]
            [com.rpl.rama.ops :as ops]))

(defn test-mode? [] false)
(defn num-who-to-follow-recs [] 300)

(defrecord Follow [from to])

(defn declare-who-to-follow-topology [topologies]
  (let [mb (microbatch-topology topologies "who-to-follow")]
    (declare-pstate
      mb
      $$who-to-follow
      {Long [Long]})
    (declare-pstate mb $$next-id Long)
    (<<sources mb
      (source> *who-to-follow-tick :> %microbatch)
      (%microbatch)
      (<<batch
        (|all)
        (local-select> STAY $$next-id :> *start-id)
        (local-select>
          (sorted-map-range-from *start-id
                                 {:max-amt 15
                                  :inclusive? false})
          $$follows :> *m)
        (<<if (< (count *m) 15)
          (local-transform> (termval -1) $$next-id)
         (else>)
          (key (-> *m rseq first) :> *max-id)
          (local-transform> (termval *max-id) $$next-id))
        (ops/explode-map *m :> *account-id *follows)
        (ops/explode *follows :> *following-id)
        (|hash *following-id)
        (local-select> [(keypath *following-id) ALL]
                       $$follows :> *candidate-id)
        (filter> (not= *candidate-id *account-id))
        (|hash *account-id)
        (+compound {*account-id {*candidate-id (aggs/+count)}} :> *m)
        (ops/explode-map *m :> *account-id *candidate-counts)
        (mapv first
              (->> *candidate-counts
                   vec
                   (sort-by (comp - val))
                   (take 1000))
              :> *candidate-order)
        (loop<- [*chosen []
                 *candidate-order (seq *candidate-order)
                 :> *who-to-follow]
          (<<if (or> (>= (count *chosen) (num-who-to-follow-recs))
                     (empty? *candidate-order))
            (:> *chosen)
           (else>)
            (yield-if-overtime)
            (first *candidate-order :> *candidate-id)
            (local-select>
              [(keypath *account-id) (view contains? *candidate-id)]
              $$follows :> *already-follows?)
            (<<if (not *already-follows?)
              (continue> (conj *chosen *candidate-id)
                         (next *candidate-order))
             (else>)
              (continue> *chosen (next *candidate-order)))
            ))
        (local-transform>
          [(keypath *account-id) (termval *who-to-follow)]
          $$who-to-follow)
        ))))

(defmodule WhoToFollowModule
  [setup topologies]
  (declare-depot setup *follows-depot (hash-by :from))
  (if (test-mode?)
    (declare-depot setup *who-to-follow-tick :random {:global? true})
    (declare-tick-depot setup *who-to-follow-tick 30000))
  (let [topology (stream-topology topologies "core")]
    (declare-pstate
      topology
      $$follows
      {Long (set-schema Long {:subindex? true})})
    (<<sources topology
      (source> *follows-depot :> {:keys [*from *to]})
      (local-transform> [(keypath *from) NONE-ELEM (termval *to)] $$follows)))
  (declare-who-to-follow-topology topologies))
