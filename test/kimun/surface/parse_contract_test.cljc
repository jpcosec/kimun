(ns kimun.surface.parse-contract-test
  "The parse contract and its digest (K6): the components that are recorded,
   and that a comparison names exactly the component that moved."
  (:require [clojure.test :refer [deftest is testing]]
            [kimun.host.default :as host]
            [kimun.host.store-cache :as store-cache]
            [kimun.host.treesitter :as ts]
            [kimun.surface.markdown.ast :as ast]
            [kimun.surface.parse-contract :as pc]))

(def h host/host)

(defmacro with-treesitter
  "Runs `body` as if `KIMUN_PARSER=treesitter` were set and the pod answered."
  [& body]
  `(with-redefs [ts/available? (constantly true)
                 ts/version (constantly {:pod "pod-kimun-treesitter" :version "9.9.9" :tree_sitter "0.25"})]
     (binding [ast/*parser* "treesitter"] ~@body)))

(defn- contract [class] (pc/contract (pc/environment) class))
(defn- record [class] (pc/record h (pc/environment) class))
(defn- compared [recorded class]
  (select-keys (pc/compare-recorded h (pc/environment) recorded class) [:status :changed]))

(deftest the-contract-names-the-components-in-force
  (binding [ast/*parser* "native"]
    (is (= {:contract/version 1
            :parser ["kimun.surface.markdown/native" store-cache/parser-version]
            :grammar "builtin"
            :normalization "nfc"
            :extractor ["kimun.surface.models" "1"]
            :class "class-a"}
           (contract "class-a")))
    (is (= (set pc/components) (set (keys (contract "class-a")))))
    (is (re-matches #"sha-256:[0-9a-f]{64}" (pc/digest h (contract "class-a"))))
    (is (= (assoc (contract "class-a") :digest (pc/digest h (contract "class-a")))
           (record "class-a")))))

(deftest the-effective-parser-is-part-of-the-contract
  (is (= "native" (binding [ast/*parser* "native"] (ast/effective-parser))))
  (is (= ["treesitter" "native"]
         (with-redefs [ts/available? (constantly false)]
           (binding [ast/*parser* "treesitter"] [(ast/requested-parser) (ast/effective-parser)])))
      "the pod view is not in force when the pod is absent: the contract says what really parses")
  (with-treesitter
    (is (= "treesitter" (ast/effective-parser)))
    (is (= [["kimun.surface.markdown/treesitter" store-cache/parser-version]
            "pod-kimun-treesitter/9.9.9 tree-sitter/0.25"]
           ((juxt :parser :grammar) (contract "class-a"))))))

(deftest every-component-moves-the-digest
  (let [base (binding [ast/*parser* "native"] (contract "class-a"))
        d (pc/digest h base)]
    (is (= d (pc/digest h (into {} (reverse (seq base))))) "map order does not matter")
    (doseq [[k v] {:contract/version 2 :parser ["other" "1"] :grammar "g2" :normalization "nfd"
                   :extractor ["kimun.surface.models" "2"] :class "class-b"}]
      (is (not= d (pc/digest h (assoc base k v))) (str k)))))

(deftest a-comparison-names-exactly-what-moved
  (binding [ast/*parser* "native"]
    (let [recorded (record "class-a")]
      (is (= {:status :same :changed []} (compared recorded "class-a")))
      (is (= {:status :unrecorded :changed []} (compared nil "class-a"))
          "a document written before K6 is never reported as changed")
      (testing "only the class"
        (is (= {:status :changed :changed [:class]} (compared recorded "class-b"))))
      (testing "only the extractor: it is not reported as the parser"
        (with-redefs [pc/extractor ["kimun.surface.models" "2"]]
          (is (= {:status :changed :changed [:extractor]} (compared recorded "class-a")))))
      (testing "only the parser version"
        (with-redefs [store-cache/parser-version "2"]
          (is (= {:status :changed :changed [:parser]} (compared recorded "class-a")))))
      (testing "the alternate parser: parser and grammar, nothing else"
        (with-treesitter
          (is (= {:status :changed :changed [:parser :grammar]} (compared recorded "class-a")))))
      (testing "the class and the parser together"
        (with-redefs [store-cache/parser-version "2"]
          (is (= [:parser :class] (:changed (compared recorded "class-b"))))))
      (testing "the digests of both sides are reported"
        (let [r (pc/compare-recorded h (pc/environment) recorded "class-b")]
          (is (= (:digest recorded) (:recorded r)))
          (is (= (:digest (record "class-b")) (:current r))))))))

(deftest a-recorded-digest-is-verified-against-its-components
  (binding [ast/*parser* "native"]
    (let [recorded (record "class-a")]
      (is (true? (pc/consistent? h recorded)))
      (doseq [bad [(assoc recorded :grammar "tampered")
                   (assoc recorded :digest "sha-256:00")
                   (dissoc recorded :normalization)
                   (dissoc recorded :digest)
                   "sha-256:abc"]]
        (is (false? (pc/consistent? h bad)) (pr-str bad))
        (is (= {:status :inconsistent :changed []} (compared bad "class-a"))
            "evidence that does not verify is neither :same nor :changed")))))

(deftest an-unknown-grammar-is-never-recorded-or-compared
  (with-redefs [ts/available? (constantly true)
                ts/version (fn [] (throw (ex-info "pod died" {})))]
    (binding [ast/*parser* "treesitter"]
      (let [env (pc/environment)]
        (is (= {:parser "treesitter" :pod? true :grammar nil} env))
        (is (false? (pc/known? env)))
        (is (nil? (pc/contract env "class-a")))
        (is (= :docs/parser-unavailable
               (try (pc/record h env "class-a") (catch clojure.lang.ExceptionInfo e (:type (ex-data e))))))
        (let [native (binding [ast/*parser* "native"] (record "class-a"))]
          (is (= {:status :parser-unavailable :changed []} (compared native "class-a"))
              "the contract in force is not known: no change is claimed"))))))

(deftest a-document-recorded-under-tree-sitter-without-the-pod-is-parser-unavailable
  (let [recorded (with-treesitter (record "class-a"))]
    (with-redefs [ts/available? (constantly false)]
      (binding [ast/*parser* "native"]
        (is (= {:status :parser-unavailable :changed []} (compared recorded "class-a")))))
    (testing "with the pod at hand and the in-process parser chosen it is a plain change"
      (with-redefs [ts/available? (constantly true)]
        (binding [ast/*parser* "native"]
          (is (= {:status :changed :changed [:parser :grammar]} (compared recorded "class-a"))))))))
