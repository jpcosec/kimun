(ns desk.materializations (:require [desk.base :refer [isa tagged titled materializes target-kind contract-for]]))

(reading (files "desk/materializations/**/*.md")
  (fn [doc]
    (let [f (:front doc)
          id (symbol (get f "id"))
          target (symbol (get f "target_identity"))]
      (isa id Materialization)
      (titled id (get f "title"))
      (target-kind id (symbol (get f "target_kind")))
      (doseq [a (get f "source_atoms")]
        (contract-for id (symbol a))
        (materializes (symbol a) target))
      (doseq [t (get f "tags")] (tagged id (symbol t))))))
