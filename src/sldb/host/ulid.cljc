(ns sldb.host.ulid
  "Host adapter implementing `sldb.kernel.ports/IdMinter` (docs/v2/02 §3.1):
   ULID = 48-bit millisecond time + 80-bit randomness, 26 Crockford-base32
   characters. Monotonicity within a millisecond is not guaranteed (drawer task)."
  (:require [sldb.kernel.ports :as ports])
  #?(:clj (:import [java.security SecureRandom])))

(def ^:private alphabet "0123456789ABCDEFGHJKMNPQRSTVWXYZ")

(defn- now-ms []
  #?(:clj (System/currentTimeMillis) :cljs (js/Date.now)))

#?(:clj (def ^:private rng (SecureRandom.)))

(defn- random-bytes [n]
  #?(:clj  (let [bs (byte-array n)] (.nextBytes ^SecureRandom rng bs) (mapv #(bit-and % 0xff) bs))
     :cljs (let [crypto (js/require "crypto")] (vec (array-seq (.randomBytes crypto n))))))

(defn- encode [value chars]
  (loop [i (dec chars) v value acc ()]
    (if (neg? i)
      (apply str acc)
      (recur (dec i) (quot v 32) (conj acc (nth alphabet (mod v 32)))))))

(defn ulid
  "A fresh ULID string."
  []
  (str (encode (now-ms) 10)
       (encode (reduce (fn [acc b] (+ (* acc 256N) b)) 0N (random-bytes 10)) 16)))

(defrecord Minter []
  ports/IdMinter
  (ulid [_] (ulid)))

(def minter
  "The standard IdMinter."
  (->Minter))
