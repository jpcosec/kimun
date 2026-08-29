(ns sldb.kernel.canon
  "Canonical EDN serialization — the reference implementation of
   `canonical-bytes` (docs/v2/02 §2.1).

   Rules:
   1. every string is NFC-normalized;
   2. the value is printed as EDN where map entries are sorted by `compare` of the
      printed form of the key, set elements by `compare` of their canonical printed
      form, and vectors/lists keep their order; one space between elements, none
      next to delimiters;
   3. the resulting string is UTF-8 encoded.

   Only strings, integers, booleans, nil, keywords, symbols, vectors, lists, maps
   and sets are admitted. Anything else (floats, ratios, #inst, #uuid, chars,
   records, functions) is rejected with an ex-info of type :canon/invalid-value."
  (:require [clojure.string :as str]
            [sldb.host.text :as text]
            [sldb.host.hash :as hash]))

(defn- invalid [v why]
  (throw (ex-info (str "canonical-bytes: " why)
                  {:type :canon/invalid-value :value v :why why})))

(defn- integer-value? [v]
  #?(:clj  (or (instance? Long v) (instance? Integer v) (instance? java.math.BigInteger v)
               (instance? clojure.lang.BigInt v) (instance? Short v) (instance? Byte v))
     :cljs (and (number? v) (js/Number.isInteger v))))

(defn- atomic-str [v]
  (cond
    (nil? v)            "nil"
    (true? v)           "true"
    (false? v)          "false"
    (string? v)         (pr-str (text/nfc v))
    (keyword? v)        (pr-str v)
    (symbol? v)         (pr-str v)
    (integer-value? v)  (str v)
    (number? v)         (invalid v "floats and ratios are not admitted; pre-convert to a string")
    :else               nil))

(declare normalize)

(defn- join [open close parts]
  (str open (str/join " " parts) close))

(defn- print-canon [v]
  (if-some [s (atomic-str v)]
    s
    (cond
      (map? v)
      (let [entries (->> v
                         (map (fn [[k val]] [(print-canon k) (print-canon val)]))
                         (sort-by first compare))]
        (join "{" "}" (map (fn [[k val]] (str k " " val)) entries)))

      (set? v)
      (join "#{" "}" (sort compare (map print-canon v)))

      (or (vector? v) (list? v) (seq? v))
      (join "[" "]" (map print-canon v))

      :else
      (invalid v (str "type not admitted in canonical content: " (type v))))))

(defn canon-str
  "Canonical printed form of `v` (steps 1–2 of docs/v2/02 §2.1): the value is
   NFC-normalized first, so a set or map whose elements/keys differ only by
   normalization collapses before printing."
  [v]
  (print-canon (normalize v)))

(defn canonical-bytes
  "UTF-8 bytes of the canonical printed form of `v`."
  [v]
  (hash/utf8-bytes (canon-str v)))

(defn digest
  "Lower-case hex digest of `canonical-bytes v` under `hasher`."
  [hasher v]
  (hash/hash-bytes hasher (canonical-bytes v)))

(defn normalize
  "Walks `v` returning the same structure with every string NFC-normalized and
   every list/seq turned into a vector. This is the form the kernel stores."
  [v]
  (cond
    (string? v) (text/nfc v)
    (map? v)    (into {} (map (fn [[k val]] [(normalize k) (normalize val)])) v)
    (set? v)    (into #{} (map normalize) v)
    (or (vector? v) (list? v) (seq? v)) (into [] (map normalize) v)
    :else       v))

(defn valid?
  "True when `v` is admitted in canonical content (no exception from canon-str)."
  [v]
  (try (canon-str v) true
       (catch #?(:clj clojure.lang.ExceptionInfo :cljs :default) _ false)))
