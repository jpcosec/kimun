(ns kimun.parity.resolver-test
  "Ports of sldb v1's store/test_resolver.py (`find_local_store`): which store a
   command finds from the directory it runs in. The home-directory catalog and
   the `core/` layout tests are n/a (parity-v1.md, deviations)."
  (:require [clojure.test :refer [deftest is]]
            [clojure.string :as str]
            [babashka.fs :as fs]
            [kimun.cli.main :as main]))

(def env {"USER" "tester"})

(defn- tmp [] (str (fs/create-temp-dir {:prefix "kimun-resolver-"})))
(defn- run [cwd & argv] (:envelope (main/run argv env cwd)))
(defn- store-of [e] (str (fs/real-path (get-in e [:store :path]))))
(defn- expected [dir] (str (fs/real-path (fs/path dir ".kimun"))))

;; v1: store/test_resolver.py::test_find_local_store_in_start_dir
(deftest finds-the-store-in-the-start-directory
  (let [p (tmp)]
    (run p "stores" "init" "--name" "p")
    (is (= (expected p) (store-of (run p "stores" "show"))))))

;; v1: store/test_resolver.py::test_find_local_store_in_parent
(deftest finds-the-store-in-a-parent-directory
  (let [p (tmp) nested (str (fs/path p "src" "models"))]
    (run p "stores" "init" "--name" "p")
    (fs/create-dirs nested)
    (is (= (expected p) (store-of (run nested "stores" "show"))))))

;; v1: store/test_resolver.py::test_find_local_store_returns_none_when_missing
(deftest finds-nothing-when-there-is-no-store
  (let [e (run (tmp) "stores" "show")]
    (is (= [5 "cli/no-store"] [(:exit e) (get-in e [:error :type])]))
    (is (str/includes? (get-in e [:error :message]) "no kimun store found"))))
