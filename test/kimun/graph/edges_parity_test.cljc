(ns kimun.graph.edges-parity-test
  "Differential on real data: the edge index v1 held for 40 real documents of
   23 models (frozen under test/fixtures/parity/edges, no v1 at run time)
   against what `kimun.graph.edges/index` derives from a store rebuilt from
   the same descriptors and texts. Compared: the set of (relation, from, to)
   per scope and relation, both directions, against v1's *composed* view (what
   v1's readers return), plus the type of every endpoint. A difference is
   either absent or listed, with its reason, in EXPECTED-DIFFS.edn; a listed
   entry that no longer differs fails."
  (:require [kimun.test-util :as ktu] [babashka.fs :as fs]
            [cheshire.core :as json]
            [clojure.edn :as edn]
            [clojure.set :as set]
            [clojure.string :as str]
            [clojure.test :refer [deftest is testing]]
            [kimun.cli.main :as main]
            [kimun.cli.store :as cli-store]
            [kimun.surface.docs :as tdocs]
            [kimun.graph.edges :as edges]
            [kimun.surface.models.store :as mstore]
            [kimun.host.default :as host]
            [kimun.kernel.ports :as ports]
            [kimun.kernel.store :as store]))

(def ^:private fixtures "test/fixtures/parity/edges/")
(def ^:private env {"USER" "tester"})

(defn- ok! [cwd & argv]
  (let [e (:envelope (main/run (vec argv) env cwd))]
    (when-not (true? (:ok e)) (throw (ex-info (str "setup failed: " (pr-str argv)) {:error (:error e)})))
    e))

(defn- registered-models
  "The models v1 had registered: those its index holds a `has_model` edge for."
  [v1-edges]
  (set (keep #(when (= "has_model" (% "relation")) (subs (% "target") (count "sldb://model/"))) v1-edges)))

(defn- rebuild!
  "A kimun store holding the fixture: `descriptors` defined, every document
   tracked at its relative path with its frozen text. One open store, the plan
   builders the CLI's `models define` and `docs track` call, one commit each.
   Returns the opened store."
  [descriptors documents]
  (let [p (str (ktu/temp-dir "kimun-edges-parity-"))
        _ (ok! p "stores" "init" "--name" "parity")
        {:keys [backend] s :store} (cli-store/open! (cli-store/resolve-store {} env p))
        ulid #(ports/ulid (:ids host/host))
        stamp {:actor "human/tester" :timestamp "2026-01-01T00:00:00.000Z"}
        commit (fn [s plan] (:store (store/commit! backend s plan)))
        s (reduce (fn [s d] (commit s (:plan (mstore/define-plan host/host s d (assoc stamp :tree-id (ulid)))))) s descriptors)]
    ;; the frozen v1 fixture holds two documents on one file (`Board` and `desk-board`, both on
    ;; desk/tasks/Board.md): v1 allowed it. A new `docs track` refuses a held file (one route, one
    ;; document, K2a); the fixture is rebuilt in the explicit legacy mode a v1 migration uses, where
    ;; the second document is tracked without a route. The graph compared below does not depend on routes.
    (reduce (fn [s d] (commit s (:plan (tdocs/track-plan host/host s (d "model") (d "name") (d "text")
                                                         (assoc stamp :doc (ulid) :tree-id (ulid) :path (d "path")
                                                                :shared-path :allow)))))
            s documents)))

(defn- v1-view
  "v1's composed view of the frozen shards, as its readers return it
   (`resolve_edges` + the compose step): a `composes` edge points at the
   document owning the referenced path (`resolved_target`; none = dropped) and
   an edge of origin `schema`/`alias` whose target is no node of v1's index is
   dropped. `{[relation from to] scope}`."
  [v1-edges]
  (into {}
        (keep (fn [e]
                (let [rel (e "relation") origin (get-in e ["metadata" "origin"])
                      to (if (= "composes" rel) (e "resolved_target") (e "target"))]
                  (when (and to (or (= "composes" rel) (e "target_in_v1_index") (not (#{"schema" "alias"} origin))))
                    [[rel (e "source") to] (e "scope")]))))
        v1-edges))

(defn- scope-of
  "The shard scope v1 files an edge under."
  [{:keys [from type]}]
  (cond (#{"semantic_parent" "semantic_equivalent"} type) "semantic"
        (= "has_model" type) "store"
        (and (str/starts-with? from "sldb://model/") (not= "has_document" type)) "model"
        :else "document"))

(defn- by-group [view] (reduce-kv (fn [m t scope] (update m [scope (first t)] (fnil conj #{}) t)) {} view))

(def ^:private v1-composed-counts
  "Edges of v1's composed view per [scope relation]: the frozen 2497 minus the
   11 dangling `schema` edges v1's readers drop (9 applies_to_*, 2 extends)."
  {["document" "tagged_as"] 1794 ["document" "has_section"] 298 ["document" "has_document"] 36 ["document" "composes"] 14
   ["model" "has_field"] 216 ["model" "tagged_as"] 50 ["store" "has_model"] 24 ["semantic" "semantic_parent"] 54})

;; Edges both sides hold, per [scope relation], once the listed differences are set aside.
(def ^:private reproduced-counts
  {["document" "tagged_as"] 1373 ["document" "has_section"] 224 ["document" "has_document"] 36 ["document" "composes"] 14
   ["model" "has_field"] 216 ["model" "tagged_as"] 50 ["store" "has_model"] 24 ["semantic" "semantic_parent"] 54})

(defn- covered-by
  "The triples of `universe` an EXPECTED-DIFFS entry speaks for: one edge
   (`:relation :from :to`), every edge of a relation leaving a node
   (`:relation :from`) or every edge of a relation (`:relation`)."
  [{:keys [relation from to]} universe]
  (set (filter (fn [[r f t]] (and (= relation r) (or (nil? from) (= from f)) (or (nil? to) (= to t)))) universe)))

(defn- frontmatter-end
  "1-based line of the `---` closing the YAML frontmatter of `text`, 0 without one."
  [text]
  (let [ls (str/split-lines text)]
    (if (= "---" (first ls)) (+ 2 (count (take-while #(not= "---" %) (rest ls)))) 0)))

;; Differential on real data: v1's edge index for 40 documents of 23 models.
(deftest the-derived-graph-reproduces-the-index-v1-held
  (let [frozen (json/parse-string (slurp (str fixtures "edges.json")))
        documents (json/parse-string (slurp (str fixtures "documents.json")))
        all-descriptors (mapv edn/read-string (remove str/blank? (str/split-lines (slurp (str fixtures "models.edn")))))
        registered (registered-models (frozen "edges"))
        expected-diffs (edn/read-string (slurp (str fixtures "EXPECTED-DIFFS.edn")))
        store (rebuild! (filterv #(registered (:model/name %)) all-descriptors) documents)
        graph (:graph (edges/index store))
        v1 (v1-view (frozen "edges"))
        kimun (into {} (map (fn [e] [[(:type e) (:from e) (:to e)] (scope-of e)])) (:edges graph))
        v1-groups (by-group v1) kimun-groups (by-group kimun)
        missing (set/difference (set (keys v1)) (set (keys kimun)))
        extra (set/difference (set (keys kimun)) (set (keys v1)))
        differing (set/union missing extra)
        universe (set/union (set (keys v1)) (set (keys kimun)))
        listed (into #{} (mapcat #(covered-by % universe)) expected-diffs)
        v1-nodes (into {} (map (juxt #(% "id") identity)) (frozen "nodes"))
        v1-types (update-vals v1-nodes #(% "type"))]
    (testing "the fixture is what the brief asks for and every frozen relation is accounted for"
      (is (= 40 (count documents)))
      (is (= 23 (count (set (map #(get % "model") documents)))))
      (is (= 26 (count all-descriptors)))
      (is (= 24 (count registered)))
      (is (= 2497 (count (frozen "edges"))))
      (is (= v1-composed-counts (update-vals v1-groups count)))
      (is (= 2486 (count v1)))
      (is (= (set (map #(% "relation") (frozen "edges")))
             (set/union (set (map second (keys v1-groups))) #{"applies_to_source" "applies_to_target" "extends"}))
          "a relation of edges.json is neither in the composed view nor one v1's readers drop entirely")
      (testing "and the 11 dangling schema edges v1's readers drop are not derived by kimun either"
        (is (empty? (filter #(#{"applies_to_source" "applies_to_target" "extends"} (:type %)) (:edges graph))))))
    (testing "nothing missing, nothing extra, per scope and relation"
      (doseq [k (sort (set/union (set (keys v1-groups)) (set (keys kimun-groups))))
              :let [a (get v1-groups k #{}) b (get kimun-groups k #{})]]
        (is (empty? (remove listed (set/difference a b))) (str k ": in v1's index, not derived by kimun"))
        (is (empty? (remove listed (set/difference b a))) (str k ": derived by kimun, not in v1's index"))
        (is (= (get reproduced-counts k 0) (count (set/intersection a b))) (str k ": edges both sides hold"))))
    (testing "an edge is filed under the scope v1 filed it under"
      (is (= (select-keys v1 (keys kimun)) (select-keys kimun (keys v1)))))
    (testing "EXPECTED-DIFFS.edn: every entry gives its reason, none is stale, and they are all the differences there are"
      (is (= 194 (count expected-diffs)))
      (is (= [495 46] [(count missing) (count extra)]))
      (is (= differing listed))
      (doseq [e expected-diffs :let [c (covered-by e universe)]]
        (is (not (str/blank? (:reason e))) (pr-str e))
        (is (and (seq c) (every? differing c)) (str "listed but reproduced: " (pr-str (dissoc e :reason))))))
    (testing "a listed has_section is what its reason says: a v1 section starting inside the YAML frontmatter"
      (let [end (into {} (map (fn [d] [(str (d "model") ":" (d "name")) (frontmatter-end (d "text"))])) documents)]
        (doseq [e expected-diffs :when (= "has_section" (:relation e)) :let [sem (get-in v1-nodes [(:to e) "semantics"])]]
          (is (<= 1 (sem "line_start") (end (sem "document_id"))) (:to e)))))
    (testing "every endpoint is a node of the type v1 gave it"
      (let [endpoints (set (mapcat (fn [[_ from to]] [from to]) (remove listed (keys v1))))
            wrong (keep (fn [id] (let [got (get-in graph [:nodes id :type])] (when (not= (v1-types id) got) [id (v1-types id) got]))) (sort endpoints))]
        (is (= 690 (count v1-types)))
        (is (every? v1-types endpoints) "an endpoint of v1's composed view is no node of the frozen index")
        (is (empty? wrong) (str (count wrong) " endpoints differ in type, e.g. " (pr-str (take 5 wrong))))))
    (testing "the nodes are the same ones: store, models, fields, tags, documents and sections (but the listed frontmatter sections)"
      (let [comparable (fn [[id t]] (or (#{"sldb_section" "sldb_field" "sldb_model" "semantic_tag" "sldb_store"} t) (str/starts-with? id "sldb://document/")))
            touched (set (mapcat (juxt #(% "source") #(% "target")) (frozen "edges")))
            ;; the fixture froze the tag nodes of v1's whole store: 26 belong to documents outside the 40 and have no
            ;; frozen edge; nor has relation type `composes` (no applies_to_*), checked by name below
            untouched (remove (comp touched key) v1-types)
            a (set (filter comparable (filter (comp touched key) v1-types)))
            b (set (filter comparable (map (fn [[id n]] [id (:type n)]) (:nodes graph))))
            invented (set (keep #(when (= "has_section" (:relation %)) [(:to %) "sldb_section"]) expected-diffs))]
        (is (= {"semantic_tag" 26 "relation_type" 1} (frequencies (map val untouched))))
        (is (= 660 (count a)))
        (is (empty? (set/difference b a)) "nodes kimun has and v1 lacks")
        (is (= invented (set/difference a b)) "nodes v1 has and kimun lacks")))
    (testing "the four RelationTypeDoc documents are relation types, not documents, on both sides"
      (doseq [n ["extends" "composes" "applies_to_source" "references"] :let [id (str "sldb://relation_type/" n)]]
        (is (= "relation_type" (v1-types id) (get-in graph [:nodes id :type])) id))
      (is (empty? (filter #(str/starts-with? % "sldb://document/RelationTypeDoc:") (keys (:nodes graph))))))))
