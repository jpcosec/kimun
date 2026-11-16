(ns kimun.surface.propositions
  "Asking the propositions of declaration files (docs/v2/drafts/diseno-evaluar.md
   §9, cut 3b): to ask is to walk relations (nucleus 1.1). Pure: it receives the
   propositions that `kimun.surface.declare` produces (of one or several files)
   and, where needed, the list of the project's file paths, and returns data;
   it touches no disk and no git.

   It never interprets the NAME of a relation: `isa`, `anchors` and the rest are
   names each project defines, and nothing here knows which one is \"the class
   one\". Relations come back as written (`:r`, an opaque string).

   Hierarchy of terms: the dots of a name are hierarchy, `UI.view.dashboard` is
   also `UI.view` and `UI`. A node is one of the tagged forms of the evaluator:
   `{:term s}`, `{:proposition id}`, `{:anchor {:path :locator}}`, `{:files
   pattern}`, `{:generated {:path p}}` or a scalar literal."
  (:require [clojure.string :as str]
            [kimun.surface.include :as include]))

(defn ancestors-of
  "The ancestors of term `t` by dots, nearest first: `UI.view.dashboard` →
   `[\"UI.view\" \"UI\"]`. A name without dots, or whose pieces are empty
   (`a..b`, a leading or trailing dot), has none beyond the non-empty prefixes."
  [t]
  (let [parts (str/split (str t) #"\." -1)]
    (if (some str/blank? parts)
      []
      (vec (for [i (range (dec (count parts)) 0 -1)] (str/join "." (take i parts)))))))

(defn- term-of [node] (when (and (map? node) (contains? node :term)) (:term node)))

(defn- sides
  "`[[side node]…]` of a proposition."
  [p] [[:a (:a p)] [:b (:b p)]])

(defn terms
  "Every term written in `props`, sorted by name:
   `[{:term s :count n :ancestors [s…] :implicit bool}]`. `:count` is how many
   propositions name the term (once each, however many sides) and `:ancestors`
   its dotted ancestors, nearest first. An ancestor nobody wrote is listed too,
   with `:count` 0 and `:implicit true`; one somebody wrote is `:implicit false`."
  [props]
  (let [written (frequencies (mapcat (fn [p] (distinct (keep (comp term-of second) (sides p)))) props))
        all (into (set (keys written)) (mapcat ancestors-of) (keys written))]
    (vec (for [t (sort all)]
           {:term t :count (get written t 0) :ancestors (ancestors-of t)
            :implicit (not (contains? written t))}))))

(defn- matches-term?
  "Whether the term `t` is `target`, or (unless `exact`) a descendant of it."
  [target exact t]
  (or (= t target) (and (not exact) (str/starts-with? t (str target ".")))))

(defn about
  "What is known of the term `target`: `{:term :exact :levels 1 :results [row…]}`.
   A row is `{:via :term :proposition p :side :a|:b :matched t :other node}`: the
   proposition names a term that is `target` or, unless `:exact true`, a more
   specific one (`UI.view` includes `UI.view.dashboard`); `:side` is where it
   appears, `:matched` the term as written and `:other` what is on the opposite
   side. A proposition naming the term on both sides gives one row per side.
   After those come the rows `{:via :proposition :proposition p :side :a|:b
   :about id :other node}`: the propositions that have, as a term, a proposition
   already in the result (`:about` is its id). ONE level only (`:levels 1`):
   propositions about those are not followed. A proposition already in the
   result by its terms is not repeated."
  ([props target] (about props target {}))
  ([props target {:keys [exact]}]
   (let [target (str target)
         direct (vec (for [p props
                           [side node] (sides p)
                           :let [t (term-of node)]
                           :when (and t (matches-term? target (boolean exact) t))]
                       {:via :term :proposition p :side side :matched t
                        :other (if (= side :a) (:b p) (:a p))}))
         ids (set (map (comp :id :proposition) direct))
         via (vec (for [p props
                        :when (not (contains? ids (:id p)))
                        [side node] (sides p)
                        :let [id (when (map? node) (:proposition node))]
                        :when (contains? ids id)]
                    {:via :proposition :proposition p :side side :about id
                     :other (if (= side :a) (:b p) (:a p))}))]
     {:term target :exact (boolean exact) :levels 1 :results (into direct via)})))

(defn- clean-path [p] (str/replace (include/normalize-path p) #"^(\./)+" ""))

(defn about-path
  "What is known of the file `path` (relative to the project, never opened):
   `{:path p :results [row…]}`. A row is `{:via :anchor :proposition p :side
   :other node}` when the proposition has an `(at …)` anchor whose `:path` is
   `path` (`:locator` says where inside it), or `{:via :files :pattern s
   :proposition p :side :other node}` when it has a `(files pattern)` node whose
   pattern matches `path` (`*` stays inside a segment, `**` crosses them), or
   `{:via :generated :proposition p :side :other node}` when it has a `{:generated {:path
   path}}` node (a file materialized from the propositions, `diseno-generar.md`). `:other`
   is what is on the opposite side: so \"of which classes is this file?\" is answered
   without anything declared per file. Several patterns reaching the same file give
   several rows."
  [props path]
  (let [path (clean-path path)]
    {:path path
     :results (vec (for [p props
                         [side node] (sides p)
                         :let [other (if (= side :a) (:b p) (:a p))
                               a (when (map? node) (:anchor node))
                               pat (when (map? node) (:files node))
                               g (when (map? node) (:generated node))]
                         row [(when (and a (= path (clean-path (:path a))))
                                {:via :anchor :proposition p :side side :locator (:locator a) :other other})
                              (when (and g (= path (clean-path (:path g))))
                                {:via :generated :proposition p :side side :other other})
                              (when (and (string? pat) (re-matches (include/compile-glob (clean-path pat)) path))
                                {:via :files :pattern pat :proposition p :side side :other other})]
                         :when row]
                     row))}))

(defn members
  "The paths of `paths` that the pattern of a `{:files pattern}` node matches, sorted
   and without repeats. The expansion is made when asked, never stored."
  [pattern paths]
  (let [rx (include/compile-glob (clean-path pattern))]
    (vec (sort (distinct (filter #(re-matches rx %) (map clean-path paths)))))))
