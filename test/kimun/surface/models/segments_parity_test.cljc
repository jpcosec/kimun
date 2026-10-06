(ns kimun.surface.models.segments-parity-test
  "Differential parity of `kimun.surface.models.segments` with sldb v1: every
   case recorded under test/fixtures/parity/segments (layout in the docstring
   of scripts/parity/record_segments.py) is replayed, dispatching on `:op`.
   A case is skipped only through an EXCLUSIONS.edn entry with a reason, and
   an entry that went stale — its case left the corpus, or kimun now
   reproduces it — fails the suite until it is removed."
  (:require [babashka.fs :as fs]
            [cheshire.core :as json]
            [clj-yaml.core :as yaml]
            [clojure.edn :as edn]
            [clojure.string :as str]
            [clojure.test :refer [deftest is]]
            [kimun.surface.models.segments :as segments]))

(def root "test/fixtures/parity/segments")

(defn- slurp? [dir f] (let [p (fs/path dir f)] (when (fs/exists? p) (slurp (str p)))))

(defn load-case
  "One recorded case as a map."
  [dir]
  {:id (str (fs/file-name dir))
   :meta (edn/read-string (slurp? dir "meta.edn"))
   :descriptor (some-> (slurp? dir "descriptor.edn") edn/read-string)
   :input (slurp? dir "input.md")
   :template (slurp? dir "template.md")
   :payload (some-> (slurp? dir "payload.json") (yaml/parse-string :keywords false))
   :args (some-> (slurp? dir "args.json") json/parse-string)
   :output (some-> (slurp? dir "output.json") json/parse-string)
   :error (some-> (slurp? dir "error.json") json/parse-string)
   :ctx (let [refs (some-> (slurp? dir "refs.json") json/parse-string)
              models (some-> (slurp? dir "ref-models.edn") edn/read-string)]
          (when (or refs models) {:ref-text #(get refs %) :ref-model #(get models %)}))})

(defn- norm [v] (json/parse-string (json/generate-string v)))

(defn- differing [got want]
  (when (not= got want)
    (str "differs at " (pr-str (vec (remove #(= (get got %) (get want %)) (distinct (concat (keys want) (keys got)))))))))

(defn check
  "nil when kimun reproduces case `c`, else a short reason string."
  [{:keys [meta descriptor input template payload args output error ctx]}]
  (try
    (case (keyword (name (:op meta)))
      :render_with_spans (differing (norm (segments/segments descriptor payload ctx)) output)
      :build_extraction_segments
      (if error
        (try (segments/extract-segments descriptor input ctx) "expected an error"
             (catch clojure.lang.ExceptionInfo e
               (when-not (contains? #{"models" "validate"} (some-> (:type (ex-data e)) namespace)) "untyped error")))
        (differing (norm (dissoc (segments/extract-segments descriptor input ctx) :canonical?)) output))
      :map_fields_to_sections
      (let [secs (mapv #(update-keys % keyword) (get args "sections"))]
        (differing {:v (norm (segments/field-owners template secs (some-> (get args "known_fields") set)))} {:v output}))
      :extract_sections (differing {:v (norm (segments/sections input))} {:v output})
      (str "unknown op " (:op meta)))
    (catch Throwable t (str "threw: " (or (ex-message t) (class t))))))

(defn run-corpus
  "`{:pass n :fail {id reason} :excluded n :pending 0 :ops {op {:pass :fail :excluded}}}`."
  []
  (let [excl (edn/read-string (slurp (str root "/EXCLUSIONS.edn")))]
    (reduce (fn [acc dir]
              (let [c (load-case dir) op (keyword (name (:op (:meta c))))
                    bump (fn [acc k] (-> acc (update k inc) (update-in [:ops op k] (fnil inc 0))))]
                (if (contains? excl (:id c))
                  (bump acc :excluded)
                  (if-let [r (check c)]
                    (-> acc (assoc-in [:fail (:id c)] (str (name op) ": " r)) (update-in [:ops op :fail] (fnil inc 0)))
                    (bump acc :pass)))))
            {:pass 0 :fail {} :excluded 0 :pending 0 :ops (sorted-map)}
            (sort (filter fs/directory? (fs/list-dir root))))))

(defn stale-exclusions
  "`{:missing [id…] :reproduced [id…]}`: the EXCLUSIONS.edn entries naming no
   case of the corpus, and those whose case kimun reproduces after all."
  []
  (reduce (fn [acc id]
            (let [dir (fs/path root (str id))]
              (cond (not (fs/directory? dir)) (update acc :missing conj (str id))
                    (nil? (check (load-case dir))) (update acc :reproduced conj (str id))
                    :else acc)))
          {:missing [] :reproduced []}
          (sort-by str (keys (edn/read-string (slurp (str root "/EXCLUSIONS.edn")))))))

(deftest exclusions-are-not-stale
  (let [{:keys [missing reproduced]} (stale-exclusions)]
    (is (empty? missing) (str "EXCLUSIONS.edn names cases that are not in the corpus: " (pr-str missing)))
    (is (empty? reproduced) (str "these excluded cases are reproduced now; remove them from EXCLUSIONS.edn: " (pr-str reproduced)))))

(deftest corpus-is-reproduced
  (let [{:keys [pass fail excluded pending ops]} (run-corpus)]
    (println "parity segments corpus:" {:pass pass :fail (count fail) :excluded excluded :pending pending})
    (println "parity segments corpus by op:" ops)
    (is (empty? fail) (str (count fail) " case(s) fail: " (str/join "; " (map pr-str (take 5 fail)))))
    (is (pos? pass))))
