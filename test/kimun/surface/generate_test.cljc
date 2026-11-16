(ns kimun.surface.generate-test
  "Generated documents (docs/v2/drafts/diseno-generar.md): the vocabulary `(generated path
   recipe)` of the evaluator and the pure running of the recipes of `kimun.surface.generate`."
  (:require [clojure.string :as str]
            [clojure.test :refer [deftest is testing]]
            [kimun.host.default :as host]
            [kimun.surface.declare :as declare]
            [kimun.surface.generate :as generate]))

(defn- project [& files]
  (declare/read-project host/host (mapv (fn [[f t]] {:file f :text t}) (partition 2 files))))

(defn- failure [& files]
  (try (apply project files) nil
       (catch clojure.lang.ExceptionInfo e (assoc (ex-data e) :message (ex-message e)))))

(defn- run-all
  "Texts by path of every recipe of `results`, asking `ctx` (default: its propositions)."
  ([results] (run-all results {}))
  ([results ctx] (run-all results ctx {}))
  ([results ctx opts]
   (into {} (map (juxt :path :text))
         (generate/run host/host (declare/recipes results)
                       (merge {:propositions (vec (mapcat :propositions results)) :paths [] :read-now (constantly nil)} ctx)
                       opts))))

(def example
  "The example of diseno-generar.md §2, in two files (the base and the views)."
  ["kimun/base.clj" "(ns proyecto.base)\n(relation isa \"A es de la clase B.\")\n"
   "kimun/vistas.clj"
   "(ns proyecto.vistas (:require [proyecto.base :refer [isa]]))

(isa uno decision)
(isa dos decision)
(isa tres otra)

(defn decisiones [ask]
  (str \"# Decisiones\\n\\n\"
       (apply str (for [p ((:about ask) 'decision)]
                    (str \"- \" (:term (:other p)) \"\\n\")))))

(isa (generated \"docs/decisiones.md\" decisiones) doc.indice)
"])

(deftest the-example-of-the-design-gives-the-hand-written-text
  (let [rs (apply project example)]
    (is (= {"docs/decisiones.md" "# Decisiones\n\n- uno\n- dos\n"} (run-all rs)))
    (testing "the file is a node of a proposition like any other"
      (is (= [{:r "proyecto.base/isa" :a {:generated {:path "docs/decisiones.md"}} :b {:term "doc.indice"}
               :context "proyecto.vistas" :at {:file "kimun/vistas.clj" :line 12}}]
             (map #(dissoc % :id) (filter #(:generated (:a %)) (:propositions (second rs)))))))
    (testing "the data of evaluating carries the path and the position, never the function"
      (is (= [] (:generated (first rs))))
      (is (= [{:path "docs/decisiones.md" :at {:file "kimun/vistas.clj" :line 12} :context "proyecto.vistas"}]
             (:generated (second rs)))))))

(deftest the-id-does-not-depend-on-the-recipe
  (let [id (fn [body] (-> (project "k/a.clj" (str "(ns a)\n(relation r \"d\")\n(r (generated \"out.md\" (fn [_] " body ")) x)"))
                          first :propositions first :id))]
    (is (string? (id "\"one\"")))
    (is (= (id "\"one\"") (id "(str \"completely\" \" other\")")) "same path, other recipe: same id")
    (is (not= (id "\"one\"") (-> (project "k/a.clj" "(ns a)\n(relation r \"d\")\n(r (generated \"other.md\" (fn [_] \"one\")) x)")
                                 first :propositions first :id))
        "another path: another id")))

(deftest evaluating-runs-no-recipe
  (let [calls (atom 0)]
    (with-redefs [generate/run-one (fn [& _] (swap! calls inc) "x")]
      (apply project example))
    (is (zero? @calls) "evaluation does not even look at run-one")
    (let [rs (project "k/a.clj" "(ns a)\n(def counter (atom 0))\n(generated \"o.md\" (fn [_] (swap! counter inc) \"x\"))\n(generated \"p.md\" (fn [_] (throw (Exception. \"never\"))))")]
      (is (= 2 (count (:generated (first rs)))) "registered, not run: the throwing one did not throw"))))

(deftest ask-answers-with-each-function
  (let [rs (project "k/a.clj"
                    "(ns a)
(relation isa \"d\")
(relation anchors \"d\")
(isa x UI.view)
(isa y UI.view.dash)
(anchors x (at \"docs/s.md\"))
(isa (files \"src/**/*.py\") Python)
(generated \"o.md\" (fn [ask] (pr-str {:about (mapv (juxt :via :matched :side :other) ((:about ask) 'UI.view))
                                      :exact (mapv :matched ((:about ask) \"UI.view\" {:exact true}))
                                      :path (mapv (juxt :via :pattern) ((:about-path ask) \"src/m/x.py\"))
                                      :members ((:members ask) \"src/**/*.py\")
                                      :terms (mapv (juxt :term :count) ((:terms ask)))})))")
        out (read-string (get (run-all rs {:paths ["src/a.py" "src/m/x.py" "README.md"]}) "o.md"))]
    (is (= [[:term "UI.view" :b {:term "x"}] [:term "UI.view.dash" :b {:term "y"}]] (:about out))
        "the rows of props/about (not its wrapper), with `:term` as strings; a symbol asks like a string")
    (is (= ["UI.view"] (:exact out)))
    (is (= [[:files "src/**/*.py"]] (:path out)))
    (is (= ["src/a.py" "src/m/x.py"] (:members out)))
    (is (= [["Python" 1] ["UI" 0] ["UI.view" 1] ["UI.view.dash" 1] ["x" 2] ["y" 1]] (:terms out)))))

(deftest ask-text-reads-the-region-an-anchor-names
  (let [doc "# Spec\n\nPrimer parrafo.\n\n## Caché\n\nLa caché no es una fuente.\n"
        rs (project "k/a.clj"
                    "(ns a)
(generated \"o.md\" (fn [ask]
  (pr-str [((:text ask) (at \"docs/spec.md\" [\"Spec\" \"Caché\"] {:type :paragraph :n 1}))
           ((:text ask) (at \"docs/spec.md\" [\"Spec\" \"Caché\"]))
           ((:text ask) (at \"docs/spec.md\" [\"Spec\"] {:type :paragraph :n 1} [0 6]))
           ((:text ask) (at \"docs/spec.md\"))
           ((:text ask) (at \"docs/spec.md\" [\"Nope\"] {:type :paragraph :n 1}))
           ((:text ask) (at \"docs/gone.md\"))])))")
        [para section span whole nope gone] (read-string (get (run-all rs {:read-now {"docs/spec.md" doc}} {}) "o.md"))]
    (is (= "La caché no es una fuente." para))
    (is (= "## Caché\n\nLa caché no es una fuente." section))
    (is (= "Primer" span))
    (is (= doc whole))
    (is (nil? nope) "does not resolve")
    (is (nil? gone) "the file is not there")))

(deftest a-bad-recipe-is-a-declared-error
  (let [rs (fn [body] (project "k/a.clj" (str "(ns a)\n\n(generated \"o.md\" (fn [ask] " body "))")))
        err (fn [body & [opts]]
              (try (run-all (rs body) {} (or opts {})) nil
                   (catch clojure.lang.ExceptionInfo e (assoc (ex-data e) :message (ex-message e)))))]
    (testing "not a string"
      (let [e (err "42")]
        (is (= [:declare/bad-recipe "o.md" "k/a.clj" 3] ((juxt :type :path :file :line) e)))
        (is (re-find #"^k/a\.clj:3: .*o\.md.*string" (:message e)))))
    (testing "a nil result (the common mistake) is not a string either"
      (is (= :declare/bad-recipe (:type (err "nil")))))
    (testing "it throws"
      (let [e (err "(throw (ex-info \"boom\" {}))")]
        (is (= [:declare/recipe "o.md" 3] ((juxt :type :path :line) e)))
        (is (re-find #"boom" (:message e)))))
    (testing "it does not end"
      (let [t0 (System/nanoTime)
            e (err "(loop [] (recur))" {:timeout-ms 300})]
        (is (= [:declare/timeout "o.md" 3 300] ((juxt :type :path :line :timeout-ms) e)))
        (is (< (/ (- (System/nanoTime) t0) 1e6) 5000))))))

(deftest recipes-are-isolated-to-ask
  ;; sci resolves the symbols of a `fn` when the file is evaluated, so what the host has is
  ;; refused there (`unresolved`); either way nothing of the host is reached
  (doseq [body ["(slurp \"/etc/hostname\")" "(spit \"/tmp/never-written-by-a-recipe\" \"x\")" "(System/exit 3)"
                "(java.io.File. \"x\")" "(require 'clojure.java.shell)"]]
    (let [e (try (run-all (project "k/a.clj" (str "(ns a)\n(generated \"o.md\" (fn [ask] " body "))"))) nil
                 (catch clojure.lang.ExceptionInfo e (ex-data e)))]
      (is (contains? #{:declare/unresolved :declare/recipe :declare/eval} (:type e)) (str body " -> " (:type e)))))
  (is (not (.exists (java.io.File. "/tmp/never-written-by-a-recipe"))))
  (testing "a recipe only sees `ask`: the host functions it gets are the ones in the map"
    (is (= #{:about :about-path :members :terms :text}
           (set (keys (generate/ask-for host/host {:propositions [] :paths [] :read-now (constantly nil)})))))))

(deftest a-recipe-called-after-the-evaluation-and-from-another-namespace-works
  ;; the function is a sci fn made while the file evaluated; it is called later, outside that eval,
  ;; with *ns* bound to something else (the trap of cut 1)
  (let [rs (project "k/a.clj" "(ns a)\n(defn helper [x] (str \"<\" x \">\"))\n(generated \"o.md\" (fn [ask] (helper (count ((:terms ask))))))")]
    (binding [*ns* (create-ns 'some.other.ns)]
      (is (= {"o.md" "<0>"} (run-all rs))))))

(deftest the-same-path-needs-the-same-recipe
  (testing "two different recipes: conflict with both positions"
    (let [e (failure "k/a.clj" "(ns a)\n(generated \"o.md\" (fn [_] \"a\"))\n"
                     "k/b.clj" "(ns b)\n\n(generated \"./o.md\" (fn [_] \"b\"))\n")]
      (is (= [:declare/generated-conflict "k/b.clj" 3 "o.md"] ((juxt :type :file :line :path) e)))
      (is (= [{:file "k/a.clj" :line 2} {:file "k/b.clj" :line 3}] (:positions e)))
      (is (re-find #"^k/b\.clj:3: .*k/a\.clj:2" (:message e))))
    (is (= :declare/generated-conflict (:type (failure "k/a.clj" "(ns a)\n(generated \"o.md\" (fn [_] \"a\"))\n(generated \"o.md\" (fn [_] \"b\"))")))
        "also inside one file"))
  (testing "the same function named twice (loose and inside a proposition) is one registration"
    (let [rs (project "k/a.clj" "(ns a)\n(relation isa \"d\")\n(defn r [_] \"x\")\n(generated \"o.md\" r)\n(isa (generated \"o.md\" r) doc)\n")]
      (is (= 1 (count (declare/recipes rs))))
      (is (= [4] (map (comp :line :at) (declare/recipes rs))) "the first declaration")
      (is (= {"o.md" "x"} (run-all rs))))))

(deftest many-documents-of-a-class-are-declared-with-code
  (let [rs (project "k/a.clj"
                    "(ns a)
(relation isa \"d\")
(doseq [n [\"uno\" \"dos\" \"tres\"]]
  (isa n modulo)
  (isa (generated (str \"gen/\" n \".md\") (fn [_] (str \"# \" n))) doc.modulo))")]
    (is (= {"gen/uno.md" "# uno" "gen/dos.md" "# dos" "gen/tres.md" "# tres"} (run-all rs)))
    (is (= 3 (count (:generated (first rs)))))))

(deftest a-generated-path-has-the-shape-of-a-project-path
  (doseq [[p line] [["\"/etc/x\"" 2] ["\"../x\"" 2] ["\"a/../x\"" 2] ["\"a//x\"" 2] ["\"dir/\"" 2] ["\"\"" 2] ["42" 2] ["\"a\\\\b\"" 2]]]
    (let [e (failure "k/a.clj" (str "(ns a)\n(generated " p " (fn [_] \"\"))"))]
      (is (= [:declare/bad-generated "k/a.clj" line] ((juxt :type :file :line) e)) p)))
  (testing "the recipe has to be a function"
    (doseq [r ["\"text\"" "nil" "{:a 1}" ":kw"]]
      (is (= :declare/bad-generated (:type (failure "k/a.clj" (str "(ns a)\n(generated \"o.md\" " r ")")))) r)))
  (testing "arity"
    (is (= :declare/bad-generated (:type (failure "k/a.clj" "(ns a)\n(generated \"o.md\")")))))
  (testing "a leading ./ is dropped"
    (is (= "o.md" (-> (project "k/a.clj" "(ns a)\n(generated \"./o.md\" (fn [_] \"\"))") first :generated first :path)))))

(deftest forbidden-places
  (is (every? generate/forbidden ["kimun/x.md" ".git/hooks/x" ".kimun/x" "kimun/sub/x.clj" ".git"]))
  (is (not-any? generate/forbidden ["docs/kimun/x.md" "kimunx/y" ".github/x" ".gitignore" "docs/.git/x" "docs/x.md"])))
