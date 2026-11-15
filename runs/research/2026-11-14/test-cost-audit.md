# Test cost and duplication audit (read-only)

Tree: `2c56e680` (C4 integrated). Full run evidence:
`runs/subagents/2026-11-14-k2a-c4-route-coverage/full-test.txt`
(`tree-sitter pod: available`, `1005 tests / 11503 assertions / 123 namespaces / 173 s`).
The owner's earlier `1002 / 137 s` is not the same tree (C4 adds, and timing is
machine/load dependent); compare proportions, not absolute seconds.

## Categories (why they exist)

| Category | NS | Tests | Assertions | Purpose |
|---|---:|---:|---:|---|
| `kimun.surface` | 45 | 412 | 3298 | Public document/model/query/HTTP/graph/signal surfaces; widest behavior matrix. |
| `kimun.cli` | 34 | 276 | 3408 | End-to-end CLI envelopes, store commands, include/docs flows; highest assertions per test. |
| `kimun.parity` | 21 | 124 | 2273 | v1/canonical/golden promises; protects migration and stable bytes. |
| `kimun.kernel` | 18 | 180 | 1109 | Store/revision/mmap/edge/tree invariants; low-level state machine. |
| `kimun.spec16-models-contract-test` | 1 | 4 | 278 | Spec 16 model contract manifest. |
| `kimun.wittgenstein-manifest-test` | 1 | 4 | 742 | Language/manifest corpus promises. |
| `kimun.adapters` | 2 | 3 | 389 | Python and tree-sitter agreement. |
| `kimun.lint-test` | 1 | 2 | 6 | Ring/docstring policy. |

## Two costly namespaces

Static reading only; timings in the full run are load-sensitive and the
diagnostics/mmap targeted re-measure was contaminated by the P1 full run.

- `kimun.cli.diagnostics-test`: 13 tests / 531 assertions, 45.1 s in the full
  run. `many-commits` creates 20 docs plus 2 edits; `check-reports-each-kind-of-damaged-object`
  then makes 11 victims × 3 damage modes, each copied and exercised through
  `check`, `verify`, `journal verify` and `health`, plus direct `verify`/`verify-full`;
  the open-refusal table adds 3 critical objects × 3 modes × 4 commands. The
  cost is dominated by fixture copies and repeated CLI invocations, not by
  assertion logic.
- `kimun.kernel.mmap-test`: 19 tests / 113 assertions, 25.1 s in the full run.
  Six property specs run 60 trials each, and deterministic tests build maps of
  500–2000 entries (`no-ops`, `reads-touch`, `diff-descends`). The assertions are
  cheap; map construction and repeated `from-entries` are the cost.

## Conservar / optimizar / fusionar

| Test or group | Verdict | Evidence | Risk if changed |
|---|---|---|---|
| `diagnostics/check-reports-each-kind-of-damaged-object` | Conservar | Covers every reachable object kind, three damage modes, and all reporting commands (`diagnostics_test.cljc:143-208`). | Highest value corruption matrix. |
| `diagnostics/check-reports-a-tampered-object-whichever-it-is` | Optimizar o fusionar | Its own comment says the table above replaces the random-victim test (`:210-228`); it still makes 6 random copies and overlaps the table's `check` assertions. | Loses randomized discovery if deleted; reduce samples or fold into table. |
| `diagnostics/update-repairs-what-needs-no-decision` | Fusionar | Same command and outcome as `generic_docs_test/stores-update-repairs-what-needs-no-decision` (`generic_docs_test.cljc:104-120`); fixture differs only by model. | Two CLI entry points must stay covered; choose one fixture per layer. |
| `diagnostics/health-names-the-document-the-category-and-the-command` | Conservar; partial overlap | Assertion overlap with typed-doc health status (`typed_docs_test.cljc:290`) but this test owns the health envelope/action contract (`diagnostics_test.cljc:230-244`). | Dropping loses the health-specific shape. |
| `diagnostics/check-in-json-and-yaml` | Conservar | Only JSON/YAML output-contract test for diagnostics (`:98-107`); cheap. | Output regressions become invisible. |
| `diagnostics/many-commits` + damage tables | Optimizar | Static source: 20 docs, 2 edits, 33+9 damaged copies, 4 commands per copy. | Keep one victim per kind/mode; do not re-run all four commands for every cell. |
| `mmap/reads-touch-one-node-per-level`, `diff-descends-only-where-ids-differ` | Optimizar | Each independently builds 2000 entries (`mmap_test.cljc:209-238`); invariants only need `B+1`/threshold-sized maps. | Shrinking sizes keeps the asymptotic assertion; verify branch depth assumptions. |
| `mmap/no-ops-create-nothing` | Optimizar | 500-entry map plus 1000–1100 absent keys (`:192-207`); boundary sizes suffice. | Same invariants; fewer constructed nodes. |
| `mmap` six 60-trial property specs | Conservar | Independent invariants: history independence, insert/remove, map model, diff model, fresh nodes, moved leaves (`:270-371`). | Reducing trials weakens randomized coverage; only do with retained counterexamples. |
| `mmap` deterministic split/collapse tests | Conservar | They encode counterexamples and exact freshness (`:100-191`); cheap relative to 2000-entry tests. | These are regression anchors. |

## Conclusion

No tests or policy changed. Largest safe optimization is reducing fixture size
and repeated command matrix in `diagnostics-test`; `mmap` can shrink only the
large deterministic maps. The random-victim diagnostics test is the clearest
fusion candidate, but only after preserving one random property or its table
coverage.
