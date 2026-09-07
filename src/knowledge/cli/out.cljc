(ns knowledge.cli.out
  "Result envelopes of the `knowledge` CLI and their rendering (docs/v2/06 §B):
   every command returns one envelope value, `{:ok true …}` or `{:ok false …}`,
   which `emit` renders as json, edn or text. Building an envelope never
   performs I/O; printing is the caller's job."
  (:require [cheshire.core :as json]
            [clojure.string :as str]))

(defn type-name
  "The wire form of an error type: a keyword `:store/error` becomes the string
   \"store/error\"; strings pass through."
  [t]
  (if (keyword? t) (subs (str t) 1) (str t)))

(defn ok
  "A success envelope for `command` (a string such as \"stores check\").
   `extra` may carry `:store {:name :path}`, `:revision` (head or nil),
   `:data` and `:warnings` (default [])."
  [command {:keys [store revision data warnings]}]
  {:ok true
   :command command
   :store store
   :revision revision
   :data data
   :warnings (vec (or warnings []))})

(defn failure
  "A failure envelope: `type` (keyword or string), a human `message`, the
   reproducing `data` (never a stacktrace) and the process `exit` code."
  [type message data exit]
  {:ok false
   :error {:type (type-name type) :message message :data (or data {})}
   :exit exit})

(defn exit-code
  "Process exit code of an envelope: 0 for success, its `:exit` otherwise."
  [envelope]
  (if (:ok envelope) 0 (or (:exit envelope) 70)))

(defn- value-str [v]
  (cond (string? v) v
        (keyword? v) (type-name v)
        (nil? v) "nil"
        :else (pr-str v)))

(defn- kv-line [indent k v]
  (let [v' (value-str v)]
    (if (and (string? v) (str/includes? v "\n"))
      (str indent (value-str k) ":\n" v')
      (str indent (value-str k) ": " v'))))

(defn- sorted-entries [m]
  (sort-by (comp str key) m))

(defn- text-success [{:keys [command data revision]}]
  (let [body (cond (= "help" command) [(:usage data)]
                   (map? data) (map (fn [[k v]] (kv-line "" k v)) (sorted-entries data))
                   (sequential? data) (map value-str data)
                   (nil? data) []
                   :else [(value-str data)])
        rev (when revision [(str "revision: " revision)])]
    (str/join "\n" (concat body rev))))

(defn- text-failure [{:keys [error]}]
  (let [{:keys [type message data]} error]
    (str/join "\n" (cons (str "✗ " type ": " message)
                         (map (fn [[k v]] (kv-line "  " k v)) (sorted-entries data))))))

(defn emit
  "Renders `envelope` as a string in format `fmt`: \"json\" (pretty, via
   cheshire), \"edn\" (`pr-str`, reads back with clojure.edn) or \"text\"
   (human: `key: value` lines of :data when it is a map, one line per item when
   it is sequential, then `revision: …` when present; failures render as
   `✗ <type>: <message>` followed by indented `key: value` lines of the data)."
  [envelope fmt]
  (case fmt
    "json" (json/generate-string envelope {:pretty true})
    "edn" (pr-str envelope)
    "text" (if (:ok envelope) (text-success envelope) (text-failure envelope))
    (json/generate-string envelope {:pretty true})))
