# Dossier del store SLDB — Carril L3

Investigación de solo lectura. Cito textual y no propongo. Cobertura: corpus SLDB v1, `docs/concepts/**`, `wittgenstein`, y el store real `.sldb/` (evidencia viva). El carril L3 añade la sección final **Cómo persiste v1 hoy** con estructura real del `.sldb/`.

Convención de estado: `vigente` / `histórico` / `superado` según el propio documento.

---

## /home/jp/proyectos/hum-ecosystem/tools/sldb/docs/target_architecture.md
Estado: **histórico** ("Historical design proposal, predating the 2026-09-07 freeze note", `target_architecture.md:3`).

- **1 Fuente de verdad:** propone bounded contexts; `Store & Hashing (SLDB-Store)` mantiene "the integrity, indexing, and on-disk representation of the document store" y es "a prime candidate for a completely independent library" (`:19`).
- **3/5 Identidad y AST:** Store provee "base indexing maps (Document Index, Model Index)"; Query construye grafos "without interacting directly with file I/O operations whenever possible" (`:40`).
- **8 Decisiones y porqué:** criterios de extracción: "No Circular Dependencies", interfaz abstracta, suite aislada (`:42-45`).

## /home/jp/proyectos/hum-ecosystem/tools/sldb/docs/addressability_model.md
Estado: **vigente** (ejercitado por `tests/test_address_surface.py`).

- **1 Fuente de verdad:** "The store is a database of addressable fields over Markdown… The Markdown stays canonical; the address is how you reach into it" (`:3`). Regla: "**Read and write by address. SLDB owns the file.**" (`:9`).
- **2 Qué se persiste / 3 Punteros:** un read resuelve "store → model → document → field"; "The store index gives the document's path and model, the model's template gives the field recipe" (`:11`). Escribe: re-render, roundtrip, escribe el `.md`, "updates `hash_c`/`hash_d`, rebuilds the semantic index, and cascades `hash_a`" (`:13`).
- **3 Direccionamiento:** espacios `st.` (estructural), `se.` (semántico/DAG de tags), `<store>:st.` (store enlazado), `gse.` (global). Ejemplos `st.{Book}.my-book.title`, `se.<tag>`, `gse.<tag>.se.{store}` (`:17-52`).
- **4 Historia/estabilidad:** "Document and field addresses are the stable ones"; "Section paths break when a heading changes"; "List positions shift" (`:106-110`).
- **6 Capa G:** provenance = identidad para export/KGDB: `sldb://model/<Model>`, `sldb://document/<Model>:<doc>`, `sldb://section/<Model>:<doc>#<path>` (`:115`).
- **7 Escala:** "Every address read resolves through `load_runtime_documents`, which extracts every tracked document of the store before selecting. Addresses narrow the *answer*, not the *work*" (`:114`).
- **9 Contradicción interna:** el doc dice que `se.` sale de `__semantics__` + `tags`/`semantic_tags` (`:34`), coherente con shards reales.

## /home/jp/proyectos/hum-ecosystem/tools/sldb/docs/versionado-y-colaboracion.md
Estado: **vigente** (investigación fechada 2026-09-21, rama main).

- **4 Historia/versionado:** "No existe ningún contador ni versión entera por documento… El token que cumple el rol sin inventar nada es `hash_d`" (`:60-62`).
- **2 Qué se persiste:** tabla de hashes: `store_hash`/`hash_a` (root), `model_hash`/`hash_b` (por modelo), `source_hash`/`hash_c` (texto markdown), `content_hash`/`hash_d` (payload extraído) (`:22-25`).
- **4 Journal:** "Append-only, un YAML por write en `.sldb/runtime/journal/` (`append_entry`, bajo `journal_lock`)" (`:38`); entrada con `operation, address, field, previous_value, new_value, hash_*_before/after, actor, timestamp` y cadena `previous_hash -> entry_hash` (`:39-41`).
- **7 Historia/undo:** "el journal es **log de auditoría**… Hoy solo audita"; no hay replay, checkpoints, ni inversión (`:73-79`).
- **8 Decisión + porqué:** `/save` con control optimista por `expected` que "Detecta el conflicto pero no lo resuelve" (`:16`); propuesta con `base_version` = `hash_d` (`:96-118`).
- **2 Función pura:** "`hash_d` es **función pura del payload**" (`:30`).

## /home/jp/proyectos/hum-ecosystem/tools/sldb/docs/workspaces.md
Estado: **vigente**.

- **8 Decisiones:** tres capas: `sldb` (infra de documentos, store, AST), `specyaml` (contratos semánticos, source-of-truth compartido), `opsys`/`deskops` (workflow). "Changes to opsys-owned surfaces should not leak into sldb's generic infrastructure" (`:20`).
- **4 Historia vs git:** "Git remains the durable historical record. The store is the current queryable routing layer" (`:46`).
- **3/6 Store discipline:** `docs track`/`create`, `stores update`, `docs untrack` (`:40-45`).

## /home/jp/proyectos/hum-ecosystem/tools/sldb/docs/migration_strategy.md
Estado: **histórico** ("Historical proposal, predating the 2026-09-07 freeze note", `:3`).

- **8 Decisión:** extraer `sldb-store` (hashing, layout, IO) como librería independiente; separar query/semantic; CLI como capa de presentación (`:9-33`). "Rely on `git` history to preserve legacy implementations" (`:37`).

## /home/jp/proyectos/hum-ecosystem/tools/sldb/docs/export_provenance_contract.md
Estado: **vigente** (contrato de export semántico).

- **1/3 Identidad y provenance:** "Every node in the exported graph must track its root document source": `doc_address`, `doc_version` ("current hash or version identifier") (`:7-9`); `local_address` (`field.tasks[2]`, `sec.roadmap`), `model_identity` (`:12-14`).
- **2 Evidencia:** "Export packages should contain the global store hash to correlate cross-document state at a single point in time" (`:19`).
- **6 Capa G:** "When a user clicks to edit, the IDE uses SLDB primitives to find and open the exact physical file and line number" (`:25`).

## /home/jp/proyectos/hum-ecosystem/tools/sldb/docs/self-documentation.md
Estado: **vigente**.

- **2 Qué se persiste:** `python-sync` materializa el inventario bajo `knowledge/symbols/` y "persists the regenerable source facts through KGDB" (`:5`); records con identidad `python:<module>:<qualname>`, span, firma, docstring, imports y file hash (`:5`).
- **6 Capa G:** `python-sync` proyecta "module/symbol containment, import bindings, and resolvable annotation references, with source span and hash evidence. These are store facts for any consumer" (`:7`).
- **2 Tags:** records se renderizan como Markdown; el índice semántico distingue formato: `PythonSymbolDoc` tiene `representation.markdown` + `source.code.python` (`:9`).
- **5/7 Persistencia:** "`python-check` reports removed records without deleting them"; "SLDB never rewrites a symbol ID or authored context automatically" (`:37-39`).

## /home/jp/proyectos/hum-ecosystem/tools/sldb/docs/user-manual.md
Estado: **vigente** (ejecutado contra un store real, §4 "El store").

- **2 Qué se persiste:** anatomía `core/` (store_index, models, documents) versionado; `runtime/` (sections, edges, semantic, journal, locks, edges.yaml, semantic_dag.yaml) derivado. "**`core` es estado, `runtime` es caché.** Si `runtime` se corrompe, `stores update` lo rehace" (`:206-229`).
- **2/3 Shards:** "Un archivo por documento ("shard"): dos ramas de git que tocan documentos distintos no chocan" (`:231`). "**No guarda el contenido.** Guarda dónde está, cómo verificarlo y cómo encontrarlo" (`:244`).
- **4 Hashes:** cuatro hashes + `model_hash = hash(contract_hash + roster_hash)` (`:249-260`); `contract_hash` = campos + template, `roster_hash` = lista `(nombre, source_hash, content_hash)` (`:265-267`).
- **2 field_hashes:** cada doc guarda hash por campo; "Un mapa **vacío** significa "no se pudo calcular"… **nunca** "ningún campo cambió"" (`:274-295`).
- **9 Contradicción con lo real:** "Las claves persistidas en disco siguen siendo `hash_c`/`hash_d`: renombrarlas invalidaría todos los stores existentes" (`:301`), pero el store real persiste `source_hash`/`content_hash` (ver sección final).

## /home/jp/proyectos/hum-ecosystem/tools/sldb/docs/architecture/current-system-overview.md
Estado: **vigente** ("Store layout checked on 2026-09-13", `:5`).

- **2 Store shape:** reproduce `.sldb/core/{store_index.yaml, models/<Model>.yaml, documents/<Model>.yaml}` y `runtime/{semantic_dag.yaml, semantic_index.yaml, sections/<Model>.yaml, cache/, locks/}` (`:25-36`).
- **4 Hash chain:** `hash_c` texto, `hash_d` payload, `hash_b` índice por modelo, `hash_a` top-level (`:43-46`).
- **6 Semántica:** `semantic_dag.yaml` = grafo de nodos + equivalencias; `semantic_index.yaml` = materialización doc→tag (`:50-51`).
- **8 Constraints:** "Diagnostics treat missing documents and changed extracted payloads as failures"; "Semantic tags are explicit model/document metadata, not inferred from prose" (`:57-63`).

## /home/jp/proyectos/hum-ecosystem/tools/sldb/docs/architecture/smg-ir.md
Estado: **vigente**.

- **1/2 Fuente de verdad:** "`S`, `M` and `G` are all derived. They are not stored… Only three floors are material" (`:21-22`). Floor físico: STORE → `core/store_index.yaml`, `core/models/<Model>.yaml` (contract), `core/documents/<Model>/<doc>.yaml` "DOCUMENT -> payload + content_hash + field_hashes" (`:25-31`).
- **5 AST:** "The AST is never persisted; it is reconstructed on demand (only an in-memory runtime cache, no on-disk projection)" (`:57`).
- **2/7 Seam:** `_map_fields_to_sections` es heurística; "That is what makes `nodes -> section` wrong when rendering changes line counts" (`:60-66`).
- **6 Capa G:** `DocumentIR` con `context/structure/nodes/surface/graph/context_index`; "Section context indexes are now persisted in store artifacts during `stores update`" (`:76-99`).

## /home/jp/proyectos/hum-ecosystem/tools/sldb/docs/architecture/database_component_diagram.md
Estado: **vigente** (diagrama descriptivo).

- **2/3 Persistencia:** `.sldb/` como "Hidden Metadata" con `store_index.yaml (Hash A)`, `models/*.yaml (Hash B)`, `documents/*.yaml (Hashes C & D)`, `semantic_dag.yaml (Graph Edges)` (`:1-40`).
- **1 Separación:** "Metadata… is strictly isolated in the `.sldb/` folder, allowing the Markdown to remain fully independent of the database tooling if needed" (`:52`).
- **4 Integridad:** "4-layer Merkle-style hash cascade (Hash A, B, C, D) to detect tampering, content drift, or model schema changes" (`:49`).

## /home/jp/proyectos/hum-ecosystem/tools/sldb/docs/architecture/sldb-absorbs-kgdb.md
Estado: **vigente / Accepted** (ADR 2026-09-20; supersede al ADR de dos capas).

- **8 Decisión:** "**SLDB is one product with two internal layers: a text layer and a graph layer.** KGDB stops being a separate product" (`:33`); tabla text/graph con `store/`, `store/edge_index/`, `store/graph/`, `api/graph/` (`:38-41`).
- **1 Fuente de verdad:** "The typed graph was never independent of the store. `kgdb ingest` read SLDB through its library… every node and edge it produced was derived from tracked documents. The graph was a projection with its own process, not a second source of truth" (`:16-19`).
- **4/6 Fuente de aristas:** "On 2026-09-17 the assembly half was absorbed: the edge index became a store index, kept per-document in shards with the same hash chain as `sections` and `semantic`, current after every write" (`:26-28`).
- **4 Journal:** "The write journal belongs to the substrate… a per-store journal chained by hash" (`:64`); journal entry vs `MoveDoc` (`:70-76`).
- **8 Amendment (networkx):** analytics se habilitan; "Derived relations are excluded by default"; "`node_types` means two different things, on purpose" (`:130-160`).

## /home/jp/proyectos/hum-ecosystem/tools/sldb/docs/architecture/sldb-text-layer-vs-kgdb-graph-layer.md
Estado: **superado** ("Superseded by SLDB Absorbs KGDB", `:3`).

- **1 Fuente de verdad:** "Human-readable authorship must remain canonical"; "Graph export should project… It should not erase the distinction between authored text and graph-native interpretation" (`:60`, `:88`).
- **8 Decisiones (revertidas):** "SLDB should not own graph-native reasoning… full multi-hop graph traversal as a first-class truth model" (Consequence D, `:150-156`); el ADR sucesor revierte esto.

## /home/jp/proyectos/hum-ecosystem/tools/sldb/docs/architecture/semantic-export-boundary.md
Estado: **vigente**.

- **1 Fuente de verdad:** "SLDB owns the semantic truth for tracked Markdown documents. KGDB should ingest that truth through the versioned semantic export contract instead of scraping `.sldb/runtime` files directly" (`:3`).
- **2 Qué se persiste (payload):** contrato `sldb_kgdb_semantic_export` v1; incluye model entries, "stable export id", document content hash, sections, DAG y equivalencias (`:7-29`).
- **2 Excluye:** "Raw Markdown document bodies and rendered document text"; "Ad hoc access to `.sldb/runtime` as an ingestion mechanism" (`:31-39`).

## /home/jp/proyectos/hum-ecosystem/tools/sldb/docs/architecture/edges-retirement-plan.md
Estado: **plan vigente** (decisión del mantenedor, 2026-09-24; retiro aún no aplicado al store real, ver final).

- **8 Decisión:** "la capa derivada `runtime/edges/` (un YAML por documento) se retira; se queda la capa de grafo `src/sldb/store/graph/` (networkx + node-link JSON portable)" (`:4`).
- **7 Escala (el porqué):** KB de 1027 docs markdown (~1MB texto) → "`runtime/edges/` pesa 16MB; `runtime/graphs/*.nx.json` + `*.kg.json` pesan 350KB" (`:11`); "Un documento de 1.3KB genera un edges YAML de 405 líneas" (`:13`).
- **3 Source-of-truth actual:** "**La "capa nueva" es hoy lectora de la capa vieja.**" Todo `store/graph/` entra por `load_edge_index` → shards de `runtime/edges/` (`:19-26`).
- **6 Capa G / GAPS:** falta productor de nodos `sldb://field/` + `has_field`/`extends` (G1) y relation_type/anchor (G2); sin resolución de reglas cross-document (G3) ni validación de edges (G4) ni federación de lectura (G5) (`:293-301`).
- **4 Rebuild:** "el "rebuild" nuevo = regenerar el snapshot completo (barato)"; se retira la staleness por-shard (`:109-110`).

## /home/jp/proyectos/hum-ecosystem/tools/sldb/docs/architecture/deskops-boundary-proposal.md
Estado: **vigente** (propuesta de frontera).

- **8 Decisión:** SLDB conserva "stores and integrity hashing", "semantic and physical query primitives", `StructuredNLDoc`, extract/render/validate; deskops asume "desk workspaces and inbox semantics", tasks/pills/rituals (`:20-35`).

## /home/jp/proyectos/hum-ecosystem/tools/sldb/docs/architecture/what-the-graph-is-not-being-asked.md
Estado: **vigente** (review 2026-09-20).

- **6/7 Grafo real:** store de pron: "nodes 1812, edges 16013"; relations `tagged_as` 13873, `has_section` 1361, `implements` 349, `has_document` 316, `has_field` 70, `semantic_parent` 26 (`:8-14`).
- **8 Decisión:** "A tag's dotted name **is** a path in a graph"; "sldb stores one graph as strings and then navigates the strings, next to a graph engine" (`:38-40`).
- **6 Identidad semántica:** las dos familias de tags `type.knowledge.anchor` vs `workspace.knowledge.anchors` son "two axes of a faceted classification", no duplicados a mergear (`:44-63`).

## /home/jp/proyectos/hum-ecosystem/tools/sldb/docs/architecture/code-docstring-indexing.spec.md (+ handoff)
Estado: **vigente** (spec de próxima slice).

- **2 Tags:** base `representation.markdown`; `source.document.markdown` vs `source.code.python`; "Consumers filter these semantic tags instead of inferring format from a folder, filename, or model name" (`:21`).
- **6 Capa G:** "KGDB-backed projections" con source span y hash; el adapter "does not read the artifact back through another runtime" (self-documentation.md:7, referenciado).

## /home/jp/proyectos/hum-ecosystem/tools/sldb/docs/atoms/store.atom.md
Estado: **vigente** (SSOT conceptual).

- **1 Fuente de verdad:** "An SLDB store is the metadata workspace that registers models, tracks documents, keeps integrity hashes, and materializes semantic or section indexes without becoming the source-of-truth for the Markdown content itself" (`:11`).
- **2 Persistencia:** "The store keeps YAML indexes for registered models and tracked docs plus hash layers that detect content drift, payload drift, missing files, and contract changes. The Markdown files stay where they already live in the repo" (`:19`).
- **3 Ubicación:** "The normal project-local location is `.sldb/`. A global `~/.sldb/` can also exist, and the local store takes precedence" (`:30`).

## /home/jp/proyectos/hum-ecosystem/tools/sldb/docs/atoms/address-space.atom.md
Estado: **vigente**.

- **3 Direccionamiento:** `st.{Model}.<doc>.<field>`, dicts por key y listas por posición (`tasks.0.title`); `se.<tag>`, `gse.<tag>`; spelling `docs/<doc>/<field>/<sub>` (`:7`).
- **2 Escritura:** re-render, "verifies the render extracts back to the same payload, writes the file, updates `hash_c` and `hash_d`, rebuilds the semantic index and cascades `hash_a`" (`:13`).
- **4/6 Motores:** `sldb.store.query` + `query_engine.filter`; writes por `sldb.cli.commands.fields_save.save_payload` (`:21`).

## /home/jp/proyectos/hum-ecosystem/tools/sldb/docs/atoms/tracked-document.atom.md
Estado: **vigente**.

- **2 Qué se persiste:** "the store records its logical name, physical path, payload hash, content hash, and semantic tags" (`:13`).
- **1/3 Ubicación:** "The content lives in the Markdown file… The tracked metadata lives inside the store indexes under `.sldb/`" (`:19`).

## /home/jp/proyectos/hum-ecosystem/tools/sldb/docs/atoms/runtime-cache.atom.md
Estado: **vigente** (crítico para escala).

- **2/7 Cache:** "The runtime cache is how sldb reads a store once per change instead of once per query, using the hash chain as the Merkle tree it is… a read descends only where a hash moved" (`:9`).
- **4 Contrato de cache:** payload cacheado en disco en `$XDG_CACHE_HOME/sldb` fallback `~/.cache/sldb`, `runtime-cache-extracted/<hash of store path>.json`; built cache por `hash_b` (`:9`).
- **8 Decisión:** "Both cache files are derived: delete them freely and keep them out of git" (`:9`). `SLDB_TRUST_CHAIN=1` salta el sweep de stat.
- **2 AST:** "the extracted documents are kept whole-store… in memory" (no AST persistido).

## /home/jp/proyectos/hum-ecosystem/tools/sldb/docs/atoms/ast-view.atom.md
Estado: **vigente**.

- **5 AST:** "The AST view is SLDB's normalized graph representation of stores, models, documents, sections, fields, and their semantic or physical relationships" (`:9`); materializado por CLI graph helpers, no persistido.

## /home/jp/proyectos/hum-ecosystem/tools/sldb/docs/atoms/semantic-vs-physical.atom.md
Estado: **vigente**.

- **6 Capas:** "Physical information comes from file locations and document structure. Semantic information lives in model semantics, tracked doc tags, and section-context artifacts in the store runtime indexes" (`:19`).

## /home/jp/proyectos/hum-ecosystem/tools/sldb/docs/atoms/text-layer-vs-graph-layer.atom.md
Estado: **vigente** (contenido pre-fusión KGDB; ver contradicción).

- **1/8 Frontera:** "SLDB owns the text layer and KGDB owns the graph layer"; "KGDB consumes exported knowledge structures and owns graph persistence, traversal, equivalence, inference" (`:11-15`).
- **9 Contradicción:** contradice el ADR `sldb-absorbs-kgdb.md` (que declara ambos como capas internas de un solo producto).

## /home/jp/proyectos/hum-ecosystem/tools/sldb/docs/atoms/predicate-links.atom.md
Estado: **vigente**.

- **6 Capa G / registrar predicates:** "Las definiciones viven en `.sldb/core/store_index.yaml`"; `predicates: - name: implements axis: HOW` (`:15-22`).
- **4 Vocabulario por store:** "Cada store mantiene su propio vocabulario de predicates" (`:12`).

## /home/jp/proyectos/hum-ecosystem/tools/sldb/docs/atoms/compose-vs-recover.atom.md
Estado: **vigente**.

- **6 Transclusiones:** `recover` resuelve `[[links]]` y `compose` expande `![[transclusions]]`; "The links live in Markdown content. The resolution logic lives in `src/sldb/links.py`" (`:13-19`).

## /home/jp/proyectos/hum-ecosystem/tools/sldb/docs/atoms/structurednldoc-model.atom.md · model-reference.atom.md · payload-inputs.atom.md
Estado: **vigente**.

- **1/2 Contrato:** "A `StructuredNLDoc` model is the Pydantic-based contract that tells SLDB how to render Markdown, extract typed payload data… and validate roundtrip" (`structurednldoc:11`); "It lives in Python code" (`:19`).
- **3 Identidad de modelo:** "A model reference is the Python import string… required form is `module:ClassName`" (`model-reference:11`); vive en CLI input y store metadata (`:19`).
- **2 Payload:** inputs son YAML/JSON que llenan el modelo; "The data must match the target model schema" (`payload-inputs:13`).

## /home/jp/proyectos/hum-ecosystem/tools/sldb/docs/ast_query_primitives.md
Estado: **vigente** (verificado 2026-09-13).

- **5 AST:** operaciones reales: `fields show`, `ast show` (`document.ir.surface`, `document.ir.structure`, `document.ir.nodes`), `sections show/fields/find` (`:11-32`).
- **7 Límites:** "Field ownership is a heuristic with a reproduced defect when template and rendered line positions differ. Do not treat it as guaranteed source provenance" (`:44`).

## /home/jp/proyectos/hum-ecosystem/tools/sldb/docs/composition_modes.md
Estado: **vigente** (verificado 2026-10-01).

- **2 Composiciones:** `__compositions__` con `source_field` lista de paths, `model`, `template`, `separator`; el ref se resuelve como path del proyecto bajo `ref_root` (`:5-24`).
- **6 Aristas del grafo:** cada ref compone una arista `composes` con "the composed field, the field holding the refs, the ref as authored and the composed model" (`:38-42`).
- **8 Límite explícito:** "What the edge does *not* give you is edit-through… Render-only markers are not extracted back into the parent payload" (`:56`).

## /home/jp/proyectos/hum-ecosystem/tools/sldb/docs/faq.md
Estado: **vigente**.

- **1 Fuente de verdad:** "It does not replace your Markdown files and it does not become the canonical source-of-truth for the document text" (`:57`).
- **2 Contenido:** "In practical terms, the store holds metadata about documents, not the documents themselves" (`:76`).

## /home/jp/proyectos/hum-ecosystem/tools/sldb/docs/README.md
Estado: **vigente**.

- **8 Mapeo modelos↔archivos:** `ArchitectureNarrativeDoc → docs/architecture/*.md`, `AtomicDocumentDoc → docs/atoms/*.atom.md`, `FAQDoc → docs/faq.md`, `StoreReadmeDoc → .sldb/README.md` (`:22-28`).
- **2 Shallow models:** modelos intencionalmente superficiales; "rely on `sections` and `find` for deeper navigation" (`:44-50`).

## /home/jp/proyectos/hum-ecosystem/tools/sldb/docs/requests/model-path-not-portable.md
Estado: **abierto** (feature/bug p2, 2026-09-30).

- **3 Identidad/portabilidad:** `ModelEntry.path` escribe ruta absoluta cuando el modelo vive fuera del repo; "84 entradas con ruta absoluta" en AgentsKBs (`:11-20`).
- **1 No es fuente de verdad funcional:** "`ModelEntry.path` no se usa para importar el modelo. La resolución va por `model_ref` + `pythonpath`" (`:90`).
- **8 Recomendación:** opción A "`path: null` cuando el modelo es externo… Es la única que no miente" (`:105-120`).

## /home/jp/proyectos/hum-ecosystem/tools/sldb/docs/requests/anchoring-surface-proposal.md
Estado: **superado** ("Superseded por `docs/surface/PROPUESTA-SUPERFICIE.md`", `:9`).

- **8 Decisión/gaps:** anclaje sobre texto crudo; H1 identidad estable de sección, H2 composición por query, H3 `Corpus.rank()` sin CLI (`:180-210`).
- **3 Identidad:** `AnchorDoc` resuelve `ref` a node ids; "No existe id de sección independiente de posición y título" (`:150`).

## /home/jp/proyectos/hum-ecosystem/tools/sldb/docs/surface/PROPUESTA-SUPERFICIE.md
Estado: **vigente** (borrador para revisión, 2026-09-28).

- **8 Decisión central:** "**Separar los hechos de los documentos.**" Un documento es "una **lista de qué fichas mostrar y en qué orden**" (`:28-33`).
- **1 Fuente de verdad:** un `StructuredNLDoc` deriva "tres representaciones equivalentes: el documento Markdown, el índice del store y el grafo. Ninguna es sustrato de otra" (`:42-45`).
- **2/4 Servicios de store:** dirección `st.`/`se.`, grafo networkx, DAG de tags, `stores check`, journal verifiable, `WikiPageDoc.__compositions__` (`:47-59`).
- **8 Gaps:** H1 procedencia por fragmento en el IR, H2 composición por query, H3 identidad estable de sección (`:61-98`).
- **9 Reglas:** "**Nunca escribir markdown.** Se escribe payload; SLDB renderiza y verifica el round-trip"; "Solo `rev`/`optrev` son editables"; "El id no es un string plano: es **la hoja de una ruta semántica**" (`:372-380`, `:390`).

## /home/jp/proyectos/hum-ecosystem/tools/sldb/docs/surface/OPERACIONALIZACION-VISTAS.md
Estado: **vigente** (borrador 2026-09-30; verificado contra `sldb serve`).

- **2 Modelo mínimo:** átomos como documentos; anchors de átomo = campos `start/end/quote`; estado del ancla "se calcula, no se guarda" (`:100-135`).
- **6 Aristas:** `RelationDoc` (arista) + `RelationTypeDoc` (verbo con tipos permitidos y cardinalidad); validación `sldb edges check` (`:36-40`).
- **7 Composición:** `__compositions__` sobre lista de rutas; slot con consulta "no se puede expresar en `__compositions__`" (`:150-170`).

## /home/jp/proyectos/hum-ecosystem/tools/sldb/docs/requests/sldb-knowledge-graph-integration.md · sldb-ontology-feature-request.md
Estado: **abiertos** (feature requests 2026-04-22, previos a la fusión).

- **6 Capa G:** pide edges tipadas en `.sldb/documents/<Doc>.yaml` (`relationships: - target … relation`), facets (`:60-100`); OWL/energy/context/cleansing declarados explícitamente **fuera** de sldb (van a kg_db) (`:30-40`).
- **9 Contradicción parcial:** su división de trabajo (sldb=minimal graph, kg_db=full) fue superada por `sldb-absorbs-kgdb` (SLDB absorbe el grafo).
- **2 Ontología:** propone `Semantic` en `Field()` o `.sldb/ontology.yaml`; "When a model is registered, metadata should be … stored in `.sldb/models/<Model>.yaml`" e "Included in hash computation" (`sldb-ontology…:120-130`).

## /home/jp/proyectos/hum-ecosystem/tools/sldb/docs/opsys/primitives/README.md · bridge_primitives/README.md · cli_grammar/README.md
Estado: **vigente** (diseño de la capa opsys/deskops, no del store SLDB).

- **8 Decisión:** primitivas documentales (Artifact, Schema, Record, Relation, Evidence, Routine, Checklist, Condition, Hook, Ritual, View) + bridge (Reference, Constraint, Lint, Probe, Transform) (`primitives:1-40`).
- **3 Referencia a código:** `Reference` "links an operational record or documentary artifact to a code target… should be resolvable, not just textual" (`bridge_primitives:40-50`).
- **9 Nota:** `how_to_report` y `how_to_test_ux_cli` redirigen a deskops; "this local copy is now only a forwarding note" (`how_to_report:3`).

## /home/jp/proyectos/hum-ecosystem/tools/sldb/docs/sldb_store_cascade.spec.yaml
Estado: **vigente** (spec de forma).

- **4 Cadena:** `StoreIndex` contains `ModelsIndex` contains `DocumentsIndex` contains `DocumentEntry`; aristas "hash_a depends on hash_b", "hash_b depends on hash_c and hash_d" (`:1-36`).

## /home/jp/proyectos/hum-ecosystem/tools/sldb/docs/sldb_federated_topology.spec.yaml · sldb_model_lifecycle.spec.yaml · sldb_query_engine.spec.yaml
Estado: **vigente** (specs de forma).

- **3 Federación:** `LocalProjectStore` "federates" `SharedOntologyStore` vía `StoreResolver` (`federated_topology:1-27`).
- **4 Lifecycle:** `ModelContract` Active→Drafting→ValidationFailed/ReadyForPromotion→Active; "models validate --promote (updates hash_a)" (`model_lifecycle:1-28`).
- **3 Query:** namespaces `st.*`/`se.*`/`gse.*` → `Load Runtime Documents` → filters → "Resolve Semantic DAG equivalences" (`query_engine:1-30`).

## /home/jp/proyectos/hum-ecosystem/tools/sldb/sldb.spec.yml · docs/sldb_ast_architecture.spec.yaml · sldb.spec.yml (root) · sldb_architecture.spec.yaml · sldb_extraction_flow.spec.yaml
Estado: **vigente** (specs generadas por spec2viz; son inventarios de nodos/código, no prosa de diseño de store).

- **5 Sin diseño de store:** `sldb.spec.yml` (3083 líneas) y `sldb_ast_architecture.spec.yaml` (4354) listan módulos/clases (`module.sldb.store.diagnostics`, `class.sldb.cli.commands.store.StoreCLI`, `sldb.store.edge_index`, etc.) sin describir layout ni persistencia.
- **7 `sldb_architecture.spec.yaml`:** `StoreManager` con auto-arista "handles persistence" (`:12-45`).
- **3 `sldb_extraction_flow.spec.yaml`:** flujo parse template → recipes → parse doc → data → `StructuredNLDoc` (`:1-40`).

## /home/jp/proyectos/hum-ecosystem/tools/sldb/.sldb/README.md
Estado: **vigente** (contrato del workspace, es el `StoreReadmeDoc`).

- **1/2 Capas:** "`.sldb/` is the project-local SLDB workspace. It keeps the repo's queryable store state close to the codebase while separating durable shared knowledge from ephemeral runtime state" (`:5`).
- **2 Durable vs runtime:** "Durable knowledge should become versioned models, tracked documents, and explicit policy. Runtime noise should stay rebuildable"; "Anything under `runtime/` must be safe to recreate from the durable repo state plus local command execution" (`:9`, `:57`).
- **8 Promotion Rules:** "if another contributor should pull it and rely on it, it belongs in `core/`; if it only helps the current session run, it belongs in `runtime/`; if it only affects one machine, it belongs in `.config/`" (`:72-80`).
- **4 Layout target:** `core/{models,documents,semantics,routines}`, `runtime/{indexes,locks,temp,drafts}`, `.config/{local.yaml,pythonpath}` (`:20-31`).

## /home/jp/proyectos/hum-ecosystem/tools/sldb/desk/inbox/20261007-133032-…md (fuente L3)
Estado: **nota abierta** (decisión del dueño 2026-10-07).

- **8 Decisión:** "en sldb, los shards de documentos (`.sldb/core/documents/`, 1551 archivos) y el indice (`.sldb/core/store_index.yaml`) NO deben ir en git; hoy estan versionados" (`:20`). Pendiente decidir `core/models/` (35 índices).
- **4 Reconstrucción:** receta requerida: 1368 documentos de `knowledge/` se regeneran con `sldb selfdoc sync` + `python-sync`; "los otros 183 (docs del repo, desk, inbox) solo los conoce hoy el indice, asi que hace falta declarar modelo y patron de rutas (como `kimun.include.edn`) y un script que corra `models add` + `docs track` + `selfdoc sync`" (`:20`).
- **7 Escala/validación:** "reconstruir en un directorio aparte y comparar los 1551 payloads contra el store actual antes de borrar nada" (`:20`).
- **8 Cierre propuesto:** "`git rm --cached` de lo que salga y `.sldb/` en `.gitignore` salvo `README.md`" (`:20`).
- **9 Nota presente:** el store real tiene 1552 shards, no 1551 (ver final); la nota dice "payloads" pero los shards no guardan payload.

## /home/jp/proyectos/hum-ecosystem/tools/sldb/desk/inbox/20260916-170500-…md (hash_b / contrato)
Estado: **cerrada** (ack 2026-09-23; "Reabierto y cerrado de verdad").

- **2/7 Bug de cache:** "el payload de un documento es funcion de (documento, contrato del modelo), pero el cache de nivel documento solo indexa por el documento" (`:20`).
- **2 Resolución:** "El nodo del modelo ahora es `hash_model_layer(roster_hash, contract_hash)`, con los dos hijos persistidos en `ModelsIndex`" (`:38`); `cached_document` firma por `hash_model_contract(model_type)` (`:39`).
- **4/7 Diagnóstico:** `stores update` re-extrae ante cambio de contrato; `stores check` reporta `contract_mutation`/`contract_ok` (`:40`).

## /home/jp/proyectos/hum-ecosystem/tools/sldb/desk/inbox/20260930-173600-…md
Estado: **abierta** (unclear, deskops→sldb).

- **4/7 Falla 1:** "stores update no recomputa content_hash en shards sin field_hashes… `stores check` reporta data_mutation permanente aunque el .md este intacto en git"; afectó "~236 documentos en 6 repos"; workaround: borrar `content_hash`/`source_hash` y correr `stores update` (`:20-24`).
- **4 Falla 2:** "`migrate_store_layout` nunca corre en stores con layout viejo… El migrador muere antes de migrar" (`:26-30`).

## /home/jp/proyectos/hum-ecosystem/tools/sldb/desk/inbox/20260924-010708-…md
Estado: **abierta** (deskops confirma el retiro).

- **8 Consumidores:** deskops "NO consumimos los simbolos que se retiran"; sus únicos contactos `sldb.api` son `init_store`, `track_document_file`, `untrack_document` (`:14-20`).
- **9 Hallazgo cruzado:** 34 documentos de deskops cuyo modelo "no podia leer su propio contenido" porque la extracción ancla en el texto fijo del template; "`stores check` daba PASS y los hashes cuadraban" (`:40-44`). Sugiere finding propio.

## /home/jp/proyectos/hum-ecosystem/docs/concepts/SMG_DEFINITIONS.md
Estado: **vigente** (v0.3).

- **1/4 Fuente de verdad:** S→M→G: S ancla a la realidad, M procesa la lógica, `G` es "el cementerio de las ideas que M ha demostrado que tienen sentido" (`:17-40`).
- **6 Capa G:** "**Hechos Proyectados:** Grafo de triples (Sujeto-Predicado-Objeto) resultante de la validación en M"; "**Persistencia Indexada:** Cada hecho en G mantiene un vínculo indestructible con su origen en S" (`:26-27`).
- **2 Retorno:** C. Perspectivismo y D. Autopoiesis: G se mira a través de lentes S y puede generar nuevos registros en S (`:36-40`).

## /home/jp/proyectos/hum-ecosystem/docs/concepts/KG_TREES.md
Estado: **vigente**.

- **2/5 Forma intermedia:** "KG-Trees are the AST-like intermediate structures used to move from structured surface knowledge toward validated graph persistence"; "between document structure and graph memory" (`:3-23`).
- **8 Decisión:** no define serialización ni esquema del grafo; "Those belong in implementation docs or RFCs" (`:55-62`).

## /home/jp/proyectos/hum-ecosystem/docs/concepts/KNOWLEDGE_STRUCTURE_CHALLENGE.md
Estado: **vigente**.

- **2/6 Ingestion:** "Knowledge ingestion is not just about extracting labels (atoms). It is about the reconstruction of a complex system where the **relationships**… generate emergent properties" (`:7`).
- **7/8 Implicación:** "Ingestion is Systems Engineering"; "Emergence cannot be hardcoded" (`:40-43`).

## /home/jp/proyectos/hum-ecosystem/docs/concepts/HEURISTIC_OF_SANITY.md
Estado: **vigente**.

- **6 Gate M→G:** "Only content that preserves truth and sense should advance toward persistent knowledge"; categorías `Sinn`/`Sinnlos`/`Unsinnig` (`:9-30`).
- **4 Enforcement:** "primarily enforced by `truth_machine` durante validation"; decide "can be consolidated into `kgdb`" (`:34-42`).

## /home/jp/proyectos/hum-ecosystem/docs/concepts/DETERMINIZATION_PRINCIPLE.md
Estado: **vigente**.

- **8 Decisión:** "anything which can be made explicit, repeatable, and mechanically reliable should be pushed out of agent ambiguity and into structure" (`:9`).
- **2 Formas preferidas:** "deterministic Python tool", "structured document", "explicit contract", "explicit procedure or protocol" (`:28-34`).

## /home/jp/proyectos/hum-ecosystem/docs/concepts/DECONVERSION.md
Estado: **vigente**.

- **6 Retorno G→S:** "starting from graph-backed knowledge… rendering that material into SL or NL output" (`:11`); "deconversion is the return path from `G` back toward `S`" (`:30`).
- **8 Constraints:** grounded, goal-shaped, coherent, "loss-aware: explicit that rendering is a projection, not the whole graph" (`:38-43`).

## /home/jp/proyectos/hum-ecosystem/docs/concepts/FAMILIES.md
Estado: **vigente**.

- **3 Families:** "metadata-driven grouping coordinates that keep structured surface documents organized without collapsing distinct contexts"; "routing and organization primitives for the surface layer" (`:9-25`).
- **6 Relación SMG:** "Families primarily belong to `S`"; no definen "graph persistence rules" (`:29-60`).

## /home/jp/proyectos/wittgenstein/README.md (+ matrix-shrdlu-spec/README.md)
Estado: **vigente**.

- **1/2 Fuente de verdad:** "`atoms/` es la fuente durable; los `docs/` de cada paquete son materializaciones" (`README.md:93`). El flujo: texto → populator (chunks, signos locales, offsets) → descompositor (declaraciones `(R a b)` con `Prov`) → `WiGame` con hechos `observed` (`README.md:78-83`).
- **2/4 Estado:** "un `WiGame` por (contexto, relación)"; promoción por convergencia o curado → consultas ruteadas con ruta y cita (`matrix-shrdlu-spec/README.md:14-20`).

## /home/jp/proyectos/wittgenstein/matrix-shrdlu-spec/specs/provenance.md
Estado: **vigente** (spec que da esquema a `Fact.evidence`).

- **2 Evidencia:** "En `Fact.evidence` (`matrix/src/operational_model/core/fact.py:20`, `dict[str, Any]`). Hoy no tiene esquema, solo lo escribe un test y lo serializa `system/wi_game_serialization.py:85,125`" (`:5`).
- **2 Contenido:** `prov` = lista `(document_id, span, extractor, confidence)`; `tier`; `curation` `proposed|confirmed|rejected`; `stats`; `derived_from`; `history` (`:18-27`).
- **4 Niveles:** observed > confirmed > inferred > statistical; "Un hecho `statistical` nunca participa en la deducción" (`:28-36`).
- **4 Conflictos:** "nunca se sobrescribe en silencio"; `retract` "no destruye: pone `truth=FALSE` o `UNKNOWN`… y anota `history`" (`:38-44`).

## /home/jp/proyectos/wittgenstein/matrix-shrdlu-spec/specs/matrix.md
Estado: **vigente**.

- **2 Persistencia:** "Serialización YAML de mundos: `system/wi_game_serialization.py`, `examples/*.yaml` — Formato `wigame_id, axis_a, axis_b, relation, Vi{1/0/∅}, Si{V/L/U}, facts[]`" (`:16`).
- **8 Decisión:** "El estado vive en cada `WiGame`"; "Hipertensor canónico global y migración a JAX… no se hacen" (`:33`).
- **7 Escala:** "**Nunca `p×p`**: ninguna estructura puede crecer con el cuadrado del número de símbolos del corpus (tres OOM el 2026-09-20)"; límite 4 GB por comando (`:57-60`).

## /home/jp/proyectos/wittgenstein/matrix-shrdlu-spec/specs/grounding.md · SPEC.md
Estado: **vigente**.

- **4/6 Promoción:** estados `proposed | confirmed | rejected`; "El LLM no decide verdad… Actúa como descompositor de superficie"; reglas de promoción automática `φ ≥ 0.95` (`grounding:35-45`).
- **2 Unidad lógica:** "La unidad es la configuración `(R a b)`"; `WiGame` por `(contexto, R)` (`SPEC.md:5-20`).
- **1 Fuente de verdad:** "No hay mapeo automático texto → ontología. Lo que se garantiza es: **todo hecho puede referenciarse a su fuente** (`Prov(f)`) y **nada pasa de `UNKNOWN` a `TRUE` sin una decisión registrada**" (`grounding:5`).

---

## Tabla de ejes

| Eje | `addressability_model` | `user-manual` | `store.atom` | `versionado` | `smg-ir` | `sldb-absorbs-kgdb` | `edges-retirement-plan` | `.sldb/README` | `wittgenstein/provenance` | `SMG_DEFINITIONS` |
|---|---|---|---|---|---|---|---|---|---|---|
| 1 Fuente de verdad | store = índice de campos; markdown canónico | store = metadata, no contenido | store = metadata workspace, no source-of-truth | shards apuntan al `.md` | solo 3 floors materiales | grafo es proyección derivada del store | fuente nueva aún lee shards viejos | core=estado, runtime=caché | hechos con `Prov`; nada pasa a TRUE sin decisión | G = grafo de triples validados; S ancla a realidad |
| 2 Qué se persiste | path+model por doc; hashes c/d | name/path/hashes/semantic_tags; no contenido | YAML indexes + capas de hash | hashes a/b/c/d; journal por write | payload+content_hash+field_hashes en shard | edge index per-doc shard | edges 16MB vs snapshot 350KB | models/documents/policy en core | `Fact.evidence` (prov/tier/stats) | triples SPO con vínculo a S |
| 3 Identidad/punteros | `st.`/`se.`/`gse.`; `sldb://model|document|section` | name lógico + path físico | `~/.sldb` fallback | `hash_d` como versión | context=store+model+path | ids `sldb://<kind>/...` | ids portables en snapshot | — | símbolo = (signo, contexto) | ids canónicos en M |
| 4 Historia/versionado | doc/field estables; secciones no | `stores update` rehace runtime | — | journal auditoría, sin replay; git aparte | IR por query | journal por write, encadenado | rebuild = regenerar snapshot | versionado vs gitignore pendiente | `history` en evidence; retract anota | M consolida en G |
| 5 AST | — | — | — | — | nunca persistido; caché en memoria | — | — | — | — | M = S-exprs operables |
| 6 Capa G | provenance de export | semantic_dag + equivalencias | — | — | graph edges en IR | edge index + snapshot networkx | retiro de shards, snapshot como fuente | — | hechos R a b | G = hechos + `kgdb` |
| 7 Escala | cada read extrae todo el store | 4 hashes; cache/ legacy | — | — | — | 1812 nodos / 16013 aristas | 16MB vs 350KB | — | nunca p×p; tope 4GB | — |
| 8 Decisión | read/write por address | shard por doc | store no reemplaza markdown | version=hash_d | solo 3 floors materiales | KGDB absorbido; 2 capas internas | retirar runtime/edges | core/runtime/config | `tier` decide deducción | S→M→G |
| 9 Contradicciones | — | claves persistidas hash_c/d vs real | — | — | DOCUMENT con payload vs shard real sin payload | — | nueva capa lee vieja | — | — | — |

---

## Contradicciones (con citas de ambos lados)

1. **`documents/<Model>.yaml` (índice por modelo) vs shards por documento.**
   - `current-system-overview.md:25-36` y `database_component_diagram.md` describen `documents/<Model>.yaml`.
   - El store real tiene `.sldb/core/documents/<Model>/<doc>.yaml` (1552 shards) y **ningún** `documents/<Model>.yaml`; `models/AtomDoc.yaml` apunta a `documents_index: .sldb/core/documents/AtomDoc.yaml`, que no existe.
   - El user-manual ya describe lo real ("un archivo por documento", `user-manual.md:231`).

2. **El shard contiene `payload` vs no lo contiene.**
   - `smg-ir.md:30`: "DOCUMENT -> payload + content_hash + field_hashes".
   - Real: 1552/1552 shards sin `payload`; solo `name, path, source_hash, content_hash, field_hashes, semantic_tags`. Coherente con `user-manual.md:244` ("**No guarda el contenido.**").

3. **Claves persistidas `hash_c`/`hash_d` vs `source_hash`/`content_hash`.**
   - `user-manual.md:301`: "Las claves persistidas en disco siguen siendo `hash_c`/`hash_d`".
   - Real: los shards persisten `source_hash`/`content_hash`; `versionado-y-colaboracion.md:27-29` dice que el rename reescribe las claves nuevas con `stores migrate-hashes`.

4. **Frontera SLDB/KGDB como repos vs capas internas.**
   - `atoms/text-layer-vs-graph-layer.atom.md`: "SLDB owns the text layer and KGDB owns the graph layer" (`:11`).
   - `architecture/sldb-absorbs-kgdb.md:33`: "**SLDB is one product with two internal layers**… KGDB stops being a separate product". El atom no fue actualizado.

5. **El retiro de `runtime/edges/` está decidido vs sigue presente.**
   - `edges-retirement-plan.md:4`: la capa "se retira".
   - Real: `.sldb/runtime/edges/` existe (41 MB) y este store no tiene `runtime/graphs/`; la nota `20260924-010708` confirma que deskops no consume los símbolos retirados.

6. **`hash_d` como token de concurrencia vs shards sin payload.**
   - `versionado-y-colaboracion.md:60-62` usa `hash_d` como "versión" y firma del payload; el shard no guarda el payload, así que `/save` compara contra un payload re-extraído, no almacenado (coherente con `addressability_model.md:114`).

7. **`sldb-knowledge-graph-integration.md` (sldb = minimal graph, kg_db = full) vs `sldb-absorbs-kgdb.md`.**
   - El request (`:30-40`) reserva OWL/analytics a kg_db; el ADR posterior habilita traversal y analytics dentro de SLDB (`:50-60`, amendment).

---

## Cómo persiste v1 hoy

Descripción del store real `/home/jp/proyectos/hum-ecosystem/tools/sldb/.sldb/` (inspección directa, 2026-10-08). Contrasta con `user-manual.md:206-231`; las diferencias se marcan.

### `git`
- `core/` **versionado** (1587 archivos tracked); `runtime/` y `.config/` **ignorados** (`.gitignore:21-22`).
- Pendiente de la decisión del dueño (`desk/inbox/20261007-133032…:20`): sacar `core/documents/` (1552 shards) y `core/store_index.yaml` de git, dejando `.sldb/` ignorado salvo `README.md`.

### Árbol observado
```text
.sldb/
  README.md
  core/
    store_index.yaml                 # raíz: stores, models, predicates, store_hash
    models/<Model>.yaml              # 35 índices de modelo
    documents/<Model>/<doc>.yaml     # 1552 shards, uno por documento
  runtime/
    sections/<Model>/<doc>.yaml      # 13 MB
    semantic/<Model>/<doc>.yaml      # 6.4 MB
    edges/<Model>.yaml               # contribución del modelo
    edges/<Model>/<doc>.yaml         # contribución por documento (41 MB total)
    edges.yaml
    semantic_dag.yaml
    journal/NNNNNNNNNN.yaml          # append-only, 689+ entradas
    locks/{store.lock,journal.lock}
```
No hay `runtime/cache/` en este store (el user-manual nota que "`cache/` aparece en stores antiguos").

### Ejemplo real mínimo — raíz `core/store_index.yaml`
```yaml
stores: []
models:
- name: AtomDoc
  model_ref: deskops.models:AtomDoc
  path: /home/jp/proyectos/hum-ecosystem/tools/deskops/deskops/models/atom.py
  models_index: .sldb/core/models/AtomDoc.yaml
  version: 1
  canonical: false
  family: null
  semantics: [...]
predicates:
- name: has_document
  axis: WHAT
  description: The model has this document as an instance.
store_hash: 889feeff5fdf5f849367e72423a63bd1f6d413244714cc63679c23957121de55
```
Incluye `path` absoluto de modelos externos (el defecto de `model-path-not-portable.md:11-20`).

### Ejemplo real mínimo — `core/models/AtomDoc.yaml`
```yaml
name: AtomDoc
model_ref: deskops.models:AtomDoc
path: /home/jp/proyectos/hum-ecosystem/tools/deskops/deskops/models/atom.py
documents_index: .sldb/core/documents/AtomDoc.yaml   # ruta registrada; no existe como archivo
sections_index: .sldb/runtime/sections/AtomDoc.yaml  # ídem: es directorio
model_hash: 7ccdc76c…
roster_hash: 69cc066c…      # hijos de model_hash (user-manual:260)
contract_hash: 1efa0e43…
documents_count: 4
version: 1
canonical: false
family: null
semantics: [representation.markdown, source.document.markdown, type.knowledge.atom, workspace.desk.atoms]
base_models: []
```

### Ejemplo real mínimo — shard `core/documents/AtomDoc/<doc>.yaml`
```yaml
name: atom-el-desk-puede-afirmar-lo-contrario-del-repo-y-nada-falla
path: desk/atoms/atom-el-desk-puede-afirmar-lo-contrario-del-repo-y-nada-falla.md
source_hash: 4502a504…      # hash_c: texto markdown
content_hash: 8f628bf5…      # hash_d: payload extraído
field_hashes:
  id: 155f261d…
  title: 56c6c907…
  five_wh_one_plus: c7d4bb6c…
  answer: d3330cad…
  tags: 605ee3c2…
  provenance: 3fd05cdc…
semantic_tags: [representation.markdown, source.document.markdown, system:deskops, …, workspace.desk.atoms]
```
Sin `payload`: el payload se re-extrae del `.md` (contradicción 2).

### Ejemplo real mínimo — `runtime/sections/<Model>/<doc>.yaml`
```yaml
doc_name: atom-el-desk-puede-afirmar-lo-contrario-del-repo-y-nada-falla
sections:
- path: el-desk-puede-afirmar-lo-contrario-del-repo-y-nada-falla
  title: El desk puede afirmar lo contrario del repo y nada falla
  breadcrumbs: [...]
  about: [...]
  semantic_tags: [...]
  slug: el-desk-puede-afirmar-lo-contrario-del-repo-y-nada-falla
  level: 1
  line_start: 14
  line_end: 18
source_hash: 4502a504…
sections_version: '2'
```

### Ejemplo real mínimo — `runtime/semantic/<Model>/<doc>.yaml`
```yaml
model: AtomDoc
path: desk/atoms/atom-el-desk-puede-afirmar-lo-contrario-del-repo-y-nada-falla.md
tags: [representation.markdown, source.document.markdown, system:deskops, …, workspace.desk.atoms]
source_hash: 4502a504…
```

### Ejemplo real mínimo — `runtime/edges/<Model>/<doc>.yaml`
```yaml
edges_version: 3|sections:2
nodes:
- id: sldb://model/TaskDoc
  node_type: sldb_model
  semantics: {name: TaskDoc, model_ref: deskops.models:TaskDoc, …}
  facets: {}
- id: sldb://semantic_tag/type.workflow.task
  node_type: semantic_tag
  semantics: {tag: type.workflow.task}
edges:
- source: sldb://section/AtomDoc:<doc>#…/answer
  target: sldb://semantic_tag/workspace.desk.atoms
  relation: tagged_as
  metadata: {origin: structural}
content_hash: 8f628bf5…
```
Además existe un shard por modelo (`runtime/edges/<Model>.yaml`) con nodos de modelo/fields.

### Ejemplo real mínimo — `runtime/journal/<N>.yaml`
```yaml
operation: untrack_document
address: RelationTypeDoc:reltype-references
field: null
previous_value: null
new_value: null
hash_c_before: 0fa56e8a…   hash_c_after: null
hash_d_before: 0942ea04…   hash_d_after: null
hash_a_before: c8536196…   hash_a_after: 695f17b3…
actor: null
timestamp: 2026-10-02 02:31:48.232612+00:00
previous_hash: da32aadf…
entry_hash: 20d02036…
```
Append-only y encadenado (`versionado-y-colaboracion.md:38-41`).

### Cifras reales
- 1552 shards de documento; 41 MB `runtime/edges/`; 13 MB `runtime/sections/`; 6.4 MB `runtime/semantic/`; 6.4 MB `core/documents/`.
- El peso de `edges` es exactamente el motivo del retiro documentado en `edges-retirement-plan.md:11`.

---

## Documentos revisados y descartados (sin aporte al diseño de store)

- `docs/architecture/current-cli-tree.md` — inventario de comandos.
- `docs/architecture/current-source-tree.md` — mapa de fuentes.
- `docs/architecture/superseded-code-audit.md` — auditoría de código muerto.
- `docs/architecture/refactored_components.md` — solo diagrama de capas, sin layout/persistencia.
- `docs/architecture_map.md` — puntero/índice.
- `docs/documentation-review.md` — findings de CLI/anchoring; usa el store pero no define su diseño (sí confirma discovery de stores y `~/.sldb`, `:29-46`).
- `docs/packaging instructions.md`, `docs/faq.md` (fuera de la definición de store), `docs/sldb_*` restantes generados.
- `docs/surface/tasks/*` — historia de tareas de UI.
- `docs/architecture/spec2viz/*` — vistas generadas.
- `docs/surface/mockup-*.html` — mock sin backend.
- `wittgenstein/matrix-shrdlu-spec/specs/{chic,populator,appendix_causality,sources,shrdlu,runtime_api}.md` — mecanismos de SIA/interfaz; solo `populator` roza el almacenamiento (offsets) y se citó.
