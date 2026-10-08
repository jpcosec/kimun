(ns kimun.kernel.upgrade
  "Upgrade of a format-1 store to format 2 (docs/v2/10-escala.md, the upgrade
   path). Host-independent: it reads one Backend and writes another, which
   must be empty; choosing directories and swapping them is the caller's
   (kimun `stores upgrade`).

   What it does: every committed transaction of the source log, in log order,
   is committed again into the destination through the format-2 engine
   (`kimun.kernel.store/commit!`), with its resolved plan unchanged except for
   :base, rewritten to the format-2 id of the same revision. Node, edge and
   tree-object ids do not depend on the revision format, so every node, edge
   and tree (descriptor and merkle root) comes out the same; actor, timestamp,
   engines and plan ops of every transaction are the plan's own, and the
   parent chain follows log order as it did. Revision ids and transaction ids
   change (a Revision's fields differ by format and a transaction id hashes
   its plan, :base included): the result maps old -> new for both.

   What it checks before returning (any failure raises :store/error and the
   destination must be discarded): each transaction re-commits without a
   rebase and with the same resolved plan; the final heads are the source
   heads mapped (store head and every per-tree head); the head trees
   (tree-id -> [descriptor-id merkle-root]) and the active edge set equal the
   ones the source head Revision records; and `kimun.kernel.store/verify-full`
   (full replay of the new log plus the hash of every reachable object) is ok.
   An :uncommitted tail of the source log (a writer that died mid-commit) is
   not carried over: it never was a commit; it is returned as :dropped-tail."
  (:require [kimun.kernel.err :as err]
            [kimun.kernel.ports :as ports]
            [kimun.kernel.revision :as revision]
            [kimun.kernel.store :as store]))

(defn- fail [why data] (err/raise :store/error (str "upgrade: " why) data))

(defn- format-1-head-state
  "{:trees {tid [descriptor-id merkle-root]} :edges #{edge-id}} recorded by the
   format-1 Revision `rev-id` of backend `b` (reads three objects; no replay)."
  [b rev-id]
  (if (nil? rev-id)
    {:trees {} :edges #{}}
    (let [rev (or (store/load-object b rev-id) (fail "source head revision is missing" {:id rev-id}))
          descs (into {} (store/load-object b (:trees rev)))]
      {:trees (into {} (map (fn [[tid root]] [tid [(get descs tid) root]])) (:roots rev))
       :edges (set (store/load-object b (:edges rev)))})))

(defn- format-2-head-state
  "The same shape as `format-1-head-state`, from a format-2 store value."
  [s]
  {:trees (into {} (map (fn [tid] [tid (revision/tree-entry s tid)])) (revision/tree-ids s))
   :edges (set (revision/active-edges s))})

(defn upgrade!
  "Re-commits the history of the format-1 store in backend `src` into the
   empty backend `dst` as a format-2 store (see the ns docstring), with the
   descriptor of `src` (every key kept) at :format-version 2. Returns
   {:transactions n :head new-head :revisions {old-rev new-rev}
    :transaction-ids {old-tx new-tx} :dropped-tail entry|nil :verify map}.
   `src` is only read. Raises :store/error when `src` is not a format-1 store,
   `dst` is not empty, the source log does not end at its heads, or any check
   fails."
  [src dst host]
  (let [desc (or (store/read-descriptor src) (fail "the source is not a store" {}))
        alg (ports/algorithm (ports/hasher host))]
    (when (not= 1 (:format-version desc 1)) (fail "the source is not a format-1 store" {:format-version (:format-version desc)}))
    (when (not= alg (:hash-alg desc)) (fail "hash algorithm mismatch" {:store (:hash-alg desc) :hasher alg}))
    (when (store/read-descriptor dst) (fail "the destination is not empty" {}))
    (let [heads (or (store/read-heads src) store/no-heads)
          log (vec (store/read-log src))
          state (store/log-tail heads (subvec log (max 0 (- (count log) 2))))
          _ (when (= :inconsistent state) (fail "the source log does not end at its heads; run a full verification" {:head (:head heads)}))
          committed (if (= :uncommitted state) (pop log) log)
          _ (store/write-descriptor! dst (assoc desc :format-version 2))
          s0 (revision/empty-store host (:capabilities desc))
          result (reduce
                  (fn [{:keys [store revs txs]} {:keys [id plan revision] :as entry}]
                    (when-not (store/transaction? entry) (fail "unreadable source log line" {:entry entry}))
                    (let [base (:base plan)
                          plan' (cond-> plan base (assoc :base (or (get revs base) (fail "a plan names a base that is not an earlier revision" {:tx id :base base}))))
                          r (store/commit! dst store plan')]
                      (when (:rebased-from r) (fail "a transaction rebased while upgrading" {:tx id}))
                      (when (not= plan' (:plan (:transaction r))) (fail "a re-committed plan differs from the logged one" {:tx id}))
                      {:store (:store r)
                       :revs (assoc revs revision (:revision-id r))
                       :txs (assoc txs id (:id (:transaction r)))}))
                  {:store s0 :revs {} :txs {}}
                  committed)
          {s :store revs :revs txs :txs} result
          mapped {:head (get revs (:head heads)) :heads (into {} (map (fn [[t r]] [t (get revs r)])) (:heads heads))}]
      (when (not= mapped {:head (:head s) :heads (:heads s)})
        (fail "the new heads are not the source heads" {:expected mapped :got {:head (:head s) :heads (:heads s)}}))
      (when (not= (format-1-head-state src (:head heads)) (format-2-head-state s))
        (fail "the head trees or the active edges differ from the source head" {:head (:head heads)}))
      (let [v (store/verify-full dst host)]
        (when-not (:ok? v) (fail "the new store does not verify" {:bad (:bad v)}))
        {:transactions (count committed) :head (:head s) :revisions revs :transaction-ids txs
         :dropped-tail (when (= :uncommitted state) (peek log))
         :verify (dissoc v :store)}))))
