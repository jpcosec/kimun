(ns desk.atoms (:require [desk.base :refer [isa tagged titled answers]]))

(reading (files "desk/atoms/**/*.md")
  (fn [doc]
    (let [f (:front doc)
          id (symbol (get f "id"))]
      (isa id Atom)
      (titled id (get f "title"))
      (when-let [w (get f "five_wh_one_plus")] (answers id (symbol w)))
      (doseq [t (get f "tags")] (tagged id (symbol t))))))
