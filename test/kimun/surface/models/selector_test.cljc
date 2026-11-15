(ns kimun.surface.models.selector-test
  "The selector engine (docs/v2/16 §3, §3.1) in legacy mode: the interpreter of
   recipe steps split out of `extract` must change nothing observable."
  (:require [babashka.fs :as fs]
            [clojure.edn :as edn]
            [clojure.string :as str]
            [clojure.test :refer [deftest is testing]]
            [kimun.host.default :as host]
            [kimun.surface.docs :as docs]
            [kimun.surface.models.descriptor :as descriptor]
            [kimun.surface.models.extract :as extract]
            [kimun.surface.models.parity-test :as parity]
            [kimun.surface.models.recipe :as recipe]
            [kimun.surface.models.selector :as selector]))

(defn- golden [corpus model]
  (edn/read-string (slurp (str (fs/path "test" "fixtures" "models" corpus (str model ".edn"))))))

(def ^:private atom-doc (golden "deskops" "AtomDoc"))

(defn- steps [d] (recipe/recipes (:template d)))

(defn- via-selector
  "Typed payload of `d` through the selector engine alone."
  [d text]
  (descriptor/finalize d (selector/extract-selector (steps d) text)))

(defn- outcome
  "Value of `f`, or the `:type` of the error it raises."
  [f]
  (try (f) (catch clojure.lang.ExceptionInfo e [:raised (:type (ex-data e))])))

(def ^:private extract-cases
  "Every packed v1 case that extracts: descriptor, input, expected payload."
  (delay (->> (parity/load-cases) (map parity/load-case)
              (filter #(and (= :extract (:op (:meta %))) (:descriptor %) (nil? (:error %))
                            (not (:retired (:meta %)))))
              vec)))

(def ^:private golden-descriptors
  (delay (vec (for [corpus ["deskops" "hum-scrapper"]
                    f (sort (fs/glob (str (fs/path "test" "fixtures" "models" corpus)) "*.edn"))]
                (edn/read-string (slurp (str f)))))))

(deftest extract-and-selector-agree
  (testing "the v1 corpus: same typed payload (or the same error)"
    (is (> (count @extract-cases) 100))
    (doseq [{:keys [id descriptor input]} @extract-cases]
      (is (= (outcome #(via-selector descriptor input)) (outcome #(extract/extract descriptor input))) id)))
  (testing "the golden descriptors of both corpora, on their own template"
    (is (= 29 (count @golden-descriptors)))
    (doseq [d @golden-descriptors]
      (is (= (outcome #(via-selector d (:template d))) (outcome #(extract/extract d (:template d))))
          (:model/name d)))))

(def ^:private atom-head "---\nid: atom-x\ntitle: T\nfive_wh_one_plus: what\ntags: [a:b]\n---\n\n# T\n\n")

(deftest r3-quirk-of-v1-is-kept
  (testing "AtomDoc without `## Answer`: the required answer is captured anyway, not reported missing (3 cases of R3)"
    (doseq [body ["Some body.\n" "One line.\n\nTwo lines.\n" "- a\n- b\n"]
            :let [payload (via-selector atom-doc (str atom-head body))]]
      (is (= (str/trim body) (get payload "answer")) body))))

(deftest steps-survive-an-edn-round-trip
  (testing "§3.1: a selector is plain data"
    (doseq [d @golden-descriptors :let [s (steps d)]]
      (is (= s (edn/read-string (pr-str s))) (:model/name d))))
  (doseq [{:keys [id descriptor input]} @extract-cases
          :let [s (steps descriptor)
                back (edn/read-string (pr-str s))]]
    (is (= (outcome #(selector/extract-selector s input))
           (outcome #(selector/extract-selector back input))) id)))

(deftest a-step-without-outer-type-names-itself
  (let [good (first (steps atom-doc))
        bad (dissoc (second (steps atom-doc)) :outer-type)
        e (try (selector/extract-selector [good bad] atom-head) nil
               (catch clojure.lang.ExceptionInfo e e))]
    (is (= :models/invalid (:type (ex-data e))))
    (is (= 1 (:step (ex-data e))))
    (is (str/includes? (ex-message e) "step 1"))
    (is (str/includes? (ex-message e) ":outer-tag"))))

(deftest unselected-paragraph-leaves-the-content-hash
  (let [plain (str atom-head "## Answer\n\nThe answer.\n")
        styled (str atom-head "*an aside the class never selects*\n\n## Answer\n\nThe answer.\n")
        edited (str atom-head "## Answer\n\nThe answer, changed.\n")
        hash-of #(docs/content-hash host/host (via-selector atom-doc %))]
    (is (= (via-selector atom-doc plain) (via-selector atom-doc styled)))
    (is (= (hash-of plain) (hash-of styled)))
    (is (not= (hash-of plain) (hash-of edited)) "control: a captured edit does move it")))

;; ---------------------------------------------------------------- :strict (M1d)

(defn- raw [d text mode] (selector/extract-selector (steps d) text mode))

(deftest strict-reports-an-absent-section-as-missing
  (testing "docs/v2/16 §3.5 line 1: AtomDoc without `## Answer` (legacy keeps the v1 quirk)"
    (doseq [body ["Some body.\n" "One line.\n\nTwo lines.\n" "- a\n- b\n"]
            :let [text (str atom-head body)]]
      (is (= (str/trim body) (get (raw atom-doc text :legacy-v1-template) "answer")) "legacy captures it anyway")
      (is (not (contains? (raw atom-doc text :strict) "answer")) "strict leaves it out")
      (is (= [:raised :validate/missing]
             (outcome #(extract/extract atom-doc text :strict))) "so the typed payload fails as missing")
      (is (= (str/trim body) (get (extract/extract atom-doc text) "answer")) "default mode is still legacy"))))

(deftest strict-gives-a-present-empty-section-the-empty-string
  (let [notes-steps (recipe/recipes "# ⸢rev•title⸥\n\n## Notes\n\n⸢optrev•notes⸥\n\n## Next\n\n⸢rev•next⸥")
        text "# T\n\n## Notes\n\n## Next\n\nn\n"]
    (testing "docs/v2/16 §3.5 line 2: `## Notes` is present and empty"
      (is (= {"title" "T" "next" "n"} (selector/extract-selector notes-steps text)) "legacy leaves the optional field out")
      (is (= {"title" "T" "notes" "" "next" "n"} (selector/extract-selector notes-steps text :strict))))))

(deftest strict-equals-legacy-on-a-document-without-quirks
  (let [text (str atom-head "## Answer\n\nThe answer.\n")]
    (is (= "The answer." (get (raw atom-doc text :strict) "answer")))
    (is (= (raw atom-doc text :legacy-v1-template) (raw atom-doc text :strict))))
  (testing "every golden template on its own text"
    (doseq [d @golden-descriptors]
      (is (= (outcome #(raw d (:template d) :legacy-v1-template))
             (outcome #(raw d (:template d) :strict))) (:model/name d)))))

(deftest an-unknown-mode-fails-naming-it
  (let [e (try (selector/extract-selector (steps atom-doc) atom-head :lenient) nil
               (catch clojure.lang.ExceptionInfo e e))]
    (is (= :models/invalid (:type (ex-data e))))
    (is (= :lenient (:mode (ex-data e))))
    (is (str/includes? (ex-message e) ":lenient"))))
