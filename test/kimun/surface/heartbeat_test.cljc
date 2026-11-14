(ns kimun.surface.heartbeat-test
  "The heartbeat loop without a process or a clock: every effect is injected,
   so the tests drive beats, failures, stops and a gone consumer directly."
  (:require [clojure.test :refer [deftest is testing]]
            [kimun.surface.heartbeat :as hb]))

(defn- observation
  "A successful observation result with head `rev` and `signals`."
  [rev signals]
  {:ok true :store {:name "s" :path "/s/.kimun"} :revision rev
   :data {:observed-at "t" :tracked 1 :valid (empty? signals) :signals signals
          :counts {:total (count signals)} :summary "x"}})

(def ^:private failure
  {:ok false :error {:type "store/busy" :message "busy" :data {}} :exit 4})

(defn- drive
  "Runs the loop over the scripted `results` (one per attempt). `opts` may set
   `:beats`, `:stop-after` (a stop is requested during the wait that follows
   that attempt) and `:deliver` (records the consumer accepts before it is
   gone). Returns `{:result :records :waits}`."
  [results {:keys [beats stop-after deliver]}]
  (let [records (atom []) waits (atom []) queue (atom results) stop (atom false) tick (atom 0)]
    {:result (hb/run {:observe #(let [r (first @queue)] (swap! queue rest) r)
                      :emit (fn [r] (if (and deliver (>= (count @records) deliver))
                                      false
                                      (do (swap! records conj r) true)))
                      :wait (fn [ms]
                              (swap! waits conj ms)
                              (when (= stop-after (count @waits)) (reset! stop true))
                              @stop)
                      :stop? #(deref stop)
                      :clock (constantly "T")
                      :now-ms #(swap! tick + 5)
                      :store {:name "s" :path "/s/.kimun"}
                      :interval-ms 250
                      :beats beats})
     :records @records
     :waits @waits}))

(deftest a-stream-is-started-beats-stopped
  (let [{:keys [result records waits]} (drive (repeat (observation "r1" [])) {:beats 3})]
    (is (= {:reason :beats :beats 3 :errors 0} result))
    (is (= ["started" "beat" "beat" "beat" "stopped"] (mapv :kind records)))
    (is (= {:kind "started" :schema "kimun.heartbeat/1" :at "T" :store {:name "s" :path "/s/.kimun"}
            :interval-ms 250 :beats 3}
           (first records)))
    (is (= [1 2 3] (keep :seq records)))
    (is (= [250 250] waits) "a fixed delay after each observation, none after the last")
    (is (= {:kind "stopped" :at "T" :reason "beats" :beats 3 :errors 0} (last records)))))

(deftest a-beat-carries-the-observation-of-sense
  (let [o (observation "r1" [{:type :source/mutated}])
        b (second (:records (drive [o] {:beats 1})))]
    (is (= {:kind "beat" :seq 1 :store (:store o) :revision "r1" :changed true :elapsed-ms 5
            :observation (:data o)}
           b))))

(deftest changed-compares-with-the-last-successful-observation
  (let [s [{:type :source/mutated :document "login"}]
        {:keys [records]} (drive [(observation "r1" []) (observation "r1" [])
                                  (observation "r1" s) failure (observation "r1" s)
                                  (observation "r2" s)]
                                 {:beats 6})]
    (is (= [true false true nil false true] (mapv :changed (filter :seq records))))
    (testing "the observation time alone is not a change"
      (is (= (hb/fingerprint (observation "r1" s))
             (hb/fingerprint (assoc-in (observation "r1" s) [:data :observed-at] "later")))))))

(deftest a-failed-observation-is-an-error-record-and-the-loop-goes-on
  (let [{:keys [result records]} (drive [(observation "r1" []) failure failure (observation "r1" [])]
                                        {:beats 4})]
    (is (= ["started" "beat" "error" "error" "beat" "stopped"] (mapv :kind records)))
    (is (= {:kind "error" :seq 2 :at "T" :error (:error failure) :consecutive-errors 1 :elapsed-ms 5}
           (nth records 2)))
    (is (= [1 2] (keep :consecutive-errors records)) "the count restarts after a success")
    (is (false? (:changed (nth records 4))) "a failure does not forget the last observation")
    (is (= {:reason :beats :beats 4 :errors 2} result))))

(deftest a-stop-requested-during-the-wait-ends-with-stopped
  (let [{:keys [result records]} (drive (repeat (observation "r1" [])) {:stop-after 2})]
    (is (= {:reason :cancelled :beats 2 :errors 0} result))
    (is (= ["started" "beat" "beat" "stopped"] (mapv :kind records)))
    (is (= "cancelled" (:reason (last records))))))

(deftest a-stop-requested-before-the-first-beat-observes-nothing
  (let [observed (atom 0) records (atom [])]
    (hb/run {:observe #(swap! observed inc) :emit #(do (swap! records conj %) true)
             :wait (constantly true) :stop? (constantly true) :clock (constantly "T")
             :now-ms (constantly 0) :store {} :interval-ms 100 :beats nil})
    (is (zero? @observed))
    (is (= ["started" "stopped"] (mapv :kind @records)))))

(deftest a-gone-consumer-ends-the-loop-without-more-output
  (testing "the consumer leaves after the first beat"
    (let [{:keys [result records waits]} (drive (repeat (observation "r1" [])) {:deliver 2})]
      (is (= {:reason :stdout-closed :beats 2 :errors 0} result))
      (is (= ["started" "beat"] (mapv :kind records)))
      (is (= [250] waits))))
  (testing "the consumer was never there"
    (let [{:keys [result records]} (drive (repeat (observation "r1" [])) {:deliver 0})]
      (is (= {:reason :stdout-closed :beats 0 :errors 0} result))
      (is (empty? records)))))

(deftest interval-and-beats-are-validated
  (is (= 30000 (hb/parse-interval nil)))
  (is (= [100 1500 60000] (mapv hb/parse-interval ["0.1" "1.5" " 60 "])))
  (is (= [nil nil nil nil nil] (mapv hb/parse-interval ["0" "0.05" "-1" "abc" "86401"])))
  (is (nil? (hb/parse-beats nil)))
  (is (= [1 20] (mapv hb/parse-beats ["1" "20"])))
  (is (= [:invalid :invalid :invalid :invalid] (mapv hb/parse-beats ["0" "-1" "x" "1.5"]))))
