# Paridad con sldb v1 — marcador (cerrado)

> Objetivo: `kimun` equivalente a sldb v1, medido contra los tests de v1
> (`hum-ecosystem/tools/sldb/tests`: 82 archivos, 590 funciones). Cada función de v1 queda
> en un estado del marcador: **portada** (test kimun que ejerce la misma promesa),
> **mapeada** (otro test kimun), **oráculo** (cubierta por el corpus diferencial grabado
> desde v1), **n/a** (mecanismo interno que v2 reemplaza; se nombra el test del kernel que
> sostiene la promesa), **brecha** o sin clasificar (**mencionada**, **corpus**, **abierta**). El mapeo de
> comandos está en `docs/v2/06-superficies-y-cli.md`; las superficies retiradas, en ese
> mismo documento.

## Marcador (se regenera con `python3 scripts/parity/coverage.py <v1> .`)

Estado al 2026-10-09, sobre las 590 funciones de test de sldb v1 (marcador 518/1/14/55/0/0/1/1; actualizado el 2026-11-14 tras cerrar `C1/F25`: `portada` +1 → 519, `corpus` → 0):

| estado | funciones | qué significa |
|---|---|---|
| portada | 519 | un test de kimun la nombra y ejerce la misma promesa |
| mapeada | 1 | cubierta por un test de kimun con otro nombre (`parity-map.edn`) |
| oráculo | 14 | su tema es el motor y los corpus diferenciales la reproducen (`parity-oracle.edn`) |
| n/a | 55 | probaba un mecanismo que v2 no tiene por diseño; cada una con su razón y lo que sostiene la promesa hoy (`parity-na.edn`) |
| brecha | 0 | promesa de v1 que kimun **no** cumple todavía (`parity-gaps.edn`, fijadas en `test/kimun/parity/known_gaps_test.cljc`) |
| mencionada | 0 | nombrada solo en comentarios negativos (n/a, diferida…); no ejerce la promesa |
| corpus | 0 | cerró en 2026-11-14 al portar `selfdoc/test_preflight.py::test_before_image_guard_prevents_clobbering_concurrent_edits` (`kimun.cli.adapters-test/before-image-guard-prevents-clobbering-concurrent-edits`) |
| abierta | 1 | `selfdoc/test_python_scanner.py::test_kgdb_graph_check_rejects_missing_or_stale_source_nodes` |

Brechas abiertas (`parity-gaps.edn`): ninguna. Cerradas el 2026-10-08: A1 (un documento
`superseded` se lee bajo el contrato con el que se escribió; `models field-add` de un campo
opcional no reasigna el último hueco), A2 (`descriptor-requires-a-non-empty-description-per-field`,
`field-add-requires-a-description`, `field-add-of-an-optional-field-is-neutral-for-existing-documents`),
A3 (`linked-store-documents-come-back-qualified`, `graph-export-composes-linked-stores`), todas
en `test/kimun/parity/closed_gaps_test.cljc`.

## Cómo se mide

1. **Oráculo diferencial del motor**: un plugin de pytest corre la suite de v1 y graba cada
   llamada a extracción y render (clase de modelo → descriptor EDN, Markdown de entrada,
   payload, Markdown renderizado) en `test/fixtures/parity/engine/`. El motor de kimun
   tiene que reproducir cada caso. Son literalmente los mismos tests, en la frontera del motor.
2. **Tests portados**: para store, CLI, grafo, consultas y serve, cada test de v1 se
   reescribe contra la CLI/API de kimun con el vocabulario de `06-superficies-y-cli.md`.
3. **Corpus real**: `migrate --from-v1` sobre el `.sldb` de sldb (1.551 documentos) y
   comparación payload a payload.

## DAG y estado (histórico)

El DAG T1–E3 y la tabla de estado (todo *hecho*) se archivaron el 2026-10-09 en
`docs/archive/v2/tests/parity-v1-dag-y-estado.md`. El marcador de arriba es la traza viva.

## n/a previstos (mecanismos internos de v1)

`store/test_cache_identity`, `test_documents_shards`, `test_io_atomic_write`,
`test_legacy_layout_migration`, `test_merkle_cost`, `test_merkle_operation_cache`,
`test_merkle_shards`, `test_migration_hash_names` (×2), `test_runtime_cache_outside_store`,
`test_stale_shard_hashes`, `test_store_name_relpaths`, `test_runtime_cache`,
`test_clean_code_rules`: shards, cachés por capas, migraciones de layout y de nombres de
hash. En v2 no existen: los objetos son inmutables y direccionados por contenido. E3
nombra, para cada uno, el test del kernel que sostiene la promesa equivalente.

## Diferido (pendiente, lo recoge la tarea indicada)

| de | qué falta | lo recoge |
|---|---|---|
| ~~C2~~ | cerrado en A3: aristas y export de grafo ven los stores enlazados (`test_linked_store_documents_come_back_qualified`, `…linked_stores_gap_G5`); `--local` los deja fuera | — |
| K1 | **disco**: aparente 97,7 MB, pero **364 MB en disco real**, porque 80.435 de 82.787 objetos pesan menos de 4 KB y cada uno ocupa un bloque; la meta de 150 MB es solo aparente y **no se cumple en disco**. Arreglo: empaquetar objetos (packfiles al estilo git, `12 §1`); K1 sigue abierto | K2 (pendiente) |
| K1 | `docs show` y `fields set` leen el descriptor de cada árbol para el índice por nombre (O(árboles)); constante requeriría un índice persistente en la Revision; no hay GC ni informe de objetos inalcanzables | K2 (pendiente) |

Todo lo demás que estaba diferido quedó portado o declarado n/a; lo mide
`scripts/parity/coverage.py` (marcador arriba).

## Desviaciones deliberadas respecto de v1 (acumuladas)

- `--where` no reconocido es `query/bad-where` con posición, no `False` silencioso (`06-superficies-y-cli.md`). Regex inválida se rechaza al parsear. `>=`/`<=` entre tipos distintos da falso en vez de `TypeError`. Dialecto de regex: Java.
- Adaptadores: `sync` deja de rastrear los documentos huérfanos que el adaptador posee (v1 solo los informaba); el drift de `check` es `adapters/missing` con `:reason "drift"` (exit 1, como v1); no se portaron la heurística de renombres ni la proyección a kgdb; la guarda de before-image se porta en `adapters sync` (`:adapters/conflict`, exit 4). v1 ya no puede sincronizar sus propios símbolos: cinco pares getter/setter chocan en un mismo id; kimun lo informa como `adapters/collision`.
- Rutas HTTP: `/models/promote` y los borradores `.py.temp` se retiran (`POST /models/define {edn}`); `/lint` sirve los hallazgos de `stores check`; `/ast` es el documento con payload y secciones; `/models/detail` de un modelo desconocido es 404 (v1: 200 con `ok false`); las listas `unavailable` de `/schema` y `/graph` existen pero ningún estado del store las llena, porque un modelo es un nodo y no un módulo que pueda fallar al importarse. Claves que cambian de nombre: `id`→`name`, resultados bajo `data`.
- Índice y búsqueda: la búsqueda física también mira el texto del documento y el valor de los campos (v1: solo nombres y rutas); `--fuzzy` y el ranking por defecto usan Dice, no `difflib`; no se persisten vectores (`:score-fn` reemplaza al `Embedder`); una dirección que rompe la gramática es `query/bad-address` (exit 3), una bien formada que no nombra nada da vacío; `rebuild-indexes` con documentos que no extraen sale con 0 y `:complete false`.
- Aristas: `edges init|rebuild` son no-ops informados (el grafo se deriva de `(store, head)`); los 14 tipos builtin existen siempre; `edges check` y `predicates validate` salen con 3 (v1: 1); v1 inventaba secciones dentro del frontmatter YAML y kimun no (74 `has_section` y sus 421 `tagged_as` en la muestra real).
- Campos y secciones: `fields clean` no vacía una lista (en v1 es `--dedupe`/`--drop-empty`; vaciar es `fields clear`); `fields query` sin resultados es ok con lista vacía (v1 salía con 1); una ref de composición que sale de la raíz del proyecto resuelve a nada (v1 leía cualquier ruta); el modelo de un store enlazado se liga copiando su nodo descriptor al árbol `models` local, y los cambios posteriores del dueño no se propagan. Huecos conocidos del parseo de fechas YAML: colección en flujo de varias líneas y fecha sola en la línea siguiente a un `key:` vacío.
- Diagnósticos: `stores check` falla con `store/invalid` (exit 5; v1 salía con 1) cuando algo va más allá de `ok`/`reformatted`; el ruido ya rastreado se informa sin bloquear, el ruido que entra por una edición a mano sí bloquea; `stores update` nunca acepta una mutación de datos (v1 sí): eso es `docs update NAME`. `stores check` corre `store/verify` en cada llamada.
- Semántica: no hay `semantic_dag.yaml` ni rebuild; los padres implícitos por nombre con puntos se derivan al leer; las equivalencias valen en ambos sentidos; export v2 sin hashes de letras y con rutas relativas. Las fuentes de tags son solo `:semantics` del modelo sobre su linaje y las listas `tags`/`semantic_tags` del payload.
- Composiciones: la clave del descriptor es la de v1 (`source_field`, `model`, `template`, bajo `:py/compositions`), no `{:model :field :order}` de `07 §2`; una ref no resoluble renderiza vacío (v1 lanzaba `ImportError` para el modelo y caía al cwd para la ruta).
- Enlaces en CLI: un enlace sin resolver sale con 1 y el informe completo en el error, como afirman los tests de v1.
- Documentos: el payload se extrae del texto fuente guardado tal cual (signo `markdown/source`), no del render canónico del árbol, que escapa `_ * [ ]` y une saltos suaves; el árbol de bloques queda para estructura. `docs track` rechaza un archivo que no hace round-trip salvo `--force`; `docs delete` exige `--yes`; un archivo editado afuera es un warning de `stores check`; no hay outbox, el comando escribe el archivo tras el commit. La opción `{:doc}` no se agregó a `kimun.surface.markdown.plan`: `kimun.surface.docs` la pone en el AST.
- Modelos: redefinir el mismo contrato no es error (`:unchanged? true`, nada se escribe); la versión sube solo si el contrato cambia; un borrador es un `.edn` del usuario, no un archivo gestionado por el store; `extract` rechaza un `rev` vacío salvo `--allow-empty`. El template **no** se ingesta como documento `model/<Name>/template` (`07 §4`): duplicaría revisiones y aparecería en `docs list`. No hay template heredado: el exportador aplana la herencia.
- Motor: no usa el CST/AST de SLDB-MD; `kimun.surface.models.blocks` reimplementa la segmentación por bloques de markdown-it que usaba v1, porque la paridad byte a byte depende de sus tipos y rangos de línea. La receta es por bloque, no por `:path` como pide `07 §4`: **deuda de diseño** a saldar cuando el motor se monte sobre el árbol del kernel. Excluidos del corpus: 9 con `py•`/Jinja (`07 §8`) y 15 de `TaskDoc` de deskops, cuya clase omite un campo vacío con código Python propio. El descriptor admite `:exclude true` en un campo.
- Serve: todo cuerpo es el envelope (v1 enviaba el payload desnudo y `{ok:false,error:"…"}`); los status coinciden con v1. Ruta conocida con método equivocado es 405. CORS permite la cabecera `Authorization`. La variable es `KIMUN_SERVE_TOKEN`.
- Journal: no hay segundo almacén ni escritura en el commit; la cadena de hashes es el DAG de revisiones (`previous_hash` ⇒ `:parents`); no hay campo `operation`, sino el conteo de ops del plan; el filtro por actor es `--by`.
- Federación: `stores link NAME PATH` con ruta relativa al store; no existe catálogo global en el home (`~/.sldb` con rutas absolutas era el vicio nombrado en `06-superficies-y-cli.md`), así que `test_store_init_registers_project_in_global_catalog` y `test_get_store_context_readonly_falls_back_to_global` no se portan. Un destino ausente es un warning, no un fallo.
- Grafo: el orden es determinista por id donde v1 heredaba el de networkx (camino, orden topológico, ciclos); `paths-between` no repite un camino por cada relación paralela; `gt`/`lt` entre tipos no comparables dan falso.
- Enlaces: se informa `:ambiguous` cuando un mismo nivel de búsqueda da más de un candidato (v1 tomaba el primero). Los offsets son unidades de string de la plataforma.

