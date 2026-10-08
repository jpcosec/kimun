(ns kimun.surface.index.address-test
  "Addresses over hand-built federated views (the index contract of 06 §6).
   Each deftest ported from v1 names its v1 function; the rule applied to v1's
   `invalid` tests is: a string outside the address space resolves to `[]`,
   a malformed address raises `:query/bad-address`."
  (:require [clojure.test :refer [deftest is testing]]
            [kimun.surface.index.address :as address]
            [kimun.surface.index.where :as where]
            [kimun.surface.semantic :as sem]
            [kimun.kernel.err :as err]))

(defn- index-of
  "A contract-shaped index from `models` (`{name {:base [lineage] :fields {…}}}`),
   doc literals (`{:name :model :payload :tags}`; tree id `t-<name>`) and the
   declared tag relations (`{:parents … :equivalences …}`)."
  ([models docs] (index-of models docs nil))
  ([models docs declared]
   (let [docs (for [d docs] (assoc d :tree (str "t-" (:name d)) :bases (vec (get-in models [(:model d) :base]))
                                   :tags (vec (sort (:tags d))) :path (str (:name d) ".md")))]
     {:head "h" :models (into {} (map (fn [[n m]] [n (assoc m :name n)])) models)
      :docs (into {} (map (juxt :tree identity)) docs)
      :by-model (reduce (fn [m d] (update m (:model d) (fnil conj []) (:tree d))) {} (sort-by :tree docs))
      :by-name (into {} (map (juxt :name :tree)) docs)
      :tags (reduce (fn [m d] (reduce #(update %1 %2 (fnil conj []) (:tree d)) m (:tags d))) {} (sort-by :tree docs))
      :tag-dag (sem/dag-of (mapcat :tags docs) declared)
      :equivalences (into {} (map (fn [[k v]] [k (vec (sort v))])) (:equivalences declared))
      :sections {} :links {} :anchors {}})))

(def ^:private memo-models
  {"MemoDoc" {:base ["BaseNote"] :fields [{:name "title" :type :string :description "Title."} {:name "status" :type :string :description "Workflow status."}]}})

(defn- memo-view
  "v1 `_setup`: two MemoDoc documents (memo1 open, memo2 `status2`) in the current store."
  [status2]
  [{:store "repo"
    :index (index-of memo-models
                     [{:name "memo1" :model "MemoDoc" :payload {"title" "Memo1" "status" "open"} :tags ["type.note.memo"]}
                      {:name "memo2" :model "MemoDoc" :payload {"title" "Memo2" "status" status2} :tags ["type.note.memo"]}])}])

(defn- addresses [results] (mapv :address results))

(defn- find-where
  "v1 `legacy find ADDRESS --where EXPR`: the addresses of the documents `a` denotes that match."
  [a expr view]
  (let [ast (where/parse expr) docs (into {} (map (fn [e] [(:store e) (get-in e [:index :docs])])) view)]
    (addresses (filter #(where/matches? ast (get-in docs [(:store %) (:tree %)])) (address/resolve-address a view)))))

(defn- bad
  "The ex-data of the `:query/bad-address` error parsing `s` raises, or nil."
  [s]
  (err/rescue ex-data (address/parse s) nil))

;; ---- tests/test_address_surface.py ----

(deftest get-reads-a-field-by-address
  ;; v1 test_legacy_get_reads_a_field_by_address
  (let [[r :as rs] (address/resolve-address "st.{MemoDoc}.memo1.status" (memo-view "done"))]
    (is (= 1 (count rs)))
    (is (= {:store "repo" :tree "t-memo1" :name "memo1" :model "MemoDoc" :path "memo1.md"
            :kind :field :field "status" :value "open" :address "st.{MemoDoc}.memo1.status"} r))))

(deftest ls-walks-store-model-doc
  ;; v1 test_legacy_ls_walks_store_model_doc
  (let [view (memo-view "done")]
    (is (= ["st.{MemoDoc}"] (addresses (address/list-address "st" view))))
    (is (= [:model] (map :kind (address/list-address "st" view))))
    (is (= ["memo1" "memo2"] (map :name (address/list-address "st.{MemoDoc}" view))))
    (is (= [["status" "Workflow status." "open"] ["title" "Title." "Memo1"]]
           (map (juxt :field :description :value) (address/list-address "st.{MemoDoc}.memo1" view))))))

(deftest family-scope-works-for-an-unregistered-base
  ;; v1 test_family_scope_works_for_an_unregistered_base: BaseNote is in no `:models`
  (let [view (memo-view "done")]
    (is (= ["st.{BaseNote+}.memo1"] (find-where "st.{BaseNote+}" "status = \"open\"" view)))
    (is (= [] (address/list-address "st.{BaseNote}" view)))))

(deftest get-and-glob-by-address
  ;; v1 test_singular_alias_routes_to_the_same_handler (the alias is retired by 06 §8; the answers stay)
  (let [view (memo-view "done")]
    (is (= ["Memo2"] (map :value (address/resolve-address "st.{MemoDoc}.memo2.title" view))))
    (is (= ["st.{MemoDoc}.memo1.status" "st.{MemoDoc}.memo2.status"]
           (addresses (address/resolve-address "st.{MemoDoc}.memo*.status" view))))
    (is (= ["st.{MemoDoc}.memo1" "st.{MemoDoc}.memo2"] (addresses (address/resolve-address "st.{MemoDoc}.memo?" view))))
    (is (= ["status" "title"] (map :field (address/resolve-address "st.{MemoDoc}.memo1.*t*" view))))))

(deftest field-update-is-visible-at-the-address
  ;; v1 test_field_update_is_visible_at_the_address, the address half: the answer is a
  ;; function of the view, so the view of the store after the write shows the new value
  (is (= ["st.{BaseNote+}.memo1"] (find-where "st.{BaseNote+}" "status = \"open\"" (memo-view "done"))))
  (is (= ["st.{BaseNote+}.memo1" "st.{BaseNote+}.memo2"] (find-where "st.{BaseNote+}" "status = \"open\"" (memo-view "open")))))

;; ---- tests/store/test_queries_expanded.py ----

(deftest structural-list-root
  ;; v1 test_structural_list_root
  (is (vector? (address/list-address "st" (memo-view "done"))))
  (is (= [] (address/list-address "st" [{:store "repo" :index (index-of {} [])}]))))

(deftest invalid-address-is-empty
  (let [view (memo-view "done")]
    ;; v1 test_structural_list_invalid_address
    (is (= [] (address/list-address "invalid" view)))
    ;; v1 test_get_structural_invalid_address, test_glob_structural_invalid_pattern
    (is (= [] (address/resolve-address "invalid" view)))
    ;; v1 test_find_structural_invalid_address
    (is (= [] (find-where "invalid" "title = \"x\"" view)))
    (is (false? (address/address? "invalid")))
    (testing "well-formed but naming nothing: empty, never an error"
      (doseq [a ["st.{Nope}" "st.{MemoDoc}.nope" "st.{MemoDoc}.memo1.nope" "st.{MemoDoc}.memo1.title.deeper"
                 "st.elsewhere" "elsewhere:st.{MemoDoc}" "se.no.such.tag" "gse.no.such.tag" "gse.x.se.{elsewhere}"]]
        (is (= [] (address/resolve-address a view)) a)
        (is (= [] (address/list-address a view)) a)))
    (testing "claims the address space and breaks its grammar: typed error with position"
      (is (= {:type :query/bad-address :address "st.{Memo" :position 3} (err/rescue ex-data (address/resolve-address "st.{Memo" view) nil))))))

(deftest model-inheritance-query
  ;; v1 test_model_inheritance_query
  (let [view [{:store "local"
               :index (index-of {"BaseDoc" {:base []} "ChildDoc" {:base ["BaseDoc"]}}
                                [{:name "doc1" :model "BaseDoc" :payload {"title" "Base"} :tags ["type.base"]}
                                 {:name "doc2" :model "ChildDoc" :payload {"title" "Child" "child_field" "x"} :tags ["type.child"]}])}]]
    (is (= ["doc1" "doc2"] (map :name (address/list-address "st.{BaseDoc+}" view))))
    (is (= ["doc1"] (map :name (address/list-address "st.{BaseDoc}" view))))
    (is (= ["doc1" "doc2"] (map :name (address/resolve-address "st.{*}" view))))))

(deftest global-semantic-empty
  ;; v1 test_get_global_semantic_empty, the `gse.` half
  (is (= [] (address/resolve-address "gse.type" [{:store "repo" :index (index-of {} [])}])))
  (is (= [] (address/resolve-address "gse.type" []))))

;; ---- the grammar ----

(deftest parse-forms
  (is (= {:space :st :store nil} (address/parse "st")))
  (is (= {:space :st :store "B"} (address/parse "st.B") (address/parse "B:st")))
  (is (= {:space :st :store nil :model "Memo" :family? false} (address/parse "st.{Memo}") (address/parse "local:st.{Memo}")
         (address/parse "st.local.Memo")))
  (is (= {:space :st :store "B" :model "Base" :family? true} (address/parse "st.B.Base+") (address/parse "B:st.{Base+}")
         (address/parse "st.B.{Base+}")))
  (is (= {:space :st :store nil :model "*" :family? false :doc "memo*"} (address/parse "st.{*}.memo*")))
  (is (= {:space :st :store "B" :model "Memo" :family? false :doc "memo1" :field "tasks.0.title"}
         (address/parse "st.B.Memo.memo1.tasks.0.title")))
  (is (= {:space :se :tag nil} (address/parse "se")))
  (is (= {:space :se :tag "type.*"} (address/parse "se.type.*")))
  (is (= {:space :gse :tag "topic.x"} (address/parse "gse.topic.x")))
  (is (= {:space :gse :tag "topic.x" :bridge "B"} (address/parse "gse.topic.x.se.{B}"))))

(deftest parse-errors-carry-the-position
  (doseq [[s pos] {"invalid" 0 "status" 0 "st x" 0 "st." 3 "st.{Memo" 3 "st.{}" 4 "st.{+}" 4 "st.{Memo}x" 9 "st.{Memo}." 10
                   "st.{Memo}..f" 10 "st.{Memo}.d." 12 "st.{Memo}.d.a..b" 14 "st.B." 5 "B:st.C.M" 5 "st.B.M+x" 5
                   "se." 3 "se..x" 3 "se.a..b" 5 "se.a." 5 "gse" 3 "gse." 4 "se.{x}" 3}]
    (is (= {:type :query/bad-address :address s :position pos} (bad s)) s)))

(deftest address-routing
  (doseq [s ["st" "se" "gse" "st.{M}" "st.B.M" "B:st.{M}" "se.type" "gse.type" "st.{broken"]]
    (is (address/address? s) s))
  (doseq [s ["status" "search" "gsex" "" "se habla" "st.{M} and more" "B:se.x" nil]]
    (is (not (address/address? s)) (str s))))

;; ---- stores and the semantic spaces ----

(def ^:private federated
  "Current store `here` and the linked `there`, whose `area.billing` means the global `topic.money`."
  [{:store "here"
    :index (index-of {"Note" {:base []}}
                     [{:name "n1" :model "Note" :payload {"title" "One" "tasks" [{"title" "first"}]} :tags ["topic.money.tax" "kind.note"]}
                      {:name "n2" :model "Note" :payload {"title" "Two"} :tags ["topic.art"]}])}
   {:store "there"
    :index (index-of {"Card" {:base []}}
                     [{:name "c1" :model "Card" :payload {"title" "Bill"} :tags ["area.billing.invoice"]}
                      {:name "c2" :model "Card" :payload {"title" "Tax"} :tags ["topic.money"]}]
                     {:equivalences {"area.billing" #{"topic.money"}}})}])

(deftest structural-addresses-name-a-store
  (is (= ["st.{Note}"] (addresses (address/resolve-address "st" federated))))
  (is (= ["there:st.{Card}"] (addresses (address/resolve-address "st.there" federated))
         (addresses (address/resolve-address "there:st" federated))))
  (is (= [["there" "t-c1" "there:st.{Card}.c1"] ["there" "t-c2" "there:st.{Card}.c2"]]
         (map (juxt :store :tree :address) (address/resolve-address "st.there.Card" federated))))
  (is (= ["Bill"] (map :value (address/resolve-address "there:st.{Card}.c1.title" federated))))
  (is (= ["first"] (map :value (address/resolve-address "st.here.Note.n1.tasks.0.title" federated))))
  (is (= [] (address/resolve-address "st.{Card}" federated)) "an unqualified address stays in the current store")
  (is (= {"title" "One" "tasks" [{"title" "first"}]} (:payload (first (address/resolve-address "st.{Note}.n1" federated))))))

(deftest semantic-addresses-stay-in-the-current-store
  (is (= ["kind" "topic"] (map :tag (address/resolve-address "se" federated))))
  (is (= ["st.{Note}.n1" "st.{Note}.n2"] (addresses (address/resolve-address "se.topic" federated))) "a parent names its subtree")
  (is (= ["st.{Note}.n1"] (addresses (address/resolve-address "se.topic.money" federated))))
  (is (= ["st.{Note}.n2"] (addresses (address/resolve-address "se.topic.a*" federated))))
  (is (= [["art" "se.topic.art" :tag] ["money" "se.topic.money" :tag]]
         (map (juxt :name :address :kind) (address/list-address "se.topic" federated))))
  (is (= ["n2"] (map :name (address/list-address "se.topic.art" federated))) "a leaf lists the documents carrying it")
  (is (= ["se.topic.art" "se.topic.money"] (addresses (address/list-address "se.topic.*" federated)))))

(deftest global-addresses-span-the-view
  (testing "the current store answers by what its documents reach; a linked one only by its equivalences"
    (is (= [["here" "t-n1" "here:st.{Note}.n1"] ["there" "t-c1" "there:st.{Card}.c1"]]
           (map (juxt :store :tree :address) (address/resolve-address "gse.topic.money" federated)))))
  (is (= ["here:st.{Note}.n1" "here:st.{Note}.n2"] (addresses (address/resolve-address "gse.topic" federated))))
  (is (= [] (address/resolve-address "gse.area.billing.invoice" federated)) "a linked store's own tags are not global")
  (is (= [["there" "se.area.billing" :tag]] (map (juxt :store :address :kind) (address/resolve-address "gse.topic.money.se.{there}" federated))))
  (is (= (address/resolve-address "gse.topic.money" federated) (address/resolve-address (address/parse "gse.topic.money") federated))))
