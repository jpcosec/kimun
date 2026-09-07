#!/usr/bin/env bb
;; Enforces docs/v2/03-estandares-de-codigo.md §1 (dependency rings, no global
;; mutable def in ring 0) and §3 (docstrings) over <root>/**/*.cljc for every
;; root given on the command line (default: src). Exit 1 on any violation.
(ns check-rings
  (:require [babashka.fs :as fs]
            [clojure.string :as str]
            [clojure.tools.reader :as r]
            [clojure.tools.reader.reader-types :as rt]))

(def rings
  "namespace prefix → set of prefixes it may require (besides itself)."
  {"sldb.kernel."  #{"clojure." "sldb.kernel."}
   "sldb.host."    #{"clojure." "sldb.kernel." "sldb.host." "babashka."}
   "sldb.surface." #{"clojure." "sldb.kernel." "sldb.host." "sldb.surface." "babashka."}
   "knowledge."    #{"clojure." "sldb.kernel." "sldb.host." "sldb.surface." "knowledge."
                     "babashka." "cheshire." "clj-yaml."}})

(def mutable-constructors
  "Heads of a `def` value form that create mutable state; forbidden in ring 0
   (docs/v2/03 §1: \"Ningún def global mutable\")."
  '#{atom ref agent volatile!})

(defn- ring-of [ns-name]
  (some (fn [[prefix _]] (when (str/starts-with? ns-name prefix) prefix)) rings))

(defn- read-forms [file]
  (let [rdr (rt/indexing-push-back-reader (slurp (str file)))]
    (loop [forms []]
      (let [f (r/read {:read-cond :allow :features #{:clj} :eof ::eof} rdr)]
        (if (= f ::eof) forms (recur (conj forms f)))))))

(defn- requires-of [ns-form]
  (for [clause (rest ns-form)
        :when (and (seq? clause) (= :require (first clause)))
        spec (rest clause)
        :let [lib (if (vector? spec) (first spec) spec)]
        :when (symbol? lib)]
    (str lib)))

(defn- public-vars-without-doc [forms]
  (for [f forms
        :when (and (seq? f) (symbol? (first f)))
        :let [head (name (first f))
              nm (second f)]
        :when (and (contains? #{"defn" "defmacro" "defprotocol" "def" "defrecord"} head)
                   (symbol? nm)
                   (not (:private (meta nm)))
                   (not (str/starts-with? (name nm) "-")))
        :let [doc? (case head
                     ("defn" "defmacro" "defprotocol") (string? (nth f 2 nil))
                     "def" (and (= 4 (count f)) (string? (nth f 2)))
                     "defrecord" true)]
        :when (not doc?)]
    (str head " " nm)))

(defn- protocol-methods-without-doc [forms]
  (for [f forms
        :when (and (seq? f) (= 'defprotocol (first f)))
        m (drop 2 f)
        :when (and (seq? m) (not (string? (last m))))]
    (str "defprotocol " (second f) " / " (first m))))

(defn- global-mutable-defs [forms]
  (for [f forms
        :when (and (seq? f) (= 'def (first f)) (symbol? (second f)))
        :let [v (last f)]
        :when (and (> (count f) 2) (seq? v) (contains? mutable-constructors (first v)))]
    (str "global mutable def in kernel: (def " (second f) " (" (first v) " …))")))

(defn check-file [file]
  (let [forms (read-forms file)
        ns-form (first (filter #(and (seq? %) (= 'ns (first %))) forms))
        ns-name (str (second ns-form))
        ring (ring-of ns-name)
        ns-doc? (string? (nth ns-form 2 nil))
        bad-requires (when ring
                       (for [req (requires-of ns-form)
                             :when (not (some #(str/starts-with? req %) (conj (rings ring) ns-name)))]
                         req))
        cond-in-kernel (when (and ring (= ring "sldb.kernel.") (not= ns-name "sldb.kernel.err"))
                         (when (str/includes? (slurp (str file)) "#?") ["reader conditional in kernel"]))
        mutable-in-kernel (when (= ring "sldb.kernel.") (global-mutable-defs forms))]
    (concat
     (when-not ring [(str ns-name ": namespace outside every ring")])
     (map #(str ns-name ": forbidden require " %) bad-requires)
     (map #(str ns-name ": " %) cond-in-kernel)
     (map #(str ns-name ": " %) mutable-in-kernel)
     (when-not ns-doc? [(str ns-name ": ns without docstring")])
     (map #(str ns-name ": missing docstring on " %) (public-vars-without-doc forms))
     (map #(str ns-name ": missing docstring on " %) (protocol-methods-without-doc forms)))))

(defn -main [& roots]
  (let [roots (if (seq roots) roots ["src"])
        files (sort (mapcat (fn [root] (map str (fs/glob root "**/*.cljc"))) roots))
        problems (mapcat check-file files)]
    (if (seq problems)
      (do (doseq [p problems] (println "RING/DOC:" p))
          (println (count problems) "violation(s) in" (count files) "files")
          (System/exit 1))
      (println "rings and docstrings ok:" (count files) "files"))))

(when (= *file* (System/getProperty "babashka.file")) (apply -main *command-line-args*))
