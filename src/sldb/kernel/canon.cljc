(ns sldb.kernel.canon
  "Canonical EDN serialization — the reference implementation of
   `canonical-bytes` (docs/v2/02 §2.1).

   1. every string is NFC-normalized (through the host TextNormalizer);
   2. the value is printed as EDN with map entries sorted by `compare` of the
      printed key, set elements sorted by `compare` of their canonical printed
      form, vectors/lists in order; one space between elements, none next to
      delimiters;
   3. the host Hasher digests the UTF-8 encoding of that string.

   Admitted: strings, integers, booleans, nil, keywords, symbols, vectors,
   lists, maps, sets. Anything else (floats, ratios, #inst, #uuid, chars,
   records, functions) raises :canon/invalid-value."
  (:require [clojure.string :as str]
            [sldb.kernel.ports :as ports]
            [sldb.kernel.err :as err]))

(defn- invalid [v why]
  (err/raise :canon/invalid-value (str "canonical-bytes: " why) {:value v :why why}))

(defn- integer-value? [v]
  (and (number? v) (integer? v)))

(defn normalize
  "Walks `v` returning the same structure with every string NFC-normalized and
   every list/seq turned into a vector. This is the form the kernel stores."
  [host v]
  (cond
    (string? v) (ports/nfc (ports/text host) v)
    (map? v)    (into {} (map (fn [[k val]] [(normalize host k) (normalize host val)])) v)
    (set? v)    (into #{} (map #(normalize host %)) v)
    (or (vector? v) (list? v) (seq? v)) (into [] (map #(normalize host %)) v)
    :else       v))

(defn- atomic-str [v]
  (cond
    (nil? v)            "nil"
    (true? v)           "true"
    (false? v)          "false"
    (string? v)         (pr-str v)
    (keyword? v)        (pr-str v)
    (symbol? v)         (pr-str v)
    (integer-value? v)  (str v)
    (number? v)         (invalid v "floats and ratios are not admitted; pre-convert to a string")
    :else               nil))

(defn- join [open close parts]
  (str open (str/join " " parts) close))

(defn- print-canon
  "Step 2 over an already-normalized value."
  [v]
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
  "Canonical printed form of `v` (steps 1–2): NFC-normalized first, so sets or
   maps whose elements/keys differ only by normalization collapse before printing."
  [host v]
  (print-canon (normalize host v)))

(defn digest
  "Lower-case hex digest of the canonical form of `v` under the host Hasher (step 3)."
  [host v]
  (ports/digest-str (ports/hasher host) (canon-str host v)))

(defn valid?
  "True when `v` is admitted in canonical content."
  [host v]
  (err/rescue (fn [_] false) (canon-str host v) true))
