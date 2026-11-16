(ns kimun.cli.changed-test
  "`stores changed --since COMMIT`: git says which files changed (up to the file), and only
   the marks of changed files are read, comparing what git keeps at COMMIT with the text now
   (inside the file). Every project here is a throwaway git repository."
  (:require [babashka.fs :as fs]
            [babashka.process :as process]
            [clojure.string :as str]
            [clojure.test :refer [deftest is testing use-fixtures]]
            [kimun.cli.main :as main]
            [kimun.host.default :as host]
            [kimun.host.hash :as hash]
            [kimun.surface.markdown.anchors :as anchors]
            [kimun.surface.markdown.ast :as ast]
            [kimun.test-util :as ktu]))

(def ^:dynamic *p* nil)
(def env {"USER" "tester"})

(defn- run-in [p & argv] (binding [ast/*parser* "native"] (:envelope (main/run (vec argv) env p))))
(defn- run [& argv] (apply run-in *p* argv))
(defn- file [& parts] (str (apply fs/path *p* parts)))
(defn- git! [d & args]
  (let [r (apply process/shell {:out :string :err :string :continue true :dir (str d)} "git" args)]
    (when-not (zero? (:exit r)) (throw (ex-info (str "git failed: " (:err r)) {:args args})))
    (str/trim (:out r))))
(defn- commit! [msg] (git! *p* "add" "notes") (git! *p* "commit" "-qm" msg) (git! *p* "rev-parse" "HEAD"))
(defn- changed [since] (run "stores" "changed" "--since" since))
(defn- by-path [r] (into {} (map (juxt :path identity)) (get-in r [:data :changed])))

(def t0 "# Note\n\nUno.\n\nDos.\n\n## Otra\n\nTres.\n")
(defn- paras [text n]
  (binding [ast/*parser* "native"]
    (let [bs (filterv #(= :paragraph (:type (:locator %))) (:blocks (anchors/anchors host/host text)))]
      (is (= n (count bs)) "there are paragraphs to anchor")
      (mapv :locator bs))))

(defn- init-git! [p]
  (git! p "init" "-q") (git! p "config" "user.email" "t@e") (git! p "config" "user.name" "t"))

(defn- setup! [p]
  (fs/create-dirs (str p "/notes"))
  (init-git! p)
  (spit (str p "/notes/plain.md") t0)
  (spit (str p "/notes/other.md") "# Otro\n\nAlgo.\n")
  (spit (str p "/notes/unknown.md") "# Nadie\n\nNo registrado.\n")
  (is (true? (:ok (run-in p "stores" "init" "--name" "p"))))
  (is (true? (:ok (run-in p "docs" "ingest" "notes/plain.md"))))
  (is (true? (:ok (run-in p "docs" "ingest" "notes/other.md"))))
  (is (true? (:ok (run-in p "docs" "mark" "plain" "--locator" (pr-str (second (paras t0 3))))))))

(use-fixtures :each
  (fn [f]
    (let [p (str (ktu/temp-dir "kimun-changed-"))]
      (try (binding [*p* p] (setup! p) (f)) (finally (fs/delete-tree p))))))

(defn- counting [thunk]
  (let [orig anchors/anchors n (atom 0)]
    [(with-redefs [anchors/anchors (fn [& a] (swap! n inc) (apply orig a))] (thunk)) @n]))

(deftest nothing-changed-opens-nothing
  (let [x (commit! "base")
        [r n] (counting #(changed x))]
    (is (true? (:ok r)) (pr-str r))
    (is (= x (get-in r [:data :since])))
    (is (= [] (get-in r [:data :changed])))
    (is (= [] (get-in r [:data :marks])))
    (is (zero? n) "no document was analysed")
    (testing "the same counter does see an analysis when a marked file changed"
      (spit (file "notes" "plain.md") (str/replace t0 "Tres." "Tres, editado."))
      (let [[r2 n2] (counting #(changed x))]
        (is (= 1 (count (get-in r2 [:data :changed]))))
        (is (pos? n2))))))

(deftest git-sees-what-kimun-does-not-know
  (let [x (commit! "base")]
    (spit (file "notes" "unknown.md") "# Nadie\n\nCambiado.\n")
    (spit (file "notes" "other.md") "# Otro\n\nAlgo mas.\n")
    (spit (file "notes" "nuevo.md") "# Nuevo\n")
    (let [[r n] (counting #(changed x))
          m (by-path r)]
      (is (= #{"notes/unknown.md" "notes/other.md" "notes/nuevo.md"} (set (keys m))) (pr-str (keys m)))
      (is (= :modified (:change (m "notes/unknown.md"))))
      (is (= :untracked (:change (m "notes/nuevo.md"))))
      (is (false? (:known (m "notes/unknown.md"))))
      (is (false? (:known (m "notes/nuevo.md"))))
      (is (true? (:known (m "notes/other.md"))) "registered, but with nothing anchored")
      (is (every? false? (map :opened (vals m))))
      (is (zero? n) "none of them was opened")
      (is (not-any? #(str/starts-with? % ".kimun") (keys m))))))

(deftest anchored-mutated-ambiguous-missing
  (let [x (commit! "base")
        edit! (fn [text] (spit (file "notes" "plain.md") text))
        mark-of (fn [] (let [r (changed x)] [r (first (get-in r [:data :marks]))]))]
    (testing "an edit outside what is anchored"
      (edit! (str/replace t0 "Tres." "Tres, editado."))
      (let [[r mk] (mark-of)]
        (is (= ["notes/plain.md"] (map :path (get-in r [:data :changed]))))
        (is (true? (get-in r [:data :changed 0 :opened])))
        (is (= :anchored (:status mk)))))
    (testing "an edit inside it"
      (edit! (str/replace t0 "Dos." "Dos, editado."))
      (is (= :mutated (:status (second (mark-of))))))
    (testing "a paragraph inserted before: the locator now reads another paragraph"
      (edit! (str/replace t0 "Uno." "Uno.\n\nNuevo."))
      (let [mk (second (mark-of))]
        (is (= :ambiguous (:status mk)))
        (is (seq (:candidates mk)) "the candidates are listed, none chosen")))
    (testing "deleting what was marked"
      (edit! "# Note\n\nUno.\n")
      (let [mk (second (mark-of))]
        (is (= :missing (:status mk)))))))

(deftest before-is-what-git-has-not-the-stored-hash
  ;; commit X holds a text whose marked paragraph differs from the one the mark was declared on:
  ;; the stored hash is behind X. Against X the mark is anchored; against the stored hash it
  ;; would be :mutated.
  (let [edited (str/replace t0 "Dos." "Dos, ya cambiado.")
        _ (spit (file "notes" "plain.md") edited)
        x (commit! "marked paragraph already edited at X")]
    (spit (file "notes" "plain.md") (str/replace edited "Tres." "Tres, editado."))
    (let [r (changed x) mk (first (get-in r [:data :marks]))]
      (is (= :anchored (:status mk)) (pr-str mk))
      (testing "the stored hash is the old one"
        (let [stored (get-in (run "docs" "marks" "plain") [:data :marks 0])]
          (is (= :mutated (:status stored)) "kimun's own reading, against its stored hash, differs"))))))

(deftest uncommitted-changes-count-and-unknown-at-since
  (let [x (commit! "base")]
    (spit (file "notes" "plain.md") (str/replace t0 "Dos." "Dos, sin commit."))
    (let [r (changed x)]
      (is (true? (get-in r [:data :dirty])))
      (is (= :mutated (:status (first (get-in r [:data :marks]))))))
    (testing "committed or not gives the same marks"
      (let [before (get-in (changed x) [:data :marks])
            _ (commit! "now committed")
            after (get-in (changed x) [:data :marks])]
        (is (seq before))
        (is (= before after))
        (is (false? (get-in (changed x) [:data :dirty])))))
    (testing "a file that did not exist at X is said, not invented"
      (spit (file "notes" "late.md") "# Tarde\n\nUno.\n")
      (is (true? (:ok (run "docs" "ingest" "notes/late.md"))))
      (is (true? (:ok (run "docs" "mark" "late" "--locator" (pr-str (first (paras (slurp (file "notes" "late.md")) 1)))))) "")
      (commit! "late")
      (let [mk (first (filter #(= "late" (:document %)) (get-in (changed x) [:data :marks])))]
        (is (some? mk))
        (is (= :unknown (:status mk)))
        (is (= :file-not-at-since (:reason mk)))))))

(defn- tree-digest [dir]
  (into (sorted-map)
        (for [f (file-seq (fs/file dir)) :when (.isFile f)]
          [(str (fs/relativize dir f)) (hash/sha256-hex (fs/read-all-bytes f))])))

(defn- git-state []
  {:status (git! *p* "status" "--porcelain") :head (git! *p* "rev-parse" "HEAD")
   :stash (git! *p* "stash" "list") :refs (git! *p* "for-each-ref")
   :index (hash/sha256-hex (fs/read-all-bytes (str (fs/path *p* ".git" "index"))))})

(deftest read-only
  (let [x (commit! "base")]
    (spit (file "notes" "plain.md") (str/replace t0 "Dos." "Dos, editado."))
    (spit (file "notes" "nuevo.md") "# Nuevo\n")
    (let [k0 (tree-digest (file ".kimun")) g0 (git-state)
          r (changed x)]
      (is (true? (:ok r)))
      (is (seq (get-in r [:data :changed])))
      (is (seq k0))
      (is (= k0 (tree-digest (file ".kimun"))))
      (is (= g0 (git-state)))
      (is (seq (:status g0)) "there were uncommitted changes to disturb"))))

(deftest not-a-git-repository
  (let [p (str (ktu/temp-dir "kimun-changed-nogit-"))]
    (try
      (fs/create-dirs (str p "/notes"))
      (spit (str p "/notes/a.md") t0)
      (is (true? (:ok (run-in p "stores" "init" "--name" "q"))))
      (is (true? (:ok (run-in p "docs" "ingest" "notes/a.md"))))
      (let [strip #(dissoc (:data %) :observed-at)
            s0 (run-in p "stores" "sense")
            r (run-in p "stores" "changed" "--since" "HEAD")
            s1 (run-in p "stores" "sense")]
        (is (true? (:ok s0)))
        (is (pos? (get-in s0 [:data :tracked])))
        (is (false? (:ok r)))
        (is (= "git/not-a-repository" (get-in r [:error :type])) (pr-str r))
        (is (= (strip s0) (strip s1))))
      (finally (fs/delete-tree p)))))
