(ns nlb.content-moderation
  (:use [com.rpl.rama]
        [com.rpl.rama.path])
  (:require [com.rpl.rama.aggs :as aggs]
            [com.rpl.rama.ops :as ops])
  (:import [java.util UUID]))

(defrecord Post [from-user-id to-user-id content])
(defrecord Mute [user-id muted-user-id])
(defrecord Unmute [user-id unmuted-user-id])

(defmodule ContentModerationModule
  [setup topologies]
  (declare-depot setup *post-depot (hash-by :to-user-id))
  (declare-depot setup *mute-depot (hash-by :user-id))
  (let [topology (stream-topology topologies "core")]
    (declare-pstate
      topology
      $$posts
      {Long (vector-schema Post {:subindex? true})})
    (declare-pstate
      topology
      $$mutes
      {Long (set-schema Long {:subindex? true})})
   (<<sources topology
     (source> *post-depot :> {:keys [*to-user-id] :as *post})
     (local-transform> [(keypath *to-user-id) AFTER-ELEM (termval *post)]
       $$posts)

     (source> *mute-depot :> *data)
     (<<subsource *data
       (case> Mute :> {:keys [*user-id *muted-user-id]})
       (local-transform> [(keypath *user-id) NONE-ELEM (termval *muted-user-id)]
         $$mutes)

       (case> Unmute :> {:keys [*user-id *unmuted-user-id]})
       (local-transform> [(keypath *user-id) (set-elem *unmuted-user-id) NONE>]
         $$mutes)
       )))
  (<<query-topology topologies "get-posts-helper"
    [*user-id *start-offset *end-offset :> *posts]
    (|hash *user-id)
    (local-select> [(keypath *user-id) (srange *start-offset *end-offset) ALL]
      $$posts :> {:keys [*from-user-id] :as *post})
    (local-select> [(keypath *user-id) (view contains? *from-user-id)]
      $$mutes :> *muted?)
    (filter> (not *muted?))
    (|origin)
    (aggs/+vec-agg *post :> *posts))
  (<<query-topology topologies "get-posts" [*user-id *from-offset *limit :> *ret]
    (|hash *user-id)
    (loop<- [*query-offset *from-offset
             *posts []
             :> *posts *next-offset]
      (local-select> [(keypath *user-id) (view count)] $$posts :> *num-posts)
      (- *limit (count *posts) :> *fetch-amount)
      (min *num-posts (+ *query-offset *fetch-amount) :> *end-offset)
      (invoke-query "get-posts-helper" *user-id *query-offset *end-offset
        :> *fetched-posts)
      (reduce conj *posts *fetched-posts :> *new-posts)
      (<<cond
        (case> (= *end-offset *num-posts))
        (:> *new-posts nil)

        (case> (= (count *new-posts) *limit))
        (:> *new-posts *end-offset)

        (default>)
        (continue> *end-offset *new-posts)
        ))
    (|origin)
    (hash-map :posts *posts :next-offset *next-offset :> *ret))
  )
