# Executor report — task-milestone-6-stand-off-below-the-paragraph

Milestone 6 (stand-off below the paragraph, docs/v2/02 §4/§4.1) implemented: the
deterministic UAX #29 word and sentence layers are added to the segmenter port and its
host adapter (derived on demand, cached by leaf id, never materialized as nodes), and a
virtual stand-off address `[leaf-id start end]` in grapheme offsets resolves against a
leaf's NFC text without any node existing. `:span` and its §6.2 validation were left as-is.

## Per-file, what I implemented

### src/sldb/kernel/ports.cljc (modified)
Extended the `TextSegmenter` protocol from `graphemes` only to also declare:
- `(words [this s])` — vector of `[start end]` grapheme-offset ranges of the UAX #29 word segments, in order and covering `s`.
- `(sentences [this s])` — same for sentence segments.
Updated the protocol docstring to name §4/§4.1. Each new method carries a docstring
(required by `bb lint`'s protocol-method check).

### src/sldb/host/text.cljc (modified)
- Added `words` and `sentences` functions beside the existing `graphemes`:
  - JVM: `java.text.BreakIterator/getWordInstance` and `/getSentenceInstance`.
  - cljs: `Intl.Segmenter` with granularity `"word"`/`"sentence"`.
- Both convert BreakIterator/Intl **code-unit** offsets to **grapheme** offsets through a
  private `grapheme-offset` closure built from the existing `graphemes` (reuses the
  UAX #29 grapheme counting already present, as the task required). Word/sentence
  boundaries always fall on grapheme boundaries, so the mapping is exact.
- The `Segmenter` record now implements all three `TextSegmenter` methods.
- Namespace docstring updated to say it implements `TextSegmenter` too.

### src/sldb/kernel/standoff.cljc (new)
New kernel namespace, requiring only `kernel/ports`, `kernel/revision`, `kernel/err`
(dependency ring respected; `bb lint` green). Public API, all with docstrings:
- `layers` — `{:graphemes … :words [[a b]…] :sentences [[a b]…]}` for a `:sign/:text`
  leaf, **derived on demand and memoized by leaf id** in a private `cache` atom.
- `cached?` — whether a leaf's layers are currently memoized (used by tests to prove
  memoization behaviour without asserting an inline literal).
- `grapheme-count` — the leaf's grapheme count, the §6.2 ceiling.
- `resolve-address [leaf-id start end]` — returns the addressed substring **without
  materializing any node**; raises `:standoff/invalid-range` (bad offsets),
  `:standoff/unresolved-leaf` (absent or non-text leaf), or
  `:standoff/range-out-of-bounds` (end past grapheme count).

### test/sldb/kernel/standoff_test.cljc (new)
Real assertions (no inline-literal duplication of the generator's output; expected
segment ranges were computed independently from BreakIterator and are anchored to the
observable text, e.g. `"Chau."`, `"Bye."`, the emoji grapheme):
- (a) `word-and-sentence-offsets-are-grapheme-based` uses `"a😀b"` and `"Hi 😀. Bye."`
  where a grapheme is 2 UTF-16 units, so grapheme offsets provably differ from code-unit
  offsets; `layers-are-deterministic` re-derives and compares.
- (b) `layers-are-memoized-by-leaf-id` uses a per-run unique leaf text: `cached?` is false
  before and true after first derivation, and the second call is `identical?` to the first.
- (c) `an-address-resolves-without-materializing-a-node` snapshots `(count (:objects store))`
  before resolving several addresses and asserts it is unchanged; the emoji case asserts the
  range `[1 2]` returns the whole emoji, not a UTF-16 half.
- (d) `out-of-range-and-unresolved-leaf-raise-typed-errors` covers out-of-bounds,
  missing leaf, a real non-text node (`:symbol/:term`) for both `resolve-address` and
  `layers`, and a malformed range.

## Validation (run from repo root; full output in validation.log)
- `bb lint`   → `rings and docstrings ok: 25 files`
- `bb test`   → `Ran 127 tests containing 551 assertions. 0 failures, 0 errors.`
- `bb oracle` → `9 rows, 0 mismatches`

## Scope / safety
- Touched exactly the four files named in the task `files:` list. Nothing staged.
- `:span` kind (`node.cljc`) and `resolves?` (`anchor.cljc`) unchanged; no change to
  canonical bytes / revision / store — the oracle's frozen node ids still match.
- The other working-tree changes (`desk/…`, `.deskops.log`) predate this Executor and were
  not made by me.

## Residual risks
- Word/sentence segmentation is delegated to the platform (JVM BreakIterator / Node
  Intl.Segmenter); cross-host parity of these layers is not proven here (that is the
  separate node-host-parity task). The kernel/tests run on Babashka/JVM per the first slice.
- The layer cache is a process-global atom keyed by content-hash leaf id; correct because a
  leaf id fixes its text, but it is not bounded/evicted (acceptable for the current in-memory
  kernel; a GC/eviction concern belongs to a later milestone).

```acceptance-report
{
  "criteriaSatisfied": [
    {
      "id": "criterion-1",
      "status": "satisfied",
      "evidence": "Only the 4 files in the task files: list were changed (git status: M src/sldb/kernel/ports.cljc, M src/sldb/host/text.cljc, ?? src/sldb/kernel/standoff.cljc, ?? test/sldb/kernel/standoff_test.cljc). :span/anchor validation, canonical bytes, revision and store untouched; oracle 9/9 unchanged."
    }
  ],
  "changedFiles": [
    "src/sldb/kernel/ports.cljc",
    "src/sldb/host/text.cljc",
    "src/sldb/kernel/standoff.cljc",
    "test/sldb/kernel/standoff_test.cljc"
  ],
  "testsAddedOrUpdated": [
    "test/sldb/kernel/standoff_test.cljc"
  ],
  "commandsRun": [
    {
      "command": "bb lint",
      "result": "passed",
      "summary": "rings and docstrings ok: 25 files"
    },
    {
      "command": "bb test",
      "result": "passed",
      "summary": "Ran 127 tests containing 551 assertions. 0 failures, 0 errors."
    },
    {
      "command": "bb oracle",
      "result": "passed",
      "summary": "9 rows, 0 mismatches"
    }
  ],
  "validationOutput": [
    "bb lint: rings and docstrings ok: 25 files",
    "bb test: Ran 127 tests containing 551 assertions. 0 failures, 0 errors. {:test 127, :pass 551, :fail 0, :error 0}",
    "bb oracle: 9 rows, 0 mismatches",
    "Full combined log: runs/subagents/20260831-000438-task-milestone-6-stand-off-below-the-paragraph/validation.log"
  ],
  "residualRisks": [
    "Word/sentence layers delegate to platform BreakIterator (JVM) / Intl.Segmenter (Node); cross-host parity is out of scope here and belongs to the node-host-parity task.",
    "Layer cache is a process-global atom keyed by content-hash leaf id (correct but unbounded/no eviction; a later-milestone concern)."
  ],
  "noStagedFiles": true,
  "diffSummary": "Extend TextSegmenter port with words/sentences (grapheme-offset ranges); implement them in host/text.cljc via BreakIterator/Intl.Segmenter with code-unit->grapheme conversion; add kernel/standoff.cljc (layers memoized by leaf id + virtual resolve-address with typed errors); add standoff_test.cljc.",
  "reviewFindings": [
    "no blockers"
  ],
  "manualNotes": "Working tree also shows pre-existing desk/*.md and .deskops.log changes from the parent's orchestration; those were not made by this Executor. Only the four task files were touched by me, and nothing is staged. Task is on its testing-ready node; closeout should go through deskops closeout commit with this run-dir."
}
```
