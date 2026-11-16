(ns desk.tasks (:require [desk.base :refer [isa tagged has-status depends-on uses has-pill at-node has-type touches]]))

(reading (files "desk/**/task-*.md")
  (fn [doc]
    (let [f (:front doc)
          id (symbol (get f "id"))]
      (isa id Task)
      (when-let [s (get f "status")] (has-status id (symbol s)))
      (when (seq (get f "current_node")) (at-node id (symbol (get f "current_node"))))
      (when (seq (get f "task_type")) (has-type id (symbol (get f "task_type"))))
      (doseq [d (get f "depends_on")] (depends-on id (symbol d)))
      (doseq [a (get f "atoms")] (uses id (symbol a)))
      (doseq [p (get f "pills")] (has-pill id (symbol p)))
      (doseq [x (get f "files")] (touches id (symbol x)))
      (doseq [t (get f "tags")] (tagged id (symbol t))))))
