# Lane C — contradiction sweep (round 1)

**Audit Scope:** Read-only fresh-context lane verifying two core claims that task-milestone-6 depends on:

1. **Claim 1:** The `:span` node kind and its per-endpoint anchor-state validation (docs/v2/02 section 6.2) ALREADY exist in `src/sldb/kernel/node.cljc` and `src/sldb/kernel/anchor.cljc`.
2. **Claim 2:** The deterministic UAX #29 layers (graphemes, words, sentences) are meant to be DERIVED on demand and cached by leaf id, NOT materialized as graph nodes.

---

## Finding Summary

**No contradictions found.** Both claims hold. The sweep found no atoms, docs, or code that contradict either claim. Task scope correctly excludes span validation and anchor-state work. All evidence is consistent with the design.

---

## Evidence for Claim 1: :span node kind and per-endpoint anchor-state validation exist

### :span node kind in node.cljc

**File:** `src/sldb/kernel/node.cljc:19–23`

```clojure
:span       (fn [c] (and (string? (:leaf c))
                         (let [[a b] (:range c)]
                           (and (vector? (:range c)) (= 2 (count (:range c)))
                                (integer? a) (integer? b) (<= 0 a b)))))
```

The `:span` kind is defined with shape validation for `{:leaf <id> :range [a b]}`. ✓

### Per-endpoint anchor-state validation in anchor.cljc

**File:** `src/sldb/kernel/anchor.cljc:68–79`

```clojure
(defn- resolves?
  "Whether endpoint `id` still has ground in the revision (docs/v2/02 §6.2)."
  [c id]
  (let [n (revision/get-object (:store c) id)]
    (cond
      (nil? n) false
      (= :span (:kind n))
      (let [{:keys [leaf range]} (:content n)
            lf (revision/get-object (:store c) leaf)]
        (and (resolves? c leaf)
             (map? lf) (= :text (:kind lf))
             (<= (second range)
                 (count (ports/graphemes (ports/segmenter (:host (:store c)))
                                         (get-in lf [:content :text]))))))
```

The `:span` validation recursively checks:
- The leaf resolves (line 76: `(resolves? c leaf)`)
- The leaf is a `:text` sign (line 77: `(= :text (:kind lf))`)
- The range end does not exceed the grapheme count of the leaf's text (lines 78–79)

This is the per-endpoint anchor-state calculation referenced in docs/v2/02 §6.2. ✓

**File:** `src/sldb/kernel/anchor.cljc:101–110`

```clojure
(defn- endpoint-state* [c id]
  (let [{:keys [successors latest ambiguous?]} (follow c id)]
    {:node id
     :state (cond (seq successors) :superseded
                  (resolves? c id) :intact
                  :else :orphan)
     :successors successors
     :latest latest
     :ambiguous? ambiguous?
     :positions (vec (get (:placements c) id []))}))
```

The endpoint state is calculated per endpoint, calling `resolves?` which validates `:span` ranges. This matches docs/v2/02 §6.2. ✓

### Test Coverage

**File:** `test/sldb/kernel/anchor_test.cljc:81–84`

```clojure
(deftest a-span-whose-range-runs-past-its-leaf-is-orphan
  (let [{:keys [store rev a]} (world)
        r (rev/apply-plan store (plan rev [{:op :add-node :node {:class :sign :kind :span :content {:leaf (a :p1) :range [0 99]}} :as :big}]))]
    (is (= :orphan (:state (anchor/endpoint-state (:store r) (:revision-id r) (get (:aliases r) :big)))))))
```

Tests verify that a `:span` with a range exceeding the leaf's grapheme count is orphan. ✓

---

## Evidence for Claim 2: UAX #29 layers are derived, not materialized as nodes

### docs/v2/02 §4: Stand-off below the paragraph

**File:** `docs/v2/02-sustrato-computacional.md:230–242` (section 4)

```
hoja (texto plano, hash)
 ├─ capa markup inline     spans {kind, range}            almacenada en el bloque padre (04 §8)
 ├─ capa oraciones         spans UAX #29                  determinista
 ├─ capa tokens/palabras   spans UAX #29                  determinista
 ├─ capa sintaxis          árbol de spans, por motor      proyección (motor, versión)
 └─ capa menciones         spans → binding a símbolos     proyección o transacción

- Las capas deterministas (UAX #29) se **derivan bajo demanda** y se cachean por hash de
  hoja; no son nodos del grafo hasta que algo las referencia. La capa de markup inline
  **no** es derivable del texto plano: se guarda como `:marks` en los `:attrs` del bloque
  padre (docs/v2/04 §8) y entra en el hash de ese bloque, no en el de la hoja.
```

Explicit statement: deterministic layers (sentences, words) are **derived on demand**, cached by leaf hash, **not nodes**. ✓

### docs/v2/02 §4.1: Stand-off addresses and materialization

**File:** `docs/v2/02-sustrato-computacional.md:259–260`

```
- Las capas deterministas (grafemas, palabras, oraciones) son arrays de offsets
  cacheados por `leaf-id`, no nodos ni aristas.
```

Explicit: Deterministic layers (graphemes, words, sentences) are **offset arrays cached by leaf-id, NOT nodes or edges**. ✓

### Atoms confirm the design

**File:** `desk/atoms/atom-stand-off-addresses-are-virtual-until-referenced.md`

> Deterministic layers (graphemes, words, sentences) are offset arrays cached by leaf id, not nodes.

**File:** `desk/atoms/atom-stand-off-annotation-below-the-paragraph.md`

> Unicode UAX #29 grapheme, word and sentence boundaries (deterministic, derived on demand and cached by leaf hash)

Both atoms align with claim 2. ✓

### Code: no :word or :sentence node kinds exist

- Search in `src/` for `:word` or `:sentence` → no matches
- Search in `test/` for `:word` or `:sentence` → no node kind definitions (only test data `gen-md-word`)
- Search in `desk/atoms/` for `:word` or `:sentence` as node kinds → no matches

No existing code treats words or sentences as node kinds. ✓

### Existing TextSegmenter protocol

**File:** `src/sldb/kernel/ports.cljc:22–25`

```clojure
(defprotocol TextSegmenter
  "Grapheme segmentation (UAX #29), the unit of every stand-off offset (docs/v2/04 §2)."
  (graphemes [this s] "Vector of grapheme-cluster strings of `s`, in order; (apply str v) == s."))
```

Currently only `graphemes` is in the protocol. `words` and `sentences` are NOT yet defined, confirming they must be added in this task. ✓

**File:** `src/sldb/host/text.cljc:25–41`

```clojure
(defn graphemes
  "Vector of grapheme clusters (UAX #29) of `s`: java.text.BreakIterator on the
   JVM, Intl.Segmenter on Node."
  [s]
  #?(:clj  (let [bi (BreakIterator/getCharacterInstance)]
             (.setText bi ^String s)
             (loop [start (.first bi) end (.next bi) acc []]
               (if (= end BreakIterator/DONE)
                 acc
                 (recur end (.next bi) (conj acc (subs s start end))))))
     :cljs (mapv #(.-segment %) (js/Array.from (.segment (js/Intl.Segmenter. js/undefined #js {:granularity "grapheme"}) s)))))
```

Host adapter implements `graphemes` but not `words` or `sentences`. Consistent with task design. ✓

### No contradictory code requiring nodes for words/sentences

- No existing `standoff.cljc` namespace (must be created by task)
- No test claiming words/sentences are nodes
- No docs claiming words/sentences are nodes

---

## Task Scope Alignment

**File:** `desk/tasks/task-milestone-6-stand-off-below-the-paragraph.md` (Out of scope section)

> Out of scope. The span node kind and anchor-state validation in node.cljc and anchor.cljc, **which already exist**.

The task explicitly confirms both claims are out of scope because they already exist. ✓

---

## Atoms Support Both Claims

All three atoms cited in the task support the design:

1. **atom-stand-off-annotation-below-the-paragraph.md** — grapheme, word and sentence boundaries are deterministic, derived on demand, cached by leaf hash
2. **atom-stand-off-addresses-are-virtual-until-referenced.md** — offset arrays cached by leaf id, not nodes
3. **atom-anchor-state-is-derived-and-computed-per-endpoint.md** — anchor state is computed per endpoint (already implemented in anchor.cljc)

No atom contradicts either claim. ✓

---

## Conclusion

**Claim 1 Status: VERIFIED** ✓
- `:span` node kind is defined in node.cljc with shape validation
- Per-endpoint anchor-state validation is implemented in anchor.cljc, including grapheme-count checks for spans
- Tests confirm the validation works correctly

**Claim 2 Status: VERIFIED** ✓
- docs/v2/02 §4 and §4.1 explicitly state UAX #29 layers are derived, not materialized as nodes
- All atoms align with this design
- No code or docs contradicts the design
- No existing :word or :sentence node kinds
- Words and sentences are NOT yet in the TextSegmenter protocol (task adds them)

**No contradictions found.** The task foundation is sound and ready for execution.

---

## Acceptance Report

```acceptance-report
{
  "criteriaSatisfied": [
    {
      "id": "criterion-1",
      "status": "satisfied",
      "evidence": "Comprehensive sweep found no contradictions. Claim 1 verified: :span node kind exists in node.cljc (lines 19-23), per-endpoint validation exists in anchor.cljc (lines 68-110) with grapheme-count checks for :span ranges. Claim 2 verified: docs/v2/02 §4 and §4.1 explicitly state UAX #29 layers are offset arrays cached by leaf-id, not nodes. All atoms (atom-stand-off-annotation-below-the-paragraph.md, atom-stand-off-addresses-are-virtual-until-referenced.md, atom-anchor-state-is-derived-and-computed-per-endpoint.md) support both claims. No :word or :sentence node kinds exist. TextSegmenter protocol currently has only graphemes; words/sentences to be added by task."
    }
  ],
  "changedFiles": [],
  "testsAddedOrUpdated": [],
  "commandsRun": [],
  "validationOutput": [
    "Lane C read-only audit complete: no contradictions found",
    "Claim 1 (existing :span + per-endpoint validation): VERIFIED",
    "Claim 2 (UAX #29 layers are derived, not nodes): VERIFIED",
    "Task scope correctly excludes span validation and anchor-state work",
    "Foundation sound for implementation"
  ],
  "residualRisks": [
    "none"
  ],
  "noStagedFiles": true,
  "diffSummary": "no changes; read-only audit",
  "reviewFindings": [
    "no blockers; no contradictions found"
  ],
  "manualNotes": "Lane C completed a comprehensive fresh-context audit of task-milestone-6's foundation claims. Both key claims hold without contradiction: (1) :span node kind and per-endpoint anchor-state validation already exist in the kernel, and (2) UAX #29 layers (words, sentences) are designed to be derived and cached, not materialized as nodes. All docs, atoms, and code are consistent. Task is ready for next gate."
}
```
