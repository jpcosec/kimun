# SLDB v2 Workspace

This worktree holds the direction, atoms and (soon) first-slice implementation of SLDB v2:
a **structured-language database** persisting the S/M/G model (signs, symbols, facts) as one
pool of content-addressed immutable nodes indexed by many trees.

## Read first

0. `docs/v2/05-estado.md` — current state: what exists, how to run it, decisions, next step
1. `docs/v2/01-orden-filosofico.md` — what the persisted object is and which guarantees make sense
2. `docs/v2/02-sustrato-computacional.md` — data structures, invariants, runtime, roadmap (Git-object-model analogy)
3. `desk/atoms/` — durable concept truth; atoms tagged `epoch:v2` prevail over earlier ones
4. `desk/tasks/Board.md` — active work

## Layout

- `docs/v2/` — v2 direction documents (highest authority); `05-estado.md` is the status entry point
- `src/sldb/{kernel,host,surface}/` — ring 0 / 1 / 2 (`bb lint`, `bb test`, `bb oracle`)
- `docs/architecture/` — previous-stage contracts, spec2viz specs and diagrams; superseded where they contradict `docs/v2/`
- `desk/` — deskops workflow state (atoms, tasks, pills, drawer, rituals)
- `raw/source/` — frozen source material from before the v2 reorientation (see its README); tag `pre-v2-planning-freeze`
- `scripts/` — vistas HTML generation and spec traceability checks

## Target direction (summary)

- the kernel is pure Clojure (`.cljc`); hosts (Babashka CLI, ClojureScript/Node) are adapters
- the canonical model is a pool of content-addressed nodes (S/M/G) plus trees-as-indexes and typed edges with evidence
- identity is the content hash; succession is a recorded `supersedes` edge
- guarantee: reversible in S, traceable through S → M → G, generative back
- no separate Lisp (EDN plans + SCI), no Rust in the repo (WASM artifacts only), no JVM for users
