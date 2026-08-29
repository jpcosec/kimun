(ns sldb.host.hash
  "Host adapter for hashing (docs/v2/02 §8.1). The kernel only depends on the
   `Hasher` protocol; the algorithm name is recorded in the store descriptor."
  #?(:clj (:import [java.security MessageDigest]
                   [java.nio.charset StandardCharsets])))

(defprotocol Hasher
  (algorithm [this] "Keyword naming the algorithm, e.g. :sha-256.")
  (hash-bytes [this ^bytes bs] "Lower-case hex digest of a byte array."))

#?(:clj
   (defn- hex [^bytes digest]
     (let [sb (StringBuilder. (* 2 (alength digest)))]
       (doseq [b digest]
         (let [v (bit-and b 0xff)]
           (when (< v 16) (.append sb "0"))
           (.append sb (Integer/toHexString v))))
       (str sb))))

(defrecord Sha256 []
  Hasher
  (algorithm [_] :sha-256)
  (hash-bytes [_ bs]
    #?(:clj  (let [md (MessageDigest/getInstance "SHA-256")]
               (hex (.digest md ^bytes bs)))
       :cljs (let [crypto (js/require "crypto")]
               (-> (.createHash crypto "sha256")
                   (.update bs)
                   (.digest "hex"))))))

(def sha-256 (->Sha256))

(defn utf8-bytes
  "UTF-8 encoding of a string as a byte array (or Buffer on Node)."
  [^String s]
  #?(:clj  (.getBytes s StandardCharsets/UTF_8)
     :cljs (js/Buffer.from s "utf8")))
