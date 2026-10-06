(ns kimun.adapters.treesitter-test
  "The tree-sitter symbols adapter (python/kimun/adapters/treesitter_symbols.py) run as a
   subprocess agrees with the ast adapter (python_symbols.py) on the shared python fixture trees
   (fact ids, kind, qualified_name, span, doc) and yields the expected facts for the javascript
   fixture. The interpreter is `$KIMUN_ADAPTER_PYTHON` (default `python3`); when it cannot import
   tree_sitter + its grammars the tests print a one-line SKIP and pass."
  (:require [babashka.fs :as fs]
            [babashka.process :as process]
            [cheshire.core :as json]
            [clojure.java.io :as io]
            [clojure.string :as str]
            [clojure.test :refer [deftest is testing]]))

(def ^:private py (or (System/getenv "KIMUN_ADAPTER_PYTHON") "python3"))

(def ^:private root
  "The worktree root: the directory holding `resources/` (and so `python/`, `test/`)."
  (-> (io/resource "VERSION") .getPath fs/path fs/parent fs/parent))

(defn- sh [& args]
  (apply process/shell {:out :string :err :string :continue true
                        :extra-env {"PYTHONDONTWRITEBYTECODE" "1"}} args))

(defn- importable? [& mods]
  (zero? (:exit (sh py "-c" (str "import " (str/join ", " mods))))))

(defn- adapter [name] (str (fs/path root "python" "kimun" "adapters" name)))
(defn- fixture [tree] (str (fs/path root "test" "fixtures" "adapters" tree)))

(defn- facts [script tree & args]
  (let [r (apply sh py (adapter script) (fixture tree) args)]
    (when-not (zero? (:exit r)) (throw (ex-info (str script " failed: " (:err r)) {:exit (:exit r)})))
    (let [fs (:facts (json/parse-string (:out r) true))]
      (into (sorted-map) (map (juxt :id identity)) fs))))

(defn- skip-unless [mods label f]
  (if (apply importable? mods)
    (f)
    (println (str "SKIP kimun.adapters.treesitter-test: " label " not available (set KIMUN_ADAPTER_PYTHON)"))))

(defn- contained-in [fact] (vec (keep #(when (= "contained-in" (:type %)) (:target %)) (:relations fact))))

(deftest python-agreement
  (skip-unless ["tree_sitter" "tree_sitter_python"] "tree_sitter runtime"
    (fn []
      (doseq [tree ["pytree" "pytree_ts"]]
        (testing tree
          (let [a (facts "python_symbols.py" tree) t (facts "treesitter_symbols.py" tree)]
            (is (= (keys a) (keys t)) "same fact ids")
            (is (pos? (count a)))
            (doseq [[id fa] a :let [ft (get t id)]]
              (doseq [k [:kind :name :qualified_name :path :span :source_hash :doc]]
                (is (= (k fa) (k ft)) (str id " " k)))
              (is (= (contained-in fa) (contained-in ft)) (str id " contained-in")))
            (println (str "treesitter agreement[" tree "]: compared " (count a) " facts"))))))))

(deftest javascript-fixture
  (skip-unless ["tree_sitter" "tree_sitter_javascript"] "tree_sitter javascript grammar"
    (fn []
      (let [t (facts "treesitter_symbols.py" "jstree" "--language" "javascript")]
        (is (= ["index" "index.Shape" "index.Shape.area" "index.Shape.constructor" "index.Shape.create"
                "index.Shape.load" "index.add" "index.double" "index.expr" "index.plain" "index.stream"
                "lib.util" "lib.util.Hidden" "lib.util.helper"]
               (vec (keys t))))
        (is (= "Add two numbers.\n\n@param {number} a\n@param {number} b" (:doc (t "index.add"))))
        (is (= "method" (:kind (t "index.Shape.area"))))
        (is (= ["index.Shape"] (contained-in (t "index.Shape.area"))))))))
