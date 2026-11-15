(ns kimun.surface.markdown.anchors-test
  "S2a: structural anchors as evidence, without identity
   (docs/v2/12 §1 S2, docs/v2/14 §13). A locator is where something was read
   under a reading contract, and its ordinals move when something is inserted;
   the hash says whether what is there changed. Nothing here mints an id."
  (:require [clojure.string :as str]
            [clojure.test :refer [deftest is testing]]
            [kimun.host.default :as host]
            [kimun.host.treesitter :as ts]
            [kimun.kernel.canon :as canon]
            [kimun.surface.markdown.anchors :as anchors]
            [kimun.surface.markdown.ast :as ast]
            [kimun.surface.models.blocks :as blocks]))

(def doc
  (str "---\ntitle: fixture\n---\n\n"
       "# A\n\n"
       "Primer párrafo.\n\n"
       "## A.1\n\n"
       "Segundo párrafo.\n\n"
       "## A.1\n\n"
       "Tercer párrafo.\n\n"
       "- uno\n- dos\n\n"
       "```clj\n(+ 1 2)\n```\n\n"
       "> cita\n\n"
       "# B\n\n"
       "Último.\n"))

(defn- spans
  "Line spans of the block layer for `text`, after checking it is one to one
   with the neutral AST (same count, same digests). Nil when it is not: no
   range is invented from a misaligned layer."
  [text]
  (let [kids (:children (ast/parse host/host text))
        bs (blocks/parse text)
        sp (mapv :map bs)
        lines (vec (str/split-lines text))
        slice (fn [[ls le]] (str/join "\n" (subvec lines (long ls) (long (min le (count lines))))))]
    (when (and (every? some? sp) (= (count kids) (count sp))
               (every? true? (map (fn [m b]
                                    (let [kk (:children (ast/parse host/host (slice m)))]
                                      (and (= 1 (count kk))
                                           (= (canon/digest host/host (first kk))
                                              (canon/digest host/host b)))))
                                  sp kids)))
      sp)))

(defn- anchors-of [text] (anchors/anchors host/host text {:spans (spans text)}))

(defn- section-locators [a] (mapv :locator (:sections a)))
(defn- block-locators [a] (mapv :locator (:blocks a)))
(defn- hashes [a] (into {} (map (fn [b] [(:locator b) (:hash b)]) (:blocks a))))
(defn- section-hashes [a] (into {} (map (fn [s] [(:locator s) (:hash s)]) (:sections a))))

(deftest the-shape-and-the-locators
  (let [a (anchors-of doc)
        s0 [{:title "A" :n 0}]
        s1 (conj s0 {:title "A.1" :n 0})
        s2 (conj s0 {:title "A.1" :n 1})
        s3 [{:title "B" :n 0}]]
    (is (= [s0 s1 s2 s3] (section-locators a)) "sections nest by level, sisters counted")
    (is (= [1 2 2 1] (mapv :level (:sections a))))
    (is (= [9 2 5 2] (mapv :blocks (:sections a))))
    (is (= [{:section [] :type :opaque :n 0}
            {:section s0 :type :heading :n 0}
            {:section s0 :type :paragraph :n 0}
            {:section s1 :type :heading :n 0}
            {:section s1 :type :paragraph :n 0}
            {:section s2 :type :heading :n 0}
            {:section s2 :type :paragraph :n 0}
            {:section s2 :type :list :n 0}
            {:section s2 :type :code :n 0}
            {:section s2 :type :quote :n 0}
            {:section s3 :type :heading :n 0}
            {:section s3 :type :paragraph :n 0}]
           (block-locators a)))
    (let [all (concat (section-locators a) (block-locators a))]
      (is (pos? (count all)))
      (is (= (count all) (count (distinct all))) "every locator is different"))
    (testing "resolve-anchor reads the same anchors back"
      (is (= :code (get-in (anchors/resolve-anchor a (nth (block-locators a) 8))
                           [:locator :type])))
      (is (= 2 (:level (anchors/resolve-anchor a s1))))
      (is (nil? (anchors/resolve-anchor a [{:title "nope" :n 0}]))))))

(deftest editing-a-block-is-local
  (let [a (anchors-of doc)
        edited (str/replace doc "Segundo párrafo." "Segundo párrafo, editado.")
        b (anchors-of edited)
        h0 (hashes a) h1 (hashes b)
        sh0 (section-hashes a) sh1 (section-hashes b)
        changed (into #{} (comp (filter (fn [[k v]] (not= v (get h0 k)))) (map key)) h1)
        changed-sections (into #{} (comp (filter (fn [[k v]] (not= v (get sh0 k)))) (map key)) sh1)]
    (is (= (block-locators a) (block-locators b)) "no locator moves when a block is edited")
    (is (= (section-locators a) (section-locators b)))
    (is (= #{{:section [{:title "A" :n 0} {:title "A.1" :n 0}] :type :paragraph :n 0}} changed)
        "only the edited block changes its hash")
    (is (= #{(conj [{:title "A" :n 0}] {:title "A.1" :n 0}) [{:title "A" :n 0}]}
           changed-sections)
        "and the sections that contain it")
    (is (pos? (count changed)) "there is something to compare")
    (is (< (count changed) (count h0)))))

(deftest inserting-shifts-ordinals-only-in-that-section
  (let [a (anchors-of doc)
        ;; a paragraph right after A's heading: the paragraphs of A below move
        inserted (str/replace doc "# A\n\nPrimer párrafo." "# A\n\nNuevo primero.\n\nPrimer párrafo.")
        b (anchors-of inserted)
        paragraphs (fn [x section]
                     (filterv #(and (= :paragraph (:type %)) (= section (:section %)))
                              (block-locators x)))
        hash-at (fn [x loc] (get (hashes x) loc))
        s0 [{:title "A" :n 0}]
        s2 (conj s0 {:title "A.1" :n 1})
        s3 [{:title "B" :n 0}]
        first-a {:section s0 :type :paragraph :n 0}
        second-a {:section s0 :type :paragraph :n 1}]
    (is (= 1 (count (paragraphs a s0))) "A has one paragraph before")
    (is (= 2 (count (paragraphs b s0))) "and two after the insertion")
    (is (= #{0 1} (set (map :n (paragraphs b s0)))))
    (testing "the same paragraph is now n 1: its hash moved with the ordinal"
      (is (= (hash-at a first-a) (hash-at b second-a)))
      (is (not= (hash-at a first-a) (hash-at b first-a))))
    (testing "the other sections and the other kinds keep their locators"
      (is (= (filter #(= s2 (:section %)) (block-locators a))
             (filter #(= s2 (:section %)) (block-locators b))))
      (is (= (filter #(= s3 (:section %)) (block-locators a))
             (filter #(= s3 (:section %)) (block-locators b))))
      (is (= (filter #(= :code (:type %)) (block-locators a))
             (filter #(= :code (:type %)) (block-locators b)))))))

(deftest the-range-is-the-block
  (let [a (anchors-of doc)]
    (is (every? :lines (:blocks a)) "the block layer aligns here: there are ranges")
    (is (every? :bytes (:blocks a)))
    (is (every? :lines (:sections a)))
    (is (pos? (count (:blocks a))))
    (testing "a block's byte range, parsed alone, is the same block"
      (doseq [b (:blocks a)]
        (let [[s e] (:bytes b)
              slice (subs doc (long s) (long e))
              kids (:children (ast/parse host/host slice))]
          (is (= 1 (count kids)) (pr-str (:locator b)))
          (is (= (:hash b) (canon/digest host/host (first kids))) (pr-str (:locator b))))))
    (testing "ranges do not overlap and are in document order"
      (let [spans (mapv :bytes (:blocks a))]
        (is (every? (fn [[[s e] [s2 _]]] (<= e s2)) (partition 2 1 spans))
            (pr-str spans))
        (is (apply < (map (comp long first) spans))))
      (testing "a subsection sits inside its parent and siblings do not overlap"
        (let [by-loc (into {} (map (fn [s] [(:locator s) (:bytes s)]) (:sections a)))
              a0 [{:title "A" :n 0}]
              a1 (conj a0 {:title "A.1" :n 0})
              a2 (conj a0 {:title "A.1" :n 1})
              b0 [{:title "B" :n 0}]
              [ps pe :as parent] (get by-loc a0)]
          (is (pos? (count by-loc)))
          (doseq [child [a1 a2]]
            (let [[cs ce] (get by-loc child)]
              (is (and (<= ps cs) (<= ce pe)) (pr-str child " inside " parent))))
          (let [[x1 x2] (get by-loc a1) [y1 y2] (get by-loc a2) [z1 _] (get by-loc b0)]
            (is (<= x2 y1))
            (is (<= y2 z1))))))))

(deftest both-parsers-give-the-same-anchors
  (binding [ast/*parser* "native"]
    (let [native (anchors-of doc)]
      (when (ts/available?)
        (binding [ast/*parser* "treesitter"]
          (let [pod (anchors-of doc)]
            (is (= (section-locators native) (section-locators pod)))
            (is (= (block-locators native) (block-locators pod)))
            (is (= (hashes native) (hashes pod)))
            (is (= (section-hashes native) (section-hashes pod)))
            (is (= (mapv :lines (:blocks native)) (mapv :lines (:blocks pod))))))))))
