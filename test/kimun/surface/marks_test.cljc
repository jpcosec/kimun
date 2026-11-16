(ns kimun.surface.marks-test
  "K1e, pure part: the state of a structural mark against the anchors of the
   text read now, and what a normal write does with it. A mark has identity of
   its own (minted when declared); its anchor (locator + contract) is evidence.
   kimun never decides that a unit moved, was split or is another one: it
   computes a state and, at most, PROPOSES candidates (docs/v2/14 §20–§21;
   rules are the team's inferences)."
  (:require [clojure.test :refer [deftest is testing]]
            [kimun.host.default :as host]
            [kimun.surface.markdown.anchors :as anchors]))

(def doc
  (str "# A\n\nUno.\n\nDos.\n\n## B\n\nTres.\n\nCuatro.\n\n# C\n\nCinco.\n"))

(defn- as [] (anchors/anchors host/host doc))

(defn- sec [title] [{:title title :n 0}])
(defn- blk [section type n] {:section section :type type :n n})
(defn- hash-of-block [a section type n]
  (:hash (anchors/resolve-anchor a (blk section type n))))
(defn- hash-of-section [a section]
  (:hash (anchors/resolve-anchor a section)))

(defn- mark [kind locator hash] {:kind kind :locator locator :hash hash})

(deftest the-state-table
  (let [a (as)
        sA (sec "A") sB (conj sA {:title "B" :n 0})
        p0 (blk sA :paragraph 0)
        h0 (hash-of-block a sA :paragraph 0)
        hb (hash-of-block a sB :paragraph 0)
        nowhere "0000000000000000000000000000000000000000000000000000000000000000"
        states (into {} (map (juxt :id identity))
                     (anchors/mark-states
                      a
                      {"anchored" (mark :block p0 h0)
                       "mutated" (mark :block p0 nowhere)
                       "ambiguous" (mark :block p0 hb)
                       "missing-empty" (mark :block (blk (conj sA {:title "Z" :n 0}) :paragraph 0) nowhere)
                       "missing-cand" (mark :block (blk (conj sA {:title "Z" :n 0}) :paragraph 0) hb)
                       "section" (mark :section sB (hash-of-section a sB))}))]
    (is (= 6 (count states)) "there are marks to compare")
    (testing "anchored: the locator resolves to the registered hash"
      (is (= :anchored (:status (states "anchored"))))
      (is (= [] (:candidates (states "anchored")))))
    (testing "mutated: it resolves to something else and the old hash is nowhere"
      (is (= :mutated (:status (states "mutated"))))
      (is (= [] (:candidates (states "mutated")))))
    (testing "ambiguous: it resolves to another thing and the old hash is elsewhere"
      (is (= :ambiguous (:status (states "ambiguous"))))
      (is (= [{:locator p0 :reason :locator}
              {:locator (blk sB :paragraph 0) :reason :hash}]
             (:candidates (states "ambiguous")))))
    (testing "missing without candidates: the locator does not resolve and the hash is nowhere"
      (is (= :missing (:status (states "missing-empty"))))
      (is (= [] (:candidates (states "missing-empty")))))
    (testing "missing with a candidate: the hash appears elsewhere"
      (is (= :missing (:status (states "missing-cand"))))
      (is (= [{:locator (blk sB :paragraph 0) :reason :hash}]
             (:candidates (states "missing-cand")))))
    (testing "a section mark reads the same way"
      (is (= :anchored (:status (states "section")))))))

(deftest one-to-many-lists-every-candidate-and-decides-nothing
  (let [dup (str "# A\n\nMismo.\n\nOtro.\n\n# B\n\nMismo.\n\nMismo.\n")
        a (anchors/anchors host/host dup)
        sA [{:title "A" :n 0}] sB (conj sA {:title "B" :n 0})
        same (hash-of-block a sA :paragraph 0)
        first-b (blk sB :paragraph 0)
        victim (blk sA :paragraph 1)
        marks {"m" (mark :block victim same)}
        st (first (anchors/mark-states a marks))]
    (is (= 4 (count (:candidates st))) "one locator and three hashes, all listed")
    (is (= #{:locator :hash} (set (map :reason (:candidates st)))))
    (is (= 1 (count (filter #(= :locator (:reason %)) (:candidates st)))))
    (is (= 3 (count (filter #(= :hash (:reason %)) (:candidates st)))))
    (is (= victim (:locator (first (filter #(= :locator (:reason %)) (:candidates st))))))
    (is (= 4 (count (distinct (map :locator (:candidates st))))) "each candidate is a different place")
    (is (= :ambiguous (:status st)))
    (testing "a normal write does not touch an ambiguous mark"
      (is (= marks (anchors/advance-marks a marks))))))

(deftest a-normal-write-advances-only-the-mutated-hash
  (let [a (as)
        sA (sec "A") sB (conj sA {:title "B" :n 0})
        p0 (blk sA :paragraph 0)
        h0 (hash-of-block a sA :paragraph 0)
        hb (hash-of-block a sB :paragraph 0)
        nowhere "0000000000000000000000000000000000000000000000000000000000000000"
        marks {"anchored" (mark :block p0 h0)
               "mutated" (mark :block p0 nowhere)
               "ambiguous" (mark :block p0 hb)
               "missing" (mark :block (blk (conj sA {:title "Z" :n 0}) :paragraph 0) nowhere)}
        advanced (anchors/advance-marks a marks)]
    (is (= (set (keys marks)) (set (keys advanced))) "no mark is removed")
    (doseq [[id m] marks]
      (is (= (:id (get advanced id)) (:id m)))
      (is (= (:kind (get advanced id)) (:kind m)))
      (is (= (:locator (get advanced id)) (:locator m)) (str id " keeps its locator")))
    (is (= h0 (:hash (get advanced "anchored"))))
    (is (= (:hash (get advanced "mutated")) (:hash (anchors/resolve-anchor a p0)))
        "the mutated mark records the hash it was read at")
    (is (not= nowhere (:hash (get advanced "mutated"))))
    (is (= hb (:hash (get advanced "ambiguous"))) "ambiguous and missing keep the old hash")
    (is (= nowhere (:hash (get advanced "missing"))))))

(deftest an-anchored-mark-has-no-candidates-even-with-a-twin
  ;; two identical paragraphs: the mark on the first is anchored, and its twin
  ;; is not offered as a candidate (nothing is in doubt)
  (let [text "# A\n\nIgual.\n\nIgual.\n"
        a (anchors/anchors host/host text)
        loc (blk (sec "A") :paragraph 0)
        h (:hash (anchors/resolve-anchor a loc))
        twin (:hash (anchors/resolve-anchor a (blk (sec "A") :paragraph 1)))
        st (first (anchors/mark-states a {"m" (mark :block loc h)}))]
    (is (= h twin) "the two paragraphs do hash the same")
    (is (= :anchored (:status st)))
    (is (= [] (:candidates st)))))


;; ---------------------------------------------------------------- K1h: items and nested keys

(defn- fa [payload] (anchors/field-anchors host/host payload))
(defn- ilocs [payload] (mapv :locator (:items (fa payload))))

(deftest item-and-nested-marks-read-with-the-same-table
  (let [before (fa {"tags" ["a" "b" "c"] "meta" {"k" "v" "n" {"x" 1}}})
        item (fn [i] (:hash (anchors/resolve-anchor before {:field "tags" :index i})))
        nested (:hash (anchors/resolve-anchor before {:field "meta" :path ["n" "x"]}))
        marks {"b" {:kind :item :locator {:field "tags" :index 1} :hash (item 1)}
               "x" {:kind :nested :locator {:field "meta" :path ["n" "x"]} :hash nested}}
        status (fn [as] (into {} (map (juxt :id identity)) (anchors/mark-states as marks)))]
    (is (= 3 (count (:items before))) "there are items to compare")
    (is (= 3 (count (:nested before))) "every key path of the map: k, n and n.x")
    (is (= [:anchored :anchored] (map :status (vals (status before)))))
    (testing "an item inserted before it shifts the index: ambiguous, with the hash candidate, nothing decided"
      (let [st ((status (fa {"tags" ["a" "new" "b" "c"] "meta" {"k" "v" "n" {"x" 1}}})) "b")]
        (is (= :ambiguous (:status st)))
        (is (= [{:locator {:field "tags" :index 1} :reason :locator}
                {:locator {:field "tags" :index 2} :reason :hash}]
               (:candidates st)))))
    (testing "an edit in place is a mutation; the nested key too"
      (let [st (status (fa {"tags" ["a" "B" "c"] "meta" {"k" "v" "n" {"x" 2}}}))]
        (is (= [:mutated :mutated] [(:status (st "b")) (:status (st "x"))]))))
    (testing "the parent field gone: every mark is missing, none is removed, no candidate"
      (let [st (status (fa {}))]
        (is (= 2 (count st)))
        (is (= [:missing :missing] (map :status (vals st))))
        (is (= [[] []] (map :candidates (vals st))))))
    (testing "the same value in ANOTHER field is not a candidate"
      (let [st ((status (fa {"tags" ["a" "z" "c"] "other" ["b"]})) "b")]
        (is (= :mutated (:status st)))
        (is (= [] (:candidates st)))))))

(deftest identical-items-are-two-units-and-one-to-many-lists-all
  (let [p {"tags" ["dup" "dup" "x" "dup"]}
        a (fa p)
        h (:hash (first (:items a)))
        marks {"first" {:kind :item :locator {:field "tags" :index 0} :hash h}
               "second" {:kind :item :locator {:field "tags" :index 1} :hash h}}
        st (into {} (map (juxt :id identity)) (anchors/mark-states a marks))]
    (is (= 3 (count (filter #(= h (:hash %)) (:items a)))) "three items hash alike")
    (is (= [:anchored :anchored] [(:status (st "first")) (:status (st "second"))]) "two marks, two units")
    (testing "item 0 replaced: it is ambiguous and ALL the other equal items are listed"
      (let [a2 (fa {"tags" ["changed" "dup" "x" "dup"]})
            s (first (filter #(= "first" (:id %)) (anchors/mark-states a2 marks)))]
        (is (= :ambiguous (:status s)))
        (is (= [{:field "tags" :index 0} {:field "tags" :index 1} {:field "tags" :index 3}]
               (mapv :locator (:candidates s)))
            "the locator candidate and every hash candidate")
        (is (= marks (anchors/advance-marks a2 marks)) "a normal write does not choose")))
    (testing "advance-marks records the new hash of a mutated item only"
      (let [a3 (fa {"tags" ["Z" "other"]})
            adv (anchors/advance-marks a3 marks)]
        (is (not= h (:hash (adv "first"))))
        (is (= (:hash (anchors/resolve-anchor a3 {:field "tags" :index 0})) (:hash (adv "first"))))
        (is (= (:hash (anchors/resolve-anchor a3 {:field "tags" :index 1})) (:hash (adv "second"))))
        (is (= (map :locator (vals marks)) (map :locator (vals adv))))))))
