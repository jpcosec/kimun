(ns desk.base)

(relation isa "A es de la clase B.")
(relation tagged "A lleva la etiqueta B.")
(relation titled "A se titula B.")
(relation answers "A responde la pregunta B (five_wh_one_plus).")
(relation has-status "A esta en el estado B.")
(relation depends-on "A no puede cerrarse antes que B.")
(relation uses "A se apoya en el atomo B.")
(relation has-pill "La tarea A lleva la pill B.")
(relation at-node "La tarea A esta en el nodo B del ritual.")
(relation has-type "La tarea A es del tipo B.")
(relation touches "A toca el archivo B.")
(relation materializes "El atomo A se materializa en B.")
(relation target-kind "El contrato A materializa en una cosa de tipo B.")
(relation contract-for "El contrato A materializa el atomo B.")
(relation has-owner-repo "La tarea A pertenece al repositorio B.")
(relation has-node-role "La tarea A tiene el papel B en su arbol de tareas.")
(relation has-requires "A requiere B.")
(relation has-parent-id "A es hija de B.")
(relation has-children "A tiene por hija a B.")
(relation has-kind "A es del tipo (kind) B.")
(relation has-sender-project "La nota A la envia el proyecto B.")
(relation has-target-project "La nota A va al proyecto B.")
(relation has-acknowledged-by "La nota A fue acusada por B.")
(relation has-subject "A trata sobre B.")
(relation has-severity "La regla A tiene severidad B.")
(relation has-moment "La regla A se evalua en el momento B.")
(relation relation-source "La relacion A parte de B.")
(relation relation-target "La relacion A llega a B.")
(relation relation-type "La relacion A es del tipo B.")
(relation has-name "A se llama B.")
(relation has-direction "La relacion A tiene direccion B.")
(relation has-cardinality "La relacion A tiene cardinalidad B.")
(relation has-axis "La relacion A esta en el eje B.")
(relation has-source-types "La relacion A admite como origen el tipo B.")
(relation has-target-types "La relacion A admite como destino el tipo B.")
(relation has-path "A esta en la ruta B.")
(relation has-fact "La review A trata el hecho B.")
(relation has-engine "A usa el motor B.")
(relation has-actor "A la hizo el actor B.")
(relation has-verdict "La review A tiene el veredicto B.")
(relation has-model "El rol A usa el modelo B.")
(relation has-fallback-models "El rol A tiene de reserva el modelo B.")
(relation has-tools "El rol A puede usar la herramienta B.")
(relation has-system-prompt-mode "A tiene el modo de prompt de sistema B.")
(relation has-default-context "El rol A parte con el contexto B.")
(relation has-binary "El runtime A ejecuta el binario B.")
(relation has-model-flag "El runtime A pasa el modelo con B.")
(relation has-fallback-models-flag "El runtime A pasa los modelos de reserva con B.")
(relation has-fallback-models-join "El runtime A une los modelos de reserva con B.")
(relation has-tools-flag "El runtime A pasa las herramientas con B.")
(relation has-tools-join "El runtime A une las herramientas con B.")
(relation has-system-prompt-flag "El runtime A pasa el prompt de sistema con B.")
(relation has-system-prompt-delivery "El runtime A entrega el prompt de sistema por B.")
(relation has-session-flag "El runtime A retoma sesion con B.")
(relation has-extra-args "El runtime A pasa el argumento B.")
(relation has-scope "El tablero A es del ambito B.")
(relation has-tasks "El tablero A lista la tarea B.")
(relation has-unsupported-role-fields "El runtime A no soporta el campo de rol B.")

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
