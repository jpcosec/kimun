(ns kimun.surface.models.subgraph-test
  "Spec 16 descriptors (docs/v2/16 §3.4, §4): shape, hierarchy, composition."
  (:require [babashka.fs :as fs]
            [clojure.edn :as edn]
            [clojure.string :as str]
            [clojure.test :refer [deftest is testing]]
            [kimun.surface.models.subgraph :as sg]))

(def ^:private models
  "The seven example models of the spec 16 fixture."
  (:models (edn/read-string
            (slurp (str (fs/path (System/getProperty "user.dir")
                                 "test" "fixtures" "models" "spec16_examples.edn"))))))

(def ^:private index (sg/index-models models))

(defn- invalid-msg
  "Message of the `:models/invalid` raised by `f`, or nil if it raises nothing else."
  [f]
  (try (f) nil
       (catch clojure.lang.ExceptionInfo e
         (when (= :models/invalid (:type (ex-data e))) (ex-message e)))))

(defn- m
  "Minimal valid spec 16 model `nm` with `base` and `fields`."
  [nm base fields]
  {:model/name nm :model/version 1 :base base :document-role :source
   :substrate {:parser :treesitter :compat :strict}
   :anchoring {:fields fields :selector []}
   :generation {:projections {:markdown/default {:format :markdown}}}})

(defn- fld
  "Field `n` of type `t`, required or not."
  [n t req]
  {:name n :type t :required req :description "d"})

(deftest shape
  (doseq [d models] (is (= d (sg/validate! d))))
  (let [d (first models)]
    (doseq [[mutate key-name]
            [[#(dissoc % :anchoring) ":anchoring"]
             [#(assoc-in % [:substrate :compat] :loose) ":compat"]
             [#(assoc % :document-role :other) ":document-role"]]]
      (is (str/includes? (or (invalid-msg #(sg/validate! (mutate d))) "") key-name)
          key-name))))

(deftest hierarchy-and-isa
  (let [h (sg/hierarchy models)]
    (is (sg/isa-model? h "ResumenTecnicoDoc" "ResumenDoc"))
    (is (sg/isa-model? h "ResumenTecnicoDoc" "PrimitiveDoc"))
    (is (not (sg/isa-model? h "PrimitiveDoc" "ResumenTecnicoDoc"))))
  (is (str/includes? (or (invalid-msg #(sg/hierarchy [(m "A" ["B"] []) (m "B" ["A"] [])])) "") "cycle"))
  (is (str/includes? (or (invalid-msg #(sg/hierarchy [(m "A" ["Ghost"] [])])) "") "Ghost")))

(deftest selector-composition
  (let [steps (sg/compose-selector index "ResumenTecnicoDoc")]
    (is (= (range 8) (map :outer-index steps)))
    (is (= "^\\QSummary\\E$" (:regex (steps 2))))
    (is (= "^\\QComplexity\\E$" (:regex (steps 4)))))
  (let [step (fn [t] {:outer-index 9 :outer-type t :handler :text})
        dm (sg/index-models
            [(assoc-in (m "A" [] []) [:anchoring :selector] [(step "a")])
             (assoc-in (m "B" ["A"] []) [:anchoring :selector] [(step "b")])
             (assoc-in (m "C" ["A"] []) [:anchoring :selector] [(step "c")])
             (assoc-in (m "D" ["B" "C"] []) [:anchoring :selector] [(step "d")])])]
    (is (= ["A" "B" "C"] (sg/ancestors-of dm "D")))
    (is (= ["a" "b" "c" "d"] (map :outer-type (sg/compose-selector dm "D"))))
    (is (= [0 1 2 3] (map :outer-index (sg/compose-selector dm "D"))))))

(deftest inherited-restrictions
  (let [p (m "P" [] [(fld "x" :string true)])
        chk (fn [child] (invalid-msg #(sg/check-composition! (sg/index-models [p child]) "K")))]
    (is (str/includes? (or (chk (m "K" ["P"] [(fld "x" :string false)])) "") "required"))
    (is (str/includes? (or (chk (m "K" ["P"] [(fld "x" :int true)])) "") "type"))
    (is (nil? (chk (m "K" ["P"] [(fld "x" :string true) (fld "y" :int false)]))))
    (is (nil? (chk (m "K" [] [(fld "x" :int false)]))) "a root class inherits nothing")))

(deftest projections
  (let [before (sg/compose-selector index "ResumenTecnicoDoc")
        projs (sg/compose-projections index "ResumenTecnicoDoc")
        over (sg/index-models
              [(m "P" [] []) (assoc-in (m "K" ["P"] []) [:generation :projections]
                                       {:markdown/default {:format :json}})])]
    (is (= #{:markdown/default :summary/technical} (set (keys projs))))
    (is (= :json (get-in (sg/compose-projections over "K") [:markdown/default :format])))
    (is (= :markdown (get-in (sg/compose-projections over "P") [:markdown/default :format])))
    (is (= before (sg/compose-selector index "ResumenTecnicoDoc")))))

(deftest blocking
  (is (= #{:F10} (sg/blocked-by (index "ResumenDigestView"))))
  (is (= #{} (sg/blocked-by (index "ChecklistDoc")))))
