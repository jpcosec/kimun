(ns kimun.surface.models.selector-test
  "The selector engine (docs/v2/16 §3, §3.1, §3.5): the interpreter of recipe
   steps, with its single behaviour for absent and empty sections."
  (:require [babashka.fs :as fs]
            [clojure.edn :as edn]
            [clojure.string :as str]
            [clojure.test :refer [deftest is testing]]
            [kimun.host.default :as host]
            [kimun.surface.docs :as docs]
            [kimun.surface.models.descriptor :as descriptor]
            [kimun.surface.models.extract :as extract]
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

(def ^:private golden-descriptors
  (delay (vec (for [corpus ["deskops" "hum-scrapper"]
                    f (sort (fs/glob (str (fs/path "test" "fixtures" "models" corpus)) "*.edn"))]
                (edn/read-string (slurp (str f)))))))

(def ^:private atom-head "---\nid: atom-x\ntitle: T\nfive_wh_one_plus: what\ntags: [a:b]\n---\n\n# T\n\n")

(deftest steps-survive-an-edn-round-trip
  (testing "§3.1: a selector is plain data"
    (doseq [d @golden-descriptors :let [s (steps d)]]
      (is (= s (edn/read-string (pr-str s))) (:model/name d))))
  (testing "and extracts the same through the round trip"
    (doseq [d @golden-descriptors
            :let [s (steps d)
                  back (edn/read-string (pr-str s))]]
      (is (= (outcome #(selector/extract-selector s (:template d)))
             (outcome #(selector/extract-selector back (:template d)))) (:model/name d)))))

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

;; ------------------------------------------------------- absent and empty (M1d)

(defn- raw [d text] (selector/extract-selector (steps d) text))

(deftest an-absent-section-is-missing
  (testing "docs/v2/16 §3.5 line 1: AtomDoc without `## Answer`"
    (doseq [body ["Some body.\n" "One line.\n\nTwo lines.\n" "- a\n- b\n"]
            :let [text (str atom-head body)]]
      (is (not (contains? (raw atom-doc text) "answer")) "the selector leaves it out")
      (is (= [:raised :validate/missing]
             (outcome #(extract/extract atom-doc text))) "so the typed payload fails as missing"))))

(deftest a-present-empty-section-is-the-empty-string
  (let [notes-steps (recipe/recipes "# ⸢rev•title⸥\n\n## Notes\n\n⸢optrev•notes⸥\n\n## Next\n\n⸢rev•next⸥")
        text "# T\n\n## Notes\n\n## Next\n\nn\n"]
    (testing "docs/v2/16 §3.5 line 2: `## Notes` is present and empty"
      (is (= {"title" "T" "notes" "" "next" "n"} (selector/extract-selector notes-steps text))))))

(deftest a-document-with-all-its-sections-extracts-them
  (let [text (str atom-head "## Answer\n\nThe answer.\n")]
    (is (= "The answer." (get (raw atom-doc text) "answer")))))
