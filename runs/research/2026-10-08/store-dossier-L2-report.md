# Dossier L2 — diseño del store (carril documental/archivo + desk)

> Investigación de solo lectura. Carril L2: `docs/archive/**` (excepto `raw/source/{core,sldb-v1,ecosystem}` y `raw/source/architecture*`, que son de L1) + `desk/` store-relevante. Fuente común: `/home/jp/.cache/coord-briefs/store-dossier-common.md`.
> Fecha de recogida: 2026-10-08. Todo lo de `docs/archive/` está congelado el 2026-10-08 (ver `docs/archive/README.md:3-6`). Cita textual entre comillas, con `ruta:línea`.
> No se edita ningún repo. No se ejecuta `bb`.

## Convenciones de estado

- **archivado/superado**: el propio doc lleva banner de archivado o "Superseded".
- **vigente-desk**: doc activo en `desk/` (el store `.kimun` se construye desde `kimun.include.edn`).
- Los `desk/atoms/atom-*.md` con tag `epoch:v2` son la capa autoritativa v2; los `desk/atoms/*.md` sin `atom-` y sin `epoch:v2` son legado pre-v2 que sigue en el desk (no archivado).

---

# 1. docs/archive — arquitectura (contratos, overview, mapas)

## docs/archive/README.md
Estado: vigente como política del archivo (congelado 2026-10-08).

- [1 fuente de verdad] El archivo "No es autoridad y no entra al store `.kimun`" y cada fichero "lleva un banner al principio que dice qué era, cuándo se archivó y qué lo superó" (`docs/archive/README.md:3-6`).
- [8 decisiones] `architecture/` (contratos, diagramas pre-v2) queda "Superado por `docs/v2/` (autoridad: `docs/v2/README.md`)" (`docs/archive/README.md:12`).
- [8] `desk/drawer/` (backlog features/tracks/tasks pre-v2) queda "Superado por `docs/v2/05-estado.md` §6" (`docs/archive/README.md:18`).

## docs/archive/architecture/target-system-overview.md
Estado: archivado 2026-10-08; banner "Superseded (2026-08-29)" con 4 puntos que ya no se sostienen.

- [1/3] "the pool of content-addressed nodes is [authority]; a document is a tree-index" (`target-system-overview.md:7`).
- [6 grafo] Dirección: "The current YAML-index-first store should evolve into an **append-only, immutable graph-store-first** local `.sldb/` workspace" (`:28`).
- [2 persistencia] "persist canonical documents, revisions, nodes, and typed edges"; "persist canonical structural AST data for authored document families"; "persist canonical links and anchor nodes" (`:40-43`).
- [2/5 AST] "treat hashes as canonical fields, not adapter-specific metadata"; "materialize section/field/search/semantic views as derived indexes behind replaceable storage/query adapters" (`:44-45`).
- [4 historia] "the canonical persistence model is an append-only immutable revisioned graph" (`:17`).
- [1 superado] Banner: "document as root of authority — the pool of content-addressed nodes is; a document is a tree-index (01 §4)" (`:7`); "render round-trips ... must stay exact" y "The rendered text must remain equal across the cycle" ya no valen (`:4-5`).
- [9 contradicción interna] El cuerpo dice "links and anchors are canonical kernel/store concepts, not late projections" (`:19`) mientras el banner lo reancla a la dirección v2.

## docs/archive/architecture/contracts/graph-store-contract.md
Estado: archivado 2026-10-08; banner "Superseded (2026-08-29)" por `docs/v2/`.

- [1] Componentes autoritativos del `.sldb`: "Transaction Log: append-only record of all transactions; a committed transaction is never modified", "Revision Store: immutable revisions with parents, producing transaction, and root hash", "Content-Addressed Store: `hash -> content` payloads", "Document Heads: per-document pointer to the current revision, updated via compare-and-swap" (`graph-store-contract.md:36-39`).
- [2] "Derived, rebuildable components (never authoritative): structural, field, and search indexes, caches, and projections" (`:40`).
- [3 identidad] "Hash-Addressing: Nodes are indexed by their Blake3 hashes" (`:45`); "Reference Integrity: The store must prevent or detect dangling edges" (`:46`); "Atomic Commits: Bulk imports ... must be atomic" (`:47`).
- [5 AST] No dice si el AST se persiste o se recomputa; sí "persist the entire structural graph of a v1 project" (`:52`).
- [7 escala] "Insert: O(Log N) or better"; "Query: Indexed retrieval of any node by Hash or Path in < 50ms for projects < 10k nodes" (`:57-58`).
- [8/9 decisiones superadas por el banner] "redb is the default embedded backend" y "document-scoped node identity" ya no se sostienen; "first slice persists to files (CAS + append-only log + heads); no backend dependency (02 §8.1)" y "identity is the content hash; succession is a supersedes edge (02 §6)" (`:4-5`).
- [8] "all persistence sits behind the kernel-owned `StorageBackend` interface ... `redb` is the default embedded backend and `CozoDB` a valid alternative" (`:41`).

## docs/archive/architecture/contracts/anchoring-contract.md
Estado: archivado 2026-10-08; banner "Superseded (2026-09-06)".

- [3 punteros] Taxonomía: internal AST anchors "Uses a path-like selector (e.g., `doc_id#section_id`)" (`anchoring-contract.md:23`); external anchors "Uses a `stable-selector` string that can survive minor formatting changes" (`:27`); text/line anchors "`L{line}C{col}`" (`:31`).
- [2] "Anchors are detected during document import/indexing"; "The system must verify that an anchor still 'hits' its target" (`:40-41`).
- [8] "Broken Anchors: Detection of stale or missing targets must be reported as a validation error" (`:42`).
- [8/9 superado] El banner reancla: "anchor states are derived per endpoint (intact/superseded/orphan) and the evaluator anchors are documents of the AnchorDoc model" (`:3`).

## docs/archive/architecture/contracts/canonical-state-derivation-glossary.md
Estado: archivado 2026-10-08; banner "Absorbed (2026-09-06) into `docs/v2/01-orden-filosofico.md` §4.5".

- [1 fuente de verdad] "Canonical state ... The maximum authority of knowledge in this system: Clojure-owned kernel state persisted in the canonical database" (`canonical-state-derivation-glossary.md:69`); "canonical state is the source of truth"; "Markdown is not the source of truth"; "Lisp is not the source of truth" (`:82-84`).
- [2] Canonical state "includes: nodes, edges, revisions, types, metadata, provenance" (`:73-78`).
- [1 pipeline] "Input authored → parse/import → Transacción → Estado canónico → {renderizado/materialización, proyección, índice/artefacto derivado}" (`:37-46`).
- [5 AST] "Derived index / derived artifact ... A rebuildable structure calculated from canonical state" (search index, embeddings, vector index, cached hashes) (`:121-131`).
- [8] "derived indexes are auxiliary and reconstructible ... they are never authority" (`:135-136`).
- [9 contradicción] Dice "canonical database"; la v2 vigente persiste a ficheros (CAS + log + heads) — ver `atom-first-slice-runtime-choices`.

## docs/archive/architecture/contracts/query-and-index-parity-contract.md
Estado: archivado 2026-10-08; banner "Superseded (2026-09-06)".

- [2/5] "Structural Indexing: Sections ... Fields ... Links" (`query-and-index-parity-contract.md:20-23`).
- [6] "Full-Text Search (FTS) ... Engine: replaceable, behind a kernel-owned search interface ... no fixed engine is mandated" (`:25-26`).
- [3] "Find by Path", "Find by Metadata", "Find by Relation" (`:31-33`).
- [8] "No Vector Search: Implementation of LLM-based semantic search is explicitly out of scope"; "Local Only: Queries must execute against the local `.sldb` store without external dependencies" (`:37-38`).
- [7 escala] "Point Queries: < 10ms"; "Complex Joins: < 100ms for projects < 50k edges" (`:42-43`).
- [9 superado] Banner: "indexes are reconstructible maps cached per revision and `--where` parses to an AST (docs/v2/06 §7); an unknown `--where` is an error, never silent False" (`:3`).

## docs/archive/architecture/contracts/cli-parity-contract.md
Estado: archivado 2026-10-08; banner "Superseded (2026-09-06)".

- [8] Grupos: `docs`, `ast`, `fields`, `sections`, `stores` ("Maintenance of the local `.sldb` infrastructure: `stores init`, `stores check`, `stores rebuild`"), `models`, `find` (`cli-parity-contract.md:27-40`).
- [1] "Direct Mode — Commands that operate on individual files without requiring an initialized store"; "Store-Backed Mode — Commands that utilize the `.sldb` graph for context" (`:44-49`).
- [9 superado] "the v1 command map, envelope and exit codes are defined by docs/v2/06 (pista S)" (`:3`).

## docs/archive/architecture/contracts/core-source-downstream-coverage-contract.md
Estado: archivado 2026-10-08; banner "Superseded (2026-09-06)".

- [8] Lista de átomos downstream que "must exist": `identity-stability-rules`, `node-reconciliation`, `garbage-collection`, `retention-policy`, `backup-export`, `store-recovery`, `transaction-plan`, `query-plan`, `projection-plan`, `effect-plan`, etc. (`core-source-downstream-coverage-contract.md:45-64`).
- [8] Superficies spec2viz exigidas: "backup/recovery surface", "garbage-collection/retention surface", "failure/degraded/corruption state surface" (`:66-76`).
- [1] "Plan compilation must remain explicit: Lisp compiles to plan families before kernel execution" (`:89`).

## docs/archive/architecture/contracts/python-clojure-ownership-and-ffi-contract.md
Estado: archivado 2026-10-08; banner "Superseded (2026-08-29)".

- [1] "Clojure Kernel (Canonical Truth) ... Ownership of documents, revisions, nodes, edges, transactions, and provenance"; "Graph Store: Persistence of canonical state and history in the append-only database" (`python-clojure-ownership-and-ffi-contract.md:28-31`).
- [3 identidad] "Identity: Blake3-based hashing of canonical units and document fragments" (`:32`).
- [2] "No Duplicate Persistence: Python must not attempt to parse or cache the internal `.sldb` state" (`:44`).
- [8 superado] Banner: "FFI Boundary (PyO3) — no Rust in the repo; Python is an optional adapter over subprocess/HTTP (02 §8)"; "Blake3-based hashing — SHA-256 via the host Hasher in the first slice (02 §8.1)" (`:4-5`).
- [8] "Boundary Floor": `init_store`, `ingest_document`, `query_graph`, `render_document`, `verify_integrity` (`:49-55`).

## docs/archive/architecture/contracts/repo-source-order-and-contradiction-review.md
Estado: archivado 2026-10-08; banner "Superseded (2026-09-06)".

- [1] Orden de autoridad: `also_core.md` > `core_README.md` > ... > `desk/atoms/` > `docs/architecture/contracts/` > overview/spec2viz (`repo-source-order-and-contradiction-review.md:40-60`).
- [9] Gobierna contradicciones: "Any future planning task may not cite deleted historical reports as authority" (`:116`).
- [9 superado] Banner: "the source order is docs/v2 > desk/atoms epoch:v2 > docs/architecture > raw/source" (`:3`).

## docs/archive/architecture/contracts/documentation-contradiction-map.md
Estado: archivado 2026-10-08; banner "Superseded (2026-09-06)".

- [9] Contradicción de naming resuelta: "The canonical model is a revisioned graph, persisted in an append-only immutable graph store" (`documentation-contradiction-map.md:173`); evidencia de las frases divergentes en `README.md`, `target-system-overview.md`, `python-clojure-ownership-and-ffi-contract.md`, `clojure-canonical-core-contract.md` (`:166-169`).
- [9] "Clojure core contract can read AST-centric instead of full-model-centric" → "The Clojure core is authoritative for the full canonical model: documents, revisions, nodes, typed edges, transactions, provenance, and the structural AST persisted within the append-only revisioned graph" (`:175-186`).
- [1] "Markdown never owns canonical meaning or revisions; it is an input/output surface around Clojure-owned canonical state" (`:198-200`).
- [9] Método: clustering + review por subagentes; "corpus size scanned: `231` Markdown docs", "chunk count: `1125`", "candidate contradiction pairs found: `345`" (`:47-51`).
- [8] Política: "historical reports do not belong in the live KB"; "Git history is the retention layer" (`:287-288`).

## docs/archive/architecture/contracts/atom-ontology-map.md
Estado: archivado 2026-10-08 (contrato de ontología de atoms; pre-v2).

- [2/8] Clasifica atoms por capas: `store` = 27 (`atom-ontology-map.md:59`); `domain:store-graph` (12), `store-hashing` (4), `store-history` (4), `store-indexes` (5), `store-integrity` (3), `store-persistence` (2): `content-addressed-store`, `storage-backend` (`:358-401`).
- [8] "Store and history band": `graph-store`, `content-addressed-store`, `store-infrastructure` (`:451-456`).
- [8] Lista decisiones de capa core/store: `decision-blake3-hashing`, `decision-rusqlite-store`, `decision-search-index-library`, etc. (`:134-138`).

## docs/archive/architecture/atom-provenance-map.md
Estado: archivado 2026-10-08 (mapa atom → source snapshot).

- [8] Trazabilidad del store: `graph-store` ← `source docs/core/reasoning.md`; `content-addressed-store` ← `diagramas_core.md`; `immutable-append-only-database`/`derived-index`/`hash-field`/`ast-persistence` ← `target-system-overview.md`; `merkle-index`/`snapshots`/`dependency-index`/`cache`/`append-only-event-log` ← `feature-canonical-ast-design-current-state.md`; `transaction-log` y `node-hash` ← `diagramas_core.md`; `what-a-store-is`/`store-integrity-checks` ← `faq.md` (sldb-v1) (`atom-provenance-map.md:26-225`).

## docs/archive/architecture/tag-domain-map.md
Estado: archivado 2026-10-08.

- [8] El árbol de dominios se alinea con los diagramas target: "`domain:store.*` maps to `GraphStore`, `ASTPersistence`, `DerivedIndexes`, `HistoryArtifacts`, `Hashing`, and `HashFields`" (`tag-domain-map.md:29`); "`domain:runtime.*` maps to `Adapters`, `SemanticExporter`, projection surfaces, provenance, and query/runtime behavior" (`:30`).

---

# 2. docs/archive/architecture — spec2viz (specs target)

## docs/archive/architecture/spec2viz/target-store-graph.yml
Estado: archivado; version "0.3"; vistas d04/d07/d09.

- [2] `KernelRepository` "persists" Documents, Revisions, Transactions, Nodes, Edges, Artifacts y contiene TransactionLog, ContentStore, HeadStore, CompatibilityMetadata, BackupExports, RetentionRoots, RebuildableState (`target-store-graph.yml:12-31`).
- [1] "KernelRepository treats_as_source_of_truth TransactionLog" y "ContentStore" (`:204-209`).
- [2] `TransactionLog` "append-only transaction log"; `ContentStore` "content-addressed store"; `HeadStore` "atom-tree-as-index heads" (`:103-119`).
- [2] `RebuildableState`: "rebuildable indexes / caches" contiene HashCache, SearchIndex, SemanticIndex, ProjectionCache, PetgraphView, ObservabilityLog, DegradedState (`:139-159`).
- [6 grafo] `OwnershipEdges` → Nodes "participates in Merkle recursion"; `ReferenceEdges` "excluded from structural recursion"; `SemanticEdges` "derived meaning"; `DerivedEdges` "derived lineage" (`:240-255`).
- [5 AST] `ASTPersistence` aparece (índice de átomos `ASTPersistence` vía `ast-persistence`) pero este YAML no lo modela como nodo propio; sí `Revisions materializes Nodes/Edges` (`:225-233`).
- [7/8] `RetentionRoots` "keeps_reachable Revisions", `GarbageCollector` aparece en target-components; `CompatibilityMetadata` "version_tracks" y "versions_semantics_for Transactions / versions_format_for Revisions" (`:214-273`).

## docs/archive/architecture/spec2viz/target-anchoring.yml
Estado: archivado; version "0.3".

- [3] `AnchorNode` contiene `DocumentPath`, `SourceHash`, `StableSelector`, `SampleText`, `Comments`, `AnchorKind`; requiere DocumentPath, SourceHash, StableSelector (`target-anchoring.yml:10-30`, `106-123`).
- [3] `StableSelector` "may_use StructuralLocator / TextLocator", "may_specialize_to FamilySpecificLocator" (PDFLocator, HTMLLocator, CodeLocator) (`:35-81`, `124-132`).
- [1] "AnchorKind prefers AST-aware anchors where canonical structure exists" (`:133-136`).

## docs/archive/architecture/spec2viz/target-components.yml
Estado: archivado; version "0.3".

- [1/2] Nodo `Persistence` contiene TransactionLog, ContentStore, RevisionStore, HeadStore, CompatibilityMetadata, BackupExportStore, IndexesCaches, StorageBackend, GarbageCollector, RetentionPolicy (`target-components.yml:285-302`).
- [3] `ContentStore` "content-addressed store"; `RevisionStore` "revision store"; `HeadStore` "atom-tree-as-index heads"; `StorageBackend` "storage backend" (`:330-347`).
- [2] `MerkleEngine` "merkle engine", átomo `merkle-index` (`:183-190`); `EmbeddingIndex` "embedding index (deferred after first slice)" (`:244-245`).

## docs/archive/architecture/spec2viz/target-runtime.yml
Estado: archivado; version "0.3" (deployment).

- [1/2] `persistence_runtime` contiene local_database, content_store, tracked_files, compatibility_metadata, backup_exports, retention_runtime, garbage_collector (`target-runtime.yml:51-62`).
- [8] `local_database` "redb / Cozo / memory backend" (`:168-172`); `content_store` "content-addressed payload store" (`:173-177`).
- [8] Conexiones: "transaction_engine -> local_database (storage backend)", "query_engine -> local_database", "projection_engine -> content_store (artifact IO)", "recovery_manager -> local_database (verify / replay / rebuild)", "garbage_collector -> content_store (payload pruning)" y "-> local_database (cache / index pruning)" (`:268-326`).

## docs/archive/architecture/spec2viz/manifest.yml
Estado: archivado (índice de specs).

- [8] Registra las specs v2 del kernel y da el comando de regeneración: `sldb.v2.rings`, `sldb.v2.cas_objects`, `sldb.v2.transaction_flow`, `sldb.v2.replace_reanchoring`, `sldb.v2.store_layout`, `sldb.v2.markdown_surface` (`manifest.yml:20-33`). Esas specs v2 viven fuera del archivo (`docs/architecture/spec2viz/v2-*.yml`), no en este carril.

## docs/archive/architecture/spec2viz/desk/tasks/task-specify-sldb-target-crates-spec.md
Estado: archivado (sub-desk pre-v2).

- [8] Objetivo: "Create target-crates.yml describing the crate partition and compile dependencies so vista d03 gains a governing spec" (`task-specify-sldb-target-crates-spec.md:42`).

## docs/archive/architecture/spec2viz/desk/tasks/task-specify-sldb-target-documents-spec.md
Estado: archivado (sub-desk pre-v2).

- [8] Objetivo: "Create target-documents.yml covering StructuredNLDoc models, importers, emitters, renderers and families so vistas d05/d06 gain a governing spec" (`task-specify-sldb-target-documents-spec.md:42`).

## docs/archive/architecture/spec2viz/desk/atoms/atom-diagram-as-projection.md
Estado: archivado (sub-desk pre-v2).

- [2/1] "A diagram is a projection of upstream truth (atoms and specyaml), never a document of record: it is derived, registered, and reconstructible"; "This mirrors the kernel's own principle that derived state is rebuildable from canonical state" (`atom-diagram-as-projection.md:18`, `24`).

## docs/archive/architecture/spec2viz/desk/atoms/atom-specyaml-nexus.md
Estado: archivado.

- [1] "SpecYAML ... is the machine-readable nexus between knowledge atoms and diagram projections: atoms govern it, and diagrams are derived from it" (`atom-specyaml-nexus.md:18`).
- [8] "It is a candidate `StructuredNLDoc` family once the kernel store exists: model, validation, and projections are already its shape" (`:24`).

## docs/archive/architecture/spec2viz/.sldb/ (store embebido de spec2viz)
Estado: archivado (store `.sldb` v1 real del sub-proyecto spec2viz).

- [2/8] `core/store_index.yaml` guarda `models` (name, model_ref, path, models_index, version, canonical:false, family:null, semantics:[]) y `predicates` con `axis` (HOW/WHY/WHAT/...) y un `hash_a` global (`store_index.yaml:1-97`).
- [2/8] `runtime/semantic_index.yaml` = `tags: {}` / `documents: {}`; `runtime/semantic_dag.yaml` = `nodes: []` / `equivalences: {}` (`semantic_index.yaml:1-2`, `semantic_dag.yaml:1-2`): son índices derivados vacíos, coherentes con "rebuildable".
- [6] `runtime/knowledge_graph.kg.json` (snapshot KGDB): "extractor": "deskops_kgdb_snapshot_v1", "node_count": 12, "edge_count": 2, y nodos con `identity.node_id` = `atom:...` (`knowledge_graph.kg.json`).

---

# 3. docs/archive/architecture — vistas y diagramas de persistencia

## docs/archive/architecture/vistas/d07-persistencia.mmd
Estado: archivado (vista pre-v2).

- [1/2] `TransactionLog`, `RevisionStore`, `ContentAddressedStore` marcados `<<Source of truth>>`; `MaterializedIndex`, `HashCache`, `ProjectionCache` marcados `<<Rebuildable>>` (`d07-persistencia.mmd:18-53`).
- [8] Backends: `RedbBackend`, `MemoryBackend`, `RocksDbBackend` implementan `StorageBackend` (`:54-60`).
- [1] "Append-only. Nunca se modifica una transacción pasada"; "Puede eliminarse y reconstruirse desde el log y las revisiones" (`:70-71`).

## docs/archive/architecture/vistas/d09-merkle-perezoso.mmd
Estado: archivado.

- [5/7] `subtree_hash(node, revision)` consulta `Hash Cache`; en miss, `get_owned_children`, recursión por hijos, `hash(canonical data + child hashes)`, guarda en caché (`d09-merkle-perezoso.mmd:9-27`).

## docs/archive/architecture/vistas/d10-sync-externo.mmd
Estado: archivado.

- [3/4] `compare_source_fingerprint(document)` con `stat` (size, modified_at, file_id); si cambió: parse → normalize → `diff(current graph, canonical AST)` → `commit(operations)` → nueva revision (`d10-sync-externo.mmd:11-30`).

## docs/archive/architecture/vistas/d04-dominio.mmd
Estado: archivado.

- [2/4] `Document {id, type, source, current_revision}`; `Revision {id, parents, transaction, root_hash, created_at}`; `Node {id, type, payload, metadata}`; `Edge {id, source, target, type, semantics, metadata}` (`d04-dominio.mmd:2-28`).
- [6] `EdgeSemantics` = Ownership, Reference, Semantic, Derived; nota: "Solo las aristas Ownership forman la estructura Merkle" (`:29-35`, `99`).

## docs/archive/architecture/vistas/d11-proyeccion.mmd
Estado: archivado.

- [2/5] Flujo de proyección: cache válida → subgrafo canónico → tipo (AST estructural/semántico/Render/Embeddings/Lógica) → `Calcular source_hash` → `Guardar resultado derivado` → `ProjectionResult` (`d11-proyeccion.mmd:1-15`).

## docs/archive/architecture/vistas/d13-estados.mmd
Estado: archivado.

- [4/8] Estados de documento: Registered → MetadataKnown → Parsed → Canonical → Indexed → Ready; `Ready --> Conflict`; `Ready --> Archived`; `Parsed --> Invalid`; `Invalid --> Parsed` (`d13-estados.mmd:1-18`).

## docs/archive/architecture/vistas/d16-autoridad.mmd
Estado: archivado.

- [1] "Core --> Storage --> Backend"; "CoreNote: Único componente autorizado para producir nuevas revisiones"; "LispNote: Extensible, pero no controla directamente la persistencia" (`d16-autoridad.mmd:11-21`).

## docs/archive/architecture/vistas/d14-parse-render-parse.mmd
Estado: archivado.

- [4/9] Garantía: "canonicalize(parse(render(C))) == C. No necesariamente render(parse(source)) == source" (`d14-parse-render-parse.mmd:23`).

## docs/archive/architecture/vistas/d05-modelos-documentos.mmd
Estado: archivado.

- [8] `DocumentFamily` = Reversible / NonReversible; nota: "Reversible: invariante canonicalize(parse(render(C))) == C. NonReversible: artifacts externos, sin garantía de round-trip" (`d05-modelos-documentos.mmd:22-49`).

## docs/archive/architecture/core-diagrams/05-persistencia-del-kernel.puml
Estado: archivado (mismo contenido que d07; trazabilidad: `vista: d07`, `governed-by: sldb.target.store_graph`).

- [1/2] Idéntico a d07: `<<Source of truth>>` para TransactionLog/RevisionStore/ContentAddressedStore, `<<Rebuildable>>` para MaterializedIndex/HashCache/ProjectionCache (`05-persistencia-del-kernel.puml:24-76`).

## docs/archive/architecture/core-diagrams/07-calculo-merkle-perezoso.puml / 08-sincronizacion-de-archivo-externo.puml / 09-construccion-de-una-proyeccion.puml / 11-estados-de-un-documento.puml / 14-dependencias-y-direccion-de-autoridad.puml
Estado: archivados (equivalentes .puml de d09, d10, d11, d13, d16).

- [5/7] (07) `get_hash` con Hash Cache y CAS, `hash(canonical data + child hashes)` (`07-calculo-merkle-perezoso.puml:14-39`).
- [3/4] (08) fingerprint por tamaño/mtime/file_id, reconciliación y commit de operaciones (`08-...puml:16-43`).
- [2/5] (09) "Obtener subgrafo canónico" → "Calcular source_hash" → "Guardar resultado derivado" (`09-...puml:17-39`).
- [8] (14) "Único componente autorizado para producir nuevas revisiones" (`14-...puml:25-28`).

---

# 4. docs/archive/desk — backlog pre-v2

## docs/archive/desk/README.md
Estado: archivado (2026-10-08).

- [1] "El store se construye hoy desde `kimun.include.edn`, no desde este fichero [desk/config.json]"; "`kimun.include.edn` define lo que el repo versiona; todo lo que queda bajo `desk/` entra al store al construir con `bin/kimun include build`" (`docs/archive/desk/README.md:4-15`).
- [8] Lo no archivado del desk es lo que kimun sigue leyendo: `desk/primitives/{checklists,conditions,edges,operators}/**/*.md` ("287 documentos del store") y `desk/registry/repo-sldb-refactor-worktree.md` ("el único `RepositoryDoc` del store") (`:48-54`).

## docs/archive/desk/drawer/features/feature-canonical-graph-store-hardening.md
Estado: archivado; status proposed.

- [8] "Harden canonical graph-store integrity, verification, recovery, corruption handling, and degraded-mode behavior beyond the first slice" (`feature-canonical-graph-store-hardening.md:6`); incluye "verify/rebuild flows", "corruption detection and classification", "store-usable vs store-needs-recovery boundary" (`:35-39`).
- [8] Pendiente de diseño: "exact verify command surface", "exact rebuild scope for caches, projections, and indexes", "exact corruption-state classification", "exact operator recovery sequence" (`:48-51`).

## docs/archive/desk/drawer/features/feature-node-identity-and-reconciliation.md
Estado: archivado.

- [3] "stable node identity and deterministic reconciliation across repeated imports, edits, and canonical rewrites"; "merge/update semantics"; "canonicalization conflict handling" (`feature-node-identity-and-reconciliation.md:6`, `33-36`).
- [8] Pendiente: "exact identity inputs", "duplicate vs same-entity rules", "reconciliation precedence", "fixtures for identity stability" (`:45-48`).

## docs/archive/desk/drawer/features/feature-anchoring-and-selector-stability.md
Estado: archivado.

- [3] "stable anchor identity and selector semantics across canonical revisions and surface materializations"; "source hash and locator behavior"; "family-specific locator boundaries" (`feature-anchoring-and-selector-stability.md:6`, `33-36`).

## docs/archive/desk/drawer/features/feature-garbage-collection-and-retention.md
Estado: archivado.

- [8] "Define retention roots, pinning, pruning, and garbage-collection policy for canonical and derived artifacts"; "pruning rules for payloads, caches, projections, and indexes"; "safety rules around canonical history" (`feature-garbage-collection-and-retention.md:6`, `31-36`).
- [8] Pendiente: "canonical reachability rules", "what may be pruned vs must be retained", "scheduling and operator controls", "attestation for safe pruning" (`:44-47`).

## docs/archive/desk/drawer/features/feature-backup-export-import.md
Estado: archivado.

- [8] "canonical export, backup, restore, and import flows for durable portability and disaster recovery"; "integrity validation on restore" (`feature-backup-export-import.md:6`, `31-36`).

## docs/archive/desk/drawer/features/feature-migrations-and-compatibility.md
Estado: archivado.

- [8] "versioning, migration units, compatibility metadata, and upgrade rules for canonical persistence and surface contracts"; "downgrade or read-older-data policy" (`feature-migrations-and-compatibility.md:6`, `31-37`).

## docs/archive/desk/drawer/features/feature-lexical-search-and-indexing.md
Estado: archivado.

- [2/5] "lexical and structural search behavior plus rebuildable search indexes over canonical content"; excluye "embeddings" y "LLM-mediated retrieval" (`feature-lexical-search-and-indexing.md:6`, `30-41`).

## docs/archive/desk/drawer/features/feature-query-language-and-explainability.md
Estado: archivado.

- [8] "query surface, plan/explain behavior, and operator-visible inspection tools over canonical state and derived views" (`feature-query-language-and-explainability.md:6`).

## docs/archive/desk/drawer/features/feature-logic-rules-and-derived-facts.md
Estado: archivado.

- [6/8] "rule-based derivation and materialized logical facts over canonical content"; "invalidation/rebuild policy"; "excludes authority reassignment to logic outputs" (`feature-logic-rules-and-derived-facts.md:6`, `30-39`).

## docs/archive/desk/drawer/features/feature-semantic-indexing-and-embeddings.md
Estado: archivado (deferred).

- [2/6] "optional semantic indexing, embeddings, provider boundaries, and rebuild policy as non-authoritative derived artifacts"; "explicit non-authority guarantees" (`feature-semantic-indexing-and-embeddings.md:6`, `33-38`).

## docs/archive/desk/drawer/features/feature-effect-system-and-automation.md
Estado: archivado.

- [2] "effect plans, outbox behavior, runners, hooks ... around canonical state changes" (`feature-effect-system-and-automation.md:6`).

## docs/archive/desk/drawer/features/feature-failure-model-and-operational-modes.md
Estado: archivado.

- [8] "failure classes, degraded modes, recovery states, and operator-visible operational transitions" (`feature-failure-model-and-operational-modes.md:6`).

## docs/archive/desk/drawer/features/feature-observability-and-diagnostics.md
Estado: archivado.

- [8] "traces, diagnostics, explain/status surfaces, and operator visibility across kernel, queries, projections, and effects"; "stored observability artifacts where appropriate" (`feature-observability-and-diagnostics.md:6`, `31-36`).

## docs/archive/desk/drawer/features/feature-canonical-graph-store-hardening … (resto: multi-family, renderers, projections, conformance, cli, agent, desktop, prosemirror, treesitter)
Estado: archivados; tocan el store solo como contexto.

- [8] `feature-multi-family-document-support.md:6`: "additional authored/imported document families with canonicalization and materialization contracts".
- [8] `feature-renderers-and-materializers-beyond-markdown.md:6`: "render/materialization surfaces beyond Markdown"; "treating renders as authority" excluido (`:37-39`).
- [8] `feature-specialized-projection-surfaces.md:6`: "specialized projection surfaces such as semantic graph views, dependency trees, anchor maps, task views, and agent subgraphs".
- [8] `feature-conformance-harness-and-fixtures.md:6`: "behavioral attestation harness, fixture corpus, and reference behavior".
- [8] `feature-cli-surface-implementation.md:36`: "direct vs store-backed mode behavior".
- [8] `feature-agent-and-mcp-surface.md:6` y `feature-desktop-or-visual-ui-surface.md:6`: superficies sobre "canonical state, projections, and controlled actions".
- [8] `feature-prosemirror-editor-surface.md:47-48`: "editor state is authored input or rendered materialization, not authority; canonical meaning and revision truth stay in the Clojure kernel/database layer".
- [8] `feature-treesitter-code-family-support.md:46-47`: "Tree-sitter is parser/locator infrastructure, not the authority layer".

## docs/archive/desk/drawer/tracks/track-kernel-store-hardening.md
Estado: archivado.

- [8] Agrupa store hardening, compatibility, retention, backup, observability, failure modes; orden de promoción: 1 hardening, 2 migrations/compat, 3 GC/retention, 4 backup, 5 observability, 6 failure model (`track-kernel-store-hardening.md:6`, `32-39`).
- [8] Pendiente: "minimum safe-store bar before retention or backup work" (`:45`).

## docs/archive/desk/drawer/tracks/track-derived-views-and-retrieval.md
Estado: archivado.

- [8] "Bundle projections, search, logic, and later semantic retrieval as explicitly non-authoritative derived-state work" (`track-derived-views-and-retrieval.md:6`).

## docs/archive/desk/drawer/tracks/README.md / features/README.md
Estado: archivados (guías de forma del drawer).

- [8] `features/` = macrotasks; `tracks/` = lanes agrupadas; `tasks/` = bounded work units (`tracks/README.md:9-11`).

## docs/archive/desk/drawer/tasks/task-freeze-canonical-state-derivation-glossary.md
Estado: archivado.

- [1] "Freeze the glossary for inputs, canonical state, renders, projections, and derived indexes"; validación: "Does it keep canonical authority in Clojure-owned database-backed state?" (`task-freeze-canonical-state-derivation-glossary.md:6`, `63`).

## docs/archive/desk/drawer/tasks/task-order-repo-sources-and-review-contradictions.md
Estado: archivado.

- [1/9] "Define source-authority order for this planning workspace and record source contradictions"; refs a `decision-graph-store-over-yaml-indexes`, `decision-links-and-anchors-are-canonical` (`task-order-repo-sources-and-review-contradictions.md:6`, `16-17`).

## docs/archive/desk/contexts/pill-planning-contracts.md
Estado: archivado (superado por `AGENTS.md` + pill v2).

- [8] "Planning tasks in this worktree produce contract docs, not code"; "Each artifact must cite the governing atoms and target architecture docs" (`pill-planning-contracts.md:11-13`).

## docs/archive/desk/rituals/{execution,testing,closeout,phase}.md
Estado: archivados (planning-era).

- [8] execution: "keep the task planning-only and avoid implementation claims" (`execution.md:9`); testing: "verify no implementation progress is claimed" (`testing.md:10`); closeout: cierra solo cuando "contract artifact, task metadata, and board state all agree" (`closeout.md:5-11`).

---

# 5. docs/archive/raw/source/atoms-retired — átomos retirados (decisiones del store)

## decision-rusqlite-store.md
Estado: retirado (superado por `atom-first-slice-runtime-choices` / `decision-graph-store-over-yaml-indexes`).

- [8] "The kernel must own a `StorageBackend` boundary and avoid baking SQLite-specific behavior into the canonical model. `rusqlite` is no longer the governing decision" (`:16`).
- [8] "`redb` is the default embedded direction ... `CozoDB` remains a valid prototyping backend"; "Transaction semantics, revisions, hashes, and capabilities must stay invariant across backend swaps" (`:21-23`).

## decision-blake3-hashing.md
Estado: retirado.

- [3] "`blake3` is used for cryptographic hashing of nodes and documents (like `SourceHash`). Its extreme performance and intrinsic parallelism make it ideal ... building Merkle indexes during workspace rebuilds" (`:14`).

## node-hash.md
Estado: retirado.

- [3/5] "A node hash is the deterministic Merkle fingerprint computed from canonical node data plus the hashes of structurally owned children" (`:16`).
- [5] "Structural hashing is lazy: dirty branches are invalidated on change and recomputed only when queried, committed, exported, or verified" (`:20`).
- [6] "Only ownership edges participate recursively in the structural Merkle so references and semantic cycles cannot break hashing" (`:21`).
- [8] "Hash caches are rebuildable accelerators, not sources of truth" (`:23`).

## identity-stability-rules.md
Estado: retirado (readoptado en v2 como sucesión registrada).

- [3] "define when a canonical unit keeps its identity, receives a new identity, counts as moved, or counts as deleted and recreated"; "Reconciliation quality depends on making identity stability explicit rather than heuristic-only" (`:16-20`).

## node-reconciliation.md
Estado: retirado.

- [4] "matches external-source changes against canonical identity rules and compiles the result into primitive operations instead of blindly replacing whole documents"; "output ... is a transaction-shaped change set, not ad hoc source patching" (`:16-22`).

## document.md
Estado: retirado (→ `atom-tree-as-index`).

- [1/4] "A document is the stable canonical unit whose evolving states are recorded as immutable revisions over a graph of typed nodes and edges"; "A document may have an external source reference, but the kernel persists canonical structure, revision lineage, and derived artifacts separately from that source"; "Documents are the unit for heads, history, conflict detection, projections, and provenance" (`:16-22`).

## text-layer-vs-graph-layer.md
Estado: retirado.

- [1/6] "The text layer and the graph layer are complementary but different system layers: SLDB owns structured authored text and its canonical AST/store behavior, while downstream graph systems own graph-native persistence, traversal, and higher-order reasoning"; "Export is a projection boundary from SLDB into graph consumers" (`:16-23`).

## decision-canonical-ast-over-markdown.md
Estado: retirado.

- [1] "The refactor treats the canonical AST as sovereign because Markdown is too surface-bound to carry the full stable identity, relation, anchoring, hashing, and graph semantics" (`:15`); "Stable node identity, typed relations, anchors, and derived indexes need a canonical internal substrate" (`:20`).

## decision-rowan-ast.md
Estado: retirado (→ `atom-reversibility-scope-in-v2`).

- [5 AST] "`rowan` (lossless syntax trees) is chosen. It retains all bytes, including whitespace and trivia, guaranteeing that rendering a non-mutated AST yields the exact original text" (`:14`).

## reversible-document-family.md
Estado: retirado.

- [4] "The required invariant is `canonicalize(parse(render(C))) == C` for canonical content `C`"; "Byte-for-byte source preservation may be offered by lossless adapters, but semantic reversibility is the required floor"; "Reversibility must survive persistence and reload, not only in-memory transforms" (`:20-23`).

## non-reversible-document-family.md
Estado: retirado.

- [3] "may only support partial structure or family-specific anchoring rather than a fully reversible render cycle"; "They use family-specific structure or locators where available" (`:16-21`).

## phase-1-v1-replication.md / decision-v1-parity-before-scope-expansion.md
Estado: retirados.

- [8] "The initial phase ('Phase 1') ... is explicitly focused on replicating the v1 functionality" (`phase-1-v1-replication.md:14`).
- [8] "The first target slice prioritizes recreating SLDB v1 behavior because the refactor is architectural, not a product-scope expansion exercise" (`decision-v1-parity-before-scope-expansion.md:16`).

## decision-rayon-parallelism.md
Estado: retirado.

- [7] "`rayon` enables data-parallelism during the Importer phase ... reading files, parsing ASTs, computing hashes, and extracting anchors can be executed concurrently" (`:14`).

## decision-clojure-core-with-minimal-python.md
Estado: retirado.

- [1] "keeps all authoritative mutation, revision, hashing, persistence, and capability validation inside the Clojure kernel" (`:16`).

## decision-pyo3-ffi.md
Estado: retirado.

- [8] "An embedded FFI such as PyO3 may exist for Python clients, but it is an adapter choice rather than the defining architecture boundary"; "No adapter is allowed to redefine storage, revision, or capability behavior" (`:16-22`).

---

# 6. docs/archive/raw/source/drawer-features — features de la etapa intermedia

## feature-sldb-explicit-target-architecture.md
Estado: archivado (raw, pre-v2, "Status: open").

- [1] "SLDB should move toward a **canonical AST-centered architecture**"; "The AST is the main source of truth. Everything else becomes an importer, adapter, projection, cache, or derived view over that AST" (`feature-sldb-explicit-target-architecture.md:21`, `48-50`).
- [1/5] "Store should stop being the conceptual center ... it should mostly become infrastructure over the canonical AST"; "Store-like behavior should become infrastructure over AST, not the core architecture itself" (`:82`, `348-352`).
- [2] AST debe portar provenance/history más allá de git: "We want more than git-only history" (`:227`); hashing: "node hashes, subtree hashes, dependency hashes, structural fingerprints, dirty-state invalidation, projection cache keys" (`:238-247`).
- [8] "Legacy runtime surfaces should not be preserved as first-class architecture"; "They stay recoverable through git history" (`:383-385`).

## feature-canonical-ast-design-current-state.md
Estado: archivado (raw, pre-v2).

- [2] El AST debe soportar capas: structural (node_id, document_id, parent_id, sibling order, source spans), field/schema, link/reference, addressability, external anchoring, hooks, provenance/temporal, semantic, hashing/fingerprint, semantic embeddings (`feature-canonical-ast-design-current-state.md:55-216`).
- [2/5] "The AST or AST-adjacent core model must support structural hashing": "node hash, subtree hash, dependency hash, materialization hash, structural fingerprint, dirty-state / invalidation metadata" (`:197-208`).
- [2] "Infrastructure beside the AST": "append-only event log, snapshots, cache storage, Merkle index persistence, dependency graph indexes, projection registries, embedding stores when vectors are too large to inline" (`:298-306`).
- [2] "External sidecars / indices": "full embedding vectors, dense graph indexes, cache artifacts, render snapshots, historical event stores" (`:418-424`).
- [6] "AST nodes/fields/links -> semantic facts/propositions -> external semantic engine or matrix runtime" (placeholder futuro) (`:286-290`).
- [1] "the AST must be the center, but not everything belongs directly inside every node" (`:294`).

## feature-sldb-product-principles-and-cli-continuity.md
Estado: archivado (raw, pre-v2).

- [1] "a document may exist canonically in AST form without being rendered to Markdown yet"; "Materialized documents become one projection among others" (`feature-sldb-product-principles-and-cli-continuity.md:44-56`).
- [1/8] "Materialization is a projection, not existence itself" (`:104`).

## feature-rust-library-stack.md (id: feature-clojure-library-stack)
Estado: archivado (raw, pre-v2).

- [8] Propuesta de librerías por capa: `rusqlite` "Para manejar la persistencia local en `.sldb/` usando SQLite ... la base de datos es de tipo *append-only* e inmutable"; `DataScript/Asami` para grafos en memoria; `rowan` "estrictamente necesaria" para reversible; `blake3` para hashing/Merkle; `rmp-serde`/`bincode` "para almacenar las cargas dinámicas de los `RelationASTs` directamente en blobs binarios compactos dentro del Graph Store (SQLite)"; `tantivy` o SQLite FTS5 para `SearchIndex`; `rayon`/`dashmap` (`feature-rust-library-stack.md:20-38`).

---

# 7. docs/archive/raw/source/drawer-retired — features first-slice retiradas

## feature-clojure-kernel-foundation-first-slice.md
Estado: retirado (raw).

- [1/2] "Establish the Clojure-owned canonical kernel and database-backed authority boundary as the first implementation lane"; incluye "append-only transaction and revision persistence"; "rebuildable derived-state boundary"; "no-authority status for caches, projections, and indexes" (`:30-41`).
- [8] Pendiente: "exact first-slice storage backend choice and fallback policy"; "exact transaction/revision payload envelope"; "exact degraded/recovery envelope required before calling the store usable" (`:55-60`).

## feature-markdown-roundtrip-first-slice.md
Estado: retirado (raw).

- [4] "byte-level roundtrip acceptance for the first reversible family"; "the first acceptance bar is byte-level roundtrip behavior for the initial Markdown family" (`:35-55`).

## feature-phase-1-closure-package.md
Estado: retirado (raw).

- [8] "closure checklist across core feature lanes"; "store integrity" entre las superficies de cierre (`:34-36`).

## feature-lisp-control-and-data-surface-first-slice.md / feature-lisp-surface-expansion.md / feature-post-phase-1-scope-expansion.md
Estado: retirados (raw).

- [1] "no direct privileged persistence path outside the kernel" (`feature-lisp-control-and-data-surface-first-slice.md:42`).
- [8] "embeddings and semantic indexing" diferidos a post-Phase-1 (`feature-post-phase-1-scope-expansion.md:29`).

## track-first-slice-foundation.md
Estado: retirado (raw).

- [8] Orden: "1. Clojure kernel foundation 2. Lisp control/data surface 3. Markdown roundtrip 4. CLI surface implementation 5. Conformance harness and fixtures" (`track-first-slice-foundation.md:38-42`).

---

# 8. docs/archive/raw/source/subagent — exploraciones de ontología/AST

## spec2viz-ast-architecture.md
Estado: archivado (raw).

- [1] "The AST is the source of truth; Markdown, store artifacts, search indexes, and visual models are projections or infrastructure" (`spec2viz-ast-architecture.md:153-154`).
- [1] "Store demotion invariant — Store behavior becomes infrastructure over the AST, not the conceptual center of the system" (`:168-169`).
- [5] Infraestructura: "structural hashing / subtree hashes / Merkle behavior", "cache and recomputation", "dependency indexes", "snapshots and append-only event log", "projection registries", "large sidecars such as embedding stores" (`:67-76`).
- [3] Edge families: tree-spine (`contains`, `ordered_child`, `slot_member`), field/schema, reference, addressability/anchor, runtime/behavior, provenance/time (`superseded`, `valid_during`), semantic/graph (`:104-149`).

## atom-ontology-map.md
Estado: archivado (raw).

- [1] "The durable center is **not Markdown, not store artifacts, and not any editor model**"; "Store behavior is demoted from domain center to infrastructure over AST" (`atom-ontology-map.md:20`, `61`).
- [2/5] "Domain I — Hashing, dependency, cache, and recomputation": "Structural hashing is core infrastructure from early on"; `NodeHash`, `SubtreeHash`, `DependencyHash`, `MaterializationHash` (`:355-374`).

## atom-draft-set.md / behavior-atom-map.md / questions-*.md
Estado: archivados (raw).

- [8] `atom-draft-set.md:149-153`: "A node hash is the structural fingerprint used to support integrity, caching, and incremental recomputation".
- [8] `behavior-atom-map.md:39` (warning): "Low-level store implementation details vary across docs (`.sldb/core/` + `.sldb/runtime/` vs older YAML cascade examples). External atoms should preserve store purpose and visible outcomes, not freeze internal file layout."
- [8] `behavior-atom-map.md:50`: "A store is SLDB's metadata workspace for models, tracked docs, integrity, and indexes; it does not replace Markdown files or become the text source of truth."
- [8] `questions-implementation-ambiguities.md:6-8`: sin respuesta explícita sobre "Tracked-doc authority after import" (AST importado vs Markdown vs both under drift detection).
- [9] `questions-atom-ontology-ambiguities.md:11`: "Which history atom is foundational: `Provenance Record`, `Append-only event log`, `Temporal layer`, or `Snapshots`? ... That lineage stack currently reads as cyclic rather than clearly layered."
- [9] `questions-architecture-ambiguities.md:3-4`: "Are links/references canonical AST data, or only derived projections?"; "Which of provenance/history and hashing/cache live inside the AST core versus AST-adjacent infrastructure/sidecars in v1?"

## drafts/contracts-batch-a.md / contracts-batch-b.md
Estado: archivados (planes de redacción de contratos, no contratos).

- [8] `contracts-batch-b.md:37`: bullets requeridos del graph-store contract: "append-only local `.sldb/` storage, canonical AST nodes, typed relation edges, relation AST payloads, anchor nodes, hash fields, derived section/field/search/semantic indexes, and history/provenance expectations".
- [8] `contracts-batch-a.md:103`: "lossless `rowan` support for reversible families, `blake3` hashing, hashes as node fields, links/anchors as canonical data".

---

# 9. docs/archive/v2 — specs v2 diferidas/no implementadas

## docs/archive/v2/09-evaluador-anclado.md
Estado: archivado 2026-10-08; banner "spec diferida, NO IMPLEMENTADA" (S4–S5).

- [1] "El evaluador no tiene una puerta lateral al store: **lee del índice y escribe por `TransactionPlan`**, como cualquier grupo v1" (`:17-18`).
- [1/2] "los 13 anchors viven en `resources/grammar/` como gramática por defecto y se ingestan como documentos"; `AnchorDoc` es descriptor de modelo con `kind ∈ #{model doc relation operation projection expr}` (`:22-23`).
- [2] "La expresión evaluada se guarda como nodo `:opaque {:format "edn/expr"}` con una arista `derived` ... **la provenance de cada escritura vive en el pool**, no en un log aparte" (`:54-58`).
- [2/8] `EffectPlan` escribe el Markdown por outbox: "`outbox/log.edn` registra `{:tree :revision :path :hash-c}`" (`:57-58`).
- [6] Derivados: "la validez de un derivado es `anchor/states` sobre sus aristas: `intact` = vigente, `superseded`/`orphan` = stale. No hay un tracker aparte"; motores SCI puro y `shell:CMD` opt-in (`:64-76`).
- [1] "el índice y el pool son el único backend" (`:31`).

## docs/archive/v2/tests/parity-serve-checklist.md
Estado: archivado 2026-10-08; superseded por `docs/v2/tests/parity-v1.md`.

- [8] Describe el mapeo de rutas `/stores`, `/stores/check`, `/stores/links`, `/docs/ingest`, `/journal`, `/journal/verify` y `version` = "sha256 del payload JSON con claves ordenadas" (`:13-18`, `69`).

---

# 10. docs/archive/runs — auditorías zero-context y ejecución (persistencia)

## docs/archive/runs/README.md
Estado: archivado.

- [8] Evidencia del flujo pre-v2 de workers herdr; "No es autoridad"; cada run tiene `brief.md`, `task.txt`, `triage.md`, rondas, más `index.jsonl` (`:3-12`).

## runs/subagents/20260829-160607-…-zero-context-audit/triage.md
Estado: archivado. Gate zero-context del primer slice (milestones 0–3).

- [8] Gate CLOSED; "19 legacy atoms, 5 architecture docs and 7 drawer items retired". Cinco decisiones de diseño del store confirmadas por el usuario (`triage.md:16-37`):
  1. "`class`/`kind` enter the node hash (same text as `:text` vs `:opaque` are different nodes)".
  2. "Semantic edges are not auto-followed under succession; reference/binding are."
  3. "Timestamp is not part of an edge; the same assertion by the same origin is one edge (idempotent); different origins are two evidences."
  4. "Tree ids are nominal ULIDs; descriptors are CAS objects referenced by the revision tree-set; Revision has eight hashed fields."
  5. "First slice: SHA-256, files-only backend, Babashka-only validation, M/G as data shapes."

## runs/subagents/20260829-160607-…-audit/round-1/triage.md
Estado: archivado.

- [1/2] Decisiones con artefacto: "TransactionPlan EDN schema undefined → define schema, aliases, 7 validation checks, all-or-nothing (`02 §5.1`)"; "canonical content per node class undefined → canonical EDN bytes rule + per class/kind content table" (`round-1/triage.md:11-12`).
- [5] "sibling order in Merkle → tree objects `{:node :children [[id hash]…]}` in order; order enters hash by construction (`02 §3.1`)" (`:13`).
- [6] "re-anchoring rules per edge type → table: follow (reference, binding), review (semantic), revalidate (projection), invalidate (derived) (`02 §6.1`)" (`:15`).
- [2/3] "heads vs roots → heads `{tree-id → revision-id}` CAS per tree; document head = head of its `:document` tree"; "tree storage representation → tree objects in CAS = ownership edge set; adjacency derived"; "opaque content form → `{:format :blob}`, blob in hash; drift = id change" (`:22-24`).
- [8/9] Lane B (ejecutabilidad) había exigido: "Decide the milestone-3 backend (files only vs SQLite)"; "Decide hashing on bb and cljs (e.g. SHA-256 now via host, BLAKE3 later)" (`round-1/lane-B.md:49-51`).

## runs/subagents/20260829-160607-…-audit/round-2/triage.md, round-3, round-4, round-5
Estado: archivado.

- [3/8] round-2: "edge id includes timestamp → `:remove-edge` non-deterministic → **timestamp removed from the edge**; recorded in the transaction" (`round-2/triage.md:12`); "revision hashing → seven fields, all hashed; `:edges` edge-set object added"; "span persistence → reachability via tree objects AND edges from heads" (`:15-17`).
- [5] round-3: "tree descriptor storage → descriptor = CAS object; Revision gains `:trees` (tree-set object); **Revision has eight fields**" (`round-3/triage.md:11`); "edge object storage → each edge stored at `objects/<edge-id>`; edge-set lists ids" (`:12`); "CAS phrasing → single atomic heads.edn write; CAS logical per entry" (`:20`).
- [7] round-4: "reachability formalization → transitive closure from heads; acyclic because ids are hashes" (`round-4/triage.md:19`).
- [4/9] round-5 (lane A final): "node hashing and canonical-bytes (§2.1); dirty set and lazy Merkle (§3.1); timestamp out of edge id, in revision id; evidence per edge type (§3.2); W_i as node and tree (§2); re-anchoring rules (§6.1); span materialization by the plan (§4.1); tree objects and merkle-root (§3.1); seven validation checks (§5.1); rebase and ConflictSet (§5.2)" (`round-5/lane-A.md:5`).

## runs/subagents/20260829-165224-…-first-slice-node-pool…/result-summary.md
Estado: archivado. Milestone 0 (content-addressed node pool).

- [2/3] "src/sldb/kernel/node.cljc — 9 class/kind shapes of §2.1, validate, identity-form, **node-id = H(canonical-bytes {:class :kind :content})**"; "src/sldb/kernel/pool.cljc — in-memory pool, idempotent put with id check" (`result-summary.md:12-13`).
- [8] "First slice: SHA-256 ... BLAKE3 later"; `bb test` 21 tests (`:10`, `18`).

## runs/subagents/20260829-171145-…-milestone-1-trees…/result-summary.md
Estado: archivado. Milestone 1 (trees as indexes, lazy Merkle).

- [5] "src/sldb/kernel/tree.cljc — nominal tree ids, descriptor {:tree :kind :name :root} + descriptor-id (CAS object), ownership as children vectors (sibling order = index), ... dirty set marking node + ancestors, commit (post-order over dirty only, structural sharing), tree-object, merkle-root" (`result-summary.md:11`).
- [3] "one node in two trees → one node id, two tree hashes" (`:17`).

## runs/subagents/20260829-171558-…-milestone-2-revisions…/result-summary.md
Estado: archivado. Milestone 2 (revisions, TransactionPlan validation, CAS heads).

- [2/4] "src/sldb/kernel/revision.cljc — in-memory store (CAS objects, trees, edge set, head, heads, revisions); ... base-cas with ConflictSet (:same-parent-edit / :removed-target / :superseded-target) and automatic rebase for disjoint trees, ... eight-field Revision + id, Transaction" (`result-summary.md:9`).
- [8] "Concurrency in memory only; persistence is milestone 3" (`:19`).

## runs/subagents/20260829-172152-…-milestone-3-file-persistence…/result-summary.md
Estado: archivado. Milestone 3 (file persistence and reload).

- [1/2] "src/sldb/kernel/store.cljc — Backend protocol (descriptor, objects, log, heads CAS); hashed-form/serialize so that **H(file) == id** for nodes, edges, tree objects, descriptors, edge-sets, tree-sets, revisions and transactions"; "commit! (apply → write new objects → append Transaction line → CAS heads against the loaded heads, :store/stale otherwise), reachable (closure from heads), verify (recompute H(file) for every reachable object)" (`result-summary.md:8`).
- [2] "src/sldb/host/fs_store.cljc — ... store.edn, objects/<id> (immutable, write-once), log.edn append, heads.edn written to a temp file and atomically renamed, CAS by comparing the current content" (`:9`).
- [8] "rebuild-indexes is out of scope until derived indexes exist (milestone 4+)" (`:17`).

## runs/subagents/20260829-174447-…-testing/lane-2.md
Estado: archivado. Trazabilidad promesa→test de plan/revision/heads/store/fs-store.

- [8] "OK ... eight-field Revision and id (tx-001); ... CAS on heads; same-parent conflict + disjoint rebase; ... objects/<id> with H(file)==id; heads.edn atomic; open replays and checks ids; verify" (`lane-2.md:6`).
- [8] "WEAK: ... store.edn descriptor fields; replay mismatch path"; "UNTESTED: ... concurrent same-edge add + rebase edge-set merge; corrupt log line; partial heads.edn write; missing object file; two real processes; store.edn with another hash algorithm; commit! retry limit" (`:7`, `11`).

## runs/subagents/20260829-174837-…-code-standards…/result-summary.md
Estado: archivado.

- [8] "src/sldb/kernel/ports.cljc — Hasher, TextNormalizer, IdMinter protocols; host value {:hasher :text :ids}"; "src/sldb/host/default.cljc ... kernel namespaces (canon, node, pool, edge, tree, plan, revision, store) now take `host`" (`result-summary.md:7-9`).

## runs/subagents/20260829-175429-…-harden-test-suite…/result-summary.md
Estado: archivado.

- [8] "store failure modes (descriptor fields, missing object, corrupt log line, hash-alg mismatch)" entre los tests añadidos (`result-summary.md:9`).
- [8/9] Bugs del kernel encontrados: "apply-plan executed ops before check 1 ... instead of yielding the promised ConflictSet"; "ConflictSet kind precedence: a replaced node was reported :removed-target; :superseded-target now wins"; "heads/commit! retried a fixed plan (stale :base) only 3 times — now accepts a plan-building function and retries up to max-attempts (32)"; "fs-store read-log: a corrupt line surfaced as a raw reader exception — now :store/corrupt-log with the line number" (`:13-16`).

## runs/subagents/20260829-175435-…-spec2viz-diagrams…/result-summary.md
Estado: archivado.

- [8] "docs/architecture/spec2viz/v2-rings.yml (component), v2-cas-objects.yml (component), v2-transaction-flow.yml (sequence), v2-replace-reanchoring.yml (activity), v2-store-layout.yml (activity)" (`result-summary.md:6`). (Las specs v2 viven fuera del archivo.)

## runs/subagents/20260829-180407-…-milestone-4-…-zero-context-audit/triage.md
Estado: archivado. Gate de milestone 4 (Markdown CST → AST).

- [5] "marks live on the block and enter its hash; `_` is never emphasis in the profile (flanking `_` degrades to opaque); canonical paragraphs are single-line (soft breaks become spaces)" (`triage.md:2`).
- [2] Round-1 rewrites fijan "§8 ast->plan / store->ast / markdown->plan / store->markdown signatures" (`round-1/triage.md:3`).

## runs/subagents/20260829-181915-…-milestone-4…/result-summary.md
Estado: archivado.

- [3/5] Cambio de modelo forzado: "Trees are now trees of POSITIONS (Git-like): tree.cljc rewritten (positions with per-position hash = dirty marker, identical subtrees share objects, detach/move by path), plan ops address ownership by path ... conflicts per parent path"; "trees.edn root hash unchanged (tree objects kept their format)" (`result-summary.md:13-14`).

## runs/subagents/20260830-012800-…-milestone-5a-anchor-states-zero-context-audit/{triage,round-1/triage,round-2/triage,round-3/triage}.md
Estado: archivado.

- [6] §6.1-§6.5 fijados por el gate: "`:from` is the successor, `:to` the replaced node; the pair is `[old new]`; re-anchoring runs after the edge joins the active set; the idempotent branch never re-anchors" (`round-1/triage.md:9`).
- [6] "errors are raised as kernel `ex-info`"; "`:positions` ... every position of the node in every tree of the revision, trees by id ascending, paths in document (pre-)order"; "both [`:as-from`/`:as-to`] are `anchor/state` maps ordered by edge id" (`:10-12`).
- [6] "`reconcile` needs the position index ... `anchor/placements` added as a public query"; "an endpoint that is not in the pool is `orphan`; `add-edge` makes it unreachable, but `replay` must not depend on that" (`:21-22`).
- [8] milestone 5a closure: "`src/sldb/kernel/anchor.cljc` (new, ring 0): the seven derived queries of `docs/v2/02 §6.3` — `placements`, `endpoint-state`, `state`, `states`, `anchored-in`, `report`, `diff` — plus `anchor-types` and `state-order`. Pure over `(store, revision)`, nothing persisted (invariant 18)" (`…-milestone-5a-anchor-states/result-summary.md:8-10`).

## runs/subagents/20260830-021500-…-milestone-5b-…-zero-context-audit/{triage,round-1/triage,round-3/triage}.md
Estado: archivado.

- [6] "the input is `reconcile/orphans`, `{node-id [edge-id …]}` over every orphan endpoint; both endpoints of a doubly-orphan anchor are reconciled independently; the edge ids ride along in `:edges`"; "a method that does not apply yields no candidate and never an error" (`round-1/triage.md:5-6`).
- [4] "`:markdown/root-changed` ... the profile keeps the root invariant *and* the function verifies it; re-rooting would need a `:replace`, which records a succession the surface must not invent" (`:7`).
- [6] "`:base` becomes obligatory there and its absence raises `:reconcile/base-required`" (`round-2` citado en `round-3/triage.md` y `round-1`).

## runs/subagents/20260830-023000-…-milestone-5b…/result-summary.md
Estado: archivado.

- [6] "`src/sldb/kernel/reconcile.cljc` (new, ring 0), `docs/v2/02 §6.5`: `dice` over the multiset of grapheme trigrams of the NFC text ...; `orphans`, the index `{node-id [edge-id …]}` ...; `proposals` with the three methods `:position`, `:fingerprint` and `:sample` ...; and `accept-plan`, which returns the plan and applies nothing" (`result-summary.md:8-14`).

## runs/subagents/20260830-235820-…-milestone-6-…-zero-context-audit/… y 20260831-000438-…/…
Estado: archivado.

- [5] "in this kernel a node id IS its content hash, so leaf-id and leaf-hash denote the same value. ... the executor keys the memo cache on the leaf node id (content hash)" (`20260830-235820-…/round-1/triage.md:15`).
- [5/8] "Las capas deterministas (grafemas, palabras, oraciones) son arrays de offsets cacheados por `leaf-id`, no nodos ni aristas" (`round-1/lane-C.md:118-122`).
- [7] Riesgo residual del milestone 6: "The layer cache is a process-global atom keyed by content-hash leaf id; correct because a leaf id fixes its text, but it is not bounded/evicted (acceptable for the current in-memory kernel; a GC/eviction concern belongs to a later milestone)" (`20260831-000438-…/executor-report.md:74-76`).

---

# 11. desk/ (store-relevante, vigente en el repo)

## desk/atoms/atom-smg-node-pool.md
Estado: vigente-desk (epoch:v2, autoridad v2).

- [1] "SLDB v2 persists one pool of content-addressed, immutable nodes of three classes: signs (S ...), symbols (M ...) and facts (G ...). A node's id is the hash of its canonical content, so the node is the symbol, not its occurrence" (`:16`); "S, M and G share the pool; they are node classes and index families, not separate stores" (`:16`).

## desk/atoms/atom-smg-as-persisted-layers.md
Estado: vigente-desk (epoch:v2).

- [1/6] "S, M and G stop being an in-memory IR (v1 smg-ir) or separate stores (SLDB=S, KGDB=G per the 2026-06-19 ADR) and become the persistence model itself: three node classes and three index families over one pool, joined by binding (S to M) and projection (M to G) edges that carry evidence. A G index such as a Matrix W_i is another tree over the same pool" (`:16`).

## desk/atoms/atom-tree-as-index.md
Estado: vigente-desk (epoch:v2).

- [2/3] "A tree ... is a tree of POSITIONS over the shared node pool, not a container of nodes: each position holds a node id and an ordered vector of child positions, and the identity of a position is its path" (`:16`).
- [6] "The Merkle root of a tree hashes only its own tree objects and node ids, so no other edge type can create hash cycles. This mirrors Git: one blob referenced from many paths and many trees" (`:16`).

## desk/atoms/atom-tree-identity-tree-objects-and-heads.md
Estado: vigente-desk (epoch:v2).

- [3] "A tree has a nominal id (ULID minted by :new-tree, like a Git ref), never a content hash; its descriptor {:tree :kind :name :root} is a CAS object referenced by the revision tree-set" (`:16`).
- [5/7] "For every POSITION there is a tree object {:node id :children [[child-id child-tree-hash] ...]} in sibling order; tree-hash = H(canonical-bytes(object)); ... merkle-root = tree-hash(root); sibling order therefore enters the hash, and identical subtrees share one object"; "Lazy Merkle: a position without hash is dirty; ownership ops ... clear the hash ...; commit recomputes only those in post-order" (`:16`).
- [4] "Heads are {tree-id -> revision-id} updated by compare-and-swap per tree; a document head is the head of its :document tree" (`:16`).
- [2] "Non-ownership edges are persisted as edge objects and referenced by the revision edge-set; reachability for verify/GC follows tree objects and edges from the heads" (`:16`).

## desk/atoms/atom-canonical-content-and-node-hashing.md
Estado: vigente-desk (epoch:v2).

- [3] "id(node) = H(canonical-bytes({:class :kind :content}))"; "class and kind enter the hash ...; address, provenance, timestamps and evidence do not" (`:16`).
- [2] "Per-class content shapes: :sign/:text {:text}, :sign/:block {:format :type :attrs}, :sign/:opaque {:format :blob}, :sign/:external {:locator :sample :fingerprint}, :sign/:span {:leaf :range}, :symbol/:term {:name :lang}, :symbol/:proposition {:form [...]}, :fact/:triple {:subject :predicate :object :context}, :fact/:context {:name}"; "In milestones 0-3 M and G are data shapes only" (`:16`).
- [3] "canonical-bytes is canonical EDN: map keys sorted by printed form, sets sorted, vectors in order, no metadata, no extra whitespace, strings NFC UTF-8, dates as ISO-8601 strings" (`:16`).

## desk/atoms/atom-revision-id-edge-set-and-diff.md
Estado: vigente-desk (epoch:v2).

- [2/4] "Revision = {:roots {tree-id merkle-root} ... :trees (id of the tree-set object = H of the sorted vector of [tree-id descriptor-id] ...), :edges (id of the edge-set object = H of the sorted vector of active edge ids; each edge is stored individually at objects/<edge-id>), :parents, :tx, :actor, :engines, :timestamp}; revision-id = H(canonical-bytes) of exactly those eight fields; provenance is derived from tx and actor" (`:16`).
- [3] "Transaction = {:id H(resolved-plan) :plan :revision}" (`:16`).
- [4] "diff r1 r2 returns {:trees {tree-id {:added :removed :moved}} :edges {:added :removed} :superseded [[old new]...]} computed by descending only into tree objects whose hashes differ" (`:16`).

## desk/atoms/atom-conflictset-on-failed-compare-and-swap.md
Estado: vigente-desk (epoch:v2).

- [4] "When the CAS on a head fails, the kernel computes the delta base->head and compares it with the plan. It is a conflict when both edit the child set of the same (tree, parent), or when the plan references a node that head replaced or removed. If the touched trees are disjoint, the plan is rebased on head automatically and the CAS retried" (`:16`).

## desk/atoms/atom-transactionplan-edn-schema-and-validation.md
Estado: vigente-desk (epoch:v2).

- [2] Plan = "{:plan/version 1 :base <rev|nil> :actor <str> :engines {...} :ops [...]}"; ops: `:new-tree`, `:add-node`, `:add-edge` (ownership address by position path), `:remove-edge`, `:replace`, `:move`, `:detach`; `:as` aliases (`:16`).
- [2/8] Siete checks all-or-nothing: base-cas, ids-exist, tree-integrity, evidence-ref-hash, capability, opaque-replace-only, pure-data (`:16`).
- [8] "capability: {:op x} authorizes x in any tree, {:op x :tree t} only in t; ... an actor absent from the store capabilities map is rejected" (`:16`).

## desk/atoms/atom-identity-is-the-content-hash-succession-is-a-recorded-edge.md
Estado: vigente-desk (epoch:v2).

- [1/3] "Node identity is trivial and exact: same canonical content, same id, in any tree and any revision. Identity across edits is not identity but succession: the transaction that replaces X by X' records a supersedes edge ... Heuristic reconciliation ... is used only for changes made outside the kernel, and its result is marked drifted with a confidence value" (`:16`).

## desk/atoms/atom-git-object-model-as-the-v2-roadmap.md
Estado: vigente-desk (epoch:v2).

- [7/8] "blobs (content-addressed nodes), trees ..., commits ..., refs (document and index heads with compare-and-swap), staging (validated TransactionPlan), **packfiles and gc (content-addressed store and retention)**, remotes (pool sync, out of scope)"; milestones 0-3 = blobs, trees, commits, persistence; luego "first surface ... evidence and anchor states, stand-off layers, M and G with a Matrix adapter, retention and GC, and finally sync" (`:16`).

## desk/atoms/atom-decision-trees-are-trees-of-positions-git-like.md
Estado: vigente-desk (epoch:v2).

- [8] "the one-parent-per-node-per-tree rule proved unrepresentable: every list item is the same node ... and identical paragraphs repeat, so ast->plan failed with node-already-in-tree. The user chose the Git-like model over occurrence discriminators: a tree is a tree of positions identified by path"; "Invariant 5 now reads: a node may occur at N positions of N trees, each position has one parent"; "Milestones 1-2 were revised accordingly ... trees.edn root hash unchanged" (`:16`).

## desk/atoms/atom-first-slice-runtime-choices.md
Estado: vigente-desk (epoch:v2).

- [8] "SHA-256 through a host Hasher protocol ... BLAKE3 later, algorithm recorded in the store descriptor"; "persistence to files only (objects/<hash> CAS, log.edn append-only one transaction per line, heads.edn written atomically by rename)" (`:16`).
- [8] "evidence = bb test output in runs/subagents/<run>/validation.log plus a commit; Babashka >= 1.3 required" (`:16`).

## desk/atoms/atom-decision-first-slice-kernel-semantics-confirmed.md
Estado: vigente-desk (epoch:v2, provenance = triage del gate).

- [8] Confirma las cinco decisiones de `160607/triage.md` y añade: "These are user decisions, not implementation conveniences, and later milestones must not silently revisit them" (`:16`).

## desk/atoms/atom-decision-no-rust-in-the-repository.md
Estado: vigente-desk (epoch:v2).

- [8] "Rust adds nothing ... a prior Rust implementation was already closed and removed. Where Rust genuinely leads (tree-sitter, BLAKE3, WASM sandboxing) the kernel consumes WASM artifacts ... This retires decision-rowan-ast, decision-rusqlite-store, decision-rayon-parallelism and decision-pyo3-ffi" (`:16`).

## desk/atoms/atom-decision-pure-cljc-kernel-with-hosts-as-adapters.md
Estado: vigente-desk (epoch:v2).

- [1] "The kernel is written as pure Clojure .cljc (data plus functions, no I/O) so it runs unchanged on Babashka ..., ClojureScript on Node ... and the JVM"; "Python remains an optional orchestration adapter via subprocess or HTTP, not FFI" (`:16`).

## desk/atoms/atom-canonical-projected-or-asserted.md
Estado: vigente-desk (epoch:v2).

- [1/2] "Determinism decides authority. Markup structure and UAX #29 boundaries are deterministic from the text and therefore canonical. Anything produced by an engine ... is a projection: content-addressed by (input hash, engine, version), computed once, never recomputed on engine upgrade unless requested"; "Derived data has no authority and is rebuildable from the log and the pool" (`:16`).

## desk/atoms/atom-addressability-class-per-node.md
Estado: vigente-desk (epoch:v2).

- [2/3] "Each node declares one addressability class ... structural: canonical, idempotent, editable by transaction, anchored by node identity. opaque: preserved but not modeled ... a transaction may only replace the whole blob. external: content outside the store, anchored with locator, sample and fingerprint" (`:16`).

## desk/atoms/atom-external-fingerprint-form-and-who-computes-it.md
Estado: vigente-desk (epoch:v2).

- [3] "The fingerprint is written as the store hash algorithm name, then a colon, then the lower case hex digest; the kernel validates only that form and compares for equality, because it performs no I/O and never recomputes a digest" (`:16`).

## desk/atoms/atom-evidence-required-per-edge-type.md
Estado: vigente-desk (epoch:v2).

- [2] "Edge = {:type :from :to :tree :order :evidence}; Evidence = {:ref-hash, exactly one origin (:actor or :engine+:version), :context, :status}. Edge id = H(canonical-bytes(Edge)); the timestamp is NOT part of the edge"; "ownership none (represented by tree objects); supersedes :actor; reference :ref-hash + origin; binding ... + :context; projection adds :status ...; semantic ...; derived :ref-hash + :engine + :version" (`:16`).

## desk/atoms/atom-re-anchoring-rules-under-succession.md
Estado: vigente-desk (epoch:v2).

- [6] "ownership edges are rewritten by the replacing transaction itself; reference and binding edges ... follow the successor automatically ...; semantic edges are not followed and stay superseded ...; projection edges stay superseded pending revalidation ...; derived edges are invalidated and recomputed on demand; supersedes edges chain" (`:16`).

## desk/atoms/atom-evidence-edges-and-anchor-states.md
Estado: vigente-desk (epoch:v2).

- [6] "Mutation detection compares each anchor against a revision and yields a deterministic state per endpoint, combined into the worst of the two — intact when the referent still resolves, superseded when an active supersedes edge names a successor, orphan when it no longer resolves ... Drifted is a refinement of orphan produced by reconciliation, not a fourth parallel state" (`:16`).

## desk/atoms/atom-anchor-state-is-derived-and-computed-per-endpoint.md
Estado: vigente-desk (epoch:v2).

- [6] "Every anchor query is derived — recomputed from revision plus pool, never persisted, and reproducible from the log" (`:16`).
- [6] "Resolving means occupying a position in some tree of the revision for ordinary signs, recursing to the leaf and checking the range against its grapheme count for spans, and merely existing in the CAS for symbols and facts, which is a first-slice concession the milestone 7 tightens to membership of a context tree" (`:16`).

## desk/atoms/atom-drifted-is-a-reconciled-orphan.md
Estado: vigente-desk (epoch:v2).

- [6] "A proposal lives outside the pool, being neither node nor edge, until an actor accepts it; accepting is an ordinary transaction that adds a supersedes edge whose only evidence is that actor" (`:16`).

## desk/atoms/atom-stand-off-addresses-are-virtual-until-referenced.md
Estado: vigente-desk (epoch:v2).

- [5] "An address is [leaf-id start end] in UAX #29 grapheme offsets over the leaf's NFC text and resolves without any node. A :span node {:leaf :range} is materialized only when an edge needs an endpoint there; being content-addressed, the same span requested twice is the same node. Deterministic layers (graphemes, words, sentences) are offset arrays cached by leaf id, not nodes" (`:16`).

## desk/atoms/atom-stand-off-annotation-below-the-paragraph.md
Estado: vigente-desk (epoch:v2).

- [5] "The canonical leaf is therefore the paragraph's plain text with grapheme offsets, and everything else is a typed span layer over it ... syntax and morphology (engine projections keyed by leaf hash, engine and version)" (`:16`).

## desk/atoms/atom-w-i-context-node-and-index.md
Estado: vigente-desk (epoch:v2).

- [6] "W_i (a Matrix logical context) is both a G-class node of kind :context ({:name}) and a tree-index of kind :context over the pool ... Binding (S to M) and projection (M to G) edges name their W_i in evidence; ... one edge per context. In milestones 0-3 a W_i is only a node plus a tree; no matrix evaluation" (`:16`).

## desk/atoms/atom-replace-semantics-inside-a-transaction.md
Estado: vigente-desk (epoch:v2).

- [5] "X-prime takes exactly position p ... and inherits its subtree unless the plan moves it; other occurrences of X in t are untouched because replacement is per position; a supersedes edge X-prime -> X is created with the plan actor; the re-anchoring rules are applied inside the same transaction"; "other trees containing X are untouched" (`:16`).

## desk/atoms/atom-inline-marks-live-on-the-block-text-leaves-stay-plain.md
Estado: vigente-desk (epoch:v2).

- [5] "A heading or paragraph maps to a :sign/:block node whose :attrs carry :marks, a vector of [kind start end] ... spans in grapheme offsets over the block's single :sign/:text child, which holds the plain NFC text without markup"; "This keeps text leaves shareable across documents (same text, same node)" (`:16`).

## desk/atoms/atom-lossless-cst-and-canonical-render-for-markdown.md
Estado: vigente-desk (epoch:v2).

- [5] "cst/parse is total and lossless: a vector of blocks with their original lines ... so concat reproduces the bytes (invariant 8). The AST is the lossy neutral form; render(ast) emits one canonical spelling ... so parse(render(A)) == A for every canonical A (invariant 9)" (`:16`).

## desk/atoms/atom-reversibility-scope-in-v2.md
Estado: vigente-desk (epoch:v2).

- [4/9] "Do not promise round-trip between the canonical model and its surfaces. Inside S, text to concrete syntax tree is reversible by construction ... S to M is a mapping under context ... M to S is never an inverse ... The guarantee that replaces exact render equality is: reversible in S, traceable through S-M-G, generative back; plus transparency (what is anchored in what) and mutation detection" (`:16`).

## desk/atoms/atom-addressability-report-per-document.md
Estado: vigente-desk (epoch:v2).

- [8] "report(ast) returns {:blocks :structural :opaque :coverage :opaque-regions [{:index :format :lines}]} where coverage is the fraction of graphemes that live in :text leaves ...; it is how the tool states its limits" (`:16`).

## desk/atoms/atom-sldb-md-profile-a-closed-canonical-markdown-subset.md
Estado: vigente-desk (epoch:v2).

- [5] "Anything outside the profile is preserved byte-for-byte as an opaque node and never descended ... The profile is what makes parse(render(A)) == A a provable property and the addressability report exact" (`:16`).

## desk/atoms/atom-decision-no-separate-lisp-metalanguage.md
Estado: vigente-desk (epoch:v2). No dice nada del store (solo "Lisp compiles to plan families" en sentido general); se lista como revisado y descartado.

---

# 11b. desk/atoms — átomos legados de store (vigentes-desk, sin `epoch:v2`)

## desk/atoms/graph-store.md
- [1/2] "The graph store is the persistent kernel repository for canonical documents, nodes, edges, revisions, and transaction lineage"; "Its sources of truth are the transaction log, revision store, content-addressed store, and per-document heads"; "Petgraph, indexes, embeddings, caches, and semantic projections are rebuildable materializations, not primary persistence"; "The store is revision-aware" (`:16-23`).

## desk/atoms/content-addressed-store.md
- [2/3] "persists payloads and derived artifacts under deterministic hashes so content identity stays decoupled from mutable locations"; "The same canonical bytes should map to the same stored hash key"; "Canonical node hashes and revision hashes may reference content-addressed payloads without duplicating them inline" (`:16-23`).

## desk/atoms/transaction-log.md
- [1/2] "the append-only persistent record of every committed kernel transaction"; "Past transactions are never edited in place"; "The log is one of the sources of truth from which indexes, caches, and some projections may be rebuilt"; "Recovery after crash must be defined in terms of replayable committed log entries" (`:16-23`).

## desk/atoms/revision.md
- [2/4] "A revision is an immutable document state produced by one committed transaction and identified by a deterministic root hash plus explicit parent lineage"; "A revision records parents, transaction provenance, creation time, and root hash"; "Invalid states such as parse failures may still be recorded as revisions" (`:16-24`).

## desk/atoms/document-head.md
- [3] "A document head is the mutable pointer from a document identity to its current revision, updated by compare-and-swap during commit"; "A head is not the source of truth by itself" (`:16-23`).

## desk/atoms/storage-backend.md
- [8] "A storage backend is the replaceable persistence adapter that implements the kernel repository contracts for logs, revisions, heads, and content-addressed payloads"; "redb, Cozo, and in-memory stores are backend strategies, not kernel identity" (`:16-23`).

## desk/atoms/store-infrastructure.md
- [1/2] "The persistent core is append-only transaction history, immutable revisions, content-addressed payloads, and document heads"; "Rebuildable services include petgraph views, search indexes, semantic projections, hash caches"; "Backends must be replaceable behind a storage trait" (`:16-22`).

## desk/atoms/store-integrity-checks.md
- [8] "`stores check` reports drift or breakage in tracked state without modifying content, and `stores update` rebuilds indexes after bulk changes" (`:16`).

## desk/atoms/store-recovery.md
- [8] "the set of replay, verify, rebuild, restore, and repair flows ... after crash, migration, degradation, or corruption"; "Recovery must distinguish canonical replay from rebuildable index regeneration" (`:16-22`).

## desk/atoms/corruption-state.md
- [8] "the explicit condition where canonical persistence invariants can no longer be tclojureed without repair, replay, or restoration"; "Rebuildable caches may be discarded, but corruption of canonical logs, revisions, heads, or required payloads is a first-class incident" (`:16-22`).

## desk/atoms/degraded-mode.md
- [8] "the explicit runtime state where the kernel remains usable for a bounded subset of operations while one capability, index, provider, or adapter surface is unavailable"; "Rebuildable services such as indexes, embeddings, or projections may degrade independently from canonical revision integrity" (`:16-22`).

## desk/atoms/garbage-collection.md
- [8] "the controlled process that reclaims unreachable or expired stored state without violating revision integrity, retention guarantees, or provenance requirements"; "GC must distinguish canonical history from rebuildable caches and derived artifacts"; "GC should operate over revisions, payloads, projections, embeddings, effects, and caches with different rules" (`:16-22`).

## desk/atoms/retention-policy.md
- [8] "defines which revisions, payloads, projections, embeddings, logs, and effects must be kept, may be pruned, or must be pinned"; "Reachable, pinned, and orphaned state need explicit distinction" (`:16-20`).

## desk/atoms/backup-export.md
- [8] "the backend-independent package of canonical transactions, revisions, payloads, schemas, versions, and hashes needed to preserve and move a repository safely"; "Export must not depend on one storage engine's internal format" (`:16-20`).

## desk/atoms/compatibility-surface.md
- [8] "the versioned contract that must remain intelligible across kernel evolution for persistence, canonical model, operations, protocols, schemas, projections, or plugins"; "Compatibility should be versioned by surface, not assumed to share one global version" (`:16-19`).

## desk/atoms/migration-unit.md
- [8] "the smallest versioned surface that can be upgraded, replayed, transformed, or verified independently"; "Examples include store format, canonical schema, command protocol, projection cache, or plugin contract" (`:16-19`).

## desk/atoms/what-a-store-is.md
- [1] "A store is SLDB's metadata workspace for models, tracked docs, integrity, and indexes; it does not replace Markdown files or become the text source of truth"; "The store holds metadata about documents rather than the documents themselves" (`:16-20`).

## desk/atoms/local-vs-global-store-precedence.md
- [3] "A project-local `.sldb/` is the normal workflow location; a global `~/.sldb/` may also exist, and the local store wins when both are present" (`:16`).

## desk/atoms/dependency-index.md
- [5] "records structural and derived dependencies so recomputation, validation, and projection updates can happen incrementally"; "It is essential for scalable projection and cache logic" (`:16-22`).

## desk/atoms/temporal-layer.md
- [4] "represents validity windows, revision timing, and time-aware lineage around canonical units" (`:16`).

## desk/atoms/provenance-record.md
- [4] "captures where canonical data came from and how it changed over time"; "The target architecture requires lineage beyond git-only history" (`:16-22`).

## desk/atoms/artifact.md
- [2] "a persisted payload or derived result attached to canonical state with explicit source hashes, engine versions, and provenance"; "Artifact identity must stay separable from mutable filenames or adapter-specific storage handles" (`:16-22`).

## desk/atoms/derived-index.md
- [2] "any materialized query-oriented or projection-oriented index built from the canonical graph store rather than acting as source of truth"; "Section, field, search, and semantic indexes are derived indexes" (`:16-20`).

## desk/atoms/merkle-index.md
- [5] "organizes node and subtree hashes into a structural integrity and incremental recomputation layer"; "It should derive from canonical structure rather than file timestamps" (`:16-22`).

## desk/atoms/hash-field.md
- [3] "the node field that stores the hash value associated with a canonical unit"; "Hash is modeled as node data, not as a separate semantic node" (`:16-20`).

## desk/atoms/ast-persistence.md
- [2] "the durable storage of canonical AST nodes and their core data in the local graph store"; "It must use existing libraries rather than ad hoc invention" (`:16-22`).

## desk/atoms/immutable-append-only-database.md
- [1] "The `GraphStore` and its historical logs (`HistoryArtifacts`) operate in an 'Append-Only' and immutable fashion. This design cleanly resolves concurrency issues, as writes do not mutate existing records, ensuring lock-free reads and auditable, incremental state transitions" (`:14`).

## desk/atoms/cache.md
- [5] "Cache stores rebuildable derived artifacts keyed from canonical structure and dependency-aware invalidation"; "It should rely on canonical hashes and dependencies rather than ad hoc timestamps" (`:16-20`).

## desk/atoms/snapshots.md
- [4] "durable captured states of canonical structure or derived runtime state used for reconstruction, comparison, and temporal workflows"; "They belong beside the AST as infrastructure" (`:16-22`).

## desk/atoms/append-only-event-log.md
- [4] "records canonical changes over time as durable system-level history beyond git alone"; "It is a history infrastructure component, not a user-facing projection" (`:16-22`).

## desk/atoms/decision-graph-store-over-yaml-indexes.md
- [1] "moves from a YAML-index-first store to a graph-store-first local `.sldb/` because canonical nodes, typed edges, anchors, and relation payloads need a native persistent graph shape"; "YAML indexes worked as inspectable v1 metadata, but they do not scale cleanly to graph-native canonical state" (`:16-20`).

## desk/atoms/decision-search-index-library.md
- [8] "Search and semantic indexing are derived services behind kernel-controlled interfaces rather than fixed primary technologies"; "Index technology must not leak into node identity, transaction semantics, or revision compatibility" (`:16-22`).

## desk/atoms/decision-links-and-anchors-are-canonical.md
- [3] "Links and anchors are modeled as canonical parts of the target system because they are core addressability and relation primitives"; "External anchoring requires explicit canonical payload such as path, hash, sample, and locator data" (`:16-22`).

## desk/atoms/relation-ast.md / relation-ast-extensibility.md / decision-relation-ast-extensibility.md / store-edge.md
- [2] "A relation AST is the structured payload carried by a canonical relation so relations can evolve as extensible first-class entities"; "It is key to the graph-native store design" (`relation-ast.md:16-22`).
- [2] "Extensibility resides entirely in the Clojure environment. The `RelationASTs` payloads ... are materialized and indexed to generate semantic graphs, the store, and tags" (`relation-ast-extensibility.md:14`).
- [2] "Relations carry extensible relation-AST payloads because thin fixed edges would make the graph model too rigid" (`decision-relation-ast-extensibility.md:16`).
- [2] "A store edge is the canonical edge type that records store-level relationships between ASTs in the graph store" (`store-edge.md:16`).

## desk/atoms/graph-projection.md
- [6] "Graph projection derives graph-shaped views from canonical nodes, links, anchors, provenance, and semantic relations"; "Graphs are downstream views, not the canonical store" (`:16-20`).

## desk/atoms/canonical-identity.md / canonical-address.md / derived-address.md / source-document-hash.md / document-path.md / document-source.md / node.md / ownership-edge.md / reference-edge.md / derived-edge.md / tracked-document-identity.md / repository-registry.md / semantic-export-boundary.md
- [3] "The AST explicitly carries stable document and node identity"; "Provenance, hashes, anchors, and projections depend on stable identity" (`canonical-identity.md:16-22`).
- [3] "A canonical address is the primary stable address of a canonical unit inside the AST and graph store"; "It should remain valid across projections" (`canonical-address.md:16-22`).
- [3] "A derived address is a surface-specific or projection-specific address that resolves back to a canonical address" (`derived-address.md:16`).
- [3] "source-file hash stored with an anchor so the system can detect changes in the anchored external document" (`source-document-hash.md:16`).
- [3] "A document path is the source-path field carried by an anchor"; "It is not sufficient by itself to guarantee attachment stability" (`document-path.md:16-22`).
- [1] "Source metadata may provide cheap change hints, but canonical confirmation must come from content and hashing"; "Filesystem, Git, and HTTP are source implementations rather than canonical models" (`document-source.md:16-22`).
- [2] "A node is the primary canonical entity stored inside a document revision, carrying stable identity, a type, payload references, and metadata"; "Nodes are revisioned through transactions, not edited in place" (`node.md:16-22`).
- [6] "Only ownership edges participate directly in structural subtree hashing" (`ownership-edge.md:16-22`).
- [6] "Reference cycles must not break hashing because they are outside the ownership recursion" (`reference-edge.md:16-22`).
- [6] "Derived edges express lineage without making derived artifacts primary truth" (`derived-edge.md:16-22`).
- [3] "A tracked doc has both a logical store name and a physical file path" (`tracked-document-identity.md:16`).
- [8] "records repository-local model, document, and workspace participation"; "not canonical truth" (`repository-registry.md:16-22`).
- [1] "`stores semantic-export` emits SLDB-owned semantic document truth for graph consumers, while downstream systems add workflow-specific and code-dependency edges" (`semantic-export-boundary.md:16`).

---

# 12. desk/ — tasks, contexts y rutinas (store-relevante)

## desk/tasks/task-specify-graph-store-contract.md
Estado: vigente-desk; `status: closed` (contrato reanclado al archivado `graph-store-contract.md`).

- [1] "Anchored in `immutable-append-only-database.md`. The local store is the foundation of system integrity"; "every node remains uniquely identifiable via its content hash" (`:34`).
- [1/8] Goal original: "Mandate a kernel-owned `StorageBackend` boundary with replaceable engines (`redb` default, `CozoDB` alternative) and `Blake3` for content-addressed storage" (`:40`).
- [9] Nota de normalización: "Legacy engine mandates (SQLite/FTS5) were removed"; "persistence sits behind a replaceable kernel-owned `StorageBackend` (`redb` default, `CozoDB` alternative); no fixed engine is mandated" (`:78-80`).
- [2] In-Scope: "`ASTPersistence`, `RelationEdges`, and `AnchorNodes` storage"; "`DerivedIndexes` (Section, Field, Search, Semantic)"; "`HistoryArtifacts` (Snapshots and provenance)" (`:46-50`).

## desk/tasks/task-specify-query-and-index-parity-contract.md
Estado: vigente-desk; `status: closed`.

- [2] "`SectionIndex`, `FieldIndex`, and `SemanticIndex` (tag-based). `SearchIndex` projection behind a kernel-owned, replaceable search interface. Structural joins across `RelationEdges`" (`:46-49`).
- [7] "Performance Floor: Set point query targets (<10ms)" (`:60`).

## desk/tasks/task-specify-anchoring-contract.md
Estado: vigente-desk; `status: closed`.

- [3] "Define the taxonomy and resolution lifecycle for anchors. Mandate the use of `stable-selector.md` logic" (`:39`).
- [3] In-Scope: "`AnchorNode` structure including `SourceHash` and `Locator`"; "`TextAnchors` vs `ASTAnchors`"; "`FamilySpecificLocators`" (`:45-48`).

## desk/tasks/task-unreachable-objects-report-and-gc-groundwork.md
Estado: vigente-desk; `status: active` (promovida desde drawer/inbox).

- [8] "verify only checks reachable objects; add a report of unreachable objects in objects/ (garbage candidates) as groundwork for milestone 8 retention and GC" (`:45`).
- [8] "Triage and resolve the inbox message promoted from `desk/inbox/20260829-174026-suggestion-unreachable-objects-report-and-gc-groundwork.md`" (`:39`).

## desk/tasks/task-contention-test-for-heads-commit.md
Estado: vigente-desk; `status: active`.

- [4] "Exercise sldb.kernel.heads/commit! under real concurrent writers (futures on JVM, separate bb processes on the file backend) and prove the CAS retry and ConflictSet paths; also two processes committing to the same directory" (`:45`).

## desk/tasks/task-node-host-parity-for-the-v2-kernel.md
Estado: vigente-desk; `status: active`.

- [8] "implement sldb.host.* cljs branches (hash via crypto, NFC via String.normalize, ulid via crypto.randomBytes, fs-store via fs), prove the golden fixtures yield identical ids on both hosts. Deferred from the first slice" (`:45`).

## desk/tasks/task-external-oracle-for-canonical-bytes.md
Estado: vigente-desk; `status: active`.

- [5/8] "A second, independent implementation of canonical-bytes (Python script scripts/canon_oracle.py) computing SHA-256 over the same fixture inputs so conformity is not circular; compare against test/fixtures/nodes.edn and trees.edn ids" (`:45`).

## desk/tasks/task-ulid-monotonicity-and-clock-adapter.md
Estado: vigente-desk; `status: active`.

- [3] "ULIDs are time-prefixed but not monotonic within the same millisecond; decide whether tree ids need monotonic ULIDs and move time behind a Clock port in sldb.kernel.ports" (`:45`).

## desk/tasks/task-test-coverage-and-lint-tooling-for-babashka.md / task-rewrite-the-pre-v2-spec2viz-specs.md
Estado: vigentes-desk; `status: active`.

- [8] `task-test-coverage...:45`: "Evaluate clj-kondo (lint), cloverage or a bb-compatible coverage approach, and a JVM run of the suite".
- [8] `task-rewrite-the-pre-v2-spec2viz-specs.md:45`: "docs/architecture/spec2viz/target-*.yml describe the previous stage (Rust/Lisp era vocabulary in places); rewrite or retire them once the v2-*.yml specs exist".

## desk/tasks/Board.md
Estado: vigente-desk (formato legacy, no editable por CLI; `AGENTS.md`).

- [8] Rutea tareas activas: external oracle, contention test, ULID monotonicity, unreachable-objects/GC, rewrite spec2viz, node host parity, test coverage (`Board.md:8-14`, `42-47`).

## desk/contexts/pill-guardrail-v2-implementation-gate.md
Estado: vigente-desk.

- [8] "For epoch v2 implementation tasks, the applicable execution ritual is desk/rituals/ritual-zero-context-audit-gate.md; the legacy execution/testing/closeout rituals are archived under docs/archive/desk/rituals/ and do not apply" (`:16`).
- [8] "do not hand-edit the desk store (it is built from kimun.include.edn and desk/models/*.edn by `bin/kimun include build`)" (`:45`).

## desk/routines/routine-task-unreachable-objects-report-and-gc-groundwork.md
Estado: vigente-desk (rutina deskops; no dice nada del diseño del store).

- [8] Rutina de ciclo de vida de la tarea GC (execution-ready → activate → testing → closeout): `:9-23`.

## desk/inbox/ (notas de proceso, no de diseño)
Estado: vigente-desk.

- [8] `desk/inbox/20260829-174026-suggestion-unreachable-objects-report-and-gc-groundwork.md` es la fuente de la tarea GC (citada por la tarea; no legible directamente en esta pasada, solo referenciada).
- [9] `desk/inbox/20260829-161650-…-legacy-atoms-relinked-after-retirement.md`: "16 legacy atoms contradicting docs/v2 were moved to raw/source/atoms-retired/ and untracked".
- [8] `desk/inbox/20260901-100429-…-promotion-defaults-task-validation-to-pytest.md`: la promoción escribe "a hardcoded 'pytest' Validation line" en un repo cuyos gates son `bb lint / bb test / bb oracle`.

---

# Tabla de ejes (documento × eje)

Una frase por celda. "—" = el documento no dice nada de ese eje. Solo documentos con contenido sustantivo de store.

| documento | 1 Fuente de verdad | 2 Qué se persiste | 3 Punteros/identidad | 4 Historia/versionado | 5 AST | 6 Capa G/grafo | 7 Escala | 8 Decisiones | 9 Contradicciones |
|---|---|---|---|---|---|---|---|---|---|
| `graph-store-contract.md` (arch.) | TransactionLog+RevisionStore+CAS+Heads son "source of truth"; índices derivados "never authoritative" | grafo estructural completo, revisiones inmutables, payloads CAS, heads por documento | nodos indexados por Blake3; refs por hash; path | log append-only; cualquier versión reconstruible | dice "persist canonical structural AST data" pero sin decir si se recomputa | ownership vs reference/semantic/derived | insert O(log N); query <50ms <10k nodos | StorageBackend replaceable (redb default, Cozo alt.) | Blake3 y "document-scoped identity" quedan superseded por el banner |
| `target-system-overview.md` (arch.) | "pool of content-addressed nodes is [authority]; a document is a tree-index" | documentos, revisiones, nodos, aristas tipadas, AST, links/anchor nodes | hashes como campos canónicos | "append-only immutable revisioned graph" | "persist canonical structural AST data" | links/anchors canónicos, no proyecciones tardías | — | graph-store-first sobre YAML indexes | banner admite que "document as root" y render-exact ya no valen |
| `canonical-state-derivation-glossary.md` (arch.) | "Clojure-owned kernel state persisted in the canonical database" es la verdad | nodes, edges, revisions, types, metadata, provenance | — | revisions | — | derived index nunca autoridad | — | glossary freeze | dice "canonical database"; v2 persiste a ficheros |
| `anchoring-contract.md` (arch.) | anchors persistidos/validados | payload de anchor (path, hash, locator, sample) | `doc_id#section_id`, `L{line}C{col}`, stable-selector | validación por operación track/validate | — | — | — | broken anchor = validation error | superseded: v2 deriva anchor states por endpoint |
| `query-and-index-parity-contract.md` (arch.) | índice derivado, no verdad | section/field/link indexes, FTS | find by path/metadata/relation | — | — | joins sobre RelationEdges | <10ms punto, <100ms joins <50k aristas | FTS replaceable, no vector search | — |
| `target-store-graph.yml` (arch.) | TransactionLog/ContentStore source of truth | Documents/Revisions/Tx/Nodes/Edges/Artifacts + Compatibility/Backup/Retention/Rebuildable | heads, CAS | revisions por tx, log | — | ownership participa en Merkle, resto excluido | — | KernelRepository rebuilds RebuildableState | — |
| `target-runtime.yml` (arch.) | transaction_engine→local_database | content_store, tracked_files, backups, retention | — | — | — | — | — | redb/Cozo/memory; GC prune | — |
| `feature-canonical-ast-design-current-state.md` (retired) | AST es el centro; store es infraestructura | 11 capas del AST + sidecars | node_hash/subtree/dependency/materialization; selectors | provenance/temporal más allá de git | hashing como capa del AST | semantic layer + placeholders | embeddings en sidecars | small core + capability layers | pregunta abierta: capas obligatorias vs sidecars |
| `feature-sldb-explicit-target-architecture.md` (retired) | AST es source of truth; store deja de ser centro | AST + infraestructura (Merkle/cache/dependency/log/snapshots) | identidad estable | append-only event log | hashing/dependency hashes | graph export derived | — | legacy policy: no preservar superficies como arquitectura | — |
| `atom-smg-node-pool.md` (v2) | un pool de nodos content-addressed es la verdad | nodos S/M/G inmutables | id = hash del contenido | — | — | S/M/G son clases de nodo, no stores | — | — | — |
| `atom-tree-as-index.md` (v2) | pool es la verdad; árbol = índice de posiciones | posiciones (node id + children ordenados) | identidad de posición = path; Merkle solo tree objects | — | — | Merkle hashea solo aristas propias | — | Git-like | — |
| `atom-tree-identity-tree-objects-and-heads.md` (v2) | heads CAS por árbol | tree objects, descriptors, heads, edge objects | tree-id ULID nominal; tree-hash H(object) | heads {tree-id→revision-id} CAS | lazy Merkle con dirty set | reachability follows trees+edges from heads | — | — | — |
| `atom-canonical-content-and-node-hashing.md` (v2) | id = H(canonical-bytes) | contenido canónico por clase/kind | class/kind entran al hash; address/provenance/timestamps no | — | — | — | — | canonical EDN | — |
| `atom-revision-id-edge-set-and-diff.md` (v2) | revision-id = H de 8 campos | roots, trees, edges, parents, tx, actor, engines, timestamp | edge objects `objects/<edge-id>` | revisiones encadenadas; diff por descenso de hashes | — | edge-set | — | ocho campos hasheados | — |
| `atom-conflictset-on-failed-compare-and-swap.md` (v2) | CAS de head como coordinación | ConflictSet (base/head/plan/conflicts) | — | — | — | — | — | rebase automático si árboles disjuntos; sin merge semántico | — |
| `atom-transactionplan-edn-schema-and-validation.md` (v2) | plan validado es el único camino de escritura | ops sobre nodos/trees/edges | paths de posición; aliases | base-cas contra head | — | ownership vs no-ownership edges | — | 7 checks all-or-nothing | — |
| `atom-first-slice-runtime-choices.md` (v2) | — | files-only: `objects/<hash>`, `log.edn`, `heads.edn` | SHA-256; H(file)==name | CAS heads atómico por rename | — | — | — | primer slice | contradice Blake3/redb del material archivado |
| `atom-git-object-model-as-the-v2-roadmap.md` (v2) | — | blobs/trees/commits/refs/staging/packfiles+gc | content-addressed | refs con CAS; commits con parents | — | — | packfiles y gc para retención | roadmap Git | — |
| `atom-decision-trees-are-trees-of-positions-git-like.md` (v2) | — | posiciones; un nodo en N posiciones | path como identidad de posición | — | — | — | — | corrige invariante 5; trees.edn root hash intacto | — |
| `atom-anchor-state-is-derived-and-computed-per-endpoint.md` (v2) | estado de anchor derivado, nunca persistido | nada (derivado) | resuelve por posición en árbol/CAS | reproducible desde el log | — | M/G resuelven solo por existencia en CAS (concesión) | — | intact<superseded<orphan | — |
| `09-evaluador-anclado.md` (arch.) | índice + pool son el único backend; escribe por TransactionPlan | expresión como `:opaque edn/expr` + arista derived; outbox log | anchors de la KB; `resolve` cascade | `derive` validez = anchor/states | — | derived edges; ciclos ⇒ `:derive/cycle` | — | provenance en el pool, no en log aparte | — |
| `desk/atoms/atom-stand-off-addresses-are-virtual…` (v2) | — | nada hasta que se referencia; `:span` solo al usarse | `[leaf-id start end]` en grafemas | sucesión al cambiar la hoja | — | — | capas UAX #29 cacheadas por leaf id | — | — |
| `desk/atoms/graph-store.md` (legado) | log+revision store+CAS+heads son verdad | documentos, nodos, aristas, revisiones, tx | — | revision-aware | — | ownership vs no-ownership | — | — | "documentos" como raíz choca con `atom-tree-as-index` |
| `desk/atoms/what-a-store-is.md` (legado) | store = workspace de metadatos, no verdad textual | metadatos (no documentos) | — | — | — | — | — | — | "does not become the text source of truth" choca con el pool v2 |
| `desk/atoms/garbage-collection.md` (legado) | — | reclama estado inalcanzable | — | no viola revision integrity | — | distintas reglas por clase | — | GC por política, no ad hoc | — |
| `desk/atoms/retention-policy.md` (legado) | — | qué se conserva/poda/pinnea | — | reachable/pinned/orphaned | — | — | — | — | — |
| `20260829-160607/…/triage.md` (runs) | — | CAS+log+heads; 8-field revision; tree objects | SHA-256; class/kind al hash; ULID tree ids | CAS per head; timestamp fuera del edge | — | semantic no sigue sucesión | — | 5 decisiones usuario | resolvió los "undefined" del doc v2 |
| `20260830-012800/…/triage.md` (runs) | queries derivadas de revision+pool | nada persistido para anchors | `anchor/placements` | replay no depende de reachability de add-edge | — | re-anchoring §6.1 | — | errores `ex-info` tipados | — |
| `20260830-235820/…/round-1/triage.md` (runs) | node id == content hash | capas UAX#29 cacheadas, no nodos | leaf-id = leaf-hash | — | — | — | caché sin eviction (deuda GC) | — | — |

---

# Contradicciones (cita de ambos lados)

1. **¿El store es el documento o apunta al archivo?**
   - Pool v2: "A document is a tree-index" / "the pool of content-addressed nodes is [authority]" (`docs/archive/architecture/target-system-overview.md:7`).
   - Legado aún en desk: "A store is SLDB's metadata workspace ... it does not replace Markdown files or become the text source of truth"; "The store holds metadata about documents rather than the documents themselves" (`desk/atoms/what-a-store-is.md:16-20`).
   - Legado: "The graph store is the persistent kernel repository for canonical documents, nodes, edges, revisions" (`desk/atoms/graph-store.md:16`).

2. **Fuente de verdad: base de datos canónica vs ficheros.**
   - "canonical state ... persisted in the canonical database" (`docs/archive/architecture/contracts/canonical-state-derivation-glossary.md:69`); "Clojure-owned database-backed state" (`docs/archive/desk/drawer/tasks/task-freeze-canonical-state-derivation-glossary.md:63`).
   - v2: "persistence to files only (objects/<hash> CAS, log.edn append-only one transaction per line, heads.edn written atomically by rename)" (`desk/atoms/atom-first-slice-runtime-choices.md:16`); "no backend dependency (02 §8.1)" (`docs/archive/architecture/contracts/graph-store-contract.md:4`).

3. **Hash: Blake3 vs SHA-256.**
   - "Nodes are indexed by their Blake3 hashes" (`docs/archive/architecture/contracts/graph-store-contract.md:45`); "`blake3` is used for cryptographic hashing of nodes and documents (like `SourceHash`)" (`docs/archive/raw/source/atoms-retired/decision-blake3-hashing.md:14`).
   - "SHA-256 through a host Hasher protocol ... BLAKE3 later, algorithm recorded in the store descriptor" (`desk/atoms/atom-first-slice-runtime-choices.md:16`); banner: "SHA-256 via the host Hasher in the first slice (02 §8.1)" (`docs/archive/architecture/contracts/python-clojure-ownership-and-ffi-contract.md:5`).

4. **Backend: redb/rusqlite/Cozo vs solo ficheros.**
   - "`redb` is the default embedded backend and `CozoDB` a valid alternative" (`docs/archive/architecture/contracts/graph-store-contract.md:41`); `decision-rusqlite-store.md:21-23`.
   - "first slice persists to files (CAS + append-only log + heads); no backend dependency (02 §8.1)" (`docs/archive/architecture/contracts/graph-store-contract.md:4`); "files-only backend" (`desk/atoms/atom-decision-first-slice-kernel-semantics-confirmed.md:16`).

5. **Identidad de nodo: document-scoped vs content hash.**
   - "document-scoped node identity" queda superseded: "identity is the content hash; succession is a supersedes edge (02 §6)" (`docs/archive/architecture/contracts/graph-store-contract.md:5`).
   - "A node is the primary canonical entity stored inside a document revision" (`desk/atoms/node.md:16`); "A revision is an immutable document state ... identified by a deterministic root hash" (`desk/atoms/revision.md:16`) frente a "id(node) = H(canonical-bytes…)" y revision de 8 campos sin "document" (`desk/atoms/atom-canonical-content-and-node-hashing.md:16`, `atom-revision-id-edge-set-and-diff.md:16`).

6. **Anchor roto como error de validación vs estado derivado.**
   - "Broken Anchors: Detection of stale or missing targets must be reported as a validation error" (`docs/archive/architecture/contracts/anchoring-contract.md:42`); anchors "detected during document import/indexing" (`:40`).
   - "the anchoring-contract is Superseded ... anchor states are derived per endpoint (intact/superseded/orphan)" (`docs/archive/architecture/contracts/anchoring-contract.md:3`); "Every anchor query is derived — recomputed from revision plus pool, never persisted" (`desk/atoms/atom-anchor-state-is-derived-and-computed-per-endpoint.md:16`).

7. **Revista: root por documento vs 8 campos sin documento / heads por árbol.**
   - "Revision {id, parents, transaction, root_hash, created_at}" (`docs/archive/architecture/vistas/d04-dominio.mmd:8-14`); "RevisionStore: immutable revisions with parents, producing transaction, and root hash" (`docs/archive/architecture/contracts/graph-store-contract.md:38`).
   - "Revision = {:roots {tree-id merkle-root} ..., :trees ..., :edges ..., :parents, :tx, :actor, :engines, :timestamp}; revision-id = H(...) of exactly those eight fields" (`desk/atoms/atom-revision-id-edge-set-and-diff.md:16`).

8. **AST persistido vs recomputado.**
   - "persist canonical structural AST data for authored document families" (`docs/archive/architecture/target-system-overview.md:41`); "AST persistence is the durable storage of canonical AST nodes" (`desk/atoms/ast-persistence.md:16`).
   - v2 no tiene un objeto "AST": el "AST" es un árbol de posiciones sobre el pool de nodos (`desk/atoms/atom-tree-as-index.md:16`) y las capas deterministas "se derivan bajo demanda y se cachean por hash de hoja; no son nodos" (`docs/archive/runs/subagents/20260830-235820-…/round-1/lane-C.md:105-108`).

9. **Ciclo de vida de anchors: `anchoring-contract` ("Fallback addressability based on line/character offsets", "Selector: `L{line}C{col}`") vs v2 stand-off en offsets de grafema** ("[leaf-id start end] in UAX #29 grapheme offsets over the leaf's NFC text" — `desk/atoms/atom-stand-off-addresses-are-virtual-until-referenced.md:16`).

10. **Historia: "git-only" vs log propio, y el stack de linaje del desk.**
    - "We want more than git-only history" (`docs/archive/raw/source/drawer-features/feature-sldb-explicit-target-architecture.md:227`).
    - "Which history atom is foundational: `Provenance Record`, `Append-only event log`, `Temporal layer`, or `Snapshots`? ... That lineage stack currently reads as cyclic rather than clearly layered" (`docs/archive/raw/source/subagent/questions-atom-ontology-ambiguities.md:11`).

11. **"is_solved_by…"/modelos como nodos: el store v1 guarda `models`/`predicates`/`hash_a` como YAML** (`docs/archive/architecture/spec2viz/.sldb/core/store_index.yaml:1-97`) **vs la dirección v2** "graph-store-first ... they do not scale cleanly to graph-native canonical state" (`desk/atoms/decision-graph-store-over-yaml-indexes.md:16-20`).

12. **Escala: métricas objetivo contradictorias o sin base.**
    - "Query: Indexed retrieval of any node by Hash or Path in < 50ms for projects < 10k nodes" (`docs/archive/architecture/contracts/graph-store-contract.md:58`).
    - "Point Queries: < 10ms. Complex Joins: < 100ms for projects < 50k edges" (`docs/archive/architecture/contracts/query-and-index-parity-contract.md:42-43`).
    - Y ninguna spec v2 fija cifras; el único límite declarado es "unbounded/no eviction" en la caché de capas (`docs/archive/runs/subagents/20260831-000438-…/executor-report.md:74-76`).

13. **"packfiles" aparece en el roadmap v2 pero no en ninguna spec de store.**
    - "packfiles and gc (content-addressed store and retention)" (`desk/atoms/atom-git-object-model-as-the-v2-roadmap.md:16`); la tarea activa solo pide "a report of unreachable objects in objects/ (garbage candidates) as groundwork for milestone 8 retention and GC" (`desk/tasks/task-unreachable-objects-report-and-gc-groundwork.md:45`).

---

# Revisados y descartados (no dicen nada del store, o solo lo mencionan)

- `docs/archive/architecture/contracts/specyaml-schema-contract.md` — solo forma de los YAML; `diagram-traceability-contract.md` — vistas/puml/HTML.
- `docs/archive/architecture/contracts/cli-parity-contract.md`, `core-source-downstream-coverage-contract.md` — incluidos arriba por sus listas de grupos/átomos store, pero sin diseño de persistencia.
- `docs/archive/architecture/vistas/{d00,d01,d02,d03,d05,d06,d08,d12,d15}`, `core-diagrams/{01,02,03,06,10,12,13}`, `vistas/template.html`, `sldb_Kernel_Vistas_UML.html` — proyecciones generadas o vistas sin eje de store.
- `docs/archive/architecture/spec2viz/desk/{config.json,manifest.yml,rituals,primitives/**,routines/**,drawer/README.md,deskops.log}`, `desk/contexts/pill-traceability-workflow.md`, `desk/atoms/{atom-coverage-warning,atom-traceability-symmetry,tag-namespaces.yaml}` — trazabilidad de documentación, no store.
- `docs/archive/desk/{config.json,atoms/tag-namespaces.yaml,contexts/vistas-uml.annotations.json}` (y `roles/`, `primitives/hooks/` vacíos) — andamiaje deskops; el README del archivo ya lo explica.
- `docs/archive/desk/drawer/tasks/{task-downstream-core-source-coverage…,task-map-documentation-contradictions…}` — contratos de documentación; solo referencian atoms de store.
- `docs/archive/raw/source/atoms-retired/{lisp-metalanguage,lisp-macro-language,lisp-schema-language}.md` — Lisp; no store.
- `docs/archive/raw/source/architecture/` — fuera de carril (L1).
- `docs/archive/raw/source/core/`, `sldb-v1/`, `ecosystem/` — fuera de carril (L1).
- `docs/archive/raw/source/scripts/{pivot_to_clojure.py,update_atoms.py}` — utilidades de renombrado/clasificación; solo nombran "store" como categoría de capa (`update_atoms.py:8-14`).
- `docs/archive/runs/subagents/index.jsonl`, `git-status.txt`, `graph.txt`, `board.txt`, `next.txt`, `task.txt`, `validation.log`, `lint.log`, `oracle.log`, `bundle.md`, `run.yaml` — metadatos/evidencia de ejecución sin diseño de store.
- `docs/archive/runs/subagents/20260829-160607-…/round-{1..5}/lane-{B,B0,B1-3,C1,C2,C3}.md` — ejecutabilidad de tareas y barrido de KB legacy; los hallazgos de store están en sus triages (citados arriba).
- `docs/archive/architecture/spec2viz/.sldb/core/{documents,models}/*.yaml` — descriptores de modelos StructuredNLDoc; sin contenido de store.
- `desk/atoms/*` no listados arriba (ast-command-group, cli-workflow-surface, direct-mode, etc.) — superficies CLI/workflow, no diseño de store.
- `desk/primitives/{checklists,conditions,edges,operators}/**` y `desk/routines/*` (salvo el de GC) — andamiaje de ciclo de vida deskops, sin diseño de store.
- `desk/registry/repo-sldb-refactor-worktree.md` — registro de repo, no store.
- `docs/archive/architecture/contracts/atom-ontology-map.md` / `atom-provenance-map.md` — incluidos como inventario (secciones 1).
- `docs/archive/raw/source/subagent/{atom-draft-set.md,behavior-atom-map.md}` — incluidos arriba; `drafts/contracts-batch-{a,b}.md` — planes, no contratos finales.
