(ns kimun.surface.journal
  "The journal as a read-only view over the log and the revisions (docs/v2/02
   §5): like `git log` / `git diff`, nothing is stored and nothing is written on
   commit. `entries` derives one entry per logged transaction from the opened
   store and the transaction vector; `verify` checks the revision DAG
   (every id hashes its content, every parent exists, the log agrees with the
   store's revisions and head). Pure: no I/O."
  (:require [clojure.edn :as edn]
            [kimun.kernel.canon :as canon]
            [kimun.kernel.plan :as plan]
            [kimun.kernel.revision :as revision]))

(defn- tree-info
  "{:name :kind} of tree `tid` in `store`, or an empty map when unknown."
  [store tid]
  (let [d (get-in store [:trees tid :descriptor])]
    {:name (:name d) :kind (:kind d)}))

(defn- node-texts
  "The text content of those of the node `ids` that are text nodes."
  [store ids]
  (vec (keep #(let [n (revision/get-object store %)]
                (when (= :text (:kind n)) (get-in n [:content :text])))
             ids)))

(defn- tree-change
  "The summary of one touched tree in a diff: counts of nodes added, removed and
   moved and, for `:document` trees, the text before and after. Extra keys
   (field-level detail) may be added without changing these."
  [store r1 tid d]
  (let [{:keys [name kind]} (tree-info store tid)
        before (node-texts store (:removed d))
        after (node-texts store (:added d))]
    (cond-> {:tree tid :name name :kind kind
             :created? (nil? (revision/tree-entry store tid r1))
             :nodes-added (count (:added d))
             :nodes-removed (count (:removed d))
             :moved (count (:moved d))}
      (and (= :document kind) (or (seq before) (seq after)))
      (assoc :text {:before before :after after}))))

(defn- changes
  "Per-tree change summaries of revision `rev-id` against its first parent
   (nil for the first revision)."
  [store rev-id parent]
  (->> (:trees (revision/diff store parent rev-id))
       (map (fn [[tid td]] (tree-change store parent tid td)))
       (sort-by :tree)
       vec))

(defn- entry
  "The journal entry of transaction `tx` (`{:id :plan :revision}`)."
  [store tx]
  (let [rev-id (:revision tx)
        rev (revision/revision store rev-id)
        parent (first (:parents rev))
        ch (changes store rev-id parent)
        touched (into (set (map :tree ch)) (filter string?) (plan/touched-trees (:plan tx)))]
    (merge
     (when-let [m (some-> (get (:engines rev) "kimun/meta") edn/read-string)]
       (select-keys m [:operation :address :fields]))
     {:revision rev-id
     :parents (vec (:parents rev))
     :actor (:actor rev)
     :timestamp (:timestamp rev)
     :transaction (:id tx)
     :ops (frequencies (map :op (get-in tx [:plan :ops])))
     :trees (vec (sort-by :tree (map #(assoc (tree-info store %) :tree %) touched)))
     :changes ch})))

(defn- matches-tree? [e token]
  (boolean (some #(or (= token (:tree %)) (= token (:name %))) (:trees e))))

(defn entries
  "Journal entries of `store` for the logged transactions `txs`, newest first:
   `{:revision :parents :actor :timestamp :transaction :ops {op n}
     :trees [{:tree :name :kind}] :changes [{:tree :name :kind :created?
     :nodes-added :nodes-removed :moved (:text {:before [..] :after [..]})}]}`,
   plus, when the writing command recorded them (`kimun.surface.docs`):
   `:operation` (e.g. \"docs update\"), `:address` (`Model:doc`) and `:fields
   {path {:before v :after v}}` with dotted paths for nested keys.
   Transactions whose revision is not in `store` are skipped (see `verify`).
   `opts`: `:actor` keeps entries by that actor, `:tree` those touching the tree
   with that id or name, `:limit` keeps the first n (newest)."
  ([store txs] (entries store txs {}))
  ([store txs {:keys [actor tree limit]}]
   (cond->> (->> txs
                 (filter #(revision/revision store (:revision %)))
                 reverse
                 (mapv #(entry store %)))
     actor (filter #(= actor (:actor %)))
     tree (filter #(matches-tree? % tree))
     limit (take limit)
     true vec)))

(defn verify
  "Integrity of the revision DAG: for the logged transactions `txs`, every
   revision exists in `store`, its id is the digest of its content, every parent
   exists, the transaction's `:tx` matches, and the last logged revision is the
   store head. Returns `{:ok? bool :revisions n :checked n :bad [{:revision id
   :reason kw}]}` with reasons `:missing-revision`, `:hash-mismatch`,
   `:missing-parent`, `:transaction-mismatch` and `:head-mismatch`."
  [store txs]
  (let [host (:host store)
        revs (:revisions store)
        per-tx (fn [{:keys [id revision]}]
                 (let [rev (get revs revision)]
                   (cond
                     (nil? rev) [{:revision revision :reason :missing-revision}]
                     :else
                     (concat
                      (when (not= revision (canon/digest host rev)) [{:revision revision :reason :hash-mismatch}])
                      (for [p (:parents rev) :when (not (contains? revs p))]
                        {:revision revision :reason :missing-parent :parent p})
                      (when (not= id (:tx rev)) [{:revision revision :reason :transaction-mismatch}])))))
        head (:revision (last txs))
        bad (vec (concat (mapcat per-tx txs)
                         (when (and (seq txs) (not= head (:head store)))
                           [{:revision head :reason :head-mismatch :head (:head store)}])
                         (when (and (empty? txs) (:head store))
                           [{:revision (:head store) :reason :head-mismatch :head (:head store)}])))]
    {:ok? (empty? bad) :revisions (count revs) :checked (count txs) :bad bad}))
