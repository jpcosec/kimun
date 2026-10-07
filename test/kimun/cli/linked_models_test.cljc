(ns kimun.cli.linked-models-test
  "`docs create|track` with a model, or a target store, reached through a
   link (docs/v2/06 §4; `kimun.cli.linked`): the port of the two federation
   tests of sldb v1 `tests/test_cli_v2.py`, plus the typed errors. Each test
   names in a comment the v1 function whose promise it carries."
  (:require [kimun.test-util :as ktu] [babashka.fs :as fs]
            [cheshire.core :as json]
            [clojure.edn :as edn]
            [clojure.string :as str]
            [clojure.test :refer [deftest is testing]]
            [kimun.cli.main :as main]
            [kimun.cli.store :as cli-store]
            [kimun.surface.docs :as tdocs]
            [kimun.surface.models.store :as mstore]))

(def env {"USER" "tester"})
(defn- run [cwd & argv] (:envelope (main/run argv env cwd)))

(def request-doc
  {:model/name "RequestDoc" :model/version 1 :base []
   :fields [{:name "title" :type :string :required true :description "Request title."}
            {:name "body" :type :string :required true :description "Request body."}]
   :template "# ⸢rev•title⸥\n\n⸢rev,markdown•body⸥"})

(defn- stores!
  "A temp directory with one initialised project per name: `{name dir}` plus `:base`."
  [& names]
  (let [base (str (ktu/temp-dir "kimun-linked-"))]
    (into {:base base}
          (for [n names :let [dir (str (fs/create-dirs (fs/path base n)))]]
            (do (is (true? (:ok (run dir "stores" "init" "--name" n)))) [n dir])))))

(defn- define! [base dir descriptor]
  (let [f (str (fs/path base (str (:model/name descriptor) "-" (fs/file-name dir) ".edn")))]
    (spit f (pr-str descriptor))
    (is (true? (:ok (run dir "models" "define" "-f" f))))))

(defn- link! [from alias to] (is (true? (:ok (run from "stores" "link" alias to)))))

(defn- payload [base name m] (let [f (str (fs/path base name))] (spit f (json/generate-string m)) f))

(defn- opened [dir] (cli-store/open! (cli-store/normalize-store-path dir)))

(defn- doc-paths [dir] (mapv :path (get-in (run dir "docs" "list" "--model" "RequestDoc") [:data])))

;; v1 test_docs_create_accepts_linked_store_alias
(deftest docs-create-accepts-linked-store-alias
  (let [{:strs [central target] :keys [base]} (stores! "central" "target")]
    (define! base target request-doc)
    (link! central "target" target)
    (let [e (run central "docs" "create" "RequestDoc" "request-123" "--store" "target"
                 "--path" "desk/inbox/request-123.md"
                 "--data" (payload base "p.json" {"title" "Need review" "body" "Please review this."}))
          output (fs/path target "desk" "inbox" "request-123.md")]
      (is (true? (:ok e)) (pr-str e))
      (is (fs/exists? output))
      (is (str/includes? (slurp (str output)) "# Need review"))
      (is (= ["desk/inbox/request-123.md"] (doc-paths target)))
      (is (empty? (tdocs/documents (:store (opened central)))) "nothing was written to the store the command ran in"))))

;; v1 test_docs_create_uses_model_from_linked_store_namespace
(deftest docs-create-uses-model-from-linked-store-namespace
  (let [{:strs [central deskops target] :keys [base]} (stores! "central" "deskops" "target")]
    ;; the owner is at its second version: the binding must carry that very node, not a local v1
    (define! base deskops (assoc-in request-doc [:fields 0 :description] "Title."))
    (define! base deskops request-doc)
    (link! central "deskops" deskops)
    (link! central "target" target)
    (let [e (run central "docs" "create" "deskops:RequestDoc" "request-456" "--store" "target"
                 "--path" "desk/inbox/request-456.md"
                 "--data" (payload base "p.json" {"title" "Need model" "body" "Use deskops model."}))
          output (fs/path target "desk" "inbox" "request-456.md")]
      (is (true? (:ok e)) (pr-str e))
      (is (fs/exists? output))
      (is (str/includes? (slurp (str output)) "Use deskops model."))
      (is (= ["desk/inbox/request-456.md"] (doc-paths target)))
      (testing "the binding: the owner's node itself, its origin in the journal, no link needed afterwards"
        (let [owner (mstore/find-model (:store (opened deskops)) "RequestDoc")
              s (:store (opened target))
              mine (mstore/find-model s "RequestDoc")
              metas (keep #(some-> (get-in % [:engines tdocs/meta-key]) edn/read-string)
                          (filter map? (vals (:objects s))))]
          (is (= (:id owner) (:id mine)) "same content-addressed node in both stores")
          (is (= [2 2] [(:version owner) (:version mine)]))
          (is (some #(and (= "models import" (:operation %)) (= "deskops" (get-in % [:origin :alias]))
                          (= (:id owner) (get-in % [:origin :id]))) metas)
              "the import transaction records where the model came from")
          (is (= :intact (:state (tdocs/find-document s "request-456"))))
          (is (true? (:ok (run target "stores" "check"))) (pr-str (run target "stores" "check")))
          (is (true? (:ok (run target "docs" "update" "request-456" "--set" "title=Changed"))))
          (is (str/includes? (slurp (str output)) "# Changed"))
          (is (true? (:ok (run central "docs" "create" "deskops:RequestDoc" "again" "--store" "target"
                               "--set" "title=T" "--set" "body=B")))
              "binding the same model again writes no second definition")
          (is (= 1 (count (mstore/models (:store (opened target)))))))))))

(deftest docs-track-binds-an-unqualified-model-of-a-single-linked-store
  (let [{:strs [deskops target] :keys [base]} (stores! "deskops" "target")]
    (define! base deskops request-doc)
    (link! target "deskops" deskops)
    (spit (str (fs/path target "note.md")) "# Note\n\nBody text.\n")
    (let [e (run target "docs" "track" "RequestDoc" "note.md")]
      (is (true? (:ok e)) (pr-str e))
      (is (= "RequestDoc" (get-in e [:data :model])))
      (is (= {"title" "Note" "body" "Body text."} (get-in e [:data :payload]))))))

(deftest linked-model-errors-are-typed
  (let [{:strs [a b target] :keys [base]} (stores! "a" "b" "target")
        fail (fn [& argv] (let [e (apply run target argv)] [(get-in e [:error :type]) (:exit e)]))
        args ["--set" "title=T" "--set" "body=B"]]
    (define! base a request-doc)
    (define! base b (update request-doc :fields conj {:name "extra" :type :string :required false :description "Extra."}))
    (link! target "a" a)
    (testing "unknown alias"
      (is (= ["docs/unknown-alias" 3] (apply fail "docs" "create" "nope:RequestDoc" "x" args)))
      (is (= ["a"] (get-in (apply run target "docs" "create" "nope:RequestDoc" "x" args) [:error :data :known]))))
    (testing "unknown model, in a linked store and anywhere"
      (is (= ["models/missing" 1] (apply fail "docs" "create" "a:Ghost" "x" args)))
      (is (= ["models/missing" 1] (apply fail "docs" "create" "Ghost" "x" args))))
    (testing "the same name defined differently in two linked stores, unqualified"
      (link! target "b" b)
      (is (= ["docs/ambiguous" 2] (apply fail "docs" "create" "RequestDoc" "x" args)))
      (is (= ["a" "b"] (get-in (apply run target "docs" "create" "RequestDoc" "x" args) [:error :data :stores]))))
    (testing "nothing was written by any refusal"
      (is (empty? (mstore/models (:store (opened target)))))
      (is (not (fs/exists? (fs/path target "x.md")))))
    (testing "qualified, it binds; the other store's different contract is then a conflict"
      (is (true? (:ok (apply run target "docs" "create" "a:RequestDoc" "x" args))))
      (is (= ["models/conflict" 4] (apply fail "docs" "create" "b:RequestDoc" "y" args)))
      (is (true? (:ok (apply run target "docs" "create" "RequestDoc" "y" args))) "local wins once bound"))))
