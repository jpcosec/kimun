(ns kimun.cli.contract-test
  "K6 end to end: `stores sense` tells a changed source from a changed parse
   contract. The same file read by another parser is `contract/changed`, never
   an outside edit; a document recorded before K6 keeps working.

   Coverage: the alternate parser is simulated (`ast/*parser*` plus a stubbed
   pod), which is exact for sensing because sensing compares contracts and
   never parses. No test here runs the real tree-sitter pod, an optional
   component; the real `KIMUN_PARSER=treesitter` run is manual evidence
   (`runs/subagents/2026-11-14-K6/`)."
  (:require [babashka.fs :as fs]
            [clojure.test :refer [deftest is testing]]
            [kimun.cli.main :as main]
            [kimun.cli.store :as cli-store]
            [kimun.host.store-cache :as store-cache]
            [kimun.host.treesitter :as ts]
            [kimun.kernel.store :as kstore]
            [kimun.surface.docs :as tdocs]
            [kimun.surface.markdown.ast :as ast]
            [kimun.surface.parse-contract :as pc]
            [kimun.test-util :as ktu]))

(def env {"USER" "tester"})

(defn- run [cwd & argv] (:envelope (main/run argv env cwd)))
(defn- file [p & parts] (str (apply fs/path p parts)))

(defn- ticket [template]
  (pr-str {:model/name "TicketDoc" :model/version 1 :base []
           :fields [{:name "title" :type :string :required true :description "Title."}
                    {:name "status" :type :string :required true :description "Status."}]
           :template template}))

(defn- project
  "A project with TicketDoc and the tracked document `login` (login.md),
   written by the in-process parser."
  []
  (binding [ast/*parser* "native"]
    (let [p (str (ktu/temp-dir "kimun-contract-"))]
      (is (true? (:ok (run p "stores" "init" "--name" "p"))))
      (spit (file p "ticket.edn") (ticket "# ⸢rev•title⸥\n\nStatus: ⸢rev•status⸥\n"))
      (is (true? (:ok (run p "models" "define" "-f" "ticket.edn"))))
      (is (true? (:ok (run p "docs" "create" "TicketDoc" "login"
                            "--set" "title=T" "--set" "status=open"))))
      p)))

(defn- signals
  "The signals of `stores sense`, reduced to what K6 distinguishes."
  [p]
  (mapv (fn [s] [(:type s) (get-in s [:detail :content-ok]) (get-in s [:detail :contract-ok])
                 (get-in s [:detail :contract :changed])])
        (get-in (run p "stores" "sense") [:data :signals])))

(defn- recorded
  "The `:parse-contract` recorded for `login`."
  [p]
  (let [{:keys [store]} (cli-store/open! (file p ".kimun"))]
    (:parse-contract (tdocs/find-document store "login"))))

(defmacro as-treesitter
  "Runs `body` as if `KIMUN_PARSER=treesitter` were set and the pod answered
   (no document is parsed inside: sensing compares, it does not parse)."
  [& body]
  `(with-redefs [ts/available? (constantly true)
                 ts/version (constantly {:pod "pod-kimun-treesitter" :version "9.9.9" :tree_sitter "0.25"})]
     (binding [ast/*parser* "treesitter"] ~@body)))

(deftest a-new-document-records-its-contract-and-senses-clean
  (binding [ast/*parser* "native"]
    (let [p (project) r (recorded p)]
      (is (= (pc/record (:host (:store (cli-store/open! (file p ".kimun")))) (pc/environment) (:class r)) r))
      (is (= ["kimun.surface.markdown/native" store-cache/parser-version] (:parser r)))
      (is (= [] (signals p)))
      (is (= [{:status :same :changed []} true true]
             (let [d (first (get-in (run p "stores" "check") [:data :documents :documents]))]
               [(:contract d) (:content-ok d) (:contract-ok d)]))))))

(deftest the-same-file-under-the-alternate-parser-is-a-contract-change
  (let [p (project)
        text (slurp (file p "login.md"))]
    (as-treesitter
      (let [s (first (get-in (run p "stores" "sense") [:data :signals]))]
        (is (= [[:contract/changed true false [:parser :grammar]]] (signals p))
            "the source is intact; only the contract moved")
        (is (= [:contract-moved :info false "kimun docs update login"]
               [(:category s) (:severity s) (:needs-human s) (:action s)]))
        (is (re-find #"parser, grammar" (:message s)))
        (is (not (re-find #"edited" (:message s))) "it is not diagnosed as an outside edit")))
    (is (= text (slurp (file p "login.md"))))
    (binding [ast/*parser* "native"]
      (is (= [] (signals p)) "back under the recorded parser nothing is reported"))))

(deftest a-changed-source-is-not-a-contract-change
  (binding [ast/*parser* "native"]
    (let [p (project)]
      (spit (file p "login.md") "# T\n\nStatus: closed\n")
      (is (= [[:source/mutated false true []]] (signals p))))))

(deftest both-can-move-and-the-signal-says-so
  (let [p (project)]
    (spit (file p "login.md") "# T\n\nStatus: closed\n")
    (as-treesitter
      (is (= [[:source/mutated false false [:parser :grammar]]] (signals p))
          "the edit needs a human; the detail still says the contract moved too"))))

(deftest an-extractor-or-a-class-change-is-not-reported-as-the-parser
  (binding [ast/*parser* "native"]
    (let [p (project)]
      (with-redefs [pc/extractor ["kimun.surface.models" "2"]]
        (is (= [[:contract/changed true false [:extractor]]] (signals p))))
      (spit (file p "ticket.edn") (ticket "# ⸢rev•title⸥\n\nState: ⸢rev•status⸥\n"))
      (is (true? (:ok (run p "models" "define" "-f" "ticket.edn"))))
      (is (= [[:contract/changed true false [:class]]] (signals p))))))

(deftest stores-update-re-registers-under-the-contract-in-force-without-touching-the-file
  (let [p (project)
        text (slurp (file p "login.md"))
        before (recorded p)]
    (binding [ast/*parser* "native"]
      (with-redefs [store-cache/parser-version "2"]
        (is (= [[:contract/changed true false [:parser]]] (signals p)))
        (is (= ["login"] (get-in (run p "stores" "update") [:data :updated])))
        (is (= text (slurp (file p "login.md"))) "a parser change never rewrites the source")
        (is (= ["kimun.surface.markdown/native" "2"] (:parser (recorded p))))
        (is (not= (:digest before) (:digest (recorded p))))
        (is (= [] (signals p)) "the repair is not offered again"))
      (is (= [[:contract/changed true false [:parser]]] (signals p))
          "and the store now says it was recorded under version 2"))))

(defn- rewrite-parse-contract!
  "Rewrites the anchor edge of `login` with `(f recorded)` as its
   `:parse-contract` (nil removes it: the shape of a document tracked before K6)."
  [p f]
  (let [{:keys [backend store]} (cli-store/open! (file p ".kimun"))
        doc (tdocs/find-document store "login")
        eid (first (filter #(get-in store [:objects % :evidence :parse-contract]) (:edges doc)))
        e (get-in store [:objects eid])]
    (kstore/commit! backend store
                    {:plan/version 1 :base (:head store) :actor "human/tester" :engines {}
                     :timestamp "2026-01-01T00:00:00.000Z"
                     :ops [{:op :remove-edge :edge eid}
                           {:op :add-edge
                            :edge (dissoc (let [v (f (get-in e [:evidence :parse-contract]))]
                                            (if v
                                              (assoc-in e [:evidence :parse-contract] v)
                                              (update e :evidence dissoc :parse-contract)))
                                          :id)}]})))

(defn- forget-parse-contract! [p] (rewrite-parse-contract! p (constantly nil)))

(deftest a-document-recorded-before-k6-keeps-working-and-gains-the-contract-when-written
  (binding [ast/*parser* "native"]
    (let [p (project)]
      (forget-parse-contract! p)
      (is (nil? (recorded p)))
      (is (= [] (signals p)))
      (is (= {:status :unrecorded :changed []}
             (:contract (first (get-in (run p "stores" "check") [:data :documents :documents])))))
      (with-redefs [store-cache/parser-version "2"]
        (is (= [] (signals p)) "without a recorded contract a parser change is not claimed"))
      (is (true? (:ok (run p "docs" "show" "login"))) "it is still read")
      (testing "an edit is still an edit"
        (spit (file p "login.md") "# T\n\nStatus: closed\n")
        (is (= [[:source/mutated false true []]] (signals p))))
      (testing "the next write records the contract"
        (is (true? (:ok (run p "docs" "update" "login"))))
        (is (= :same (:status (pc/compare-recorded (:host (:store (cli-store/open! (file p ".kimun"))))
                                                   (pc/environment) (recorded p) (:class (recorded p))))))
        (is (= [] (signals p)))))))

(deftest a-document-recorded-under-an-absent-parser-is-parser-unavailable-not-a-change
  (let [p (project)]
    (as-treesitter
      (with-redefs [store-cache/parser-version store-cache/parser-version]
        ;; record `login` as written under tree-sitter, without parsing with a pod
        (rewrite-parse-contract! p (fn [r] (pc/record (:host (:store (cli-store/open! (file p ".kimun"))))
                                                      (pc/environment) (:class r))))))
    (with-redefs [ts/available? (constantly false)]
      (binding [ast/*parser* "treesitter"]
        (let [s (first (get-in (run p "stores" "sense") [:data :signals]))]
          (is (= [[:contract/parser-unavailable true false []]] (signals p)))
          (is (= [:parser-unavailable :warning true] [(:category s) (:severity s) (:needs-human s)]))
          (is (= {:updated [] :skipped [{:document "login" :category :parser-unavailable :action "kimun docs update login"}]}
                 (select-keys (:data (run p "stores" "update")) [:updated :skipped]))
              "it is not repaired without a decision"))))))

(deftest a-pod-that-does-not-answer-is-parser-unavailable-and-nothing-is-recorded
  (let [p (project) before (recorded p)]
    (with-redefs [ts/available? (constantly true)
                  ts/version (fn [] (throw (ex-info "pod died" {})))]
      (binding [ast/*parser* "treesitter"]
        (is (= [[:contract/parser-unavailable true false []]] (signals p))
            "no `unknown` grammar is compared as if it were a contract")))
    (is (= before (recorded p)))))

(deftest inconsistent-evidence-is-reported-not-trusted
  (binding [ast/*parser* "native"]
    (let [p (project)]
      (rewrite-parse-contract! p #(assoc % :grammar "tampered"))
      (let [s (first (get-in (run p "stores" "sense") [:data :signals]))]
        (is (= [[:contract/inconsistent true false []]] (signals p)))
        (is (= [:contract-inconsistent true] [(:category s) (:needs-human s)])))
      (testing "writing the document records a contract that verifies"
        (is (true? (:ok (run p "docs" "update" "login"))))
        (is (true? (pc/consistent? (:host (:store (cli-store/open! (file p ".kimun")))) (recorded p))))
        (is (= [] (signals p)))))))

(deftest the-environment-is-resolved-once-per-observation
  (let [p (project)
        calls (atom 0)]
    (binding [ast/*parser* "native"]
      (is (true? (:ok (run p "docs" "create" "TicketDoc" "logout" "--set" "title=U" "--set" "status=open")))))
    (with-redefs [ts/available? (constantly true)
                  ts/version (fn [] (swap! calls inc) {:pod "pod-kimun-treesitter" :version "9.9.9" :tree_sitter "0.25"})]
      (binding [ast/*parser* "treesitter"]
        (is (= 2 (count (signals p))))
        (is (= 1 @calls) "two documents, one question to the pod")))))
