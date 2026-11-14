(ns kimun.surface.signals-test
  "The pure mapping from diagnostic findings to structured sensorization
   signals. The first cut is read-only and independent of D0: signals are
   observations, not identities or stored events."
  (:require [clojure.test :refer [deftest is testing]]
            [kimun.surface.signals :as signals]))

(defn- finding [category & {:as extras}]
  (merge {:category category
          :model "TicketDoc"
          :document "login"
          :path "login.md"
          :needs-human (not (contains? #{:ok :reformatted :contract-moved} category))
          :action (when-not (= :ok category) "kimun docs update login")
          :message (str "finding " (name category))}
         extras))

(deftest maps-each-diagnostic-category-to-a-stable-signal-type
  (is (= {:corrupt :store/corrupt
          :edited :source/mutated
          :reformatted :source/reformatted
          :missing :source/missing
          :contract-moved :contract/changed
          :unextractable :source/unreadable
          :noise :source/noise
          :orphan :model/missing}
         signals/category->type))
  (is (= :diagnostic/unknown (:type (signals/from-finding (finding :something-new))))))

(deftest keeps-the-repair-and-human-decision-fields
  (let [s (signals/from-finding (finding :edited :fields ["status"]))]
    (is (= :source/mutated (:type s)))
    (is (= :edited (:category s)))
    (is (= :warning (:severity s)))
    (is (= [true "kimun docs update login"] [(:needs-human s) (:action s)]))
    (is (= {:fields ["status"]} (:detail s)))
    (is (nil? (:id s)) "a signal is not an identity")
    (is (nil? (:confidence s)) "no similarity is asserted")))

(deftest orders-critical-first-then-warning-then-info
  (let [report {:findings [(finding :reformatted)
                           (finding :missing :document "gone")
                           (finding :corrupt :document nil :needs-human true)
                           (finding :edited :document "login")]}
        ss (signals/from-report report)]
    (is (= [:critical :warning :warning :info] (mapv :severity ss)))
    (is (= [:store/corrupt :source/missing :source/mutated :source/reformatted]
           (mapv :type ss)))
    (is (= {:total 4
            :by-type {:source/missing 1 :source/mutated 1 :source/reformatted 1 :store/corrupt 1}
            :by-severity {:critical 1 :info 1 :warning 2}}
           (signals/counts ss)))))

(deftest clean-report-has-no-signals
  (is (= [] (signals/from-report {:findings []})))
  (is (= "no signals" (signals/summary [])))
  (is (= "2 signal(s) — 1 warning, 1 info"
         (signals/summary [(signals/from-finding (finding :edited))
                           (signals/from-finding (finding :reformatted))]))))
