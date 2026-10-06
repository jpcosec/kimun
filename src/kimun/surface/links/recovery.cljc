(ns kimun.surface.links.recovery
  "The report of `docs recover`: the links of a document, resolved or not,
   optionally followed through resolved targets (v1: `sldb.links.recovery`)."
  (:require [clojure.string :as str]
            [kimun.surface.links.parser :as parser]
            [kimun.surface.links.resolver :as resolver]))

(defn stem
  "The file name of `path` without directory nor last extension, like
   Python's `Path.stem`."
  [path]
  (let [name (last (str/split path #"/"))
        i    (str/last-index-of name ".")]
    (if (and i (pos? i)) (subs name 0 i) name)))

(defn- entry [l]
  (select-keys l [:target :kind :resolved :path :source :predicate :w5h1-type :reason]))

(defn- dedup [links]
  (:out (reduce (fn [{:keys [seen] :as acc} l]
                  (let [k [(:target l) (:kind l)]]
                    (if (seen k) acc (-> acc (update :seen conj k) (update :out conj l)))))
                {:seen #{} :out []} links)))

(defn- walk
  "`[links seen']` for the document at `path`, following resolved links down
   to `depth`; `seen` is shared across the walk exactly as v1's set is."
  [path {:keys [lookup content predicate-types] :as opts} depth seen]
  (if (or (contains? seen path) (< depth 1))
    [[] seen]
    (let [seen  (conj seen path)
          links (mapv #(entry (resolver/resolve-link lookup % path))
                      (parser/parse-links (content path) predicate-types))]
      (if (> depth 1)
        (reduce (fn [[acc seen] l]
                  (let [[sub seen] (walk (:path l) opts (dec depth) seen)]
                    [(into acc sub) seen]))
                [links seen]
                (filter #(and (:resolved %) (:path %)) links))
        [links seen]))))

(defn recover-links
  "The recovery report of the document at `path`:
   `{:root stem :path path :links […] :unresolved [target …]}`. Options:
   `:lookup` (see `resolver/resolve-target`), `:content` `(fn [path]) -> text`,
   `:predicate-types` (predicate -> axis) and `:depth` (default 1; with more,
   resolved targets are followed and their links appended, each document read
   once). `:links` are deduplicated by `[target kind]`, each
   `{:target :kind :resolved :path :source :predicate :w5h1-type}` (+ `:reason`
   when unresolved). v1's `include_transclusions` flag has no effect there and
   is not offered."
  [path {:keys [depth] :or {depth 1} :as opts}]
  (let [[links _] (walk path opts depth #{})
        u (dedup links)]
    {:root (stem path) :path path :links u
     :unresolved (mapv :target (remove :resolved u))}))
