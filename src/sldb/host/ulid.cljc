(ns sldb.host.ulid
  "Host adapter for minting nominal tree ids: ULID, 26 Crockford-base32 chars,
   48-bit millisecond time + 80-bit randomness (docs/v2/02 §3.1)."
  #?(:clj (:import [java.security SecureRandom])))

(def ^:private alphabet "0123456789ABCDEFGHJKMNPQRSTVWXYZ")

(defn- now-ms []
  #?(:clj (System/currentTimeMillis) :cljs (js/Date.now)))

#?(:clj (def ^:private rng (SecureRandom.)))

(defn- random-bytes [n]
  #?(:clj  (let [bs (byte-array n)] (.nextBytes ^SecureRandom rng bs) (mapv #(bit-and % 0xff) bs))
     :cljs (let [crypto (js/require "crypto")] (vec (array-seq (.randomBytes crypto n))))))

(defn- encode-time [ms]
  ;; 10 chars, most significant first
  (loop [i 9 v ms acc ()]
    (if (neg? i)
      (apply str acc)
      (recur (dec i) (quot v 32) (conj acc (nth alphabet (mod v 32)))))))

(defn- encode-random [bytes10]
  ;; 80 bits -> 16 chars of 5 bits
  (let [bits (reduce (fn [acc b] (+ (* acc 256N) b)) 0N bytes10)]
    (loop [i 15 v bits acc ()]
      (if (neg? i)
        (apply str acc)
        (recur (dec i) (quot v 32) (conj acc (nth alphabet (mod v 32))))))))

(defn ulid
  "A fresh ULID string."
  []
  (str (encode-time (now-ms)) (encode-random (random-bytes 10))))

(defn ulid?
  [s]
  (and (string? s) (= 26 (count s)) (every? #(some #{%} alphabet) s)))
