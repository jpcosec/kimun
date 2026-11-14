(ns kimun.surface.heartbeat
  "The heartbeat loop (docs/v2/06 §3, `stores heartbeat`): periodically
   recompute the existing sensorization signals and hand each observation to a
   consumer as one record.

   Its only function is to sense. It dispatches no action, runs no LLM, computes
   no embedding and writes nothing: every effect is injected (`:observe`,
   `:emit`, `:wait`, `:stop?`, the clocks), so the loop itself is pure of
   process and of time. It keeps no state between beats beyond the fingerprint
   of the last successful observation, which only answers `:changed`."
  (:require [clojure.string :as str]))

(def schema
  "Name and version of the record contract, carried by the `started` record."
  "kimun.heartbeat/1")

(def stop-reasons
  "Why a heartbeat ends: the requested number of beats was reached, a stop was
   requested (signal), or the consumer stopped reading."
  #{:beats :cancelled :stdout-closed})

(defn fingerprint
  "What makes two observations the same for `:changed`: the head revision, the
   tracked count, validity and the signals. The observation time is excluded."
  [{:keys [revision data]}]
  [revision (:tracked data) (:valid data) (:signals data)])

(defn started
  "The first record of a stream: the contract `:schema`, the store, the
   interval in milliseconds and the requested number of beats (nil: unbounded)."
  [at store interval-ms beats]
  {:kind "started" :schema schema :at at :store store
   :interval-ms interval-ms :beats beats})

(defn beat
  "The record of a successful observation: `:seq` (1-based attempt number),
   the store and head revision observed, `:changed` (differs from the last
   successful observation of this process; true for the first), the time the
   observation took and the `:observation` itself, the data of `stores sense`."
  [n {:keys [store revision data]} changed elapsed-ms]
  {:kind "beat" :seq n :store store :revision revision
   :changed changed :elapsed-ms elapsed-ms :observation data})

(defn error
  "The record of a failed observation: the attempt `:seq`, when it failed, the
   `:error` (`{:type :message :data}`, the shape of a failure envelope) and
   how many attempts in a row have failed. Every failure is treated as
   transient: the next beat observes again from scratch."
  [n at err consecutive elapsed-ms]
  {:kind "error" :seq n :at at :error err
   :consecutive-errors consecutive :elapsed-ms elapsed-ms})

(defn stopped
  "The last record of a stream that ended in order: the `:reason` (one of
   `stop-reasons`, as a string), the attempts made and how many failed."
  [at reason beats errors]
  {:kind "stopped" :at at :reason (name reason) :beats beats :errors errors})

(defn run
  "Runs the heartbeat until it is stopped; returns `{:reason :beats :errors}`.

   `opts`:
   - `:observe` `(fn [] result)`: one observation, never raising; `result` is
     `{:ok true :store :revision :data}` or `{:ok false :error {…}}`.
   - `:emit` `(fn [record] delivered?)`: hands a record to the consumer; false
     when the consumer is gone.
   - `:wait` `(fn [ms] stop?)`: waits up to `ms`; true when a stop was requested.
   - `:stop?` `(fn [] bool)`: true once a stop was requested.
   - `:clock` `(fn [] timestamp-string)`, `:now-ms` `(fn [] long)`.
   - `:store`, `:interval-ms`, `:beats` (nil: unbounded).

   The interval is a fixed delay counted from the end of an observation, so
   observations never overlap or pile up. A stop is honoured between
   observations and during the wait, never in the middle of one. A failed
   observation is an `error` record and the loop goes on. Every stop but a
   gone consumer ends with a `stopped` record."
  [{:keys [observe emit wait stop? clock now-ms store interval-ms beats]}]
  (let [finish (fn [reason n errors]
                 (when-not (= :stdout-closed reason)
                   (emit (stopped (clock) reason n errors)))
                 {:reason reason :beats n :errors errors})]
    (if-not (emit (started (clock) store interval-ms beats))
      {:reason :stdout-closed :beats 0 :errors 0}
      (loop [n 0 previous ::none consecutive 0 errors 0]
        (if (stop?)
          (finish :cancelled n errors)
          (let [n (inc n)
                t0 (now-ms)
                r (observe)
                elapsed (- (now-ms) t0)
                ok? (boolean (:ok r))
                fp (when ok? (fingerprint r))
                consecutive (if ok? 0 (inc consecutive))
                errors (if ok? errors (inc errors))
                delivered? (emit (if ok?
                                   (beat n r (not= fp previous) elapsed)
                                   (error n (clock) (:error r) consecutive elapsed)))
                previous (if ok? fp previous)]
            (cond
              (not delivered?) (finish :stdout-closed n errors)
              (and beats (>= n beats)) (finish :beats n errors)
              (wait interval-ms) (finish :cancelled n errors)
              :else (recur n previous consecutive errors))))))))

(defn parse-interval
  "Milliseconds of an `--interval` value in seconds (decimals allowed), or nil
   when it is not a number of at least 0.1 s. A nil or blank value is the
   default, 30 s."
  [v]
  (if (or (nil? v) (str/blank? (str v)))
    30000
    (let [s (when (re-matches #"\d+(\.\d+)?" (str/trim (str v)))
              #?(:clj (Double/parseDouble (str/trim (str v)))
                 :cljs (js/parseFloat (str/trim (str v)))))]
      (when (and s (>= s 0.1) (<= s 86400))
        (long (Math/round (* 1000.0 s)))))))

(defn parse-beats
  "The number of an `--beats` value: nil for nil (unbounded), a positive
   integer, or `:invalid`."
  [v]
  (cond
    (nil? v) nil
    (re-matches #"[1-9]\d{0,8}" (str/trim (str v)))
    #?(:clj (Long/parseLong (str/trim (str v))) :cljs (js/parseInt (str/trim (str v))))
    :else :invalid))
