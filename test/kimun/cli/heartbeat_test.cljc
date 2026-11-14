(ns kimun.cli.heartbeat-test
  "`kimun stores heartbeat` end to end: an NDJSON stream of the observations of
   `stores sense`, which writes nothing to the store, fails as a normal envelope
   before the stream starts and stops in order on SIGTERM."
  (:require [babashka.fs :as fs]
            [babashka.process :as process]
            [cheshire.core :as json]
            [clojure.java.io :as io]
            [clojure.string :as str]
            [clojure.test :refer [deftest is testing]]
            [kimun.cli.main :as main]
            [kimun.test-util :as ktu]))

(def env {"USER" "tester"})

(defn- run [cwd & argv] (:envelope (main/run argv env cwd)))
(defn- file [p & parts] (str (apply fs/path p parts)))

(def ticket
  (pr-str {:model/name "TicketDoc" :model/version 1 :base []
           :fields [{:name "title" :type :string :required true :description "Title."}
                    {:name "status" :type :string :required true :description "Status."}]
           :template "# ⸢rev•title⸥\n\nStatus: ⸢rev•status⸥\n"}))

(defn- project
  "A project with TicketDoc and the tracked document `login` (login.md)."
  []
  (let [p (str (ktu/temp-dir "kimun-heartbeat-"))]
    (is (true? (:ok (run p "stores" "init" "--name" "p"))))
    (spit (file p "ticket.edn") ticket)
    (is (true? (:ok (run p "models" "define" "-f" "ticket.edn"))))
    (is (true? (:ok (run p "docs" "create" "TicketDoc" "login"
                          "--set" "title=T" "--set" "status=open"))))
    p))

(defn- store-files
  "Relative path → content of every file under the store directory of `p`."
  [p]
  (let [root (fs/path p ".kimun")]
    (into (sorted-map)
          (for [f (fs/glob root "**" {:hidden true}) :when (fs/regular-file? f)]
            [(str (fs/relativize root f)) (slurp (str f))]))))

(defn- stream
  "Runs `stores heartbeat` with `argv` in-process: `{:envelope e :lines [record]}`,
   every stdout line parsed as JSON."
  [p & argv]
  (let [e (atom nil)
        out (with-out-str (reset! e (apply run p "stores" "heartbeat" argv)))]
    {:envelope @e
     :raw out
     :lines (mapv #(json/parse-string % true) (str/split-lines out))}))

(deftest the-stream-is-ndjson-and-repeats-the-observation-of-sense
  (let [p (project)
        {:keys [envelope lines raw]} (stream p "--interval" "0.1" "--beats" "2")
        [started b1 b2 stopped] lines
        sensed (:data (run p "stores" "sense"))]
    (is (= ["started" "beat" "beat" "stopped"] (mapv :kind lines)))
    (is (str/ends-with? raw "\n"))
    (is (= {:schema "kimun.heartbeat/1" :interval-ms 100 :beats 2}
           (select-keys started [:schema :interval-ms :beats])))
    (is (= "p" (get-in started [:store :name])))
    (is (= [1 2] (mapv :seq [b1 b2])))
    (is (= [true false] (mapv :changed [b1 b2])))
    (is (string? (:revision b1)))
    (is (= (json/parse-string (json/generate-string (dissoc sensed :mode :observed-at)) true)
           (dissoc (:observation b2) :observed-at))
        "a beat carries exactly what `stores sense` reports")
    (is (= {:kind "stopped" :reason "beats" :beats 2 :errors 0} (dissoc stopped :at)))
    (testing "the envelope is returned for the exit code but marked as already streamed"
      (is (true? (:ok envelope)))
      (is (= "stores heartbeat" (:command envelope)))
      (is (true? (:streamed (meta envelope)))))))

(deftest a-mutated-source-shows-up-as-a-signal-in-the-beat
  (let [p (project)]
    (spit (file p "login.md") "# T\n\nStatus: closed\n")
    (let [beat (second (:lines (stream p "--interval" "0.1" "--beats" "1")))
          s (first (get-in beat [:observation :signals]))]
      (is (false? (get-in beat [:observation :valid])))
      (is (= ["source/mutated" "edited" "warning" "login" "kimun docs update login"]
             [(:type s) (:category s) (:severity s) (:document s) (:action s)])))))

(deftest the-heartbeat-writes-nothing-in-the-store
  (let [p (project)
        before (store-files p)]
    (spit (file p "login.md") "# T\n\nStatus: closed\n")
    (stream p "--interval" "0.1" "--beats" "3")
    (is (= before (store-files p))
        "no object, log line, head, cache entry or any other store file is added or changed")))

(deftest a-failing-observation-is-an-error-line-not-the-end
  (let [p (project)
        log (file p ".kimun" "heads.edn")
        good (slurp log)]
    (spit log "{:head")
    (let [{:keys [lines envelope]} (stream p "--interval" "0.1" "--beats" "2")]
      (spit log good)
      (is (= ["started" "error" "error" "stopped"] (mapv :kind lines)))
      (is (= [1 2] (mapv :consecutive-errors (filter #(= "error" (:kind %)) lines))))
      (is (string? (get-in (second lines) [:error :type])))
      (is (string? (get-in (second lines) [:error :message])))
      (is (= 2 (:errors (last lines))))
      (is (true? (:ok envelope)) "the daemon reports the failures; it does not die of them"))
    (is (= ["beat"] (mapv :kind (filter :seq (:lines (stream p "--interval" "0.1" "--beats" "1")))))
        "the next run observes again once the store reads")))

(deftest bad-options-and-a-missing-store-fail-before-any-record
  (let [p (project)]
    (doseq [argv [["--interval" "0"] ["--interval" "soon"] ["--beats" "0"] ["--beats" "x"]]]
      (let [{:keys [envelope raw]} (apply stream p argv)]
        (is (= ["cli/usage" 6 ""] [(get-in envelope [:error :type]) (:exit envelope) raw]) (pr-str argv))
        (is (nil? (:streamed (meta envelope))) "a pre-stream failure is printed as the usual envelope"))))
  (let [{:keys [envelope raw]} (stream (str (ktu/temp-dir "kimun-nostore-")) "--beats" "1")]
    (is (= ["cli/no-store" 5 ""] [(get-in envelope [:error :type]) (:exit envelope) raw]))))

(deftest sigterm-stops-in-order-with-a-stopped-record
  (let [p (project)
        proc (process/process {:dir p :err :string :extra-env {"USER" "tester"}}
                              "bb" "--config" (str (fs/absolutize "bb.edn"))
                              "-m" "kimun.cli.main" "--" "stores" "heartbeat" "--interval" "0.2")
        rdr (io/reader (:out proc))
        first-two [(.readLine rdr) (.readLine rdr)]]
    (is (= ["started" "beat"] (mapv #(:kind (json/parse-string % true)) first-two)))
    ;; `kill`, not Process.destroy: destroy closes this side of the pipe before the last record is read
    (process/shell "kill" "-TERM" (str (.pid ^Process (:proc proc))))
    (let [rest-lines (vec (line-seq rdr))
          exit (:exit @proc)
          final (json/parse-string (last rest-lines) true)]
      (is (= {:kind "stopped" :reason "cancelled" :errors 0} (select-keys final [:kind :reason :errors])))
      (is (every? #(contains? #{"beat" "stopped"} (:kind (json/parse-string % true))) rest-lines)
          "nothing but whole NDJSON records follows")
      (is (= 143 exit) "the process ends with the status of the signal"))))
