(ns kimun.surface.links.composer
  "Expands `![[transclusions]]` into composed Markdown (v1:
   `sldb.links.composer`). Cycles and repeats are not errors in v1: a document
   already visited anywhere in the walk is inlined raw, its own transclusions
   left unexpanded. An unresolved transclusion is left in place and reported."
  (:require [clojure.string :as str]
            [kimun.surface.links.parser :as parser]
            [kimun.surface.links.recovery :as recovery]
            [kimun.surface.links.resolver :as resolver]))

(defn- sorted-distinct [xs] (vec (sort (distinct xs))))

(declare compose*)

(defn- expand
  "Folds one transclusion match into `state` `{:out :seen :tr :un}`."
  [opts path text state {:keys [start end groups]}]
  (let [[raw bang target] groups
        state (assoc state :out (str (:out state) (subs text (:cursor state) start)) :cursor end)
        target (str/trim target)
        res   (when bang (resolver/resolve-target (:lookup opts) target path))]
    (cond
      (nil? res) (update state :out str raw)
      (not (:resolved res)) (-> state (update :un conj target) (update :out str raw))
      :else
      (let [[n seen] (compose* opts (:path res) (:seen state))]
        (-> state
            (assoc :seen seen)
            (update :tr #(into (conj % target) (:transclusions n)))
            (update :un into (:unresolved n))
            (update :out str (str/trimr (:markdown n))))))))

(defn- compose*
  "`[result seen']` for the document at `path`; `result` is
   `{:root :path :markdown :transclusions :unresolved}`, or just the raw
   `:markdown` with empty lists when `path` was already visited."
  [opts path seen]
  (let [text ((:content opts) path)]
    (if (contains? seen path)
      [{:markdown text :transclusions [] :unresolved []} seen]
      (let [st (reduce (partial expand opts path text)
                       {:out "" :cursor 0 :seen (conj seen path) :tr [] :un []}
                       (parser/matches parser/link-pattern text))
            md (str (:out st) (subs text (:cursor st)))]
        [{:root (recovery/stem path) :path path :markdown md
          :transclusions (sorted-distinct (:tr st))
          :unresolved (sorted-distinct (:un st))}
         (:seen st)]))))

(defn compose-document
  "Composes the document at `path`. Options: `:lookup` (see
   `resolver/resolve-target`) and `:content` `(fn [path]) -> markdown`.
   Returns `{:root stem :path path :markdown :transclusions [target …]
   :unresolved [target …]}`, both lists sorted and distinct. Every `![[t]]`
   (also inside a predicate link) whose target resolves is replaced by the
   target's composed Markdown with trailing whitespace trimmed; plain
   `[[links]]` are untouched."
  [path opts]
  (first (compose* opts path #{})))
