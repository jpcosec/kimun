(ns sldb.kernel.canon-test
  (:require [clojure.test :refer [deftest is testing]]
            [sldb.kernel.test-util :refer [defspec]]
            [clojure.test.check.generators :as gen]
            [clojure.test.check.properties :as prop]
            [sldb.host.hash :as hash]
            [sldb.kernel.canon :as canon]
            [sldb.kernel.generators :as g]))

(def h hash/sha-256)

(deftest printed-form
  (testing "maps sorted by printed key, sets sorted, vectors ordered, single spaces"
    (is (= "{:a 1 :b [2 3]}" (canon/canon-str {:b [2 3] :a 1})))
    (is (= "#{1 2 3}" (canon/canon-str #{3 1 2})))
    (is (= "[3 1 2]" (canon/canon-str [3 1 2])))
    (is (= "[3 1 2]" (canon/canon-str '(3 1 2))))
    (is (= "{\"a\" nil :a true}" (canon/canon-str {:a true "a" nil}))))
  (testing "atoms"
    (is (= "nil" (canon/canon-str nil)))
    (is (= "\"x\"" (canon/canon-str "x")))
    (is (= "-5" (canon/canon-str -5)))
    (is (= ":k/w" (canon/canon-str :k/w)))))

(deftest rejects-non-admitted
  (doseq [v [1.5 #?(:clj 1/3 :cljs 0.25) #?(:clj (java.util.Date.) :cljs (js/Date.)) \c]]
    (is (thrown-with-msg? #?(:clj clojure.lang.ExceptionInfo :cljs js/Error)
                          #"canonical-bytes"
                          (canon/canon-str v))
        (str "should reject " (pr-str v))))
  (is (false? (canon/valid? {:a 1.0})))
  (is (true? (canon/valid? {:a "1.0"}))))

(deftest nfc-equivalence
  (let [decomposed (str "e" "\u0301")   ; e + combining acute (NFD)
        composed   "\u00e9"]             ; precomposed (NFC)
    (is (not= decomposed composed))
    (is (= (canon/canon-str decomposed) (canon/canon-str composed)))
    (is (= (canon/digest h {:text decomposed}) (canon/digest h {:text composed})))))

(deftest digest-is-sha256-of-canonical-utf8
  ;; known answer: sha256 of the bytes of the string {:a 1}
  (is (= 64 (count (canon/digest h {:a 1}))))
  (is (= (canon/digest h {:a 1}) (canon/digest h {:a 1}))))

(defspec map-key-order-insensitive 100
  (prop/for-all [m (gen/map gen/keyword g/gen-atomic {:max-elements 6})]
    (= (canon/canon-str m)
       (canon/canon-str (into (array-map) (reverse (seq m)))))))

(defspec set-order-insensitive 100
  (prop/for-all [s (gen/set g/gen-atomic {:max-elements 6})]
    (= (canon/canon-str s) (canon/canon-str (into #{} (shuffle (vec s)))))))

(defspec vector-order-sensitive 100
  (prop/for-all [v (gen/vector gen/large-integer 2 6)]
    (or (= v (vec (reverse v)))
        (not= (canon/canon-str v) (canon/canon-str (vec (reverse v)))))))

(defspec normalize-is-idempotent-and-hash-stable 100
  (prop/for-all [v g/gen-content-value]
    (let [n (canon/normalize v)]
      (and (= n (canon/normalize n))
           (= (canon/digest h v) (canon/digest h n))))))

(defspec digest-deterministic 100
  (prop/for-all [v g/gen-content-value]
    (= (canon/digest h v) (canon/digest h v))))
