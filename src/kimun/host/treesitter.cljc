(ns kimun.host.treesitter
  "Host adapter for the tree-sitter pod (docs/v2/14 §11, decision 29): discovers,
   loads and calls the native parser of the M layer. Ring 1 — this is the only
   namespace that imports the pod, so the pod stays an implementation detail and
   the surfaces go through this narrow host port (docs/v2/03 §1)."
  (:require [babashka.fs :as fs]
            [clojure.string :as str]
            #?(:clj [babashka.pods :as pods])))

(def ^:private exe-name
  (if (str/includes? (str (System/getProperty "os.name")) "Windows")
    "pod-kimun-treesitter.exe"
    "pod-kimun-treesitter"))

(defn- bb-executable
  "Path of the babashka executable running this process, or nil: the first
   argument of `/proc/self/cmdline` (Linux), else `bb` on PATH."
  []
  (or #?(:clj (try (let [cmd (slurp "/proc/self/cmdline")
                         s (subs cmd 0 (max 0 (str/index-of cmd "\u0000")))]
                     (when-not (str/blank? s) s))
                   (catch Exception _ nil))
         :cljs nil)
      (some-> (fs/which "bb") str)))

(defn- repo-root
  "Best guess of the repository root: the directory of `bb.edn` when babashka
   resolved one, else the working directory."
  []
  (let [cfg (System/getProperty "babashka.config")]
    (if (str/blank? cfg) (System/getProperty "user.dir") (str (fs/parent cfg)))))

(defn- candidates
  "Pod paths to try, in discovery order: `KIMUN_POD`, next to the bundled `bb`
   (`<dir of bb>/../lib/…`), then the development build."
  []
  (let [env (System/getenv "KIMUN_POD")
        bb (bb-executable)
        root (repo-root)]
    (cond-> []
      (not (str/blank? env)) (conj env)
      bb (conj (str (fs/path (fs/parent bb) ".." "lib" exe-name)))
      root (conj (str (fs/path root "pods" "treesitter" "target" "release" exe-name))))))

(defn pod-file
  "Existing path of the tree-sitter pod executable in this environment, or nil
   when none is installed: `KIMUN_POD` → `<dir of bb>/../lib/…` →
   `pods/treesitter/target/release/…` (development)."
  []
  (first (filter #(and % (fs/exists? %)) (candidates))))

(def ^:private pod
  (delay
    (when-let [f (pod-file)]
      (try
        (let [loaded (pods/load-pod f)
              id (:pod/id loaded)
              ns (symbol (if (string? id) id (str id)))]
          (require ns)
          ns)
        (catch Throwable _ nil)))))

(defn available?
  "True when the tree-sitter pod is discoverable and loaded. Loads it on first
   call (one pod process per Babashka process)."
  []
  (some? @pod))

(defn- call
  "Calls pod var `name` with `args` using the loaded pod; throws when the pod
   is not available so callers can fall back before reaching here."
  [name & args]
  (if-let [ns (some-> @pod find-ns)]
    (if-let [f (ns-resolve ns (symbol name))]
      (apply f args)
      (throw (ex-info (str "tree-sitter pod has no var " name) {:var name})))
    (throw (ex-info "tree-sitter pod is not available"
                    {:pod (pod-file) :hint "run `bb pod` or set KIMUN_POD"}))))

(defn version
  "`{:pod :version :tree_sitter :languages}` as reported by the pod."
  []
  (call "version"))

(defn blocks
  "Flat top-level Markdown block list, each
   `{:type :start_byte :end_byte :start_point :end_point}`: the pod segmentation
   without children or text (R2 §2 — never materialize the full CST here)."
  [text]
  (call "blocks" text))

(defn parse-block
  "Markdown **block** CST of `text` as `[root]`, no inline nodes."
  [text]
  (call "parse-block" text))

(defn parse-inline
  "Markdown inline CST of `text` as `[root]`."
  [text]
  (call "parse-inline" text))

(defn parse-lang
  "CST of `text` with grammar `language` (`\"markdown\"`, `\"markdown-inline\"`,
   `\"python\"`)."
  [text language]
  (call "parse-lang" text language))

(defn query
  "Tree-sitter captures of `query-string` over `text` parsed as `language`."
  [text query-string language]
  (call "query" text query-string language))
