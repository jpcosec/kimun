(ns sldb.kernel.store-test
  (:require [clojure.test :refer [deftest is testing]]
            [sldb.kernel.test-util :refer [defspec]]
            [clojure.test.check.generators :as gen]
            [clojure.test.check.properties :as prop]
            [clojure.edn :as edn]
            [clojure.string :as str]
            [sldb.host.default :as host]
            [sldb.kernel.revision :as rev]
            [sldb.kernel.store :as store]
            [sldb.host.fs-store :as fs]
            [sldb.kernel.generators :as g]
            #?@(:clj [[clojure.java.io :as io]
                      [babashka.fs :as bfs]
                      [babashka.process :as p]])))

(def h host/host)

(defn- read-fixture [f] #?(:clj (edn/read-string (slurp (io/file f)))))
(defn- tmp-dir [] #?(:clj (str (bfs/create-temp-dir {:prefix "sldb-store-"}))))

(defn- snapshot [s]
  {:head (:head s) :heads (:heads s)
   :revisions (:revisions s)
   :roots (into {} (map (fn [[tid t]] [tid (sldb.kernel.tree/merkle-root t)])) (:trees s))
   :edges (:edges s)
   :nodes (set (keep (fn [[id o]] (when (:class o) id)) (:objects s)))
   :tree-sets (set (map :trees (vals (:revisions s))))
   :edge-sets (set (map :edges (vals (:revisions s))))})

(defn- populate!
  "init + tx-001 + the base plans of tx-002 in `dir`; returns the final in-memory store."
  [dir]
  (let [b (fs/backend dir)
        fx1 (read-fixture "test/fixtures/tx-001.edn")
        fx2 (sldb.kernel.test-util/format-2-fixture (read-fixture "test/fixtures/tx-002-conflict.edn"))
        s0 (store/init! b h (get-in fx1 [:store :capabilities]))
        s1 (:store (store/commit! b s0 (:plan fx1)))
        s2 (:store (store/commit! b s1 (second (:base-plans fx2))))]
    s2))

(deftest close-and-reopen-reproduces-state-and-hashes-invariants-1-3-10
  (let [dir (tmp-dir)
        before (populate! dir)
        after (store/open (fs/backend dir) h)]
    (is (= (snapshot before) (snapshot after)))
    (is (= 2 (count (:revisions after))))))

(deftest replay-from-log-and-objects-only
  (let [dir (tmp-dir)
        before (populate! dir)
        _ (bfs/delete (str dir "/heads.edn"))
        after (store/replay (fs/backend dir) h)]
    (is (= (:head before) (:head after)))
    (is (= (:heads before) (:heads after)))
    (is (= (:revisions before) (:revisions after)))))

(deftest every-object-file-hashes-to-its-name-invariant-17
  (let [dir (tmp-dir)
        s (populate! dir)
        b (fs/backend dir)
        v (store/verify b s)]
    (is (:ok? v) (pr-str (:bad v)))
    (is (pos? (:checked v)))
    (is (every? #(= % (sldb.kernel.ports/digest-str (:hasher h) (store/read-object b %))) (store/object-ids b)))))

(deftest corrupting-one-object-makes-verify-fail-naming-it
  (let [dir (tmp-dir)
        s (populate! dir)
        b (fs/backend dir)
        victim (first (sort (store/reachable s)))]
    (spit (str dir "/objects/" victim) "tampered")
    (let [v (store/verify b s)]
      (is (not (:ok? v)))
      (is (= [{:id victim :reason :hash-mismatch}] (:bad v))))))

;; "without writing" = neither log.edn nor heads.edn change and the store still
;; opens and verifies; the loser's write-once objects stay, unreferenced.
(deftest stale-heads-cas-is-rejected-without-writing
  (let [dir (tmp-dir)
        s (populate! dir)
        b (fs/backend dir)
        fx2 (sldb.kernel.test-util/format-2-fixture (read-fixture "test/fixtures/tx-002-conflict.edn"))
        stale (store/open b h)
        ;; another writer commits first
        _ (store/commit! b s (assoc (:disjoint-plan fx2) :base (:head s)))
        heads-before (store/read-heads b)
        log-before (slurp (str dir "/log.edn"))
        e (try (store/commit! b stale (assoc (:disjoint-plan fx2) :base (:head stale) :timestamp "2026-08-29T12:00:09.000Z"
                                             :ops [{:op :add-node :node {:class :sign :kind :text :content {:text "late"}} :as :x}
                                                   {:op :add-node :node {:class :sign :kind :block :content {:format :markdown :type :document :attrs {:name "late"}}} :as :r}
                                                   {:op :new-tree :tree {:kind :document :name "late"} :id "01ARZ3NDEKTSV4RRFFQ69G5FAX" :root :r}]))
               nil
               (catch #?(:clj clojure.lang.ExceptionInfo :cljs :default) e (ex-data e)))]
    (is (= :store/stale (:type e)))
    (is (= heads-before (store/read-heads b)) "heads.edn untouched")
    (is (= log-before (slurp (str dir "/log.edn"))) "log.edn untouched, byte for byte")
    (let [reopened (store/open b h)]
      (is (= heads-before {:head (:head reopened) :heads (:heads reopened)}) "the store still opens, at the winner's heads")
      (is (= {:head (:head reopened) :heads (:heads reopened)}
             (select-keys (store/replay-open b h) [:head :heads])) "the full replay agrees")
      (is (:ok? (store/verify b reopened)))
      (is (nil? (:uncommitted-tail (store/verify b reopened)))))))

;; ---------------------------------------------------------------- losing writer / dead writer

(defn- doc-plan
  "A plan on top of `s` creating document tree number `i`."
  [s i]
  {:plan/version 1 :engines {} :base (:head s) :actor "jp" :timestamp "2026-08-29T12:00:00.000Z"
   :ops [{:op :add-node :node {:class :sign :kind :block :content {:format :markdown :type :document :attrs {:name (str "d" i)}}} :as :r}
         {:op :new-tree :tree {:kind :document :name (str "d" i)} :id (str "01ARZ3NDEKTSV4RRFFQ69G" (subs (str (+ 10000 i)) 1)) :root :r}]})

(defn- mem-backend
  "In-memory Backend: one atom, commit-tx! is a plain compare-and-set."
  []
  (let [state (atom {:objects {} :log []})]
    (reify store/Backend
      (read-descriptor [_] (:descriptor @state))
      (write-descriptor! [_ d] (swap! state assoc :descriptor d))
      (read-object [_ id] (get-in @state [:objects id]))
      (write-object! [_ id content] (swap! state update-in [:objects id] #(or % content)))
      (object-ids [_] (keys (:objects @state)))
      (read-log [_] (:log @state))
      (read-heads [_] (:heads @state))
      (commit-tx! [_ expected tx new]
        (let [[old _] (swap-vals! state (fn [st] (if (= expected (or (:heads st) store/no-heads))
                                                  (-> st (update :log conj tx) (assoc :heads new))
                                                  st)))]
          (= expected (or (:heads old) store/no-heads)))))))

(defn- ex-of [f] (try (f) nil (catch #?(:clj clojure.lang.ExceptionInfo :cljs :default) e (ex-data e))))
(defn- heads-of [s] {:head (:head s) :heads (:heads s)})

(deftest two-writers-on-the-same-loaded-store-the-loser-leaves-nothing-visible
  (doseq [[kind mk] [[:fs #(fs/backend (tmp-dir))] [:memory mem-backend]]
          fv [1 2]]
    (testing (str kind " backend, format " fv)
      (let [b (mk)
            s0 (store/init! b h {"jp" :all} {:format-version fv})
            loaded (:store (store/commit! b s0 (doc-plan s0 1)))   ; both writers hold this value
            winner (store/commit! b loaded (doc-plan loaded 2))
            log-before (store/read-log b)
            heads-before (store/read-heads b)
            loser-tx (:id (:transaction (rev/apply-plan loaded (doc-plan loaded 3))))
            e (ex-of #(store/commit! b loaded (doc-plan loaded 3)))]
        (is (= :store/stale (:type e)))
        (is (= log-before (store/read-log b)) "the log has no line from the loser")
        (is (not (contains? (set (map :id (store/read-log b))) loser-tx)))
        (is (= heads-before (store/read-heads b) (heads-of (:store winner))))
        (let [reopened (store/open b h)
              v (store/verify b reopened)]
          (is (= heads-before (heads-of reopened)) "opens at the winner's heads")
          (is (= heads-before (heads-of (store/replay-open b h))) "passes the full replay verification")
          (is (:ok? v) (pr-str (:bad v)))
          (is (not (contains? v :uncommitted-tail)))
          (is (nil? (store/uncommitted-tail b)))
          (testing "the retry on a freshly opened store succeeds"
            (let [retry (store/commit! b reopened (doc-plan reopened 3))
                  again (store/open b h)]
              (is (= (heads-of (:store retry)) (heads-of again) (heads-of (store/replay-open b h))))
              (is (= 3 (count (store/read-log b))))
              (is (= 3 (rev/tree-count again)))
              (is (:ok? (store/verify b again))))))))))

#?(:clj
   (deftest concurrent-writers-on-the-fs-backend-exactly-one-wins
     (doseq [fv [1 2]]
       (let [dir (tmp-dir)
             b (fs/backend dir)
             s0 (store/init! b h {"jp" :all} {:format-version fv})
             loaded (:store (store/commit! b s0 (doc-plan s0 1)))
             results (mapv deref (mapv (fn [i] (future (try (store/commit! (fs/backend dir) loaded (doc-plan loaded (+ 2 i))) :won
                                                           (catch clojure.lang.ExceptionInfo e (:type (ex-data e))))))
                                       (range 8)))]
         (is (= {:won 1 :store/stale 7} (frequencies results)))
         (is (= 2 (count (store/read-log b))) "one line per winner, none per loser")
         (let [reopened (store/replay-open b h)]
           (is (= 2 (rev/tree-count reopened)))
           (is (:ok? (store/verify b reopened))))))))

(deftest log-tail-rule
  (let [tx (fn [r] {:id (str "t" r) :plan {} :revision r})
        hd (fn [r] {:head r :heads {}})]
    (is (= :clean (store/log-tail nil [])))
    (is (= :clean (store/log-tail (hd nil) [])))
    (is (= :clean (store/log-tail (hd "b") [(tx "a") (tx "b")])))
    (is (= :clean (store/log-tail (hd "a") [(tx "a")])))
    (is (= :uncommitted (store/log-tail nil [(tx "a")])) "first commit died before writing heads")
    (is (= :uncommitted (store/log-tail (hd "a") [(tx "a") (tx "b")])))
    (is (= :uncommitted (store/log-tail (hd "a") [(tx "a") {:torn-line 2}])) "died mid-append")
    (is (= :uncommitted (store/log-tail nil [{:torn-line 1}])))
    (is (= :inconsistent (store/log-tail (hd "a") [(tx "b") (tx "c")])) "two lines beyond the heads")
    (is (= :inconsistent (store/log-tail nil [(tx "a") (tx "b")])) "heads.edn lost")
    (is (= :inconsistent (store/log-tail (hd "b") [(tx "a") {:torn-line 2}])) "the committed line itself is damaged")
    (is (= :inconsistent (store/log-tail (hd "z") [])) "heads without a log")
    (is (= :inconsistent (store/log-tail (hd "z") [(tx "a")])))))

#?(:clj
   (deftest a-writer-dead-between-log-append-and-heads-write-is-ignored-reported-and-repaired
     ;; the half-written state is produced through the files of the backend: what
     ;; commit-tx! leaves when the process dies after its append (or inside it)
     (doseq [fv [1 2]
             [case-name first-commit? dead-line] [[:whole-line false (fn [r] (str (pr-str (:transaction r)) "\n"))]
                                                  [:torn-line false (fn [r] (subs (pr-str (:transaction r)) 0 40))]
                                                  [:first-commit-of-the-store true (fn [r] (str (pr-str (:transaction r)) "\n"))]]]
       (testing (str "format " fv " " case-name)
         (let [dir (tmp-dir)
               b (fs/backend dir)
               s0 (store/init! b h {"jp" :all} {:format-version fv})
               committed (if first-commit? s0 (:store (store/commit! b s0 (doc-plan s0 1))))
               dead (rev/apply-plan committed (doc-plan committed 2))
               n (if first-commit? 0 1)]
           (spit (str dir "/log.edn") (dead-line dead) :append true)
           (is (some? (store/uncommitted-tail b)))
           (is (= n (count (store/committed-log b))))
           (let [opened (store/open b h)
                 v (store/verify b opened)]
             (is (= (heads-of committed) (heads-of opened) (heads-of (store/replay-open b h))) "open ignores the dead writer's line")
             (is (:ok? v) "reported, not an error")
             (is (= (store/uncommitted-tail b) (:uncommitted-tail v)))
             (let [r (store/commit! b opened (doc-plan opened 3))
                   after (store/replay-open b h)]
               (is (= (inc n) (count (store/read-log b))) "the next commit discarded the dead line before appending")
               (is (not (contains? (set (map :id (store/read-log b))) (:id (:transaction dead)))))
               (is (str/ends-with? (slurp (str dir "/log.edn")) "\n"))
               (is (= (heads-of (:store r)) (heads-of after)))
               (is (nil? (store/uncommitted-tail b)))
               (is (not (contains? (store/verify b after) :uncommitted-tail)))
               (is (:ok? (store/verify b after))))))))))

#?(:clj
   (deftest a-log-that-does-not-end-at-the-heads-refuses-further-commits
     (doseq [fv [1 2]]
       (let [dir (tmp-dir)
             b (fs/backend dir)
             s0 (store/init! b h {"jp" :all} {:format-version fv})
             s1 (:store (store/commit! b s0 (doc-plan s0 1)))
             r2 (rev/apply-plan s1 (doc-plan s1 2))
             r3 (rev/apply-plan (:store r2) (doc-plan (:store r2) 3))
             _ (spit (str dir "/log.edn") (str (pr-str (:transaction r2)) "\n" (pr-str (:transaction r3)) "\n") :append true)
             log-before (slurp (str dir "/log.edn"))
             heads-before (store/read-heads b)]
         (is (nil? (store/uncommitted-tail b)) "two lines beyond the heads are not a dead writer's tail")
         (is (= :store/error (:type (ex-of #(store/replay-open b h)))) "the full replay refuses")
         (is (= [:replay-failed] (map :reason (:bad (store/verify-full b h)))) "and the full verification reports it")
         (is (some #{:log-heads-mismatch} (map :reason (:bad (store/verify b s1)))) "the cheap check sees a log that does not end at the heads")
         (is (= :store/error (:type (ex-of #(store/commit! b s1 (doc-plan s1 4))))) "a holder of the recorded heads cannot commit on top")
         (is (= log-before (slurp (str dir "/log.edn"))))
         (is (= heads-before (store/read-heads b)))))))

(deftest reopen-from-a-separate-bb-process
  (let [dir (tmp-dir)
        before (populate! dir)
        out (-> (p/shell {:out :string :err :string :continue true}
                         "bb" "-cp" "src" "-e"
                         (str "(require '[sldb.kernel.store :as s] '[sldb.host.fs-store :as fs] '[sldb.host.default :as h]) "
                              "(let [st (s/open (fs/backend \"" dir "\") h/host)] (prn [(:head st) (:heads st) (count (:revisions st))]))"))
                :out edn/read-string)]
    (is (= [(:head before) (:heads before) 2] out))))

(defspec any-valid-plan-sequence-reloads-identically 20
  (prop/for-all [ps g/gen-plan-sequence]
    (let [dir (tmp-dir)
          b (fs/backend dir)
          s (reduce (fn [s p] (:store (store/commit! b s (assoc p :base (:head s)))))
                    (store/init! b h {"jp" :all})
                    ps)
          reloaded (store/open b h)]
      (and (= (snapshot s) (snapshot reloaded))
           (:ok? (store/verify b reloaded))))))

;; ------------------------------------------------- open from heads / verification (format 2)

(defn- empty-plan [s] {:plan/version 1 :engines {} :base (:head s) :actor "jp" :timestamp "2026-08-29T12:00:00.000Z" :ops []})

(defn- counting-backend
  "`b` recording in the atom `reads` every object read, log read and listing."
  [b reads]
  (reify store/Backend
    (read-descriptor [_] (store/read-descriptor b))
    (write-descriptor! [_ d] (store/write-descriptor! b d))
    (read-object [_ id] (swap! reads update :objects (fnil conj []) id) (store/read-object b id))
    (write-object! [_ id c] (store/write-object! b id c))
    (object-ids [_] (swap! reads update :listings (fnil inc 0)) (store/object-ids b))
    (read-log [_] (swap! reads update :log (fnil inc 0)) (store/read-log b))
    (read-heads [_] (store/read-heads b))
    (commit-tx! [_ e tx n] (store/commit-tx! b e tx n))))

(defn- docs-store!
  "A fresh fs store with `docs` document trees followed by `extra` empty revisions; [dir backend store]."
  [docs extra]
  (let [dir (tmp-dir) b (fs/backend dir)
        s (reduce (fn [s i] (:store (store/commit! b s (doc-plan s i)))) (store/init! b h {"jp" :all}) (range 1 (inc docs)))
        s (reduce (fn [s _] (:store (store/commit! b s (empty-plan s)))) s (range extra))]
    [dir b s]))

(defn- tamper! [dir id] #?(:clj (let [f (io/file dir "objects" id)] (.setWritable f true) (spit f (str (slurp f) " ")))))

#?(:clj
   (deftest open-reads-only-the-head-revisions-and-never-the-log
     (let [sizes (for [extra [0 25]]
                   (let [[_ b s] (docs-store! 3 extra)
                         reads (atom {})
                         opened (store/open (counting-backend b reads) h)
                         head-ids (set (cons (:head s) (vals (:heads s))))]
                     (is (= (heads-of s) (heads-of opened)))
                     (is (= head-ids (set (:objects @reads))) "exactly the head Revisions are read")
                     (is (= (count head-ids) (count (:objects @reads))) "each once")
                     (is (nil? (:log @reads)) "the log is not read")
                     (is (nil? (:listings @reads)) "the backend is not listed")
                     (count (:objects @reads))))]
       (is (= [3 4] sizes) "25 more revisions cost one more read (the head itself): O(heads), not O(history)"))))

#?(:clj
   (deftest the-lazy-and-the-eager-store-show-the-same-views
     (let [[dir b s] (docs-store! 4 2)
           eager-host (dissoc h :lazy-maps)
           views (fn [st] (assoc (snapshot st) :trees (into {} (:trees st)) :objects (into {} (:objects st))
                                 :capabilities (:capabilities st) :format-version (:format-version st)))
           lazy (store/open b h)
           eager (store/open b eager-host)]
       (is (not (sldb.kernel.ports/lazy-maps? eager-host)))
       (is (set? (:edges lazy)))
       (is (= (dissoc (views eager) :host) (dissoc (views lazy) :host)) "opened from the heads vs replayed eagerly")
       (is (= (views s) (views lazy)) "and vs the value the commits returned")
       (testing "after one more commit on each"
         (let [p (doc-plan lazy 9)
               l2 (:store (rev/apply-plan lazy p))
               e2 (:store (rev/apply-plan eager p))]
           (is (= (views e2) (views l2)))
           (is (= (views l2) (views (:store (store/commit! b lazy p)))))
           (is (= (views l2) (views (store/open (fs/backend dir) h)))))))))

#?(:clj
   (deftest open-checks-heads-edn-and-the-head-revisions
     (let [damaged (fn [f] (let [[dir b s] (docs-store! 2 1)] (f dir b s) (ex-of #(store/open b h))))
           node-id (fn [s] (some (fn [[id o]] (when (:class o) id)) (:objects s)))]
       (is (= [:store/corrupt-head :missing]
              ((juxt :type :reason) (damaged (fn [dir _ s] (io/delete-file (io/file dir "objects" (:head s))))))))
       (is (= [:store/corrupt-head :hash-mismatch]
              ((juxt :type :reason) (damaged (fn [dir _ s] (tamper! dir (:head s)))))))
       (is (= [:store/corrupt-head :hash-mismatch]
              ((juxt :type :reason) (damaged (fn [dir _ s] (tamper! dir (first (remove #{(:head s)} (vals (:heads s)))))))))
           "a per-tree head is checked like the head")
       (is (= [:store/corrupt-head :not-a-revision]
              ((juxt :type :reason) (damaged (fn [dir _ s] (spit (str dir "/heads.edn") (pr-str (assoc (heads-of s) :head (node-id s)))))))))
       (is (= :store/corrupt-heads (:type (damaged (fn [dir _ _] (spit (str dir "/heads.edn") "{:head \"abc"))))) "unreadable")
       (is (= :store/corrupt-heads (:type (damaged (fn [dir _ _] (spit (str dir "/heads.edn") "{:head 1 :heads []}"))))) "wrong shape")
       (is (= :store/corrupt-heads (:type (damaged (fn [dir _ s] (spit (str dir "/heads.edn") (pr-str (:heads s))))))) "not {:head :heads}"))))

#?(:clj
   (deftest a-tampered-object-is-detected-by-the-hash-check
     (let [[dir b s] (docs-store! 3 0)
           id (some (fn [[id o]] (when (:class o) id)) (:objects s))]
       (tamper! dir id)
       (let [opened (store/open b h)]
         (is (= (:head s) (:head opened)) "open trusts the objects the heads do not name")
         (is (= [{:id id :reason :hash-mismatch}] (:bad (store/verify b opened))))
         (is (= [{:id id :reason :hash-mismatch}] (:bad (store/verify-full b h))))
         (is (false? (:ok? (store/verify-full b h))))))))

#?(:clj
   (deftest the-full-verification-detects-a-damaged-log-and-heads-that-disagree-with-it
     (let [full (fn [f] (let [[dir b s] (docs-store! 3 0)
                              log (str dir "/log.edn")]
                          (f dir log s)
                          {:open (ex-of #(store/open b h)) :full (store/verify-full b h) :replay (ex-of #(store/replay-open b h))}))
           failed? (fn [r] (and (false? (:ok? (:full r))) (= [:replay-failed] (map :reason (:bad (:full r)))) (some? (:replay r))))]
       (testing "sanity: an intact store passes"
         (let [r (full (fn [_ _ _]))] (is (true? (:ok? (:full r)))) (is (nil? (:replay r)))))
       (testing "a truncated log line"
         (let [r (full (fn [_ log _] (let [t (slurp log)] (spit log (subs t 0 (- (count t) 40))))))]
           (is (failed? r))
           (is (nil? (:open r)) "open does not read the log: only the full verification sees it")))
       (testing "an altered log line"
         (let [r (full (fn [_ log _] (spit log (str/replace-first (slurp log) ":actor \"jp\"" ":actor \"xx\""))))]
           (is (failed? r))
           (is (nil? (:open r)))))
       (testing "a log line removed from the middle"
         (is (failed? (full (fn [_ log _] (let [ls (str/split-lines (slurp log))] (spit log (str (str/join "\n" (cons (first ls) (drop 2 ls))) "\n"))))))))
       (testing "heads that disagree with the log (a well-formed, older state)"
         (let [[dir b _] (docs-store! 1 0)
               old (heads-of (store/open b h))
               s (store/open b h)
               s (reduce (fn [s i] (:store (store/commit! b s (doc-plan s i)))) s [2 3])]
           (spit (str dir "/heads.edn") (pr-str old))
           (is (= old (heads-of (store/open b h))) "open trusts heads.edn")
           (is (= [:replay-failed] (map :reason (:bad (store/verify-full b h)))))
           (is (some #{:log-heads-mismatch} (map :reason (:bad (store/verify b (store/open b h))))) "the cheap check compares the end of the log"))))))
