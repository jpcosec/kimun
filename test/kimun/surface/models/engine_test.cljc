(ns kimun.surface.models.engine-test
  "Unit and property tests of the models engine: marker spellings, recipe
   invariants (v1 test_marker_invariants), section bodies (v1
   test_section_body_extraction), validation semantics (v1
   test_validation_semantics) and `extract(render(d)) == d`."
  (:require [clojure.string :as str]
            [clojure.test :refer [deftest is testing]]
            [clojure.test.check.generators :as gen]
            [clojure.test.check.properties :as prop]
            [kimun.surface.models.descriptor :as descriptor]
            [kimun.surface.models.extract :as extract]
            [kimun.surface.models.marker :as marker]
            [kimun.surface.models.recipe :as recipe]
            [kimun.surface.models.render :as render]
            [kimun.surface.models.validate :as validate]
            [sldb.kernel.test-util :refer [defspec]]))

(defn- err-type [f] (try (f) nil (catch clojure.lang.ExceptionInfo e (:type (ex-data e)))))

(def doc
  "A descriptor exercising frontmatter keys, a heading, an inline field, a
   section body, a list and an optional section."
  {:model/name "NoteDoc" :model/version 1 :base []
   :fields [{:name "id" :type :string :required true} {:name "tags" :type [:list :string] :required false :default []}
            {:name "title" :type :string :required true} {:name "status" :type :string :required true}
            {:name "body" :type :text :required true} {:name "items" :type [:list :string] :required false :default []}
            {:name "notes" :type :text :required false}]
   :template "---\nid: ⸢rev•id⸥\ntags: ⸢rev•tags⸥\n---\n\n# ⸢rev•title⸥\n\nStatus: ⸢rev•status⸥\n\n## Body\n\n⸢rev•body⸥\n\n## Items\n\n- ⸢rev,list•items⸥\n\n## Notes\n\n⸢optrev•notes⸥"})

(deftest both-marker-spellings-parse-alike
  (is (= {:kind "rev" :traits ["list"] :name "items"} (marker/parse "rev,list•items") (marker/parse "rev•items|list")))
  (is (= {:kind "optrev" :traits ["dict"] :name "meta"} (marker/parse "optrev,dict•meta")))
  (is (= ["a" "b"] (marker/table-columns (marker/parse "rev,table[a, b]•rows"))))
  (is (= [] (marker/table-columns (marker/parse "rev•rows|table"))))
  (is (nil? (marker/table-columns (marker/parse "rev•x")))))

(deftest recipe-invariants
  (testing "two rev markers for one field are rejected"
    (is (= :models/invalid (err-type #(recipe/recipes "# ⸢rev•title⸥\n\nAgain: ⸢rev•title⸥")))))
  (testing "a lone data marker in a block that cannot own a section body is rejected"
    (is (= :models/invalid (err-type #(recipe/recipes "> ⸢rev•quote⸥")))))
  (testing "a lone marker in a paragraph owns its section body; in a heading it stays inline"
    (let [[h p] (recipe/recipes "# ⸢rev•title⸥\n\n⸢rev•body⸥")]
      (is (nil? (:capture h)))
      (is (= :section-body (:capture p)))))
  (is (= #{"id" "tags" "title" "status" "body" "items" "notes"} (recipe/data-fields (:template doc)))))

(deftest section-body-keeps-line-breaks-and-blocks
  (let [md "---\nid: n1\ntags: []\n---\n\n# T\n\nStatus: open\n\n## Body\n\nline one\nline two\n\n```clj\n(+ 1 2)\n```\n\n## Items\n\n- a\n- b\n\n## Notes\n"
        p (extract/extract doc md)]
    (is (= "line one\nline two\n\n```clj\n(+ 1 2)\n```" (get p "body")))
    (is (= ["a" "b"] (get p "items")))
    (is (not (contains? p "notes")) "an empty optional section is absent, not empty")
    (is (= "n1" (get p "id")))))

(deftest missing-required-field-is-an-explicit-error
  (is (= :validate/missing (err-type #(extract/extract doc "# T\n\nStatus: open\n"))))
  (is (= :validate/missing (err-type #(render/render doc {"id" "x"})))))

(deftest descriptor-validation
  (is (= doc (descriptor/validate! doc)))
  (is (= :models/invalid (err-type #(descriptor/validate! (assoc doc :template "⸢py•1+1⸥")))))
  (is (= :models/invalid (err-type #(descriptor/validate! (dissoc doc :template))))))

(deftest validate-covers-optrev
  (let [data {"id" "n1" "title" "T" "status" "open" "body" "b" "items" ["x"] "notes" "n"}
        r (validate/validate doc {:data data})]
    (is (:ok r) (pr-str (:errors r)))
    (is (= "n" (get-in r [:extracted "notes"])))
    (is (:ok (validate/validate doc {:markdown (:rendered r)} :strict-roundtrip)))))

(def ^:private gen-word (gen/fmap #(apply str %) (gen/vector gen/char-alpha 1 8)))
(def ^:private gen-line (gen/fmap #(str/join " " %) (gen/vector gen-word 1 5)))
(def ^:private gen-payload
  (gen/let [id gen-word tags (gen/vector gen-word 0 3) title gen-line status gen-line
            body gen-line items (gen/vector gen-line 0 4) notes (gen/one-of [(gen/return nil) gen-line])]
    (cond-> {"id" id "tags" tags "title" title "status" status "body" body "items" items}
      notes (assoc "notes" notes))))

(defspec extract-of-render-is-identity 100
  (prop/for-all [d gen-payload]
    (= d (extract/extract doc (render/render doc d)))))
