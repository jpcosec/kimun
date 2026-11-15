(ns kimun.cli.tour-test
  "T-V1: the observable tour of one mutation, in process. The same transitions
   `scripts/tour_mutation.sh` shows with the CLI: rest → mutated source (signal
   and degraded health) → the action the answer proposes, chosen by an actor →
   ok → changed parse contract (K6, simulated as `contract_test` does) → the
   no-decision repair `stores update` → ok → move (the absence is only reported)
   → `docs reanchor` → clean with the same tree → two beats, the second without
   change. Every observation leaves the head where it was. One test, no
   variants (docs/v2/12 §1, front «Operación y vista»)."
  (:require [babashka.fs :as fs]
            [clojure.string :as str]
            [clojure.test :refer [deftest is testing]]
            [kimun.cli.main :as main]
            [kimun.cli.store :as cli-store]
            [kimun.host.treesitter :as ts]
            [kimun.surface.heartbeat :as heartbeat]
            [kimun.surface.markdown.ast :as ast]
            [kimun.test-util :as ktu]))

(def env {"USER" "tester"})

(defn- run [p & argv] (:envelope (main/run (vec argv) env p)))
(defn- file [p & parts] (str (apply fs/path p parts)))
(defn- head [p] (let [{:keys [store]} (cli-store/open! (file p ".kimun"))]
                  (:head store)))
(defn- signals [p] (mapv :type (get-in (run p "stores" "sense") [:data :signals])))
(defn- health [p] (select-keys (get-in (run p "stores" "health") [:data]) [:status :next :actions]))
(defn- tree-of [p nm]
  (first (keep #(when (= nm (:name %)) (:tree %)) (get-in (run p "docs" "list") [:data]))))

(defmacro as-treesitter
  "`KIMUN_PARSER=treesitter` with the pod answering, without parsing
   (`contract_test`): sensing compares contracts."
  [& body]
  `(with-redefs [ts/available? (constantly true)
                 ts/version (constantly {:pod "pod-kimun-treesitter" :version "9.9.9" :tree_sitter "0.25"})]
     (binding [ast/*parser* "treesitter"] ~@body)))

(defn- tour!
  "The tour itself, on the project at `p` (the caller owns the temporary dir)."
  [p]
    (fs/create-dirs (file p "docs")) (fs/create-dirs (file p "notes"))
    (spit (file p "ticket.edn")
          (pr-str {:model/name "TicketDoc" :model/version 1 :base []
                   :fields [{:name "title" :type :string :required true :description "Title."}
                            {:name "status" :type :string :required true :description "Status."}]
                   :template "# ⸢rev•title⸥\n\nStatus: ⸢rev•status⸥\n"}))
    (spit (file p "docs" "a.md") "# T\n\nStatus: open\n")
    (spit (file p "notes" "plain.md") "# Note\n\none\n")
    (is (true? (:ok (run p "stores" "init" "--name" "tour"))))
    (is (true? (:ok (run p "models" "define" "-f" "ticket.edn"))))
    (is (true? (:ok (run p "docs" "track" "TicketDoc" "docs/a.md"))))
    (is (true? (:ok (run p "docs" "ingest" "notes/plain.md"))))

    (testing "rest"
      (let [h (head p)]
        (is (= [] (signals p)))
        (is (= :ok (:status (health p))))
        (is (= h (head p)) "observing writes nothing")))

    (testing "a mutated source is a signal and a degraded health"
      (let [h (head p)]
        (spit (file p "docs" "a.md") "# T\n\nStatus: closed\n")
        (is (= [:source/mutated] (signals p)))
        (let [{:keys [status next actions]} (health p)]
          (is (= :needs-human status))
          (is (str/starts-with? next "inspect:") "`:next` asks for an inspection")
          (is (not (some #{next} actions)) "and it is not one of the proposed actions")
          (is (= ["kimun docs update a"] actions)))
        (is (= h (head p)) "neither sense nor health writes")))

    ;; the test stands in for the actor: no person reviews anything here
    (testing "one of the proposed actions, applied as an explicit decision, reconciles the store"
      (let [action (first (:actions (health p)))]
        (is (true? (:ok (apply run p (rest (str/split action #" "))))) (pr-str action))
        (is (= :ok (:status (health p))))
        (is (= [] (signals p)))))

    (testing "the same source under another parser is a contract change (K6)"
      (as-treesitter
        (is (= [:contract/changed :contract/changed] (signals p))
            "both documents record the native contract")
        (is (= [:degraded "run: kimun stores update"]
               [(:status (health p)) (:next (health p))])
            "here `:next` IS a no-decision repair, unlike the edit above")
        (is (true? (:ok (run p "stores" "update"))))
        (is (= [] (signals p))))
      ;; the contract is recorded under the parser in force: back to the
      ;; native one, the same no-decision repair is what fixes it
      (is (true? (:ok (run p "stores" "update"))))
      (is (= [] (signals p))))

    (testing "moving is only reported as an absence; the re-anchor is declared"
      (let [t0 (tree-of p "plain")]
        (fs/create-dirs (file p "b"))
        (fs/move (file p "notes" "plain.md") (file p "b" "plain.md"))
        (is (= [:source/missing] (signals p)) "the absence does not end the unit")
        (is (true? (:ok (run p "docs" "reanchor" "plain" "b/plain.md" "--reason" "moved by hand"))))
        (is (= t0 (tree-of p "plain")) "the tree id is kept")
        (is (= [] (signals p))))

    (testing "two beats: the second one without change, and nothing written"
      (let [h (head p)
            observed (atom [])
            r (heartbeat/run {:observe (fn [] (run p "stores" "sense"))
                              :emit (fn [rec] (swap! observed conj rec) true)
                              :wait (fn [_] false)
                              :stop? (fn [] false)
                              :clock (fn [] "2026-11-15T00:00:00.000Z")
                              :now-ms (fn [] 0)
                              :store {:name "tour"}
                              :interval-ms 10
                              :beats 2})
            beats (filter #(= "beat" (:kind %)) @observed)]
        (is (= :beats (:reason r)))
        (is (= 2 (count beats)))
        (is (false? (get (second beats) :changed)) "the second beat repeats the state")
        (is (= h (head p)) "the heartbeat writes nothing")))))

(deftest the-tour-of-one-mutation
  (binding [ast/*parser* "native"]
    (let [p (str (ktu/temp-dir "kimun-tour-"))]
      (try (tour! p) (finally (fs/delete-tree p))))))
