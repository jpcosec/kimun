(ns kimun.surface.index.build-test
  "The derived index as a pure value: its contract (docs/v2/06 §6), that it
   agrees with the surfaces it summarises, determinism, the EDN round trip
   and a document that no longer extracts. Each test names in a comment the
   v1 function whose promise it carries."
  (:require [babashka.fs :as fs]
            [clojure.edn :as edn]
            [clojure.test :refer [deftest is testing]]
            [kimun.cli.main :as main]
            [kimun.cli.store :as cli-store]
            [kimun.surface.docs :as tdocs]
            [kimun.surface.index.build :as build]
            [kimun.surface.index.where :as where]
            [kimun.surface.models.store :as mstore]
            [kimun.surface.semantic :as sem]
            [sldb.host.default :as host]
            [sldb.kernel.store :as store]))

(def env {"USER" "tester"})
(defn- run [cwd & argv] (:envelope (main/run argv env cwd)))
(defn- opened [p] (:store (cli-store/open! (cli-store/resolve-store {} env p))))

(defn- model [n base semantics fields template]
  (pr-str {:model/name n :model/version 1 :base base :semantics semantics
           :fields (into [{:name "title" :type :string :required true :description "Title."}] fields)
           :template template}))

(defn- define! [p n text]
  (spit (str (fs/path p (str n ".edn"))) text)
  (is (true? (:ok (run p "models" "define" "-f" (str n ".edn")))) n))

(defn- move-contract!
  "Redefines model `n` as `descriptor` straight through the surface plan, the
   way a writer that skips the CLI's guard would: its documents stay behind."
  [p n descriptor]
  (let [{:keys [backend] s :store} (cli-store/open! (cli-store/resolve-store {} env p))
        r (mstore/define-plan host/host s (edn/read-string descriptor)
                              {:actor "human/tester" :timestamp "2026-01-01T00:00:00.000Z"})]
    (is (some? (:plan r)) n)
    (store/commit! backend s (:plan r))))

(defn- world
  "Spec (type.knowledge.spec) with s1 and s2, Topology (layer.topology) with
   t1, and the declared relations layer.topology < type.knowledge, spec ≡ norm."
  []
  (let [p (str (fs/create-temp-dir {:prefix "kimun-index-"}))]
    (run p "stores" "init" "--name" "p")
    (define! p "Spec" (model "Spec" [] {"type" ["knowledge" "spec"]}
                             [{:name "status" :type :string :required true :description "Status."}]
                             "# ⸢rev•title⸥\n\n## State\n\nStatus: ⸢rev•status⸥\n"))
    (define! p "Topology" (model "Topology" [] {"layer" ["topology"]} [] "# ⸢rev•title⸥\n"))
    (is (true? (:ok (run p "docs" "create" "Spec" "s1" "--set" "title=S1" "--set" "status=open"))))
    (is (true? (:ok (run p "docs" "create" "Spec" "s2" "--set" "title=S2" "--set" "status=closed" "--path" "notes/s2.md"))))
    (is (true? (:ok (run p "docs" "create" "Topology" "t1" "--set" "title=T1"))))
    (is (true? (:ok (run p "semantic" "parent-add" "layer.topology" "type.knowledge"))))
    (is (true? (:ok (run p "semantic" "equivalent-add" "type.knowledge.spec" "norm"))))
    p))

(deftest the-index-keeps-its-contract
  (let [s (opened (world)) idx (build/build s)
        id #(get-in idx [:by-name %])]
    (is (= (set build/contract-keys) (set (keys idx))))
    (is (= (:head s) (:head idx)))
    (is (some? (:head idx)))
    (is (= ["Spec" "Topology"] (keys (:models idx))))
    (is (= ["status" "title"] (sort (map :name (get-in idx [:models "Spec" :fields])))))
    (is (= {"Spec" (vec (sort [(id "s1") (id "s2")])) "Topology" [(id "t1")]} (:by-model idx)))
    (is (= #{"s1" "s2" "t1"} (set (keys (:by-name idx)))))
    (is (= (set (keys (:docs idx))) (set (vals (:by-name idx)))))
    (testing "a document entry"
      (let [d (get-in idx [:docs (id "s2")]) full (tdocs/document s "s2")]
        (is (= {"title" "S2" "status" "closed"} (:payload d)))
        (is (= (:payload full) (:payload d)))
        (is (= "notes/s2.md" (:path d)) "relative to the project root, never absolute")
        (is (= (:source full) (:source d)))
        (is (= (:field-hashes full) (:field-hashes d)))
        (is (= :intact (:state d)))
        (is (= (id "s2") (:tree d)))
        (is (= [] (:bases d)))
        (is (some #{"type.knowledge.spec"} (:tags d)))
        (is (= (:tags d) (vec (sort (:tags d)))))))
    (testing "tags, DAG and equivalences agree with kimun.surface.semantic"
      (is (= (sem/dag s) (:tag-dag idx)))
      (is (= (into {} (map (fn [e] [(id (:name e)) (:tags e)])) (sem/document-tags s))
             (into {} (map (fn [[t d]] [t (:tags d)])) (:docs idx))))
      (is (= (vec (sort [(id "s1") (id "s2")])) (get-in idx [:tags "type.knowledge.spec"])))
      (is (= [(id "t1")] (get-in idx [:tags "layer.topology"])))
      (is (= {"norm" ["type.knowledge.spec"] "type.knowledge.spec" ["norm"]} (:equivalences idx)) "read in both directions")
      (is (sem/in-scope? (:tag-dag idx) "type.knowledge" (get-in idx [:docs (id "t1") :tags])) "the declared parent reaches t1"))
    (testing "sections of each model's template and the fields they own"
      (is (= ["rev-title" "rev-title/state"] (mapv :path (get-in idx [:sections "Spec" :sections]))))
      (is (= {"status" "rev-title/state" "title" "rev-title"} (get-in idx [:sections "Spec" :owners])))
      (is (contains? (:sections idx) "Topology")))
    (is (= {} (:links idx)))
    (is (= {} (:anchors idx)))
    (testing "an entry is what where/matches? takes"
      (let [ast (where/parse "status ~ \"^open$\"")]
        (is (= ["s1"] (sort (map :name (filter #(where/matches? ast %) (vals (:docs idx)))))))))))

(deftest the-index-is-deterministic-and-round-trips-through-edn
  (let [p (world) idx (build/build (opened p))]
    (is (= idx (build/build (opened p))) "same store, opened again: equal map")
    (is (= (pr-str idx) (pr-str (build/build (opened p)))) "and the same text")
    (is (= idx (edn/read-string (pr-str idx))))
    (is (= (:tag-dag idx) (:tag-dag (edn/read-string (pr-str idx)))))))

(deftest an-empty-store-has-an-empty-index
  (let [p (str (fs/create-temp-dir {:prefix "kimun-index-"}))]
    (run p "stores" "init" "--name" "p")
    (let [s (opened p) idx (build/build s)]
      (is (= (:head s) (:head idx)))
      (is (= {} (:docs idx) (:models idx) (:by-name idx) (:by-model idx) (:tags idx) (:equivalences idx)))
      (is (= idx (edn/read-string (pr-str idx)))))))

(deftest a-document-that-no-longer-extracts-does-not-break-the-build
  (let [p (world)]
    ;; the contract moves under t1: a required field its text does not carry
    (move-contract! p "Topology" (model "Topology" [] {"layer" ["topology"]}
                                        [{:name "owner" :type :string :required true :description "Owner."}]
                                        "# ⸢rev•title⸥\n\nOwner: ⸢rev•owner⸥\n"))
    (let [s (opened p) idx (build/build s) d (get-in idx [:docs (get-in idx [:by-name "t1"])])]
      (is (string? (:error d)))
      (is (not (contains? d :payload)))
      (is (not (contains? d :hash-c)))
      (is (= :superseded (:state d)))
      (is (= "Topology" (:model d)))
      (is (= (tdocs/text-hash (:host s) (:source d)) (:hash-d d)))
      (is (= (get-in idx [:models "Topology" :tags]) (:tags d)) "the model's tags still hold")
      (is (some #{"layer.topology"} (:tags d)))
      (is (= ["t1"] (build/skipped idx)))
      (is (some? (get-in idx [:docs (get-in idx [:by-name "s1"]) :payload])) "the others are indexed")
      (is (= idx (edn/read-string (pr-str idx))))
      (is (false? (where/matches? (where/parse "title ~ \"^T1$\"") d)) "no payload, no match — and no exception"))))

;; v1: test_store_fingerprint_matches_the_index_walk
(deftest the-fingerprint-matches-the-store
  (let [s (opened (world)) fp (build/fingerprint (build/build s))]
    (is (= (:head s) (:store fp)) "the store hash is the head revision")
    (is (= (into {} (map (juxt :name :id)) (mstore/models s)) (:models fp)) "a model hash is its descriptor node id")
    (is (= (set (map #(str (:model %) ":" (:name %)) (tdocs/documents s))) (set (keys (:documents fp)))))))

;; v1: test_store_fingerprint_document_hashes_match_their_shards
(deftest fingerprint-document-hashes-match-the-documents
  (let [s (opened (world)) fp (build/fingerprint (build/build s))]
    (doseq [d (tdocs/documents s) :let [full (tdocs/document s (:name d))]]
      (is (= {:hash-d (:source-hash full) :hash-c (:content-hash full)}
             (get-in fp [:documents (str (:model d) ":" (:name d))]))))))

;; v1: test_runtime_documents_carry_their_index_hashes
(deftest indexed-documents-carry-the-hashes-of-the-loaded-ones
  (let [s (opened (world)) idx (build/build s)]
    (is (= (mapv #(let [f (tdocs/document s (:name %))] [(:name f) (:source-hash f) (:content-hash f)]) (tdocs/documents s))
           (mapv (juxt :name :hash-d :hash-c) (sort-by :name (vals (:docs idx))))))))
