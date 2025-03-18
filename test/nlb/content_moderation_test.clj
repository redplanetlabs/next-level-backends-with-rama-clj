(ns nlb.content-moderation-test
  (:use [com.rpl rama]
        [com.rpl.rama path])
  (:require
   [clojure.test :refer [deftest is testing]]
   [com.rpl.rama.test :as rtest]
   [nlb.content-moderation :as cm]))

(deftest module-test
  (with-open [ipc (rtest/create-ipc)]
    (rtest/launch-module! ipc cm/ContentModerationModule {:tasks 4 :threads 2})
    (let [module-name (get-module-name cm/ContentModerationModule)
          post-depot (foreign-depot ipc module-name "*post-depot")
          mute-depot (foreign-depot ipc module-name "*mute-depot")
          get-posts (foreign-query ipc module-name "get-posts")
          user1 100
          user2 101
          user3 102
          user4 103
          user5 104
          post1 (cm/->Post user2 user1 "Post 1")
          post2 (cm/->Post user1 user1 "Post 2")
          post3 (cm/->Post user2 user1 "Post 3")
          post4 (cm/->Post user5 user1 "Post 4")
          post5 (cm/->Post user4 user1 "Post 5")
          post6 (cm/->Post user1 user1 "Post 6")
          post7 (cm/->Post user2 user1 "Post 7")
          post8 (cm/->Post user3 user1 "Post 8")]
      (foreign-append! post-depot post1)
      (foreign-append! post-depot post2)
      (foreign-append! post-depot post3)
      (foreign-append! post-depot post4)
      (foreign-append! post-depot post5)
      (foreign-append! post-depot post6)
      (foreign-append! post-depot post7)
      (foreign-append! post-depot post8)

      (is (= {:posts [post1 post2 post3] :next-offset 3}
             (foreign-invoke-query get-posts user1 0 3)))
      (is (= {:posts [post4 post5 post6] :next-offset 6}
             (foreign-invoke-query get-posts user1 3 3)))
      (is (= {:posts [post7 post8] :next-offset nil}
             (foreign-invoke-query get-posts user1 6 3)))
      (is (= {:posts [] :next-offset nil}
             (foreign-invoke-query get-posts user1 8 3)))

      (foreign-append! mute-depot (cm/->Mute user1 user2))
      (foreign-append! mute-depot (cm/->Mute user1 user4))

      (is (= {:posts [post2 post4 post6] :next-offset 6}
             (foreign-invoke-query get-posts user1 0 3)))
      (is (= {:posts [post8] :next-offset nil}
             (foreign-invoke-query get-posts user1 6 3)))

      (foreign-append! mute-depot (cm/->Unmute user1 user2))

      (is (= {:posts [post1 post2 post3] :next-offset 3}
             (foreign-invoke-query get-posts user1 0 3)))
      (is (= {:posts [post4 post6 post7] :next-offset 7}
             (foreign-invoke-query get-posts user1 3 3)))
      (is (= {:posts [post8] :next-offset nil}
             (foreign-invoke-query get-posts user1 7 3)))
      )))
