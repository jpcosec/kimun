(ns kimun.surface.declare
  "Evaluator of declaration files (docs/v2/drafts/diseno-evaluar.md, cut 1:
   read). A declaration file is real Clojure, evaluated in an isolated `sci`
   context whose only vocabulary beyond `clojure.core` is `relation`, `at`, `files`,
   `generated` and `reading`; evaluating it produces propositions `(R A B)` in a context and
   writes nothing anywhere: this namespace touches no disk, network or store.

   Entry points: `read-declarations` (one file without requires) and
   `read-project` (a set of files whose contexts require each other). The result
   is `{:context s :relations {s {:doc s}} :propositions [p …] :generated [{:path :at
   :context}]}` where a
   proposition is `{:r s :a node :b node :context s :at {:file :line} :id s}`
   (plus `:also-at` when the same proposition is written again). `:r` is
   `\"defining-context/name\"`: a relation belongs to the context that defines it,
   so `:context` is the context that WRITES the proposition and `:r` the one
   that defined the relation (they differ when it was required).

   The language of a file
   - `(ns x.y)` must be the first form; it fixes the context. Its only clause
     is `(:require c [c :as o] [c :refer [relation …]])`, with contexts of other
     files of the same set; anything else is `:declare/ns-clauses`. Only
     relations are imported, never the `def`s of the other context.
   - `(relation name \"doc\")` defines a relation; afterwards `(name A B)`
     records a proposition. Exactly two arguments; the docstring is mandatory.
   - `(at path & locator)` is `{:anchor {:path path :locator [..]}}` and
     `(files pattern)` is `{:files pattern}`: neither opens a file.
   - `(generated path recipe)` (docs/v2/drafts/diseno-generar.md) is the node
     `{:generated {:path path}}` and registers `recipe`, a function of the file, as the
     one that produces `path` (relative to the project, no `..`). Nothing is run here:
     the recipes travel in the metadata of each file result (`recipes` reads them) and
     `kimun.surface.generate` runs them. The recipe is not part of the node nor of the `:id`.
   - `(reading (files pattern) reader)` (docs/v2/drafts/diseno-leer.md) registers `reader`, a
     function of one file (`fn?`), for the project files `pattern` reaches. Evaluating opens no
     file and runs no reader: the readers travel in the metadata of each file result (`readers`
     reads them), the result's data has `:readings [{:pattern :at :context}]`, and
     `kimun.surface.reading` runs them later through `collect`: what the relation uses inside a
     reader emit goes to the sink of that call (`*sink*`), not to the closed state of the file.
     The form returns nil.

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
   `:file` and `:line`: `syntax`, `relation-redefined`, `no-context`, `reserved-context`,
   `ns-clauses`, `unresolved`, `arity`, `bad-relation`, `bad-node`, `bad-arg`, `bad-generated`, `bad-reading`,
   `bad-locator`
   (the shape of every `(at …)` is checked after the file evaluates, with
   `kimun.surface.declared-anchors/translate`), `timeout` (a file that evaluates longer than
   the `:timeout-ms` option, default `default-timeout-ms`), `eval`; of a set: `duplicate-context`,
   `unknown-context`, `require-cycle`, `unknown-relation`, `relation-conflict`,
   `generated-conflict` (one path, two different recipes)."
  (:require [clojure.string :as str]
            [sci.core :as sci]
            [kimun.surface.declared-anchors :as declared-anchors]
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

(def ^:dynamic *sink*
  "When bound to an atom `{:props [] :index {}}`, the propositions a relation use emits go there
   and not to the state of the file (readers run AFTER the file finished evaluating:
   `docs/v2/drafts/diseno-leer.md`); see `collect`."
  nil)

(defn- emit!
  "Builds, registers and returns the proposition `(r a b)` (values already
   evaluated). The context, file and hasher come from the atom `st` of this
   evaluation; the proposition is registered in `st` or, inside `collect`, in its sink."
  [st r line a b]
  (let [{:keys [context file host]} @st
        tgt (or *sink* st)
        a' (node-of r line a)
        b' (node-of r line b)
        id (try (canon/digest host {:r (str r) :a a' :b b' :context (str context)})
                (catch clojure.lang.ExceptionInfo e
                  (fail :declare/bad-node (str "`" r "`: cannot be hashed: " (ex-message e)) line {:relation r})))
        at {:file file :line line}]
    (if-let [i (get-in @tgt [:index id])]
      (do (swap! tgt update-in [:props i :also-at] (fnil conj []) at)
          (nth (:props @tgt) i))
      (let [p (with-meta {:r (str r) :a a' :b b' :context (str context) :at at :id id} {::proposition true})]
        (swap! tgt (fn [s] (-> s (update :props conj p) (assoc-in [:index id] (count (:props s))))))
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
  (when-let [from (get-in @st [:referred (str r)])]
    (fail :declare/relation-conflict
          (str "relation `" r "` is defined in context `" (:context @st) "` but it is also imported from context `" from "` by :refer: rename one of them")
          line {:relation (str r) :contexts [(str (:context @st)) from]}))
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
                    (list 'kimun.declare.impl/expand st (list 'quote (symbol (str (:context @st)) (str r))) '&form '&env 'args 'resolve)))))
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

(defn- generated-path
  "The normalized path (no leading `./`) of a `generated` at `file`:`line`, or `declare/bad-generated`
   when it is not a relative path of the project: a string, not blank, not absolute, no `..`
   or empty segments, not a folder."
  [file line path]
  (let [bad (fn [why] (fail :declare/bad-generated
                            (str file ":" line ": `generated` needs a file path inside the project, relative to its root: " why)
                            line {:file file :path (pr-str path)}))]
    (when-not (string? path) (bad (str "not a string (" (pr-str path) ")")))
    (let [p (str/replace path #"^(\./)+" "")]
      (when (or (str/blank? p) (str/starts-with? p "/") (re-find #"^[A-Za-z]:" p) (str/includes? p "\\"))
        (bad (pr-str path)))
      (when (or (str/ends-with? p "/") (some #(contains? #{"" "." ".."} %) (str/split p #"/" -1)))
        (bad (str (pr-str path) " (empty, `.` or `..` segment, or a folder)")))
      p)))

(defn- generate!
  "Registers `recipe` for `path` and returns the node `{:generated {:path p}}`."
  [st path recipe line]
  (let [file (:file @st)
        p (generated-path file line path)]
    (when-not (fn? recipe)
      (fail :declare/bad-generated
            (str file ":" line ": the recipe of `" p "` must be a function (a `fn` or a `defn` of this file), got " (pr-str recipe))
            line {:file file :path p}))
    (swap! st update :generated conj {:path p :at {:file file :line line} :context (str (:context @st)) :fn recipe})
    (with-meta {:generated {:path p}} {::node true})))

(defn- generated-macro
  "The sci macro `generated`: `(generated path recipe)`; a macro only to know its line."
  [st]
  (with-meta
    (fn [form _env & args]
      (let [line (or (:line (meta form)) 0)]
        (when (not= 2 (count args))
          (fail :declare/bad-generated (str (:file @st) ":" line ": `generated` takes a path and a recipe: (generated \"docs/x.md\" recipe)")
                line {:file (:file @st) :arity (count args)}))
        (list 'kimun.declare.impl/generated st (first args) (second args) line)))
    {:sci/macro true}))

(defn- reading!
  "Registers the reader `f` of the files `conjunto` (a `(files pattern)` node) and returns nil.
   Nothing is opened or run here."
  [st conjunto f line]
  (let [file (:file @st)
        bad (fn [why] (fail :declare/bad-reading (str file ":" line ": `reading` " why) line {:file file}))]
    (when-not (and (map? conjunto) (string? (:files conjunto)) (node? conjunto))
      (bad (str "needs a (files \"pattern\") as its first argument, got " (pr-str conjunto))))
    (when-not (fn? f)
      (bad (str "needs a function of the file as its second argument (a `fn` or a `defn` of this file), got " (pr-str f))))
    (swap! st update :readings conj {:pattern (:files conjunto) :at {:file file :line line} :context (str (:context @st)) :fn f :st st})
    nil))

(defn- reading-macro
  "The sci macro `reading`: `(reading (files pattern) reader)`; a macro only to know its line."
  [st]
  (with-meta
    (fn [form _env & args]
      (let [line (or (:line (meta form)) 0)]
        (when (not= 2 (count args))
          (fail :declare/bad-reading (str (:file @st) ":" line ": `reading` takes a set of files and a reader: (reading (files \"docs/*.md\") (fn [doc] …))")
                line {:file (:file @st) :arity (count args)}))
        (list 'kimun.declare.impl/reading st (first args) (second args) line)))
    {:sci/macro true}))

(defn- make-ctx
  "A fresh isolated sci context; nothing of the host is exposed. The
   vocabulary lives in the namespace of the file's `context` (sci evaluates in
   the namespace of the calling process unless told otherwise, so the
   evaluation is pinned to this one by `read-declarations`)."
  [st context]
  (sci/init
   {:namespaces {context {'relation (relation-macro st) 'at at-fn 'files files-fn 'generated (generated-macro st) 'reading (reading-macro st)}
                 'kimun.declare.impl {'emit (fn [r line args st'] (emit! st' r line (first args) (second args)))
                                      'define (fn [r doc line st'] (define! st' r doc line))
                                      'generated (fn [st' path recipe line] (generate! st' path recipe line))
                                      'reading (fn [st' conjunto f line] (reading! st' conjunto f line))
                                      'expand expand
                                      'expand-as (fn [r form env args resolver] (expand st r form env args resolver))}}
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

(defn- libspec
  "The import `{:context :as :refer :line}` of one `:require` libspec of the
   `ns` form of `file`, or a declared `ns-clauses` error: only a context name, or
   `[context :as alias]` / `[context :refer [relation …]]`, are accepted. The line is the
   one the reader gives the libspec (`scan` reads with `:location?` for every form), so two
   libspecs of the same context have their own line; `ns-line` when it has none."
  [file ns-line spec]
  (let [line (or (:line (meta spec)) ns-line)
        bad (fn [why] (fail :declare/ns-clauses (str "`:require` accepts only a context, `[context :as alias]` or `[context :refer [relation …]]`: " why)
                            line {:file file}))
        plain? (fn [x] (and (symbol? x) (nil? (namespace x))))]
    (cond
      (plain? spec) {:context spec :line line}
      (and (vector? spec) (plain? (first spec)))
      (let [opts (rest spec)]
        (when (odd? (count opts)) (bad (pr-str spec)))
        (reduce (fn [m [k v]]
                  (case k
                    :as (if (plain? v) (assoc m :as v) (bad (str ":as needs a plain symbol in " (pr-str spec))))
                    :refer (if (and (vector? v) (every? plain? v)) (assoc m :refer v) (bad (str ":refer needs a vector of relation names in " (pr-str spec))))
                    (bad (str "unsupported option " (pr-str k) " in " (pr-str spec)))))
                {:context (first spec) :line line}
                (partition 2 opts)))
      :else (bad (pr-str spec)))))

(defn- reserved-context?
  "True when the context name `nm` (a symbol) is `clojure`, `user`, `sci`, `kimun.declare` or
   anything under `clojure.`, `sci.` or `kimun.declare.`: names the evaluator or the language use."
  [nm]
  (let [n (str nm)]
    (boolean (or (#{"clojure" "user" "sci" "kimun.declare"} n)
                 (some #(str/starts-with? n %) ["clojure." "sci." "kimun.declare."])))))

(defn- ns-info
  "`{:context sym :requires [import…] :line n}` of the `(ns …)` form, or a
   declared error."
  [file form]
  (let [line (or (:line (meta form)) 1)
        [_ nm & more] form
        require? (fn [c] (and (seq? c) (= :require (first c))))]
    (when-not (and (symbol? nm) (nil? (namespace nm)))
      (fail :declare/ns-clauses "`ns` needs a plain symbol name" line {:file file}))
    (when (reserved-context? nm)
      (fail :declare/reserved-context
            (str "context `" nm "` is reserved (the name of the language or of the evaluator): a declaration there could shadow what the language needs; choose another `ns` name")
            line {:file file :context (str nm)}))
    (when (some #(not (or (string? %) (map? %) (require? %))) more)
      (fail :declare/ns-clauses "`ns` accepts only a name, a docstring, an attribute map and `(:require …)` of other contexts in a declaration file" line {:file file}))
    {:context nm :line line
     :requires (vec (mapcat (fn [c] (map #(libspec file line %) (rest c))) (filter require? more)))}))

(defn- scan
  "First pass over `text` (file `file`): syntax, and the `ns` form, which must
   be the first and only one. Returns `ns-info`."
  [text file]
  (let [scan-ctx (sci/init {})
        rdr (sci/reader (str text))
        eof (Object.)
        next-form (fn []
                    (try (sci/parse-next scan-ctx rdr {:eof eof :location? (fn [_] true)})
                         (catch Exception e
                           (let [line (or (:line (ex-data e)) 1)]
                             (err/raise :declare/syntax (str file ":" line ": " (ex-message e)) {:file file :line line})))))]
    (loop [first? true info nil]
      (let [form (next-form)]
        (if (identical? form eof)
          (or info (err/raise :declare/no-context (str file ":1: the file has no (ns context.name)") {:file file :line 1}))
          (let [line (or (:line (meta form)) 1)
                ns-form? (and (seq? form) (= 'ns (first form)))]
            (cond
              (and first? (not ns-form?))
              (err/raise :declare/no-context
                         (str file ":" line ": the first form must be (ns context.name): a proposition without a context is not a proposition")
                         {:file file :line line})

              (and ns-form? (not first?))
              (err/raise :declare/no-context (str file ":" line ": a second `ns`: one context per file") {:file file :line line})

              :else
              (recur false (if ns-form? (try (ns-info file form) (catch clojure.lang.ExceptionInfo e (rethrow e file line))) info)))))))))

(defn- prelude
  "Source that defines, in the sci namespace of the required context `ctx-sym`,
   one macro per relation name in `names`; each stores its propositions under
   the qualified relation `ctx/name`."
  [ctx-sym names]
  (str/join "\n" (map (fn [n] (str "(defmacro " n " [& args] (kimun.declare.impl/expand-as '" ctx-sym "/" n " &form &env args resolve))")) names)))

(def default-timeout-ms
  "How long one file may take to evaluate, unless the `:timeout-ms` option says otherwise."
  10000)

(defn run-limited
  "Calls `f` on a daemon thread and waits at most `timeout-ms` for it; returns its value,
   rethrows what it threw, or raises `declare/timeout` for `file`. The JVM (and Babashka's
   native image) cannot stop a thread that does not cooperate: after a timeout the thread is
   interrupted and ABANDONED, so a loop like `(loop [] (recur))` keeps a core busy until the
   process ends. It is a daemon (it never keeps the process alive) of minimum priority."
  [file timeout-ms f]
  (let [result (promise)
        bound (bound-fn* f)
        t (doto (Thread. nil (fn [] (deliver result (try {:value (bound)} (catch Throwable e {:error e})))) "kimun-declare-eval" 33554432)
            (.setDaemon true)
            (.setPriority Thread/MIN_PRIORITY)
            (.start))
        r (deref result timeout-ms ::timeout)]
    (cond
      (identical? r ::timeout)
      (do (.interrupt t)
          (err/raise :declare/timeout
                     (str "the file took more than " timeout-ms " ms to evaluate (an endless loop?); the evaluation was abandoned")
                     {:file file :line 1 :timeout-ms timeout-ms}))
      (:error r) (throw (:error r))
      :else (:value r))))

(defn- check-locators!
  "Raises `declare/bad-locator` when an `(at …)` node of the propositions `props` is not one of
   the forms of `kimun.surface.declared-anchors`."
  [props]
  (doseq [p props, node [(:a p) (:b p)]
          :when (and (map? node) (:anchor node))]
    (declared-anchors/translate (:at p) (:anchor node))))

(defn- evaluate-file
  "Evaluates one file whose `info` (from `scan`) is known; `imports` are the
   already evaluated required contexts `[{:context sym :names [relation-name…] :refer […]}]`."
  [host text file info imports timeout-ms]
  (let [referred (into {} (for [{c :context rs :refer} imports, r rs] [(str r) (str c)]))
        st (atom {:file file :host host :context (:context info) :relations {} :props [] :index {} :generated [] :readings [] :referred referred})
        context (:context info)
        ctx (make-ctx st context)]
    (try
      (run-limited file timeout-ms
                   (fn []
                     (doseq [{c :context names :names} imports]
                       (sci/eval-string+ ctx (prelude c names) {:ns (sci/create-ns c nil)}))
                     ;; the whole text, ns form included, pinned to the context namespace; sci
                     ;; resolves the `:require`s against the macros the prelude just defined
                     (sci/eval-string+ ctx (str text) {:ns (sci/create-ns context nil)})))
      (catch Throwable e (rethrow e file 1)))
    (let [{:keys [relations props generated readings]} @st
          recipes (reduce (fn [acc e] (if (some #(and (= (:path e) (:path %)) (identical? (:fn e) (:fn %))) acc) acc (conj acc e)))
                          [] generated)]
      (check-locators! props)
      (with-meta
        {:context (str context)
         :relations relations
         :propositions (mapv #(vary-meta % dissoc ::proposition) props)
         :generated (mapv #(dissoc % :fn) recipes)
         :readings (mapv #(select-keys % [:pattern :at :context]) readings)}
        {::recipes recipes ::readers readings}))))

(defn- check-generated-conflicts!
  "Raises `declare/generated-conflict` when two `generated` of the file results `results` name
   the same path with different recipes (at the second one; both positions in the data)."
  [results]
  (reduce (fn [seen {:keys [path at] f :fn :as e}]
            (if-let [prev (get seen path)]
              (if (identical? (:fn prev) f)
                seen
                (fail :declare/generated-conflict
                      (str (:file at) ":" (:line at) ": `" path "` already has another recipe, declared at " (:file (:at prev)) ":" (:line (:at prev)))
                      (:line at) {:file (:file at) :path path :positions [(:at prev) at]}))
              (assoc seen path e)))
          {} (mapcat #(::recipes (meta %)) results)))

(defn recipes
  "The recipes of the file results `results` (as `read-project` returns them): `[{:path :at {:file
   :line} :context :fn}]`, one per path (the first declaration), in order. The functions travel
   only in the metadata of the results, never in their data."
  [results]
  (:out (reduce (fn [{:keys [seen] :as acc} e]
                  (if (contains? seen (:path e)) acc (-> acc (update :seen conj (:path e)) (update :out conj e))))
                {:seen #{} :out []} (mapcat #(::recipes (meta %)) results))))

(defn readers
  "The readers of the file results `results` (as `read-project` returns them), in order:
   `[{:pattern :at {:file :line} :context :fn}]`. Like the recipes, the functions travel only in
   the metadata of the results, never in their data (`:readings` of a result has no `:fn`)."
  [results]
  (vec (for [e (mapcat #(::readers (meta %)) results)] (select-keys e [:pattern :at :context :fn]))))

(defn collect
  "Runs `f` (a call of a reader, made AFTER its file finished evaluating) on a limited thread
   (`run-limited`, `timeout-ms`, attributed to `file`) and returns the propositions that the
   relation uses inside it emitted, `[p…]` without their return value (ignored); what `f`
   returns is dropped. Emissions do not go to the (closed) state of the file but to a sink of
   this call: the dynamic `*sink*`, which `run-limited` conveys to its thread, so a reader
   abandoned after a timeout can only write into a sink nobody reads. The shape of every
   `(at …)` it emitted is checked (`declare/bad-locator`). Raises what `f` raises."
  [file timeout-ms f]
  (let [sink (atom {:props [] :index {}})]
    (binding [*sink* sink]
      (run-limited file timeout-ms f))
    (let [ps (:props @sink)]
      (check-locators! ps)
      (mapv #(vary-meta % dissoc ::proposition) ps))))

(defn- order
  "The files of `scanned` (`[{:file :text :info}]`) in dependency order (each
   context after the ones it requires, ties in input order), or a declared
   `duplicate-context`, `unknown-context` or `require-cycle` error."
  [scanned]
  (let [by-ctx (reduce (fn [m {:keys [file info] :as f}]
                         (if-let [other (get m (:context info))]
                           (fail :declare/duplicate-context
                                 (str file ":" (:line info) ": context `" (:context info) "` is also defined in " (:file other))
                                 (:line info) {:file file :other (:file other) :context (str (:context info))})
                           (assoc m (:context info) f)))
                       {} scanned)]
    (doseq [{:keys [file info]} scanned, {:keys [context line]} (:requires info)]
      (when-not (contains? by-ctx context)
        (fail :declare/unknown-context
              (str file ":" line ": required context `" context "` is not defined by any declaration file of this set")
              line {:file file :context (str context)})))
    (letfn [(visit [{:keys [seen] :as acc} path {:keys [file info] :as f}]
              (let [c (:context info)]
                (cond
                  (contains? seen c) acc
                  (some #{c} path)
                  (let [cyc (conj (vec (drop-while #(not= % c) path)) c)]
                    (fail :declare/require-cycle
                          (str file ":" (:line info) ": contexts require each other: " (str/join " -> " cyc))
                          (:line info) {:file file :cycle (mapv str cyc)}))
                  :else
                  (let [acc' (reduce (fn [a {:keys [context]}] (visit a (conj path c) (get by-ctx context)))
                                     acc (:requires info))]
                    (-> acc' (update :seen conj c) (update :out conj f))))))]
      (:out (reduce #(visit %1 [] %2) {:seen #{} :out []} scanned)))))

(defn read-project
  "Evaluates a set of declaration files `[{:file name :text text} …]` as one
   project: a file may `(:require [other :refer [relation …]])` or
   `[other :as o]` (`o/relation`) contexts defined by other files of the SAME
   set; they are evaluated in dependency order, each once. Only RELATIONS are
   imported (never the `def`s of another context), and a use of an imported
   relation stores `:r` as `\"defining-context/name\"`. Returns, in input order,
   `[{:file :context :relations :propositions}]`. Declared errors (all with
   `:file` and `:line`): those of `read-declarations` and
   `declare/duplicate-context`, `declare/unknown-context`,
   `declare/require-cycle`, `declare/unknown-relation`, `declare/relation-conflict`
   (an own relation named like one imported with `:refer`), `declare/reserved-context`,
   `declare/timeout`. `host` defaults to the standard host; `opts` is `{:timeout-ms n}`, the
   limit for EACH file (default `default-timeout-ms`)."
  ([files] (read-project host-default/host files {}))
  ([host files] (read-project host files {}))
  ([host files {:keys [timeout-ms] :or {timeout-ms default-timeout-ms}}]
   (let [scanned (mapv (fn [{:keys [file text]}] {:file file :text text :info (scan text file)}) files)
         results (reduce
                  (fn [acc {:keys [file text info]}]
                    (let [imports (mapv (fn [{:keys [context refer line]}]
                                          (let [names (vec (keys (:relations (get acc context))))]
                                            (doseq [r refer]
                                              (when-not (some #{(str r)} names)
                                                (fail :declare/unknown-relation
                                                      (str file ":" line ": context `" context "` does not define a relation `" r "`")
                                                      line {:file file :context (str context) :relation (str r)})))
                                            {:context context :names names :refer refer}))
                                        (:requires info))]
                      (assoc acc (:context info) (assoc (evaluate-file host text file info imports timeout-ms) :file file))))
                  {} (order scanned))]
     (let [out (mapv #(get results (get-in % [:info :context])) scanned)]
       (check-generated-conflicts! out)
       out))))

(defn header-of
  "`{:context \"c\" :requires [\"other\" …]}` of the declaration file `text` (named `file`): its
   `ns` form read, nothing evaluated. Raises the declared errors of reading (`syntax`,
   `no-context`, `ns-clauses`, `reserved-context`)."
  [text file]
  (let [{:keys [context requires]} (scan text file)]
    {:context (str context) :requires (mapv (comp str :context) requires)}))

(defn count-forms
  "How many top-level forms the Clojure `text` has, read with the reader of the evaluator; a
   text that does not read is `declare/syntax` (`file` and the line in the data)."
  [text file]
  (let [ctx (sci/init {})
        rdr (sci/reader (str text))
        eof (Object.)]
    (loop [n 0]
      (let [f (try (sci/parse-next ctx rdr {:eof eof})
                   (catch Exception e
                     (let [line (or (:line (ex-data e)) 1)]
                       (err/raise :declare/syntax (str file ":" line ": " (ex-message e)) {:file file :line line}))))]
        (if (identical? f eof) n (recur (inc n)))))))

(defn read-declarations
  "Evaluates the declaration file whose text is `text` (named `file` in
   positions) and returns `{:context :relations :propositions}`; raises a
   `:declare/*` error carrying `:file` and `:line` otherwise. A file that
   requires other contexts needs `read-project`. `host` defaults to the
   standard host (it only supplies the hasher of the `:id`s)."
  ([text file] (read-declarations host-default/host text file))
  ([host text file] (dissoc (first (read-project host [{:file file :text text}])) :file)))
