(ns nlb.who-to-follow-test
  (:use [com.rpl rama]
        [com.rpl.rama path])
  (:require
   [clojure.test :refer [deftest is testing]]
   [com.rpl.rama.test :as rtest]
   [nlb.who-to-follow :as wtf]))

(deftest who-to-follow-test
  (with-redefs [wtf/test-mode? (constantly true)
                wtf/num-who-to-follow-recs (constantly 3)]
    (with-open [ipc (rtest/create-ipc)]
      (rtest/launch-module! ipc wtf/WhoToFollowModule {:tasks 4 :threads 2})
      (let [module-name (get-module-name wtf/WhoToFollowModule)
            follows-depot (foreign-depot ipc module-name "*follows-depot")
            who-to-follow-tick (foreign-depot ipc module-name "*who-to-follow-tick")
            who-to-follow (foreign-pstate ipc module-name "$$who-to-follow")]
        (foreign-append! follows-depot (wtf/->Follow 1 2))
        (foreign-append! follows-depot (wtf/->Follow 1 3))
        (foreign-append! follows-depot (wtf/->Follow 1 4))
        (foreign-append! follows-depot (wtf/->Follow 1 5))

        (foreign-append! follows-depot (wtf/->Follow 2 1))
        (foreign-append! follows-depot (wtf/->Follow 2 3))
        (foreign-append! follows-depot (wtf/->Follow 2 6))
        (foreign-append! follows-depot (wtf/->Follow 2 7))
        (foreign-append! follows-depot (wtf/->Follow 2 8))
        (foreign-append! follows-depot (wtf/->Follow 2 9))
        (foreign-append! follows-depot (wtf/->Follow 2 10))

        (foreign-append! follows-depot (wtf/->Follow 3 1))
        (foreign-append! follows-depot (wtf/->Follow 3 8))
        (foreign-append! follows-depot (wtf/->Follow 3 9))
        (foreign-append! follows-depot (wtf/->Follow 3 11))
        (foreign-append! follows-depot (wtf/->Follow 3 12))

        (foreign-append! follows-depot (wtf/->Follow 4 8))
        (foreign-append! follows-depot (wtf/->Follow 4 3))
        (foreign-append! follows-depot (wtf/->Follow 4 10))
        (foreign-append! follows-depot (wtf/->Follow 4 13))
        (foreign-append! follows-depot (wtf/->Follow 4 14))

        (foreign-append! follows-depot (wtf/->Follow 5 1))
        (foreign-append! follows-depot (wtf/->Follow 5 3))
        (foreign-append! follows-depot (wtf/->Follow 5 10))

        (foreign-append! follows-depot (wtf/->Follow 6 12))
        (foreign-append! follows-depot (wtf/->Follow 7 12))
        (foreign-append! follows-depot (wtf/->Follow 8 12))
        (foreign-append! follows-depot (wtf/->Follow 9 12))

        (foreign-append! who-to-follow-tick nil)
        (rtest/wait-for-microbatch-processed-count ipc module-name "who-to-follow" 1)

        (is (= #{10 8 9} (set (foreign-select-one (keypath 1) who-to-follow))))
        ))))
