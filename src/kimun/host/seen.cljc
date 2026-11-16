(ns kimun.host.seen
  "The pointer \"I looked up to this commit\" (docs/v2/drafts/diseno-declaraciones.md §4): a
   fact about the derived index, not a declaration. It lives in `<store>/seen.edn` as
   `{:commit \"<full sha>\"}` and nothing else, outside the log, the plans and the revisions
   of the store. Writing is atomic (temporary file + rename). An unreadable file,
   or a sha git does not know, is `:git/bad-pointer`: it is never ignored silently."
  (:require [babashka.fs :as fs]
            [clojure.edn :as edn]
            [kimun.host.git :as git]
            [kimun.kernel.err :as err]))

(defn- file [store-dir] (str (fs/path store-dir "seen.edn")))

(defn raw
  "The `:commit` string stored in `<store-dir>/seen.edn` as written, unchecked; nil when
   there is no file or it holds nothing usable."
  [store-dir]
  (let [f (file store-dir)]
    (when (fs/regular-file? f)
      (try (let [c (:commit (edn/read-string (slurp f)))] (when (string? c) c))
           (catch Exception _ nil)))))

(defn read-pointer
  "The full sha the pointer of `store-dir` holds, or nil when there is no pointer. Raises
   `:git/bad-pointer` when `seen.edn` cannot be read or names a commit git (asked in `root`)
   does not know."
  [store-dir root]
  (let [f (file store-dir)]
    (when (fs/regular-file? f)
      (let [c (raw store-dir)]
        (when-not c
          (err/raise :git/bad-pointer (str f " is not readable as {:commit \"<sha>\"}; fix it or run `stores seen --set HEAD`")
                     {:file f}))
        (try (git/resolve-commit root c)
             (catch clojure.lang.ExceptionInfo e
               (if (= :git/unknown-commit (:type (ex-data e)))
                 (err/raise :git/bad-pointer (str f " names commit " c ", which git does not know; run `stores seen --set HEAD`")
                            {:file f :commit c})
                 (throw e))))))))

(defn write-pointer!
  "Moves the pointer of `store-dir` to the full sha `sha`: temporary file in the same
   directory, then atomic rename. Touches no other file."
  [store-dir sha]
  (let [f (file store-dir)
        tmp (str f ".tmp-" (System/nanoTime))]
    (spit tmp (str (pr-str {:commit sha}) "\n"))
    (fs/move tmp f {:atomic-move true :replace-existing true})
    sha))

(defn point-at-head!
  "Leaves the pointer of `store-dir` at HEAD of the git repository holding `root`. Returns
   the sha, or nil (writing nothing, failing never) when `root` is not a repository or has no
   commits."
  [store-dir root]
  (try (write-pointer! store-dir (git/repo-head root))
       (catch clojure.lang.ExceptionInfo e
         (if (#{:git/not-a-repository :git/no-commits} (:type (ex-data e))) nil (throw e)))))
