(ns kimun.surface.changes
  "\"What changed since commit X\" in two levels (docs/v2/drafts/diseno-declaraciones.md §3, §9).
   Up to the file, git says (its answer arrives as data, this namespace never runs git); inside
   the file, only what is anchored is read: for each mark of a changed file the state is the
   existing `mark-states` table applied to the text git keeps at X (what the locator held
   then) against the text now. A changed file without marks is not opened."
  (:require [kimun.surface.docs :as tdocs]
            [kimun.surface.markdown.anchors :as anchors]))

(defn- docs-by-path
  "Every tracked document (typed or generic) with a file, as `{path [doc …]}`."
  [store]
  (group-by :path (filter :path (concat (tdocs/documents store) (tdocs/generic-documents store)))))

(defn- mark-view [d id m]
  {:id id :kind (:kind m) :label (:label m) :locator (:locator m) :document (:name d) :path (:path d)})

(defn- before-hash
  "The hash the locator of `m` holds in `then-text` (read from git), or nil."
  [host store d then-text m]
  (when then-text
    (:hash (anchors/resolve-anchor (tdocs/mark-anchors host store d then-text) (:locator m)))))

(defn- marks-of-file
  "The affected marks of document `d`: state of each against `now-text` (nil: the file is
   gone), comparing with what `then-text` held (nil: the file did not exist at X)."
  [host store d then-text now-text]
  (let [now (when now-text (tdocs/mark-anchors host store d now-text))]
    (vec (for [[id m] (sort-by key (or (:marks d) {})) :let [view (mark-view d id m)
                                                          h (before-hash host store d then-text m)]]
           (cond
             (nil? then-text)
             (assoc view :status :unknown :reason :file-not-at-since :candidates [])
             (nil? h)
             (assoc view :status :unknown :reason :unresolved-at-since :candidates [])
             (nil? now-text)
             (assoc view :status :missing :candidates [])
             :else
             (merge view (select-keys (first (anchors/mark-states now {id (assoc m :hash h)}))
                                      [:status :candidates])))))))

(defn since
  "The answer to \"what changed since X\". `git` is what the host learned from git:
   `{:since sha :head sha :changes [{:path :change :from?}]}`; `read-then`/`read-now` are
   `(fn [relative-path] text-or-nil)` for the text at X and the text on disk. Data:
   `{:since :head :dirty :changed [{:path :change :from? :known :documents :opened}] :marks
   [{:id :kind :label :locator :document :path :status :candidates :reason?}] :summary}`.
   `:known` is whether the store registers a document at that path; `:opened` whether the
   file was read (only a changed file with a mark is). A mark of an unchanged file is not
   evaluated. `:status` is `:anchored | :mutated | :ambiguous | :missing`, or `:unknown`
   with `:reason` when X does not tell what the locator held."
  [host store {:keys [since head dirty changes]} {:keys [read-then read-now]}]
  (let [by-path (docs-by-path store)
        rows (for [{:keys [path from] :as c} changes
                   :let [docs (concat (get by-path path) (when from (get by-path from)))
                         marked (filter (comp seq :marks) docs)]]
               {:row (cond-> {:path path :change (:change c) :known (boolean (seq docs))
                              :documents (mapv :name docs) :opened (boolean (seq marked))}
                       from (assoc :from from))
                :marks (when (seq marked)
                         (let [then (read-then (or from path)) now (read-now path)]
                           (vec (mapcat #(marks-of-file host store % then now) marked))))})
        changed (mapv :row rows)
        marks (vec (mapcat :marks rows))]
    {:since since :head head :dirty dirty :changed changed :marks marks
     :summary {:changed (count changed) :known (count (filter :known changed))
               :opened (count (filter :opened changed)) :marks (count marks)
               :by-status (frequencies (map :status marks))}}))
