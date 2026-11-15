(require '[kimun.surface.models.subgraph :as sg] '[kimun.surface.models.selector-cst :as c]
         '[kimun.cli.main :as main] '[babashka.fs :as fs] '[clojure.edn :as edn])
(def base (edn/read-string (slurp "test/fixtures/models/ticket.edn")))
(def fx (edn/read-string (slurp "test/fixtures/models/spec16_examples.edn")))
(println "fixture models:" (count (:models fx)) "with cst steps in selectors:"
         (count (filter #(some c/cst-step? (get-in % [:anchoring :selector])) (:models fx))))
(println "capability examples cst-step?:" (mapv (fn [k] [(:id k) (c/cst-step? (:selector-example k))]) (:capabilities fx)))
(def steps [{:key "notes" :want :find :where {:outer-tag "h2" :regex "^\\QNotes\\E$"}}
            {:key "items" :outer-type "list" :preserve-nesting true}
            {:key "matrix" :want :table :outer-type "pipe_table"}])
(def d (-> (get (:models fx) 0) (assoc :model/name "CstDoc" :base [] :contract/digest nil)
           (assoc-in [:anchoring :selector] steps)
           (assoc-in [:anchoring :fields] [])))
(println "cst-step?" (mapv c/cst-step? steps))
(doseq [s steps]
  (println "validate!" (pr-str (:key s))
           (try (sg/validate! (assoc-in d [:anchoring :selector] [s])) :ok
                (catch Exception e (ex-message e)))))
(let [p (str (fs/create-temp-dir)) env {"USER" "t"}
      _ (main/run ["stores" "init" "--name" "p"] env p)
      f (str p "/cst.edn") _ (spit f (pr-str d))
      e (:envelope (main/run ["models" "define" "-f" f] env p))]
  (println "define:" (pr-str (select-keys e [:ok :error :message :data]))))
