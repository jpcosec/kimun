(ns kimun.surface.declared-anchors
  "The anchors written in declarations, resolved against the files and compared
   with what git keeps (docs/v2/drafts/diseno-evaluar.md §5, cut 2). Pure: the
   texts arrive through `read-now` / `read-then` (`(fn [path] text-or-nil)`)
   and the paths git reports as changed arrive as data; nothing is read from
   disk, stored or hashed beyond what `kimun.surface.markdown.anchors` does.

   FORMS of `(at path & locator)` (the locator is what follows the path; the
   evaluator keeps it as the vector `:locator`), and what each becomes:
   - `[]`                          the whole file (kind `:file`: the hash of its
                                   text, any file type);
   - `[PATH]`                      a section (`:section`);
   - `[PATH {:type T :n N}]`       a block of that section (`:block`);
   - `[PATH {:type T :n N} [S E]]` a range inside the block (`:span`).
   PATH is a vector of titles, from the outermost section down; an element is
   `\"Title\"` or `[\"Title\" K]` (K = 1-based position among the sisters with the
   same title). `N` is likewise 1-based among the blocks of type `T` that are
   direct content of the section (`[]` = before the first heading); `anchors`
   numbers from 0 and the translation subtracts one. `[S E]` counts graphemes of
   the plain text of the block, end excluded, as `anchors/span-anchor` does.
   Only Markdown files (`.md`, `.markdown`) have inner locators; anything else
   with one is `:unsupported`.

   A badly formed locator, or a path that is absolute or leaves the project, is
   `:declare/bad-locator` carrying the `:file` and `:line` of the declaration."
  (:require [clojure.string :as str]
            [kimun.kernel.canon :as canon]
            [kimun.kernel.err :as err]
            [kimun.surface.markdown.anchors :as anchors]))

(def ^:private block-types #{:heading :paragraph :list :code :quote :opaque})

(defn- bad
  "Raises `declare/bad-locator` for the declaration at `at`."
  [{:keys [file line]} msg data]
  (err/raise :declare/bad-locator (str file ":" line ": " msg) (merge {:file file :line line} data)))

(defn- path-element
  "`\"T\"` or `[\"T\" K]` as an anchors section step `{:title :n}`, or nil."
  [e]
  (cond (string? e) {:title e :n 0}
        (and (vector? e) (= 2 (count e)) (string? (first e)) (pos-int? (second e)))
        {:title (first e) :n (dec (second e))}))

(defn- section-path
  "The `anchors` path of the vector `v` of titles, or nil when malformed."
  [v]
  (when (vector? v)
    (let [steps (mapv path-element v)]
      (when (every? some? steps) steps))))

(defn- block-part
  "`{:type T :n N}` → `[T (dec N)]`, or nil."
  [m]
  (when (and (map? m) (= #{:type :n} (set (keys m)))
             (block-types (:type m)) (pos-int? (:n m)))
    [(:type m) (dec (:n m))]))

(defn- range-part [r]
  (when (and (vector? r) (= 2 (count r)) (every? integer? r) (<= 0 (first r)) (< (first r) (second r)))
    r))

(defn- markdown? [path] (boolean (re-find #"(?i)\.(md|markdown)$" path)))

(defn- clean-path
  "`path` without a leading `./`, or a bad-locator when it is blank, absolute or leaves the
   project directory."
  [at path]
  (let [p (str/replace (str path) #"^(\./)+" "")]
    (when (or (str/blank? p) (str/starts-with? p "/") (re-find #"(^|/)\.\.(/|$)" p) (re-find #"^[A-Za-z]:[\\/]" p))
      (bad at (str "the path of `at` must be a file inside the project, relative to its directory: " (pr-str path)) {:path (str path)}))
    p))

(defn translate
  "The anchor `{:path :locator}` of a declaration (`at` = its `{:file :line}`) as
   `{:path p :kind k :locator l :markdown? b}`: `k` is `:file`, `:section`,
   `:block` or `:span`, `l` the locator `kimun.surface.markdown.anchors` understands
   (nil for `:file`). Raises `:declare/bad-locator` when the form is not one of the
   documented ones."
  [at {:keys [path locator]}]
  (let [p (clean-path at path)
        md? (markdown? p)
        base {:path p :markdown? md?}
        form (fn [] (pr-str locator))
        malformed (fn [why] (bad at (str "malformed locator of `at` " (pr-str p) ": " why " (got " (form) ")") {:path p}))]
    (when-not (vector? locator) (malformed "the locator must be a sequence"))
    (case (count locator)
      0 (assoc base :kind :file :locator nil)
      (let [[v blk rng & more] locator
            steps (section-path v)]
        (when more (malformed "at most a path, a block and a range"))
        (when-not steps (malformed "the first element is a vector of titles, each \"T\" or [\"T\" K] with K >= 1"))
        (if (nil? blk)
          (do (when (empty? steps) (malformed "the root section is not an anchor; name a block of it: [] {:type … :n …}"))
              (assoc base :kind :section :locator steps))
          (let [[t n] (or (block-part blk) (malformed "the block is {:type T :n N}, T one of heading paragraph list code quote opaque, N >= 1"))
                loc {:section steps :type t :n n}]
            (if (nil? rng)
              (assoc base :kind :block :locator loc)
              (let [r (or (range-part rng) (malformed "the range is [start end] of whole numbers with 0 <= start < end"))]
                (assoc base :kind :span :locator {:block loc :range r})))))))))

(defn- unsupported
  [t] (when (and (not= :file (:kind t)) (not (:markdown? t)))
        "an inner locator needs a Markdown file; other file types can only be anchored whole: (at \"path\")"))

(defn- candidate-view [c] (select-keys c [:locator :reason :lines]))

(defn- resolve-in
  "The anchor of `t` in `an` (the anchors of a text): `{:hash h}`, or `{:why s}` when nothing resolves."
  [an t]
  (let [r (anchors/resolve-anchor an (:locator t))]
    (if r
      {:hash (:hash r) :lines (:lines r)}
      {:why (when (= :span (:kind t))
              (:why (anchors/span-anchor an (:locator t))))})))

(defn- state
  "The state of the declared anchor `t`. `ctx` holds the memoized readers (`:now`/`:then`
   return the text or nil, `:now-anchors`/`:then-anchors` the anchors of a path or nil),
   `:compare?` and `:changed` (set of paths)."
  [host {:keys [compare? changed now then now-anchors then-anchors]} t]
  (let [path (:path t)]
    (cond
      (unsupported t)
      {:status :unsupported :reason (unsupported t) :candidates [] :opened false}

      (and compare? (not (contains? changed path)))
      {:status :anchored :candidates [] :opened false}

      (not compare?)
      (let [text (now path)]
        (cond
          (nil? text) {:status :file-missing :candidates [] :opened true}
          (= :file (:kind t)) {:status :resolved :hash (canon/digest host text) :candidates [] :opened true}
          :else (let [r (resolve-in (now-anchors path) t)]
                  (if (:hash r)
                    (cond-> {:status :resolved :hash (:hash r) :candidates [] :opened true}
                      (:lines r) (assoc :lines (:lines r)))
                    (cond-> {:status :unresolved :candidates [] :opened true}
                      (:why r) (assoc :why (:why r)))))))

      :else
      (let [before-text (then path)
            now-text (now path)
            before (cond (nil? before-text) nil
                         (= :file (:kind t)) {:hash (canon/digest host before-text)}
                         :else (resolve-in (then-anchors path) t))]
        (cond
          (nil? before-text) {:status :unknown :reason :file-not-at-since :candidates [] :opened true}
          (nil? (:hash before)) {:status :unknown :reason :unresolved-at-since :candidates [] :opened true}
          (nil? now-text) {:status :missing :before (:hash before) :candidates [] :opened true}
          (= :file (:kind t))
          (let [h (canon/digest host now-text)]
            {:status (if (= h (:hash before)) :anchored :mutated) :hash h :before (:hash before) :candidates [] :opened true})
          :else
          (let [s (first (anchors/mark-states (now-anchors path) {0 {:kind (:kind t) :locator (:locator t) :hash (:hash before)}}))
                now-a (resolve-in (now-anchors path) t)]
            (cond-> {:status (:status s) :before (:hash before)
                     :candidates (mapv candidate-view (:candidates s)) :opened true}
              (:hash now-a) (assoc :hash (:hash now-a)))))))))

(defn- nodes-of
  "`[[role node other-node] …]` for the `{:anchor …}` arguments of proposition `p`."
  [p]
  (concat (when (and (map? (:a p)) (:anchor (:a p))) [[:a (:a p) (:b p)]])
          (when (and (map? (:b p)) (:anchor (:b p))) [[:b (:b p) (:a p)]])))

(defn states
  "The state of every anchor written in `propositions` (as `kimun.surface.declare`
   returns them), one row per `{:anchor …}` node used as `:a` or `:b`, in order:
   `{:proposition id :role :a|:b :r :other node :anchor {:path :locator} (as declared)
   :kind :declared-at {:file :line} :status :candidates :opened …}` plus `:hash` (now), `:before`, `:reason`
   or `:why` when they apply. The shape of every locator is validated first (any bad one
   raises before a file is read).

   `opts`: `:read-now` and `:read-then` `(fn [path] text-or-nil)`; `:changed` the set of
   paths git reports as changed, or nil for no comparison. Without comparison the status
   is `:resolved`, `:unresolved` or `:file-missing` (`:candidates` is then always empty:
   `anchors` proposes candidates from a hash, and there is none to compare). With it,
   `:anchored :mutated :ambiguous :missing` (the `mark-states` table, candidates listed
   and none chosen) or `:unknown` with `:reason` `:file-not-at-since` /
   `:unresolved-at-since`. A path git does not report as changed is not opened:
   `:anchored`, `:opened false`. An inner locator on a non-Markdown file is
   `:unsupported` with `:reason`."
  [host propositions {:keys [read-now read-then changed]}]
  (let [items (vec (for [p propositions
                         [role node other] (nodes-of p)]
                     {:p p :role role :other other :anchor (:anchor node) :t (translate (:at p) (:anchor node))}))
        memo (fn [f] (let [m (atom {})]
                       (fn [k] (if (contains? @m k) (get @m k) (let [v (f k)] (swap! m assoc k v) v)))))
        now (memo read-now)
        then (memo (or read-then (constantly nil)))
        an-of (fn [read] (memo (fn [path] (anchors/anchors host (read path) {:leaves true}))))
        ctx {:compare? (some? changed) :changed (set changed) :now now :then then
             :now-anchors (an-of now) :then-anchors (an-of then)}]
    (mapv (fn [{:keys [p role other t anchor]}]
            (merge {:proposition (:id p) :role role :r (:r p) :other other
                    :anchor anchor :kind (:kind t)
                    :declared-at (:at p)}
                   (state host ctx t)))
          items)))
