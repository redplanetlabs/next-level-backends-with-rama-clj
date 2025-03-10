(ns nlb.collaborative-document-editor-test
  (:use [com.rpl rama]
        [com.rpl.rama path])
  (:require
   [clojure.test :refer [deftest is testing]]
   [com.rpl.rama.test :as rtest]
   [nlb.collaborative-document-editor :as cde]))

(defn mk-add-edit
  ([offset content]
    (mk-add-edit 123 0 offset content))
  ([id version offset content]
    (cde/->Edit id version offset (cde/->AddText content))))

(defn mk-remove-edit
  ([offset amount]
    (mk-remove-edit 123 0 offset amount))
  ([id version offset amount]
    (cde/->Edit id version offset (cde/->RemoveText amount))))

(deftest transform-edit-test
  (let [edit (mk-add-edit 10 "abcde")]
    (testing "Add against missed add"
      (is (= [(mk-add-edit 14 "abcde")]
             (cde/transform-edit edit [(mk-add-edit 8 "....")])))
      (is (= [(mk-add-edit 12 "abcde")]
             (cde/transform-edit edit [(mk-add-edit 10 "..")])))
      (is (= [(mk-add-edit 10 "abcde")]
             (cde/transform-edit edit [(mk-add-edit 17 "...")])))
      (is (= [(mk-add-edit 10 "abcde")]
             (cde/transform-edit edit [(mk-add-edit 20 ".")])))
      (is (= [(mk-add-edit 10 "abcde")]
             (cde/transform-edit edit [(mk-add-edit 12 ".")]))))
    (testing "Add against missed remove"
      (is (= [(mk-add-edit 7 "abcde")]
             (cde/transform-edit edit [(mk-remove-edit 8 3)])))
      (is (= [(mk-add-edit 6 "abcde")]
             (cde/transform-edit edit [(mk-remove-edit 10 4)])))
      (is (= [(mk-add-edit 10 "abcde")]
             (cde/transform-edit edit [(mk-remove-edit 15 2)])))
      (is (= [(mk-add-edit 10 "abcde")]
             (cde/transform-edit edit [(mk-remove-edit 20 2)])))
      ))
  (let [edit (mk-remove-edit 10 6)]
    (testing "Remove against missed add"
      (is (= [(mk-remove-edit 13 6)]
             (cde/transform-edit edit [(mk-add-edit 8 "...")])))
      (is (= [(mk-remove-edit 14 6)]
             (cde/transform-edit edit [(mk-add-edit 10 "....")])))
      (is (= [(mk-remove-edit 10 6)]
             (cde/transform-edit edit [(mk-add-edit 16 "...")])))
      (is (= [(mk-remove-edit 10 6)]
             (cde/transform-edit edit [(mk-add-edit 20 "...")])))
      (is (= [(mk-remove-edit 15 4) (mk-remove-edit 10 2)]
             (cde/transform-edit edit [(mk-add-edit 12 "...")]))))
    (testing "Remove against missed remove"
      (is (= [(mk-remove-edit 8 6)]
             (cde/transform-edit edit [(mk-remove-edit 0 2)])))
      (is (= [(mk-remove-edit 8 3)]
             (cde/transform-edit edit [(mk-remove-edit 8 5)])))
      (is (= []
             (cde/transform-edit edit [(mk-remove-edit 7 100)])))
      (is (= [(mk-remove-edit 10 5)]
             (cde/transform-edit edit [(mk-remove-edit 10 1)])))
      (is (= []
             (cde/transform-edit edit [(mk-remove-edit 10 6)])))
      (is (= []
             (cde/transform-edit edit [(mk-remove-edit 10 10)])))
      (is (= [(mk-remove-edit 10 4)]
             (cde/transform-edit edit [(mk-remove-edit 12 2)])))
      (is (= [(mk-remove-edit 10 2)]
             (cde/transform-edit edit [(mk-remove-edit 12 10)])))
      (is (= [(mk-remove-edit 10 6)]
             (cde/transform-edit edit [(mk-remove-edit 16 1)])))
      (is (= [(mk-remove-edit 10 6)]
             (cde/transform-edit edit [(mk-remove-edit 16 10)])))
      (is (= [(mk-remove-edit 10 6)]
             (cde/transform-edit edit [(mk-remove-edit 18 10)])))
      ))
    (testing "Transform against multiple edits"
      (is (= [(mk-remove-edit 22 3) (mk-remove-edit 19 1)]
             (cde/transform-edit
               (mk-remove-edit 20 5)
               [(mk-add-edit 10 "...")
                (mk-remove-edit 100 10)
                (mk-remove-edit 19 5)
                (mk-add-edit 20 "..")])))))

(deftest module-test
  (with-open [ipc (rtest/create-ipc)]
    (rtest/launch-module! ipc cde/CollaborativeDocumentEditorModule {:tasks 4 :threads 2})
    (let [module-name (get-module-name cde/CollaborativeDocumentEditorModule)
          edit-depot (foreign-depot ipc module-name "*edit-depot")
          doc+version (foreign-query ipc module-name "doc+version")]
      (foreign-append!
        edit-depot
        (cde/->Edit 123 0 0 (cde/->AddText "Hellox")))
      (is (= {:doc "Hellox" :version 1} (foreign-invoke-query doc+version 123)))

      (foreign-append!
        edit-depot
        (cde/->Edit 123 1 5 (cde/->RemoveText 1)))
      (is (= {:doc "Hello" :version 2} (foreign-invoke-query doc+version 123)))

      (foreign-append!
        edit-depot
        (cde/->Edit 123 2 5 (cde/->AddText " wor")))
      (is (= {:doc "Hello wor" :version 3} (foreign-invoke-query doc+version 123)))

      (foreign-append!
        edit-depot
        (cde/->Edit 123 3 9 (cde/->AddText "ld!")))
      (is (= {:doc "Hello world!" :version 4} (foreign-invoke-query doc+version 123)))

      (foreign-append!
        edit-depot
        (cde/->Edit 123 2 5 (cde/->AddText "abcd")))
      (foreign-append!
        edit-depot
        (cde/->Edit 123 2 0 (cde/->RemoveText 4)))
      (foreign-append!
        edit-depot
        (cde/->Edit 123 1 0 (cde/->RemoveText 3)))
      (is (= {:doc "o world!abcd" :version 6} (foreign-invoke-query doc+version 123)))
      )))
