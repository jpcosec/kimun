(ns kimun.surface.parse-contract
  "The parse contract of a typed Markdown document, recorded with its digest
   (K6, docs/v2/10 §11.1).

   A document's structure is the output of `parse(source)` under a contract.
   The source fingerprint says whether the *source* changed; this contract says
   whether the *way of reading it* changed, so the same file read by another
   parser is reported as a contract change and not as an outside edit.

   Version 1 records the components that really take part today:
   - `:parser`        the parser in force, `[name version]`: the in-process one
                      or the tree-sitter view (`KIMUN_PARSER`,
                      `kimun.surface.markdown.ast/effective-parser`), with
                      `store-cache/parser-version`;
   - `:grammar`       `\"builtin\"` for the in-process parser; for tree-sitter,
                      the pod and tree-sitter versions the pod reports. A pod
                      that does not answer is never recorded as a grammar: the
                      contract is then unknown (`:parser-unavailable`);
   - `:normalization` the text normalisation of canonical content (NFC, 02 §2.1);
   - `:extractor`     the field extractor, `[engine version]` (the pair the
                      `:derived` root→model edge records);
   - `:class`         the id of the model descriptor: its fields and template
                      are the selector and the class.
   A document of a spec 16 class (M1h, docs/v2/16 §3.3) records version 2:
   the same six plus what decides how the selector reads it — `:substrate`
   (`parser:grammar` the class declares), `:profile`, `:ancestors`
   (the ids of the revisions its selector was composed with) and `:selector`
   (the digest of the COMPOSED selector and fields, own plus inherited). The
   caller supplies them as a `reading` (see `reading`); v1 and generic
   documents never do and keep version 1, byte for byte.
   Not covered: an EDN selector over tree-sitter (the extractor reads SLDB-MD
   blocks whatever the substrate says; M1i).

   Everything here is derived: nothing reads or writes a store."
  (:require [clojure.string :as str]
            [kimun.host.store-cache :as store-cache]
            [kimun.host.treesitter :as ts]
            [kimun.kernel.err :as err]
            [kimun.kernel.ports :as ports]
            [kimun.surface.markdown.ast :as ast]))

(def version
  "Version of the shape of the contract map itself."
  1)

(def components
  "The components of a contract, in the order they are hashed and reported."
  [:contract/version :parser :grammar :normalization :extractor :class])

(def components-spec16
  "The components of a version 2 contract (a spec 16 class): `components` plus
   the reading of the class, in the order they are hashed and reported."
  (into components [:substrate :profile :ancestors :selector]))

(defn components-of
  "The components of contract map `c`, by its `:contract/version`."
  [c]
  (if (= 2 (:contract/version c)) components-spec16 components))

(def extractor
  "Engine and version of the field extractor, as recorded in the `:derived`
   root→model edge of a document."
  ["kimun.surface.models" "1"])

(defn environment
  "What reads documents in this process, resolved ONCE per operation (a
   diagnosis of many documents asks the pod once, not once per document):
   `{:parser :grammar :pod?}`. `:parser` is the parser in force
   (`ast/effective-parser`); `:grammar` is `\"builtin\"` for the in-process
   parser and, for tree-sitter, what the pod reports — nil when the pod is in
   force but does not answer, in which case the contract in force is not
   known and nothing may be recorded or compared as if it were; `:pod?` says
   whether the tree-sitter pod can be used here at all."
  []
  (let [parser (ast/effective-parser)
        pod? (boolean (ts/available?))]
    {:parser parser
     :pod? pod?
     :grammar (if (= "treesitter" parser)
                (let [v (try (ts/version) (catch #?(:clj Exception :cljs :default) _ nil))]
                  (when (and (map? v) (:version v))
                    (str (:pod v) "/" (:version v) " tree-sitter/" (:tree_sitter v))))
                "builtin")}))

(defn known?
  "True when `env` names the whole contract in force (its grammar is known)."
  [env]
  (some? (:grammar env)))

(defn- parser-id [parser] (str "kimun.surface.markdown/" parser))

(defn- kw-str [x] (cond (keyword? x) (subs (str x) 1) (nil? x) "" :else (str x)))

(defn reading
  "What a spec 16 class adds to the contract (M1h): `{:substrate :profile
   :ancestors :selector-text}` as the caller read it from the class
   and its lineage. `:substrate` is the map the class declares; `:selector-text`
   is the canonical text of the composed selector and fields (hashed by
   `contract`'s caller, `record`). Nil in, nil out: a v1 or generic document
   has no reading."
  [{:keys [substrate profile ancestors selector-text]}]
  {:substrate (str (kw-str (:parser substrate)) ":" (:grammar substrate))
   :profile (str (or profile (:profile substrate)))
   :ancestors (vec ancestors)
   :selector-text selector-text})

(defn contract
  "The parse contract in force under `env` (see `environment`) for a document
   of class `class-id` (a model descriptor id): a map of `components`. Nil when
   `env` is not `known?`. With a `reading` (spec 16 class, M1h; `:selector` is
   already a digest here, see `record`) it is a version 2 contract."
  ([env class-id] (contract env class-id nil))
  ([env class-id rd]
   (when (known? env)
     (cond-> {:contract/version (if rd 2 version)
              :parser [(parser-id (:parser env)) store-cache/parser-version]
              :grammar (:grammar env)
              :normalization "nfc"
              :extractor extractor
              :class class-id}
       rd (merge (select-keys rd [:substrate :profile :ancestors :selector]))))))

(defn- canonical
  "The exact string that is hashed: one component per line in the order of
   `components`, so the digest depends neither on map order nor on a printer."
  [c]
  (apply str (map (fn [k]
                    (let [v (get c k)]
                      (str (name k) " " (if (sequential? v) (apply str (interpose " " v)) v) "\n")))
                  (components-of c))))

(defn digest
  "`<alg>:<hex>` of contract map `c` under the hasher of `host`."
  [host c]
  (let [h (ports/hasher host)]
    (str (name (ports/algorithm h)) ":" (ports/digest-str h (canonical c)))))

(defn- with-selector-digest
  "`rd` (a `reading`) with its `:selector-text` replaced by the digest `:selector`.
   The digest of a declared `:contract/digest` is never read: it is always this one."
  [host rd]
  (when rd
    (-> rd (dissoc :selector-text)
        (assoc :selector (let [h (ports/hasher host)]
                           (str (name (ports/algorithm h)) ":" (ports/digest-str h (str (:selector-text rd)))))))))

(defn record
  "What is written in the anchor edge of a document of class `class-id`: the
   contract in force under `env` plus its `:digest`. The components travel
   with the digest so a later reading can say *which* of them moved. A spec 16
   class passes its `reading` (4th argument; M1h) and gets version 2; without
   one the record is the version 1 of always. Raises `:docs/parser-unavailable`
   when the contract in force is not known: an unknown grammar is never recorded."
  ([host env class-id] (record host env class-id nil))
  ([host env class-id rd]
   (if-let [c (contract env class-id (with-selector-digest host rd))]
     (assoc c :digest (digest host c))
     (err/raise :docs/parser-unavailable
                "the tree-sitter parser is selected but its pod does not report a version: the parse contract cannot be recorded (run `bb pod`, or unset KIMUN_PARSER)"
                {:parser (:parser env)}))))

(defn consistent?
  "True when `recorded` (a map read from an anchor edge) has every component
   and its `:digest` is the digest of those components. Evidence that does not
   verify is not trusted for a comparison."
  [host recorded]
  (and (map? recorded)
       (every? #(contains? recorded %) (components-of recorded))
       (= (:digest recorded) (digest host (select-keys recorded (components-of recorded))))))

(defn compare-recorded
  "How the contract a document was recorded under (`recorded`, the map of
   `record`, or nil for a document written before K6) relates to the one in
   force under `env` for `current-class` (the id of its model today, nil when
   it is gone). Returns `{:recorded digest :current digest :status :changed}`
   with `:status`
   - `:unrecorded`         nothing was recorded: nothing is claimed;
   - `:inconsistent`       the recorded digest is not the digest of the
                           recorded components: the evidence is not trusted;
   - `:parser-unavailable` the contract in force is not known (`env` is not
                           `known?`), or the document was recorded under the
                           tree-sitter parser and its pod cannot be used here:
                           this is not a change of the document's contract;
   - `:same`               the recorded digest is the one in force;
   - `:changed`            it is not; `:changed` names the components whose
                           value differs, in the order of `components` —
                           `:parser` only when the parser itself differs,
                           never for a class or an extractor. For a spec 16
                           document (`rd`, the `reading` in force now; 5th
                           argument) `:ancestors` and `:selector` are named
                           when its composed selector moved, whoever moved it."
  ([host env recorded current-class] (compare-recorded host env recorded current-class nil))
  ([host env recorded current-class rd]
  (let [;; a document recorded as version 1 is compared as version 1: what a spec 16
        ;; class adds is claimed only for a document that recorded it (never a mass change)
        rd (when (= 2 (:contract/version recorded)) rd)
        now (when (and current-class (known? env)) (record host env current-class rd))
        base {:recorded (:digest recorded) :current (:digest now) :changed []}]
    (cond
      (nil? recorded) (assoc base :status :unrecorded)
      (not (consistent? host recorded)) (assoc base :status :inconsistent)
      (or (not (known? env))
          (and (not (:pod? env))
               (or (= (parser-id "treesitter") (first (:parser recorded)))
                   ;; a spec 16 class that declares the tree-sitter substrate needs the pod too
                   (and (string? (:substrate recorded)) (str/starts-with? (:substrate recorded) "treesitter:")))))
      (assoc base :status :parser-unavailable)
      (and now (= (:digest recorded) (:digest now))) (assoc base :status :same)
      :else (assoc base :status :changed
                   :changed (filterv #(not= (get recorded %) (get now %)) (components-of recorded)))))))
