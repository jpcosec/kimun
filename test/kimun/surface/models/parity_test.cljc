(ns kimun.surface.models.parity-test
  "Differential parity with sldb v1 (docs/v2/tests/parity-v1.md): every case
   recorded from v1's own suite under test/fixtures/parity/engine must be
   reproduced, except those listed with a reason in EXCLUSIONS.edn (retired
   by design) or in PENDING.edn (not reproduced yet — a debt, not an excuse)."
  (:require [babashka.fs :as fs]
            [cheshire.core :as json]
            [clj-yaml.core :as yaml]
            [clojure.edn :as edn]
            [clojure.string :as str]
            [clojure.test :refer [deftest is]]
            [kimun.surface.models.extract :as extract]
            [kimun.surface.models.render :as render]))

(def root "test/fixtures/parity/engine")

(defn- slurp? [dir f] (let [p (fs/path dir f)] (when (fs/exists? p) (slurp (str p)))))

(defn load-case
  "One recorded case as a map."
  [dir]
  {:id (str (fs/file-name dir))
   :meta (edn/read-string (slurp? dir "meta.edn"))
   :descriptor (edn/read-string (slurp? dir "descriptor.edn"))
   :input (slurp? dir "input.md")
   :payload (some-> (slurp? dir "payload.json") (yaml/parse-string :keywords false))
   :rendered (slurp? dir "rendered.md")
   :error (some-> (slurp? dir "error.json") json/parse-string)
   :ctx (let [refs (some-> (slurp? dir "refs.json") json/parse-string)
              models (some-> (slurp? dir "ref-models.edn") edn/read-string)]
          (when (or refs models) {:ref-text #(get refs %) :ref-model #(get models %)}))})

(defn- norm [v] (json/parse-string (json/generate-string v)))

(defn check
  "nil when kimun reproduces case `c`, else a short reason string."
  [{:keys [meta descriptor input payload rendered error ctx]}]
  (try
    (case (:op meta)
      :extract (if error
                 (try (extract/extract descriptor input) "expected an error"
                      (catch clojure.lang.ExceptionInfo e (when-not (:type (ex-data e)) "untyped error")))
                 (let [got (norm (extract/extract descriptor input))]
                   (when (not= got (norm payload)) "payload differs")))
      :render (if error
                (try (render/render descriptor payload ctx) "expected an error"
                     (catch clojure.lang.ExceptionInfo e (when-not (:type (ex-data e)) "untyped error")))
                (let [out (render/render descriptor payload ctx)]
                  (cond (not= (str/trim out) (str/trim rendered)) "render differs"
                        :else nil))))
    (catch Throwable t (str "threw: " (or (ex-message t) (class t))))))

(defn- ids [f] (let [p (str root "/" f)] (if (fs/exists? p) (edn/read-string (slurp p)) {})))

(defn run-corpus
  "`{:pass n :fail {id reason} :excluded n :pending n}` over the whole corpus."
  []
  (let [excl (ids "EXCLUSIONS.edn") pend (ids "PENDING.edn")]
    (reduce (fn [acc dir]
              (let [c (load-case dir)]
                (cond (or (:retired (:meta c)) (contains? excl (:id c))) (update acc :excluded inc)
                      :else (let [r (check c)]
                              (cond (nil? r) (if (contains? pend (:id c)) (update acc :fixed conj (:id c)) (update acc :pass inc))
                                    (contains? pend (:id c)) (update acc :pending inc)
                                    :else (assoc-in acc [:fail (:id c)] r))))))
            {:pass 0 :fail {} :excluded 0 :pending 0 :fixed []}
            (sort (filter fs/directory? (fs/list-dir root))))))

(deftest corpus-is-reproduced
  (let [{:keys [pass fail excluded pending fixed]} (run-corpus)]
    (println "parity engine corpus:" {:pass pass :fail (count fail) :excluded excluded :pending pending})
    (is (empty? fail) (str (count fail) " case(s) regressed: " (pr-str (take 5 fail))))
    (is (empty? fixed) (str "these PENDING cases now pass; remove them from PENDING.edn: " (pr-str (take 5 fixed))))
    (is (pos? pass))))
