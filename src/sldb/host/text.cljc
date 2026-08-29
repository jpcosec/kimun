(ns sldb.host.text
  "Host adapter for text normalization. Only `sldb.host.*` namespaces may use
   reader conditionals (docs/v2/02 §8.1)."
  #?(:clj (:import [java.text Normalizer Normalizer$Form])))

(defn nfc
  "Unicode NFC normalization of a string (UAX #15). Identity for non-strings."
  [s]
  (if (string? s)
    #?(:clj  (Normalizer/normalize ^String s Normalizer$Form/NFC)
       :cljs (.normalize s "NFC"))
    s))

(defn nfc?
  "True when `s` is already in NFC form."
  [s]
  (= s (nfc s)))
