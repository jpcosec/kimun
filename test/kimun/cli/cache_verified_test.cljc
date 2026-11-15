(ns kimun.cli.cache-verified-test
  "C1/C2: the derived cache is verified on the way in. A cached object whose
   bytes do not hash to its id is a cache miss (`kimun.host.fs-store`), and a
   cached tree is a hit only when every object it needs is there
   (`kimun.surface.docs/doc-tree` calls it cold cache otherwise). In both cases
   the document is rebuilt from its file and the reading path does not touch the
   damaged file (docs/v2/10 §11, «Caché por hash»)."
  (:require [babashka.fs :as fs]
            [clojure.edn :as edn]
            [clojure.test :refer [deftest is testing]]
            [kimun.cli.main :as main]
            [kimun.cli.store :as cli-store]
            [kimun.host.store-cache :as store-cache]
            [kimun.kernel.revision :as revision]
            [kimun.kernel.store :as kstore]
            [kimun.surface.docs :as tdocs]
            [kimun.surface.markdown.ast :as ast]
            [kimun.test-util :as ktu]))

(def env {"USER" "tester"})

(defn- run [cwd & argv] (binding [ast/*parser* "native"] (:envelope (main/run argv env cwd))))
(defn- file [p & parts] (str (apply fs/path p parts)))

(def ticket
  (pr-str {:model/name "TicketDoc" :model/version 1 :base []
           :fields [{:name "title" :type :string :required true :description "Title."}
                    {:name "status" :type :string :required true :description "Status."}]
           :template "# ⸢rev•title⸥\n\nStatus: ⸢rev•status⸥\n"}))

(def ticket-text "# T\n\nStatus: open\n")
(def plain-text "# Note\n\nPara one.\n\n- a\n- b\n")

(defn- project
  "A store with the typed document `tickets/login.md` (TicketDoc) and the
   generic document `notes/plain.md` (ingested)."
  []
  (let [p (str (ktu/temp-dir "kimun-cache-verified-"))]
    (is (true? (:ok (run p "stores" "init" "--name" "p"))))
    (spit (file p "ticket.edn") ticket)
    (is (true? (:ok (run p "models" "define" "-f" "ticket.edn"))))
    (fs/create-dirs (file p "tickets")) (fs/create-dirs (file p "notes"))
    (spit (file p "tickets" "login.md") ticket-text)
    (spit (file p "notes" "plain.md") plain-text)
    (is (true? (:ok (run p "docs" "track" "TicketDoc" "tickets/login.md"))))
    (is (true? (:ok (run p "docs" "ingest" "notes/plain.md"))))
    p))

(defn- opened [p] (:store (cli-store/open! (file p ".kimun"))))

(defn- cache-dir [p] (store-cache/objects-dir (file p ".kimun")))

(defn- cached-objects
  "`{id {:text … :value …}}` for the `:kind :text` cache objects under `p`."
  [p]
  (into {}
        (keep (fn [f]
                (let [id (str (fs/file-name f))
                      t (slurp (str f))]
                  (try (when (= :text (:kind (edn/read-string t))) [id {:text t :value (edn/read-string t)}])
                       (catch Exception _ nil)))))
        (fs/list-dir (cache-dir p))))

(defn- aligned-text-object
  "The cache object of the `:kind :text` nodes that carries `snippet`."
  [p snippet]
  (->> (cached-objects p)
       (filter (fn [[_ {:keys [value]}]] (clojure.string/includes? (str (get-in value [:content :text])) snippet)))
       first))

;; ---------------------------------------------------------------- generic doc

(deftest a-damaged-cache-object-does-not-reach-the-render-of-a-generic-document
  (let [p (project)
        [id {:keys [value]}] (aligned-text-object p "Para one")
        f (file (cache-dir p) id)
        altered (pr-str (assoc-in value [:content :text] "ALTERED BY THE TEST"))
        _ (is (some? id) "the cache holds a text object of the document")]
    (spit f altered)
    (let [before (slurp f)]
      (testing "the render comes from the file, not from the altered cache"
        (is (= plain-text (:data (run p "docs" "render" "plain"))))
        (is (not (clojure.string/includes? (pr-str (run p "docs" "render" "notes/plain.md")) "ALTERED"))))
      (testing "the damaged object keeps its bytes: a read never mutates"
        (is (= before (slurp f)))
        (is (= (seq (.getBytes ^String altered "UTF-8")) (seq (fs/read-all-bytes f))))))))

(deftest a-missing-cache-object-is-cold-cache-for-a-generic-document
  (let [p (project)
        [id _] (aligned-text-object p "Para one")]
    (is (some? id) "a text node of the document is cached (never the root)")
    (fs/delete (file (cache-dir p) id))
    (testing "the document is rebuilt from the file"
      (is (= plain-text (:data (run p "docs" "render" "plain")))))
    (testing "and a subsequent read works too (the resolver delivered the rebuilt tree)"
      (is (= plain-text (:data (run p "docs" "render" "plain")))))))

;; ---------------------------------------------------------------- typed doc

(deftest a-damaged-cache-object-does-not-reach-docs-show-of-a-typed-document
  (let [p (project)
        [id {:keys [value]}] (aligned-text-object p "Status: open")
        f (file (cache-dir p) id)
        _ (is (some? id) "the cache holds a text object of the ticket")
        altered (pr-str (assoc-in value [:content :text] "Status: forged"))
        _ (spit f altered)
        before (slurp f)
        d (:data (run p "docs" "show" "login"))]
    (testing "fields come from the file"
      (is (= "open" (get-in d [:payload "status"])) (pr-str d)))
    (testing "the damaged object keeps its bytes"
      (is (= before (slurp f))))))

(deftest a-missing-cache-object-is-cold-cache-for-a-typed-document
  (let [p (project)
        [id _] (aligned-text-object p "T")]
    (is (some? id) "a text node of the ticket is cached (never the root)")
    (fs/delete (file (cache-dir p) id))
    (let [e (run p "docs" "show" "login")]
      (is (true? (:ok e)) (pr-str e))
      (is (= "open" (get-in e [:data :payload "status"]))))))

;; ---------------------------------------------------------------- backend

(deftest a-cache-hit-is-verified-by-its-id
  (let [p (project)
        b (cli-store/backend (file p ".kimun"))
        [id {:keys [value]}] (aligned-text-object p "Para one")]
    (let [f (file (cache-dir p) id)]
      (is (= (slurp f) (kstore/read-object b id)) "a sound object is served")
      (spit f (pr-str (assoc-in value [:content :text] "forged")))
      (is (nil? (kstore/read-object b id)) "bytes that do not hash to the id are a cache miss")
      (is (some? (slurp f)) "and the file is still there"))))
