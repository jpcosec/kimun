(ns kimun.surface.models.capture-test
  "C6 of docs/v2/16 §3.3 (M1j): every capture carries the coordinates of where
   it was read, in the vocabulary of `kimun.surface.markdown.anchors` (S2a),
   and no identity. Block-view steps only: no pod needed for these."
  (:require [babashka.fs :as fs]
            [clojure.edn :as edn]
            [clojure.string :as str]
            [clojure.test :refer [deftest is testing]]
            [clojure.walk :as walk]
            [kimun.host.default :as host]
            [kimun.surface.markdown.anchors :as anchors]
            [kimun.surface.models.capture :as capture]
            [kimun.surface.models.recipe :as recipe]
            [kimun.surface.models.selector :as selector]))

(def ^:private atom-steps
  (recipe/recipes (:template (edn/read-string (slurp (str (fs/path "test" "fixtures" "models" "deskops" "AtomDoc.edn")))))))

(defn- atom-text [title first-para second-para]
  (str "---\nid: atom-x\ntitle: T\nfive_wh_one_plus: what\ntags: [a:b]\n---\n\n# " title
       "\n\n## Answer\n\n" first-para "\n\n" second-para "\n"))

(def ^:private base (atom-text "T" "First paragraph." "Second paragraph."))

(defn- cap [text] (capture/capture-selector atom-steps text))
(defn- field [text f] (get-in (cap text) [:captures f]))

(deftest an-edit-outside-the-capture-keeps-its-leaf-hashes
  (let [edited (atom-text "U" "First paragraph." "Second paragraph.")
        a (field base "answer")]
    (is (= 2 (count (:leaf-hashes a))) "the answer has two leaf blocks")
    (is (= (:leaf-hashes a) (:leaf-hashes (field edited "answer"))))
    (is (= (:value a) (:value (field edited "answer"))))
    (is (not= (:leaf-hashes a) (:leaf-hashes (field (atom-text "T" "First paragraph." "Second changed.") "answer")))
        "control: an edit inside does move them")))

(deftest an-edit-inside-the-capture-changes-only-the-leaf-it-touches
  (let [a (field base "answer")
        b (field (atom-text "T" "First paragraph." "Second changed.") "answer")
        meta-a (field base "five_wh_one_plus")
        meta-b (field (atom-text "T" "First paragraph." "Second changed.") "five_wh_one_plus")]
    (is (= (first (:leaf-hashes a)) (first (:leaf-hashes b))))
    (is (not= (second (:leaf-hashes a)) (second (:leaf-hashes b))))
    (is (= (:path a) (:path b)) "the locators did not move: only the content did")
    (is (seq (:leaf-hashes meta-a)))
    (is (= (:leaf-hashes meta-a) (:leaf-hashes meta-b)) "another field of the same document is untouched")))

(deftest the-spans-cut-the-text-back-into-the-value
  (let [a (field base "answer")
        [s0 s1] (:spans a)
        cut (subs base (first (:bytes s0)) (second (:bytes s1)))]
    (is (= (:value a) (str/trim cut)))
    (is (= "First paragraph." (str/trim (subs base (first (:bytes s0)) (second (:bytes s0))))))
    (testing "a document the block layer does not match one to one carries values and no ranges"
      (let [lazy (str base "\n> quoted\nlazy continuation\n")
            r (cap lazy)]
        (is (false? (:aligned? r)))
        (is (str/starts-with? (get-in r [:captures "answer" :value]) "First paragraph."))
        (is (empty? (select-keys (get-in r [:captures "answer"]) [:path :spans :leaf-hashes])))))))

(deftest no-capture-carries-an-identity
  (let [r (cap base)
        ks (atom #{})]
    (is (seq (:captures r)))
    (is (every? #(seq (:spans %)) (vals (:captures r))) "control: the coordinates are there")
    (walk/postwalk (fn [x] (when (map? x) (swap! ks into (keys x))) x) r)
    (is (not-any? #{:id :node-id :identity} @ks))
    (is (= (selector/extract-selector atom-steps base)
           (into {} (map (fn [[k c]] [k (:value c)])) (:captures r)))
        "the values are exactly those of extract-selector")))

(deftest the-leaves-move-as-the-s2a-blocks-do
  (let [anchored (fn [text] (anchors/anchors host/host text {:spans (capture/aligned-spans text)}))
        by-loc (fn [text] (into {} (map (juxt :locator :hash)) (:blocks (anchored text))))
        edited (atom-text "T" "First paragraph." "Second changed.")
        before (by-loc base) after (by-loc edited)
        moved (set (filter #(not= (before %) (after %)) (keys before)))
        a (field base "answer") b (field edited "answer")]
    (is (= 1 (count moved)))
    (is (= moved (set (map first (filter (fn [[_ h0 h1]] (not= h0 h1)) (map vector (:path a) (:leaf-hashes a) (:leaf-hashes b)))))))
    (is (= (:leaf-hashes a) (map before (:path a))) "a leaf hash is the S2a block hash at its locator")))

(deftest the-capture-format-is-stable
  (is (= {:aligned? true
          :captures
          {"answer" {:value "First paragraph.\n\nSecond paragraph."
                     :path [{:section [{:title "T" :n 0} {:title "Answer" :n 0}] :type :paragraph :n 0}
                            {:section [{:title "T" :n 0} {:title "Answer" :n 0}] :type :paragraph :n 1}]
                     :spans [{:lines [11 12] :bytes [80 97]} {:lines [13 14] :bytes [98 116]}]
                     :leaf-hashes (get-in (cap base) [:captures "answer" :leaf-hashes])}}}
         (update (cap base) :captures select-keys ["answer"]))))
