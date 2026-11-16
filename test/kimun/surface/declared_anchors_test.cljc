(ns kimun.surface.declared-anchors-test
  "The anchors written in declarations (`kimun.surface.declared-anchors`), with the texts
   injected: what each form of `(at …)` resolves to, every state with and without a commit
   to compare with, and that a file git does not report is never read."
  (:require [clojure.test :refer [deftest is testing]]
            [kimun.host.default :as host]
            [kimun.kernel.canon :as canon]
            [kimun.surface.declare :as declare]
            [kimun.surface.declared-anchors :as da]
            [kimun.surface.markdown.ast :as ast]))

(def t0 "# Note\n\nUno.\n\nDos.\n\n## Sub\n\nTres.\n\n## Sub\n\nCuatro.\n")

(defn- props
  "The propositions of a declaration file that anchors each of the `ats` (source strings of `(at …)`)."
  [& ats]
  (:propositions
   (declare/read-declarations
    (str "(ns t.d)\n(relation anchors \"A is in B.\")\n"
         (apply str (map-indexed (fn [i a] (str "(anchors x" i " " a ")\n")) ats)))
    "d.clj")))

(defn- states
  "Rows for `ats` with `now`/`then` maps path→text; `changed` nil = no comparison."
  [ats now then changed]
  (binding [ast/*parser* "native"]
    (da/states host/host (apply props ats) {:read-now now :read-then then :changed changed})))

(defn- one [at now then changed] (first (states [at] now then changed)))

(defn- m [text] (fn [p] (when (= p "n.md") text)))

(def sec "(at \"n.md\" [\"Note\" \"Sub\"])")
(defn- para [n] (str "(at \"n.md\" [\"Note\"] {:type :paragraph :n " n "})"))

(deftest forms-resolve-without-comparison
  (let [r #(one % (m t0) nil nil)
        h #(:hash (r %))]
    (testing "every form resolves; different forms hold different hashes"
      (is (every? #(= :resolved (:status (r %)))
                  [sec "(at \"n.md\" [\"Note\" [\"Sub\" 2]])" (para 1) (para 2)
                   "(at \"n.md\" [\"Note\"] {:type :paragraph :n 1} [0 2])" "(at \"n.md\")"]))
      (is (= 6 (count (distinct (map h [sec "(at \"n.md\" [\"Note\" [\"Sub\" 2]])" (para 1) (para 2)
                                         "(at \"n.md\" [\"Note\"] {:type :paragraph :n 1} [0 2])" "(at \"n.md\")"]))))))
    (testing "the 2nd sister with the same title is another section than the 1st"
      (is (not= (h sec) (h "(at \"n.md\" [\"Note\" [\"Sub\" 2]])")))
      (is (= :unresolved (:status (r "(at \"n.md\" [\"Note\" [\"Sub\" 3]])")))))
    (testing "the ordinal of a block is 1-based: :n 1 is the first paragraph"
      (is (= (h (para 1)) (:hash (one (para 1) (m "# Note\n\nUno.\n") nil nil))))
      (is (= :unresolved (:status (r (para 9))))))
    (testing "a missing file"
      (is (= :file-missing (:status (one sec (constantly nil) nil nil)))))
    (testing "the row says where and what"
      (let [x (r sec)]
        (is (= {:file "d.clj" :line 3} (:declared-at x)))
        (is (= {:term "x0"} (:other x)))
        (is (= :b (:role (first (states ["(at \"n.md\")"] (m t0) nil nil)))))
        (is (= :section (:kind x)))))))

(deftest whole-file-of-any-type-and-inner-locators-on-code
  (let [txt "plain text\n"
        now {"a.txt" txt}
        rd #(get now %)]
    (is (= {:status :resolved :hash (canon/digest host/host txt)}
           (select-keys (one "(at \"a.txt\")" rd nil nil) [:status :hash])))
    (let [x (one "(at \"a.txt\" [\"T\"])" rd nil nil)]
      (is (= :unsupported (:status x)))
      (is (string? (:reason x))))
    (testing "unsupported also with a comparison, even for an unchanged file"
      (is (= :unsupported (:status (one "(at \"a.txt\" [\"T\"])" rd rd #{})))))
    (testing "a file anchored whole mutates with any byte of it"
      (is (= :mutated (:status (one "(at \"a.txt\")" rd (constantly "other\n") #{"a.txt"}))))
      (is (= :anchored (:status (one "(at \"a.txt\")" rd (constantly txt) #{"a.txt"})))))))

(deftest states-with-a-commit
  (let [then (m t0)
        cmp (fn [at now-text] (one at (m now-text) then #{"n.md"}))
        st #(:status (cmp %1 %2))]
    (testing "editing the anchored paragraph mutates it; editing another leaves it anchored"
      (is (= :mutated (st (para 2) (.replace t0 "Dos." "Dos, editado."))))
      (is (= :anchored (st (para 1) (.replace t0 "Dos." "Dos, editado."))))
      (is (= :anchored (st sec (.replace t0 "Dos." "Dos, editado.")))
          "the section Note/Sub does not hold that paragraph"))
    (testing "a section moved to elsewhere in the title path is missing"
      (is (= :missing (:status (cmp sec "# Note\n\nUno.\n\nDos.\n")))))
    (testing "a range edited"
      (is (= :mutated (st "(at \"n.md\" [\"Note\"] {:type :paragraph :n 1} [0 2])" (.replace t0 "Uno." "Xno."))))
      (is (= :anchored (st "(at \"n.md\" [\"Note\"] {:type :paragraph :n 1} [0 2])" (.replace t0 "Uno." "Unx.")))))
    (testing "an inserted paragraph shifts the ordinal: ambiguous, with candidates and none chosen"
      (let [x (cmp (para 1) "# Note\n\nCero.\n\nUno.\n\nDos.\n\n## Sub\n\nTres.\n\n## Sub\n\nCuatro.\n")]
        (is (= :ambiguous (:status x)))
        (is (= #{[{:section [{:title "Note" :n 0}] :type :paragraph :n 0} :locator]
                 [{:section [{:title "Note" :n 0}] :type :paragraph :n 1} :hash]}
               (set (map (juxt :locator :reason) (:candidates x)))))
        (is (not= (:before x) (:hash x)))))
    (testing "the whole file deleted: missing"
      (is (= :missing (:status (one sec (constantly nil) then #{"n.md"})))))))

(deftest unknown-when-the-commit-does-not-say
  (testing "the file was not at the commit"
    (let [x (one sec (m t0) (constantly nil) #{"n.md"})]
      (is (= [:unknown :file-not-at-since] ((juxt :status :reason) x)))))
  (testing "the locator did not resolve in the text of the commit"
    (let [x (one sec (m t0) (m "# Note\n\nUno.\n") #{"n.md"})]
      (is (= [:unknown :unresolved-at-since] ((juxt :status :reason) x))))))

(deftest a-file-git-does-not-report-is-not-opened
  (let [reads (atom [])
        rd (fn [p] (swap! reads conj p) (when (= p "n.md") t0))
        rows (binding [ast/*parser* "native"]
               (da/states host/host (props sec "(at \"quiet.md\" [\"A\"])" "(at \"quiet.md\")")
                          {:read-now rd :read-then rd :changed #{"n.md"}}))]
    (is (= [:anchored :anchored :anchored] (mapv :status rows)))
    (is (= [true false false] (mapv :opened rows)))
    (is (not-any? #{"quiet.md"} @reads) "neither the text now nor the text then of an unreported file")
    (is (seq @reads) "while the reported one was read")
    (testing "without comparison everything is opened"
      (reset! reads [])
      (binding [ast/*parser* "native"]
        (da/states host/host (props "(at \"quiet.md\")") {:read-now rd :changed nil}))
      (is (= ["quiet.md"] @reads)))))

(deftest badly-formed-locators-are-declared-with-file-and-line
  (let [err (fn [at]
              (try (states [at] (m t0) nil nil) nil
                   (catch clojure.lang.ExceptionInfo e (assoc (ex-data e) :message (ex-message e)))))]
    (doseq [at ["(at \"n.md\" [\"A\"] {:type :paragraph})"            ; no :n
                "(at \"n.md\" [\"A\"] {:type :nope :n 1})"            ; unknown type
                "(at \"n.md\" [\"A\"] {:type :paragraph :n 0})"       ; 0 is not 1-based
                "(at \"n.md\" [[\"A\" 0]])"                           ; bad sister ordinal
                "(at \"n.md\" \"A\")"                                 ; path is not a vector
                "(at \"n.md\" [\"A\"] {:type :paragraph :n 1} [3 3])" ; empty range
                "(at \"n.md\" [\"A\"] {:type :paragraph :n 1} [0 2] 1)"
                "(at \"n.md\" [])"                                    ; root section alone
                "(at \"../x.md\")" "(at \"/etc/passwd\")"]]
      (let [e (err at)]
        (is (= :declare/bad-locator (:type e)) at)
        (is (= "d.clj" (:file e)) at)
        (is (= 3 (:line e)) at)
        (is (re-find #"^d\.clj:3: " (:message e)) at)))))
