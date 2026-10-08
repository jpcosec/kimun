(ns kimun.surface.models.blocks-parity-test
  "Fase 1 of R2 §5 (decisión 29): the pod's block segmentation must reproduce
   `blocks/parse-native` (the differential oracle) on every document of the
   parity corpus and the desk — kinds **and** spans. Runs in streaming: one
   document at a time, only counters and the first few divergences kept."
  (:require [babashka.fs :as fs]
            [cheshire.core :as json]
            [clojure.java.io :as io]
            [clojure.test :refer [deftest is testing]]
            [kimun.host.treesitter :as ts]
            [kimun.surface.models.blocks :as blocks])
  (:import (java.util.zip GZIPInputStream)))

(def packed "test/fixtures/parity/engine.cases.jsonl.gz")
(def desk-glob "desk")

(def ^:private empty-acc
  {:total 0 :matched 0 :divergent 0 :pod-nil 0 :pod-error 0 :examples []})

(defn- snippet [s] (subs s 0 (min 200 (count s))))

(defn- step
  "Accumulates the comparison of one document: counters plus up to ten
   divergences. Never keeps whole results or trees."
  [acc text]
  (let [n (blocks/parse-native text)
        p (try (blocks/parse-pod text) (catch Throwable _ ::error))
        nk (mapv :type n)
        pk (when (vector? p) (mapv :type p))
        nm (mapv :map n)
        pm (when (vector? p) (mapv :map p))
        acc (update acc :total inc)]
    (cond
      (= p ::error) (update acc :pod-error inc)
      (nil? p) (update acc :pod-nil inc)
      (and (= nk pk) (= nm pm)) (update acc :matched inc)
      :else
      (-> acc
          (update :divergent inc)
          (update :examples (fn [es]
                              (if (< (count es) 10)
                                (conj es {:text (snippet text) :native nk :pod pk
                                          :native-map nm :pod-map pm})
                                es)))))))

(defn summarize
  "Streams the packed corpus (inside its reader) and then the desk through
   `step`, returning only counters and examples."
  []
  (with-open [r (io/reader (GZIPInputStream. (io/input-stream packed)))]
    (let [acc (reduce step empty-acc
                      (eduction (comp (map #(json/parse-string % true))
                                      (keep :input))
                                (line-seq r)))]
      (reduce step acc (map slurp (sort (map str (fs/glob desk-glob "**/*.md"))))))))

(deftest pod-blocks-parity
  (testing "tree-sitter pod blocks vs blocks/parse-native (kinds and spans)"
    (if-not (ts/available?)
      (println "skipped pod-blocks-parity: tree-sitter pod absent (run `bb pod`)")
      (let [{:keys [total matched divergent pod-nil pod-error examples]} (summarize)]
        (println (format "pod-blocks-parity: %d/%d identical (divergent %d, no-counterpart %d, error %d)"
                         matched total divergent pod-nil pod-error))
        (doseq [e (take 3 examples)]
          (println "  divergence:" (pr-str (:text e))
                   "native" (:native e) (:native-map e)
                   "pod" (:pod e) (:pod-map e)))
        (is (pos? total) "the corpus and desk must not be empty")
        (is (zero? pod-error) "the pod path must not throw")
        (is (zero? pod-nil) "every pod block kind must have a counterpart here")
        (is (zero? divergent)
            (str divergent " of " total " documents diverge between the pod and the oracle"))))))

(deftest pod-blocks-directed-cases
  (testing "the two blocks.cljc gaps against CommonMark, now seen by the pod"
    (if-not (ts/available?)
      (println "skipped pod-blocks-directed-cases: tree-sitter pod absent (run `bb pod`)")
      (do
        ;; setext with `===`: the oracle does not detect it (R2 §6), the pod does.
        (is (= ["heading"] (mapv :type (blocks/parse-pod "T\n===\n")))
            "setext `===` is a heading for the pod (a gap the oracle does not close)")
        ;; html block: the pod sees `html_block`; this surface keeps the v1
        ;; vocabulary (paragraph) — the SLDB-MD profile makes it :opaque instead (04).
        (is (= ["paragraph"] (mapv :type (blocks/parse-pod "<div>x</div>\n")))
            "an html block keeps the surface vocabulary as a paragraph")))))
