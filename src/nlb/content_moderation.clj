(ns nlb.content-moderation
  (:use [com.rpl.rama]
        [com.rpl.rama.path])
  (:require [com.rpl.rama.aggs :as aggs]
            [com.rpl.rama.ops :as ops])
  (:import [java.util UUID]))


(defrecord Post [from-user-id to-user-id content])
(defrecord Ban [user-id banned-user-id])
(defrecord Unban [user-id unbanned-user-id])

(defmodule ContentModerationModule
  [setup topologies]
  (declare-depot setup *post-depot (hash-by :to-user-id))
  (declare-depot setup *ban-depot (hash-by :user-id))
  (let [topology (stream-topology topologies "core")]
    (declare-pstate
      topology
      $$posts
      {Long (vector-schema Post {:subindex? true})})
    (declare-pstate
      topology
      $$bans
      {Long (set-schema Long {:subindex? true})})
   (<<sources topology
     (source> *post-depot :> {:keys [*to-user-id] :as *post})
     (local-transform> [(keypath *to-user-id) AFTER-ELEM (termval *post)]
       $$posts)

     (source> *ban-depot :> *item)
     (<<subsource *item
       (case> Ban :> {:keys [*user-id *banned-user-id]})
       (local-transform> [(keypath *user-id) NONE-ELEM (termval *banned-user-id)]
        $$bans)

       (case> Unban :> {:keys [*user-id *unbanned-user-id]})
       (local-transform> [(keypath *user-id) (set-elem *unbanned-user-id) NONE>]
        $$bans)
       )))
  (<<query-topology topologies "get-posts-helper"
    [*user-id *from-offset *limit :> *ret]
    (|hash *user-id)
    (local-select> [(keypath *user-id) (view count)] $$posts :> *num-posts)
    (min *num-posts (+ *from-offset *limit) :> *end-offset)
    (<<if (= *end-offset *num-posts)
      (identity nil :> *next-offset)
     (else>)
      (identity *end-offset :> *next-offset))
    (local-select> [(keypath *user-id) (srange *from-offset *end-offset) ALL]
      $$posts :> {:keys [*from-user-id] :as *post})
    (local-select> [(keypath *user-id) (view contains? *from-user-id)]
      $$bans :> *banned?)
    (filter> (not *banned?))
    (|origin)
    (aggs/+vec-agg *post :> *posts)
    (aggs/+last *next-offset :> *next-offset)
    (hash-map :fetched-posts *posts :next-offset *next-offset :> *ret))
  (<<query-topology topologies "get-posts" [*user-id *from-offset *limit :> *ret]
    (|hash *user-id)
    (loop<- [*next-offset *from-offset
             *posts []
             :> *posts *next-offset]
      (invoke-query "get-posts-helper"
        *user-id
        *next-offset
        (- *limit (count *posts))
        :> {:keys [*fetched-posts *next-offset]})
      (reduce conj *posts *fetched-posts :> *new-posts)
      (<<if (or> (nil? *next-offset)
                 (= (count *new-posts) *limit))
        (:> *new-posts *next-offset)
       (else>)
        (continue> *next-offset *new-posts)
        ))
    (|origin)
    (hash-map :posts *posts :next-offset *next-offset :> *ret))
  )
