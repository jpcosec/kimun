(ns kimun.surface.links-test
  "Ports of sldb v1 `tests/test_links.py` (link-level assertions over an
   in-memory lookup), plus the behaviours of `sldb.links` the v1 suite leaves
   implicit: grammar edges, cycles, repeats and unresolved transclusions."
  (:require [clojure.test :refer [deftest is testing]]
            [kimun.surface.links :as links]
            [kimun.surface.links.parser :as parser]
            [kimun.surface.links.recovery :as recovery]))

(defn- dir [path] (subs path 0 (inc (.lastIndexOf ^String path "/"))))

(defn- world
  "`{:lookup :content}` over `files` (path -> text) and `tracked` (name -> path):
   a tracked name is a `:store` candidate, a file beside the current document
   is a `:path` candidate."
  ([files] (world files {}))
  ([files tracked]
   {:content files
    :lookup (fn [{:keys [target from]}]
              (let [rel (str (dir from) target)]
                (cond-> []
                  (contains? tracked target) (conj {:path (tracked target) :source :store})
                  (contains? files rel) (conj {:path rel :source :path}))))}))

(def note-world
  (world {"/p/concept.md" "# Concept\n\nBackground info.\n"
          "/p/constraints.md" "# Constraints\n\nAlways validate first.\n"
          "/p/root.md" "# Root\n\nSee [[concept]] for background.\n\n![[constraints]]\n"}
         {"concept" "/p/concept.md" "constraints" "/p/constraints.md"}))

(deftest recover-and-compose-links
  (testing "ports test_links.py::test_recover_and_compose_links (link-level)"
    (let [w note-world
          rec (links/recover-links "/p/root.md" (assoc w :depth 1))
          comp (links/compose-document "/p/root.md" w)]
      (is (= "root" (:root rec)))
      (is (= ["concept" "constraints"] (mapv :target (:links rec))))
      (is (= [:link :transclusion] (mapv :kind (:links rec))))
      (is (true? (:resolved (first (:links rec)))))
      (is (= [] (:unresolved rec)))
      (is (re-find #"See \[\[concept\]\] for background\." (:markdown comp)))
      (is (re-find #"# Constraints" (:markdown comp)))
      (is (re-find #"Always validate first\." (:markdown comp)))
      (is (= ["constraints"] (:transclusions comp))))))

(deftest recover-and-compose-accept-tracked-names
  (testing "ports test_links.py::test_docs_recover_and_compose_accept_tracked_doc_names_and_yaml
            (link-level: tracked names resolve through the store tier; the CLI
            name lookup and YAML output are deferred)"
    (let [rec (links/recover-links "/p/root.md" note-world)
          comp (links/compose-document "/p/root.md" note-world)]
      (is (= #{"concept" "constraints"} (set (map :target (:links rec)))))
      (is (= #{:store} (set (map :source (:links rec)))))
      (is (= "root" (:root comp)))
      (is (contains? comp :transclusions)))))

(deftest parse-predicate-links
  (testing "ports test_links.py::test_parse_predicate_links"
    (let [md "\n    This task [implements:: [[spec-001]]].\n    Another [is_solved_by:: [[solution-002]]].\n    [custom_predicate:: [[some-doc]]].\n    "
          ls (links/parse-links md {"implements" "HOW" "is_solved_by" "HOW"})]
      (is (= 3 (count ls)))
      (is (= ["spec-001" "implements" "HOW" :predicate-link]
             ((juxt :target :predicate :w5h1-type :kind) (ls 0))))
      (is (= ["solution-002" "is_solved_by" "HOW"]
             ((juxt :target :predicate :w5h1-type) (ls 1))))
      (is (= ["some-doc" "custom_predicate" "CUSTOM"]
             ((juxt :target :predicate :w5h1-type) (ls 2)))))))

(deftest parse-links-uses-injected-predicate-axes
  (testing "ports test_links.py::test_parse_links_uses_injected_predicate_axes"
    (let [ls (links/parse-links "[depends_on:: [[target]]] [unknown:: [[other]]]"
                                {"depends_on" "DEPENDENCY"})]
      (is (= ["depends_on" "DEPENDENCY"] ((juxt :predicate :w5h1-type) (ls 0))))
      (is (= ["unknown" "CUSTOM"] ((juxt :predicate :w5h1-type) (ls 1)))))))

(deftest parse-mixed-links-with-predicates
  (testing "ports test_links.py::test_parse_mixed_links_with_predicates"
    (let [md "\n    See [[background]] for context.\n\n    This solution [implements:: [[spec-v2]]] and\n    [grounded_by:: [[research-paper]]].\n\n    Also check [[related-work]].\n    "
          ls (links/parse-links md {"implements" "HOW" "grounded_by" "PROVENANCE"})
          preds (filterv #(= :predicate-link (:kind %)) ls)]
      (is (= 4 (count ls)))
      (is (contains? (set (map :kind ls)) :link))
      (is (contains? (set (map :kind ls)) :predicate-link))
      (is (= 2 (count preds)))
      (is (= ["implements" "grounded_by"] (mapv :predicate preds))))))

(deftest predicate-links-with-transclusion-bang
  (testing "ports test_links.py::test_predicate_links_with_transclusion_bang"
    (let [ls (links/parse-links "[implements:: ![[embedded-spec]]]" {"implements" "HOW"})]
      (is (= 1 (count ls)))
      (is (= ["embedded-spec" "implements" :transclusion]
             ((juxt :target :predicate :kind) (first ls)))))))

(deftest resolve-predicate-link
  (testing "ports test_links.py::test_resolve_predicate_link (relative-path tier)"
    (let [w (world {"/t/docs/spec-001.md" "# Spec 001\n"})
          link (first (links/parse-links "[implements:: [[docs/spec-001.md]]]" {"implements" "HOW"}))
          r (links/resolve-link (:lookup w) link "/t/current.md")]
      (is (true? (:resolved r)))
      (is (= :predicate-link (:kind r)))
      (is (= "implements" (:predicate r)))
      (is (= "HOW" (:w5h1-type r)))
      (is (= :path (:source r)))
      (is (= "/t/docs/spec-001.md" (:path r))))))

(deftest recover-predicate-links
  (testing "ports test_links.py::test_recover_predicate_links_cli (link-level)"
    (let [w (world {"/t/spec.md" "# Spec\n" "/t/research.md" "# Research\n"
                    "/t/task.md" "# Task\n\n[implements:: [[spec]]]\n[grounded_by:: [[research]]]"}
                   {"spec" "/t/spec.md" "research" "/t/research.md"})
          rec (links/recover-links "/t/task.md"
                                   (assoc w :predicate-types {"implements" "HOW" "grounded_by" "PROVENANCE"}))
          preds (filterv #(= :predicate-link (:kind %)) (:links rec))
          by (into {} (map (juxt :predicate :w5h1-type)) preds)]
      (is (= 2 (count preds)))
      (is (= #{"implements" "grounded_by"} (set (keys by))))
      (is (= "HOW" (by "implements")))
      (is (= "PROVENANCE" (by "grounded_by"))))))

(deftest parse-grammar-edges
  (testing "behaviour of sldb.links.parser run against v1 (no v1 test): offsets, no code awareness, no aliases or fragments"
    (is (= [{:raw "[[a]]" :target "a" :kind :link :start 1 :end 6 :predicate nil :w5h1-type nil}]
           (links/parse-links "`[[a]]`")) "code spans are not skipped")
    (is (= ["b"] (mapv :target (links/parse-links "```\n[[b]]\n```"))) "fenced code is not skipped")
    (is (= [["a | b" :link 0 11] ["c#d" :transclusion 12 20] ["" :link 21 26]]
           (mapv (juxt :target :kind :start :end) (links/parse-links "[[ a | b ]] ![[c#d]] [[ ]]")))
        "alias and fragment stay part of the target; empty target allowed")
    (is (= ["[x"] (mapv :target (links/parse-links "[[[x]]]"))))
    (is (= ["a\nb"] (mapv :target (links/parse-links "[[a\nb]]"))))
    (is (= ["a"] (mapv :target (links/parse-links "x [[a]]]"))))
    (is (= [] (links/parse-links "no links [here]")))
    (testing "predicate links first, plain links after, no double report"
      (let [ls (links/parse-links "[p:: [[t]]] [[t]] ![[u]]" {"p" "HOW"})]
        (is (= [[:predicate-link 0 11] [:link 12 17] [:transclusion 18 24]]
               (mapv (juxt :kind :start :end) ls)))))
    (testing "matches finds repeated identical text at its own offsets"
      (is (= [[0 5] [6 11]] (mapv (juxt :start :end) (parser/matches parser/link-pattern "[[a]] [[a]]")))))))

(deftest resolution-outcomes
  (testing "missing, ambiguous and tier precedence (lookup contract; v1 has no ambiguity)"
    (let [link (first (links/parse-links "[[t]]"))
          res #(links/resolve-link (constantly %) link "/p/d.md")]
      (is (= {:resolved false :reason :missing :candidates []}
             (select-keys (res []) [:resolved :reason :candidates])))
      (is (= [false :ambiguous ["/a" "/b"]]
             ((juxt :resolved :reason :candidates)
              (res [{:path "/a" :source :store} {:path "/b" :source :store}]))))
      (is (= [true "/s" :store]
             ((juxt :resolved :path :source)
              (res [{:path "/x" :source :path} {:path "/s" :source :store}])))
          "a store hit beats a path hit")
      (is (true? (:resolved (res [{:path "/a" :source :path} {:path "/a" :source :path}])))
          "the same path twice is not ambiguity"))))

(def cyc
  (world {"/lk/a.md" "# A\n\n![[b.md]]\n\n![[c.md]]\n"
          "/lk/b.md" "# B\n\n![[c.md]]\n"
          "/lk/c.md" "# C\n\ntext ![[a.md]] [[x]]\n"}))

(deftest compose-cycles-and-repeats
  (testing "golden output of sldb.links.composer on a cycle with a repeat (run against v1)"
    (is (= {:root "a" :path "/lk/a.md"
            :markdown "# A\n\n# B\n\n# C\n\ntext # A\n\n![[b.md]]\n\n![[c.md]] [[x]]\n\n# C\n\ntext ![[a.md]] [[x]]\n"
            :transclusions ["a.md" "b.md" "c.md"]
            :unresolved []}
           (links/compose-document "/lk/a.md" cyc)))))

(deftest compose-unresolved
  (testing "unresolved transclusions stay in place and are reported; plain links untouched (run against v1)"
    (let [w (world {"/lk/d.md" "x ![[nope]] ![[b]] [[b]]\n"})]
      (is (= {:root "d" :path "/lk/d.md" :markdown "x ![[nope]] ![[b]] [[b]]\n"
              :transclusions [] :unresolved ["b" "nope"]}
             (links/compose-document "/lk/d.md" w))))))

(deftest recover-depth-follows-resolved-links
  (testing "recover with depth 3 on the cycle: nested links appended, each document read once, deduped (run against v1)"
    (let [rec (links/recover-links "/lk/a.md" (assoc cyc :depth 3))]
      (is (= [["b.md" :transclusion true "/lk/b.md" :path]
              ["c.md" :transclusion true "/lk/c.md" :path]
              ["a.md" :transclusion true "/lk/a.md" :path]
              ["x" :link false nil nil]]
             (mapv (juxt :target :kind :resolved :path :source) (:links rec))))
      (is (= ["x"] (:unresolved rec)))
      (is (= [:missing] (map :reason (filter (complement :resolved) (:links rec))))))
    (is (= ["b.md" "c.md"] (mapv :target (:links (links/recover-links "/lk/a.md" cyc))))
        "depth 1 does not follow")
    (is (= [] (:links (links/recover-links "/lk/a.md" (assoc cyc :depth 0)))))
    (is (= "c" (recovery/stem "/lk/c.md")))))
