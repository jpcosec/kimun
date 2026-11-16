(ns kimun.cli.generate-test
  "`declare generate [--check]` (docs/v2/drafts/diseno-generar.md), over throwaway projects with
   and without git: it writes only the declared paths, atomically, never under `kimun/`, `.git/`
   or `.kimun/` nor outside the root; `--check` writes nothing; a failing recipe writes nothing."
  (:require [babashka.fs :as fs]
            [babashka.process :as process]
            [clojure.string :as str]
            [clojure.test :refer [deftest is testing]]
            [kimun.cli.main :as main]
            [kimun.test-util :as ktu]))

(def env {"USER" "tester"})
(defn- run [p & argv] (:envelope (main/run (vec argv) env (str p))))
(defn- git! [d & args]
  (let [r (apply process/shell {:out :string :err :string :continue true :dir (str d)} "git" args)]
    (when-not (zero? (:exit r)) (throw (ex-info (str "git failed: " (:err r)) {:args args})))))

(defn- put! [p rel text]
  (fs/create-dirs (fs/parent (fs/path p rel)))
  (spit (str (fs/path p rel)) text))

(def vistas
  "(ns proy.vistas)
(relation isa \"A es de la clase B.\")
(isa uno decision)
(isa dos decision)
(defn decisiones [ask]
  (str \"# Decisiones\\n\\n\"
       (apply str (for [p ((:about ask) 'decision)] (str \"- \" (:term (:other p)) \"\\n\")))
       \"\\nArchivos: \" (pr-str ((:members ask) \"docs/**\")) \"\\n\"))
(isa (generated \"docs/decisiones.md\" decisiones) doc.indice)
")

(defn- project
  "A project: the declarations, a source doc and a file nobody declares; with git when `git?`."
  [git? & [extra]]
  (let [p (fs/real-path (ktu/temp-dir "kimun-gen-"))]
    (put! p "kimun/vistas.clj" (or extra vistas))
    (put! p "docs/fuente.md" "# Fuente\n\nTexto.\n")
    (put! p "README.md" "hola\n")
    (when git?
      (git! p "init" "-q") (git! p "config" "user.email" "t@e") (git! p "config" "user.name" "t")
      (git! p "add" "-A") (git! p "commit" "-qm" "base"))
    p))

(defn- listing
  "Path → text of every file under `p` but `.git` (a snapshot of the tree)."
  [p]
  (into (sorted-map)
        (for [f (fs/glob p "**" {:hidden true})
              :when (and (fs/regular-file? f {:nofollow-links true}) (not (re-find #"(^|/)\.git/" (str (fs/relativize p f)))))]
          [(str (fs/relativize p f)) (slurp (str f))])))

(defn- files [e] (into {} (map (juxt :path identity)) (get-in e [:data :files])))

(def expected "# Decisiones\n\n- uno\n- dos\n\nArchivos: [\"docs/fuente.md\"]\n")

(deftest generate-creates-then-leaves-it-unchanged
  (doseq [git? [true false]]
    (let [p (project git?)
          before (listing p)
          e1 (run p "declare" "generate")
          after1 (listing p)
          e2 (run p "declare" "generate")]
      (is (:ok e1) (pr-str e1))
      (is (= [{:path "docs/decisiones.md" :status :created :bytes (count (.getBytes expected "UTF-8"))
               :declared-at {:file "kimun/vistas.clj" :line 9}}]
             (get-in e1 [:data :files])))
      (is (= {:files 1 :created 1} (get-in e1 [:data :summary])))
      (is (= (assoc before "docs/decisiones.md" expected) after1) "only the declared file appeared")
      (is (= :unchanged (:status (get (files e2) "docs/decisiones.md"))) "second run")
      (is (= after1 (listing p)) "the same bytes, and nothing else touched")
      (is (= [] (filter #(re-find #"\.tmp-" (key %)) (listing p))) "no temporary files left"))))

(deftest a-changed-declaration-updates-the-file
  (let [p (project false)
        _ (run p "declare" "generate")
        _ (put! p "kimun/vistas.clj" (str vistas "(isa tres decision)\n"))
        e (run p "declare" "generate")]
    (is (= :updated (:status (get (files e) "docs/decisiones.md"))))
    (is (str/includes? (slurp (str (fs/path p "docs/decisiones.md"))) "- tres\n"))))

(deftest check-says-differs-and-writes-nothing-and-generate-restores
  (doseq [git? [true false]]
    (let [p (project git?)
          missing (run p "declare" "generate" "--check")
          _ (run p "declare" "generate")
          same (run p "declare" "generate" "--check")
          _ (spit (str (fs/path p "docs/decisiones.md")) (str expected "editado a mano\n"))
          snapshot (listing p)
          differs (run p "declare" "generate" "--check")]
      (testing "before generating: missing, and the exit says there are differences"
        (is (false? (:ok missing)))
        (is (= ["declare/generated-differs" 3] [(get-in missing [:error :type]) (:exit missing)]))
        (is (= :missing (:status (get (files {:data (get-in missing [:error :data])}) "docs/decisiones.md")))))
      (testing "same: ok, exit 0"
        (is (:ok same)) (is (= :same (:status (get (files same) "docs/decisiones.md")))) (is (= {:files 1 :same 1} (get-in same [:data :summary]))))
      (testing "edited by hand: differs, exit 3, and the tree is untouched"
        (is (= [3 "declare/generated-differs"] [(:exit differs) (get-in differs [:error :type])]))
        (is (= :differs (:status (get (files {:data (get-in differs [:error :data])}) "docs/decisiones.md"))))
        (is (= snapshot (listing p)) "--check wrote nothing"))
      (testing "generate puts it back"
        (let [e (run p "declare" "generate")]
          (is (= :updated (:status (get (files e) "docs/decisiones.md"))))
          (is (= expected (slurp (str (fs/path p "docs/decisiones.md"))))))))))

(deftest a-failing-recipe-writes-none
  (doseq [bad ["(fn [_] 42)" "(fn [_] (throw (Exception. \"boom\")))"]]
    (let [p (project false (str vistas "(generated \"docs/z-bad.md\" " bad ")\n(generated \"docs/a-first.md\" (fn [_] \"ok\"))\n"))
          before (listing p)
          e (run p "declare" "generate")]
      (is (false? (:ok e)))
      (is (contains? #{"declare/bad-recipe" "declare/recipe"} (get-in e [:error :type])) (pr-str (:error e)))
      (is (= 3 (:exit e)))
      (is (= "docs/z-bad.md" (get-in e [:error :data :path])))
      (is (= before (listing p)) "not even the good ones were written"))))

(deftest paths-that-may-not-be-written-are-refused-before-writing-anything
  (doseq [[bad why] [["kimun/x.clj" #"kimun/"] [".git/hooks/pre-commit" #"\.git/"] [".kimun/x" #"\.kimun/"]]]
    (let [p (project false (str vistas "(generated \"" bad "\" (fn [_] \"x\"))\n"))
          before (listing p)
          e (run p "declare" "generate")]
      (is (= ["declare/bad-generated" 3 bad] [(get-in e [:error :type]) (:exit e) (get-in e [:error :data :path])]) bad)
      (is (re-find why (get-in e [:error :message])))
      (is (= before (listing p)) "the valid recipe was not written either")))
  (testing "outside the root, written in the declaration"
    (let [p (project false (str vistas "(generated \"../fuera.md\" (fn [_] \"x\"))\n"))
          e (run p "declare" "generate")]
      (is (= "declare/bad-generated" (get-in e [:error :type])))
      (is (not (fs/exists? (fs/path (fs/parent p) "fuera.md"))))))
  (testing "outside the root through a symbolic link"
    (let [out (fs/real-path (ktu/temp-dir "kimun-gen-out-"))
          p (project false (str vistas "(generated \"enlace/x.md\" (fn [_] \"x\"))\n"))
          _ (fs/create-sym-link (fs/path p "enlace") out)
          before (listing p)
          e (run p "declare" "generate")]
      (is (= "declare/bad-generated" (get-in e [:error :type])) (pr-str (:error e)))
      (is (re-find #"symbolic link" (get-in e [:error :message])))
      (is (not (fs/exists? (fs/path out "x.md"))) "nothing was written through the link")
      (is (= before (listing p)))))
  (testing "through a link that stays inside the root but points under .git or kimun"
    (let [p (project true (str vistas "(generated \"atajo/x.md\" (fn [_] \"x\"))\n"))
          _ (fs/create-sym-link (fs/path p "atajo") (fs/path p ".git"))
          e (run p "declare" "generate")]
      (is (= "declare/bad-generated" (get-in e [:error :type])) (pr-str (:error e)))
      (is (not (fs/exists? (fs/path p ".git" "x.md")))))))

(deftest only-declared-paths-are-touched-and-members-ignore-generated-files
  (let [p (project true (str vistas "(generated \"salida/indice.txt\" (fn [ask] (str (pr-str ((:members ask) \"docs/**\")) \"\\n\")))\n"))
        before (listing p)
        e1 (run p "declare" "generate")
        a1 (listing p)
        e2 (run p "declare" "generate")]
    (is (= #{"docs/decisiones.md" "salida/indice.txt"} (set (keys (files e1)))))
    (is (= (set (keys a1)) (into (set (keys before)) ["docs/decisiones.md" "salida/indice.txt"])))
    (is (= (select-keys before (keys before)) (select-keys a1 (keys before))) "everything else is byte for byte the same")
    (is (= "[\"docs/fuente.md\"]\n" (get a1 "salida/indice.txt"))
        "members lists the sources, not the generated files that happen to live in docs/")
    (is (= #{:unchanged} (set (map :status (vals (files e2))))) "so a second run gives the same bytes")))

(deftest about-path-of-a-generated-file-says-which-proposition-names-it
  (doseq [git? [true false]]
    (let [p (project git?)
          e (run p "declare" "about" "--path" "docs/decisiones.md")
          row (first (get-in e [:data :results]))]
      (is (= 1 (count (get-in e [:data :results]))))
      (is (= :generated (:via row)))
      (is (= {:term "doc.indice"} (:other row)))
      (is (= :a (:side row)))
      (is (= "proy.vistas/isa" (get-in row [:proposition :r])))))
  (testing "a file that is not generated has no such row"
    (is (empty? (get-in (run (project false) "declare" "about" "--path" "docs/fuente.md") [:data :results])))))

(deftest anchors-ignores-generated-nodes
  (let [p (project false (str vistas "(isa (at \"docs/fuente.md\") fuente)\n"))
        e (run p "declare" "anchors")]
    (is (:ok e) (pr-str e))
    (is (= ["docs/fuente.md"] (map #(get-in % [:anchor :path]) (get-in e [:data :anchors]))) "only the (at …), not the generated node")))

(deftest usage-and-an-empty-project
  (let [p (project false)]
    (is (= ["cli/usage" 6] ((juxt #(get-in % [:error :type]) :exit) (run p "declare" "generate" "kimun/vistas.clj"))) "takes no FILE"))
  (testing "a project with no declarations generates nothing and is fine"
    (let [p (fs/real-path (ktu/temp-dir "kimun-gen-empty-"))
          e (run p "declare" "generate")
          c (run p "declare" "generate" "--check")]
      (is (and (:ok e) (:ok c)))
      (is (= [] (get-in e [:data :files])))
      (is (= {:files 0} (get-in c [:data :summary]))))))
