(ns kimun.surface.declare-project-test
  "A set of declaration files whose contexts require each other
   (docs/v2/drafts/diseno-evaluar.md §9, cut 3a): imports with `:refer` and
   `:as`, the relation belonging to the context that defines it, dependency
   order, and every declared error with file and line."
  (:require [clojure.test :refer [deftest is testing]]
            [kimun.surface.declare :as declare]))

(def a-file {:file "kimun/a.clj" :text "(ns a)\n(relation isa \"A is of class B.\")\n(relation anchors \"A is in B.\")\n(def z 5)\n(isa own Thing)"})

(def c-file {:file "kimun/c.clj" :text "(ns c (:require a))\n(relation isa \"another sense of isa\")\n(relation rel \"A relates to B.\")"})

(def b-file {:file "kimun/b.clj"
             :text "(ns b\n  (:require [a :refer [isa anchors]]\n            [c :as c]))\n(relation mine \"own\")\n(isa x Y)\n(c/isa x Y)\n(c/rel x (isa x Y))\n(mine x z)"})

(defn- by-file [results] (into {} (map (juxt :file identity)) results))

(defn- failure [files]
  (try (declare/read-project files) nil
       (catch clojure.lang.ExceptionInfo e (assoc (ex-data e) :message (ex-message e)))))

(deftest requires-with-refer-and-as-bring-relations
  (let [r (by-file (declare/read-project [b-file a-file c-file]))
        b (get r "kimun/b.clj")]
    (is (= ["kimun/b.clj" "kimun/a.clj" "kimun/c.clj"] (mapv :file (declare/read-project [b-file a-file c-file]))) "results in input order")
    (is (= {"mine" {:doc "own"}} (:relations b)) ":relations are the ones the file defines, not the imported")
    (is (= ["a/isa" "c/isa" "c/rel" "b/mine"] (mapv :r (:propositions b))))
    (is (= ["b" "b" "b" "b"] (mapv :context (:propositions b))) ":context is the context that writes the proposition")
    (is (= {:term "z"} (:b (last (:propositions b)))) "z is a term here: the def of context a is not shared")
    (is (= "a/isa" (:r (first (:propositions (get r "kimun/a.clj"))))) "a relation used by its own context is qualified by it")))

(deftest the-same-relation-name-in-two-contexts-is-two-relations
  (let [props (:propositions (get (by-file (declare/read-project [b-file a-file c-file])) "kimun/b.clj"))
        [p q] props]
    (is (= [(:a p) (:b p)] [(:a q) (:b q)]) "same arguments")
    (is (= ["a/isa" "c/isa"] [(:r p) (:r q)]))
    (is (not= (:id p) (:id q)) "the defining context enters the id")))

(deftest dependency-order-does-not-depend-on-the-input-order
  (let [d {:file "kimun/d.clj" :text "(ns d (:require [b :refer [mine]] [a :refer [isa]]))\n(mine k l)\n(isa k l)"}
        expected (by-file (declare/read-project [a-file c-file b-file d]))]
    (doseq [order [[d b-file c-file a-file] [b-file d a-file c-file] [c-file d a-file b-file]]]
      (is (= expected (by-file (declare/read-project order))) (str (mapv :file order))))
    (is (= ["b/mine" "a/isa"] (mapv :r (:propositions (get expected "kimun/d.clj")))))))

(deftest a-required-context-is-evaluated-once
  (let [calls (atom [])
        real @#'declare/evaluate-file]
    (with-redefs [declare/evaluate-file (fn [host text file info imports] (swap! calls conj file) (real host text file info imports))]
      (declare/read-project [b-file a-file c-file]))
    (is (= 1 (count (filter #{"kimun/a.clj"} @calls))) "a is required by b and by c")
    (is (= 3 (count @calls)))))

(deftest only-relations-are-shared
  (testing "the def of another context is not a relation to refer"
    (let [f (failure [a-file {:file "kimun/b.clj" :text "(ns b (:require [a :refer [z]]))"}])]
      (is (= [:declare/unknown-relation "kimun/b.clj" 1] ((juxt :type :file :line) f)))))
  (testing "nor does it resolve through an alias"
    (let [f (failure [a-file {:file "kimun/b.clj" :text "(ns b (:require [a :as a]))\n(a/z x y)"}])]
      (is (= [:declare/unresolved "kimun/b.clj" 2] ((juxt :type :file :line) f)))))
  (testing "a relation the context only imported is not its own"
    (let [f (failure [a-file c-file {:file "kimun/b.clj" :text "(ns b (:require [c :refer [anchors]]))"}])]
      (is (= :declare/unknown-relation (:type f))))))

(deftest every-error-of-a-set-names-file-and-line
  (testing "unknown context, at the line of the libspec"
    (let [f (failure [a-file {:file "kimun/b.clj" :text "(ns b\n  (:require [a]\n            [nope :as n]))"}])]
      (is (= [:declare/unknown-context "kimun/b.clj" 3] ((juxt :type :file :line) f)))
      (is (re-find #"^kimun/b\.clj:3: " (:message f)))))
  (testing "unknown relation, at the line of the libspec"
    (let [f (failure [a-file {:file "kimun/b.clj" :text "(ns b\n  (:require\n            [a :refer [isa nada]]))"}])]
      (is (= [:declare/unknown-relation "kimun/b.clj" 3] ((juxt :type :file :line) f)))))
  (testing "a cycle, at the ns of the file where it is detected"
    (let [f (failure [{:file "kimun/x.clj" :text "\n(ns x (:require y))"} {:file "kimun/y.clj" :text "(ns y (:require [z]))"} {:file "kimun/z.clj" :text "(ns z (:require x))"}])]
      (is (= [:declare/require-cycle "kimun/x.clj" 2 ["x" "y" "z" "x"]] ((juxt :type :file :line :cycle) f))))
    (is (= :declare/require-cycle (:type (failure [{:file "s.clj" :text "(ns s (:require s))"}]))) "a context requiring itself"))
  (testing "two files with the same context, at the second"
    (let [f (failure [{:file "kimun/p.clj" :text "(ns same)"} {:file "kimun/q.clj" :text "\n\n(ns same)"}])]
      (is (= [:declare/duplicate-context "kimun/q.clj" 3 "kimun/p.clj"] ((juxt :type :file :line :other) f)))))
  (testing "every other form of requiring is an ns-clauses error"
    (doseq [t ["(ns b (:require [\"x.js\" :as x]))" "(ns b (:require [a :refer :all]))" "(ns b (:require [a :refer [isa] :rename {isa i}]))"
               "(ns b (:require [a :as]))" "(ns b (:require (a b)))" "(ns b (:require a.b/c))" "(ns b (:use a))" "(ns b (:import java.io.File))"
               "(ns b (:refer-clojure :only []))"]]
      (let [f (failure [a-file {:file "kimun/b.clj" :text (str "\n" t)}])]
        (is (= [:declare/ns-clauses "kimun/b.clj" 2] ((juxt :type :file :line) f)) t)))))

(deftest evaluation-stays-pinned-to-its-own-namespace
  (let [in-other (binding [*ns* (create-ns 'some.other.ns)] (declare/read-project [b-file a-file c-file]))]
    (is (= (declare/read-project [b-file a-file c-file]) in-other))
    (is (= 4 (count (:propositions (first in-other)))))))
