(ns kimun.host.git
  "Host adapter that asks git what changed (docs/v2/drafts/diseno-declaraciones.md §3, level
   \"up to the file\"). Read-only: it runs only `rev-parse`, `diff`, `ls-files` and `show`,
   with `GIT_OPTIONAL_LOCKS=0` so not even the index is refreshed. It never opens or hashes a
   project file itself. Paths are relative to the project directory handed in."
  (:require [babashka.process :as process]
            [clojure.string :as str]
            [kimun.kernel.err :as err]))

(defn- git
  "Runs `git args` in `dir`: `{:exit :out}`; `:exit` -1 when git cannot be started."
  [dir & args]
  (try (let [r (-> (apply process/process {:dir (str dir) :out :string :err :string
                                           :extra-env {"GIT_OPTIONAL_LOCKS" "0"}}
                          "git" args)
                   deref)]
         {:exit (:exit r) :out (:out r) :err (:err r)})
       (catch java.io.IOException e {:exit -1 :out "" :err (ex-message e)})))

(defn- rel-prefix
  "`exclude` (relative paths, e.g. the store directory) as prefixes `\"p/\"`."
  [exclude] (mapv #(str (str/replace % #"/+$" "") "/") exclude))

(defn- excluded? [prefixes path]
  (boolean (some #(or (str/starts-with? path %) (= path (subs % 0 (dec (count %))))) prefixes)))

(defn repo-head
  "The full sha of HEAD of the git repository that holds `dir`. Raises
   `:git/not-a-repository` when `dir` is not inside one (or git is absent) and
   `:git/no-commits` when HEAD does not exist yet."
  [dir]
  (let [inside (git dir "rev-parse" "--is-inside-work-tree")]
    (when-not (and (zero? (:exit inside)) (= "true" (str/trim (:out inside))))
      (err/raise :git/not-a-repository
                 (str (str dir) " is not inside a git repository: `--since` asks git, and git has nothing here")
                 {:dir (str dir) :detail (str/trim (:err inside))}))
    (let [h (git dir "rev-parse" "--verify" "-q" "HEAD^{commit}")]
      (when-not (zero? (:exit h))
        (err/raise :git/no-commits "the repository has no commits yet" {:dir (str dir)}))
      (str/trim (:out h)))))

(defn resolve-commit
  "The full sha of the commit `rev` names in the repository of `dir`; raises
   `:git/unknown-commit` when it names none."
  [dir rev]
  (let [r (git dir "rev-parse" "--verify" "-q" (str rev "^{commit}"))]
    (when-not (zero? (:exit r))
      (err/raise :git/unknown-commit (str "git knows no commit `" rev "`") {:since (str rev) :dir (str dir)}))
    (str/trim (:out r))))

(def ^:private change-kind
  {\M :modified \A :added \D :deleted \R :renamed \C :copied \T :type-changed})

(defn- parse-name-status
  "Entries `{:path :change :from?}` of `git diff --name-status -z` output."
  [out]
  (loop [[s & more :as toks] (remove str/blank? (str/split out #"\u0000")) acc []]
    (if (nil? s)
      acc
      (let [k (get change-kind (first s) :other)]
        (if (#{:renamed :copied} k)
          (recur (drop 2 more) (conj acc {:path (second more) :change k :from (first more)}))
          (recur (rest more) (conj acc {:path (first more) :change k})))))))

(defn changed-since
  "What git says changed between commit `sha` and the working tree of `dir` (committed or
   not): `[{:path :change :from?}]` sorted by path, `:change` one of `:modified :added
   :deleted :renamed :copied :type-changed :untracked`. Untracked files git does not ignore
   are `:untracked`. Paths under `exclude` (relative, e.g. `[\".kimun\"]`) are left out.
   Raises `:git/error` when git fails."
  [dir sha exclude]
  (let [d (git dir "diff" "--name-status" "-z" "-M" "--relative" sha "--" ".")
        u (git dir "ls-files" "-z" "--others" "--exclude-standard" "--" ".")
        _ (doseq [r [d u] :when (not (zero? (:exit r)))]
            (err/raise :git/error (str "git failed: " (str/trim (:err r))) {:dir (str dir)}))
        px (rel-prefix exclude)]
    (->> (concat (parse-name-status (:out d))
                 (map (fn [p] {:path p :change :untracked}) (remove str/blank? (str/split (:out u) #"\u0000"))))
         (remove #(excluded? px (:path %)))
         (sort-by :path) vec)))

(defn show
  "The text of `rel` (relative to `dir`) as commit `sha` has it, or nil when that commit has
   no such file."
  [dir sha rel]
  (let [r (git dir "show" (str sha ":./" rel))]
    (when (zero? (:exit r)) (:out r))))
