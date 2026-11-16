(ns kimun.surface.declare-test
  "The declaration evaluator (docs/v2/drafts/diseno-evaluar.md §1-§4): the
   example of §1, bare against bound symbols, generation with code, identity,
   declared errors with file and line, and the isolation of the sandbox."
  (:require [clojure.test :refer [deftest is testing]]
            [kimun.host.default :as host]
            [kimun.kernel.canon :as canon]
            [kimun.surface.declare :as declare]))

(def example
  "The complete example of diseno-evaluar.md §1."
  "(ns proyecto.decisiones)
(relation anchors \"A está en B.\")
(relation isa \"A es de la clase B.\")
(relation summarizes \"A resume B.\")
(relation supports \"A respalda B.\")
(isa (files \"src/ui/**/*.py\") UI.component)
(anchors cache-decision (at \"docs/spec.md\" [\"Caché\"] {:type :paragraph :n 1}))
(summarizes cache-is-not-a-source cache-decision)
(supports (summarizes cache-is-not-a-source cache-decision) owner-2026-11-15)")

(defn- id-of [r a b ctx] (canon/digest host/host {:r (str r) :a a :b b :context (str ctx)}))

(defn- t [s] {:term (str s)})

(defn- ev [text] (declare/read-declarations text "d.clj"))

(defn- failure
  "The `ex-data` of the declared error `text` raises (the test fails if none)."
  [text]
  (try (ev text) nil
       (catch clojure.lang.ExceptionInfo e (assoc (ex-data e) :message (ex-message e)))))

(deftest the-complete-example-gives-the-expected-propositions
  (let [ctx 'proyecto.decisiones
        anchor {:anchor {:path "docs/spec.md" :locator [["Caché"] {:type :paragraph :n 1}]}}
        id1 (id-of 'proyecto.decisiones/isa {:files "src/ui/**/*.py"} (t 'UI.component) ctx)
        id2 (id-of 'proyecto.decisiones/anchors (t 'cache-decision) anchor ctx)
        id3 (id-of 'proyecto.decisiones/summarizes (t 'cache-is-not-a-source) (t 'cache-decision) ctx)
        id4 (id-of 'proyecto.decisiones/supports {:proposition id3} (t 'owner-2026-11-15) ctx)
        at (fn [n] {:file "d.clj" :line n})
        r (ev example)]
    (is (= "proyecto.decisiones" (:context r)))
    (is (= {"anchors" {:doc "A está en B."} "isa" {:doc "A es de la clase B."}
            "summarizes" {:doc "A resume B."} "supports" {:doc "A respalda B."}}
           (:relations r)))
    (is (= [{:r "proyecto.decisiones/isa" :a {:files "src/ui/**/*.py"} :b (t 'UI.component) :context "proyecto.decisiones" :at (at 6) :id id1}
            {:r "proyecto.decisiones/anchors" :a (t 'cache-decision) :b anchor :context "proyecto.decisiones" :at (at 7) :id id2}
            {:r "proyecto.decisiones/summarizes" :a (t 'cache-is-not-a-source) :b (t 'cache-decision) :context "proyecto.decisiones" :at (at 8) :id id3
             ;; the nested use at line 9 is the same proposition written again
             :also-at [(at 9)]}
            {:r "proyecto.decisiones/supports" :a {:proposition id3} :b (t 'owner-2026-11-15) :context "proyecto.decisiones" :at (at 9) :id id4}]
           (:propositions r)))
    (is (= "44d0eb639f107ca71587f6a896dbb4973003721f9b67743a83ccf50e5064bbce" id1) "the id is frozen: stable across runs and machines")))

(deftest bare-symbol-is-a-term-and-bound-symbol-is-its-value
  (let [props (:propositions
               (ev "(ns t)
(relation isa \"d\")
(isa x UI.view.dashboard)
(let [x \"bound\"] (isa x UI.view.dashboard))
(def y :def-value)
(isa y name)
(defn gen [p] (isa p first))
(gen 'from-param)"))
        ab (map (juxt :a :b) props)]
    (is (= [[(t 'x) (t 'UI.view.dashboard)]
             ["bound" (t 'UI.view.dashboard)]
             [:def-value (t 'name)]
             [(t 'from-param) (t 'first)]]
           (vec ab))
        "x bare is a term, x in a let is its value; a def and a parameter are values; core names stay terms")))

(deftest declarations-can-be-generated-with-code
  (let [r (ev "(ns g)
(relation isa \"d\")
(doseq [f [\"a.py\" \"b.py\"]] (isa (files f) Gen))
(defn both [t] (isa t K) (isa K t))
(both 'q)
(dorun (for [n (range 2)] (isa n Num)))")]
    (is (= [[{:files "a.py"} (t 'Gen)] [{:files "b.py"} (t 'Gen)] [(t 'q) (t 'K)] [(t 'K) (t 'q)] [0 (t 'Num)] [1 (t 'Num)]]
           (mapv (juxt :a :b) (:propositions r))))))

(deftest the-same-proposition-twice-is-one
  (let [r (ev "(ns t)
(relation isa \"d\")
(isa a B)
(isa c D)
(isa a B)")]
    (is (= [(t 'a) (t 'c)] (mapv :a (:propositions r))))
    (is (= [{:file "d.clj" :line 5}] (:also-at (first (:propositions r)))))
    (is (nil? (:also-at (second (:propositions r)))))))

(deftest id-is-stable-independent-of-position-and-sensitive-to-context
  (let [one (fn [ns-sym pad] (first (:propositions (ev (str "(ns " ns-sym ")\n(relation isa \"d\")\n" pad "(isa a B)")))))
        base (one 't "")
        moved (one 't "\n\n\n")]
    (is (= (:id base) (:id (one 't ""))) "same text, same id")
    (is (not= (:at base) (:at moved)) "the position did change")
    (is (= (:id base) (:id moved)) ":at does not enter the id")
    (is (not= (:id base) (:id (one 'other ""))) "another context, another id")))

(deftest declared-errors-carry-file-and-line
  (testing "relation not defined"
    (let [f (failure "(ns t)\n\n(nope a b)")]
      (is (= [:declare/unresolved "d.clj" 3] ((juxt :type :file :line) f)))))
  (testing "wrong arity, too few and too many"
    (is (= [:declare/arity 3] ((juxt :type :line) (failure "(ns t)\n(relation r \"d\")\n(r a)"))))
    (is (= [:declare/arity 3] ((juxt :type :line) (failure "(ns t)\n(relation r \"d\")\n(r a b c)")))))
  (testing "missing or empty docstring"
    (is (= [:declare/bad-relation 2] ((juxt :type :line) (failure "(ns t)\n(relation r)"))))
    (is (= [:declare/bad-relation 2] ((juxt :type :line) (failure "(ns t)\n(relation r \"  \")")))))
  (testing "no ns: the first form is not one, or there is no form at all"
    (is (= [:declare/no-context 1] ((juxt :type :line) (failure "(relation r \"d\")"))))
    (is (= [:declare/no-context 1] ((juxt :type :line) (failure "(def a 1)\n(ns t)"))) "ns must be the first form")
    (is (= :declare/no-context (:type (failure "")))))
  (testing "ns clauses other than a requirement of a context are not part of a declaration file"
    (is (= :declare/ns-clauses (:type (failure "(ns t (:import java.io.File))"))))
    (is (= :declare/ns-clauses (:type (failure "(ns t (:use a))")))))
  (testing "a file that requires another context needs the set that defines it"
    (is (= [:declare/unknown-context 1] ((juxt :type :line) (failure "(ns t (:require [clojure.string :as s]))")))))
  (testing "a syntax error"
    (let [f (failure "(ns t)\n(relation r \"d\")\n(r a")]
      (is (= [:declare/syntax "d.clj"] ((juxt :type :file) f)))
      (is (= 3 (:line f)))))
  (testing "an argument that is not a node, and a float (the canon does not admit it)"
    (is (= :declare/bad-node (:type (failure "(ns t)\n(relation r \"d\")\n(r a (fn [] 1))"))))
    (testing "a map or a vector written by the user is never a node"
      (is (= :declare/bad-node (:type (failure "(ns t)\n(relation r \"d\")\n(r a {:files \"x\"})"))))
      (is (= :declare/bad-node (:type (failure "(ns t)\n(relation r \"d\")\n(r a [1 2])")))))
    (is (= :declare/bad-node (:type (failure "(ns t)\n(relation r \"d\")\n(r a 1.5)")))))
  (testing "redefining a relation with another docstring is an error; with the same one it is not"
    (let [f (failure "(ns t)\n(relation r \"one\")\n(relation r \"two\")")]
      (is (= [:declare/relation-redefined "d.clj" 3] ((juxt :type :file :line) f))))
    (is (= {"r" {:doc "one"}} (:relations (ev "(ns t)\n(relation r \"one\")\n(relation r \"one\")")))))
  (testing "the message names the file and the line"
    (is (re-find #"^d\.clj:3: " (:message (failure "(ns t)\n\n(nope a b)"))))))

(deftest the-sandbox-rejects-the-host
  (doseq [form ["(slurp \"/etc/hostname\")"
                "(spit \"/tmp/kimun-declare-should-not-exist\" \"x\")"
                "(System/exit 3)"
                "(java.io.File. \"/tmp\")"
                "(clojure.java.shell/sh \"ls\")"
                "(require 'clojure.java.io)"
                "(load-file \"/etc/hostname\")"]]
    (let [f (failure (str "(ns t)\n" form))]
      (is (some? f) (str "must be rejected: " form))
      (is (= 2 (:line f)))
      (is (contains? #{:declare/unresolved :declare/eval} (:type f)) form)))
  (is (not (.exists (java.io.File. "/tmp/kimun-declare-should-not-exist"))))
  (is (= 1 (count (:propositions (ev "(ns t)\n(relation r \"d\")\n(r a b)"))))
      "the process is alive and evaluates afterwards"))

(deftest at-and-files-are-nodes-that-open-nothing
  (let [[p q] (:propositions (ev "(ns t)\n(relation r \"d\")\n(r (at \"no/such/file.md\") (files \"**/*.nowhere\"))\n(r (at \"x.md\" [\"a\"] {:type :list :n 2}) 7)"))]
    (is (= {:anchor {:path "no/such/file.md" :locator []}} (:a p)))
    (is (= {:files "**/*.nowhere"} (:b p)))
    (is (= {:anchor {:path "x.md" :locator [["a"] {:type :list :n 2}]}} (:a q)))
    (is (= 7 (:b q)))
    (is (nil? (meta (:a q))) "no host metadata leaks into the output")))

(deftest evaluation-is-pinned-to-its-own-namespace-whatever-the-caller
  (let [other (create-ns 'otro.ns)
        before (ns-name other)
        r (binding [*ns* other]
            (let [res (ev "(ns t)\n(relation r \"d\")\n(def v :val)\n(r v w)")
                  caller (ns-name *ns*)]
              (assoc res :caller caller)))]
    (is (= [[:val {:term "w"}]] (mapv (juxt :a :b) (:propositions r))) "vocabulary and `def` resolve although the caller is in another namespace")
    (is (= 'otro.ns (:caller r)) "the namespace of the caller did not change")
    (is (= before (ns-name other)))
    (is (nil? (ns-resolve other 'v)) "nothing leaked into the caller's namespace")))
