(ns kimun.cli.names-test
  "K8: a document's short name is an alias and the path of its file is its
   qualified name. `a/README.md` and `b/README.md` are two documents; the
   alias `README` is ambiguous and fails with the candidates instead of
   picking one. The path is a locator, never the identity (the tree id is)."
  (:require [babashka.fs :as fs]
            [clojure.string :as str]
            [clojure.test :refer [deftest is testing]]
            [kimun.cli.main :as main]
            [kimun.cli.store :as cli-store]
            [kimun.surface.docs :as tdocs]
            [kimun.test-util :as ktu]))

(def env {"USER" "tester"})

(defn- run [cwd & argv] (:envelope (main/run argv env cwd)))
(defn- file [p & parts] (str (apply fs/path p parts)))

(def ticket
  (pr-str {:model/name "TicketDoc" :model/version 1 :base []
           :fields [{:name "title" :type :string :required true :description "Title."}
                    {:name "status" :type :string :required true :description "Status."}]
           :template "# ⸢rev•title⸥\n\nStatus: ⸢rev•status⸥\n"}))

(defn- readme [title] (str "# " title "\n\nStatus: open\n"))

(defn- project
  "A project with TicketDoc and the files `a/README.md`, `b/README.md` and
   `notes.md`, none tracked yet."
  []
  (let [p (str (ktu/temp-dir "kimun-names-"))]
    (is (true? (:ok (run p "stores" "init" "--name" "p"))))
    (spit (file p "ticket.edn") ticket)
    (is (true? (:ok (run p "models" "define" "-f" "ticket.edn"))))
    (fs/create-dirs (file p "a")) (fs/create-dirs (file p "b"))
    (spit (file p "a" "README.md") (readme "A"))
    (spit (file p "b" "README.md") (readme "B"))
    (spit (file p "notes.md") (readme "N"))
    p))

(defn- tracked
  "The project with both READMEs and `notes` tracked: `[p tree-a tree-b]`."
  []
  (let [p (project)
        a (run p "docs" "track" "TicketDoc" "a/README.md")
        b (run p "docs" "track" "TicketDoc" "b/README.md")]
    (is (true? (:ok a)))
    (is (true? (:ok b)) "a second file with the same stem is tracked, not refused")
    (is (true? (:ok (run p "docs" "track" "TicketDoc" "notes.md"))))
    [p (get-in a [:data :tree]) (get-in b [:data :tree])]))

(deftest two-files-with-the-same-stem-are-two-documents
  (let [[p ta tb] (tracked)]
    (is (not= ta tb))
    (is (= [["README" "a/README.md" ta] ["README" "b/README.md" tb] ["notes" "notes.md"]]
           (mapv (fn [d] (if (= "notes" (:name d)) [(:name d) (:qualified d)] [(:name d) (:qualified d) (:tree d)]))
                 (sort-by :qualified (:data (run p "docs" "list"))))))
    (is (true? (:ok (run p "stores" "verify"))))))

(deftest an-ambiguous-alias-fails-with-its-candidates
  (let [[p ta tb] (tracked)]
    (doseq [argv [["docs" "show" "README"]
                  ["docs" "update" "README"]
                  ["docs" "untrack" "README"]
                  ["fields" "get" "README" "title"]
                  ["sections" "list" "README"]]]
      (let [e (apply run p argv)]
        (is (= ["docs/ambiguous" 2] [(get-in e [:error :type]) (:exit e)]) (pr-str argv))
        (is (= [{:name "README" :qualified "a/README.md" :tree ta :path "a/README.md" :model "TicketDoc"}
                {:name "README" :qualified "b/README.md" :tree tb :path "b/README.md" :model "TicketDoc"}]
               (sort-by :qualified (get-in e [:error :data :candidates])))
            (pr-str argv))
        (is (str/includes? (get-in e [:error :message]) "a/README.md, b/README.md"))))
    (is (= 2 (count (filter #(= "README" (:name %)) (:data (run p "docs" "list")))))
        "a failed `docs untrack README` removed nothing")))

(deftest a-qualified-name-or-a-tree-id-resolves-one-document
  (let [[p ta tb] (tracked)]
    (is (= ["A" ta] ((juxt #(get-in % [:data :payload "title"]) #(get-in % [:data :tree])) (run p "docs" "show" "a/README.md"))))
    (is (= "B" (get-in (run p "docs" "show" "b/README.md") [:data :payload "title"])))
    (is (= "B" (get-in (run p "docs" "show" tb) [:data :payload "title"])))
    (is (= "A" (get-in (run p "docs" "show" "./a/README.md") [:data :payload "title"])) "the path is normalised")
    (is (= "A" (get-in (run p "fields" "get" "a/README.md" "title") [:data :value])))
    (testing "an unshared short name keeps working, and so does its path"
      (is (= "N" (get-in (run p "docs" "show" "notes") [:data :payload "title"])))
      (is (= "N" (get-in (run p "docs" "show" "notes.md") [:data :payload "title"]))))
    (testing "a path nobody tracks is missing, not a guess"
      (is (= ["docs/missing" 1] ((juxt #(get-in % [:error :type]) :exit) (run p "docs" "show" "c/README.md")))))))

(deftest writes-reach-only-the-document-named
  (let [[p ta tb] (tracked)]
    (spit (file p "b" "README.md") "# B\n\nStatus: closed\n")
    (let [u (run p "docs" "update" "b/README.md")]
      (is (true? (:ok u)))
      (is (= [tb "closed"] [(get-in u [:data :tree]) (get-in u [:data :payload "status"])])))
    (is (= "open" (get-in (run p "docs" "show" ta) [:data :payload "status"])))
    (is (true? (:ok (run p "docs" "untrack" "a/README.md"))))
    (testing "once one of them is gone the alias is unambiguous again"
      (is (= tb (get-in (run p "docs" "show" "README") [:data :tree]))))))

(deftest the-same-name-is-still-refused-when-nothing-tells-the-documents-apart
  (let [[p] (tracked)]
    (testing "the same file twice"
      (let [e (run p "docs" "track" "TicketDoc" "a/README.md")]
        (is (= ["docs/exists" 3] [(get-in e [:error :type]) (:exit e)]))
        (is (= "a/README.md" (get-in e [:error :data :path])))))
    (testing "a document without a file cannot share a name: nothing would tell them apart"
      (let [store (:store (cli-store/open! (file p ".kimun")))
            refused (fn [name path] (try (tdocs/refuse-collision! store name path) nil
                                         (catch clojure.lang.ExceptionInfo e (:type (ex-data e)))))]
        (is (= :docs/exists (refused "README" nil)))
        (is (nil? (refused "README" "c/README.md")))
        (is (nil? (refused "fresh" nil)))))))

(deftest a-link-to-a-shared-alias-is-never-bound-by-chance
  (let [[p] (tracked)]
    (spit (file p "index.md") "# I\n\nStatus: open\n\nSee [[README]], [[a/README.md]] and [[notes]].\n")
    (let [e (run p "docs" "recover" "index.md")
          data (or (:data e) (get-in e [:error :data]))
          by-target (into {} (map (juxt :target identity)) (:links data))]
      (is (false? (:resolved (by-target "README"))) "the alias means two documents: it is left unresolved")
      (is (= [true :path] ((juxt :resolved :source) (by-target "a/README.md"))))
      (is (= [true :store] ((juxt :resolved :source) (by-target "notes"))))
      (is (= "links/missing" (get-in e [:error :type])) "an unresolved link is an explicit failure"))
    (let [e (run p "docs" "recover" "README")]
      (is (= ["docs/ambiguous" 2 ["a/README.md" "b/README.md"]]
             [(get-in e [:error :type]) (:exit e) (get-in e [:error :data :candidates])])))))

(deftest sensing-and-repair-name-each-document-without-ambiguity
  (let [[p ta tb] (tracked)]
    (spit (file p "a" "README.md") "# A\n\nStatus: closed\n")
    (spit (file p "b" "README.md") "# B\n\n\nStatus: open\n")
    (spit (file p "notes.md") "# N\n\n\nStatus: open\n")
    (let [signals (get-in (run p "stores" "sense") [:data :signals])
          by-path (into {} (map (juxt :path identity)) signals)]
      (is (= {"a/README.md" [:source/mutated "README" "kimun docs update a/README.md"]
              "b/README.md" [:source/reformatted "README" "kimun docs update b/README.md"]
              "notes.md" [:source/reformatted "notes" "kimun docs update notes"]}
             (into {} (map (fn [[k s]] [k [(:type s) (:document s) (:action s)]])) by-path))
          "a shared alias is replaced by the qualified name in the repair; an unshared one is kept")
      (is (str/includes? (:message (by-path "a/README.md")) "kimun docs update a/README.md")))
    (let [u (run p "stores" "update")]
      (is (true? (:ok u)))
      (is (= ["README" "notes"] (get-in u [:data :updated])) "the reformatted README and notes are repaired")
      (is (= [{:document "README" :category :edited :action "kimun docs update a/README.md"}]
             (get-in u [:data :skipped]))))
    (is (= [["a/README.md" :source/mutated]]
           (mapv (juxt :path :type) (get-in (run p "stores" "sense") [:data :signals])))
        "the repair reached b/README.md by its tree id, not by the ambiguous alias")
    (is (= [ta tb] [(get-in (run p "docs" "show" ta) [:data :tree]) (get-in (run p "docs" "show" tb) [:data :tree])]))))

(deftest the-qualified-name-follows-the-file-and-the-tree-id-stays
  (let [[p ta] (tracked)]
    (fs/create-dirs (file p "c"))
    (fs/move (file p "a" "README.md") (file p "c" "README.md"))
    (testing "a moved file is a missing source: nothing is re-pointed by guessing"
      (is (= [["a/README.md" :source/missing]]
             (mapv (juxt :path :type) (get-in (run p "stores" "sense") [:data :signals])))))
    (is (= ta (get-in (run p "docs" "show" ta) [:data :tree])) "the tree id still names the document")))
