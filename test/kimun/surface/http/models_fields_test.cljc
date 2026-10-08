(ns kimun.surface.http.models-fields-test
  "Ports of sldb v1's `/models*`, `/lint`, `/fields`, `/extract`, `/render`,
   `/sections` and `/ast` serve tests (tests/test_serve.py) against a real
   server. Bodies are the CLI envelope (payload under `data`); the status and
   the information of each asserted case match v1, under kimun key names."
  (:require [clojure.test :refer [deftest is testing]]
            [clojure.string :as str]
            [kimun.surface.http.support :refer [req run with-server]]))

(def ^:private simple-book
  "Model `SimpleBook`: a title only (v1's `SimpleBook`/`Gadget`)."
  (pr-str {:model/name "SimpleBook" :base []
           :fields [{:name "title" :type :string :required true :description "Title."}]
           :template "# ⸢rev•title⸥"}))

(def ^:private book-with-setup
  "Child model declaring the `## Setup` section as `optrev` (v1's `BookWithSetup`)."
  (pr-str {:model/name "BookWithSetup" :base ["SimpleBook"]
           :fields [{:name "title" :type :string :required true :description "Title."}
                    {:name "setup" :type :string :required false :description "Setup."}]
           :template "# ⸢rev•title⸥\n\n## Setup\n\n⸢optrev•setup⸥"}))

(defn- write!
  "Writes `text` to `name` inside `proj`; returns the file name."
  [proj name text]
  (spit (str proj "/" name) text)
  name)

(defn- models-setup
  "Defines `SimpleBook` only."
  [proj]
  (run proj "models" "define" "-f" (write! proj "simple.edn" simple-book)))

(defn- chapter-setup
  "v1's `_build_chapter_store`: `book` (SimpleBook) and `guide` (BookWithSetup)."
  [proj]
  (models-setup proj)
  (run proj "models" "define" "-f" (write! proj "setup.edn" book-with-setup))
  (write! proj "book.md" "# My Book")
  (write! proj "guide.md" "# My Book\n\n## Setup\n\nSet it up.\n")
  (run proj "docs" "track" "SimpleBook" "book.md" "--name" "book")
  (run proj "docs" "track" "BookWithSetup" "guide.md" "--name" "guide"))

(defn- get-json
  "GET `path` on `base`; returns `[status envelope]`."
  [base path]
  (let [r (req :get (str base path))] [(:status r) (:json r)]))

(defn- post-json
  "POST `body` to `path` on `base`; returns `[status envelope]`."
  [base path body]
  (let [r (req :post (str base path) body)] [(:status r) (:json r)]))

(deftest models-endpoints-use-native-api
  ;; v1: test_models_endpoints_use_native_api
  ;; v2 differences: no `model_ref` (Python class ref); drafts (.py.temp) are retired, so
  ;; fields-add writes a new version immediately and /models/promote has no counterpart
  ;; (the equivalent is POST /models/define {edn}); unknown model is 404 (v1: 200 ok false).
  (with-server models-setup
    (fn [base proj]
      (let [[st cat] (get-json base "/models")
            gadget (first (filter #(= "SimpleBook" (:name %)) (:data cat)))]
        (is (= 200 st))
        (is (= [1 0] ((juxt :version :documents) gadget))))
      (let [[st d] (get-json base "/models/detail?model=SimpleBook")]
        (is (= 200 st))
        (is (true? (:ok d)))
        (is (= #{"title"} (set (map :name (get-in d [:data :descriptor :fields])))))
        (is (= 1 (get-in d [:data :version]))))
      (testing "unknown model: 404 (deliberate difference: v1 answered 200 with ok false)"
        (let [[st d] (get-json base "/models/detail?model=Nope")]
          (is (= 404 st))
          (is (false? (:ok d)))
          (is (str/includes? (get-in d [:error :message]) "Nope"))))
      (testing "missing parameter: 400"
        (let [[st d] (get-json base "/models/detail")]
          (is (= 400 st))
          (is (false? (:ok d)))))
      (testing "fields-add writes the next version at once (no draft)"
        (let [[st a] (post-json base "/models/fields-add"
                                {:model "SimpleBook" :field_name "price" :field_type "int" :description "Precio." :default "5"})]
          (is (= 200 st))
          (is (true? (:ok a)))
          (is (= 2 (get-in a [:data :version])))
          (is (= #{"title" "price"}
                 (set (map :name (get-in (run proj "models" "show" "SimpleBook") [:data :descriptor :fields])))))))
      (testing "validate = models check"
        (let [[st v] (post-json base "/models/validate" {:model "SimpleBook"})]
          (is (= 200 st))
          (is (true? (:ok v)))
          (is (true? (get-in v [:data :ok])))
          (is (= [{:name "SimpleBook" :ok true :errors []}] (get-in v [:data :models])))))
      (testing "promote equivalent: define with an edn body installs the next version"
        (let [edn (pr-str {:model/name "SimpleBook" :base []
                           :fields [{:name "title" :type :string :required true :description "Title."}
                                    {:name "price" :type :int :required false :default 5 :description "Precio."}
                                    {:name "note" :type :string :required false :default "" :description "Nota."}]
                           :template "# ⸢rev•title⸥"})
              [st p] (post-json base "/models/define" {:edn edn})]
          (is (= 200 st))
          (is (= 3 (get-in p [:data :version])))
          (is (false? (get-in p [:data :unchanged?])))
          (testing "defining the same contract again is unchanged, not an error"
            (let [[st2 again] (post-json base "/models/define" {:edn edn})]
              (is (= 200 st2))
              (is (true? (get-in again [:data :unchanged?])))))))
      (testing "fields-remove without field: 400"
        (let [[st d] (post-json base "/models/fields-remove" {:model "SimpleBook"})]
          (is (= 400 st))
          (is (false? (:ok d)))))
      (testing "fields-remove with field"
        (let [[st d] (post-json base "/models/fields-remove" {:model "SimpleBook" :field_name "note"})]
          (is (= 200 st))
          (is (true? (:ok d)))
          (is (= 4 (get-in d [:data :version])))))
      (testing "template-edit"
        (let [[st d] (post-json base "/models/template-edit" {:model "SimpleBook" :content "# ⸢rev•title⸥\n"})]
          (is (= 200 st))
          (is (true? (:ok d))))))))

;; n/a: test_models_endpoints_resolve_relative_imports_by_package_name — it exercises Python
;; package resolution (`from .sibling import ...` in kb_models_rel, loaded by module name) and
;; the v1-only /schema and /graph routes; v2 models are EDN contracts with no Python imports.

(deftest lint-endpoints-use-real-api
  ;; v1: test_lint_endpoints_use_real_api
  ;; /lint is retired in v2: it serves the `stores check` verdict in v1's problem shape.
  ;; v1's edge/link problems have no v2 counterpart; the store problem does: a model edit
  ;; that leaves documents unbackfilled makes `stores check` fail.
  (with-server chapter-setup
    (fn [base proj]
      (let [[st lint] (get-json base "/lint")]
        (is (= 200 st))
        (is (= 0 (get-in lint [:data :count])) "a fresh store checks clean"))
      (run proj "models" "field-add" "SimpleBook" "--field" "price" "--type" "int" "--description" "Field int.")
      (let [[st lint] (get-json base "/lint")
            problems (get-in lint [:data :problems])]
        (is (= 200 st))
        (is (= (get-in lint [:data :count]) (count problems)))
        (doseq [p problems]
          (is (= #{:kind :severity :doc :detail} (set (keys p)))))
        (is (some #(and (= "store/invalid" (:kind %)) (= "error" (:severity %))) problems))))))

(deftest fields-list-and-single-path
  ;; v1: test_fields_list_and_single_path
  ;; Differences: `fields` is the payload map ({:title "My Book"}), not a list of
  ;; {name value is_required is_list}; the single path answers {doc model path value
  ;; field address owning_section} without type/is_list/is_required/is_reference.
  (with-server chapter-setup
    (fn [base _]
      (let [[st body] (get-json base "/fields?doc=book")]
        (is (= 200 st))
        (is (= {:doc "book" :model "SimpleBook" :fields {:title "My Book"}} (:data body))))
      (let [[st body] (get-json base "/fields?doc=book&path=title")]
        (is (= 200 st))
        (is (= {:doc "book" :model "SimpleBook" :path "title" :value "My Book"}
               (select-keys (:data body) [:doc :model :path :value]))))
      (let [[st body] (get-json base "/fields?doc=book&path=missing")]
        (is (= 404 st))
        (is (false? (:ok body)))))))

(deftest sections-from-ir-context-index
  ;; v1: test_sections_from_ir_context_index
  ;; kimun's `sections list` also answers path "my-book/setup" (slug path of the
  ;; heading chain), under :data :sections with :document instead of :doc; there is
  ;; no :field_paths (it adds slug, level, line_start/line_end, breadcrumbs, about).
  (with-server chapter-setup
    (fn [base _]
      (let [[st body] (get-json base "/sections?doc=guide")
            setup (first (filter #(= "Setup" (:title %)) (get-in body [:data :sections])))]
        (is (= 200 st))
        (is (= "guide" (get-in body [:data :document])))
        (is (= "my-book/setup" (:path setup)))
        (is (= ["My Book" "Setup"] (:breadcrumbs setup)))))))

(deftest ast-exposes-full-ir-and-sections
  ;; v1: test_ast_exposes_full_ir_and_sections
  ;; Differences: fields are flat under :data (v1 nested them in `document`); `sections`
  ;; is the `sections list` envelope data; the `ir` (structure/surface) is not in /ast —
  ;; it is served by /document and /document/ir.
  (with-server chapter-setup
    (fn [base _]
      (let [[st body] (get-json base "/ast?doc=guide")
            d (:data body)]
        (is (= 200 st))
        (is (= ["guide" "BookWithSetup"] ((juxt :name :model) d)))
        (is (= {:title "My Book" :setup "Set it up."} (:payload d)))
        (is (some #(= "Setup" (:title %)) (get-in d [:sections :sections])))))))

(deftest extract-and-render
  ;; v1: test_extract_and_render
  ;; Difference: rendered Markdown ends with a newline ("# My Book\n"), v1's did not.
  (with-server chapter-setup
    (fn [base _]
      (let [[st body] (get-json base "/extract?doc=book")]
        (is (= 200 st))
        (is (= {:doc "book" :payload {:title "My Book"}} (:data body))))
      (let [[st body] (get-json base "/render?doc=book")]
        (is (= 200 st))
        (is (= "# My Book" (str/trimr (get-in body [:data :markdown])))))
      (let [[st body] (get-json base "/render?model=SimpleBook")]
        (is (= 200 st))
        (is (= "# ⸢rev•title⸥" (str/trimr (get-in body [:data :template]))))))))

(deftest reader-routes-validate-params
  ;; v1: test_reader_routes_validate_params
  (with-server chapter-setup
    (fn [base _]
      (let [[st body] (get-json base "/fields")]
        (is (= 400 st))
        (is (str/includes? (get-in body [:error :message]) "doc")))
      (doseq [path ["/sections?doc=ghost" "/ast?doc=ghost" "/render?doc=ghost"]]
        (let [[st body] (get-json base path)]
          (is (= 404 st) path)
          (is (false? (:ok body)) path)))
      (let [[st body] (get-json base "/render?doc=book&format=html")]
        (is (= 200 st))
        (is (= "# My Book" (str/trimr (get-in body [:data :markdown]))))))))
