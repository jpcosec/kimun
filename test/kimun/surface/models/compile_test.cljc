(ns kimun.surface.models.compile-test
  "The v1 `:template` compiled to a spec 16 view (docs/v2/16 §3.2)."
  (:require [babashka.fs :as fs]
            [clojure.edn :as edn]
            [clojure.string :as str]
            [clojure.test :refer [deftest is testing]]
            [kimun.surface.models.compile :as compile]
            [kimun.surface.models.store :as store]
            [kimun.surface.models.subgraph :as sg]))

(defn- root [& parts] (str (apply fs/path (System/getProperty "user.dir") parts)))

(defn- v1
  "The v1 descriptor of the desk model `nm` (frozen fixture)."
  [nm]
  (edn/read-string (slurp (root "test" "fixtures" "models" "deskops" (str nm ".edn")))))

(defn- r3
  "The R3 selector file of model `nm`."
  [nm]
  (edn/read-string (slurp (root "runs" "research" "2026-10-08" "R3-selectors" "edn" (str nm ".edn")))))

(defn- minimal
  "A tiny v1 descriptor with `template`."
  [template]
  {:model/name "TinyDoc" :model/version 1 :base []
   :fields [{:name "title" :type :string :required true :description "Title."}]
   :template template})

(deftest output-validates-as-spec16
  (testing "the compiled view passes the M1a shape check, under the legacy compat"
    (doseq [nm ["ChecklistDoc" "AtomDoc" "TaskDoc"]
            :let [view (compile/template->spec16 (v1 nm))]]
      (is (= view (sg/validate! view)) nm)
      (is (= :legacy-v1-template (get-in view [:substrate :compat])) nm)
      (is (= :sldb-md/blocks (get-in view [:substrate :parser])) nm)
      (is (contains? (get-in view [:generation :projections]) :markdown/v1) nm)
      (is (seq (get-in view [:anchoring :selector])) nm))))

(deftest selector-equals-the-r3-files
  (testing "the compiled selector is the R3 selector, step for step"
    (doseq [nm ["ChecklistDoc" "AtomDoc" "TaskDoc"]
            :let [view (compile/template->spec16 (v1 nm))
                  got (get-in view [:anchoring :selector])
                  want (:selector (r3 nm))]]
      (is (= (count want) (count got)) nm)
      (doseq [[i a b] (map vector (range) got want)]
        (is (= b a) (str nm " step " i)))
      (is (= (:base (r3 nm)) (:base view)) nm))))

(deftest the-stored-descriptor-is-not-touched
  (testing "serialize of the v1 descriptor is byte for byte the same around the compilation"
    (doseq [nm ["ChecklistDoc" "AtomDoc" "TaskDoc"]
            :let [d (v1 nm)
                  before (store/serialize d)
                  _ (compile/template->spec16 d)
                  after (store/serialize d)]]
      (is (= before after) nm)
      (is (not (contains? d :anchoring)) nm)
      (is (not (contains? d :generation)) nm)
      (is (not (contains? (compile/template->spec16 d) :template)) nm)
      (is (not= before (store/serialize (compile/template->spec16 d))) nm))))

(deftest py-markers-are-still-rejected
  (testing "a `py•` marker does not compile"
    (let [e (try (compile/template->spec16 (minimal "# ⸢rev•title⸥\n\n⸢py•x⸥"))
                 nil
                 (catch clojure.lang.ExceptionInfo e e))]
      (is (some? e))
      (is (= :models/invalid (:type (ex-data e))))
      (is (str/includes? (ex-message e) "py•")))))

(deftest compilation-is-deterministic
  (testing "two compilations are equal, and the view survives an EDN round trip"
    (doseq [nm ["ChecklistDoc" "AtomDoc" "TaskDoc"]
            :let [d (v1 nm)
                  a (compile/template->spec16 d)]]
      (is (= a (compile/template->spec16 d)) nm)
      (is (= a (edn/read-string (pr-str a))) nm))))

(deftest render-marker-lives-in-the-projection
  (testing "a `render` marker is in the projection template and in no selector step"
    (let [view (compile/template->spec16
                (minimal "# ⸢rev•title⸥\n\n## Details\n\n⸢render•details⸥"))
          sel (get-in view [:anchoring :selector])
          marks (mapcat #(concat (when (:marker %) [(:marker %)])
                                 (:props-info %)
                                 (map :marker (:markers %)))
                        sel)]
      (is (str/includes? (get-in view [:generation :projections :markdown/v1 :template])
                         "⸢render•details⸥"))
      (is (= ["title"] (map :name marks)))
      (is (not-any? #(= "render" (:kind %)) marks))
      (is (not (str/includes? (pr-str (map :props sel)) "details"))))))
