# Phase 1 Macro Implementation Plan

## Purpose

Define the macro implementation sequence for the SLDB refactor so execution can be driven from product truth (`desk/atoms/`) and target architecture (`docs/architecture/spec2viz/`).

## Fixed constraints

These are not open for redesign during implementation:

- Phase 1 = **v1 parity before scope expansion**.
- **Canonical AST is sovereign**.
- **Rust owns the canonical engine/runtime responsibility**.
- **Python stays a thin CLI orchestration shell**.
- **Links and anchors are canonical**, not late projections.
- **Graph store is append-only / immutable**.
- **Reversible families must preserve exact render equality**.
- **No semantic expansion** beyond current parity needs.

## Macro execution order

Implementation should happen in this order:

1. **Freeze product contract**
2. **Freeze ownership/boundary contract**
3. **Build canonical Rust substrate**
4. **Prove reversible Markdown round-trip**
5. **Build append-only graph store**
6. **Add canonical links + anchors**
7. **Materialize derived indexes + query surface**
8. **Wire Python CLI shell to Rust core**
9. **Close v1 command-group parity**
10. **Only then inspect post-parity gaps / next-phase work**

---

## Milestone 0 — Product freeze

### Outcome

One approved Phase 1 contract that defines exactly what “v1 parity” means.

### Must answer

- Which v1 workflows are mandatory in Phase 1?
- Which command groups are mandatory in Phase 1?
- Which behaviors are explicitly deferred?
- What counts as parity for direct mode vs store-backed mode?

### Deliverable

- One parity contract artifact.

### Why first

Without this, every later implementation slice can drift.

---

## Milestone 1 — Ownership freeze

### Outcome

One approved ownership matrix for Python vs Rust.

### Rust owns

- canonical AST
- hashing / identity
- importers
- emitters
- graph store
- canonical relations
- anchors
- derived indexes
- query engine
- semantic export substrate

### Python owns

- CLI entrypoint
- command routing
- Git orchestration
- filesystem/workflow UX
- shell-level error presentation

### Deliverable

- One ownership/FFI contract artifact.

### Why second

Without this, Python will start re-owning engine behavior again.

---

## Milestone 2 — Canonical Rust substrate

### Outcome

A minimal canonical substrate strong enough for every later slice.

### Implementation scope

- canonical AST model
- rowan-based structure
- structural identity / hashing
- stable selectors
- canonical relation model
- extension point for relation payloads (`RelationAST`)

### Must be true before moving on

- AST shape is fixed enough for importer/emitter work
- selectors are fixed enough for anchors and retrieval
- relation model is fixed enough for links/store/query work

### Deliverable

- Rust core substrate, tests, and invariants.

### Dependency

This unlocks everything else.

---

## Milestone 3 — Reversible Markdown proof slice

### Outcome

A first working vertical slice that proves:

`Markdown -> Canonical AST -> Markdown -> Canonical AST -> Markdown`

with exact rendered equality for reversible families.

### Implementation scope

- Markdown importer
- Markdown emitter
- round-trip fixture corpus
- exact equality validation

### Must be true before moving on

- emitter is purely a projection from canonical AST
- no Python-side canonical parsing logic remains
- exact reversible-family render equality is proven on fixtures

### Deliverable

- passing round-trip suite
- proof fixtures
- importer/emitter boundary locked by tests

### Why here

This is the first serious proof that the target architecture is viable.

---

## Milestone 4 — Append-only graph store

### Outcome

The `.sldb` store becomes the canonical persistence layer for Phase 1.

### Implementation scope

Canonical persisted structures:
- AST nodes
- typed edges
- relation payloads
- anchor nodes
- hash fields

Operational store structures:
- append-only event log
- snapshots
- provenance artifacts
- integrity surfaces

### Must be true before moving on

- canonical persistence is separate from derived indexes
- store is append-only / immutable by contract
- store integrity checks are defined and testable

### Deliverable

- store schema/runtime
- integrity checks
- persistence/reload tests

### Why after round-trip

The store should persist the canonical shape already proven by the reversible slice.

---

## Milestone 5 — Canonical links and anchors

### Outcome

Links and anchors exist as first-class canonical data.

### Implementation scope

- canonical link relations between ASTs
- anchor node payload:
  - document path
  - source hash
  - locator
  - sample text
  - optional comments
  - anchor kind
- text anchors
- AST anchors

### Phase 1 boundary

Mandatory first:
- reversible-family anchor behavior
- canonical payload model

Potentially deferred unless parity requires it:
- broader non-reversible family locator implementations

### Must be true before moving on

- anchors are stored canonically, not reconstructed as a late view
- links and anchors can participate in retrieval/indexing

### Deliverable

- canonical link/anchor layer
- anchor fixtures and validation rules

---

## Milestone 6 — Derived indexes and query surface

### Outcome

The canonical store materializes the minimum derived retrieval layer required for parity.

### Implementation scope

Derived indexes:
- section index
- field index
- search index
- semantic index limited to parity needs

Consumers:
- query engine
- `find` surface
- store-backed retrieval behavior

### Explicit exclusions

- embeddings
- richer semantic expansion
- speculative ranking systems beyond parity need

### Must be true before moving on

- indexes are explicitly derived, not canonical truth
- query behavior maps to parity contract
- semantic scope stays constrained

### Deliverable

- index builders/materializers
- query runtime
- parity tests for retrieval flows

---

## Milestone 7 — Python CLI shell over Rust core

### Outcome

Python becomes a thin orchestrator over approved Rust capabilities.

### Implementation scope

- CLI entrypoint
- command routing
- Git-aware workflows
- filesystem/user-shell integration
- FFI boundary consumption

### Must not happen

- Python re-implements canonical parsing
- Python re-implements graph logic
- Python becomes second truth source

### Deliverable

- thin CLI shell
- FFI integration layer
- workflow-level tests

---

## Milestone 8 — v1 command-group parity closeout

### Outcome

Recognizable CLI continuity is restored on top of the new architecture.

### Priority order

1. direct mode
   - extract
   - render
   - validate
2. store lifecycle
   - stores init/build/check/update equivalent surface
3. store-backed inspection/query flows
   - docs
   - ast
   - fields
   - sections
   - find
   - models
4. remaining parity-required utility/help surfaces
   - faq
   - help

### Must be true for closure

- command groups map to approved underlying contracts
- parity is judged by user-visible behavior, not just internal elegance
- CLI continuity survives while internals are fully replaced

### Deliverable

- parity-ready command surface
- end-to-end parity evidence

---

## Cross-cutting rules during implementation

### 1. Atoms win over task wording

If task wording and atoms diverge, atoms win.

### 2. Rust is the engine

When in doubt, canonical/runtime responsibility belongs in Rust.

### 3. Python is orchestration only

Python should compose workflows, not own canonical logic.

### 4. Derived is not canonical

Indexes, search views, projections, and emitters are downstream products of canonical structure.

### 5. Prove with fixtures early

Round-trip, anchor behavior, and parity flows need fixed fixtures early, not after the build.

### 6. No Phase 2 leakage

Do not sneak in visual UX, embeddings, or broader family work during Phase 1.

---

## Gaps to resolve before implementation starts

These are the likely places where new atoms or clarified atoms may be needed.

### A. Phase 1 parity surface needs a sharper contract

Open questions:
- exact mandatory v1 workflows
- exact command-group closure criteria
- exact deferred scope list

### B. Python/Rust boundary may need more explicit atomization

Open questions:
- exact FFI object model
- exact error/transaction boundary
- exact ownership of model loading and schema translation details

### C. Canonical AST contract may still be too implicit

Open questions:
- exact node taxonomy needed for Phase 1
- exact selector contract
- exact relation payload boundaries

### D. Anchoring Phase 1 scope may need one explicit decision

Open question:
- whether non-reversible-family locator implementations are required in Phase 1 parity or explicitly deferred

### E. Query/index parity needs a hard semantic floor

Open question:
- exact minimum search/query behavior needed for parity without semantic expansion

---

## What to inspect for missing atoms

If new atoms are needed, they will most likely belong in these areas:

- parity acceptance rules
- Python/Rust FFI seam
- canonical AST node/selector contract
- relation payload contract
- anchor payload requirements
- store integrity / rebuild semantics
- query parity floor
- command-group closure criteria

---

## Recommended implementation rhythm

Use this cadence per milestone:

1. freeze contract from atoms
2. check for missing atoms / create them
3. implement one bounded slice
4. validate against fixtures and contract
5. only then advance to the next slice

This keeps implementation subordinate to product truth instead of inventing product truth from code.
