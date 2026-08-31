# Testing Report for task-milestone-6-stand-off-below-the-paragraph

## Validation Commands Output

### bb lint
rings and docstrings ok: 25 files

### bb test
rings and docstrings ok: 25 files

Testing sldb.kernel.anchor-test
Testing sldb.kernel.canon-test
Testing sldb.kernel.edge-test
Testing sldb.kernel.hardening-test
Testing sldb.kernel.heads-test
Testing sldb.kernel.node-test
Testing sldb.kernel.plan-test
Testing sldb.kernel.pool-test
Testing sldb.kernel.reconcile-test
Testing sldb.kernel.revision-test
Testing sldb.kernel.standoff-test
Testing sldb.kernel.store-test
Testing sldb.kernel.tree-test
Testing sldb.surface.markdown.cst-test
Testing sldb.surface.markdown.drift-test
Testing sldb.surface.markdown.inline-test
Testing sldb.surface.markdown.plan-test
Testing sldb.surface.markdown.roundtrip-test

Ran 127 tests containing 551 assertions.
0 failures, 0 errors.
{:test 127, :pass 551, :fail 0, :error 0, :type :summary}

### bb oracle
ok  sign/text ffdf4147e59740e4…
ok  sign/block bdc0ef299e3d88c5…
ok  sign/opaque fcc5220a4af29a88…
ok  sign/external b44fd9099808bc77…
ok  sign/span f8bc24ee5fa9c801…
ok  symbol/term 2a03e199175a4ed0…
ok  symbol/proposition a6e631b8d0914123…
ok  fact/triple 9d1307be57183a8d…
ok  fact/context 146e6ca533092558…
9 rows, 0 mismatches

## Anti-Mock Audit of test/sldb/kernel/standoff_test.cljc
- The tests assert against REAL segmentation and resolver, not inline literals that duplicate expected answers.
- Includes grapheme-vs-UTF-16 test (multi-codepoint grapheme) in `word-and-sentence-offsets-are-grapheme-based`.
- The "no node materialized" test (`an-address-resolves-without-materializing-a-node`) snapshots and compares store object count.
- Typed errors are asserted for out-of-range and unresolved leaf in `out-of-range-and-unresolved-leaf-raise-typed-errors`.
- No tautological or mock tests found.

## Done When Conditions
- Spec exit criterion (address down to grapheme with no materialized nodes) holds via the no-node test.
- bb lint, bb test, bb oracle are green.
- New tests prove word/sentence offsets are grapheme-based and deterministic, layers memoized by leaf id, virtual address resolves correctly with no node materialization, and proper error types.

## Files Changed
Only the four files in the task `files:` list were changed under src/ and test/:
- Modified: src/sldb/host/text.cljc, src/sldb/kernel/ports.cljc
- Added: src/sldb/kernel/standoff.cljc, test/sldb/kernel/standoff_test.cljc
No other src/ or test/ files modified.

## Ring Compliance
src/sldb/kernel/standoff.cljc only requires clojure.* and sldb.kernel.* (see ns form).

## Evidence
Validation log and snapshot available in this directory.

## Verdict
PASS
