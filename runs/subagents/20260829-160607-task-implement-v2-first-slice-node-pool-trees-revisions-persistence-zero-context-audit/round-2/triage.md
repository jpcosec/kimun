# Triage — round 2 (2026-08-29)

Totals: A high=4 medium=8 low=2 · B0 high=1 medium=4 low=1 (not ready) · B1-3 high=1 medium=10 low=3 (M1 partial, M2 not ready, M3 partial) · C1 rewrite=2 · C2 rewrite=1 · C3 real findings 2 (faq, AGENTS planning rule), 3 false positives (Superseded headers already quote the sentences).
Round 1 → round 2: A high 8 → 4 (all second-order), C retire 20 → 0.

## Lane A → docs/v2/02

| finding | decision | artifact |
|---|---|---|
| `:replace` and sibling order (high) | X' takes X's `:order`; subtree inherited; explicit 4-step semantics | 02 §5.1 "Semántica exacta de :replace"; atom-replace-semantics-inside-a-transaction |
| `:context` concrete value (high, medium) | id of the `:fact/:context` node; tree descriptor `:root` carries it | 02 §2, §3.2 |
| edge id includes timestamp → `:remove-edge` non-deterministic (high) | **timestamp removed from the edge**; recorded in the transaction; same assertion by same origin = same edge; ids returned by the transaction | 02 §3.2; invariant 16; atom-evidence-required-per-edge-type updated |
| re-anchoring scope of `:replace` (high) | §6.1 rules applied inside the same transaction | 02 §5.1 |
| capability undefined (medium) | store descriptor `:capabilities {actor → :all | #{{:op :tree}}}`; absent actor rejected | 02 §5.1 |
| revision hashing (medium) | seven fields, all hashed; `:edges` edge-set object added; provenance derived | 02 §5; atom-revision-id-edge-set-and-diff |
| supersedes evidence source (medium) | plan `:actor` | 02 §5.1 |
| span persistence (medium) | reachability via tree objects AND edges from heads | 02 §3.1 |
| milestone 0 conformance suite (medium) | named in §9 row 0 | 02 §9 |
| motor/engine, actor vs engine (medium) | synonyms; exactly one origin | 02 §2 |
| bb vs Node for milestone 3 (medium) | Babashka/JVM file semantics; Node later | 02 §8, §8.1 |
| Evidence schema, Edge persistence (low) | explicit `Edge`/`Evidence` shapes; edge objects in CAS | 02 §3.2, §3.1; invariant 17 |

## Lane B0 → milestone 0 task

| finding | decision |
|---|---|
| task id still names the umbrella (high) | id kept (renaming would recreate the deskops bundle); summary now opens with "MILESTONE 0 ONLY … milestones 1-3 are separate tasks" |
| fixture shape, pool test file, NFC library, generator coverage (medium) | fixed in Done When + files (`pool_test.cljc`, `sldb.host.text`); NFC and fixture shapes in 02 §8.1 |
| status draft (low) | correct until `deskops advance` at gate closure |

## Lane B1-3 → milestone 1–3 tasks

| finding | decision |
|---|---|
| M2 diff not in Done When (high) | diff shape defined in 02 §5.2; Done When exercises it |
| M1 dirty-path algorithm, fixture shape, undefined wording, heads scope | algorithm in 02 §3.1 and atom; fixture shape in §8.1; scope reworded (heads data shape only) |
| M2 revision fields compared, rebase algorithm, capability denial | seven fields + id; rebase defined in §5.2; denial via absent actor |
| M3 rebuild-indexes, object file format, replay, log line | rebuild-indexes out of scope; UTF-8 canonical EDN with H(file)==name; replay explicit and tested; log line = Transaction map |

## Lanes C → KB

Retired (3): document (→ atom-tree-as-index), decision-blake3-hashing (→ atom-first-slice-runtime-choices), node-hash (→ atom-canonical-content-and-node-hashing); relinked.
Prose: AGENTS.md gains an "Implementation-task rule (epoch v2)"; docs/faq.md answers "what closes an implementation task".
C3 false positives recorded; legacy rituals stay by user decision.

## Round 3

Re-launch A, B0, B1-3 on the corrected bundle. C lanes not re-launched: round 2 found no retirements and only 3 rewrites, all retired; no legacy text was added since.
