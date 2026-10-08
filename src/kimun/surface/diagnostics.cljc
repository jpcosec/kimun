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
   - `:contract-moved` (contract_mutation) the model was redefined and the
                       document's text is untouched — safe: `docs update NAME`
                       or redefining the model with `--backfill`;
   - `:unextractable`  the text no longer extracts under the model's contract;
   - `:noise`          the text (the file's when it was edited, else the
                       recorded source) carries headings or frontmatter keys
                       the model does not declare (`kimun.surface.models.noise`);
   - `:orphan`         the document's model is gone.
   A store-level finding `:corrupt` (v1's tampered hash) comes from the
   kernel's verification: `kimun stores verify` names the objects."
  (:require [clojure.string :as str]
            [clojure.walk :as walk]
            [kimun.kernel.err :as err]
            [kimun.surface.docs :as tdocs]
            [kimun.surface.models.extract :as extract]
            [kimun.surface.models.noise :as noise]
            [kimun.surface.models.store :as mstore]))

(def safe
  "Categories a command repairs without a decision (v1: \"safe to run\")."
  #{:reformatted :contract-moved})

(def clean
  "Categories that keep the store valid (v1: ok and benign_mutation)."
  #{:ok :reformatted})

(defn- try-extract [descriptor text]
  (try {:payload (extract/extract descriptor text)}
       (catch #?(:clj clojure.lang.ExceptionInfo :cljs ExceptionInfo) e {:error (ex-message e)})))

(defn- action-of [category doc-name]
  (case category
    :ok nil
    (:reformatted :edited :contract-moved) (str "kimun docs update " doc-name)
    :missing (str "kimun docs untrack " doc-name)
    :corrupt "kimun stores verify"
    nil))

(defn- message-of [{:keys [category document model path fields error noise]}]
  (let [who (str "document `" document "` (" model ")")]
    (case category
      :ok "ok"
      :reformatted (str who ": the text of " path " changed, its fields are identical (reformatting) — safe to run: kimun docs update " document)
      :edited (str who ": " path " was edited outside the store"
                   (when (seq fields) (str " [fields: " (str/join ", " fields) "]"))
                   " — decide first: if the edit was intended, kimun docs update " document
                   "; if not, restore the file from the recorded source (kimun docs show " document ")")
      :missing (str who ": tracked at " path ", not on disk — decide first: restore the file if it was moved by accident, or kimun docs untrack "
                    document " if it is gone on purpose")
      :contract-moved (str who ": the contract of model `" model "` moved, not the document (its text is unchanged) — safe to run: kimun docs update "
                           document ", or redefine the model with --backfill")
      :unextractable (str who ": its text no longer extracts under the contract of `" model "` (" error
                          ") — fix " (or path "the document") " or the model's template, then kimun docs update " document)
      :noise (str who ": structural noise — " (noise/describe noise) " — remove it from " (or path "the document")
                  " or declare it in the model, then kimun docs update " document)
      :orphan (str who ": its model is no longer defined — define `" model "` again or kimun docs untrack " document))))

(defn- classify
  "`[category extras]` of document `doc` given the text of its file (nil when
   it has none or it is gone) and its model's descriptor."
  [store doc descriptor text]
  (let [host (:host store)
        content-ok (or (nil? (:path doc)) (and (some? text) (= (:fingerprint doc) (tdocs/fingerprint host text))))
        recorded (when (and descriptor (:source doc)) (try-extract descriptor (:source doc)))
        on-disk (when (and descriptor text (not content-ok)) (try-extract descriptor text))
        ;; the recorded payload's hashes live in the anchor edge (format 3): the text is not stored,
        ;; so an edit is told apart from a reformatting by comparing hashes, not payloads
        on-disk-hash (when-let [p (:payload on-disk)] (tdocs/content-hash host p))
        recorded-hash (or (:recorded-content-hash doc)
                          (when-let [p (:payload recorded)] (tdocs/content-hash host p)))
        changed (if (seq (:recorded-field-hashes doc))
                  (let [rh (:recorded-field-hashes doc) now (tdocs/field-hashes host (:payload on-disk))]
                    (vec (sort (keep (fn [k] (when (not= (get rh k) (get now k)) k)) (distinct (concat (keys rh) (keys now)))))))
                  (vec (sort (keys (tdocs/changed-fields (or (:payload recorded) {}) (:payload on-disk))))))
        noise-of (fn [t] (let [n (noise/structural-noise descriptor t {:related (keep :descriptor (map #(mstore/find-model store (:name %)) (mstore/models store)))})]
                           (when (noise/noisy? n) n)))
        noisy (fn [base n] [:noise (assoc base :noise n :undeclared (mapv :raw (:headings n)))])
        base {:content-ok (boolean content-ok) :fields-ok (boolean (and content-ok (:payload recorded) (= :intact (:state doc))))}]
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
      (= :superseded (:state doc)) [:contract-moved base]
      :else (if-let [n (noise-of (:source doc))] (noisy base n) [:ok base]))))

(defn- diagnose [store read-file doc]
  (let [descriptor (or (tdocs/contract-descriptor store doc)
                       (some->> (:model doc) (mstore/find-model store) :descriptor))
        text (when (:path doc) (read-file (:path doc)))
        [category extras] (classify store doc descriptor text)
        f (merge {:category category :model (:model doc) :document (:name doc) :path (:path doc) :state (:state doc)
                  :needs-human (not (or (clean category) (safe category)))
                  :inspect (if (= :contract-moved category) (str "model `" (:model doc) "` (kimun models history " (:model doc) "), not this document")
                               (or (:path doc) (str "kimun docs show " (:name doc))))
                  :action (action-of category (:name doc))}
                 extras)]
    (assoc f :message (message-of f))))

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
                                           [:reformatted "reformatted (ok)"] [:contract-moved "contract moved"]]
                                :when (pos? (get counts c 0))]
                            (str (get counts c) " " label))
                          (when corrupt [(str (count (:bad corrupt)) " corrupt objects")]))))

(defn report
  "The diagnosis of `store`. `read-file` is `(fn [relative-path] text-or-nil)`;
   `opts` may carry `:verify`, the result of `kimun.kernel.store/verify`
   (`{:ok? :checked :bad}`), which adds the store-level finding `:corrupt`.
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
         ;; a store with damaged objects may not be readable at all (e.g. a lost mmap node of the
         ;; head Revision): the corruption is the finding, the documents are diagnosed only if they still can be
         [docs models] (if damaged?
                         (err/rescue-any (fn [_] [[] []])
                                         (let [ds (mapv #(diagnose store read-file %) (tdocs/documents store))]
                                           (walk/postwalk identity [ds (mapv #(model-finding ds %) (mstore/models store))])))
                         (let [ds (mapv #(diagnose store read-file %) (tdocs/documents store))]
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
