(ns kimun.surface.declare-fixes-test
  "What showed up using `declare` for real (docs/v2/drafts/diseno-evaluar.md §12): reserved
   contexts, a relation named like an imported one, the line of each libspec, the time limit,
   the shape of `(at …)` checked when reading, and the form counter of `add`."
  (:require [clojure.test :refer [deftest is testing]]
            [kimun.host.default :as host]
            [kimun.surface.declare :as declare]))

(defn- failure
  ([files] (failure files {}))
  ([files opts]
   (try (declare/read-project host/host files opts) nil
        (catch clojure.lang.ExceptionInfo e (assoc (ex-data e) :message (ex-message e))))))

(def a-file {:file "kimun/a.clj" :text "(ns a)\n(relation isa \"d\")\n"})

(deftest reserved-contexts-are-declared-errors
  (doseq [c ["clojure.core" "user" "kimun.declare.impl" "clojure.string" "sci.core" "kimun.declare.x" "clojure"]]
    (let [f (failure [{:file "k/r.clj" :text (str "\n(ns " c ")\n")}])]
      (is (= [:declare/reserved-context "k/r.clj" 2 c] ((juxt :type :file :line :context) f)) c)
      (is (re-find #"^k/r\.clj:2: " (:message f)))))
  (testing "names that only look similar are fine"
    (is (nil? (failure [{:file "k/ok.clj" :text "(ns userland)"}])))
    (is (nil? (failure [{:file "k/ok.clj" :text "(ns kimun.declarations)"}])))))

(deftest an-own-relation-named-like-a-refer-is-a-conflict
  (let [f (failure [a-file {:file "kimun/b.clj" :text "(ns b (:require [a :refer [isa]]))\n\n(relation isa \"mine\")"}])]
    (is (= [:declare/relation-conflict "kimun/b.clj" 3 ["b" "a"]] ((juxt :type :file :line :contexts) f)))
    (is (re-find #"^kimun/b\.clj:3: " (:message f))))
  (testing "with :as there is no conflict: the names do not meet"
    (is (nil? (failure [a-file {:file "kimun/b.clj" :text "(ns b (:require [a :as a]))\n(relation isa \"mine\")\n(isa x y)\n(a/isa x y)"}])))))

(deftest a-libspec-repeated-has-its-own-line
  (let [f (failure [a-file {:file "kimun/b.clj" :text "(ns b\n  (:require [a :refer [isa]]\n            [a :refer [nope]]))"}])]
    (is (= [:declare/unknown-relation 3] ((juxt :type :line) f))))
  (let [f (failure [a-file {:file "kimun/b.clj" :text "(ns b\n  (:require [a :as x]\n            [a :as 1]))"}])]
    (is (= [:declare/ns-clauses 3] ((juxt :type :line) f)))))

(deftest an-endless-evaluation-is-a-timeout
  (let [t0 (System/nanoTime)
        f (failure [{:file "k/loop.clj" :text "(ns loop.x)\n(loop [] (recur))"}] {:timeout-ms 300})
        ms (/ (- (System/nanoTime) t0) 1e6)]
    (is (= [:declare/timeout "k/loop.clj" 300] ((juxt :type :file :timeout-ms) f)))
    (is (< ms 5000) "it returned, it did not hang"))
  (testing "a quick file under a limit is unaffected"
    (is (nil? (failure [a-file] {:timeout-ms 5000})))))

(deftest read-checks-the-shape-of-every-at
  (doseq [[text line] [["(ns p)\n(relation r \"d\")\n(r x (at \"docs/a.md\" [\"S\"] {:type :paragraph :n 0}))" 3]
                       ["(ns p)\n(relation r \"d\")\n\n(r (at \"/etc/passwd\") x)" 4]
                       ["(ns p)\n(relation r \"d\")\n(r x (at \"a.md\" :nope))" 3]]]
    (let [f (failure [{:file "p.clj" :text text}])]
      (is (= [:declare/bad-locator "p.clj" line] ((juxt :type :file :line) f)) text)))
  (testing "well formed ones read"
    (is (nil? (failure [{:file "p.clj" :text "(ns p)\n(relation r \"d\")\n(r x (at \"a.md\" [\"S\"] {:type :paragraph :n 1} [0 3]))\n(r x (at \"a.txt\"))"}])))))

(deftest count-forms-uses-the-reader
  (is (= [0 1 1 2 1] (mapv #(declare/count-forms % "FORM") ["; just a comment" "(isa a b)" "(isa a \")(\" ; ) (x)\n)" "(isa a b) (isa c d)" "#_(skipped) (kept)"])))
  (is (= :declare/syntax (:type (try (declare/count-forms "(isa a" "FORM") (catch clojure.lang.ExceptionInfo e (ex-data e)))))))
