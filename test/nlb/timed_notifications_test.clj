(ns nlb.timed-notifications-test
  (:use [com.rpl rama]
        [com.rpl.rama path])
  (:require
   [clojure.test :refer [deftest is testing]]
   [com.rpl.rama.test :as rtest]
   [nlb.timed-notifications :as tn]))

(deftest module-test
  (with-open [ipc (rtest/create-ipc)]
    (rtest/launch-module! ipc tn/TimedNotificationsModule {:tasks 4 :threads 2})
    (let [module-name (get-module-name tn/TimedNotificationsModule)
          scheduled-post-depot (foreign-depot ipc module-name "*scheduled-post-depot")
          feeds (foreign-pstate ipc module-name "$$feeds")]
      ;; TODO:
      ;;  - easiest way to test is to make test mode for tick depot
      ;;  - and can use sim time from rama-helpers
      )))
