# Ecosistema y pendientes — inventario (2026-10-06)

> Foto tomada al cerrar la paridad con sldb v1. Responde tres preguntas: qué queda por hacer
> en kimun, quién usa sldb y kimun hoy, y qué es `wittgenstein` respecto de kimun. El detalle
> test a test de la paridad está en `tests/parity-v1.md` y en `scripts/parity/coverage.py`.

## 1. Pendientes

### Correctitud (de más a menos grave)
1. **Contrato al leer un documento `superseded`**: se extrae con el contrato NUEVO del modelo; sin
   backfill, el payload cambia en silencio. Idea de arreglo: registrar el contrato usado con un tipo
   de arista que la sucesión no elimine (p. ej. `:projection`) y extraer bajo ese descriptor.
2. **`models field-add` de un campo opcional** reasigna el último hueco de los documentos existentes
   (fijado en `test/kimun/parity/known_gaps_test.cljc`).
3. **Aristas y export de grafo no ven stores enlazados** (2 brechas, `parity-gaps.edn`).
4. **No se exige descripción por campo** en `descriptor/validate!` (1 brecha; exige `--description`
   en `models field-add` y actualizar fixtures).
5. **selfdoc**: guarda de before-image al escribir; chequeo del grafo kgdb (2 tests de v1 abiertos).

### Kernel
- Disco: 94 MB de datos ocupan 364 MB (80.435 de 82.787 objetos < 4 KB, un bloque cada uno) → packfiles.
- El índice por nombre lee el descriptor de cada árbol (O(árboles)) → índice persistente en la Revision.
- Sin GC ni informe de objetos inalcanzables.

### Funciones
- Embeddings: `kimun.surface.index.corpus/rank` acepta `:score-fn`, pero no hay modelo conectado ni
  vectores persistidos (drawer: `feature-semantic-indexing-and-embeddings`).
- Capa G: nada produce `:fact/:triple`; el evaluador de S-expressions (S4) no existe. Ver §3.
- Lenguaje de consulta general (Datalog/Datascript) — el índice derivado es EAV-compatible.
- Cliente Python (HTTP/CLI) — `python/kimun` solo exporta modelos y aloja adaptadores.
- Rutas HTTP que graph_ui usa y kimun no tiene: `/kgdb/snapshot`, `/models/promote`.

### Deuda de diseño
- `kimun.surface.models.blocks` imita la segmentación de markdown-it (por paridad byte a byte con v1)
  en vez de trabajar sobre el árbol SLDB-MD del kernel.
- Clave de composiciones del descriptor: la de v1 (`:py/compositions`), no la de `07 §2`.
- La tabla "Diferido" de `tests/parity-v1.md` conserva filas ya resueltas.

### Orden (sin código)
- Commits sin subir: kimun 108, sldb 14.
- 21 worktrees en `/home/jp/proyectos/kimun-worktrees`; ~1,4 GB de benchmarks en `/tmp/kimun-bench`, `/tmp/kimun-parity`.
- El store del desk se construye al vuelo con `bb desk` (alias de `kimun include build`) desde `kimun.include.edn`, que declara qué de este repo se versiona (modelos y documentos); los stores nunca se commitean (regla del dueño, 2026-10-07).
- sldb v1: 233 commits después del tag `v1-frozen`; el congelamiento no se respeta.
- `~/.sldb`: 49 stores enlazados, 37 ya no existen.

## 2. Consumidores y superficies

**kimun no tiene consumidores en código.** Aparece solo en documentos (pron, legos, graph_ui,
iso-lab, knowledge). No hay ningún `.kimun/` en `/home/jp/proyectos`.

| consumidor | cómo usa sldb v1 | store propio | último commit |
|---|---|---|---|
| deskops | librería profunda (`sldb.store.*`, `sldb.cli.*`, `runtime.validation`) + CLI `doctor` | 267 docs | 2026-10-05 |
| pron | librería profunda (`sldb.api`, `query_engine`, `store.io`) | 686 docs | 2026-10-03 |
| legos | librería + CLI | 230 docs | 2026-10-01 |
| TraderBot | librería + kgdb | 523 docs | 2026-09-20 |
| gemini_test | librería + CLI; despliegue Modal | 6 stores | 2026-09-16 |
| VSM viewer | modelos + `stores check` | 81 docs | 2026-10-03 |
| spec2viz | librería + lee `.sldb/runtime/knowledge_graph.kg.json` | 115 docs | 2026-09-09 |
| hum-core, hum | librería (**rota**) + CLI | 1 / 5 docs | 2026-09-20 / 08-19 |
| wittgenstein | `paper_v2` (pipeline de papers) | 5 stores | 2026-10-01 |
| AntonIA (`AWS_Infra`, `kbs`, `AgentsKBs`) | librería; kbsurfaces sirve sus stores | varios | 2026-10-05 |
| kgdb | capa hueca de re-exportación de sldb | 461 docs | 2026-09-20 |

Casi todos entran por dentro (`sldb.cli.*`, `sldb.store.io`, `sldb.runtime.validation`), no por `sldb.api`.
Casi todos los stores de 17 modelos son desks de deskops, incluido el de kimun.

| UI | tecnología | canal |
|---|---|---|
| graph_ui `frontends/mindmap` (activa) | React + React Flow por CDN, server Python | librería (local) o HTTP a `sldb serve` (remoto) |
| graph_ui `apps/knowledge-workbench` | React 18 + Vite 5 | HTTP |
| graph_ui `apps/review-workbench` (histórica) | React + xyflow + Vite | HTTP |
| sldb `surface/` | React 18 + Vite 7 + ProseMirror (editor) | HTTP |
| sldb-ui | Astro 7 + React 19 + Bun | CLI `python -m sldb … --format json` |
| kbsurfaces | lanzador de las tres por store (puertos 8800/8810/8820) | CLI + HTTP + lee `.sldb/` |

`kimun serve` usa el mismo puerto (8787) y casi las mismas rutas: las UIs por HTTP son lo más barato
de mover; deskops es lo que más stores mueve.

## 3. wittgenstein: la capa G

`/home/jp/proyectos/wittgenstein` (Python): texto → contextos y signos locales con offsets →
declaraciones `(R a b) @ctx` → un WiGame por (contexto, relación) con matriz de verdad V_i y de
sentido S_i (Sinn/Sinnlos/Unsinnig) → reglas `(if …)` y deducción (I*) → convergencia entre contextos
(identidad propuesta por implicación entre configuraciones, nunca por string) → curaduría. Cada hecho
lleva evidencia `{prov:[{document_id, span, extractor, confidence}], tier, curation, derived_from, history}`.
Runtime de S-expressions. Diseño que manda: `matrix-shrdlu-spec/specs/convergence.md`.

| wittgenstein | kimun |
|---|---|
| `Prov{document_id, span}` | arista con evidencia hacia `:sign/:span` |
| contexto | `:fact/:context` |
| declaración / `Fact.proposition` | `:fact/:triple` |
| regla `(if …)` | `:symbol/:proposition` |
| promote/reject con historia | sucesión con actor |
| V_i, S_i, I, proyecciones | índices derivados, no nodos |

Desajustes: evidencia dentro del hecho (kimun la quiere en aristas); ids aleatorios (salvo
`tools/kb/store.py` y el legado `hashed_tractatus`, que ya hashean contenido); offsets en code points
sobre el documento entero vs grafemas por hoja; `:proposition` sin formas anidadas; verdad y sentido
sin lugar en el triple; símbolos atados al idioma (`she@Q7186`) y relaciones en inglés.

Estado del repo: raíz sin commits, falta `MIGRATION.md`, no existe el layout del README, las dos
líneas del motor (`f13c67d`, `f016c75`) no están reconciliadas (sí comparten historia vía
`origin/main`, contra lo que dice el README); persistencia mínima (un YAML por WiGame).

## 4. Decisiones del 2026-10-06 (ver `05-estado.md`, 18 y 19)

- v1 sigue en v1: no se migran consumidores por ahora; kimun se puede regenerar ("retrackear").
- Lógica de alto nivel en Clojure; servicios nativos portables y plugins en cualquier lenguaje, con
  frontera clara. tree-sitter (C) como pod pasa a ser candidato a parser único de Markdown: reemplazaría
  `kimun.surface.models.blocks` (imitación de markdown-it) y el parser propio de `sldb.surface.markdown`;
  las diferencias de corte contra v1 se revisan y pasan a ser la nueva referencia.

## 5. Orden acordado (2026-10-06)

1. Limpieza — **hecha 2026-10-06**: kimun y sldb subidos a GitHub; 21 worktrees fusionados y sus ramas borrados; 14 GB de temporales en `/tmp` borrados y `bb test` ahora barre los que crea. El store del desk se construye al vuelo con `bb desk` (alias de `kimun include build`) desde `kimun.include.edn` (regla del dueño, 2026-10-07: los stores nunca se commitean).
2. Bug del contrato de documentos `superseded` (§1, ítem 1).
3. ~~Primer consumidor real de kimun~~ — postergado por la decisión 18 (v1 sigue en v1).
3b. Prototipo del pod de tree-sitter y medición de diferencias de corte contra el corpus.
4. En paralelo: consolidar wittgenstein y diseñar la frontera kimun↔wittgenstein (adaptador que lee
   S/M de kimun y escribe hechos G con evidencia en aristas).
