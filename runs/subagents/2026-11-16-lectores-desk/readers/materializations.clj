(ns desk.materializations (:require [desk.base :refer [isa tagged titled materializes target-kind contract-for]]))

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

(reading (files "desk/materializations/**/*.md")
  (fn [doc]
    (when-let [f (:front doc)]
      (let [id (term (get f "id"))]
        (isa id Materialization)
        (when (seq (get f "title")) (titled id (get f "title")))
        (each target-kind id f "target_kind")
        (doseq [a (get f "source_atoms")]
          (contract-for id (term a))
          (when (seq (get f "target_identity"))
            (materializes (term a) (term (get f "target_identity")))))
        (each tagged id f "tags")))))
