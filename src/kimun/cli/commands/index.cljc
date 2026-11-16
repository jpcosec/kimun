(ns kimun.cli.commands.index
  "The `kimun rebuild-indexes` command (v1 `stores semantic-map` and the index
   refresh of `stores update`; docs/v2/06 §3): rebuilds the derived index
   from the store and writes its cache, `.kimun/index/<head>.edn`. It is the
   only command that writes the cache — reads never do — and it commits
   nothing: no transaction, the store's revision is the one
   it had. A top-level command: `dispatch` is registered under
   \"rebuild-indexes\" and takes no subcommand."
  (:require [kimun.cli.out :as out]
            [kimun.cli.store :as cli-store]
            [kimun.surface.index.cache :as cache]))

(def spec
  "Options of `rebuild-indexes` (babashka.cli spec, merged by `kimun.cli.main`): none of its own."
  {})

(defn rebuild-indexes
  "`rebuild-indexes`: `{:head :cache :bytes :documents :models :tags :skipped
   :complete :pruned}` (`kimun.surface.index.cache/rebuild!` without the index
   itself). A document that no longer extracts is indexed without payload,
   named in `:skipped` and in a warning, and makes `:complete` false; the
   command still succeeds, because the cache it wrote is exact."
  [args opts {:keys [env cwd]}]
  (if (seq args)
    (out/failure :cli/usage (str "`rebuild-indexes` takes no arguments, got: " (pr-str (vec args)))
                 {:group "rebuild-indexes" :args (vec args)} 6)
    (let [{:keys [descriptor path] :as opened} (cli-store/open! (cli-store/resolve-store opts env cwd))
          report (cache/rebuild! opened)
          idx (:index report)]
      (out/ok "rebuild-indexes"
              {:store {:name (:name descriptor) :path path}
               :revision (:head report)
               :data (dissoc report :index)
               :warnings (mapv (fn [n] (let [d (or (get-in idx [:docs (get-in idx [:by-name n])])
                                         (first (filter #(= n (:name %)) (vals (:generics idx)))))]
                                         {:type "index/skipped-document" :document n :model (:model d) :message (:error d)}))
                               (:skipped report))}))))

(def commands
  "Command name → handler `(fn [args opts ctx] envelope)`."
  {"rebuild-indexes" rebuild-indexes})

(defn dispatch
  "The dispatch fn of the top-level command `rebuild-indexes`: `sub` is the
   first positional token after the command (there should be none) and `args`
   the rest; any of them is the failure `cli/usage` (exit 6)."
  [sub args opts ctx]
  (rebuild-indexes (cond->> (vec args) sub (into [sub])) opts ctx))
