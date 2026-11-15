(ns kimun.cli.fault-injection-test
  "F1: what the system does TODAY when the write of the project file fails after
   the store commit of `docs update --set` / `fields set`, and when a write into
   `<store>/cache/` fails. Characterisation, not a new protocol: there is no
   outbox, the command writes the file after the commit
   (docs/v2/tests/parity-v1.md, «Documentos»). The injected fault is a
   `with-redefs` of `clojure.core/spit` that throws for one path, so the tests
   do not depend on the uid running them.

   What the spec promises and each test asserts:
   - docs/v2/10-escala.md §11 (l. 397): the repair of a file that does not match
     the store is `docs update NAME` / `stores update`; «Ningún cambio silencioso».
   - docs/v2/06-superficies-y-cli.md (l. 516, l. 90): a file that differs from
     the store is `source/mutated`, `stores health` says `needs-human` with a
     `:next` of inspection; `stores health` returns
     `{:status :summary :findings :actions :next}`.
   - docs/v2/10-escala.md §11 (l. 387): the cache under `<store>/cache/` is
     derived and deletable: its failure must not corrupt the store."
  (:require [babashka.fs :as fs]
            [clojure.string :as str]
            [clojure.test :refer [deftest is testing use-fixtures]]
            [kimun.cli.main :as main]
            [kimun.cli.store :as cli-store]
            [kimun.surface.markdown.ast :as ast]
            [kimun.test-util :as ktu]))

(def ^:dynamic *p* nil)
(def env {"USER" "tester"})

(defn- run [& argv] (binding [ast/*parser* "native"] (:envelope (main/run (vec argv) env *p*))))
(defn- file [& parts] (str (apply fs/path *p* parts)))
(defn- head [] (:head (:store (cli-store/open! (file ".kimun")))))
(defn- health [] (:data (run "stores" "health")))
(defn- shown [] (:data (run "docs" "show" "a")))

(def ticket
  (pr-str {:model/name "TicketDoc" :model/version 1 :base []
           :fields [{:name "title" :type :string :required true :description "Title."}
                    {:name "status" :type :string :required true :description "Status."}]
           :template "# ⸢rev•title⸥\n\nStatus: ⸢rev•status⸥\n"}))

(use-fixtures :each
  (fn [f]
    (let [p (str (ktu/temp-dir "kimun-fault-"))]
      (try (binding [*p* p]
             (fs/create-dirs (file "docs"))
             (spit (file "ticket.edn") ticket)
             (spit (file "docs" "a.md") "# T\n\nStatus: open\n")
             (assert (:ok (run "stores" "init" "--name" "p")))
             (assert (:ok (run "models" "define" "-f" "ticket.edn")))
             (assert (:ok (run "docs" "track" "TicketDoc" "docs/a.md")))
             (assert (= :ok (:status (health))))
             (f))
           (finally (fs/delete-tree p))))))

(defmacro ^:private failing-spit
  "Runs `body` with `spit` throwing for every path that contains `needle`."
  [needle & body]
  `(let [real# spit]
     (with-redefs [spit (fn [f# & more#]
                          (if (str/includes? (str f#) ~needle)
                            (throw (java.io.IOException. (str "injected: " f#)))
                            (apply real# f# more#)))]
       ~@body)))

(defn- tmp-names
  "Names under the store that look like temporaries (`.tmp`), objects included."
  []
  (->> (fs/glob (file ".kimun") "**" {:hidden true}) (map str) (filter #(str/includes? % ".tmp"))))

(def old-text "# T\n\nStatus: open\n")
(def new-text "# T\n\nStatus: done\n")

(deftest docs-update-whose-file-write-fails-leaves-a-revision-and-the-old-file
  ;; 10 §11 l. 397: the divergence file/store is named, never silent.
  (let [h0 (head)
        r (failing-spit "docs/a.md" (run "docs" "update" "a" "--set" "status=done"))]
    (testing "the command still reports ok, with a declared warning, and the revision exists"
      (is (true? (:ok r)))
      (is (= ["docs/file-not-written"] (mapv :type (:warnings r))))
      (is (str/includes? (:message (first (:warnings r))) "docs/a.md"))
      (is (not= h0 (:revision r)))
      (is (= (:revision r) (head)) "the store head is the new revision"))
    (testing "the file keeps its previous content; the store holds the new source"
      (is (= old-text (slurp (file "docs" "a.md"))))
      (is (= new-text (:markdown (shown)))))
    (testing "stores health names it: needs-human, with the document, the path and an inspection next"
      (let [h (health)]
        (is (= :needs-human (:status h)))
        (is (= [{:category :edited :document "a" :path "docs/a.md"}]
               (mapv #(select-keys % [:category :document :path]) (:findings h))))
        (is (= ["kimun docs update a"] (:actions h)))
        (is (= "inspect: docs/a.md" (:next h)))))
    (testing "stores update does not guess: it skips the document and writes nothing"
      (let [h1 (head)
            u (run "stores" "update")]
        (is (true? (:ok u)))
        (is (= [] (get-in u [:data :updated])))
        (is (= [{:document "a" :category :edited}]
               (mapv #(select-keys % [:document :category]) (get-in u [:data :skipped]))))
        (is (= h1 (head)))
        (is (= old-text (slurp (file "docs" "a.md"))))))))

(deftest fields-set-whose-file-write-fails-is-named-the-same-way
  ;; 06 l. 516 + 10 §11 l. 397: same signal as docs update.
  (let [h0 (head)
        r (failing-spit "docs/a.md" (run "fields" "set" "a" "status" "\"done\""))]
    (is (true? (:ok r)))
    (is (= ["docs/file-not-written"] (mapv :type (:warnings r))))
    (is (not= h0 (:revision r)))
    (is (= old-text (slurp (file "docs" "a.md"))))
    (is (= new-text (:markdown (shown))))
    (is (= [:needs-human :edited] ((juxt :status #(get-in % [:findings 0 :category])) (health))))))

(deftest repeating-docs-update-converges-once-the-file-is-writable
  ;; 10 §11 l. 397: the repair is `docs update NAME`; repeating it must reach a clean store.
  (failing-spit "docs/a.md" (run "docs" "update" "a" "--set" "status=done"))
  (is (= :needs-human (:status (health))))
  (let [h1 (head)
        r (run "docs" "update" "a" "--set" "status=done")]
    (is (true? (:ok r)))
    (is (= [] (:warnings r)))
    (is (true? (get-in r [:data :unchanged?])) "the store already held the change: no second revision")
    (is (= h1 (head)))
    (is (= new-text (slurp (file "docs" "a.md"))) "the file is written by the repeat")
    (is (= :ok (:status (health))))
    (is (= [] (:findings (health))))))

(deftest readers-do-not-fail-when-the-cache-cannot-be-written
  ;; 10 §11 l. 758: «Un lector no escribe ni falla por la caché». The cache is
  ;; emptied (cold, l. 387: «borrable») and every write under `cache/` fails
  ;; while readers run; the store must stay intact. (The same fault during a
  ;; MUTATION is not asserted: the spec is silent, see REPORT.md.)
  (is (true? (:ok (run "docs" "update" "a" "--set" "status=done"))))
  (fs/delete-tree (file ".kimun" "cache"))
  (let [h0 (head)]
    (failing-spit "/cache/"
      (is (= :ok (:status (health))))
      (is (= new-text (:markdown (shown))))
      (is (true? (:ok (run "stores" "verify")))))
    (is (= h0 (head)) "readers wrote no revision")
    (is (= new-text (slurp (file "docs" "a.md"))))))

(deftest a-failed-file-write-leaves-no-temporary-in-the-store
  ;; 10 §5/§7: objects are written once; `.tmp-*` names are only transient.
  (failing-spit "docs/a.md" (run "docs" "update" "a" "--set" "status=done"))
  (failing-spit "docs/a.md" (run "fields" "set" "a" "title" "\"U\""))
  (is (true? (:ok (run "journal" "verify"))))
  (is (= [] (tmp-names)))
  (is (seq (fs/glob (file ".kimun" "objects") "**")) "objects/ exists and is not empty"))
