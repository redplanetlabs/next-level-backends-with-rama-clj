(ns nlb.family-tree-test
  (:use [com.rpl rama]
        [com.rpl.rama path])
  (:require
   [clojure.test :refer [deftest is testing]]
   [com.rpl.rama.test :as rtest]
   [nlb.family-tree :as ft])
  (:import [java.util UUID]))

(deftest family-tree-module-test
  (with-open [ipc (rtest/create-ipc)]
    (rtest/launch-module! ipc ft/FamilyTreeModule {:tasks 4 :threads 2})
    (let [module-name (get-module-name ft/FamilyTreeModule)
          people-depot (foreign-depot ipc module-name "*people-depot")
          family-tree (foreign-pstate ipc module-name "$$family-tree")
          ancestors-query (foreign-query ipc module-name "ancestors")
          descendants-count-query (foreign-query ipc module-name "descendants-count")
          p1 (UUID/randomUUID)
          p2 (UUID/randomUUID)
          p3 (UUID/randomUUID)
          p4 (UUID/randomUUID)
          p5 (UUID/randomUUID)
          p6 (UUID/randomUUID)
          p7 (UUID/randomUUID)
          p8 (UUID/randomUUID)
          p9 (UUID/randomUUID)
          p10 (UUID/randomUUID)
          p11 (UUID/randomUUID)
          p12 (UUID/randomUUID)
          p13 (UUID/randomUUID)
          p14 (UUID/randomUUID)]
      (foreign-append! people-depot (ft/->Person p1 nil nil "Person 1"))
      (foreign-append! people-depot (ft/->Person p2 nil nil "Person 2"))
      (foreign-append! people-depot (ft/->Person p3 nil nil "Person 3"))
      (foreign-append! people-depot (ft/->Person p4 nil nil "Person 4"))
      (foreign-append! people-depot (ft/->Person p5 nil nil "Person 5"))
      (foreign-append! people-depot (ft/->Person p6 nil nil "Person 6"))
      (foreign-append! people-depot (ft/->Person p7 p1 p2 "Person 7"))
      (foreign-append! people-depot (ft/->Person p8 p3 p4 "Person 8"))
      (foreign-append! people-depot (ft/->Person p9 p1 p5 "Person 9"))
      (foreign-append! people-depot (ft/->Person p10 p7 p8 "Person 10"))
      (foreign-append! people-depot (ft/->Person p11 p9 p10 "Person 11"))
      (foreign-append! people-depot (ft/->Person p12 p10 p11 "Person 12"))
      (foreign-append! people-depot (ft/->Person p13 p10 p11 "Person 13"))
      (foreign-append! people-depot (ft/->Person p14 p10 p11 "Person 14"))

      (is (= "Person 6" (foreign-select-one [(keypath p6) :name] family-tree)))
      (is (= 0 (foreign-select-one [(keypath p6) :children (view count)] family-tree)))
      (is (= 4 (foreign-select-one [(keypath p10) :children (view count)] family-tree)))
      (is (= 3 (foreign-select-one [(keypath p11) :children (view count)] family-tree)))

      (is (= #{p7 p8} (foreign-invoke-query ancestors-query p10 0)))
      (is (= #{p7 p8 p1 p2 p3 p4} (foreign-invoke-query ancestors-query p10 1)))
      (is (= #{p10 p11 p7 p8 p9 p1 p2 p3 p4 p5} (foreign-invoke-query ancestors-query p14 3)))

      (is (= {0 0} (foreign-invoke-query descendants-count-query p14 10)))
      (is (= {0 3 1 0} (foreign-invoke-query descendants-count-query p11 10)))
      (is (= {0 2} (foreign-invoke-query descendants-count-query p1 1)))
      (is (= {0 2 1 2} (foreign-invoke-query descendants-count-query p1 2)))
      (is (= {0 2 1 2 2 7} (foreign-invoke-query descendants-count-query p1 3)))
      )))
