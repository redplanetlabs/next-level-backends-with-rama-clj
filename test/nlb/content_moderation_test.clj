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
          get-posts (foreign-query ipc module-name "get-posts")]
      ;; TODO
      )))
