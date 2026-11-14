(ns kimun.cli.c4-route-rebuild-test
  "C4 end to end: include build registers a route for every declared document,
   and include build --rebuild regenerates the complete route map."
  (:require [babashka.fs :as fs]
            [babashka.process :as process]
            [clojure.string :as str]
            [clojure.test :refer [deftest is]]
            [kimun.cli.main :as main]
            [kimun.test-util :as ktu]))

(def model-text
  (str "{:base []\n"
       " :fields\n"
       " [{:description \"Name.\" :name \"name\" :py/annotation \"<class 'str'>\" :required true :type :string}\n"
       "  {:description \"Body.\" :name \"body\" :py/annotation \"<class 'str'>\" :required true :type :string}]\n"
       " :model/name \"RouteDoc\"\n"
       " :model/version 1\n"
       " :semantics {\"type\" [\"test\"]}\n"
       " :template \"---\\nid: \u2e22rev\u2022name\u2e25\\n---\\n\\n# Title\\n\\n## Body\\n\\n\u2e22rev\u2022body\u2e25\\n\"}\n"))

(def include-text
  (str "{:include/version 1\n"
       " :store {:path \".kimun\" :git :ignore}\n"
       " :models [\"models/*.edn\"]\n"
       " :documents [{:glob \"docs/**/*.md\" :model \"RouteDoc\"}]}\n"))

(defn- git! [dir & args]
  (let [r (apply process/shell {:out :string :err :string :continue true :dir (str dir)} "git" args)]
    (when-not (zero? (:exit r)) (throw (ex-info (str "git failed: " (:err r)) {:args args})))))

(defn- write! [dir rel text]
  (let [f (fs/path dir rel)]
    (some-> (fs/parent f) fs/create-dirs)
    (spit (str f) text)))

(defn- project []
  (let [p (str (ktu/temp-dir "kimun-c4-route-"))]
    (git! p "init" "-q")
    (git! p "config" "user.email" "tester@example.com")
    (git! p "config" "user.name" "tester")
    (write! p "models/RouteDoc.edn" model-text)
    (doseq [n ["a" "b" "c"]]
      (write! p (str "docs/" n ".md")
              (str "---\nid: " (str/upper-case n) "\n---\n\n# Title\n\n## Body\n\n" n "\n")))
    (write! p "kimun.include.edn" include-text)
    (git! p "add" ".")
    (git! p "commit" "-qm" "init")
    p))

(defn- run [p & argv] (:envelope (main/run (vec argv) {"USER" "tester"} p)))
(defn- check-data [p] (:data (run p "stores" "check")))
(defn- verify-data [p] (:data (run p "stores" "verify")))

(deftest include-build-and-rebuild-register-every-route
  (let [p (project)]
    (is (:ok (run p "include" "build")))
    (let [c (check-data p)
          v (verify-data p)]
      (is (true? (:routes-complete? c)))
      (is (= 3 (:route-count c)))
      (is (string? (:routes-root c)))
      (is (= {:complete? true :cached-trees 3 :routed-trees 3}
             (select-keys (:routes v) [:complete? :cached-trees :routed-trees])))
      (is (true? (get-in v [:routes :complete?]))))
    (let [root-before (:routes-root (check-data p))]
      (is (:ok (run p "include" "build" "--rebuild")))
      (let [c (check-data p)
            v (verify-data p)]
        (is (true? (:routes-complete? c)))
        (is (= 3 (:route-count c)))
        (is (= root-before (:routes-root c))
            "the route root is stable across rebuild with new ULIDs")
        (is (true? (get-in v [:routes :complete?])))))))
