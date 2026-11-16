(ns desk.runtimes (:require [desk.base :refer [isa tagged has-status has-kind has-binary has-model-flag has-fallback-models-flag has-fallback-models-join has-tools-flag has-tools-join has-system-prompt-flag has-system-prompt-delivery has-session-flag has-extra-args has-unsupported-role-fields]]))

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

(reading (files "desk/runtimes/**/*.md")
  (fn [doc]
    (when-let [f (:front doc)]
      (let [id (term (get f "id"))]
        (isa id Runtime)
        (each has-status id f "status")
        (each has-kind id f "kind")
        (each has-binary id f "binary")
        (each has-model-flag id f "model_flag")
        (each has-fallback-models-flag id f "fallback_models_flag")
        (each has-fallback-models-join id f "fallback_models_join")
        (each has-tools-flag id f "tools_flag")
        (each has-tools-join id f "tools_join")
        (each has-system-prompt-flag id f "system_prompt_flag")
        (each has-system-prompt-delivery id f "system_prompt_delivery")
        (each has-session-flag id f "session_flag")
        (each has-extra-args id f "extra_args")
        (each has-unsupported-role-fields id f "unsupported_role_fields")
        (each tagged id f "tags")))))
