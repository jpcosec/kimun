(ns kimun.cli.errors
  "Error contract of the `kimun` CLI (docs/v2/06 §B): maps every kernel or
   surface error `:type` to a process exit code and turns any Throwable that
   escapes a command into a failure envelope, never a stacktrace."
  (:require [kimun.cli.out :as out]))

(def ^:private exit-by-type
  {:eval/missing 1
   :eval/ambiguous 2
   :plan/conflict 4
   :store/stale 4
   :cli/no-store 5
   :cli/store-exists 5
   :cli/usage 6
   :org.babashka/cli 6
   :eval/not-available 6})

(def ^:private exit-by-namespace
  {"query" 3 "models" 3 "validate" 3
   "store" 5 "markdown" 5 "index" 5})

(defn exit-for
  "Exit code for error `type` (a namespaced keyword) with its `data`: 0 for nil;
   1 missing; 2 ambiguous; 3 rejection (`:plan/rejected` unless the failed
   check is `:capability`, which is 7; `:query/*`, `:models/*`, `:validate/*`);
   4 conflict/stale; 5 store errors, corruption, missing or duplicate store,
   markdown and index errors; 6 usage; 70 anything else."
  ([type] (exit-for type nil))
  ([type data]
   (cond
     (nil? type) 0
     (= :plan/rejected type) (if (= :capability (:check data)) 7 3)
     (contains? exit-by-type type) (exit-by-type type)
     (and (keyword? type) (contains? exit-by-namespace (namespace type))) (exit-by-namespace (namespace type))
     :else 70)))

(defn- cli-data
  "The reproducing part of an ex-data: everything but the type, and for
   babashka.cli parse errors only the fields that describe the offending flag."
  [type data]
  (if (= :org.babashka/cli type)
    (select-keys data [:msg :cause :option :flag :value])
    (dissoc data :type)))

(defn wrap
  "Runs `thunk` and returns an envelope. A returned envelope passes through. An
   ExceptionInfo whose data carries `:type` becomes a failure envelope with that
   type, the exception message, the data minus `:type` and the exit of
   `exit-for`. Any other Throwable becomes type `cli/unexpected`, exit 70, with
   the exception message. The Throwable is kept as `:throwable` in the
   envelope's metadata so `--debug` can print it to stderr."
  [thunk]
  (try (thunk)
       (catch clojure.lang.ExceptionInfo e
         (let [data (ex-data e)
               type (:type data)]
           (if type
             (with-meta (out/failure type (ex-message e) (cli-data type data) (exit-for type data))
               {:throwable e})
             (with-meta (out/failure :cli/unexpected (ex-message e) (or data {}) 70)
               {:throwable e}))))
       (catch Throwable e
         (with-meta (out/failure :cli/unexpected (or (ex-message e) (str (class e))) {} 70)
           {:throwable e}))))
