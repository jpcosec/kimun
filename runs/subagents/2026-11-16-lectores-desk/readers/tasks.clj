(ns desk.tasks (:require [desk.base :refer [isa tagged has-status depends-on uses has-pill at-node has-type touches]]))

;; Referencias: doc:Model:id@sha256:HASH, doc:Model:id y Model:id pasan a id.
;; Todo lo demas queda igual.
(defn ref-id [v]
  (if-let [m (re-matches #"(?:doc:)?[A-Z][A-Za-z0-9]*:([^@\s]+)(?:@sha256:[0-9a-fA-F]+)?" v)]
    (second m)
    v))

(defn basename [path]
  (let [n (last (clojure.string/split path #"/"))]
    (if (clojure.string/ends-with? n ".md") (subs n 0 (- (count n) 3)) n)))

;; Valores de una clave: una cadena no vacia o una lista de cadenas no vacias.
(defn vals-of [f k]
  (let [v (get f k)]
    (cond (string? v) (if (clojure.string/blank? v) [] [v])
          (sequential? v) (filter #(and (string? %) (not (clojure.string/blank? %))) v)
          :else [])))

(defn term [v] (symbol (ref-id v)))

;; (each has-kind id f "kind"): una proposicion por valor de la clave. Es macro porque
;; las relaciones lo son: no se pueden pasar como valor.
(defmacro each [rel id f k]
  `(doseq [v# (vals-of ~f ~k)] (~rel ~id (term v#))))

;; --- fin de los helpers compartidos

;; Referencias: doc:Model:id@sha256:HASH, doc:Model:id y Model:id pasan a id.
;; Todo lo demas queda igual.
(defn ref-id [v]
  (if-let [m (re-matches #"(?:doc:)?[A-Z][A-Za-z0-9]*:([^@\s]+)(?:@sha256:[0-9a-fA-F]+)?" v)]
    (second m)
    v))

(defn basename [path]
  (let [n (last (clojure.string/split path #"/"))]
    (if (clojure.string/ends-with? n ".md") (subs n 0 (- (count n) 3)) n)))

;; Valores de una clave: una cadena no vacia o una lista de cadenas no vacias.
(defn vals-of [f k]
  (let [v (get f k)]
    (cond (string? v) (if (clojure.string/blank? v) [] [v])
          (sequential? v) (filter #(and (string? %) (not (clojure.string/blank? %))) v)
          :else [])))

(defn term [v] (symbol (ref-id v)))

;; (each has-kind id f "kind"): una proposicion por valor de la clave. Es macro porque
;; las relaciones lo son: no se pueden pasar como valor.
(defmacro each [rel id f k]
  `(doseq [v# (vals-of ~f ~k)] (~rel ~id (term v#))))

(reading (files "desk/tasks/**/task-*.md")
  (fn [doc]
    (when-let [f (:front doc)]
      (let [id (term (get f "id"))]
        (isa id Task)
        (each has-status id f "status")
        (each at-node id f "current_node")
        (each has-type id f "task_type")
        (each depends-on id f "depends_on")
        (each uses id f "atoms")
        (each has-pill id f "pills")
        (each touches id f "files")
        (each tagged id f "tags")))))
