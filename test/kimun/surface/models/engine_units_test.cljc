(ns kimun.surface.models.engine-units-test
  "Direct unit tests of three engine behaviours the differential corpus covers
   only in bulk. (1) The `markdown` marker trait: in sldb v1 it is inert (no
   code reads it), so a `⸢rev,markdown•f⸥` marker behaves exactly as `⸢rev•f⸥`
   in both directions. (2) PyYAML's width-80 folding of plain scalars, seen
   through `render/render` of a `dict` field; every expected value is the
   literal output of `yaml.dump(v, allow_unicode=True, sort_keys=False)`, the
   call v1 makes. (3) The `:exclude true` carrier: extraction lifts it into
   declared fields without a value of their own, rendering rebuilds it from
   the declared fields without a marker of their own."
  (:require [clojure.test :refer [deftest is testing]]
            [kimun.surface.models.descriptor :as descriptor]
            [kimun.surface.models.extract :as extract]
            [kimun.surface.models.render :as render]))

;; ---------------------------------------------------------------- markdown

(def ^:private notes-doc
  {:model/name "NotesDoc" :model/version 1
   :fields [{:name "title" :type :string :required true}
            {:name "notes" :type :string :required false :default ""}]
   :template "# ⸢rev•title⸥\n\n## Notes\n\n⸢rev,markdown•notes⸥"})

(def ^:private plain-notes-doc
  (assoc notes-doc :template "# ⸢rev•title⸥\n\n## Notes\n\n⸢rev•notes⸥"))

(def ^:private inline-doc
  (assoc notes-doc :template "# ⸢rev•title⸥\n\nNote: ⸢rev,markdown•notes⸥"))

(def ^:private rich-notes
  "Some **bold** and [a link](http://x.y) and `code`.\n\n- one\n- two\n\nSecond para\nwith break.")

(deftest markdown-trait-section-body-keeps-raw-markdown
  (testing "v1 captures the raw slice of the section, whatever the trait: inline formatting, lists and line breaks survive verbatim"
    (let [md (str "# Hi\n\n## Notes\n\n" rich-notes "\n")]
      (is (= {"title" "Hi" "notes" rich-notes} (extract/extract notes-doc md)))
      (is (= (extract/extract plain-notes-doc md) (extract/extract notes-doc md))))))

(deftest markdown-trait-render-is-verbatim
  (testing "v1 renders the string as is: no escaping, no reflow"
    (let [notes "Some **bold**\n\n- a\n- b\n\n[l](u)"]
      (is (= "# Hi\n\n## Notes\n\nSome **bold**\n\n- a\n- b\n\n[l](u)"
             (render/render notes-doc {"title" "Hi" "notes" notes})))
      (is (= (render/render plain-notes-doc {"title" "Hi" "notes" notes})
             (render/render notes-doc {"title" "Hi" "notes" notes}))))))

(deftest markdown-trait-inline-marker
  (testing "an inline marker captures one line of inline markdown and writes it back unchanged"
    (is (= {"title" "Hi" "notes" "**bold** [l](http://x) end"}
           (extract/extract inline-doc "# Hi\n\nNote: **bold** [l](http://x) end\n")))
    (is (= "# H\n\nNote: **b** [l](u)"
           (render/render inline-doc {"title" "H" "notes" "**b** [l](u)"})))))

(deftest markdown-trait-roundtrip
  (testing "extract then render reproduces the document"
    (let [md (str "# Hi\n\n## Notes\n\n" rich-notes)]
      (is (= md (render/render notes-doc (extract/extract notes-doc md)))))))

;; ----------------------------------------------------------- YAML folding

(def ^:private dict-doc
  {:model/name "DictDoc" :model/version 1
   :fields [{:name "data" :type :yaml :required true}]
   :template "⸢rev,dict•data⸥"})

(def ^:private words
  "alpha beta gamma delta epsilon zeta eta theta iota kappa lambda mu nu xi omicron pi rho sigma tau")

(defn- dump-of [v] (render/render dict-doc {"data" v}))

(deftest fold-leaves-short-and-unbreakable-values-alone
  (is (= "k: short words here" (dump-of {"k" "short words here"})))
  (testing "no space to break at: the line stays whole"
    (let [x (apply str (repeat 100 "x"))]
      (is (= (str "k: " x) (dump-of {"k" x})))))
  (testing "a break is taken at the first space reached past column 80"
    (let [y (apply str (repeat 85 "y"))]
      (is (= (str "k: " y "\n  z") (dump-of {"k" (str y " z")}))))
    (let [a (apply str (repeat 70 "a"))]
      (is (= (str "k: " a " bbbbbbb\n  cc") (dump-of {"k" (str a " bbbbbbb cc")}))))))

(deftest fold-long-plain-scalar
  (is (= (str "k: alpha beta gamma delta epsilon zeta eta theta iota kappa lambda mu nu xi omicron\n"
              "  pi rho sigma tau")
         (dump-of {"k" words})))
  (testing "several continuation lines"
    (is (= (str "k: alpha beta gamma delta epsilon zeta eta theta iota kappa lambda mu nu xi omicron\n"
                "  pi rho sigma tau alpha beta gamma delta epsilon zeta eta theta iota kappa lambda\n"
                "  mu nu xi omicron pi rho sigma tau")
           (dump-of {"k" (str words " " words)}))))
  (testing "a scalar that merely starts like a number or contains a double quote stays plain"
    (is (= (str "k: 123 alpha beta gamma delta epsilon zeta eta theta iota kappa lambda mu nu xi omicron\n"
                "  pi rho sigma tau")
           (dump-of {"k" (str "123 " words)})))
    (is (= (str "k: alpha beta gamma delta epsilon zeta eta theta iota kappa lambda mu nu xi omicron\n"
                "  pi rho sigma tau \"q\"")
           (dump-of {"k" (str words " \"q\"")})))))

(deftest fold-quoted-scalar-folds-too
  (testing "a value PyYAML single-quotes (it contains `: `) folds inside the quotes"
    (is (= (str "k: 'alpha beta gamma delta epsilon zeta eta theta iota kappa lambda mu nu xi omicron\n"
                "  pi rho sigma tau: colon'")
           (dump-of {"k" (str words ": colon")})))))

(deftest fold-list-items-and-nesting
  (testing "block list item: continuation indented two past the dash"
    (is (= (str "k:\n- alpha beta gamma delta epsilon zeta eta theta iota kappa lambda mu nu xi omicron\n"
                "  pi rho sigma tau\n- short")
           (dump-of {"k" [words "short"]}))))
  (testing "nested mapping: continuation two past the key"
    (is (= (str "a:\n  b: alpha beta gamma delta epsilon zeta eta theta iota kappa lambda mu nu xi omicron\n"
                "    pi rho sigma tau")
           (dump-of {"a" {"b" words}}))))
  (testing "list inside a mapping inside a mapping"
    (is (= (str "a:\n  b:\n  - alpha beta gamma delta epsilon zeta eta theta iota kappa lambda mu nu xi omicron\n"
                "    pi rho sigma tau")
           (dump-of {"a" {"b" [words]}}))))
  (testing "a mapping that is a list item: continuation aligns past the key"
    (is (= (str "- name: alpha beta gamma delta epsilon zeta eta theta iota kappa lambda mu nu xi omicron\n"
                "    pi rho sigma tau\n  v: 1")
           (dump-of [{"name" words "v" 1}])))
    (is (= (str "a:\n  b:\n  - name: alpha beta gamma delta epsilon zeta eta theta iota kappa lambda mu nu xi\n"
                "      omicron pi rho sigma tau")
           (dump-of {"a" {"b" [{"name" words}]}})))
    (is (= (str "k:\n- a:\n  - name: alpha beta gamma delta epsilon zeta eta theta iota kappa lambda mu nu xi\n"
                "      omicron pi rho sigma tau\n    v: 1")
           (dump-of {"k" [{"a" [{"name" words "v" 1}]}]}))))
  (testing "a list that is a list item"
    (is (= (str "k:\n- - alpha beta gamma delta epsilon zeta eta theta iota kappa lambda mu nu xi omicron\n"
                "    pi rho sigma tau\n  - x")
           (dump-of {"k" [[words "x"]]})))))

;; ----------------------------------------------------------------- carrier

(def ^:private carrier-doc
  {:model/name "CarrierDoc" :model/version 1
   :fields [{:name "title" :type :string :required true}
            {:name "status" :type :string :required false :default "draft"}
            {:name "tags" :type [:list :string] :required false :default []}
            {:name "owner" :type :string :required false}
            {:name "meta" :type :yaml :required false :exclude true}]
   :template "---\n⸢rev,dict•meta⸥\n---\n\n# ⸢rev•title⸥"})

(deftest carrier-extract-lifts-front-matter-into-declared-fields
  (is (= {"title" "Hi" "status" "active" "tags" ["a" "b"] "owner" "ana"}
         (extract/extract carrier-doc "---\nstatus: active\nowner: ana\ntags:\n- a\n- b\nunknown: 1\n---\n\n# Hi\n"))
      "undeclared keys are dropped, the carrier is never in the payload")
  (is (= {"title" "Hi" "status" "draft" "tags" [] "owner" "ana"}
         (extract/extract carrier-doc "---\nowner: ana\n---\n\n# Hi\n"))
      "declared fields the front matter lacks take their default"))

(deftest carrier-finalize-own-value-wins
  (is (= {"title" "T" "status" "x" "tags" [] "owner" "own"}
         (descriptor/finalize carrier-doc {"title" "T" "meta" {"status" "x" "owner" "o"} "owner" "own"}))
      "a field with a value of its own is not overridden by the carrier")
  (is (= {"title" "T" "status" "x" "tags" ["q"]}
         (descriptor/finalize carrier-doc {"title" "T" "meta" {"status" "x" "tags" ["q"]}})))
  (testing "a carrier that is not a mapping lifts nothing and is dropped"
    (is (= {"title" "T" "status" "draft" "tags" []}
           (descriptor/finalize carrier-doc {"title" "T" "meta" "notamap"})))))

(deftest carrier-render-collects-fields-without-a-marker
  (is (= "---\nstatus: active\ntags:\n- a\n- b\nowner: ana\n---\n\n# T"
         (render/render carrier-doc {"title" "T" "status" "active" "owner" "ana" "tags" ["a" "b"]})))
  (testing "defaults flow into the carrier; an absent optional field is left out"
    (is (= "---\nstatus: draft\ntags: []\n---\n\n# T" (render/render carrier-doc {"title" "T"}))))
  (testing "a carrier value in the payload is not rendered as such, its lifted keys are"
    (is (= "---\nstatus: draft\ntags: []\nowner: ana\n---\n\n# T"
           (render/render carrier-doc {"title" "T" "meta" {"owner" "ignored"} "owner" "ana"}))))
  (testing "a declared field with a marker of its own is not repeated in the carrier"
    (let [d (assoc carrier-doc :template (str (:template carrier-doc) "\n\nOwner: ⸢rev•owner⸥"))]
      (is (= "---\nstatus: s\ntags: []\n---\n\n# T\n\nOwner: ana"
             (render/render d {"title" "T" "status" "s" "owner" "ana"})))
      (is (= {"title" "Hi" "status" "s" "tags" [] "owner" "body"}
             (extract/extract d "---\nstatus: s\nowner: fm\n---\n\n# Hi\n\nOwner: body\n"))
          "on extraction the body marker's value beats the front matter's"))))

;; ---------------------------------------------------------------- dates

;; YAML timestamps in frontmatter. SnakeYAML parses a date and a datetime to
;; the same java.util.Date, so extract quotes date-only scalars first. v1
;; (PyYAML) returns: a date as "2026-06-14", a datetime as ISO with `T`, and
;; that includes a datetime at exact midnight ("2026-06-14T00:00:00"), which
;; must not be collapsed to a date.
(def ^:private dates-doc
  {:model/name "DatesDoc" :model/version 1
   :fields [{:name "midnight" :type :string :required true}
            {:name "later" :type :string :required true}
            {:name "day" :type :string :required true}]
   :template "---\nmidnight: ⸢rev•midnight⸥\nlater: ⸢rev•later⸥\nday: ⸢rev•day⸥\n---\n"})

(deftest yaml-dates-roundtrip-as-v1
  (let [md "---\nmidnight: 2026-06-14T00:00:00\nlater: 2026-06-14T01:02:03\nday: 2026-06-14\n---\n"]
    (is (= {"midnight" "2026-06-14T00:00:00" "later" "2026-06-14T01:02:03" "day" "2026-06-14"}
           (extract/extract dates-doc md)))))

;; quote-dates must never rewrite text it should not: block scalar bodies,
;; already-quoted values, multi-line plain scalars. A trailing comment and
;; flow collections: PyYAML reads the date there, so v1 returns "2026-06-14".
(def ^:private free-doc
  {:model/name "FreeDoc" :model/version 1
   :fields (mapv (fn [n] {:name n :type :any :required false}) ["a" "b" "c" "d"])
   :template "---\na: ⸢optrev•a⸥\nb: ⸢optrev•b⸥\nc: ⸢optrev•c⸥\nd: ⸢optrev•d⸥\n---\n"})

(deftest yaml-date-quoting-leaves-other-text-alone
  (let [ex (fn [y] (extract/extract free-doc (str "---\n" y "\n---\n")))]
    (testing "block scalar bodies are verbatim"
      (is (= {"a" "x\nd: 2026-06-14\n- 2026-06-14\n" "b" "2026-06-14T00:00:00"}
             (select-keys (ex "a: |\n  x\n  d: 2026-06-14\n  - 2026-06-14\nb: 2026-06-14T00:00:00") ["a" "b"])))
      (is (= "x d: 2026-06-14" (get (ex "a: >\n  x\n  d: 2026-06-14") "a"))))
    (testing "quoted, commented, multi-line plain"
      (is (= {"a" "2026-06-14" "b" "2026-06-14" "c" "2026-06-14" "d" "foo 2026-06-14"}
             (ex "a: \"2026-06-14\"\nb: '2026-06-14'\nc: 2026-06-14 # note\nd: foo\n  2026-06-14"))))
    (testing "flow collections"
      (is (= {"a" ["2026-06-14" "x"] "b" {"d" "2026-06-14"}}
             (select-keys (ex "a: [2026-06-14, x]\nb: {d: 2026-06-14}") ["a" "b"]))))
    (testing "a bracket in ordinary text is not a flow collection: the text comes back as written"
      (is (= {"a" "Release [2026-06-14]" "b" "notes [2026-06-14, x]" "c" "a {d: 2026-06-14}"}
             (ex "a: Release [2026-06-14]\nb: \"notes [2026-06-14, x]\"\nc: 'a {d: 2026-06-14}'"))))
    (testing "inside a flow collection, quoted strings and the trailing comment are untouched"
      (is (= {"a" ["x, 2026-06-14" "2026-06-15"] "b" {"s" "it's, 2026-06-14" "d" "2026-06-14"} "c" ["a 2026-06-14" "2026-06-14T00:00:00"]}
             (ex "a: [\"x, 2026-06-14\", 2026-06-15] # c, 2026-01-01\nb: {s: 'it''s, 2026-06-14', d: 2026-06-14}\nc: [a 2026-06-14, 2026-06-14T00:00:00]"))))))
