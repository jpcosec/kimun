(ns kimun.cli.commands.sections
  "The `kimun sections` group (v1 `sldb sections show|find|fields`): `list`
   (alias `show`), `find` and `fields`, read-only and thin over
   `kimun.surface.sections`. The sections of a tracked document come from its
   source Markdown, so the output is the same whatever else the store holds
   (v1 needed a persisted index to be kept in step). Every command is
   `(fn [args opts ctx] envelope)`; failures are raised as typed errors."
  (:require [clojure.string :as str]
            [kimun.cli.out :as out]
            [kimun.cli.store :as cli-store]
            [kimun.surface.docs :as tdocs]
            [kimun.surface.models.store :as mstore]
            [kimun.surface.sections :as sections]
            [kimun.surface.semantic :as sem]
            [kimun.kernel.err :as err]))

(defn- open [opts {:keys [env cwd]}] (cli-store/open! (cli-store/resolve-store opts env cwd)))
(defn- store-ref [{:keys [descriptor path]}] {:name (:name descriptor) :path path})

(defn- require-arg! [args i what sub]
  (or (get args i)
      (err/raise :cli/usage (str "`sections " sub "` needs " what) {:group "sections" :subcommand sub})))

(defn- flag
  "The boolean of option `k`: a boolean, or the strings true/false."
  [opts k]
  (let [v (get opts k)]
    (cond (nil? v) false
          (boolean? v) v
          (#{"true" "yes"} (str/lower-case (str v))) true
          (#{"false" "no"} (str/lower-case (str v))) false
          :else (err/raise :cli/usage (str "--" (name k) " is a flag, got `" v "`") {:option (name k) :value v}))))

(defn- search-in [opts]
  (let [v (or (:in opts) "both")]
    (if (#{"physical" "semantic" "both"} v)
      v
      (err/raise :cli/usage (str "--in expects physical, semantic or both, got `" v "`") {:option "in" :value v}))))

(defn- tags-index
  "`{doc-name tags}` of every tracked document under its defined model."
  [store]
  (into {} (map (juxt :name :tags)) (sem/document-tags store)))

(defn- doc-entries
  "`[document entries]` of the tracked document `token`."
  [store token]
  (let [d (tdocs/find-document store token)
        _ (when-not d (err/raise :docs/missing (str "no document named or identified by `" token "`") {:document token}))]
    [d (sections/entries (:source d) (get (tags-index store) (:name d) []))]))

(defn- non-empty! [what rows data]
  (if (seq rows) rows (err/raise :sections/empty (str "no " what) data)))

(defn list-sections
  "`sections list DOC` (alias `show`): the sections of the tracked document
   DOC in order. Data: `{:document :model :path :sections [{:path :title
   :breadcrumbs :about :slug :semantic_tags :level :line_start :line_end}]}`.
   A document without headings raises `:sections/empty` (exit 1, as v1)."
  [args opts ctx]
  (let [token (require-arg! args 0 "a document NAME" "list")
        o (open opts ctx)
        [d es] (doc-entries (:store o) token)]
    (non-empty! "sections" es {:document (:name d)})
    (out/ok "sections list" {:store (store-ref o) :revision (:head (:store o))
                             :data {:document (:name d) :model (:model d) :path (:path d) :sections es}})))

(defn find-cmd
  "`sections find TERM [--in physical|semantic|both] [--regex] [--fuzzy]
   [--where EXPR] [--model M]`: the sections of every tracked document that
   match TERM (empty TERM: all). Data: `{:term :results [{:kind :store :name
   :model :doc :field :path :title :value :semantic :about :breadcrumbs}]}`; `path`
   is `file#section`. No match raises `:sections/empty` (exit 1, as v1)."
  [args opts ctx]
  (let [term (or (get args 0) "")
        in (search-in opts) regex (flag opts :regex) fuzzy (flag opts :fuzzy)
        o (open opts ctx)
        store (:store o)
        tags (tags-index store)
        recs (vec (mapcat (fn [d]
                            (sections/records {:store (:name (:descriptor o)) :name (:name d) :model (:model d) :path (:path d)
                                               :tags (get tags (:name d) [])}
                                              (sections/entries (:source d) (get tags (:name d) []))))
                          (filter #(or (nil? (:model opts)) (= (:model opts) (:model %))) (tdocs/documents store))))
        found (sections/find-sections recs term {:in in :regex regex :fuzzy fuzzy :where (:where opts)})]
    (non-empty! "matching sections" found {:term term})
    (out/ok "sections find" {:store (store-ref o) :revision (:head store)
                             :data {:term term :results (mapv sections/result found)}})))

(defn fields
  "`sections fields DOC SECTION`: the payload fields the section owns, by its
   path or slug (`:sections/missing` exit 1; `:sections/ambiguous` exit 2 for a
   repeated slug). Data: `{:document :section :fields [{:field_path :value
   :owning_section}]}`. A section owning nothing raises `:sections/empty`."
  [args opts ctx]
  (let [token (require-arg! args 0 "a document NAME" "fields")
        wanted (require-arg! args 1 "a SECTION (path or slug)" "fields")
        o (open opts ctx)
        store (:store o)
        [d es] (doc-entries store token)
        sec (sections/resolve-section es wanted)
        model (mstore/find-model store (:model d))
        doc (tdocs/document store (:name d))]
    (when-not model (err/raise :models/missing (str "no model named `" (:model d) "`") {:model (:model d)}))
    (when-not (:payload doc)
      (err/raise :sections/unreadable (str "the source of `" (:name d) "` no longer extracts: " (:error doc)) {:document (:name d)}))
    (let [descriptor (or (tdocs/contract-descriptor store doc) (:descriptor model))
          rows (sections/fields-of descriptor (:source d) (:payload doc) (:path sec))]
      (non-empty! "fields in that section" rows {:document (:name d) :section (:path sec)})
      (out/ok "sections fields" {:store (store-ref o) :revision (:head store)
                                 :data {:document (:name d) :section (:path sec) :fields rows}}))))

(def spec
  "Options of the `sections` group (babashka.cli spec, merged by `kimun.cli.main`)."
  {:in {:ref "WHERE" :desc "Search physical text, semantic tags or both (sections find; default both)"}
   :regex {:desc "TERM is a regular expression (sections find)" :coerce :boolean}
   :fuzzy {:desc "Fuzzy match of TERM (sections find)" :coerce :boolean}
   :where {:ref "EXPR" :desc "Keep sections whose record satisfies EXPR, e.g. '\"Tasks\" in about' (sections find)"}})

(def usage
  "Usage lines of the group, for the CLI help."
  ["  sections list DOC           the sections of a document (alias: show)"
   "  sections find [TERM] [--in physical|semantic|both] [--regex] [--fuzzy] [--where EXPR] [--model M]"
   "  sections fields DOC SECTION the fields a section owns, with their values"])

(def commands
  "Subcommand name → command fn `(fn [args opts ctx] envelope)`."
  {"list" list-sections "show" list-sections "find" find-cmd "fields" fields})

(defn dispatch
  "Runs subcommand `sub` of the `sections` group with the positional tokens that
   follow it; an unknown or missing subcommand is the failure `cli/usage`."
  [sub args opts ctx]
  (if-let [f (get commands sub)]
    (f (vec args) opts ctx)
    (out/failure :cli/usage
                 (str (if sub (str "unknown subcommand `sections " sub "`") "missing subcommand for `sections`")
                      "; expected one of: " (str/join ", " (sort (keys commands))))
                 {:group "sections" :subcommand sub :known (vec (sort (keys commands)))}
                 6)))
