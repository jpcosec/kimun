(ns desk.reviews (:require [desk.base :refer [isa tagged has-kind has-fact has-subject has-engine has-actor has-verdict]]))

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

;; Limite de kimun medido (ver REPORT): kimun reconoce el frontmatter solo si no hay una linea
;; en blanco antes del cierre `---`; 44 reviews tienen un escalar YAML entre comillas de varias
;; lineas con lineas en blanco, asi que `:front` llega nil. Para ellas, lectura de respaldo:
;; solo claves de primer nivel de una linea (`clave: valor`) y listas `- x` bajo una clave vacia.
(defn unquote-scalar [s]
  (let [s (clojure.string/trim s)]
    (cond (re-matches #"'(?:[^']|'')*'" s) (clojure.string/replace (subs s 1 (dec (count s))) "''" "'")
          (re-matches #"\"[^\"]*\"" s) (subs s 1 (dec (count s)))
          (re-find #"^['\"]" s) nil
          :else s)))

(defn fallback-front [text]
  (when (and text (clojure.string/starts-with? text "---"))
    (let [fm (take-while #(not= "---" (clojure.string/trimr %)) (rest (clojure.string/split-lines text)))]
      (loop [ls fm cur nil m {}]
        (if (empty? ls)
          m
          (let [l (first ls)]
            (if-let [[_ k v] (re-matches #"([A-Za-z_][A-Za-z0-9_]*):(?: +(.*))?" l)]
              (if (clojure.string/blank? v)
                (recur (rest ls) k (assoc m k []))
                (recur (rest ls) nil (if-let [u (unquote-scalar v)] (assoc m k u) m)))
              (if-let [[_ item] (and cur (re-matches #"- +(.*)" l))]
                (recur (rest ls) cur (update m cur conj (unquote-scalar item)))
                (recur (rest ls) cur m)))))))))

(reading (files "desk/reviews/**/*.md")
  (fn [doc]
    (when-let [f (or (:front doc) (fallback-front (:text doc)))]
      (let [id (term (get f "id"))]
        (isa id Review)
        (each has-kind id f "kind")
        (each has-fact id f "fact")
        (each has-subject id f (if (seq (get f "subject")) "subject" "subject_ref"))
        (each has-engine id f "engine")
        (each has-actor id f "actor")
        (each has-verdict id f "verdict")
        (each tagged id f "tags")))))
