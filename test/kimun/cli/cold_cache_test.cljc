(ns kimun.cli.cold-cache-test
  "C1: `<store>/cache/` is derived and deletable (docs/v2/00-nucleo.md,
   docs/v2/10-escala.md §11). Every read command of the CLI that can touch a
   document tree must answer the same with a hot cache, with `cache/` deleted
   and with ONE corrupted cache object. The matrix below is the list of those
   commands; `known-cold-cache-gaps` is the list of the ones that do not hold
   the promise yet. The test fails when a command not in the list diverges
   (new gap) and when a command in the list stops diverging (fixed: remove it
   from the list)."
  (:require [babashka.fs :as fs]
            [clojure.test :refer [deftest is testing use-fixtures]]
            [kimun.cli.main :as main]
            [kimun.test-util :as ktu]))

(def ^:dynamic *p* nil)
(def env {"USER" "tester"})

(defn- run [& argv]
  (binding [kimun.surface.markdown.ast/*parser* "native"]
    (:envelope (main/run (vec argv) env *p*))))
(defn- file [& parts] (str (apply fs/path *p* parts)))

(def ticket
  (pr-str {:model/name "TicketDoc" :model/version 1 :base []
           :fields [{:name "title" :type :string :required true :description "Title."}
                    {:name "status" :type :string :required true :description "Status."}]
           :template "# ⸢rev•title⸥\n\nStatus: ⸢rev•status⸥\n"}))

(defn- project
  "A store with the typed document `docs/a.md` (TicketDoc, links to `notes/plain`
   by path) and the generic document `notes/plain.md` (ingested), the derived
   index written, and a populated `cache/`."
  []
  (is (true? (:ok (run "stores" "init" "--name" "p"))))
  (fs/create-dirs (file "docs")) (fs/create-dirs (file "notes"))
  (spit (file "ticket.edn") ticket)
  (is (true? (:ok (run "models" "define" "-f" "ticket.edn"))))
  (spit (file "docs" "a.md") "# T\n\nStatus: open\n")
  (spit (file "notes" "plain.md") "# Note\n\nPara one.\n\n## Sec\n\nSecond words.\n")
  (is (true? (:ok (run "docs" "track" "TicketDoc" "docs/a.md"))))
  (is (true? (:ok (run "docs" "ingest" "notes/plain.md"))))
  (is (true? (:ok (run "rebuild-indexes")))))

(use-fixtures :each
  (fn [f]
    (let [p (str (ktu/temp-dir "kimun-coldcache-"))]
      (try (binding [*p* p] (project) (f)) (finally (fs/delete-tree p))))))

;; label → argv. Read commands only; each one is expected to answer ok with the
;; hot cache (asserted), so an error cannot hide a comparison of two errors.
(def matrix
  [["docs list"                  ["docs" "list"]]
   ["docs list --model"          ["docs" "list" "--model" "TicketDoc"]]
   ["docs show typed"            ["docs" "show" "a"]]
   ["docs show --ast typed"      ["docs" "show" "a" "--ast"]]
   ["docs show --ast generic"    ["docs" "show" "plain" "--ast"]]
   ["docs render typed"          ["docs" "render" "a"]]
   ["docs render generic"        ["docs" "render" "plain"]]
   ["docs status"                ["docs" "status"]]
   ["docs compose typed"         ["docs" "compose" "a"]]
   ["docs compose generic"       ["docs" "compose" "notes/plain.md"]]
   ["docs recover typed"         ["docs" "recover" "a"]]
   ["docs recover generic"       ["docs" "recover" "notes/plain.md"]]
   ["fields get"                 ["fields" "get" "a" "title"]]
   ["fields schema"              ["fields" "schema" "TicketDoc"]]
   ["fields query"               ["fields" "query" "title"]]
   ["sections list typed"        ["sections" "list" "a"]]
   ["sections fields"            ["sections" "fields" "a" "t"]]
   ["sections find"              ["sections" "find" "T"]]
   ["find physical"              ["find" "open" "--in" "physical"]]
   ["find semantic"              ["find" "representation" "--in" "semantic"]]
   ["find field"                 ["find" "title"]]
   ["find model"                 ["find" "TicketDoc"]]
   ["edges show"                 ["edges" "show"]]
   ["edges show --from"          ["edges" "show" "--from" "sldb://document/TicketDoc:a"]]
   ["edges check"                ["edges" "check"]]
   ["edges node"                 ["edges" "node" "sldb://document/TicketDoc:a"]]
   ["edges nodes"                ["edges" "nodes"]]
   ["edges types"                ["edges" "types"]]
   ["graph get"                  ["graph" "get" "sldb://document/TicketDoc:a"]]
   ["graph list"                 ["graph" "list"]]
   ["graph neighborhood"         ["graph" "neighborhood" "sldb://document/TicketDoc:a"]]
   ["graph path"                 ["graph" "path" "sldb://document/TicketDoc:a" "sldb://model/TicketDoc"]]
   ["graph cycles"               ["graph" "cycles"]]
   ["graph order"                ["graph" "order"]]
   ["graph components"           ["graph" "components"]]
   ["graph central"              ["graph" "central"]]
   ["graph isolated"             ["graph" "isolated"]]
   ["graph similar"              ["graph" "similar" "sldb://document/TicketDoc:a"]]
   ["graph export"               ["graph" "export"]]
   ["models list"                ["models" "list"]]
   ["models show"                ["models" "show" "TicketDoc"]]
   ["models check"               ["models" "check"]]
   ["models history"             ["models" "history" "TicketDoc"]]
   ["stores check"               ["stores" "check"]]
   ["stores verify"              ["stores" "verify"]]
   ["stores health"              ["stores" "health"]]
   ["stores show"                ["stores" "show"]]
   ["stores sense"               ["stores" "sense" "--once"]]
   ["stores unreachable"         ["stores" "unreachable"]]
   ["semantic tags"              ["semantic" "tags"]]
   ["journal show"               ["journal" "show"]]
   ["journal verify"             ["journal" "verify"]]])

(def known-cold-cache-gaps
  "Commands (by label) that do NOT answer the same with `cache/` deleted /
   with one corrupted cache object, found by this matrix on 2026-11-15. Fix
   them through `kimun.surface.docs/doc-tree` and remove them from here."
  {:cold #{"journal show" "journal verify"}
   ;; cache/ minus cache/plans: only objects/ and docs/ are deleted
   :cold-no-plans #{"journal show"}
   :corrupt #{"journal show"}})

(defn- volatile-free
  "The answer of a command without its clock readings."
  [x]
  (cond (map? x) (into {} (comp (remove (fn [[k _]] (#{:observed-at :timestamp} k))) (map (fn [[k v]] [k (volatile-free v)]))) x)
        (sequential? x) (mapv volatile-free x)
        :else x))

(defn- answer [argv]
  (let [r (try (apply run argv) (catch Throwable t {:ok false :exception (str t)}))]
    (volatile-free (select-keys r [:ok :command :data :error :revision :warnings :exception]))))

(defn- cache-dir [] (file ".kimun" "cache"))
(defn- cache-objects [] (vec (sort (map str (fs/glob (file ".kimun" "cache" "objects") "*")))))

(defn- reset-cache! [backup]
  (fs/delete-tree (cache-dir))
  (fs/copy-tree backup (cache-dir)))

(defn- diverging
  "Labels of the matrix whose answer after `(mutate!)` differs from the hot
   one. `hot` is `{label answer}`; the cache is restored before each command."
  [hot backup mutate!]
  (set (for [[label argv] matrix
             :let [_ (reset-cache! backup)
                   _ (mutate!)]
             :when (not= (get hot label) (answer argv))]
         label)))

(deftest cold-cache-answers-like-hot-cache
  (let [backup (str (fs/path *p* "cache-backup"))
        _ (fs/copy-tree (cache-dir) backup)
        hot (into {} (for [[label argv] matrix] [label (answer argv)]))]
    (testing "every matrix command answers ok and non-empty with the hot cache"
      (is (seq (cache-objects)) "the fixture populated cache/objects")
      (is (= [] (vec (for [[label _] matrix :when (not (true? (:ok (get hot label))))] [label (get hot label)]))))
      (is (= [] (vec (for [[label _] matrix :when (empty? (:data (get hot label)))] label)))))
    (testing "cache/ deleted: the same answers (gaps are listed in known-cold-cache-gaps)"
      (let [gaps (diverging hot backup #(fs/delete-tree (cache-dir)))]
        (is (= (:cold known-cold-cache-gaps) gaps)
            (str "new gaps: " (sort (remove (:cold known-cold-cache-gaps) gaps))
                 " fixed (remove from the list): " (sort (remove gaps (:cold known-cold-cache-gaps)))))))
    (testing "cache/objects and cache/docs deleted, cache/plans kept: isolates the plans"
      (let [gaps (diverging hot backup #(do (fs/delete-tree (file ".kimun" "cache" "objects"))
                                            (fs/delete-tree (file ".kimun" "cache" "docs"))))]
        (is (= (:cold-no-plans known-cold-cache-gaps) gaps)
            (str "new gaps: " (sort (remove (:cold-no-plans known-cold-cache-gaps) gaps))
                 " fixed (remove from the list): " (sort (remove gaps (:cold-no-plans known-cold-cache-gaps)))))))))

(deftest one-corrupted-cache-object-answers-like-hot-cache
  (let [backup (str (fs/path *p* "cache-backup"))
        _ (fs/copy-tree (cache-dir) backup)
        hot (into {} (for [[label argv] matrix] [label (answer argv)]))
        objs (mapv #(str (fs/file-name %)) (cache-objects))]
    (is (seq objs))
    (let [gaps (into #{}
                     (mapcat (fn [o]
                               (diverging hot backup #(spit (file ".kimun" "cache" "objects" o) "corrupted, not the bytes of its id"))))
                     objs)]
      (is (= (:corrupt known-cold-cache-gaps) gaps)
          (str "new gaps: " (sort (remove (:corrupt known-cold-cache-gaps) gaps))
               " fixed (remove from the list): " (sort (remove gaps (:corrupt known-cold-cache-gaps))))))))
