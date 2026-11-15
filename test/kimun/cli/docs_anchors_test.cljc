(ns kimun.cli.docs-anchors-test
  "S2a from the CLI: `docs anchors NAME` is read-only and gives the same
   locators as `kimun.surface.markdown.anchors/anchors`, for a typed and for a
   generic document, with the cache present or gone: nothing is written to the
   store (same head, same sha256 of every file) and no id is minted."
  (:require [babashka.fs :as fs]
            [clojure.test :refer [deftest is testing use-fixtures]]
            [kimun.cli.main :as main]
            [kimun.cli.store :as cli-store]
            [kimun.host.default :as host]
            [kimun.surface.markdown.anchors :as anchors]
            [kimun.surface.docs :as tdocs]
            [kimun.surface.markdown.ast :as ast]
            [kimun.surface.models.blocks :as blocks]
            [kimun.kernel.canon :as canon]
            [kimun.kernel.store :as kstore]
            [kimun.test-util :as ktu]))

(def ^:dynamic *p* nil)
(def env {"USER" "tester"})

(defn- run-in [p & argv]
  (binding [ast/*parser* "native"]
    (:envelope (main/run (vec argv) env p))))
(defn- run [& argv] (apply run-in *p* argv))
(defn- file [& parts] (str (apply fs/path *p* parts)))

(def ticket
  (pr-str {:model/name "TicketDoc" :model/version 1 :base []
           :fields [{:name "title" :type :string :required true :description "Title."}
                    {:name "status" :type :string :required true :description "Status."}]
           :template "# ⸢rev•title⸥\n\nStatus: ⸢rev•status⸥\n"}))

(defn- setup! [p]
  (fs/create-dirs (str p "/docs")) (fs/create-dirs (str p "/notes"))
  (spit (str p "/ticket.edn") ticket)
  (spit (str p "/docs/a.md") "# T\n\nStatus: open\n")
  (spit (str p "/notes/plain.md") "# Note\n\nUno.\n\n## Otra\n\nDos.\n")
  (is (true? (:ok (run-in p "stores" "init" "--name" "p"))))
  (is (true? (:ok (run-in p "models" "define" "-f" "ticket.edn"))))
  (is (true? (:ok (run-in p "docs" "track" "TicketDoc" "docs/a.md" "--name" "a"))))
  (is (true? (:ok (run-in p "docs" "ingest" "notes/plain.md")))))

(use-fixtures :each
  (fn [f]
    (let [p (str (ktu/temp-dir "kimun-anchors-cli-"))]
      (try (binding [*p* p] (setup! p) (f)) (finally (fs/delete-tree p))))))

(defn- sha256
  "sha256 of every file under `.kimun/`, as a map path → digest."
  [p]
  (into (sorted-map)
        (map (fn [f]
               (let [md (java.security.MessageDigest/getInstance "SHA-256")]
                 [(str (fs/relativize (file ".kimun") f))
                  (apply str (map #(format "%02x" (bit-and 0xff %))
                                  (.digest md (java.nio.file.Files/readAllBytes (fs/path f)))))])))
        (filter fs/regular-file? (fs/glob (file ".kimun") "**" {:hidden true}))))

(defn- head [p] (:head (:store (cli-store/open! (str p "/.kimun")))))

(defn- expected
  "The anchors of the source of document `name`, computed with the function."
  [name]
  (binding [ast/*parser* "native"]
    (let [s (:store (cli-store/open! (file ".kimun")))
          d (tdocs/resolve-any s name)
          text (or (when-let [rel (:path d)]
                     (let [f (fs/path *p* rel)] (when (fs/regular-file? f) (slurp (str f)))))
                   (:source (or (tdocs/find-document s (:tree d))
                                (tdocs/generic-document s (:tree d)))))
          kids (:children (ast/parse host/host text))
          sp (mapv :map (blocks/parse text))
          lines (vec (clojure.string/split-lines text))
          aligned? (and (= (count kids) (count sp))
                        (every? true?
                                (map (fn [m b]
                                       (let [slice (clojure.string/join "\n"
                                                                        (subvec lines (long (first m))
                                                                                (long (min (second m) (count lines)))))
                                             kk (:children (ast/parse host/host slice))]
                                         (and (= 1 (count kk))
                                              (= (canon/digest host/host (first kk))
                                                 (canon/digest host/host b)))))
                                     sp kids)))]
      (assoc (anchors/anchors host/host text (cond-> {} aligned? (assoc :spans sp)))
             :path (:path d)))))

(deftest docs-anchors-gives-the-same-locators-and-writes-nothing
  (doseq [name ["a" "plain"]]
    (testing (str "document " name)
      (let [before (sha256 *p*)
            h0 (head *p*)
            e (:data (run "docs" "anchors" name))
            exp (expected name)]
        (is (true? (:ok (run "docs" "anchors" name))) (pr-str e))
        (is (pos? (count (:sections e))) "there are sections to compare")
        (is (pos? (count (:blocks e))))
        (is (= (mapv :locator (:sections exp)) (mapv :locator (:sections e))))
        (is (= (mapv :locator (:blocks exp)) (mapv :locator (:blocks e))))
        (is (= (mapv :hash (:blocks exp)) (mapv :hash (:blocks e))))
        (is (= (:path exp) (:path e)))
        (is (string? (get-in e [:parse-contract :digest])) "the reading contract travels with it")
        (is (= h0 (head *p*)) "the head does not move")
        (is (= before (sha256 *p*)) "no file of the store changes")
        (testing "--sections leaves the blocks out"
          (let [only (:data (run "docs" "anchors" name "--sections"))]
            (is (pos? (count (:sections only))))
            (is (nil? (:blocks only)))))))
    (testing "and nothing is written when the cache is gone (cold cache is not warmed)"
      (let [cache (file ".kimun" "cache")]
        (when (fs/exists? cache) (fs/delete-tree cache))
        (let [before (sha256 *p*)
              h0 (head *p*)
              e (:data (run "docs" "anchors" "a"))]
          (is (pos? (count (:sections e))))
          (is (= h0 (head *p*)))
          (is (= before (sha256 *p*))))))))
