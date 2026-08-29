# Lane B — executability of task-implement-v2-first-slice-node-pool-trees-revisions-persistence (round 1)

Model: claude-haiku-4-5 · type: Explore (read-only) · fresh context · 2026-08-29

## Review

**Correct:**
- Goal clear and measurable (task:43-44); scope lists the 4 milestones with in/out boundaries (task:50); rationale grounded in docs/v2 (task:38); six epoch:v2 atoms bound (task:23-29); pill referenced (task:14); implementation path names concrete surfaces (task:56); validation command present (task:62); Done When specific (task:68); checklist scaffold exists.

- Note [high]: desk/rituals/execution.md:5-6 vs desk/contexts/pill-guardrail-v2-implementation-gate.md:16 — `next.txt` requires desk/rituals/execution.md, which forbids implementation ("keep the task planning-only"), while the pill says ritual-zero-context-audit-gate.md governs v2 tasks — Which ritual governs this task's execution phase?
- Note [high]: task:62 + bb.edn missing — "bb test" cannot run today; no runner, framework or target specified — What does `bb test` invoke?
- Note [high]: task:68 — "generative tests" undefined anywhere in bundle; property-based testing is mentioned in docs/v2 only for milestone 4 (02:183,215) — test.check? random op sequences?
- Note [high]: task:68 — "a hand-described transaction yields exactly its expected revision": format unspecified (EDN fixture? .cljc unit test? prose?) — How does an executor know it is satisfied?
- Note [high]: task:15 vs task:56 — `files:` is empty while Implementation Path names src/sldb/kernel/*.cljc, test/sldb/kernel/*.cljc, bb.edn — Should `files:` list them?
- Note [high]: task:50 vs task:62 — "runs on Babashka and ClojureScript from the same source" but validation is only `bb test` — How is ClojureScript tested?
- Note [medium]: task:50 / docs/v2/02:199 — hashing: "BLAKE3 if host offers; SHA-256 fallback" — BLAKE3 has no Babashka native binding; which library on bb and on cljs?
- Note [medium]: task:50 / docs/v2/02:198 — store backend for milestone 3: files or SQLite (pod / WASM)? — Which one does the executor implement?
- Note [medium]: task:50 vs routine:10-15 — four milestones, each a vertical slice with its own exit criterion in 02:205-220, but one task with one Done When — 4 commits, 1 commit, split?
- Note [medium]: task:50 — "Excludes … M/G engines" while the pool includes M and G node classes — How much M/G structure is in scope for milestones 0-3 (data shapes only? validation logic?)
- Note [low]: task:4 — `summary: ''` empty.
- Note [low]: checklist-…-execution-ready.md:7-8 — execution-ready checklist has no condition_refs; gating checklist without testable conditions.

## Checklist

- A: partial — four milestones in one task; docs/v2/02 §9 defines each as a separate vertical slice (task:50, 02:205-220)
- B: pass — task-local content only (task:14, 23-29)
- C: pass — no real task prerequisites; grounding via atoms/pill (task:12)
- D: pass — six epoch:v2 atoms cover pool, trees, revisions, supersedes, CAS (task:23-29)
- E: fail — `files:` empty though Implementation Path names surfaces (task:15, 56)
- F: fail — pill points to ritual-zero-context-audit-gate; next.txt lists execution.md, which forbids implementation (pill:16 vs next.txt:8, execution.md:5-6)
- G: fail — `bb test` not runnable; no bb.edn, framework, or target (task:62)
- H: fail — "generative tests" undefined (task:68 vs 02:183,215)
- I: fail — evidence format for "hand-described transaction" unspecified (task:68 vs condition-has-closeout-evidence:23)
- J: partial — surfaces excluded clearly; M/G data-vs-logic boundary unclear (task:50)
- K: fail — six open questions: generative tests; hand-described transaction format; hashing lib on bb+cljs; store backend; cljs test strategy; what `bb test` does
- L: pass — no other active task claims milestones 0-3; drawer feature-clojure-kernel-foundation-first-slice is broader and only proposed
- M: fail — `files:` vs Implementation Path and next.txt vs pill are inconsistent (task:15,56; next.txt:8; pill:16)

## Verdict

**not ready.** Split recommendation: **yes** — four sequential tasks (0 blobs/hashing/S-M-G classes; 1 trees/ownership/lazy Merkle; 2 revisions/TransactionPlan/CAS heads; 3 persistence/append-only log/reload), plus an integration check if invariants must hold jointly.

Required fixes before routing:
1. Resolve ritual contradiction (pill vs `deskops next` ritual reference).
2. Define `bb test`: create bb.edn with a test task; name framework (clojure.test) and test paths.
3. Define "generative tests" (property-based, test.check, with one example property) or replace the term.
4. Specify the "hand-described transaction" format (EDN fixture + expected revision) with one example.
5. Fill `files:` with the concrete files to create.
6. Decide hashing on bb and cljs (e.g. SHA-256 now via host, BLAKE3 later) and record it in an atom.
7. Decide the milestone-3 backend (files only vs SQLite).
8. State how ClojureScript is tested (same tests on Node, separate step, or deferred) and add it to validation.
9. Clarify M/G boundary for milestones 0-3 (data shapes only vs logic).
10. Specify evidence format and location (tests passing + runs/ log + commit).
