# Lane B — executability (round 1)

## Overview

Audit of task-milestone-6-stand-off-below-the-paragraph for zero-context executor readiness. Walk sections A–M against task + pill + atoms + code reference surfaces.

---

## Section A: Goal unambiguous ✓

**Finding:** Goal is concrete and measurable.

- "Implement milestone 6 stand-off below the paragraph (docs/v2/02 sections 4 and 4.1)"
- Scope: TextSegmenter port + words/sentences, host adapter (JVM + cljs), new standoff.cljc namespace, virtual address resolver, no materialized nodes
- Exit criterion: address down to grapheme with no materialized nodes; bb lint/test/oracle green with standoff_test.cljc

**Status:** ✓ PASS

---

## Section B: Scope bounded (in/out) ✓

**Finding:** Scope is explicit with clear boundaries.

In scope:
- Extend TextSegmenter protocol: words, sentences returning grapheme-offset ranges
- Host adapter: java.text.BreakIterator (JVM), Intl.Segmenter (cljs) with UAX 29 grapheme clusters
- New namespace: src/sldb/kernel/standoff.cljc
- Virtual address resolver: (store, leaf-id, start, end) → text or err
- Layers derived on demand, cached by leaf id, memoized

Out of scope:
- Span node kind and anchor-state validation (already exist in node.cljc, anchor.cljc)
- Syntax/mention layers (later milestones)
- Changes to canonical bytes, revision, or store

**Status:** ✓ PASS

---

## Section C: Files exact and specified ✓

**Finding:** Task lists files to create/modify with no ambiguity.

Task declares:
```
files:
  - src/sldb/kernel/ports.cljc
  - src/sldb/host/text.cljc
  - src/sldb/kernel/standoff.cljc
  - test/sldb/kernel/standoff_test.cljc
```

Status of reference surfaces:
- `src/sldb/kernel/ports.cljc` ✓ exists; contains TextSegmenter protocol with only graphemes method
- `src/sldb/host/text.cljc` ✓ exists; contains graphemes function; ready for words/sentences addition
- `src/sldb/kernel/node.cljc` ✓ exists; span node kind exists (line 13: `:span`)
- `src/sldb/kernel/anchor.cljc` ✓ exists; anchor-state validation logic present (resolves? checks span ranges)
- `src/sldb/kernel/standoff.cljc` ✗ does not exist (new file to create)
- `test/sldb/kernel/standoff_test.cljc` ✗ does not exist (new test file to create)

**Status:** ✓ PASS

---

## Section D: Dependencies available and documented ✓

**Finding:** Task implementation path declares dependency ring compliance.

Task states:
> "Build src/sldb/kernel/standoff.cljc that only depends on kernel/ports, kernel/revision and kernel/err (respect the dependency rings, bb lint enforces no outward require and docstrings on public vars)."

Dependency ring validation:
- `sldb.kernel.*` may require: `clojure.*`, `sldb.kernel.*` (check_rings.clj line 13)
- `sldb.kernel.ports` ✓ exists; defines TextSegmenter, Hasher, TextNormalizer, IdMinter protocols; segmenter/hasher/text/ids accessors
- `sldb.kernel.revision` ✓ exists; provides empty-store, get-object, revision, apply-plan functions
- `sldb.kernel.err` ✓ exists; provides raise, rescue for error handling
- Forbidden: reader conditionals in kernel (not sldb.kernel.err)
- Required: ns docstring + docstring on all public vars (defn, defmacro, defprotocol, def, defrecord)

Port signatures present in anchor.cljc (resolves? line 15-27) show the pattern:
```clojure
(count (ports/graphemes (ports/segmenter (:host (:store c))) ...))
```

**Status:** ✓ PASS

---

## Section E: Validation runnable ✓

**Finding:** Validation commands are specified and executable.

Task declares:
```
Validation:
  - bb lint
  - bb test
  - bb oracle
```

From bb.edn:
- `bb lint` → calls `scripts/check_rings.clj` (dependency rings + docstrings)
- `bb test` → runs all `test/**/*_test.cljc` namespaces (clojure.test + test.check)
- `bb oracle` → calls `scripts/canon_oracle.py` (Python re-implementation of canonical-bytes, checks frozen node ids)

Test framework: clojure.test (deftest, is, testing) with reconcile_test.cljc and anchor_test.cljc as reference styles.

**Status:** ✓ PASS

---

## Section F: Done When observable ✓

**Finding:** Completion conditions are objective and measurable.

Task states:
> "bb lint, bb test and bb oracle are all green with new tests in test/sldb/kernel/standoff_test.cljc that prove:
> 1. word and sentence offsets are grapheme-based and deterministic
> 2. layers are cached by leaf id
> 3. a virtual address resolves to the correct substring with no node materialized
> 4. out-of-range or unresolved leaf returns a typed err

Evidence lives under runs/subagents and task closes with deskops closeout commit."

Observable conditions:
- Exit code 0 for `bb lint` (no ring or docstring violations)
- Exit code 0 for `bb test` (all tests pass, zero failures)
- Exit code 0 for `bb oracle` (canonical-bytes matches)
- standoff_test.cljc present and executes with proof of:
  - Deterministic word/sentence segmentation
  - Caching by leaf id
  - Virtual address resolution without node materialization
  - Typed error on out-of-range or unresolved leaf

**Status:** ✓ PASS

---

## Section G: No missing contract ✓

**Finding:** Atoms and pill provide sufficient contract detail.

Pill `pill-guardrail-v2-implementation-gate`:
- Applies to v2 first-slice implementation tasks
- Ritual: `desk/rituals/ritual-zero-context-audit-gate.md`
- Gate must be clean before execution
- Evidence in `runs/subagents/`

Atoms cited by task (4 atoms):

1. **atom-stand-off-annotation-below-the-paragraph** ✓
   - Answer: Below paragraph, plain text + grapheme offsets are the canonical leaf
   - Inline markup, UAX #29 graphemes/words/sentences are typed span layers
   - Deterministic layers derived on demand, cached by leaf hash, no nodes until referenced
   - Unifies sentence, phrase, mention, fact argument

2. **atom-stand-off-addresses-are-virtual-until-referenced** ✓
   - Answer: Address is [leaf-id start end] in grapheme offsets over leaf NFC text
   - Virtual: resolves without node
   - :span node materialized only when edge needs endpoint
   - Deterministic layers are offset arrays cached by leaf id, not nodes
   - Leaf change: spans follow succession rules

3. **atom-anchor-state-is-derived-and-computed-per-endpoint** ✓
   - Answer: Anchor state computed per endpoint, combined intact < superseded < orphan
   - Resolving for spans: recursing to leaf, checking range against grapheme count
   - Every anchor query derived, never persisted, reproducible from log

4. **atom-inline-marks-live-on-the-block-text-leaves-stay-plain** ✓
   - Answer: Heading/paragraph → :sign/:block with marks in :attrs
   - Single :sign/:text child holds plain NFC text without markup
   - Marks are [kind start end] spans in grapheme offsets

Existing code references (sections 2.1, 3.1, 6.2 in docs/v2/02):
- Section 4: Stand-off below paragraph, layers (graphemes, words, sentences), virtual addresses
- Section 4.1: Address format [leaf-id start end], materialization rules, caching
- Section 6.2: Anchor resolution for spans checks grapheme count (anchor.cljc line 19-21 shows the pattern)

**Status:** ✓ PASS

---

## Section H: No guessed paths, vars, or commands ✓

**Finding:** All path and function references are exact or follow established pattern.

File paths:
- `src/sldb/kernel/ports.cljc` ✓ exact, verified
- `src/sldb/host/text.cljc` ✓ exact, verified
- `src/sldb/kernel/standoff.cljc` ✓ exact, follows kernel namespace pattern
- `test/sldb/kernel/standoff_test.cljc` ✓ exact, follows test pattern (anchor_test.cljc, reconcile_test.cljc)
- `docs/v2/02-sustrato-computacional.md` ✓ exact, verified section 4, 4.1

Existing protocols/functions to use:
- `ports/TextSegmenter` protocol ✓ exists, needs words/sentences methods
- `ports/graphemes` ✓ exists, returns vector of grapheme strings
- `ports/segmenter` ✓ exists, accessor (line 42 in ports.cljc)
- `host/graphemes` ✓ exists, JVM: BreakIterator, cljs: Intl.Segmenter
- `err/raise` ✓ exists, signature: (type msg data)
- `err/rescue` ✓ exists, macro for try/catch ex-info

Commands:
- `bb lint` ✓ exact, in bb.edn
- `bb test` ✓ exact, in bb.edn
- `bb oracle` ✓ exact, in bb.edn

**Status:** ✓ PASS

---

## Section I: Atoms sufficient to implement ✓

**Finding:** Atoms provide sufficient detail for implementation without fill-in or guess.

Implementation pathway from atoms + docs + existing code:

1. **Protocol extension** (ports.cljc):
   - Add `words [this s]` method to TextSegmenter → returns vector of [start end] ranges in grapheme offsets
   - Add `sentences [this s]` method to TextSegmenter → returns vector of [start end] ranges in grapheme offsets
   - Rationale: atom-stand-off-annotation-below-the-paragraph says "UAX #29 grapheme, word and sentence boundaries (deterministic, derived on demand and cached by leaf hash)"

2. **Host adapter** (text.cljc):
   - Implement words: JVM java.text.BreakIterator.getWordInstance(), cljs Intl.Segmenter with granularity "word"
   - Implement sentences: JVM java.text.BreakIterator.getSentenceInstance(), cljs Intl.Segmenter with granularity "sentence"
   - Offsets in grapheme clusters: convert BreakIterator/Intl byte/code-unit offsets to grapheme indices using existing graphemes function
   - Return vectors of [start end] pairs in grapheme offsets
   - Rationale: atom-stand-off-annotation-below-the-paragraph, implementation path "using java.text.BreakIterator on JVM and Intl.Segmenter on cljs, with offsets counted in UAX 29 grapheme clusters not UTF-16 code units"

3. **standoff.cljc namespace**:
   - Dependency scope: require sldb.kernel.ports, sldb.kernel.revision, sldb.kernel.err (per task statement)
   - Pure derivation function: (graphemes|words|sentences leaf-text) → vector of offset arrays, e.g., [[0 3] [3 6] [6 9]] for "El cielo es azul." word boundaries
   - Memoization: cache by leaf id (leaf-id → layer-name → ranges), use a map or fn-with-cache pattern
   - Virtual address resolver: (store leaf-id start end) → substring or err
     - Look up leaf node by id in store
     - Verify leaf is a :text node
     - Verify range [start end] does not exceed leaf grapheme count
     - Extract substring using (apply str (subvec (ports/graphemes segmenter (:text content)) start end))
     - Return err if leaf not found, not a text node, or range out of bounds
   - Rationale: atom-stand-off-addresses-are-virtual-until-referenced "An address is [leaf-id start end] in UAX #29 grapheme offsets over the leaf's NFC text and resolves without any node"

4. **Test file** (standoff_test.cljc):
   - Style: follow anchor_test.cljc and reconcile_test.cljc pattern
   - Setup: (def h host/host), (def caps {"jp" :all}), helper seed plans
   - Test cases:
     - Deterministic word/sentence ranges: "El cielo es azul." → [[0 2] [3 8] [9 11] [12 17]] words (example)
     - Grapheme-based, not UTF-16: test with combining marks (e.g., "e" + combining acute = 1 grapheme)
     - Caching by leaf id: call layers twice on same leaf, verify return same refs (if memoized)
     - Virtual address resolves to correct substring: address [leaf-id 3 8] → "cielo"
     - Out-of-range returns typed err: address [leaf-id 0 1000] → :standoff/out-of-range
     - Unresolved leaf returns typed err: address [unknown-id 0 1] → :standoff/leaf-not-found
     - No node materialized during resolution (verify pool unchanged)
   - Rationale: Done When criterion requires proof of grapheme-based/deterministic, cached, virtual addresses, typed err

**Status:** ✓ PASS

---

## Section J: Pill applies ✓

**Finding:** Pill context is exactly applicable.

Pill `pill-guardrail-v2-implementation-gate`:
- When: "Whenever a task tagged for the v2 first slice sits on its -execution-ready node"
- Where: "desk/tasks/task-implement-v2-*.md, docs/v2/, desk/atoms/atom-*.md with epoch:v2, runs/subagents/*-zero-context-audit/"
- How: "Run the zero-context audit gate with cheap fresh-context lanes; fix atoms, docs and tasks through deskops; commit the runs/ evidence; then deskops advance task"

Task status: task-milestone-6-stand-off-below-the-paragraph, current node: checklist-task-milestone-6-stand-off-below-the-paragraph-execution-ready

Pill is v2 implementation epoch; task is epoch v2 first slice (docs/v2/02 sections 4 and 4.1). Ritual is zero-context audit gate (this lane).

**Status:** ✓ PASS

---

## Section K: No contradiction with existing code ✓

**Finding:** Proposed additions do not contradict existing surface contracts.

Existing TextSegmenter protocol (ports.cljc, line 27-29):
```clojure
(defprotocol TextSegmenter
  "Grapheme segmentation (UAX #29), the unit of every stand-off offset (docs/v2/04 §2)."
  (graphemes [this s] "Vector of grapheme-cluster strings of `s`, in order; (apply str v) == s."))
```

New methods (words, sentences) are additive, not modifying graphemes contract. No contradiction.

Existing Segmenter record (text.cljc, line 24-27):
```clojure
(defrecord Segmenter []
  ports/TextSegmenter
  (graphemes [_ s] (graphemes s)))
```

New implementation of words/sentences methods will extend same record. No contradiction.

Existing node shapes (node.cljc, line 5-13):
- `:span` kind for :sign class: `{:leaf <id> :range [a b]}`
- Existing validation: integers, vector of 2, 0 ≤ a ≤ b
- New standoff.cljc does not create :span nodes during resolution; it validates ranges against grapheme counts (matching the same logic in anchor.cljc resolves? line 19-21)

No contradiction.

Existing anchor resolves? (anchor.cljc, line 15-27):
```clojure
(= :span (:kind n))
(let [{:keys [leaf range]} (:content n)
      lf (revision/get-object (:store c) leaf)]
  (and (resolves? c leaf)
       (map? lf) (= :text (:kind lf))
       (<= (second range)
           (count (ports/graphemes (ports/segmenter (:host (:store c)))
                                   (get-in lf [:content :text]))))))
```

New standoff.cljc resolver will replicate this pattern for virtual addresses. No contradiction.

**Status:** ✓ PASS

---

## Section L: Test target named ✓

**Finding:** Test file and naming convention are explicit.

Task specifies:
> "Write test/sldb/kernel/standoff_test.cljc following the style of anchor_test.cljc and reconcile_test.cljc."

Test file path: `test/sldb/kernel/standoff_test.cljc` ✓ exact

Namespace pattern: `sldb.kernel.standoff-test` (following Clojure convention: file `standoff_test.cljc` → namespace `sldb.kernel.standoff-test`)

Reference tests exist:
- `test/sldb/kernel/anchor_test.cljc` ✓ namespace: `sldb.kernel.anchor-test`
- `test/sldb/kernel/reconcile_test.cljc` ✓ namespace: `sldb.kernel.reconcile-test`

bb.edn test task (line 25) auto-discovers all `test/**/*_test.cljc` and converts to namespaces:
```clojure
(->ns (fn [p]
  (-> (str (fs/relativize "test" p))
      (str/replace (re-pattern "\\.cljc$") "")
      (str/replace "_" "-")
      (str/replace fs/file-separator "."))))
```

So `test/sldb/kernel/standoff_test.cljc` → `sldb.kernel.standoff-test` → auto-loaded by `bb test`

**Status:** ✓ PASS

---

## Section M: Ring and docstring constraints stated ✓

**Finding:** Constraints are explicit and enforced by bb lint.

Task states:
> "Build src/sldb/kernel/standoff.cljc that only depends on kernel/ports, kernel/revision and kernel/err (respect the dependency rings, bb lint enforces no outward require and docstrings on public vars)."

Ring enforcement (check_rings.clj):
- Namespace `sldb.kernel.*` may require: `clojure.*`, `sldb.kernel.*`
- Task limits to: `sldb.kernel.ports`, `sldb.kernel.revision`, `sldb.kernel.err`
- Subset is safe; bb lint will fail if any other kernel.* or host.* required

Docstring enforcement (check_rings.clj line 45-59):
- All public defn, defmacro, defprotocol must have docstring (not just ; comment)
- All public def with 4+ elements: element 3 must be string
- All public defrecord: automatic (no docstring check, but recommended)
- Protocol methods must have docstring

Task does not create protocol; if standoff.cljc exports functions, each must have docstring.

bb test depends on bb lint (bb.edn line 21: `:depends [lint]`), so lint runs first; exit 1 if docstring missing.

**Status:** ✓ PASS

---

## Summary

| Section | Finding | Status |
|---------|---------|--------|
| A. Goal unambiguous | Concrete, measurable exit criterion | ✓ PASS |
| B. Scope bounded | In/out explicitly stated | ✓ PASS |
| C. Files exact | Task + reference code match | ✓ PASS |
| D. Dependencies available | kernel/ports, kernel/revision, kernel/err exist | ✓ PASS |
| E. Validation runnable | bb lint, bb test, bb oracle are defined | ✓ PASS |
| F. Done When observable | Objective completion conditions | ✓ PASS |
| G. No missing contract | 4 atoms + pill sufficient | ✓ PASS |
| H. No guessed paths/vars | All names verified or pattern-matched | ✓ PASS |
| I. Atoms sufficient | Implementation pathway clear | ✓ PASS |
| J. Pill applies | v2 implementation gate, -execution-ready node | ✓ PASS |
| K. No code contradiction | Additive to existing; validates same invariants | ✓ PASS |
| L. Test target named | test/sldb/kernel/standoff_test.cljc auto-discovered | ✓ PASS |
| M. Ring/docstring constraints | bb lint enforces; task declares scope | ✓ PASS |

---

## Findings

None.

---

## Residual Risks

None identified.

---

## FINAL VERDICT: ready

All audit sections A–M pass. Task specification is complete, unambiguous, bounded, and contains sufficient contract for a blind zero-context executor to implement without asking any question.

---

## Acceptance Report

```acceptance-report
{
  "criteriaSatisfied": [
    {
      "id": "criterion-1",
      "status": "satisfied",
      "evidence": "Lane B executability audit on task-milestone-6-stand-off-below-the-paragraph: all 13 audit sections (A–M) pass. Goal is concrete, scope bounded with clear in/out, files exact with reference surfaces verified, dependencies available (kernel/ports, kernel/revision, kernel/err), validation runnable (bb lint/test/oracle), Done When observable, contract complete (4 atoms + pill), no guessed paths or vars, atoms sufficient for implementation pathway, pill applies to v2 execution-ready node, no contradictions with existing code, test target named and auto-discovered, ring/docstring constraints stated. Zero findings at high or medium severity."
    }
  ],
  "changedFiles": [],
  "testsAddedOrUpdated": [],
  "commandsRun": [],
  "validationOutput": [],
  "residualRisks": [],
  "noStagedFiles": true,
  "diffSummary": "Read-only audit; no files modified.",
  "reviewFindings": [
    "no blockers"
  ],
  "manualNotes": "Task-milestone-6-stand-off-below-the-paragraph is ready for execution. Blind executor can implement from task + pill + 4 atoms without questions. Implementation path is clear: (1) extend TextSegmenter protocol with words/sentences methods returning grapheme-offset ranges, (2) implement in host/text.cljc using BreakIterator (JVM) and Intl.Segmenter (cljs), (3) create standoff.cljc with pure derivation and memoization by leaf id plus virtual address resolver, (4) write standoff_test.cljc proving grapheme-determinism, caching, and virtual address resolution with typed errors. Validation: bb lint (dependency rings + docstrings), bb test (clojure.test suite), bb oracle (Python canonical-bytes oracle). No contradictions with existing anchor.cljc span validation logic; both check range against grapheme count."
}
```
