(ns kimun.surface.fields-test
  "The pure `fields` surface: paths, coercion, mutations, schema, owning
   sections and the rows of a field query. Tests carrying a v1 promise name
   the v1 function in a comment."
  (:require [clojure.test :refer [deftest is testing]]
            [kimun.surface.fields :as fields]
            [kimun.surface.models.render :as render]))

(def roadmap
  {:model/name "RoadmapDoc" :model/version 1 :base []
   :fields [{:name "title" :type :string :required true :description "Document title."}
            {:name "status" :type :string :required true :description "Workflow status."}
            {:name "tasks" :type [:list :string] :required true :description "Task items."}
            {:name "semantic_tags" :type [:list :string] :required true :description "Semantic tags."}]
   :template "# ⸢rev•title⸥\nStatus: ⸢rev•status⸥\n## Tasks\n- ⸢rev,list•tasks⸥\n## Semantic Tags\n- ⸢rev,list•semantic_tags⸥"})

(def payload {"title" "Roadmap" "status" "draft" "tasks" ["Ship CLI" "Write docs"] "semantic_tags" ["project.sldb.database"]})

(def typed
  {:model/name "Typed"
   :fields [{:name "n" :type :int} {:name "done" :type :bool} {:name "name" :type :string}
            {:name "kind" :type [:enum "a" "b"]} {:name "nums" :type [:list :int]}
            {:name "meta" :type [:map :string]} {:name "free" :type :yaml}]})

(defn- kind [f] (try (f) :no-error (catch clojure.lang.ExceptionInfo e (:type (ex-data e)))))

(deftest paths-with-dots-and-list-indexes
  (let [p {"a" {"b" [{"name" "x"} {"name" "y"}]} "s" "v"}]
    (is (= ["items" "0" "name"] (fields/split-path "items.0.name")))
    (is (= ["a" "b"] (fields/split-path "a/b")) "v1's slash form")
    (is (= "y" (fields/value-at p "a.b.1.name")))
    (is (= {"name" "x"} (fields/value-at p "a.b.0")))
    (is (= "z" (fields/value-at (fields/assoc-at p "a.b.1.name" "z" false) "a.b.1.name")))
    (is (= {"c" {"d" 1}} (fields/assoc-at {} "c.d" 1 true)) "create builds the intermediate maps")
    (is (= [{"name" "y"}] (fields/value-at (fields/dissoc-at p "a.b.0") "a.b")) "a list element is dropped")
    (is (= p (fields/dissoc-at p "a.zzz")) "an absent last key is ignored, as v1's deep_delete")
    (is (true? (fields/present? p "a.b.0.name")))
    (is (false? (fields/present? p "a.b.7")))
    (testing "missing field"
      (is (= :fields/missing (kind #(fields/value-at p "nope"))))
      (is (= :fields/missing (kind #(fields/value-at p "s.deeper"))) "stepping into a scalar")
      (is (= :fields/missing (kind #(fields/value-at p "a.b.2"))) "index out of range (v1 leaked IndexError)")
      (is (= :fields/missing (kind #(fields/assoc-at p "nope.x" 1 false))))
      (is (= :fields/missing (kind #(fields/dissoc-at p "nope.x")))))
    (is (= :fields/bad-path (kind #(fields/value-at p "a.b.first"))) "a list needs a numeric index")
    (is (= :fields/bad-path (kind #(fields/split-path ""))))))

(deftest coercion-per-declared-type
  (let [co (fn [path raw value] (fields/coerce (fields/type-at typed path) path raw value))]
    (is (= :int (fields/type-at typed "nums.0")))
    (is (= :string (fields/type-at typed "meta.k")))
    (is (nil? (fields/type-at typed "undeclared")))
    (is (= "2024" (co "name" "2024" 2024)) "a scalar given for a string field is its text")
    (is (= "1.50" (co "name" "1.50" 1.5)) "…the text as typed, not a re-print")
    (is (= "true" (co "name" "true" true)))
    (is (= "# note" (co "name" "# note" nil)) "YAML's comment is the string")
    (is (= 5 (co "n" "5" 5)))
    (is (= 5 (co "n" "\"5\"" "5")))
    (is (= true (co "done" "true" true)))
    (is (= false (co "done" "\"False\"" "False")))
    (is (= "a" (co "kind" "a" "a")))
    (is (= [1 2] (co "nums" "[1, \"2\"]" [1 "2"])) "elements take the element type")
    (is (= {"k" "1"} (co "meta" "{k: 1}" {"k" 1})))
    (is (= {"x" [1]} (co "free" "{x: [1]}" {"x" [1]})) "yaml and undeclared paths take the value as parsed")
    (is (= 7 (co "undeclared" "7" 7)))
    (testing "wrong type"
      (is (= :fields/wrong-type (kind #(co "n" "abc" "abc"))))
      (is (= :fields/wrong-type (kind #(co "done" "1" 1))))
      (is (= :fields/wrong-type (kind #(co "kind" "c" "c"))))
      (is (= :fields/wrong-type (kind #(co "nums" "5" 5))))
      (is (= :fields/wrong-type (kind #(co "nums" "[a]" ["a"]))))
      (is (= :fields/wrong-type (kind #(co "name" "[a]" ["a"]))))
      (is (= :fields/wrong-type (kind #(co "meta" "x" "x")))))))

(deftest mutations-return-before-and-after
  ;; v1: test_fields_update_append_and_clean (the payload half; the CLI half is in kimun.cli.fields-test)
  (let [m (fn [op path opts] (fields/mutate roadmap payload op path opts))
        appended (m :append "tasks" {:raw "\"Ship CLI\"" :value "Ship CLI"})]
    (is (= ["Ship CLI" "Write docs" "Ship CLI"] (:after appended)))
    (is (= ["Ship CLI" "Write docs"] (:before appended)))
    (is (= {:operation "fields append" :field "tasks" :path "tasks" :existed? true :unchanged? false}
           (select-keys appended [:operation :field :path :existed? :unchanged?])))
    (is (= ["Ship CLI" "Write docs"]
           (:after (fields/mutate roadmap (:payload appended) :clean "tasks" {:dedupe true}))))
    (is (= ["a" "b" "a"] (:after (fields/mutate roadmap (assoc payload "tasks" ["a" "" "b" nil "a"]) :clean "tasks" {:drop-empty true}))))
    (is (true? (:unchanged? (m :clean "tasks" {}))) "clean without a flag changes nothing, as v1")
    (is (= "accepted" (:after (m :set "status" {:raw "\"accepted\"" :value "accepted" :create? false}))))
    (is (= "Write docs!" (get-in (m :set "tasks.1" {:raw "Write docs!" :value "Write docs!"}) [:payload "tasks" 1])))
    (is (= "tasks" (:field (m :set "tasks.1" {:raw "x" :value "x"}))) "the field is the top-level one")
    (testing "set creates, v1 update does not"
      (is (= {:before nil :after "x" :existed? false} (select-keys (m :set "extra" {:raw "x" :value "x"}) [:before :after :existed?])))
      (is (= :fields/missing (kind #(m :set "extra" {:raw "x" :value "x" :create? false})))))
    (testing "unset"
      (is (= {:before "draft" :after nil} (select-keys (m :unset "status" {}) [:before :after])))
      (is (not (contains? (:payload (m :unset "status" {})) "status")))
      (is (= ["Write docs"] (get (:payload (m :unset "tasks.0" {})) "tasks")))
      (is (true? (:unchanged? (m :unset "nope" {})))))
    (testing "clear empties a list"
      (is (= [] (:after (m :clear "tasks" {}))))
      (is (= :fields/not-a-list (kind #(m :clear "status" {})))))
    (testing "append on a non-list, a missing field, a wrong element"
      (is (= :fields/not-a-list (kind #(m :append "status" {:raw "x" :value "x"}))))
      (is (= :fields/missing (kind #(m :append "nope" {:raw "x" :value "x"}))))
      (is (= :fields/wrong-type (kind #(m :append "tasks" {:raw "[a]" :value ["a"]})))))
    (is (= :fields/wrong-type (kind #(m :set "tasks" {:raw "5" :value 5}))))
    (is (= :fields/bad-operation (kind #(m :explode "tasks" {}))))))

(deftest render-checked-validates-against-the-model
  ;; v1: test_fields_update_can_clear_list_field (an empty list is a valid document and reads back empty)
  (let [cleared (:payload (fields/mutate roadmap payload :clear "tasks" {}))
        r (fields/render-checked roadmap cleared nil "tasks")]
    (is (= [] (get (:payload r) "tasks")))
    (is (= (str (render/render roadmap cleared) "\n") (:text r))))
  (is (= :validate/missing (kind #(fields/render-checked roadmap (dissoc payload "title") nil "title"))))
  (is (= :validate/invalid (kind #(fields/render-checked roadmap (assoc payload "title" 5) nil "title"))))
  (is (= :fields/not-roundtrip (kind #(fields/render-checked roadmap (assoc payload "tasks" ["one\n- two"]) nil "tasks")))
      "a value that reads back as something else is refused"))

(deftest schema-of-a-model
  (is (= {:model "RoadmapDoc"
          :fields [{:name "title" :description "Document title." :type "string" :required true}
                   {:name "status" :description "Workflow status." :type "string" :required true}
                   {:name "tasks" :description "Task items." :type "list[string]" :required true}
                   {:name "semantic_tags" :description "Semantic tags." :type "list[string]" :required true}]}
         (fields/schema roadmap)))
  (is (= {:model "RoadmapDoc" :field "tasks" :name "tasks" :description "Task items." :type "list[string]" :required true}
         (fields/schema roadmap "tasks")))
  (is (= "enum[a, b]" (:type (fields/schema typed "kind"))))
  (is (= :fields/missing (kind #(fields/schema roadmap "nope")))))

(deftest field-rows-carry-the-owning-section
  ;; v1: test_field_query_includes_owning_section, test_find_field_shows_owning_section (the
  ;; field → owning-section part: `rows` is what a `find --type field` hit is built from)
  (let [source (str (render/render roadmap payload) "\n")
        doc {:name "roadmap" :model "RoadmapDoc" :path "roadmap.md" :payload payload :source source}
        owners (fields/owning-sections roadmap source)]
    (is (= [{:model "RoadmapDoc" :doc "roadmap" :path "roadmap.md" :field "title" :value "Roadmap" :owning_section "roadmap"}]
           (fields/rows roadmap doc "title")))
    (is (= "roadmap/tasks" (:owning_section (first (fields/rows roadmap doc "tasks")))))
    (is (= [] (fields/rows roadmap doc "nope")))
    (is (= "roadmap/semantic-tags" (fields/owning-section owners "semantic_tags")))
    (is (= "roadmap/tasks" (fields/owning-section owners "tasks.0")) "a nested path belongs to its field's section")
    (is (nil? (fields/owning-section owners "nope")))
    (is (= {} (fields/owning-sections (dissoc roadmap :template) source)))
    (testing "a suffix matches nested leaves"
      (is (= ["meta.title" "title"]
             (sort (map :field (fields/rows roadmap (assoc doc :payload (assoc payload "meta" {"title" "inner"})) "title"))))))))
