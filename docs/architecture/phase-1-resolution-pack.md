# Phase 1 Resolution Pack

## Conclusion

Yes: the next thing to resolve is not implementation, but the missing architectural decisions that make implementation non-ambiguous.

This pack resolves the open macro questions into concrete decisions and names the atoms that are still missing.

---

## Resolved macro decisions

### 1. What exactly is Phase 1 parity?

Phase 1 parity means:

- the recognizable **plural-first CLI surface** survives
- **direct mode** works for `extract`, `render`, `validate`
- **store-backed mode** works for tracked document workflows and inspection flows
- **reversible Markdown families** preserve exact render equality
- **store lifecycle/integrity** flows exist at parity level
- **find/query/index** behavior exists only to the minimum needed for v1-equivalent workflows

Phase 1 parity does **not** mean:

- future visual UX
- embeddings
- richer semantic retrieval
- broader document-family expansion unless required by current v1 behavior
- speculative editor adapters beyond what the atoms already force

### 2. What is the first real implementation proof?

The first real proof slice must be:

- `Markdown -> Canonical AST -> Markdown`
- repeated through the reversible-family cycle with exact render equality

Reason:

- it proves the canonical AST is real
- it proves emitter/importer ownership is correct
- it proves Python does not need to own canonical parsing logic
- it gives a hard acceptance harness before store/index/query work expands

### 3. What must Clojure own in Phase 1?

Clojure owns all canonical/runtime truth:

- AST structure
- hashing / identity
- selectors
- canonical relations
- links
- anchors
- importer/emitter engine
- graph-store runtime
- derived index builders
- query engine

Python owns only orchestration:

- CLI entrypoint
- command parsing/routing
- Git-facing workflows
- shell UX and error reporting
- filesystem-level workflow composition

### 4. What is the canonical store scope in Phase 1?

Canonical persisted store scope is:

- AST nodes
- typed edges
- relation payloads
- anchor nodes
- hash fields

Operational store scope is:

- append-only event log
- snapshots
- provenance artifacts
- rebuild/integrity surfaces

Derived store scope is:

- field index
- section index
- search index
- semantic index only to parity floor

### 5. What is the anchor scope in Phase 1?

Mandatory in Phase 1:

- canonical anchor payload model
- text anchor support
- AST anchor support for reversible families
- anchors participate in canonical relation/store/query flows

Deferred unless parity proves otherwise:

- broad non-reversible-family locator implementations
- deep family-specific anchor strategies for HTML/PDF/code beyond current parity need

### 6. What is the semantic/query floor in Phase 1?

Allowed in Phase 1:

- query behavior built over derived indexes
- field/section/search retrieval
- semantic/tag/link lookup only to current parity needs

Not allowed in Phase 1:

- embeddings
- expanded semantic ranking systems
- broad semantic neighborhood exploration beyond current workflows

### 7. What is the CLI closeout rule?

CLI parity is closed only when:

- direct mode is working against the real canonical substrate
- store lifecycle/integrity flows work against the real graph store
- docs/ast/fields/sections/find/models/stores command groups map to real approved contracts
- Python is still only orchestration

---

## Locked milestone order

This is now the macro sequence to follow:

1. parity contract
2. ownership / FFI contract
3. Clojure canonical substrate
4. Markdown reversible proof slice
5. graph store persistence
6. canonical links + anchors
7. derived indexes + query surface
8. thin Python CLI shell
9. command-group parity closeout

No other order is allowed without a new explicit decision.

---

## Missing atoms to create

These are the actual gaps.

### A. `phase-1-parity-contract`

Needed because current atoms say “v1 parity first” but do not define the exact closure surface.

Must answer:
- exact mandatory workflows
- exact deferred workflows
- exact parity judgment rules

### B. `python-clojure-ownership-matrix`

Needed because the current Clojure/Python direction is clear, but the explicit capability ownership matrix is missing.

Must answer:
- who owns importer/emitter runtime
- who owns model loading/schema translation
- who owns query assembly vs execution
- what crosses FFI

### C. `ffi-contract`

Needed because `decision-pyo3-ffi` says which technology, but not the shape of the boundary.

Must answer:
- object model crossing the seam
- error contract
- transaction/lifetime contract
- batch vs single-call expectations

### D. `phase-1-canonical-ast-contract`

Needed because `canonical-ast` is still too high-level.

Must answer:
- minimum node taxonomy required by parity
- minimum structural invariants
- exact rowan role in the substrate

### E. `phase-1-selector-contract`

Needed because `stable-selector` explains why selectors matter, but not the exact Phase 1 selector guarantees.

Must answer:
- what units are selector-addressable in Phase 1
- how selectors relate to anchors/fragments/relations
- what stability promise exists across materialization

### F. `relation-payload-contract`

Needed because relation extensibility exists, but the exact Phase 1 payload boundary is not frozen.

Must answer:
- minimum payload categories
- which relation types are mandatory in Phase 1
- what is canonical vs derived in relation payloads

### G. `phase-1-anchor-scope`

Needed because the target diagrams describe broad anchoring, but Phase 1 boundary is still too implicit.

Must answer:
- exact mandatory anchor payload
- exact mandatory reversible-family behavior
- explicit defer list for non-reversible families

### H. `phase-1-query-parity-floor`

Needed because query/index scope can easily expand.

Must answer:
- exact required index types
- exact `find`/query behavior floor
- explicit semantic exclusions

### I. `phase-1-cli-closeout-criteria`

Needed because command-group atoms exist, but closeout rules for parity do not.

Must answer:
- which command groups are mandatory for closure
- what counts as parity evidence per group
- what can remain deferred

---

## Missing atoms by priority

### Create first

- `phase-1-parity-contract`
- `python-clojure-ownership-matrix`
- `ffi-contract`
- `phase-1-canonical-ast-contract`

### Create second

- `phase-1-selector-contract`
- `relation-payload-contract`
- `phase-1-anchor-scope`
- `phase-1-query-parity-floor`

### Create third

- `phase-1-cli-closeout-criteria`

---

## Immediate next move

Before implementation tasks are authored, the repo needs those missing atoms filled in that order.

That is the real blocker now, not code.
