(ns kimun.surface.links.resolver
  "Resolves a parsed link to a target through a caller-supplied lookup; the
   store is never read here (v1: `sldb.links.resolver`).")

(def source-order
  "Candidate sources by precedence, as in v1: a tracked document name beats a
   path next to the current document, which beats a path from the cwd."
  [:store :path])

(defn resolve-target
  "Resolves `target` seen from the document at `from`. `lookup` is
   `(fn [{:keys [target from]}]) -> [{:path p :source :store|:path} …]`, the
   candidate documents. The first non-empty source tier (`source-order`)
   decides: one distinct path resolves; several are `:ambiguous`; none at all
   is `:missing`. Returns `{:resolved true :path :source}` or
   `{:resolved false :reason :missing|:ambiguous :candidates […]}`."
  [lookup target from]
  (let [cands (vec (lookup {:target target :from from}))
        tier  (some (fn [s]
                      (let [c (filterv #(= s (:source %)) cands)] (when (seq c) c)))
                    (conj source-order :other))
        tier  (or tier (when (seq cands) cands))
        paths (vec (distinct (map :path tier)))]
    (cond
      (empty? paths)     {:resolved false :reason :missing :candidates []}
      (= 1 (count paths)) {:resolved true :path (first paths) :source (:source (first tier))}
      :else              {:resolved false :reason :ambiguous :candidates paths})))

(defn resolve-link
  "The parsed `link` (see `parser/parse-links`) with its resolution merged in:
   `{:raw :target :kind :start :end :predicate :w5h1-type :resolved :path
   :source}` plus `:reason`/`:candidates` when unresolved. `from` is the path
   of the document holding the link."
  [lookup link from]
  (merge link (resolve-target lookup (:target link) from)))
