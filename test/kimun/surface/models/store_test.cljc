(ns kimun.surface.models.store-test
  "Descriptor validation (docs/v2/07 §2), the pure plan builders of
   `kimun.surface.models.store`, and the golden descriptors of the two real
   corpora of 07 §9 (deskops, hum-scrapper), exported from their v1 classes
   with scripts/parity/export_models.py."
  (:require [clojure.test :refer [deftest is testing]]
            [clojure.edn :as edn]
            [babashka.fs :as fs]
            [kimun.surface.models.descriptor :as descriptor]
            [kimun.surface.models.edit :as edit]
            [kimun.surface.models.store :as mstore]
            [kimun.host.default :as host]
            [kimun.kernel.revision :as revision]))

(def base {:model/name "NoteDoc" :base [] :template "# ⸢rev•title⸥\n"
           :fields [{:name "title" :type :string :required true :description "title."}]})

(defn- problem [d & [opts]]
  (try (descriptor/validate! d (or opts {})) nil
       (catch clojure.lang.ExceptionInfo e (:problem (ex-data e)))))

(deftest descriptor-shape-is-validated
  (is (nil? (problem base)))
  (is (re-find #"capitalised" (problem (assoc base :model/name "note"))))
  (is (re-find #"`:template`" (problem (dissoc base :template))))
  (is (re-find #"declared more than once" (problem (update base :fields conj {:name "title" :type :int :description "title."}))))
  (is (re-find #"unknown type" (problem (assoc-in base [:fields 0 :type] :float))))
  (is (re-find #"unknown type" (problem (assoc-in base [:fields 0 :type] [:list :nope]))))
  (is (re-find #"not of its type" (problem (assoc-in base [:fields 0 :default] 3))))
  (is (re-find #"not of its type" (problem (update base :fields conj {:name "s" :type [:enum "a" "b"] :default "c" :description "s."}))))
  (is (nil? (problem (update base :fields conj {:name "s" :type [:list [:map :string]] :default [] :description "s."}))))
  (is (re-find #"`:base`" (problem (assoc base :base "X"))))
  (is (re-find #"`:model/version`" (problem (assoc base :model/version 0))))
  (is (re-find #"py•" (problem (assoc base :template "⸢py•x⸥"))))
  (testing "every field must define a non-empty description"
    (is (re-find #"fields must define a non-empty description: title"
                 (problem (assoc-in base [:fields 0] {:name "title" :type :string :required true}))))
    (is (re-find #"fields must define a non-empty description: title"
                 (problem (assoc-in base [:fields 0 :description] "   "))))
    (is (re-find #"title, body"
                 (problem (assoc base :fields [{:name "title" :type :string :required true}
                                               {:name "body" :type :string :required true}])))))
  (testing "bases are checked only against a known set"
    (is (nil? (problem (assoc base :base ["Other"]))))
    (is (re-find #"`Other` is not defined" (problem (assoc base :base ["Other"]) {:known-models #{"NoteDoc"}})))
    (is (nil? (problem (assoc base :base ["Other"]) {:known-models #{"NoteDoc" "Other"}}))))
  (testing ":state must agree with its enum field"
    (let [d (update base :fields conj {:name "st" :type [:enum "a" "b"] :required true :description "st."})]
      (is (nil? (problem (assoc d :state {:field "st" :order ["a" "b"] :terminal ["b"]}))))
      (is (re-find #"names no field" (problem (assoc d :state {:field "zz" :order []}))))
      (is (re-find #"must be an `\[:enum" (problem (assoc d :state {:field "title" :order []}))))
      (is (re-find #"exactly the enum values" (problem (assoc d :state {:field "st" :order ["a"]}))))
      (is (re-find #"subset" (problem (assoc d :state {:field "st" :order ["a" "b"] :terminal ["c"]})))))))

(def opts {:actor "jp" :timestamp "2026-10-06T00:00:00.000Z" :tree-id "01ARZ3NDEKTSV4RRFFQ69G5FAV"})

(defn- define [store d]
  (let [r (mstore/define-plan host/host store d (assoc opts :base (:head store)))]
    (if (:plan r) (:store (revision/apply-plan store (:plan r))) store)))

(deftest plans-define-list-and-keep-history
  (let [s0 (revision/empty-store host/host {"jp" :all})
        s1 (define s0 base)
        other (assoc base :model/name "OtherDoc")
        s2 (define s1 other)
        s3 (define s2 (assoc base :template "# Note ⸢rev•title⸥\n"))]
    (is (= [] (mstore/models s0)))
    (is (nil? (mstore/models-tree s0)))
    (is (= ["NoteDoc" "OtherDoc"] (mapv :name (mstore/models s3))) "definition order survives a redefinition")
    (is (= [2 1] (mapv :version (mstore/models s3))))
    (is (= "# Note ⸢rev•title⸥\n" (:template (:descriptor (mstore/model s3 "NoteDoc")))))
    (is (= s3 (define s3 (assoc base :template "# Note ⸢rev•title⸥\n"))) "the same contract builds no plan")
    (is (:unchanged (mstore/define-plan host/host s3 other opts)))
    (is (= [2 1] (mapv :version (mstore/history s3 "NoteDoc"))))
    (is (= [(:head s3) (:head s1)] (mapv :revision (mstore/history s3 "NoteDoc"))))
    (is (nil? (mstore/find-model s3 "Ghost")))
    (is (= :models/missing (try (mstore/model s3 "Ghost") (catch clojure.lang.ExceptionInfo e (:type (ex-data e))))))
    (is (= :models/invalid (try (mstore/define-plan host/host s3 (dissoc base :template) opts)
                                (catch clojure.lang.ExceptionInfo e (:type (ex-data e))))))))

(defn- goldens [corpus]
  (mapv #(edn/read-string (slurp (str %))) (sort (fs/glob (str "test/fixtures/models/" corpus) "*.edn"))))

(deftest golden-descriptors-of-the-real-corpora
  (doseq [[corpus n] [["deskops" 23] ["hum-scrapper" 6]]]
    (testing corpus
      (let [ds (goldens corpus)
            known (set (map :model/name ds))
            store (reduce define (revision/empty-store host/host {"jp" :all}) ds)]
        (is (= n (count ds)))
        (doseq [d ds]
          (testing (:model/name d)
            (is (nil? (problem d {:known-models known})) "valid, and every base is in the corpus")
            (is (= [] (edit/undeclared-fields d)) "the template names only declared fields")))
        (is (= (set (map :model/name ds)) (set (map :name (mstore/models store)))))
        (is (every? #(= 1 (:version %)) (mstore/models store)))))))

(deftest v1-descriptor-keeps-its-address-byte-for-byte
  ;; M1f guard: spec 16 support must not add a key to, or reorder, a stored v1 descriptor. The id below
  ;; was computed on 876ba5a8 (before M1f) for ticket.edn at version 1; `bb oracle` guards the kernel.
  (let [d (assoc (edn/read-string (slurp "test/fixtures/models/ticket.edn")) :model/version 1)]
    (is (= "838f817f1f48014f7106d7b97ce67dd82317709b36ab61875655eb7037e5e4d4" (mstore/node-id host/host d)))
    (is (= d (edn/read-string (mstore/serialize d))) "serialize adds nothing")))
