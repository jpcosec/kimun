(ns kimun.surface.sections
  "The section surface of a typed document (sldb v1 `sldb sections show|find|
   fields`), thin over `kimun.surface.models.segments`: the section index of a
   document (`entries`), the search records `find` ranks (`records`,
   `find-sections`), the lookup of one section (`resolve-section`) and the
   fields it owns (`fields-of`). Pure — it receives the document's Markdown,
   payload and tags, never a store. Keys keep v1's names as keywords
   (`:line_start`, `:semantic_tags`…)."
  (:require [clojure.string :as str]
            [kimun.surface.index.where :as where]
            [kimun.surface.models.segments :as segments]
            [sldb.kernel.err :as err]))

(defn- slugify
  "v1 `_slugify`: lower-case, runs of non-alphanumerics become one `-`."
  [s]
  (-> (str/lower-case (str s)) (str/replace #"[^\p{L}\p{N}]+" "-") (str/replace #"^-+|-+$" "")))

(defn- about-terms
  "v1 `_about_terms`: each breadcrumb, its slug as words, then the tags; no repeats."
  [breadcrumbs tags]
  (let [terms (concat (mapcat (fn [b] (let [r (str/trim b)
                                            n (str/trim (str/replace (slugify r) "-" " "))]
                                        (remove str/blank? [r n])))
                              breadcrumbs)
                      tags)]
    (vec (distinct terms))))

(defn entries
  "The section index of `markdown` (v1 `SectionContextRecord`s), in document
   order: `{:path :title :breadcrumbs :about :slug :semantic_tags :level
   :line_start :line_end}`. `:breadcrumbs` are the titles of the enclosing
   headings plus the section's own; `:about` are those and their slugs as
   words followed by `tags` (the document's semantic tags)."
  [markdown tags]
  (let [tags (vec tags)]
    (loop [secs (segments/sections markdown) stack [] out []]
      (if-let [s (first secs)]
        (let [stack (vec (drop-while #(>= (first %) (:level s)) (rseq stack)))
              stack (vec (reverse stack))
              crumbs (conj (if-let [p (peek stack)] (second p) []) (:title s))]
          (recur (rest secs) (conj stack [(:level s) crumbs])
                 (conj out {:path (:path s) :title (:title s) :breadcrumbs crumbs
                            :about (about-terms crumbs tags) :slug (:slug s)
                            :semantic_tags tags :level (:level s)
                            :line_start (:line_start s) :line_end (:line_end s)})))
        out))))

(defn resolve-section
  "The entry of `entries` named by `token`: its path, else its slug. Raises
   `:sections/missing` (exit 1) when none and `:sections/ambiguous` (exit 2)
   when several sections share the slug (use the path)."
  [entries token]
  (if-let [by-path (first (filter #(= token (:path %)) entries))]
    by-path
    (let [found (filterv #(= token (:slug %)) entries)]
      (case (count found)
        0 (err/raise :sections/missing (str "no section `" token "`") {:section token :known (mapv :path entries)})
        1 (first found)
        (err/raise :sections/ambiguous (str (count found) " sections have the slug `" token "`; use the path")
                   {:section token :paths (mapv :path found)})))))

(defn fields-of
  "`[{:field_path :value :owning_section}]` (v1 `sections fields`): the leaves of
   `payload` that the section at `section-path` owns under `descriptor`'s
   template, in payload order, resolved over the sections of `markdown`. A blank
   `section-path` keeps every leaf."
  [descriptor markdown payload section-path]
  (segments/fields-of-section descriptor (segments/sections markdown) payload section-path))

;; ---------------------------------------------------------------- search records

(defn records
  "The search records of one document (v1 `iter_search_records`, kind
   \"section\"): `{:kind \"section\" :store :name :model :doc :field nil :path
   \"file#section\" :title :value nil :semantic :about :breadcrumbs :physical
   :payload}`. `doc` is `{:store :name :model :path :tags}`. `:physical` is
   what a physical search reads (title, slug, section path, file); `:payload` is
   what `--where` sees (`level`, `line_start`, `line_end`, `breadcrumbs`,
   `about`, `title`, `slug`, `section` (the section path), `path` (`file#section`),
   `semantic`, `semantic_tags`)."
  [{:keys [store name model path tags]} entries]
  (mapv (fn [e]
          {:kind "section" :store store :name (:slug e) :model model :doc name :field nil
           :path (str path "#" (:path e)) :title (:title e) :value nil
           :semantic (vec tags) :about (:about e) :breadcrumbs (:breadcrumbs e)
           :physical (vec (remove nil? [(:title e) (:slug e) (:path e) path]))
           :payload {"level" (:level e) "line_start" (:line_start e) "line_end" (:line_end e)
                     "breadcrumbs" (:breadcrumbs e) "about" (:about e) "title" (:title e)
                     "slug" (:slug e) "section" (:path e) "path" (str path "#" (:path e))
                     "semantic" (vec tags) "semantic_tags" (vec tags)}})
        entries))

(defn- ratio
  "difflib `SequenceMatcher(a, b).ratio()` (matching blocks, recursively)."
  [a b]
  (let [match (fn match [a b]
                (if (or (empty? a) (empty? b))
                  0
                  (let [la (count a) lb (count b)
                        [ai bi n] (loop [i 0 best [0 0 0] row (vec (repeat (inc lb) 0))]
                                    (if (= i la)
                                      best
                                      (let [[row' best']
                                            (reduce (fn [[r bst] j]
                                                      (let [k (if (= (nth a i) (nth b j)) (inc (nth row j)) 0)
                                                            bst (if (> k (nth bst 2)) [(inc (- i k)) (inc (- j k)) k] bst)]
                                                        [(conj r k) bst]))
                                                    [[0] best] (range lb))]
                                        (recur (inc i) best' row'))))]
                    (if (zero? n)
                      0
                      (+ n (match (subvec a 0 ai) (subvec b 0 bi))
                         (match (subvec a (+ ai n)) (subvec b (+ bi n))))))))
        a (vec a) b (vec b) total (+ (count a) (count b))]
    (if (zero? total) 1.0 (/ (* 2.0 (match a b)) total))))

(defn- term-matches? [haystacks term regex? fuzzy?]
  (let [hs (remove str/blank? haystacks)]
    (cond regex? (boolean (some #(re-find (re-pattern term) %) hs))
          fuzzy? (let [t (str/lower-case term)]
                   (boolean (some #(or (>= (ratio t (str/lower-case %)) 0.7) (str/includes? (str/lower-case %) t)) hs)))
          :else (boolean (some #(str/includes? % term) hs)))))

(defn find-sections
  "The section `records` that match `term` (v1 `search_records` for kind
   section, then `--where`). `opts`: `:in` `\"physical\"`, `\"semantic\"` or
   `\"both\"` (default; semantic reads the document tags and `:about`),
   `:regex`, `:fuzzy`, `:where` (a where expression string; see
   `kimun.surface.index.where`, evaluated on each record's `:payload` with the
   record's `:doc`/`:model` as name/model). An empty `term` keeps every record.
   This is the function `find --type section` calls."
  [records term {:keys [in regex fuzzy where] :or {in "both"}}]
  (let [ast (when-not (str/blank? where) (where/parse where))
        by-term (if (str/blank? term)
                  records
                  (filterv (fn [r]
                             (term-matches? (concat (when (#{"physical" "both"} in) (:physical r))
                                                    (when (#{"semantic" "both"} in) (concat (:semantic r) (:about r))))
                                            term regex fuzzy))
                           records))]
    (if ast
      (filterv #(where/matches? ast {:name (:doc %) :model (:model %) :bases [] :payload (:payload %) :tags (:semantic %)}) by-term)
      by-term)))

(defn result
  "The wire form of a search record (v1 `SearchRecord.as_dict`)."
  [r]
  (select-keys r [:kind :store :name :model :doc :field :path :title :value :semantic :about :breadcrumbs]))
