(ns kimun.parity.hashing-test
  "v1 `store/test_hashing.py`, the index and layer hashes: v1 hashed a
   documents index and a models layer; kimun has neither file, but the same
   promises hold on the store fingerprint read from the derived index — the
   inventory and the model identities are a function of content, not of the
   order or the store they were written in. Each test names its v1 function."
  (:require [babashka.fs :as fs]
            [clojure.string :as str]
            [clojure.test :refer [deftest is testing]]
            [kimun.cli.main :as main]
            [kimun.cli.store :as cli-store]
            [kimun.surface.index.build :as build]))

(def env {"USER" "tester"})
(defn- run [cwd & argv] (:envelope (main/run argv env cwd)))
(defn- fingerprint [p]
  (build/fingerprint (build/build (:store (cli-store/open! (cli-store/resolve-store {} env p))))))

(def book
  {:model/name "Book" :model/version 1 :base []
   :fields [{:name "title" :type :string :required true :description "Title."}]
   :template "# ⸢rev•title⸥\n"})

(defn- project
  "A store with the model `model` and one Book per name of `names`, created in that order."
  [model names]
  (let [p (str (fs/create-temp-dir {:prefix "kimun-hashing-"}))]
    (run p "stores" "init" "--name" "p")
    (spit (str (fs/path p "book.edn")) (pr-str model))
    (is (true? (:ok (run p "models" "define" "-f" "book.edn"))))
    (doseq [n names]
      (is (true? (:ok (run p "docs" "create" "Book" n "--set" (str "title=" (str/upper-case n)))))))
    p))

;; v1: store/test_hashing.py::test_hash_documents_index_deterministic
(deftest the-document-inventory-hashes-deterministically
  (let [a (project book ["a" "b"]) b (project book ["b" "a"])]
    (is (= (fingerprint a) (fingerprint a)) "the same store read twice is the same value")
    (is (= 2 (count (:documents (fingerprint a)))))
    (is (every? #(and (:hash-d %) (:hash-c %)) (vals (:documents (fingerprint a)))))
    (is (= (:documents (fingerprint a)) (:documents (fingerprint b)))
        "the same inventory written in another order, in another store, hashes the same")))

;; v1: store/test_hashing.py::test_hash_documents_index_changes_on_inventory_change
(deftest the-document-inventory-hash-changes-with-the-inventory
  (let [p (project book ["a"]) before (fingerprint p)]
    (is (true? (:ok (run p "docs" "create" "Book" "b" "--set" "title=B"))))
    (let [after (fingerprint p)]
      (is (not= (:documents before) (:documents after)))
      (is (not= (:store before) (:store after)) "the head of the store moved with it")
      (is (= (get-in before [:documents "Book:a"]) (get-in after [:documents "Book:a"]))
          "the entry of the document that did not change did not move"))))

;; v1: store/test_hashing.py::test_hash_models_layer_deterministic
(deftest the-model-layer-hashes-deterministically
  (let [a (project book [])
        b (project (into (array-map) (reverse (seq book))) ["x"])]
    (is (= (:models (fingerprint a)) (:models (fingerprint a))))
    (is (= ["Book"] (keys (:models (fingerprint a)))))
    (is (= (:models (fingerprint a)) (:models (fingerprint b)))
        "the same definition, keys written in another order, in a store with other documents")))

;; v1: store/test_hashing.py::test_hash_models_layer_changes_on_hash_b_change
(deftest the-model-layer-hash-changes-with-a-model
  (let [p (project book ["a"]) before (fingerprint p)]
    (spit (str (fs/path p "book.edn"))
          (pr-str (-> book (assoc :model/version 2)
                      (update :fields conj {:name "status" :type :string :default "open" :description "Status."}))))
    (is (true? (:ok (run p "models" "define" "-f" "book.edn"))))
    (let [after (fingerprint p)]
      (is (not= (get-in before [:models "Book"]) (get-in after [:models "Book"])))
      (is (not= (:store before) (:store after))))))

;; v1: store/test_hashing.py::test_hash_fields_normalizes_frontmatter_date_values
(deftest a-frontmatter-date-does-not-break-hashing
  (let [text "---\ncreated: 2026-04-22\n---\n\n# Request\n\nBody text.\n"
        roots (for [_ [1 2]]
                (let [p (project book [])]
                  (spit (str (fs/path p "fm.md")) text)
                  (is (true? (:ok (run p "docs" "ingest" "fm.md" "--name" "fm"))))
                  (is (= text (:data (run p "docs" "render" "fm"))) "the date is kept as written")
                  (is (true? (:ok (run p "stores" "verify"))) "every object hashes to its name")
                  (is (true? (:ok (run p "rebuild-indexes"))))
                  (is (= {} (:documents (fingerprint p))) "an untyped document adds no typed entry")
                  (:data (run p "docs" "render" "fm"))))]
    (is (apply = roots))))
