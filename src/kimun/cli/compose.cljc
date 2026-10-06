(ns kimun.cli.compose
  "The composition context of a store: the two functions
   `kimun.surface.models.render/render` (and `render-marked`, and through it
   `kimun.surface.models.segments/document-segments`) take to expand a model's
   compositions (`⸢render•name⸥`). The engine does no I/O, so the reads live
   here. Refs resolve against the PROJECT ROOT — the parent of the `.kimun`
   directory — never the process working directory: a document composed from
   one directory and re-saved from another must give the same text (v1
   `tests/api/test_composition_ref_root.py`)."
  (:require [babashka.fs :as fs]
            [clojure.string :as str]
            [kimun.surface.docs :as tdocs]
            [kimun.surface.models.store :as mstore]))

(defn project-root
  "The project root of the store directory `store-path` (the `.kimun`
   directory): its parent, absolute and normalised."
  [store-path]
  (str (fs/parent (fs/normalize (fs/absolutize store-path)))))

(defn- ref-text-fn
  "`(fn [ref] markdown-or-nil)`: the stored source of the tracked document
   whose recorded path (relative to `root`) or whose name is `ref`, else the
   content of the regular file at `root`/`ref`, else nil. A ref that leaves
   the root (`../x`, an absolute path) names no file: a model, which may come
   from a linked store, cannot pull a file from outside the project into a
   document (v1 read whatever path the ref named)."
  [store root]
  (let [docs (delay (tdocs/documents store))
        root-path (fs/path root)]
    (fn [ref]
      (let [ref (str/trim (str ref))]
        (when-not (str/blank? ref)
          (let [f (fs/normalize (fs/path root-path ref))
                rel (when (fs/starts-with? f root-path) (str (fs/relativize root-path f)))
                doc (or (some #(when (and rel (= rel (:path %))) %) @docs)
                        (some #(when (= ref (:name %)) %) @docs))]
            (cond (:source doc) (:source doc)
                  (and rel (fs/regular-file? f)) (slurp (str f))
                  :else nil)))))))

(defn- ref-model-fn
  "`(fn [model-ref] descriptor-or-nil)`: `module:Class` or a bare name, looked
   up in `store` by class name (the text after the last `:`)."
  [store]
  (fn [model-ref]
    (let [class-name (last (str/split (str/trim (str model-ref)) #":"))]
      (when-not (str/blank? class-name)
        (:descriptor (mstore/find-model store class-name))))))

(defn context
  "The render context `{:ref-text f :ref-model g}` of a store.

   Signatures:
   - `(context opened)` — `opened` is the map `kimun.cli.store/open!` returns
     (`{:store s :path dir …}`), as a command gets it from its `open` helper;
   - `(context store store-path)` — `store` is the replayed store value and
     `store-path` the `.kimun` directory (the `:path` of `open!`). Pass the
     store a command is about to READ under: after a commit, build a new
     context from the store the commit returned.

   `:ref-text` is `(fn [ref] markdown-or-nil)`: `ref` resolves against the
   project root (`project-root`), never the process working directory — the
   stored source of the tracked document whose recorded path or name is `ref`,
   else the content of the file at that path under the root, else nil. As in
   v1 a ref is a path joined to the root and is not confined to it.
   `:ref-model` is `(fn [model-ref] descriptor-or-nil)`: `module:Class` or a
   bare name, resolved to the descriptor of the store's model with that class
   name. The result is what `render/render`, `render/render-marked` and
   `segments/document-segments` take as their `ctx` (the latter also reads
   `:id`, `:doc` and `:path` from it: `assoc` them onto this map)."
  ([opened] (context (:store opened) (:path opened)))
  ([store store-path]
   {:ref-text (ref-text-fn store (project-root store-path))
    :ref-model (ref-model-fn store)}))
