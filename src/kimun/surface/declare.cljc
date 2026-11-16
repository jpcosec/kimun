(ns kimun.surface.declare
  "Evaluator of declaration files (docs/v2/drafts/diseno-evaluar.md, cut 1:
   read). A declaration file is real Clojure, evaluated in an isolated `sci`
   context whose only vocabulary beyond `clojure.core` is `relation`, `at` and
   `files`; evaluating it produces propositions `(R A B)` in a context and
   writes nothing anywhere: this namespace touches no disk, network or store.

   Entry point: `read-declarations`. Its result is
   `{:context s :relations {s {:doc s}} :propositions [p …]}` where a
   proposition is `{:r s :a node :b node :context s :at {:file :line} :id s}`
   (plus `:also-at` when the same proposition is written again).

   The language of a file
   - `(ns x.y)` must be the first form; it fixes the context. Any other clause
     of `ns` is rejected.
   - `(relation name \"doc\")` defines a relation; afterwards `(name A B)`
     records a proposition. Exactly two arguments; the docstring is mandatory.
   - `(at path & locator)` is `{:anchor {:path path :locator [..]}}` and
     `(files pattern)` is `{:files pattern}`: neither opens a file.

   BARE SYMBOL versus BOUND SYMBOL, the exact rule. An argument of a relation
   form that is a symbol is a TERM (the symbol itself, dots included:
   `UI.view.dashboard`, never resolved as a class) UNLESS it is bound in the
   code, in which case it stands for its VALUE. Bound means: (1) a local of the
   enclosing code (`let`, `doseq`, `for`, `fn`/`defn` parameters; seen through
   `&env`), or (2) a var the file itself defined with `def`/`defn` (resolved in
   the sci namespace of the file's context; relation names, being macros, are not values).
   `clojure.core` and the vocabulary never bind: `name`, `type` or `first` as a
   bare symbol are terms. A bound symbol whose value is a symbol is a term; a
   string, number, keyword or boolean is a literal; a proposition is nested by
   `:id`. Arguments that are lists are evaluated and must yield a node
   (proposition, `at`, `files`) or a scalar literal; nil, functions, vectors
   and maps are rejected (`:declare/bad-node`): a map written by the user is
   never taken for a node. Vectors and maps are valid inside `(at …)` only.

   Every argument is stored as a TAGGED node, identical in EDN and JSON:
   `{:term \"UI.component\"}` (a string, not a symbol), `{:proposition id}`,
   `{:anchor …}`, `{:files …}`, or the bare scalar literal. `:r` and `:context`
   are strings.

   A proposition used as an argument is stored inside the other as
   `{:proposition id}` and still appears in the list as its own proposition.
   `:id` is the canonical digest (`kimun.kernel.canon/digest`, default host) of
   `{:r :a :b :context}`; `:at` is not part of it. The same proposition written
   twice is returned once (first `:at`; later ones in `:also-at`), in order of
   evaluation.

   Errors are `kimun.kernel.err/raise` with `:declare/*` types, always carrying
   `:file` and `:line`: `syntax`, `relation-redefined`, `no-context`, `ns-clauses`, `unresolved`,
   `arity`, `bad-relation`, `bad-node`, `bad-arg`, `eval`."
  (:require [clojure.string :as str]
            [sci.core :as sci]
            [kimun.host.default :as host-default]
            [kimun.kernel.canon :as canon]
            [kimun.kernel.err :as err]))

(defn- fail
  "Raises the declared error `type` with `msg`, `line` and extra `data`."
  [type msg line data]
  (err/raise type msg (assoc data :line line)))

(defn- proposition? [v] (boolean (and (map? v) (::proposition (meta v)))))

(defn- node? [v] (boolean (and (map? v) (::node (meta v)))))

(defn- node-of
  "The tagged stored form of the evaluated argument `v` (line `line`, relation `r`)."
  [r line v]
  (cond
    (symbol? v) {:term (str v)}
    (proposition? v) {:proposition (:id v)}
    (node? v) (with-meta v nil)
    (or (string? v) (number? v) (keyword? v) (boolean? v)) v
    :else (fail :declare/bad-node
                (str "`" r "`: an argument must be a term, a scalar literal (string, number, keyword, boolean), a proposition, (at …) or (files …); got " (pr-str v))
                line {:relation r})))

(defn- emit!
  "Builds, registers and returns the proposition `(r a b)` (values already
   evaluated). State lives in the atom `st` of this evaluation."
  [st r line a b]
  (let [{:keys [context file host]} @st
        a' (node-of r line a)
        b' (node-of r line b)
        id (try (canon/digest host {:r (str r) :a a' :b b' :context (str context)})
                (catch clojure.lang.ExceptionInfo e
                  (fail :declare/bad-node (str "`" r "`: cannot be hashed: " (ex-message e)) line {:relation r})))
        at {:file file :line line}]
    (if-let [i (get-in @st [:index id])]
      (do (swap! st update-in [:props i :also-at] (fnil conj []) at)
          (nth (:props @st) i))
      (let [p (with-meta {:r (str r) :a a' :b b' :context (str context) :at at :id id} {::proposition true})]
        (swap! st (fn [s] (-> s (update :props conj p) (assoc-in [:index id] (count (:props s))))))
        p))))

(defn- arg-form
  "The form that stands for one argument of a relation use: bare symbols
   become quoted terms, bound symbols and everything else stay as written."
  [context env resolver arg]
  (if (symbol? arg)
    (let [v (when-not (contains? env arg) (resolver arg))
          user-var? (and v (= context (some-> v meta :ns str symbol)) (not (:macro (meta v))))]
      (if (or (contains? env arg) user-var?) arg (list 'quote arg)))
    arg))

(defn- expand
  "Macro body of a relation use: checks arity and returns the form that emits."
  [st r form env args resolver]
  (let [line (or (:line (meta form)) 0)]
    (when (not= 2 (count args))
      (fail :declare/arity (str "relation `" r "` takes exactly two arguments (A and B), got " (count args)) line {:relation r :arity (count args)}))
    (list 'kimun.declare.impl/emit (list 'quote r) line (mapv #(arg-form (:context @st) env resolver %) args) st)))

(defn- define!
  "Registers relation `r` with `doc` (checked at evaluation time)."
  [st r doc line]
  (when-not (and (string? doc) (not (str/blank? doc)))
    (fail :declare/bad-relation (str "relation `" r "` needs a non-empty docstring") line {:relation r}))
  (when-let [old (get-in @st [:relations (str r) :doc])]
    (when (not= old doc)
      (fail :declare/relation-redefined (str "relation `" r "` is already defined with another docstring") line {:relation r})))
  (swap! st assoc-in [:relations (str r)] {:doc doc})
  nil)

(defn- relation-macro
  "The sci macro `relation`: `(relation name \"doc\")`."
  [st]
  (with-meta
    (fn [form _env & args]
      (let [line (or (:line (meta form)) 0)
            [r doc] args]
        (when (not= 2 (count args))
          (fail :declare/bad-relation "`relation` takes a name and a docstring: (relation name \"doc\")" line {:arity (count args)}))
        (when-not (and (symbol? r) (nil? (namespace r)))
          (fail :declare/bad-relation "the name of a relation must be a plain symbol" line {:name (pr-str r)}))
        (list 'do
              (list 'kimun.declare.impl/define (list 'quote r) doc line st)
              (list 'defmacro r '[& args]
                    (list 'kimun.declare.impl/expand st (list 'quote r) '&form '&env 'args 'resolve)))))
    {:sci/macro true}))

(defn- at-fn
  "`(at path & locator)` → `{:anchor {:path path :locator [..]}}`."
  [path & locator]
  (when-not (string? path)
    (err/raise :declare/bad-arg "`at` needs the path of the file as a string" {:got (pr-str path)}))
  (with-meta {:anchor {:path path :locator (vec locator)}} {::node true}))

(defn- files-fn
  "`(files pattern)` → `{:files pattern}`, one node, unexpanded."
  [pattern]
  (when-not (and (string? pattern) (not (str/blank? pattern)))
    (err/raise :declare/bad-arg "`files` needs a pattern as a non-empty string" {:got (pr-str pattern)}))
  (with-meta {:files pattern} {::node true}))

(defn- make-ctx
  "A fresh isolated sci context; nothing of the host is exposed. The
   vocabulary lives in the namespace of the file's `context` (sci evaluates in
   the namespace of the calling process unless told otherwise, so the
   evaluation is pinned to this one by `read-declarations`)."
  [st context]
  (sci/init
   {:namespaces {context {'relation (relation-macro st) 'at at-fn 'files files-fn}
                 'kimun.declare.impl {'emit (fn [r line args st'] (emit! st' r line (first args) (second args)))
                                      'define (fn [r doc line st'] (define! st' r doc line))
                                      'expand expand}}
    :deny '[slurp spit load-file load-string require use import]}))

(defn- declared-in
  "The first ex-data with a `:declare/*` type along the cause chain of `e`."
  [e]
  (loop [x e]
    (cond (nil? x) nil
          (some-> (ex-data x) :type namespace (= "declare")) (ex-data x)
          :else (recur (ex-cause x)))))

(defn- rethrow
  "Turns anything thrown while evaluating a form into a declared error with
   file and line (the line of the failing form when sci knows none)."
  [e file form-line]
  (let [d (declared-in e)
        sci-d (ex-data e)
        msg (or (ex-message (some #(when (some-> (ex-data %) :type namespace (= "declare")) %)
                                  (take-while some? (iterate ex-cause e))))
                (ex-message e))]
    (if d
      (err/raise (:type d) (str file ":" (:line d form-line) ": " msg) (assoc (dissoc d :type) :file file :line (or (:line d) form-line)))
      (let [line (or (:line sci-d) form-line)
            unresolved? (str/includes? (str (ex-message e)) "Unable to resolve")]
        (err/raise (if unresolved? :declare/unresolved :declare/eval)
                   (str file ":" line ": " (ex-message e) (when unresolved? " (not a relation defined with `relation` in this file, nor part of the vocabulary)"))
                   {:file file :line line})))))

(defn- ns-name-of
  "The context symbol of the `(ns …)` form, or a declared error."
  [file form]
  (let [line (or (:line (meta form)) 1)
        [_ nm & more] form]
    (when-not (and (symbol? nm) (nil? (namespace nm)))
      (fail :declare/ns-clauses "`ns` needs a plain symbol name" line {:file file}))
    (when (some #(not (or (string? %) (map? %))) more)
      (fail :declare/ns-clauses "`ns` accepts only a name, a docstring and an attribute map in a declaration file" line {:file file}))
    nm))

(defn read-declarations
  "Evaluates the declaration file whose text is `text` (named `file` in
   positions) and returns `{:context :relations :propositions}`; raises a
   `:declare/*` error carrying `:file` and `:line` otherwise. `host` defaults
   to the standard host (it only supplies the hasher of the `:id`s)."
  ([text file] (read-declarations host-default/host text file))
  ([host text file]
   (let [st (atom {:file file :host host :context nil :relations {} :props [] :index {}})
         scan-ctx (sci/init {})
         rdr (sci/reader (str text))
         eof (Object.)
         next-form (fn []
                     (try (sci/parse-next scan-ctx rdr {:eof eof})
                          (catch Exception e
                            (let [line (or (:line (ex-data e)) 1)]
                              (err/raise :declare/syntax (str file ":" line ": " (ex-message e)) {:file file :line line})))))]
     ;; first pass: syntax, and the context (the `ns` must be the only and first form)
     (loop [first? true]
       (let [form (next-form)]
         (when-not (identical? form eof)
           (let [line (or (:line (meta form)) 1)
                 ns-form? (and (seq? form) (= 'ns (first form)))]
             (cond
               (and first? (not ns-form?))
               (err/raise :declare/no-context
                          (str file ":" line ": the first form must be (ns context.name): a proposition without a context is not a proposition")
                          {:file file :line line})

               (and ns-form? (not first?))
               (err/raise :declare/no-context (str file ":" line ": a second `ns`: one context per file") {:file file :line line})

               ns-form?
               (swap! st assoc :context (try (ns-name-of file form)
                                             (catch clojure.lang.ExceptionInfo e (rethrow e file line)))))
             (recur false)))))
     (when-not (:context @st)
       (err/raise :declare/no-context (str file ":1: the file has no (ns context.name)") {:file file :line 1}))
     ;; second pass: evaluate the whole text pinned to the context namespace
     (let [context (:context @st)
           ctx (make-ctx st context)]
       (try (sci/eval-string+ ctx (str text) {:ns (sci/create-ns context nil)})
            (catch Throwable e (rethrow e file 1))))
     (let [{:keys [context relations props]} @st]
       {:context (str context)
        :relations relations
        :propositions (mapv #(vary-meta % dissoc ::proposition) props)}))))
