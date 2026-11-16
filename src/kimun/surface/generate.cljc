(ns kimun.surface.generate
  "Running the recipes of generated documents (docs/v2/drafts/diseno-generar.md). A recipe is
   a function of a declaration file (`(generated path recipe)`, registered by
   `kimun.surface.declare`); here each one is called with ONE argument, `ask`, and must return
   the text of the file. Pure: it touches no disk (the text of an anchor is read through the
   injected `read-now`) and writes nothing; the command writes.

   `ask` is a map of functions over the propositions of the WHOLE project, with the results
   of `kimun.surface.propositions` (plain Clojure data: `:term` is a string, `:r` a string):
   - `(:about ask) term` or `term {:exact true}`: the ROWS of `props/about` (its `:results`)
   - `(:about-path ask) path`: the rows of `props/about-path`
   - `(:members ask) pattern`: the project files a `(files pattern)` reaches
   - `(:terms ask)` (call it with no argument): the vector of `props/terms`
   - `(:text ask) node`: the current text of the region an `(at …)` node (or `{:anchor …}`)
     names (the whole file, a section, a block or a span), or nil when it does not resolve.
     A section or a block is its source lines, which need the block layer to align with the
     AST (`capture/aligned-spans`); when it does not, nil.

   The recipe sees nothing else: it runs in the sci context of its file, where the vocabulary
   is the only thing beyond `clojure.core`. Errors are `kimun.kernel.err/raise` with
   `:declare/*` types: `bad-recipe` (it returned something that is not a string), `recipe` (it
   threw) and `timeout` (it took more than `:timeout-ms`), all with `:path`, `:file` and `:line`
   of its declaration."
  (:require [clojure.string :as str]
            [kimun.kernel.err :as err]
            [kimun.surface.declare :as declare]
            [kimun.surface.declared-anchors :as declared-anchors]
            [kimun.surface.markdown.anchors :as anchors]
            [kimun.surface.models.capture :as capture]
            [kimun.surface.propositions :as props]))

(defn- text-of
  "The text of the region `node` names, reading files through `read-now` and the anchors of a
   path through `anchors-of` (memoized by the caller); nil when it does not resolve."
  [read-now anchors-of node]
  (let [anchor (when (map? node) (:anchor node))
        _ (when-not (and (map? anchor) (string? (:path anchor)))
            (err/raise :declare/recipe "`:text` takes an (at …) node: {:anchor {:path … :locator …}}" {:got (pr-str node)}))
        t (declared-anchors/translate {:file "ask :text" :line 0} anchor)
        text (read-now (:path t))]
    (when text
      (cond
        (= :file (:kind t)) text
        (not (:markdown? t)) nil
        :else
        (let [{:keys [an lines]} (anchors-of (:path t) text)
              r (anchors/resolve-anchor an (:locator t))]
          (if (= :span (:kind t))
            (:text r)
            (when-let [[s e] (:lines r)]
              (str/join "\n" (subvec lines (long s) (long (min e (count lines))))))))))))

(defn ask-for
  "The `ask` map of the recipes. `propositions` are those of the whole project, `paths` the
   project files `:members` expands over, `read-now` `(fn [path] text-or-nil)`; `host` supplies
   the hasher of the anchors."
  [host {:keys [propositions paths read-now]}]
  (let [memo (atom {})
        anchors-of (fn [path text]
                     (or (get @memo path)
                         (let [v {:an (anchors/anchors host text {:spans (capture/aligned-spans text) :leaves true})
                                  :lines (vec (str/split-lines text))}]
                           (swap! memo assoc path v)
                           v)))]
    {:about (fn ([term] (:results (props/about propositions term)))
              ([term opts] (:results (props/about propositions term opts))))
     :about-path (fn [path] (:results (props/about-path propositions path)))
     :members (fn [pattern] (props/members pattern paths))
     :terms (fn [] (props/terms propositions))
     :text (fn [node] (text-of read-now anchors-of node))}))

(defn- fail-recipe
  "Raises the declared error `type` for the recipe `e` (`{:path :at}`) with `msg`."
  [type {:keys [path at]} msg data]
  (err/raise type (str (:file at) ":" (:line at) ": the recipe of `" path "` " msg)
             (merge {:path path :file (:file at) :line (:line at)} data)))

(defn run-one
  "Runs the recipe `e` (`{:path :at :fn}`) with `ask` under the time limit `timeout-ms` and
   returns its text, or a declared error (see the namespace docstring)."
  [e ask timeout-ms]
  (let [r (try (declare/run-limited (:file (:at e)) timeout-ms (fn [] ((:fn e) ask)))
               (catch clojure.lang.ExceptionInfo ex
                 (if (= :declare/timeout (:type (ex-data ex)))
                   (fail-recipe :declare/timeout e (str "took more than " timeout-ms " ms (an endless loop?); it was abandoned") {:timeout-ms timeout-ms})
                   (fail-recipe :declare/recipe e (str "failed: " (ex-message ex)) {})))
               (catch Throwable ex
                 (fail-recipe :declare/recipe e (str "failed: " (or (ex-message ex) (str (class ex)))) {})))]
    (when-not (string? r)
      (fail-recipe :declare/bad-recipe e (str "must return a string, it returned " (pr-str r)) {}))
    r))

(defn run
  "Runs every recipe of `recipes` (`declare/recipes`) in order of path, each under `timeout-ms`
   (default `declare/default-timeout-ms`), and returns `[{:path :text :declared-at {:file
   :line}}]`. The first failure is raised and nothing is returned: the caller writes after all
   have run. `ctx` is the one of `ask-for`."
  [host recipes ctx {:keys [timeout-ms] :or {timeout-ms declare/default-timeout-ms}}]
  (let [ask (ask-for host ctx)]
    (mapv (fn [e] {:path (:path e) :text (run-one e ask timeout-ms) :declared-at (:at e)})
          (sort-by :path recipes))))

(defn forbidden
  "Why the generated path `p` (relative to the root, normalized) may not be written, or nil:
   under `kimun/` (the declarations are the source, a recipe may not overwrite them) or under
   `.git/` or `.kimun/`."
  [p]
  (let [first-seg (first (str/split (str p) #"/"))]
    (case first-seg
      "kimun" "it is under kimun/, where the declarations live"
      ".git" "it is under .git/"
      ".kimun" "it is under .kimun/, the store"
      nil)))
