(ns kimun.surface.index.where-test
  "Port of sldb v1's `--where` tests (test_where_empty_and_errors.py and the
   where-relevant parts of test_queries_expanded.py) onto in-memory `doc`
   maps, plus the kimun additions: the EDN vector form, `and`/`or`/`not` and
   the typed `:query/bad-where` error with its position."
  (:require [clojure.test :refer [deftest is testing]]
            [kimun.surface.index.where :as where]
            [sldb.kernel.err :as err]))

(defn- doc
  ([payload] (doc "d" "MemoDoc" [] payload))
  ([name model bases payload] {:name name :model model :bases bases :payload payload :tags []}))

(defn- hit? [expr d] (where/matches? (where/parse expr) d))

(defn- bad-where
  "The ex-data of the `:query/bad-where` error `expr` raises, or nil."
  [expr]
  (err/rescue ex-data (where/parse expr) nil))

(deftest empty-literal-and-absent-field-semantics
  "Ports test_empty_literal_and_absent_field_semantics (8 parameter rows)."
  (doseq [[pred payload expected]
          [["status = \"\"" {"status" ""} true]
           ["status = \"\"" {"status" "open"} false]
           ["status = \"\"" {} false]
           ["status != \"\"" {"status" ""} false]
           ["status != \"\"" {"status" "open"} true]
           ["status != \"\"" {} false]
           ["has(status)" {"status" ""} false]
           ["status = \"open\"" {"status" "open"} true]]]
    (is (= expected (hit? pred (doc payload))) (pr-str pred payload))))

(deftest unparseable-predicate-raises-from-the-per-document-filter
  "Ports test_unparseable_predicate_raises_from_the_per_document_filter."
  (let [e (bad-where "status ===")]
    (is (= :query/bad-where (:type e)))
    (is (= "status ===" (:expression e)) "the message names the predicate")
    (is (integer? (:position e)))
    (is (re-find #"status ===" (:message (err/rescue (fn [x] {:message (ex-message x)}) (where/parse "status ===")))))))

(def ^:private quoted "doesn't exist \" here")
(def ^:private escaped "title = \"doesn't exist \\\" here\"")

(deftest backslash-escapes-in-string-literals
  "Ports test_backslash_escapes_in_string_literals (8 parameter rows)."
  (doseq [[pred payload expected]
          [[escaped {"title" quoted} true]
           [escaped {"title" "doesn't exist here"} false]
           [escaped {"title" (str quoted " extra")} false]
           [escaped {"status" quoted} false]
           ["\"say \\\"hi\\\"\" in title" {"title" "say \"hi\" aloud"} true]
           ["\"say \\\"hi\\\"\" in title" {"title" "say hi aloud"} false]
           ["title ~ \"exist \\\" here\"" {"title" quoted} true]
           ["title ~ \"exist \\\" here\"" {"title" "exist here"} false]]]
    (is (= expected (hit? pred (doc payload))) (pr-str pred payload))))

(deftest an-unterminated-literal-is-still-an-error
  "Ports test_an_unterminated_literal_is_still_an_error (4 parameter rows)."
  (doseq [pred ["title = \"unterminated"
                "title = \"escaped quote eats the closing \\\""
                "\"unterminated in title"
                "title ~ \"unterminated"]]
    (let [e (bad-where pred)]
      (is (= :query/bad-where (:type e)) pred)
      (is (= (.indexOf ^String pred "\"") (:position e)) "points at the opening quote"))))

(deftest find-empty-literal-matches-only-the-present-empty-document
  "Ports test_find_empty_literal_matches_only_the_present_empty_document
   (the filter over the two documents; the store and `find` are deferred)."
  (let [docs [(doc "empty" "MemoDoc" [] {"title" "Empty" "status" ""})
              (doc "open" "MemoDoc" [] {"title" "Open" "status" "open"})]]
    (is (= ["empty"] (mapv :name (filter #(hit? "status = \"\"" %) docs))))))

(deftest find-matches-the-escaped-title-and-only-it
  "Ports test_find_matches_the_escaped_title_and_only_it (filter only)."
  (let [docs [(doc "empty" "MemoDoc" [] {"title" "Empty" "status" ""})
              (doc "open" "MemoDoc" [] {"title" "Open" "status" "open"})
              (doc "quoted" "MemoDoc" [] {"title" quoted "status" "open"})]]
    (is (= ["quoted"] (mapv :name (filter #(hit? escaped %) docs))))))

(deftest unparseable-predicate-raises-from-the-engine
  "Ports test_unparseable_predicate_raises_from_the_engine (the raise only;
   `find_structural` over a store is deferred)."
  (let [e (bad-where "bogus predicate")]
    (is (= :query/bad-where (:type e)))
    (is (= "bogus predicate" (:expression e)))
    (is (= 6 (:position e)) "'bogus' is a field name; the offending token starts after it")))

(deftest cli-find-error-names-the-predicate
  "Ports the parse-side of test_cli_find_prints_the_error_and_exits_non_zero:
   the error message names the predicate. Exit code and empty stdout are CLI
   concerns (deferred)."
  (is (re-find #"bogus predicate"
               (err/rescue ex-message (where/parse "bogus predicate")))))

(deftest find-structural-invalid-address-predicate
  "Ports the predicate of test_find_structural_invalid_address: v1 never
   evaluated `title='x'` (invalid address returned []); here it is simply
   unparseable (single quotes are not literals). The address is deferred."
  (is (= :query/bad-where (:type (bad-where "title='x'")))))

(deftest model-inheritance-query
  "Ports the family check behind test_model_inheritance_query: `st.{BaseDoc+}`
   is `model <= BaseDoc`, `st.{BaseDoc}` is the exact model. Listing by
   address is deferred."
  (let [doc1 (doc "doc1" "BaseDoc" ["BaseDoc"] {"title" "Base"})
        doc2 (doc "doc2" "ChildDoc" ["ChildDoc" "BaseDoc"] {"title" "Child" "child_field" "x"})]
    (testing "family: the base and its subclass"
      (is (= ["doc1" "doc2"] (mapv :name (filter #(hit? "model <= BaseDoc" %) [doc1 doc2]))))
      (is (= ["doc1" "doc2"] (mapv :name (filter #(hit? "[:model<= BaseDoc]" %) [doc1 doc2])))))
    (testing "a subclass is not its parent's family"
      (is (= ["doc2"] (mapv :name (filter #(hit? "model <= ChildDoc" %) [doc1 doc2])))))
    (testing "bases need not include the model itself"
      (is (hit? "model<=ChildDoc" (doc "x" "ChildDoc" [] {}))))))

;; ------------------------------------------------------ kimun additions

(deftest v1-syntax-yields-the-closed-ast
  (is (= [:has "status"] (where/parse "has(status)")))
  (is (= [:in "open" "status"] (where/parse "  \"open\"  in  status ")))
  (is (= [:re "title" "^a.*"] (where/parse "title ~ \"^a.*\"")))
  (is (= [:model<= "Base"] (where/parse "model <= Base")))
  (is (= [:cmp :>= "n" 3] (where/parse "n >= 3")))
  (is (= [:cmp :<= "n" 2.5] (where/parse "n<=2.5")))
  (is (= [:cmp :!= "s" "x"] (where/parse "s != \"x\"")))
  (is (= [:cmp :<= "model" "x"] (where/parse "model <= \"x\"")) "model with a literal is a plain comparison"))

(deftest edn-form-and-combinators
  (is (= [:in "open" "status"] (where/parse "[:in \"open\" status]")))
  (is (= [:cmp := "n" 3] (where/parse "[:cmp = n 3]")))
  (is (= [:and [:has "a"] [:or [:has "b"] [:not [:re "c" "x"]]]]
         (where/parse "[:and [:has a] [:or [:has b], [:not [:re c \"x\"]]]]")))
  (let [ast (where/parse "[:and [:has a] [:not [:has b]]]")]
    (is (true? (where/matches? ast (doc {"a" 1}))))
    (is (false? (where/matches? ast (doc {"a" 1 "b" 2})))))
  (let [ast (where/parse "[:or [:has a] [:has b]]")]
    (is (false? (where/matches? ast (doc {}))))
    (is (true? (where/matches? ast (doc {"b" 1}))))))

(deftest bad-where-carries-the-position
  (doseq [[expr pos] [["" 0]
                      ["   " 0]
                      ["has(" 4]
                      ["has()" 4]
                      ["has(a) b" 7]
                      ["\"\" in f" 0]
                      ["a > 3" 2]
                      ["a = -1" 4]
                      ["a = b" 4]
                      ["a ~ \"(\"" 4]
                      ["[:has a] x" 9]
                      ["[:has a" 0]
                      ["[:bogus a]" 1]
                      ["[:has]" 0]
                      ["[:in a b]" 5]
                      ["[:cmp > a 1]" 6]
                      ["[:and]" 0]
                      ["[:not [:has a] [:has b]]" 0]
                      ["[:re a \"(\"]" 7]
                      ["[:has a \"unterminated]" 8]
                      ["[:has {a}]" 6]]]
    (let [e (bad-where expr)]
      (is (= :query/bad-where (:type e)) (pr-str expr))
      (is (= pos (:position e)) (pr-str expr))))
  (is (= :query/bad-where (:type (bad-where nil)))))

(deftest comparison-semantics
  (testing "numbers compare numerically across int and float"
    (is (hit? "n = 3" (doc {"n" 3.0})))
    (is (hit? "n >= 3" (doc {"n" 4})))
    (is (not (hit? "n <= 3" (doc {"n" 4})))))
  (testing "an absent field is != a number literal, never ordered"
    (is (hit? "n != 3" (doc {})))
    (is (not (hit? "n >= 3" (doc {}))))
    (is (not (hit? "n <= 3" (doc {"n" "x"})))))
  (testing "strings order lexicographically"
    (is (hit? "s >= \"b\"" (doc {"s" "c"})))
    (is (not (hit? "s <= \"b\"" (doc {"s" "c"}))))))

(deftest membership-regex-and-paths
  (testing "in: list element and substring"
    (is (hit? "\"a\" in tags" (doc {"tags" ["a" "b"]})))
    (is (not (hit? "\"c\" in tags" (doc {"tags" ["a" "b"]}))))
    (is (hit? "\"ell\" in t" (doc {"t" "hello"})))
    (is (not (hit? "\"a\" in t" (doc {"t" 5}))))
    (is (not (hit? "\"a\" in t" (doc {})))))
  (testing "regex searches (not full match); `doc` names the document name"
    (is (hit? "t ~ \"ell\"" (doc {"t" "hello"})))
    (is (not (hit? "t ~ \"^ell\"" (doc {"t" "hello"}))))
    (is (hit? "doc ~ \"^my-\"" (doc "my-doc" "M" [] {})))
    (is (hit? "t ~ \"^$\"" (doc {})) "an absent field is the empty string"))
  (testing "dotted paths reach maps and list items"
    (is (hit? "has(a.b)" (doc {"a" {"b" 1}})))
    (is (hit? "has(tasks.1.title)" (doc {"tasks" [{"title" "x"} {"title" "y"}]})))
    (is (hit? "[:cmp = tasks.1.title \"y\"]" (doc {"tasks" [{"title" "x"} {"title" "y"}]})))
    (is (not (hit? "has(a.c)" (doc {"a" {"b" 1}})))))
  (testing "has: nil and empty are absent, empty list is present"
    (is (not (hit? "has(a)" (doc {"a" nil}))))
    (is (hit? "has(a)" (doc {"a" []})))))
