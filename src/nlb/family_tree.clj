(ns nlb.family-tree
  (:use [com.rpl.rama]
        [com.rpl.rama.path])
  (:require [com.rpl.rama.aggs :as aggs]
            [com.rpl.rama.ops :as ops])
  (:import [java.util UUID]))


(defrecord Person [id parent1 parent2 name])

(defmodule FamilyTreeModule
  [setup topologies]
  (declare-depot setup *people-depot (hash-by :id))
  (let [topology (stream-topology topologies "core")]
    (declare-pstate
      topology
      $$family-tree
      {UUID (fixed-keys-schema
              {:parent1 UUID
               :parent2 UUID
               :name String
               :children #{UUID}})})
    (<<sources topology
      (source> *people-depot :> {:keys [*id *parent1 *parent2] :as *person})
      (local-transform>
        [(keypath *id) (termval (dissoc *person :id))]
        $$family-tree)
      (ops/explode [*parent1 *parent2] :> *parent)
      (|hash *parent)
      (local-transform>
        [(keypath *parent) :children NONE-ELEM (termval *id)]
        $$family-tree)
      ))
  (<<query-topology topologies "ancestors"
    [*start-id *num-generations :> *ancestors]
    (loop<- [*id *start-id
             *generation 0
             :> *ancestor]
      (filter> (<= *generation *num-generations))
      (|hash *id)
      (local-select> (view contains? *id) $$ancestors$$ :> *traversed?)
      (filter> (not *traversed?))
      (local-transform> [NONE-ELEM (termval *id)] $$ancestors$$)
      (local-select> [(keypath *id) (multi-path :parent1 :parent2) some?]
        $$family-tree
        :> *parent)
      (:> *parent)
      (continue> *parent (inc *generation)))
    (|origin)
    (aggs/+set-agg *ancestor :> *ancestors))
  (<<query-topology topologies "descendants-count"
    [*start-id *num-generations :> *result]
    (loop<- [*id *start-id
             *generation 0 :> *gen *count]
      (filter> (< *generation *num-generations))
      (|hash *id)
      (local-select> [(keypath *id) :children] $$family-tree :> *children)
      (:> *generation (count *children))
      (ops/explode *children :> *c)
      (continue> *c (inc *generation)))
    (|origin)
    (+compound {*gen (aggs/+sum *count)} :> *result))
  )
