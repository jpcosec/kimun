(ns kimun.surface.diagnostics
  "What is wrong with a store and what repairs it (sldb v1 `store/diagnostics`).
   `report` turns an opened store plus a `read-file` function into one finding
   per tracked document and per model, each with a `:category` and the
   `:action` that repairs it; `health` reduces a report to a verdict. Pure:
   the files are read through `read-file`, the hash verification is handed in.

   Categories of a document (v1 note in parentheses):
   - `:ok`             (ok) nothing to do;
   - `:reformatted`    (benign_mutation) the file's text moved, no field did —
                       safe: `kimun docs update NAME`; the store stays valid;
   - `:edited`         (data_mutation) a field's value was edited by hand, or
                       the file was edited while its model's contract moved —
                       a human decides before anything runs;
   - `:missing`        (missing) the tracked file is not on disk — a human
                       restores it or runs `kimun docs untrack NAME`;
   - `:contract-moved` (contract_mutation) the document's text is untouched
                       and the contract it is read under moved: the model
                       was redefined, or the parser/extractor is not the one
                       recorded (`kimun.surface.parse-contract`, K6) — safe:
                       `docs update NAME` (or `--backfill` for a model);
   - `:parser-unavailable` the document was recorded under a parser that
                       cannot be used here (the tree-sitter pod is absent or
                       does not answer): neither the source nor the contract
                       is known to have moved — a human installs the parser
                       or accepts the one in force with `docs update NAME`;
   - `:contract-inconsistent` the recorded parse-contract digest is not the
                       digest of its recorded components: the evidence is not
                       trusted — a human checks, then `docs update NAME`;
   - `:rebuild-mismatch` the source fingerprint and recorded parse contract match
                       what is in force, yet rebuilding the document in memory
                       produces a merkle root (or extracted content hash)
                       different from the registered one (`docs/v2/10-escala.md
                       §11.1`) — `:content-ok` and `:contract-ok` are both true;
                       a human decides before `kimun docs update NAME`;
   - `:unextractable`  the text no longer extracts under the model's contract;
   - `:noise`          the text (the file's when it was edited, else the
                       recorded source) carries headings or frontmatter keys
                       the model does not declare (`kimun.surface.models.noise`);
   - `:orphan`         the document's model is gone.
   Every document finding says which of the two moved: `:content-ok` (the
   source still has the recorded fingerprint) and `:contract-ok` (the recorded
   contract is the one in force), plus `:contract {:status :changed}` — the
   comparison of the recorded parse contract (`:unrecorded` for a document
   written before K6, which is never reported as changed for that). The
   environment that reads documents is resolved once per report.
   A document without a model registered under the generic class (K3,
   `kimun.surface.docs/generic-documents`) is diagnosed too, with `:generic
   true` and no `:model`: `:missing`, `:edited` (its structure changed),
   `:reformatted` (its text changed, its structure did not), the contract
   categories, or `:ok`. It has no fields, so `:fields-ok` follows `:content-ok`.
   A store-level finding `:corrupt` (v1's tampered hash) comes from the
   kernel's verification: `kimun stores verify` names the objects."
  (:require [clojure.string :as str]
            [clojure.walk :as walk]
            [kimun.kernel.err :as err]
            [kimun.surface.docs :as tdocs]
            [kimun.surface.models.noise :as noise]
            [kimun.surface.models.store :as mstore]
            [kimun.surface.parse-contract :as parse-contract]))

(def safe
  "Categories a command repairs without a decision (v1: \"safe to run\")."
  #{:reformatted :contract-moved})

(def clean
  "Categories that keep the store valid (v1: ok and benign_mutation)."
  #{:ok :reformatted})

(defn- try-extract [store doc descriptor text]
  (try {:payload (tdocs/extract-doc store descriptor doc text)}
       (catch #?(:clj clojure.lang.ExceptionInfo :cljs ExceptionInfo) e {:error (ex-message e)})))

(defn- action-of
   "The command that repairs `category`; `doc-name` is the token that names the
    document without ambiguity (its qualified name when the short one is shared)."
   [category doc-name]
   (case category
     :ok nil
     (:reformatted :edited :contract-moved :parser-unavailable :contract-inconsistent :rebuild-mismatch)
     (str "kimun docs update " doc-name)
     :missing (str "kimun docs untrack " doc-name)
     :corrupt "kimun stores verify"
     nil))

(defn- message-of [{:keys [category model path fields error noise contract registered-root rebuilt-root]
                    shown :document token :token}]
  (let [who (str "document `" shown "` (" model ")")
        ;; the commands a message names take the unambiguous token
        document (or token shown)]
    (case category
      :ok "ok"
      :reformatted (str who ": the text of " path " changed, its fields are identical (reformatting) — safe to run: kimun docs update " document)
      :edited (str who ": " path " was edited outside the store"
                   (when (seq fields) (str " [fields: " (str/join ", " fields) "]"))
                   " — decide first: if the edit was intended, kimun docs update " document
                   "; if not, restore the file from the recorded source (kimun docs show " document ")")
      :missing (str who ": tracked at " path ", not on disk — decide first: restore the file if it was moved by accident, or kimun docs untrack "
                    document " if it is gone on purpose")
      :contract-moved (if (and (seq (:changed contract)) (not (some #{:class} (:changed contract))))
                        (str who ": the contract it is read under is not the one it was recorded under ["
                             (str/join ", " (map name (:changed contract)))
                             "], not the document (its text is unchanged) — safe to run: kimun docs update " document)
                        (str who ": the contract of model `" model "` moved, not the document (its text is unchanged) — safe to run: kimun docs update "
                             document ", or redefine the model with --backfill"))
      :parser-unavailable (str who ": it was recorded under a parser that cannot be used here (the tree-sitter pod is absent or does not answer);"
                               " nothing says the document or its contract changed — decide first: build the pod (bb pod), or accept the parser in force with kimun docs update "
                               document)
      :contract-inconsistent (str who ": its recorded parse-contract digest does not match its recorded components; the evidence is not trusted"
                                  " — inspect it, then kimun docs update " document " records the contract in force")
      :rebuild-mismatch (str who ": rebuilding " (or path "the document")
                             " under its recorded contract produced "
                             (if (and registered-root rebuilt-root (not= registered-root rebuilt-root))
                               (str "root " rebuilt-root ", not the registered root " registered-root)
                               "a different content hash")
                             " (its text and contract are unchanged) — decide first: inspect the parser/extractor or re-register with kimun docs update "
                             document)
      :unextractable (str who ": its text no longer extracts under the contract of `" model "` (" error
                          ") — fix " (or path "the document") " or the model's template, then kimun docs update " document)
      :noise (str who ": structural noise — " (noise/describe noise) " — remove it from " (or path "the document")
                  " or declare it in the model, then kimun docs update " document)
      :orphan (str who ": its model is no longer defined — define `" model "` again or kimun docs untrack " document))))

(defn- classify
  "`[category extras]` of document `doc` given the text of its file (nil when
   it has none or it is gone) and its model's descriptor."
  [store env doc descriptor text]
  (let [host (:host store)
        content-ok (or (nil? (:path doc)) (and (some? text) (= (:fingerprint doc) (tdocs/fingerprint host text))))
        verified-text (if (and (:path doc) content-ok) (or text (:source doc)) (:source doc))
        recorded (when (and descriptor verified-text) (try-extract store doc descriptor verified-text))
        on-disk (when (and descriptor text (not content-ok)) (try-extract store doc descriptor text))
        ;; the recorded payload's hashes live in the anchor edge: the text is not stored,
        ;; so an edit is told apart from a reformatting by comparing hashes, not payloads
        on-disk-hash (when-let [p (:payload on-disk)] (tdocs/content-hash host p))
        recorded-hash (or (:recorded-content-hash doc)
                          (when-let [p (:payload recorded)] (tdocs/content-hash host p)))
        now-hash (when-let [p (:payload recorded)] (tdocs/content-hash host p))
        changed (if (seq (:recorded-field-hashes doc))
                  (let [rh (:recorded-field-hashes doc) now (tdocs/field-hashes host (:payload on-disk))]
                    (vec (sort (keep (fn [k] (when (not= (get rh k) (get now k)) k)) (distinct (concat (keys rh) (keys now)))))))
                  (vec (sort (keys (tdocs/changed-fields (or (:payload recorded) {}) (:payload on-disk))))))
        noise-of (fn [t] (when-not (mstore/spec16? descriptor) ; a spec 16 class has no template to declare headings in (M1g)
                           (let [n (noise/structural-noise descriptor t {:related (keep :descriptor (map #(mstore/find-model store (:name %)) (mstore/models store)))})]
                           (when (noise/noisy? n) n))))
        noisy (fn [base n] [:noise (assoc base :noise n :undeclared (mapv :raw (:headings n)))])
        pc (parse-contract/compare-recorded host env (:parse-contract doc) (:model-id doc) (tdocs/current-reading store (:model-id doc)))
        contract-moved? (or (= :superseded (:state doc)) (= :changed (:status pc)))
        base {:content-ok (boolean content-ok) :fields-ok (boolean (and content-ok (:payload recorded) (= :intact (:state doc))))
              :contract-ok (and (not contract-moved?) (not= :orphan (:state doc))
                                (contains? #{:same :unrecorded} (:status pc)))
              :contract (select-keys pc [:status :changed])}]
    (cond
      (and (:path doc) (nil? text)) [:missing (assoc base :content-ok false :fields-ok false :path-exists false)]
      (= :orphan (:state doc)) [:orphan base]
      (and (not content-ok) (:error on-disk)) [:unextractable (assoc base :error (:error on-disk))]
      (and (not content-ok) (noise-of text)) (noisy base (noise-of text))
      (and (not content-ok) (= :superseded (:state doc))) [:edited (assoc base :fields [])]
      (not content-ok) (if (and (some? recorded-hash) (= recorded-hash on-disk-hash))
                         [:reformatted (assoc base :fields-ok true)]
                         [:edited (assoc base :fields changed)])
      (:error recorded) [:unextractable (assoc base :error (:error recorded))]
      (= :inconsistent (:status pc)) [:contract-inconsistent base]
      (= :parser-unavailable (:status pc)) [:parser-unavailable base]
      contract-moved? [:contract-moved base]
      :else
      (let [rebuilt (when verified-text
                      (err/rescue-any (fn [_] nil) (tdocs/rebuilt-root host doc verified-text)))
            root-mismatch? (and (some? (:merkle-root doc)) (some? rebuilt)
                                (not= (:merkle-root doc) rebuilt))
            hash-mismatch? (and (some? (:recorded-content-hash doc)) (some? now-hash)
                                (not= (:recorded-content-hash doc) now-hash))]
        (if (or root-mismatch? hash-mismatch?)
          [:rebuild-mismatch
           (cond-> (assoc base
                          :fields-ok (and (some? recorded-hash) (= recorded-hash now-hash))
                          :registered-root (:merkle-root doc)
                          :rebuilt-root rebuilt)
             hash-mismatch? (assoc :registered-content-hash (:recorded-content-hash doc)
                                   :rebuilt-content-hash now-hash))]
          (if-let [n (noise-of verified-text)] (noisy base n) [:ok base]))))))

(defn- diagnose [store env shared read-file doc]
  (let [descriptor (or (tdocs/contract-descriptor store doc)
                       (some->> (:model doc) (mstore/find-model store) :descriptor))
        text (when (:path doc) (read-file (:path doc)))
        [category extras] (classify store env doc descriptor text)
        ;; a short name shared by several documents is an ambiguous alias (K8): commands get the qualified name
        token (if (contains? shared (:name doc)) (:qualified doc) (:name doc))
        f (merge {:category category :model (:model doc) :document (:name doc) :qualified (:qualified doc) :tree (:tree doc)
                  :token token :path (:path doc) :state (:state doc)
                  :needs-human (not (or (clean category) (safe category)))
                  :inspect (if (= :contract-moved category) (str "model `" (:model doc) "` (kimun models history " (:model doc) "), not this document")
                               (or (:path doc) (str "kimun docs show " token)))
                  :action (action-of category token)}
                 extras)]
    (dissoc (assoc f :message (message-of f)) :token)))

(defn- diagnose-generic
  "The finding of generic document `doc` (K3): its file against the recorded
   fingerprint, its structure against the registered root when the text
   changed or to detect `rebuild-mismatch` (`10 §11.1`), and its parse contract
   against the one in force."
  [store env shared read-file doc]
  (let [host (:host store)
        text (when (:path doc) (read-file (:path doc)))
        content-ok (and (some? text) (= (:fingerprint doc) (tdocs/fingerprint host text)))
        pc (parse-contract/compare-recorded host env (:parse-contract doc) (:class doc))
        rebuilt (when (and (some? text)
                           (or (not content-ok) (contains? #{:same :unrecorded} (:status pc))))
                  (err/rescue-any (fn [_] nil) (tdocs/generic-root host doc text)))
        root-mismatch? (and content-ok
                            (contains? #{:same :unrecorded} (:status pc))
                            (some? (:merkle-root doc))
                            (some? rebuilt)
                            (not= (:merkle-root doc) rebuilt))
        category (cond
                   (nil? text) :missing
                   (not content-ok) (if (= (:merkle-root doc) rebuilt)
                                      :reformatted
                                      :edited)
                   (= :inconsistent (:status pc)) :contract-inconsistent
                   (= :parser-unavailable (:status pc)) :parser-unavailable
                   (= :changed (:status pc)) :contract-moved
                   root-mismatch? :rebuild-mismatch
                   :else :ok)
        who (str "document `" (:name doc) "` (" (:class doc) ")")
        ;; a shared short name is an ambiguous alias (K8): the command then names the tree
        update (str "kimun docs update " (:path doc) " --name "
                    (if (contains? shared (:name doc)) (:tree doc) (:name doc)))]
    (cond-> {:category category :model nil :class (:class doc) :generic true
             :document (:name doc) :qualified (:qualified doc) :tree (:tree doc) :path (:path doc) :state :intact
             :needs-human (not (or (clean category) (safe category)))
             :inspect (:path doc)
             :action (when-not (= :ok category) (if (= :missing category) nil update))
             :content-ok (boolean content-ok)
             :fields-ok (and (boolean content-ok) (not= :rebuild-mismatch category))
             :contract-ok (contains? #{:same :unrecorded} (:status pc))
             :contract (select-keys pc [:status :changed])
             :message (case category
                        :ok "ok"
                        :missing (str who ": registered at " (:path doc) ", not on disk — restore the file if it was moved by accident")
                        :reformatted (str who ": the text of " (:path doc) " changed, its structure is identical (reformatting) — safe to run: " update)
                        :edited (str who ": " (:path doc) " was edited outside the store — decide first: if the edit was intended, " update)
                        :contract-moved (str who ": the contract it is read under is not the one it was recorded under ["
                                             (str/join ", " (map name (:changed pc))) "], not the document — safe to run: " update)
                        :parser-unavailable (str who ": it was recorded under a parser that cannot be used here; nothing says the document changed — build the pod (bb pod), or accept the parser in force with " update)
                        :contract-inconsistent (str who ": its recorded parse-contract digest does not match its recorded components — inspect it, then " update)
                        :rebuild-mismatch (str who ": rebuilding " (:path doc) " under its recorded contract produced root "
                                               rebuilt ", not the registered root " (:merkle-root doc)
                                               " (its text and contract are unchanged) — decide first: inspect the parser or re-register with " update))}
      (= :rebuild-mismatch category)
      (assoc :registered-root (:merkle-root doc) :rebuilt-root rebuilt))))

(defn- model-finding [docs {:keys [name version]}]
  (let [mine (filter #(= name (:model %)) docs)
        moved (filterv #(= :superseded (:state %)) mine)
        ok? (empty? moved)]
    {:category (if ok? :ok :contract-moved) :model name :version version :documents (count mine) :contract-ok ok?
     :needs-human false
     :inspect (if ok? (str (count mine) " documents tracked under " name) (str "kimun models history " name))
     :action (when-not ok? (str "kimun models define " name " --backfill"))
     :message (if ok? "ok"
                  (str "model `" name "`: its contract (fields or template) changed since " (count moved)
                       " of its documents were extracted (" (str/join ", " (map :document moved))
                       ") — kimun stores update re-extracts them, as does redefining the model with --backfill"))}))

(defn- summary [valid? docs models counts corrupt]
  (str/join " — " (concat [(str (if valid? "PASS" "FAIL") ": store integrity") (str (count docs) " documents in " (count models) " models")]
                          (for [[c label] [[:edited "edited"] [:missing "missing"] [:unextractable "unextractable"] [:noise "noisy"] [:orphan "orphan"]
                                           [:reformatted "reformatted (ok)"] [:contract-moved "contract moved"]
                                           [:rebuild-mismatch "rebuild mismatch"]]
                                :when (pos? (get counts c 0))]
                            (str (get counts c) " " label))
                          (when corrupt [(str (count (:bad corrupt)) " corrupt objects")]))))

(defn report
  "The diagnosis of `store`. `read-file` is `(fn [relative-path] text-or-nil)`;
   `opts` may carry `:verify`, the result of `kimun.kernel.store/verify`
   (`{:ok? :checked :bad}`), which adds the store-level finding `:corrupt`.
   A document finding also carries `:qualified` and `:tree`; when several
   documents share a short name, `:action` and the message name each by its
   qualified name, so the repair command is never ambiguous (K8).
   Returns `{:valid :summary :checked :counts {category n} :models [finding]
   :documents [finding] :findings [finding]}`: `:documents` has one finding per
   tracked document (`:ok` included), `:findings` only what is not `:ok`
   (corruption first, then documents). A finding is `{:category :model
   :document :path :state :action :message :inspect :needs-human :content-ok
   :fields-ok}` plus `:fields` (edited), `:error` (unextractable), `:noise`
   and `:undeclared` (the noisy headings as written; v1 `undeclared`).
   When `:verify` reports damage and the documents can no longer be read
   through the damaged objects, `:documents` and `:models` are empty and the
   `:corrupt` finding stands alone (the report never raises for damage).
   `:valid` is false when anything is neither `:ok` nor `:reformatted` —
   except `:noise` on a file that still matches what was recorded, which is
   reported and asks for a human but does not block (v1: noise already tracked
   is reported without blocking); noise brought in by a hand edit does block."
  ([store read-file] (report store read-file {}))
  ([store read-file {:keys [verify]}]
   (let [damaged? (and verify (not (:ok? verify)))
         env (parse-contract/environment)
         ;; a store with damaged objects may not be readable at all (e.g. a lost mmap node of the
         ;; head Revision): the corruption is the finding, the documents are diagnosed only if they still can be
         [docs models] (if damaged?
                         (err/rescue-any (fn [_] [[] []])
                                         (let [all (tdocs/documents store)
                                               generic (tdocs/generic-documents store)
                                               shared (set (keep (fn [[n c]] (when (> c 1) n))
                                                                 (frequencies (map :name (concat all generic)))))
                                               ds (into (mapv #(diagnose store env shared read-file %) all)
                                                        (map #(diagnose-generic store env shared read-file %))
                                                        generic)]
                                           (walk/postwalk identity [ds (mapv #(model-finding ds %) (mstore/models store))])))
                         (let [all (tdocs/documents store)
                                               generic (tdocs/generic-documents store)
                                               shared (set (keep (fn [[n c]] (when (> c 1) n))
                                                                 (frequencies (map :name (concat all generic)))))
                                               ds (into (mapv #(diagnose store env shared read-file %) all)
                                                        (map #(diagnose-generic store env shared read-file %))
                                                        generic)]
                           [ds (mapv #(model-finding ds %) (mstore/models store))]))
         corrupt (when (and verify (not (:ok? verify)))
                   {:category :corrupt :model nil :document nil :path nil :needs-human true :action (action-of :corrupt nil)
                    :inspect "kimun stores verify" :bad (vec (:bad verify))
                    :message (str (count (:bad verify)) " object(s) do not hash to their name — kimun stores verify names them; restore the store from a clone or backup")})
         counts (frequencies (map :category docs))
         valid? (and (nil? corrupt) (every? #(or (clean (:category %)) (and (= :noise (:category %)) (:content-ok %))) docs))]
     {:valid valid? :summary (summary valid? docs models counts corrupt) :checked (count docs) :counts counts
      :models models :documents docs
      :findings (into (if corrupt [corrupt] []) (remove #(= :ok (:category %))) docs)})))

(defn health
  "The verdict of a `report`: `{:status :findings :actions :next}`. `:status`
   is `:ok` (no finding), `:degraded` (every finding is repaired by a command)
   or `:needs-human` (at least one needs a decision first). `:findings` are
   `{:category :model :document :path :action :needs-human}`, `:actions` the
   distinct commands in order, and `:next` says what comes first: `run: …`
   when a command is enough, `inspect: …` when a human must look."
  [{:keys [findings]}]
  (let [human (filter :needs-human findings)]
    {:status (cond (empty? findings) :ok (seq human) :needs-human :else :degraded)
     :findings (mapv #(select-keys % [:category :model :document :path :action :needs-human]) findings)
     :actions (vec (distinct (keep :action findings)))
     :next (cond (empty? findings) nil
                 (seq human) (str "inspect: " (str/join ", " (distinct (map :inspect human))))
                 (= 1 (count findings)) (str "run: " (:action (first findings)))
                 :else "run: kimun stores update")}))
