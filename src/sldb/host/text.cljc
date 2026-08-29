(ns sldb.host.text
  "Host adapter implementing `sldb.kernel.ports/TextNormalizer` (docs/v2/02
   §2.1 step 1, §8.1): Unicode NFC via java.text.Normalizer on Babashka/JVM and
   String.prototype.normalize on ClojureScript."
  (:require [sldb.kernel.ports :as ports])
  #?(:clj (:import [java.text Normalizer Normalizer$Form])))

(defn nfc
  "Unicode NFC normalization of a string (UAX #15). Identity for non-strings."
  [s]
  (if (string? s)
    #?(:clj  (Normalizer/normalize ^String s Normalizer$Form/NFC)
       :cljs (.normalize s "NFC"))
    s))

(defrecord Nfc []
  ports/TextNormalizer
  (nfc [_ s] (nfc s)))

(def normalizer
  "The standard TextNormalizer."
  (->Nfc))
