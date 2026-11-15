(ns kimun.surface.signals
  "Read-only sensorization signals derived from diagnostics (first cut).

   A signal is a derived observation of the current revision, not an identity,
   not a stored event and not an action. It names what the existing
   `kimun.surface.diagnostics/report` already found, in a stable shape a script
   or an LLM can consume. The first cut is deliberately one observation: no
   daemon, no heartbeat, no writes."
  (:require [clojure.string :as str]))

(def category->type
  "Diagnostic category → signal type. The vocabulary is derived from the
   categories of `kimun.surface.diagnostics`; it does not invent a truth value.
   K6's `source-changed` is the `:source/*` family, its `contract-changed` is
   `:contract/changed`, its `parser-unavailable` is
   `:contract/parser-unavailable` and its `rebuild-mismatch` is
   `:contract/rebuild-mismatch`; `:detail` carries `:content-ok` and
   `:contract-ok` (and `:registered-root` / `:rebuilt-root` on mismatch) so a
   consumer sees which of the two moved, or whether rebuilding itself diverged."
  {:corrupt :store/corrupt
   :edited :source/mutated
   :reformatted :source/reformatted
   :missing :source/missing
   :contract-moved :contract/changed
   :parser-unavailable :contract/parser-unavailable
   :contract-inconsistent :contract/inconsistent
   :rebuild-mismatch :contract/rebuild-mismatch
   :unextractable :source/unreadable
   :noise :source/noise
   :orphan :model/missing})

(def severity-rank
  "Order used to sort signals; critical first."
  {:critical 0 :warning 1 :info 2})

(defn- type-of
  "Signal type of a diagnostic finding, or `:diagnostic/unknown`."
  [finding]
  (get category->type (:category finding) :diagnostic/unknown))

(defn- severity-of
  "Severity of a diagnostic finding: corruption is critical; a finding that
   needs a human is a warning; the rest are informational."
  [finding]
  (cond
    (= :corrupt (:category finding)) :critical
    (:needs-human finding) :warning
    :else :info))

(defn- detail-of
  "The diagnostic extras worth carrying in the signal, without the message or
   the repair command (both are already top-level)."
  [finding]
  (not-empty (select-keys finding [:fields :error :undeclared :bad :state
                                   :content-ok :fields-ok :path-exists
                                   :contract-ok :contract
                                   :registered-root :rebuilt-root
                                   :registered-content-hash :rebuilt-content-hash])))

(defn from-finding
  "One structured signal from one diagnostic finding. It keeps the category,
   the affected document/model/path, the repair action, `:needs-human`, the
   message and a `:detail` map; it adds no identity and no confidence."
  [finding]
  (cond-> {:type (type-of finding)
           :category (:category finding)
           :severity (severity-of finding)
           :document (:document finding)
           :model (:model finding)
           :path (:path finding)
           :action (:action finding)
           :needs-human (boolean (:needs-human finding))
           :message (:message finding)}
    (detail-of finding) (assoc :detail (detail-of finding))))

(defn from-report
  "Structured signals from a `kimun.surface.diagnostics/report`. Signals are
   ordered by severity (critical, warning, info), then type, document and path,
   so the same observation produces the same order on every host."
  [report]
  (->> (:findings report)
       (map from-finding)
       (sort-by (juxt (comp severity-rank :severity) :type :document :path))
       vec))

(defn counts
  "`{:total n :by-type {…} :by-severity {…}}` of a signal vector, with sorted
   maps so the rendered output is stable."
  [signals]
  {:total (count signals)
   :by-type (into (sorted-map) (frequencies (map :type signals)))
   :by-severity (into (sorted-map) (frequencies (map :severity signals)))})

(defn summary
  "One line describing a signal vector: `no signals` or `n signal(s) — x
   critical, y warning, z info`."
  [signals]
  (if (empty? signals)
    "no signals"
    (let [{:keys [by-severity]} (counts signals)]
      (str (count signals) " signal(s) — "
           (str/join ", " (for [s [:critical :warning :info]
                                :let [n (get by-severity s 0)]
                                :when (pos? n)]
                            (str n " " (name s))))))))
