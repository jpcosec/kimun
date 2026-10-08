#!/usr/bin/env bb
;; Packs a parity corpus laid out as one directory per case (the layout the
;; recorder plugins used to write, see record_engine.py / record_segments.py)
;; into a single gzipped JSON-lines file, one record per case:
;;
;;   {"id" "<old directory name>" "descriptor" "<file text>" …}
;;
;; Every field is the byte-exact text of the file the case directory used to
;; hold, under the name the test namespaces read it by; a file the case did not
;; have is simply absent. Cases are written sorted by id, and the gzip stream is
;; deterministic (Java writes mtime 0), so re-packing the same directory
;; reproduces the same file.
;;
;; Usage:
;;   bb scripts/parity/pack_corpus.clj <corpus-dir> [<out-file>]
;;   (out defaults to <corpus-dir>.cases.jsonl.gz, e.g.
;;    test/fixtures/parity/engine -> test/fixtures/parity/engine.cases.jsonl.gz)
;;
;; EXCLUSIONS.edn / PENDING.edn are not cases and are left where they are; the
;; recorder plugins write the packed file directly, this script packs an
;; existing directory corpus (or re-packs after extending one).
(ns pack-corpus
  (:require [babashka.fs :as fs]
            [cheshire.core :as json]
            [clojure.string :as str])
  (:import (java.io BufferedWriter FileOutputStream OutputStreamWriter)
           (java.nio.charset StandardCharsets)
           (java.util.zip GZIPOutputStream)))

(def ^:private fields
  [["descriptor.edn" "descriptor"]
   ["input.md" "input"]
   ["template.md" "template"]
   ["payload.json" "payload"]
   ["rendered.md" "rendered"]
   ["error.json" "error"]
   ["refs.json" "refs"]
   ["ref-models.edn" "ref-models"]
   ["args.json" "args"]
   ["output.json" "output"]
   ["meta.edn" "meta"]])

(defn- case-record [case-dir]
  (into {"id" (str (fs/file-name case-dir))}
        (keep (fn [[f k]]
                (let [p (fs/path case-dir f)]
                  (when (fs/exists? p) [k (slurp (str p))]))))
        fields))

(defn pack!
  "Pack every case directory under `dir` into `out` (gzip JSON lines, sorted by id)."
  [dir out]
  (let [cases (sort (filter fs/directory? (fs/list-dir dir)))]
    (fs/create-dirs (fs/parent out))
    (with-open [os (GZIPOutputStream. (FileOutputStream. (fs/file out)))
                w (BufferedWriter. (OutputStreamWriter. os StandardCharsets/UTF_8))]
      (doseq [c cases]
        (.write w (json/generate-string (case-record c)))
        (.write w "\n")))
    (count cases)))

(defn- main [args]
  (let [[dir out] args]
    (when (str/blank? dir)
      (binding [*out* *err*]
        (println "usage: bb scripts/parity/pack_corpus.clj <corpus-dir> [<out-file>]"))
      (System/exit 2))
    (let [out (or out (str (str/replace dir #"/+$" "") ".cases.jsonl.gz"))
          n (pack! dir out)]
      (println (str "packed " n " case(s) from " dir " into " out
                    " (" (fs/size out) " bytes)")))))

(main *command-line-args*)
