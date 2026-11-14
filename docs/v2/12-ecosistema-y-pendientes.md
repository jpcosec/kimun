# Ecosistema y pendientes — inventario (2026-10-09)

> **Actualización 2026-11-14:** las filas D0 y K1 de §1 que dicen «identidad =
> lugar» e «ids por lugar» están superadas por la corrección del dueño en
> `14-juego-del-lenguaje.md`. K1 sigue bloqueada hasta definir identidad en
> positivo y su continuidad. G espera la base S–M. El backlog aún no incorpora
> heartbeat, relaciones/embeddings por unidad ni ejecuciones marcadas; la
> auditoría y el mapa de ambigüedades están en `runs/research/2026-11-14/concilio/`.

> Pendientes abiertos en kimun, quién usa hoy sldb v1 y qué es `wittgenstein` (motor de la capa G).
> El estado y las decisiones vigentes están en `05-estado.md`; el detalle test a test de la
> paridad, en `tests/parity-v1.md` y `scripts/parity/coverage.py`.

## 1. Pendientes en kimun

### Complemento de cierre (2026-11-14)

La tabla de 2026-10-09 abajo conserva el inventario anterior. El concilio revisó
sus huecos en `runs/research/2026-11-14/concilio/backlog-agy.md` (inventario
exhaustivo) y `backlog-pi.md` (dependencias y pruebas). La auditoría del spec
está en `auditoria-spec.md`; las ambigüedades discriminables en `ambiguedades.md`.
Este complemento no fija prioridades ni decide la identidad por inferencia.

| frente | tareas para cerrarlo | depende de | evidencia de cierre |
|---|---|---|---|
| Autoridad y contratos | integrar la dirección 2026-11-14 en `01`, `02`, `05`, `12`, `14`; cerrar D0 en positivo y casos de continuidad; distinguir nodo, observación y ancla | corrección del dueño; mapa de ambigüedades | contrato único S–M con casos de editar, mover, copiar, dividir, fusionar, borrar y cambio de contrato |
| Sensor y store | K2a/K2b, K6, K8, K3, anclas CST, caché verificable, origen de señales, coste incremental | contrato S–M; K2b y anclas dependen de D0 | Merkle por ruta sobre lo trackeado; mutación local visible y reconciliable sin pérdida silenciosa |
| Modelos y código | P1, M1/spec 16, M2, S1, superficie de código; separar anclaje de generación | contrato S–M; implementación de M1 depende de P1 y D0 | documento y función de código clasificados, anclados, consultables y regenerables |
| Relaciones y continuo | tipar relaciones jerárquicas, horizontales, métricas y recursivas; consulta por unidad; embeddings por unidad y actualización asíncrona | D0 y contrato de relaciones | relaciones declaradas recorribles; vecindad continua visible con modelo y vigencia identificables |
| Heartbeat y respuestas | estabilizar señal, daemon de recálculo, cursores/épocas y consumo asíncrono, causalidad sin bucles | `stores sense` es primer corte; contrato del heartbeat y sensor S–M | cambios externos y resultados internos se observan sin ejecutar acciones en el daemon; reinicio y repetición cubiertos |
| Ejecuciones marcadas | definir recibo, resultado, dependencias, verificador e invalidación | contrato S–M y canal asíncrono | resultado histórico y vigencia actual distinguibles en un caso reproducible |
| G | T01, D-G, G1–G3, K7 y consultas justificadas sobre contextos | cierre de S–M; fuentes de `wittgenstein` | fixtures contextuales y trazas de soporte; sin inferencia entre contextos por coincidencia de texto |
| Operación y vista | C1/F25, K4/K5, C2/API, seguridad, CI/release reproducible, piloto y vista del circuito | contratos de alcance y capas correspondientes | suite, oráculo, include check, fallos inyectados, bundle limpio y recorrido visible del cambio a la respuesta |

El primer daemon de latido está implementado en `stores heartbeat`
(`runs/subagents/2026-11-14-heartbeat/`): recalcula y emite señales sin ejecutar
respuestas. Su revisión independiente está en
`runs/research/2026-11-14/concilio/review-heartbeat-pi.md`; la integración
asíncrona, el coste incremental y la cobertura topológica completa siguen abiertos.

### Qué bloquea qué (2026-10-09)

No hay prioridades: todo lo de esta lista se hace. El grafo dice qué depende de qué, para trabajar en
paralelo sin romper invariantes. Registro de la sesión: `docs/v2/drafts/sesion-2026-10-08-09.md`.

**Invariantes que ningún trabajo puede romper:** `bb lint` (anillos de `03 §1`) y `bb test` en verde;
paridad v1 sin brechas (`scripts/parity/coverage.py … --strict --allow-open-prefix selfdoc/`);
`bin/kimun include check` limpio; todo `bb`/script pesado bajo `memcap` (`AGENTS.md`). `bb oracle`
(ids canónicos) solo puede cambiar dentro de K1, y a propósito.

| id | trabajo | depende de | dónde está descrito |
|---|---|---|---|
| D0 | **decisión del dueño**: identidad = lugar (estructural para S, semántico para M/G); hash = verificación de mutación | — | `docs/v2/drafts/fuentes-identidad.md`, `14 §21`, decisión 46 |
| K1 | kernel: ids por lugar, hash como atributo de verificación; borrado de un ID estructural se **declara** (transparencia) | D0 | `14 §21`, `docs/v2/drafts/ids-desde-matrix.md` |
| K2a | store estable: registro por ruta (Merkle **por ruta** sobre lo trackeado) y plan del build fuera del CAS | — | `14 §19–§20`, decisiones 43–44, `10 §11` |
| K2b | store estable: sin índice mmap por ids internos ni las tres aristas redundantes por documento | K1, K2a | `14 §20` (diagnóstico del formato 4) |
| K3 | documentos sin modelo registrados con la clase genérica del perfil (hoy persisten su árbol) | — | `14 §18`, decisión 41 |
| K4 | packfiles (bloque de 4 KB por objeto pequeño en disco y caché) | K2a | `runs/research/2026-10-08/A4-packfiles.patch`, `A4-report.md` |
| K5 | índice de nombres y GC | K2a | `10 §4` |
| P1 | separar el grafo en un paquete aparte (`kimun.graph.*`: modelos, aristas semánticas, grafo, capa G) con regla de anillo en `bb lint` | — (archivos disjuntos de K2a/K3 salvo `kimun.surface.docs`: coordinar) | `14 §19`, decisión 43 |
| M1 | spec 16: modelos de anclaje y de generación, selector EDN sobre tree-sitter, herencia de clases; features de v1 sí, bugs no | — (spec); implementación ← P1, K1 | `14 §18`, decisiones 41–42, `runs/research/2026-10-08/R3-report.md` |
| M2 | regenerar los modelos del desk (borrados el 2026-10-09 para dejar una versión estable) | M1 | `desk/`, `kimun.include.edn` |
| S1 | parser M fase 2: cerrar el ~3 % de divergencia del CST en Markdown libre | — | `04 §11.2` |
| S2 | parser M fase 3: anclas por rangos de bytes / nodos del CST | K1 | `runs/research/2026-10-08/R2-report.md §5` |
| G1 | capa G: leer fuentes (paper, wittgenstein, Tractatus) y reescribir el spec sobre sentido × verdad | D0, D-G, M1, T01 | `docs/v2/drafts/capa-g-dudas.md`, `runs/research/2026-10-08/spec15/` (borrador previo, solo referencia) |
| G2 | consulta Datalog (Datalevin pod + respaldo in-process) | G1 | `runs/research/2026-10-08/R1-report.md` |
| G3 | embeddings sobre anchors (modelo multilingüe) | G1, K2a | decisión 38 |
| C1 | selfdoc: guarda de before-image al escribir y chequeo del grafo kgdb | — | `tests/parity-v1.md` (estados `corpus` y `abierta`) |
| C2 | cliente Python y rutas HTTP de graph_ui (`/kgdb/snapshot`, `/models/promote`) | K2a, P1 | `python/README.md`, `06` |
| T01 | manifest de inclusión C01–C28 (Wittgenstein W1+W2) con `required/deferred/excluded`, fixture y evidencia | — (documentación) | `docs/reviews/wittgenstein-superset/proposals/01-superset-contract.md`, `reviews/01-coverage.md` |
| K6 | contrato de parse/selector registrado como digest (gramática, perfil, normalización, selector, clase); distinguir `source-changed` / `contract-changed` / `parser-unavailable` / `rebuild-mismatch` | — | `docs/reviews/wittgenstein-superset/reviews/02-findings.md` F08, `proposals/03-store-and-authority.md` |
| K7 | licencia de inferencia/ruteo separada del alcance de búsqueda (stores enlazados y tags globales no autorizan cruces de contexto) | G1 | F22 |
| K8 | nombres cualificados por ruta; el nombre corto es alias (hoy `a/README.md` y `b/README.md` chocan) | — (alinear con K2a) | F23 |
| DOC1 | reescribir `14` por tema (integrar adendas §16–§21; separar contrato objetivo, implementado e histórico) | — | F18 |

Insumo de G1: la revisión `docs/reviews/wittgenstein-superset/` (propuestas P4 grafo contextual y P5 T02–T13) y la respuesta `respuesta-claude.md`.

En paralelo hoy, sin esperar al dueño: T01, K2a, K3, K6, K8, P1 (coordinando `kimun.surface.docs`),
M1 (spec), S1, C1 (más F25: before-image de la escritura del source), DOC1. Esperan a D0: K1, K2b, S2, G1 y lo que cuelga de ellos.

### Deuda de diseño y experimentos

- **Parser Markdown**: `kimun.surface.models.blocks/parse` segmenta con el pod tree-sitter
  (decisión 29, fase 1 de R2 §5) y `parse-native` queda como oráculo diferencial (paridad de
  kinds y spans 1099/1099 en corpus + desk, `blocks-parity-test`). La fase 2 existe
  (`kimun.surface.markdown.cst-ast`, bandera `KIMUN_PARSER=treesitter`): AST idéntico
  1099/1099 en corpus + desk, pero ~3 % de divergencia en Markdown libre, así que el default
  sigue siendo `native` (ver `04 §11.2`). Pendiente: cerrar esas clases y la fase 3 (rangos de
  bytes e ids de nodo del CST).
- **Composiciones del descriptor**: la clave es la de v1 (`:py/compositions`), aceptada junto
  a `:compositions`; `07 §2` propone `{:model :field :order}`.
- **Concesiones de M/G**: `:symbol` y `:fact` resuelven por existir en el CAS, porque todavía
  no viven en árboles `:context` (`02 §6.2`).
- **Fuera del perfil Markdown**: `_` como énfasis, tablas, footnotes e imágenes (`04 §11`).
- **Board legado**: `desk/tasks/Board.md` no es editable por CLI.
- **Tree-sitter como pod**: el pod existe y es el parser de la capa M
  (`kimun.host.treesitter`, `pods/treesitter`, decisión 29). Falta el parseo de **código**
  como superficie de modelos (hoy el adaptador `treesitter-symbols` de `11 §9` cubre
  símbolos) y la fase 3 (ids de nodo del CST como anclas).

## 2. Consumidores y superficies

**kimun no tiene consumidores en código.** Aparece solo en documentos (pron, legos, graph_ui,
iso-lab, knowledge). El único store `.kimun/` que existe hoy en `/home/jp/proyectos` es el
desk de este repo, que se construye al vuelo (`13-include.md`).

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

Casi todos entran por dentro (`sldb.cli.*`, `sldb.store.io`, `sldb.runtime.validation`), no
por `sldb.api`. Casi todos los stores de 17 modelos son desks de deskops, incluido el de kimun.

| UI | tecnología | canal |
|---|---|---|
| graph_ui `frontends/mindmap` (activa) | React + React Flow por CDN, server Python | librería (local) o HTTP a `sldb serve` (remoto) |
| graph_ui `apps/knowledge-workbench` | React 18 + Vite 5 | HTTP |
| graph_ui `apps/review-workbench` (histórica) | React + xyflow + Vite | HTTP |
| sldb `surface/` | React 18 + Vite 7 + ProseMirror (editor) | HTTP |
| sldb-ui | Astro 7 + React 19 + Bun | CLI `python -m sldb … --format json` |
| kbsurfaces | lanzador de las tres por store (puertos 8800/8810/8820) | CLI + HTTP + lee `.sldb/` |

`kimun serve` usa el mismo puerto (8787) y casi las mismas rutas: las UIs por HTTP son lo más
barato de mover; deskops es lo que más stores mueve.

## 3. wittgenstein: motor de G (no almacén)

La capa G (hechos, contextos, verdad y sentido) es un **paquete aparte de kimun sobre la base**
(decisión 43, `14 §19`); wittgenstein es su **productor y evaluador**, no su almacén (`14 §11`).

`/home/jp/proyectos/wittgenstein` (Python): texto → contextos y signos locales con offsets →
declaraciones `(R a b) @ctx` → un WiGame por (contexto, relación) con matriz de verdad V_i y de
sentido S_i (Sinn/Sinnlos/Unsinnig) → reglas `(if …)` y deducción (I*) → convergencia entre
contextos (identidad propuesta por implicación entre configuraciones, nunca por string) →
curaduría. Cada hecho lleva evidencia
`{prov:[{document_id, span, extractor, confidence}], tier, curation, derived_from, history}`.
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
`tools/kb/store.py` y el legado `hashed_tractatus`, que ya hashean contenido); offsets en code
points sobre el documento entero vs grafemas por hoja; `:proposition` sin formas anidadas;
verdad y sentido sin lugar en el triple; símbolos atados al idioma (`she@Q7186`) y relaciones
en inglés.

Estado del repo: raíz sin commits, falta `MIGRATION.md`, no existe el layout del README, las
dos líneas del motor (`f13c67d`, `f016c75`) no están reconciliadas (sí comparten historia vía
`origin/main`, contra lo que dice el README); persistencia mínima (un YAML por WiGame).

Pendiente de kimun: el adaptador que lee S/M de kimun y escribe hechos G con evidencia en
aristas, y el diseño de esa frontera (decisión 19, `05-estado.md §3`).

## 4. Ecosistema sldb v1

- **v1 sigue en v1** (decisión 18): los consumidores de la tabla de §2 no se migran por ahora
  y kimun no está desplegado, así que sus stores e historial se pueden regenerar. Detalle de
  la decisión: `05-estado.md §3`.
- **Pendiente en el repo de sldb v1** (su propia inbox): el store `.sldb` no se versiona; hay
  que sacar shards e índice de git (`f9047b5`).
- `~/.sldb` ya no es un inventario de decenas de stores enlazados.
