(ns kimun.wittgenstein-manifest-test
  "Contract checks for the Wittgenstein C01-C28 inclusion manifest (T01):
   completeness of W1/W2 capabilities, honest Kimun status reporting,
   referential integrity of canonical fixtures and negative controls, and
   alignment with docs/v2/tests/wittgenstein-manifest.md."
  (:require [babashka.fs :as fs]
            [clojure.edn :as edn]
            [clojure.string :as str]
            [clojure.test :refer [deftest is testing]]))

(defn- repo-path [& parts]
  (str (apply fs/path (System/getProperty "user.dir") parts)))

(defn- load-manifest []
  (edn/read-string (slurp (repo-path "test" "fixtures" "wittgenstein" "manifest.edn"))))

(def ^:private expected-ids
  "Exact ordered capability identifiers C01 through C28."
  (mapv #(format "C%02d" %) (range 1 29)))

(def ^:private valid-dispositions
  "Allowed inclusion dispositions for T01."
  #{:required :deferred :excluded})

(def ^:private valid-statuses
  "Allowed implementation status labels in Kimun."
  #{:implemented :partial :direction :absent})

(def ^:private required-capability-keys
  "Mandatory fields for every capability entry in the manifest."
  [:id :line :source :title :disposition :reason :kimun-status
   :blocked-by :target-task :fixture :negative-control
   :expected-outcome :current-evidence :intentional-deviation])

(deftest manifest-covers-all-w1-and-w2-capabilities
  (let [m (load-manifest)
        caps (:capabilities m)
        w1-ids (mapv :id (filter #(= :W1 (:line %)) caps))
        w2-ids (mapv :id (filter #(= :W2 (:line %)) caps))]
    (is (= 1 (:schema-version m)))
    (is (= "T01" (:task m)))
    (is (= expected-ids (mapv :id caps)))
    (is (= (subvec expected-ids 0 20) w1-ids))
    (is (= (subvec expected-ids 20 28) w2-ids))
    (is (= {:total 28
            :by-line {:W1 20 :W2 8}
            :by-disposition (merge {:required 0 :deferred 0 :excluded 0}
                                   (frequencies (map :disposition caps)))
            :by-kimun-status (merge {:implemented 0 :partial 0 :direction 0 :absent 0}
                                    (frequencies (map :kimun-status caps)))
            :transversal-exclusions (count (:exclusions m))
            :transversal-deferrals (count (:deferrals m))}
           (:summary-counts m)))))

(deftest every-capability-satisfies-contract-and-ref-integrity
  (let [m (load-manifest)
        caps (:capabilities m)
        fixtures (:canonical-fixtures m)
        negatives (:negative-controls m)]
    (doseq [cap caps]
      (testing (:id cap)
        (doseq [k required-capability-keys]
          (is (contains? cap k) (str (:id cap) " missing key " k)))
        (is (contains? valid-dispositions (:disposition cap)))
        (is (contains? valid-statuses (:kimun-status cap)))
        (is (seq (:blocked-by cap)))
        (is (not (str/blank? (:expected-outcome cap))))
        (is (not (str/blank? (:intentional-deviation cap))))
        (is (contains? fixtures (:fixture cap))
            (str (:id cap) " references unknown fixture " (:fixture cap)))
        (is (contains? negatives (:negative-control cap))
            (str (:id cap) " references unknown negative control " (:negative-control cap)))
        (when (seq (:blocked-by cap))
          (is (not= :implemented (:kimun-status cap))
              (str (:id cap) " is blocked by " (:blocked-by cap) " and cannot be :implemented")))
        (when (= :required (:disposition cap))
          (is (some? (:fixture cap)))
          (is (some? (:negative-control cap))))))))

(deftest canonical-curie-franklin-fixture-isolates-homonyms-and-provenance
  (let [m (load-manifest)
        fw01 (get-in m [:canonical-fixtures :F-W01])
        syms (into {} (map (juxt :sym-ref identity) (:contextual-symbols fw01)))
        she-curie (:sym/she-curie syms)
        she-franklin (:sym/she-franklin syms)
        fw02 (get-in m [:canonical-fixtures :F-W02])
        fw03 (get-in m [:canonical-fixtures :F-W03])]
    (is (= "she" (:sign she-curie) (:sign she-franklin)))
    (is (not= (:context she-curie) (:context she-franklin)))
    (is (some #(and (= :llm (:source-kind %))
                    (= :proposed (:expected-curation %)))
              (:assertions fw02)))
    (is (some #(= :false (:expected-truth %)) (:assertions fw03)))))

(deftest exclusions-and-markdown-doc-match-manifest
  (let [m (load-manifest)
        md (slurp (repo-path "docs" "v2" "tests" "wittgenstein-manifest.md"))
        readme (slurp (repo-path "README.md"))
        faq (slurp (repo-path "docs" "faq.md"))
        d01 (first (filter #(= "D01" (:id %)) (:deferrals m)))]
    (is (every? #(= :excluded (:disposition %)) (:exclusions m)))
    (is (every? #(= :deferred (:disposition %)) (:deferrals m)))
    (is (= 0 (get-in m [:summary-counts :by-kimun-status :implemented])))
    (is (some? d01))
    (is (str/includes? (:required-companion d01 "") "12 §1"))
    (is (str/includes? md "vista mínima del circuito"))
    (is (str/includes? readme "not yet implemented"))
    ;; 2026-11-16: the README no longer carries the D0 box; it states the direction and points to the plan
    (is (str/includes? readme "docs/v2/drafts/plan-producto.md"))
    (is (str/includes? readme "no published release"))
    (is (str/includes? readme "`D-G`"))
    (is (not (str/includes? readme "remain open under")))
    (is (str/includes? faq "not yet implemented"))
    (is (str/includes? faq "minimal view of the circuit"))
    (doseq [cap (:capabilities m)]
      (is (str/includes? md (str "`" (:id cap) "`"))
          (str "Markdown manifest must document " (:id cap)))
      (is (str/includes? md (str "`" (name (:fixture cap)) "`"))
          (str "Markdown manifest must reference fixture " (:fixture cap))))))
