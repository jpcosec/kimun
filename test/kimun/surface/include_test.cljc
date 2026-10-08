(ns kimun.surface.include-test
  "Tests of kimun.surface.include (docs/v2/13): parsing and validation, glob
   matching, the plan and every finding kind of the diff."
  (:require [babashka.fs :as fs]
            [clojure.test :refer [deftest is testing]]
            [kimun.surface.include :as inc]))

(defn- err-type [thunk]
  (try (thunk) nil (catch clojure.lang.ExceptionInfo e (:type (ex-data e)))))

(def ^:private cfg-text
  (str "{:include/version 1\n"
       " :store {:path \".kimun\" :git :ignore}\n"
       " :models [\"models/*.edn\"]\n"
       " :documents [{:glob \"docs/**/*.md\" :model \"Doc\"}\n"
       "             {:glob \"special/**/*.md\" :model \"Special\" :force true}]\n"
       " :force #{\"docs/forced.md\"}\n"
       " :exclude [\"docs/skip.md\"]}"))

(deftest parse-valid
  (let [cfg (inc/parse cfg-text)]
    (is (= 1 (:include/version cfg)))
    (is (= {:path ".kimun" :git :ignore} (:store cfg)))
    (is (= ["models/*.edn"] (:models cfg)))
    (is (= 2 (count (:documents cfg))))
    (is (= #{"docs/forced.md"} (:force cfg)))
    (is (= ["docs/skip.md"] (:exclude cfg)))))

(deftest parse-invalid
  (testing "a non-map, an unknown key, a bad version, a bad store, bad collections"
    (is (= :include/invalid (err-type #(inc/parse "[]"))))
    (is (= :include/invalid (err-type #(inc/parse "{:include/version 1 :nope 1}"))))
    (is (= :include/invalid (err-type #(inc/parse "{:include/version 2 :store {:path \".kimun\" :git :ignore} :models [] :documents []}"))))
    (is (= :include/invalid (err-type #(inc/parse "{:include/version 1 :store {:path \".kimun\"} :models [] :documents []}"))))
    (is (= :include/invalid (err-type #(inc/parse "{:include/version 1 :store {:path \".kimun\" :git :ignore} :models [1] :documents []}"))))
    (is (= :include/invalid (err-type #(inc/parse "{:include/version 1 :store {:path \".kimun\" :git :ignore} :models [] :documents [{:glob \"a/*.md\"}]}"))))
    (is (= :include/invalid (err-type #(inc/parse "{:include/version 1 :store {:path \".kimun\" :git :ignore} :models [] :documents [] :force [1]}"))))))

(deftest globs
  (testing "** spans separators (and **/ matches none), * and ? stay in one segment"
    (is (inc/glob-match? "desk/atoms/**/*.md" "desk/atoms/x.md"))
    (is (inc/glob-match? "desk/atoms/**/*.md" "desk/atoms/a/b/x.md"))
    (is (not (inc/glob-match? "desk/atoms/**/*.md" "desk/atoms/x.txt")))
    (is (inc/glob-match? "a/*.md" "a/x.md"))
    (is (not (inc/glob-match? "a/*.md" "a/b/x.md")))
    (is (inc/glob-match? "a/?.md" "a/x.md"))
    (is (not (inc/glob-match? "a/?.md" "a/xy.md")))
    (is (inc/glob-match? "**/x.md" "x.md"))
    (is (inc/glob-match? "**/x.md" "a/b/x.md"))
    (is (inc/glob-match? "desk/models/*.edn" "desk/models/AtomDoc.edn"))))

(deftest plan-over-a-tree
  (let [cfg (inc/parse cfg-text)
        files ["models/Base.edn" "models/Derived.edn" "docs/a.md" "docs/forced.md"
               "docs/skip.md" "special/s.md" "other/o.md"]
        p (inc/plan cfg files {:model-bases {"models/Derived.edn" #{"Base"}}})]
    (testing "models come base-first; documents follow the rules, first match wins"
      (is (= ["models/Base.edn" "models/Derived.edn"] (:models p)))
      (is (= [["a" "Doc" "docs/a.md"] ["forced" "Doc" "docs/forced.md"] ["s" "Special" "special/s.md"]]
             (map (juxt :name :model :path) (:documents p)))))
    (testing ":force true on a rule and a path in :force; :exclude claims nothing"
      (is (= {"docs/a.md" false "docs/forced.md" true "special/s.md" true}
             (into {} (map (fn [d] [(:path d) (:force d)])) (:documents p)))))))

(deftest plan-collision
  (let [cfg (inc/parse (str "{:include/version 1 :store {:path \".kimun\" :git :ignore}\n"
                            " :models [] :documents [{:glob \"docs/**/*.md\" :model \"Doc\"}\n"
                            "             {:glob \"special/**/*.md\" :model \"Doc\"}]}"))]
    (is (= :include/conflict (err-type #(inc/plan cfg ["docs/a.md" "special/a.md"]))))))

(deftest diff-findings
  (let [cfg (inc/parse cfg-text)
        p (inc/plan cfg ["models/A.edn" "docs/a.md" "docs/skip.md"])
        inputs {:tracked #{"docs/skip.md" "docs/ghost.md" ".kimun/store.edn" "other/x.md"}
                :files #{"other/x.md"}
                :store-documents #{"docs/only-store.md"}}
        {:keys [ok? findings]} (inc/diff cfg p inputs)]
    (is (false? ok?))
    (testing "every finding kind fires on the input crafted for it, excluded paths excepted"
      (is (= ["docs/a.md" "models/A.edn"] (:declared-not-tracked findings)))
      (is (= ["docs/ghost.md"] (:tracked-undeclared findings)))
      (is (not (some #{"docs/skip.md"} (:tracked-undeclared findings))))
      (is (= ["docs/a.md" "models/A.edn"] (:declared-missing findings)))
      (is (= ["docs/only-store.md"] (:store-undeclared findings)))
      (is (= ["docs/a.md"] (:declared-not-in-store findings)))
      (is (= [".kimun/store.edn"] (:store-tracked findings))))
    (testing "kinds limits the computation; a clean input (with an excluded tracked path) is :ok?"
      (let [limited (:findings (inc/diff cfg p inputs [:store-tracked]))]
        (is (= [".kimun/store.edn"] (:store-tracked limited)))
        (is (every? empty? (vals (dissoc limited :store-tracked)))))
      (is (true? (:ok? (inc/diff cfg p {:tracked #{"docs/a.md" "models/A.edn" "docs/skip.md"}
                                        :files #{"docs/a.md" "models/A.edn"}
                                        :store-documents #{"docs/a.md"}})))))))

(deftest this-repo-include-describes-its-world
  (testing "the checked-in kimun.include.edn covers exactly 17 models and 539 documents (4 desk documents moved to docs/archive on 2026-10-08)"
    (when (fs/exists? "kimun.include.edn")
      (let [cfg (inc/parse (slurp "kimun.include.edn"))
            files (->> (fs/glob "desk" "**")
                       (filter fs/regular-file?)
                       (map str)
                       (filter #(re-matches #".*\.(md|edn)" %))
                       (map inc/normalize-path))
            p (inc/plan cfg files)]
        (is (= 17 (count (:models p))))
        (is (= 539 (count (:documents p))))
        (is (= 209 (count (filter :force (:documents p)))))))))
