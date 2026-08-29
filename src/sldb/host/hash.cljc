(ns sldb.host.hash
  "Host adapter implementing `sldb.kernel.ports/Hasher` (docs/v2/02 §8.1):
   SHA-256 over the UTF-8 encoding of a string. Babashka/JVM uses
   MessageDigest; ClojureScript/Node uses `crypto`."
  (:require [sldb.kernel.ports :as ports])
  #?(:clj (:import [java.security MessageDigest]
                   [java.nio.charset StandardCharsets])))

(defn utf8-bytes
  "UTF-8 encoding of a string as a byte array (or Buffer on Node)."
  [^String s]
  #?(:clj  (.getBytes s StandardCharsets/UTF_8)
     :cljs (js/Buffer.from s "utf8")))

#?(:clj
   (defn- hex [^bytes digest]
     (let [sb (StringBuilder. (* 2 (alength digest)))]
       (doseq [b digest]
         (let [v (bit-and b 0xff)]
           (when (< v 16) (.append sb "0"))
           (.append sb (Integer/toHexString v))))
       (str sb))))

(defn sha256-hex
  "Lower-case hex SHA-256 of a byte array."
  [bs]
  #?(:clj  (hex (.digest (MessageDigest/getInstance "SHA-256") ^bytes bs))
     :cljs (let [crypto (js/require "crypto")]
             (-> (.createHash crypto "sha256") (.update bs) (.digest "hex")))))

(defrecord Sha256 []
  ports/Hasher
  (algorithm [_] :sha-256)
  (digest-str [_ s] (sha256-hex (utf8-bytes s))))

(def sha-256
  "The standard Hasher: SHA-256 over UTF-8."
  (->Sha256))
