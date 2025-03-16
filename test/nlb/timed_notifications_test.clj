(ns nlb.timed-notifications-test
  (:use [com.rpl rama]
        [com.rpl.rama path])
  (:require
   [clojure.test :refer [deftest is testing]]
   [com.rpl.rama.test :as rtest]
   [nlb.timed-notifications :as tn])
  (:import [com.rpl.rama.helpers TopologyUtils]))

(deftest module-test
  (with-redefs [tn/test-mode? (constantly true)]
    (with-open [ipc (rtest/create-ipc)
                sim-time (TopologyUtils/startSimTime)]
      (rtest/launch-module! ipc tn/TimedNotificationsModule {:tasks 4 :threads 2})
      (let [module-name (get-module-name tn/TimedNotificationsModule)
            scheduled-post-depot (foreign-depot ipc module-name "*scheduled-post-depot")
            tick (foreign-depot ipc module-name "*tick")
            feeds (foreign-pstate ipc module-name "$$feeds")]
        (foreign-append! scheduled-post-depot (tn/->ScheduledPost "alice" 1500 "Post 1"))
        (foreign-append! scheduled-post-depot (tn/->ScheduledPost "alice" 800 "Post 2"))
        (foreign-append! scheduled-post-depot (tn/->ScheduledPost "alice" 2000 "Post 3"))

        (foreign-append! tick nil)
        (is (= [] (foreign-select ["alice" ALL] feeds)))

        (TopologyUtils/advanceSimTime 799)
        (foreign-append! tick nil)
        (is (= [] (foreign-select ["alice" ALL] feeds)))

        (TopologyUtils/advanceSimTime 1)
        (foreign-append! tick nil)
        (is (= ["Post 2"] (foreign-select ["alice" ALL] feeds)))

        (TopologyUtils/advanceSimTime 600)
        (foreign-append! tick nil)
        (is (= ["Post 2"] (foreign-select ["alice" ALL] feeds)))

        (TopologyUtils/advanceSimTime 100)
        (foreign-append! tick nil)
        (is (= ["Post 2" "Post 1"] (foreign-select ["alice" ALL] feeds)))

        (TopologyUtils/advanceSimTime 450)
        (foreign-append! tick nil)
        (is (= ["Post 2" "Post 1"] (foreign-select ["alice" ALL] feeds)))

        (TopologyUtils/advanceSimTime 50)
        (foreign-append! tick nil)
        (is (= ["Post 2" "Post 1" "Post 3"] (foreign-select ["alice" ALL] feeds)))
        ))))
