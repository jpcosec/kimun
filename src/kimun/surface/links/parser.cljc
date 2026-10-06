(ns kimun.surface.links.parser
  "Finds the `[[links]]`, `![[transclusions]]` and `[pred:: [[links]]]` of a
   Markdown text with their exact offsets, in the grammar sldb v1 accepts
   (`sldb.links.parser`). No code-span or fenced-code awareness: v1 has none,
   so links inside code are parsed like any other."
  (:require [clojure.string :as str]))

(def link-pattern
  "A plain link or transclusion: group 1 is the optional `!`, group 2 the
   target text (everything up to the first `]`)."
  #"(!)?\[\[([^\]]+)\]\]")

(def inline-predicate-pattern
  "A predicate link `[pred:: [[t]]]` (or `[pred:: ![[t]]]`): group 1 the
   predicate, group 2 the optional `!`, group 3 the target text."
  #"\[([a-zA-Z0-9_-]+)::\s*(!)?\[\[([^\]]+)\]\]\]")

(defn matches
  "Non-overlapping matches of `re` in `text`, left to right, as
   `{:start :end :groups [g0 g1 …]}` (offsets in the platform's string units).
   A pure re-find loop, so it needs no platform matcher."
  [re text]
  (loop [from 0 acc []]
    (let [m (when (<= from (count text)) (re-find re (subs text from)))
          g (when m (if (string? m) [m] (vec m)))]
      (if-not g
        acc
        (let [start (+ from (str/index-of (subs text from) (first g)))
              end   (+ start (count (first g)))]
          (recur end (conj acc {:start start :end end :groups g})))))))

(defn- inline-link [predicate-types {:keys [start end groups]}]
  (let [[raw pred bang target] groups
        pred (str/trim pred)]
    {:raw raw :target (str/trim target) :start start :end end
     :kind (if bang :transclusion :predicate-link)
     :predicate pred
     :w5h1-type (get predicate-types pred "CUSTOM")}))

(defn- standard-link [{:keys [start end groups]}]
  (let [[raw bang target] groups]
    {:raw raw :target (str/trim target) :start start :end end
     :kind (if bang :transclusion :link)
     :predicate nil :w5h1-type nil}))

(defn parse-links
  "Every link of `markdown`, as maps `{:raw :target :kind :start :end
   :predicate :w5h1-type}`. `:kind` is `:link`, `:transclusion` or
   `:predicate-link` (a `!` inside a predicate link makes it a
   `:transclusion`). `predicate-types` maps predicate name to its axis
   (default `\"CUSTOM\"`). As in v1, predicate links come first and plain
   links after, each group in document order; a plain match lying inside a
   predicate link is not reported twice."
  ([markdown] (parse-links markdown {}))
  ([markdown predicate-types]
   (let [pt     (or predicate-types {})
         inline (mapv (partial inline-link pt) (matches inline-predicate-pattern markdown))
         spans  (map (juxt :start :end) inline)
         inside? (fn [{:keys [start end]}]
                   (some (fn [[s e]] (and (<= s start) (<= end e))) spans))]
     (into inline
           (comp (remove inside?) (map standard-link))
           (matches link-pattern markdown)))))
