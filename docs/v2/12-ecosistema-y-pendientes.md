# Ecosistema y pendientes — inventario (2026-10-08)

> Pendientes abiertos en kimun, quién usa hoy sldb v1 y qué es `wittgenstein` (la capa G).
> El estado y las decisiones vigentes están en `05-estado.md`; el detalle test a test de la
> paridad, en `tests/parity-v1.md` y `scripts/parity/coverage.py`.

## 1. Pendientes en kimun

### Correctitud (de más a menos grave)

1. **selfdoc**: falta la guarda de before-image al escribir (estado `corpus`) y el chequeo
   del grafo kgdb (estado `abierta`).

### Kernel

- **Formato 3, resto de `14 §10`** (decisión 27; ver `10-escala.md §10`): el árbol estructural
  de un documento con anchor externo se sigue persistiendo entero (nodos `block`/`text`/`opaque`
  y tree objects) en vez de ser caché verificable (root hash + posiciones referenciadas), y el
  plan resuelto de un `include build` sigue en el CAS, así que el store de este repo (8,75 MB
  aparentes) no baja de los 433 KB del desk que indexa. Hacerlo exige un resolver de árbol que
  no guarda texto y, para un store `:git :ignore`, no persistir el plan (o el árbol) del build.
- **Packfiles**: el disco desperdicia un bloque de 4 KB por objeto pequeño. Números en
  `tests/parity-v1.md` §Diferido y `10-escala.md §9`.
- **Índice de nombres O(árboles)**: `docs show` y `fields set` leen un descriptor por árbol;
  un índice persistente en la Revision lo haría constante (`10-escala.md §4`).
- **Sin GC** ni informe de objetos inalcanzables.

### Funciones

- **Embeddings**: `kimun.surface.index.corpus/rank` acepta `:score-fn`, pero no hay modelo
  conectado ni vectores persistidos.
- **Capa G**: nada produce `:fact/:triple`; el evaluador de S-expressions (S4) y los
  derivados (S5) no existen (`06 §6`, `docs/archive/v2/09-evaluador-anclado.md`).
- **Lenguaje de consulta general** (Datalog/Datascript): el índice derivado es EAV-compatible.
- **Cliente Python**: no existe; `python/kimun` solo exporta modelos y aloja adaptadores
  (`python/README.md`).
- **Rutas HTTP para graph_ui**: `/kgdb/snapshot` y `/models/promote` no existen en `serve`.

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

## 3. wittgenstein: la capa G

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
