(ns nlb.collaborative-document-editor-test
  (:use [com.rpl rama]
        [com.rpl.rama path])
  (:require
   [clojure.test :refer [deftest is testing]]
   [com.rpl.rama.test :as rtest]
   [nlb.collaborative-document-editor :as cde]))

(defn- test-add-edit
  ([offset content]
    (test-add-edit 123 0 offset content))
  ([id version offset content]
    (cde/->Edit id version offset (cde/->AddText content))))

(defn- test-remove-edit
  ([offset amount]
    (test-remove-edit 123 0 offset amount))
  ([id version offset amount]
    (cde/->Edit id version offset (cde/->RemoveText amount))))

(deftest transform-edit-test
  (let [edit (test-add-edit 10 "abcde")]
    (testing "Add against missed add"
      (is (= [(test-add-edit 14 "abcde")]
             (cde/transform-edit edit [(test-add-edit 8 "....")])))
      (is (= [(test-add-edit 12 "abcde")]
             (cde/transform-edit edit [(test-add-edit 10 "..")])))
      (is (= [(test-add-edit 10 "abcde")]
             (cde/transform-edit edit [(test-add-edit 17 "...")])))
      (is (= [(test-add-edit 10 "abcde")]
             (cde/transform-edit edit [(test-add-edit 20 ".")])))
      (is (= [(test-add-edit 10 "abcde")]
             (cde/transform-edit edit [(test-add-edit 12 ".")]))))
    (testing "Add against missed remove"
      (is (= [(test-add-edit 7 "abcde")]
             (cde/transform-edit edit [(test-remove-edit 8 3)])))
      (is (= [(test-add-edit 6 "abcde")]
             (cde/transform-edit edit [(test-remove-edit 10 4)])))
      (is (= [(test-add-edit 10 "abcde")]
             (cde/transform-edit edit [(test-remove-edit 15 2)])))
      (is (= [(test-add-edit 10 "abcde")]
             (cde/transform-edit edit [(test-remove-edit 20 2)])))
      ))
  (let [edit (test-remove-edit 10 6)]
    (testing "Remove against missed add"
      (is (= [(test-remove-edit 13 6)]
             (cde/transform-edit edit [(test-add-edit 8 "...")])))
      (is (= [(test-remove-edit 14 6)]
             (cde/transform-edit edit [(test-add-edit 10 "....")])))
      (is (= [(test-remove-edit 10 6)]
             (cde/transform-edit edit [(test-add-edit 16 "...")])))
      (is (= [(test-remove-edit 10 6)]
             (cde/transform-edit edit [(test-add-edit 20 "...")])))
      (is (= [(test-remove-edit 10 2) (test-remove-edit 15 4)]
             (cde/transform-edit edit [(test-add-edit 12 "...")]))))
    (testing "Remove against missed remove"
      (is (= [(test-remove-edit 8 6)]
             (cde/transform-edit edit [(test-remove-edit 0 2)])))
      (is (= [(test-remove-edit 8 3)]
             (cde/transform-edit edit [(test-remove-edit 8 5)])))
      (is (= []
             (cde/transform-edit edit [(test-remove-edit 7 100)])))
      (is (= [(test-remove-edit 10 5)]
             (cde/transform-edit edit [(test-remove-edit 10 1)])))
      (is (= []
             (cde/transform-edit edit [(test-remove-edit 10 6)])))
      (is (= []
             (cde/transform-edit edit [(test-remove-edit 10 10)])))
      (is (= [(test-remove-edit 10 4)]
             (cde/transform-edit edit [(test-remove-edit 12 2)])))
      (is (= [(test-remove-edit 10 2)]
             (cde/transform-edit edit [(test-remove-edit 12 10)])))
      (is (= [(test-remove-edit 10 6)]
             (cde/transform-edit edit [(test-remove-edit 16 1)])))
      (is (= [(test-remove-edit 10 6)]
             (cde/transform-edit edit [(test-remove-edit 16 10)])))
      (is (= [(test-remove-edit 10 6)]
             (cde/transform-edit edit [(test-remove-edit 18 10)])))
      ))
    (testing "Transform against multiple edits"
      (is (= [(test-remove-edit 19 1) (test-remove-edit 22 3)]
             (cde/transform-edit
               (test-remove-edit 20 5)
               [(test-add-edit 10 "...")
                (test-remove-edit 100 10)
                (test-remove-edit 19 5)
                (test-add-edit 20 "..")])))))

(deftest module-test
  (with-open [ipc (rtest/create-ipc)]
    (rtest/launch-module! ipc cde/CollaborativeDocumentEditorModule {:tasks 4 :threads 2})
    (let [module-name (get-module-name cde/CollaborativeDocumentEditorModule)
          edit-depot (foreign-depot ipc module-name "*edit-depot")
          docs (foreign-pstate ipc module-name "$$docs")]
      (foreign-append!
        edit-depot
        (cde/->Edit 123 0 0 (cde/->AddText "Hellox")))
      (rtest/wait-for-microbatch-processed-count ipc module-name "core" 1)
      (is (= "Hellox" (foreign-select-one [(keypath 123) :content] docs)))

      ; (foreign-append!
      ;   edit-batch-depot
      ;   (cde/->EditBatch 123
      ;                    1
      ;                    [(cde/->Edit 5 (cde/->RemoveText 1))
      ;                     (cde/->Edit 5 (cde/->AddText " world"))]))
      ; (rtest/wait-for-microbatch-processed-count ipc module-name "core" 2)
      ; (is (= "Hello world" (foreign-select-one [(keypath 123) :content] docs)))
      ;
      ; (foreign-append!
      ;   edit-batch-depot
      ;   (cde/->EditBatch 123
      ;                    2
      ;                    [(cde/->Edit 11 (cde/->AddText "!"))]))
      ; (rtest/wait-for-microbatch-processed-count ipc module-name "core" 3)
      ; (is (= "Hello world!" (foreign-select-one [(keypath 123) :content] docs)))

      ;; TODO:
      ;;  - do multiple edits at same version
      ;;  - do another one with many edits coming in at different versions, including behind

      ;; TODO use foreign-proxy to demonstrate
      ;;    - or just show in post and how the diffs works
      )))
