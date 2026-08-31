(ns sldb.kernel.standoff-test
  (:require [clojure.test :refer [deftest is testing]]
            [sldb.host.default :as host]
            [sldb.kernel.revision :as rev]
            [sldb.kernel.standoff :as standoff]))

(def h host/host)
(def caps {"jp" :all})
(def TS "2026-08-29T12:00:00.000Z")

;; Leaf texts. All three are NFC-stable, so node/make (which NFC-normalizes) does
;; not change them, and the emoji ones have graphemes wider than one UTF-16 unit
;; so grapheme offsets differ from code-unit offsets.
(def emoji "a\uD83D\uDE00b")            ; graphemes [a 😀 b]        : 3 graphemes, 4 UTF-16 units
(def prose "Hola mundo. Chau.")          ; 17 graphemes, two sentences
(def mixed "Hi \uD83D\uDE00. Bye.")     ; 10 graphemes, 11 UTF-16 units, two sentences

(defn- store-with
  "A store holding the given texts as :sign/:text leaves. Returns
   {:store store :ids {text leaf-id}}."
  [texts]
  (let [ops (map-indexed (fn [i t] {:op :add-node
                                    :node {:class :sign :kind :text :content {:text t}}
                                    :as (keyword (str "leaf" i))})
                         texts)
        r (rev/apply-plan (rev/empty-store h caps)
                          {:plan/version 1 :base nil :actor "jp" :engines {} :timestamp TS
                           :ops (vec ops)})
        a (:aliases r)]
    {:store (:store r)
     :ids (into {} (map-indexed (fn [i t] [t (a (keyword (str "leaf" i)))]) texts))}))

;; ---------------------------------------------------------------- §4 UAX #29 layers

(deftest word-and-sentence-offsets-are-grapheme-based
  (let [{:keys [store ids]} (store-with [emoji prose mixed])]
    (testing "a multi-UTF-16 grapheme shifts every offset that follows it"
      (let [l (standoff/layers store (ids emoji))]
        (is (= ["a" "\uD83D\uDE00" "b"] (:graphemes l)))
        ;; the trailing "b" sits at grapheme offset 2; its UTF-16 code-unit offset is 3
        (is (= [[0 1] [1 2] [2 3]] (:words l)))
        (is (= [[0 3]] (:sentences l)))))
    (testing "words and sentences of plain prose are UAX #29 ranges over graphemes"
      (let [l (standoff/layers store (ids prose))]
        (is (= 17 (count (:graphemes l))))
        (is (= [[0 4] [4 5] [5 10] [10 11] [11 12] [12 16] [16 17]] (:words l)))
        (is (= [[0 12] [12 17]] (:sentences l)))))
    (testing "sentence boundaries also count graphemes, not code units"
      (let [l (standoff/layers store (ids mixed))]
        (is (= 10 (count (:graphemes l))))
        (is (= [[0 6] [6 10]] (:sentences l)))))))

(deftest layers-are-deterministic
  (let [{:keys [store ids]} (store-with [prose])]
    (is (= (standoff/layers store (ids prose))
           (standoff/layers store (ids prose))))))

;; ---------------------------------------------------------------- memoization by leaf id

(deftest layers-are-memoized-by-leaf-id
  (let [text (str "unique memo probe " (rand))       ; a leaf id no other test derives
        {:keys [store ids]} (store-with [text])
        id (ids text)]
    (is (not (standoff/cached? id)) "not memoized before the first derivation")
    (let [first-call (standoff/layers store id)]
      (is (standoff/cached? id) "memoized after the first derivation")
      (is (identical? first-call (standoff/layers store id))
          "a second call returns the very same cached value, not a fresh derivation"))))

;; ---------------------------------------------------------------- §4.1 virtual address

(deftest an-address-resolves-without-materializing-a-node
  (let [{:keys [store ids]} (store-with [emoji prose mixed])
        n-before (count (:objects store))]
    (testing "the grapheme range [1 2] over \"a😀b\" is the emoji, not a UTF-16 half"
      (is (= "\uD83D\uDE00" (standoff/resolve-address store (ids emoji) 1 2))))
    (testing "a word range resolves to the word"
      (is (= "Hola" (standoff/resolve-address store (ids prose) 0 4))))
    (testing "a sentence range resolves to the sentence, grapheme-addressed"
      (is (= "Chau." (standoff/resolve-address store (ids prose) 12 17)))
      (is (= "Bye." (standoff/resolve-address store (ids mixed) 6 10))))
    (testing "the full leaf and an empty range are addressable"
      (is (= emoji (standoff/resolve-address store (ids emoji) 0 3)))
      (is (= "" (standoff/resolve-address store (ids prose) 4 4))))
    (testing "resolving created no node: the store object count is unchanged"
      (is (= n-before (count (:objects store)))))))

;; ---------------------------------------------------------------- typed errors

(defn- boom [f]
  (try (f) nil (catch #?(:clj clojure.lang.ExceptionInfo :cljs :default) e (ex-data e))))

(deftest out-of-range-and-unresolved-leaf-raise-typed-errors
  (let [{:keys [store ids]} (store-with [prose])]
    (testing "a range whose end runs past the leaf grapheme count"
      (let [d (boom #(standoff/resolve-address store (ids prose) 0 99))]
        (is (= :standoff/range-out-of-bounds (:type d)))
        (is (= 17 (:grapheme-count d)))))
    (testing "a leaf id that is not in the pool"
      (is (= :standoff/unresolved-leaf
             (:type (boom #(standoff/resolve-address store "no-such-leaf" 0 1))))))
    (testing "a node that exists but is not a text leaf does not resolve"
      (let [r (rev/apply-plan store
                              {:plan/version 1 :base (:head store) :actor "jp" :engines {} :timestamp TS
                               :ops [{:op :add-node
                                      :node {:class :symbol :kind :term :content {:name "x" :lang "es"}}
                                      :as :sym}]})
            sym (get (:aliases r) :sym)]
        (is (= :standoff/unresolved-leaf
               (:type (boom #(standoff/resolve-address (:store r) sym 0 1)))))
        (is (= :standoff/unresolved-leaf
               (:type (boom #(standoff/layers (:store r) sym)))))))
    (testing "a malformed range is rejected before any leaf lookup"
      (is (= :standoff/invalid-range
             (:type (boom #(standoff/resolve-address store (ids prose) 3 1))))))))
