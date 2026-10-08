(ns kimun.surface.markdown.cst-ast-parity-test
  "Fase 2 of R2 §5 (decision 29): the pod view of SLDB-MD
   (`kimun.surface.markdown.cst-ast`) must produce the **same neutral AST** as
   the in-process parser (`kimun.surface.markdown.ast/parse`) over the parity
   corpus and the desk. Same AST ⇒ same node ids and same merkle root. Streams
   one document at a time; keeps counters plus up to twenty divergences."
  (:require [babashka.fs :as fs]
            [cheshire.core :as json]
            [clojure.java.io :as io]
            [clojure.test :refer [deftest is testing]]
            [kimun.host.default :as host]
            [kimun.host.treesitter :as ts]
            [kimun.surface.markdown.ast :as ast]
            [kimun.surface.markdown.cst-ast :as cst-ast])
  (:import (java.util.zip GZIPInputStream)))

(def packed "test/fixtures/parity/engine.cases.jsonl.gz")
(def desk-glob "desk")

(def ^:private empty-acc {:total 0 :identical 0 :divergent 0 :errors 0 :by-class {} :examples []})

(defn- snippet [s] (subs s 0 (min 200 (count s))))

(defn- type-of [x] (if (map? x) [(:type x) (:format x)] [x]))

(defn- first-div
  "First differing top-level child as `[index native-type pod-type]`, or
   `[:content]` when the top-level types match but the trees differ."
  [a b]
  (loop [i 0]
    (let [x (get a i ::eof) y (get b i ::eof)]
      (cond
        (= x y) (when-not (= x ::eof) (recur (inc i)))
        (= x ::eof) [i ::extra y]
        (= y ::eof) [i x ::extra]
        (not= (type-of x) (type-of y)) [i (type-of x) (type-of y)]
        :else [:content]))))

(defn- step [acc text]
  (let [n (try (ast/parse-native host/host text) (catch Throwable _ ::error))
        p (try (cst-ast/parse host/host text) (catch Throwable _ ::error))
        acc (update acc :total inc)]
    (cond
      (or (= n ::error) (= p ::error)) (update acc :errors inc)
      (= n p) (update acc :identical inc)
      :else
      (let [d (first-div (:children n) (:children p))
            label (if (and (vector? d) (= :content (first d)))
                    [:same-type-content]
                    [(second d) (nth d 2)])]
        (-> acc
            (update :divergent inc)
            (update :by-class #(update % label (fnil inc 0)))
            (update :examples (fn [es]
                                (if (< (count es) 20)
                                  (conj es {:text (snippet text) :label label
                                            :native (pr-str n) :pod (pr-str p)})
                                  es))))))))

(defn summarize
  "Streams the packed corpus (inside its reader) and then the desk through
   `step`."
  []
  (with-open [r (io/reader (GZIPInputStream. (io/input-stream packed)))]
    (let [acc (reduce step empty-acc
                      (eduction (comp (map #(json/parse-string % true))
                                      (keep :input))
                                (line-seq r)))]
      (reduce step acc (map slurp (sort (map str (fs/glob desk-glob "**/*.md"))))))))

(deftest pod-ast-parity
  (testing "cst-ast (pod) vs ast/parse (native): neutral AST must be identical"
    (if-not (ts/available?)
      (println "skipped pod-ast-parity: tree-sitter pod absent (run `bb pod`)")
      (let [{:keys [total identical divergent errors by-class examples]} (summarize)]
        (println (format "pod-ast-parity: %d/%d identical (divergent %d, errors %d)"
                         identical total divergent errors))
        (println "  by class:" (into (sorted-map) by-class))
        (doseq [e (take 3 examples)]
          (println "  divergence" (pr-str (:label e)) (pr-str (:text e))))
        (is (pos? total) "the corpus and desk must not be empty")
        (is (zero? errors) "neither parser may throw")
        (is (zero? divergent)
            (str divergent " of " total " documents diverge"))))))
