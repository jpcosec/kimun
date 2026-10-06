(ns kimun.surface.index.search-test
  "The pure `find` engine over hand-written indexes (the index contract of
   `kimun.surface.index.build`): record kinds, layers, flags, `--where` per
   kind, addresses and ordering. The v1 tests are ported end to end, over
   real stores, in `kimun.cli.find-test`."
  (:require [clojure.test :refer [deftest is testing]]
            [kimun.surface.index.search :as search]
            [kimun.surface.index.where :as where]))

(defn- doc [tree n model bases tags payload source]
  {:tree tree :name n :model model :bases bases :tags tags :payload payload :path (str n ".md") :source source :state :intact})

(defn- index
  "A minimal index with the docs `ds` of model Memo (base Base), parent edge type.note.memo → type.note and one equivalence."
  [ds]
  {:head "h"
   :models {"Base" {:name "Base" :base [] :tags ["type.note"] :template "# ⸢rev•title⸥\n"}
            "Memo" {:name "Memo" :base ["Base"] :tags ["type.note.memo"] :template "# ⸢rev•title⸥\n\n## Tasks\n\n⸢rev•body⸥\n"}}
   :docs (into {} (map (juxt :tree identity)) ds)
   :by-model {"Memo" (mapv :tree ds)} :by-name (into {} (map (juxt :name :tree)) ds)
   :tags {"type.note.memo" (mapv :tree ds)}
   :tag-dag {:nodes {"type" #{} "type.note" #{"type"} "type.note.memo" #{"type.note"} "topic" #{} "topic.memos" #{"topic"}}
             :equivalences {}}
   :equivalences {"type.note.memo" ["topic.memos"] "topic.memos" ["type.note.memo"]}
   :sections {} :links {} :anchors {}})

(defn- memo [tree n body] (doc tree n "Memo" ["Base"] ["type.note.memo"] {"title" n "body" body} (str "# " n "\n\n## Tasks\n\n" body "\n")))

(def view "Store b is listed first on purpose (it is the current one): results must come out by store name."
  [{:store "b" :index (index [(memo "02" "gamma" "ship the cache") (memo "01" "beta" "plan the roadmap")])}
   {:store "a" :index (index [(memo "03" "alpha" "roadmap of alpha")])}])

(defn- ids [rs] (mapv (juxt :store :kind :name) rs))

(deftest records-cover-every-kind
  (let [rs (search/records view)]
    (is (= {:store 2 :model 4 :doc 3 :section 6 :field 6} (frequencies (map :kind rs))))
    (is (every? #(and (contains? % :physical) (contains? % :semantic)) rs))))

(deftest layers-and-ordering
  (testing "physical: names, paths, section titles, field values and document text; current store only without :global?"
    (is (= [["b" :doc "beta"] ["b" :field "body"]] (ids (search/search view "roadmap" {:in :physical}))))
    (is (= [["a" :doc "alpha"] ["a" :field "body"] ["b" :doc "beta"] ["b" :field "body"]]
           (ids (search/search view "roadmap" {:in :physical :global? true})))))
  (testing "ordered by store name, then tree id, whatever the view or map order"
    (is (= [["a" "03"] ["b" "01"] ["b" "02"]] (mapv (juxt :store :tree) (search/search view "" {:kinds #{:doc} :global? true})))))
  (testing "semantic: the tags, what they hang from in the DAG, and their equivalents"
    (is (= ["beta" "gamma"] (mapv :name (search/search view "type.note" {:in :semantic :kinds #{:doc}}))))
    (is (= ["beta" "gamma"] (mapv :name (search/search view "topic.memos" {:in :semantic :kinds #{:doc}}))))
    (is (= [] (search/search view "topic.memos" {:in :physical})))
    (is (= [] (search/search view "roadmap" {:in :semantic}))))
  (testing "a result says where it matched"
    (is (= [{:in :physical :value "beta"} {:in :physical :value "beta.md"} {:in :physical :value "Memo/beta"}]
           (take 3 (:matches (first (search/search view "beta" {:kinds #{:doc}}))))))))

(deftest term-forms
  (is (= 2 (count (search/search view "models" {}))) "v1: a plural kind lists that kind")
  (is (= 13 (count (search/search view "" {}))) "an empty term matches every record of the current store: 1 store, 2 models, 2 docs, 4 sections, 4 fields")
  (is (= ["beta"] (mapv :name (search/search view "^be.a$" {:regex? true :kinds #{:doc}}))))
  (is (= :query/bad-regex (try (search/search view "(" {:regex? true}) (catch #?(:clj Exception :cljs :default) e (:type (ex-data e))))))
  (is (= ["beta"] (mapv :name (search/search view "ROADMAPS" {:fuzzy? true :kinds #{:doc}}))))
  (is (= [] (search/search view "ROADMAPS" {:kinds #{:doc}}))))

(deftest where-by-kind
  (let [q (fn [term kind w] (search/search view term {:kinds #{kind} :where (where/parse w) :global? true}))]
    (is (= ["gamma"] (mapv :name (q "" :doc "body ~ \"cache\""))))
    (is (= [["b" "beta" "title" "beta"]] (mapv (juxt :store :doc :field :owning-section) (q "title" :field "value = \"beta\""))))
    (is (= ["alpha/tasks"] (mapv :owning-section (q "body" :field "doc = \"alpha\""))))
    (is (= [["Tasks" ["beta" "Tasks"]] ["Tasks" ["gamma" "Tasks"]] ["Tasks" ["alpha" "Tasks"]]]
           (mapv (juxt :title :breadcrumbs) (sort-by :store #(compare %2 %1) (q "" :section "\"Tasks\" in about")))))
    (is (= 4 (count (q "" :model "has(nothing)"))) "store and model records are not filtered (v1): 2 models x 2 stores")))

(deftest addresses
  (is (search/address-term? "st.{Memo}"))
  (is (search/address-term? "a:st"))
  (is (not (search/address-term? "st")) "the bare words stay plain text")
  (is (not (search/address-term? "status")))
  (testing "an address names its own store: no :global? needed, and results keep the resolver's keys"
    (let [rs (search/search view "a:st.{Base+}" {})]
      (is (= [["a" :doc "alpha" "a:st.{Base+}.alpha"]] (mapv (juxt :store :kind :name :address) rs)))
      (is (= [{:in :address :value "a:st.{Base+}.alpha"}] (:matches (first rs))))))
  (is (= ["beta"] (mapv :name (search/search view "st.{Memo}" {:where (where/parse "body ~ \"roadmap\"")}))))
  (is (= [[:field "body" "plan the roadmap" "beta/tasks"]]
         (mapv (juxt :kind :name :value :owning-section) (search/search view "st.{Memo}.beta.body" {}))))
  (is (= [] (search/search view "st.{Memo}" {:kinds #{:field}})))
  (is (= :query/bad-address (try (search/search view "st.{Memo" {}) (catch #?(:clj Exception :cljs :default) e (:type (ex-data e)))))))
