# Graph Store Contract

## Purpose and governing sources

This contract defines the persistence layer requirements for the SLDB local graph store.

Governing sources:
- `desk/atoms/graph-store.md`
- `desk/atoms/immutable-append-only-database.md`
- `desk/atoms/append-only-event-log.md`
- `desk/atoms/store-infrastructure.md`
- `desk/atoms/store-integrity-checks.md`
- `docs/architecture/spec2viz/target-store-graph.yml`

## Storage Architecture

### 1. Append-Only Persistence
- **Immutability**: Once a node or edge is committed to the store, it is never modified.
- **Versioning**: Changes to documents result in new nodes/edges being appended, with the history preserved via a Merkle-like event log or sequential snapshots.

### 2. Local Infrastructure (`.sldb`)
- **Location**: A hidden directory `.sldb` at the project root.
- **Components**:
  - `nodes.db`: Storage of serialized AST segments (hash-addressed).
  - `edges.db`: Storage of links and structural relations.
  - `indices.db`: SQLite/FTS5 indexes for fast structural and full-text retrieval.
  - `blobs/`: Storage of non-structural attachments (if any).

## Data Integrity

- **Hash-Addressing**: Nodes are indexed by their Blake3 hashes.
- **Reference Integrity**: The store must prevent or detect dangling edges where the target node hash is missing from the store.
- **Atomic Commits**: Bulk imports (e.g., tracking a new document) must be atomic.

## Parity Obligations

Phase 1 storage is complete when it can:
- Persist the entire structural graph of a v1 project.
- Reconstruct any historical version of a document from the append-only log.
- Perform integrity checks (`sldb stores check`) to verify that all hashes match their content.

## Performance Requirements
- **Insert**: O(Log N) or better.
- **Query**: Indexed retrieval of any node by Hash or Path in < 50ms for projects < 10k nodes.
