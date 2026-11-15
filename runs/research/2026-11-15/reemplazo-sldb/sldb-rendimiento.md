# Rendimiento de sldb hoy: lo que kimun tiene que igualar o mejorar (2026-11-15)

> Evidencia no autoritativa, como referencia para `RS1` (`docs/v2/12-ecosistema-y-pendientes.md §1`).
> Sale de leer el código de sldb en `/home/jp/proyectos/SLDB_1/sldb/src/sldb` (`641ecdfa`) y de
> dos perfiles cProfile ya guardados. No se corrió `stores build` ni `stores update`. Las rutas
> son relativas a `src/sldb`. **[I]** marca lo inferido.

## Medidas de partida

Store de sldb: 43 modelos, 3.092 documentos (unos 2.452 `PythonSymbolDoc` generados).

| Comando | Tiempo | Dónde |
|---|---|---|
| `sldb stores build --check` | 50 min, un núcleo, 1 GB | robocop2 |
| `sldb stores update`, en frío con cambios | 38 s | máquina local, 12 núcleos |
| `sldb stores update`, en caliente sin cambios | 2,4 s (7,0 s bajo cProfile) | máquina local |
| `sldb --help` | 0,7 s | máquina local |
| `deskops doctor` | 10–14 s | máquina local |

## Por qué `build --check` tarda 50 min

`update_store_indexes` corre una sola vez (`api/stores/build_store.py:55`). El costo cuadrático
está en que el build parte de un store vacío y trackea los documentos de a uno:

- `apply_includes` llama a `track_document_file` por cada fila nueva (`api/stores/apply_declaration.py:73-74`).
- Cada track reconstruye los índices semántico, de secciones y de aristas y recalcula el hash
  del store (`store/ops.py:40-50`). Cada pasada cuesta O(modelos) más O(documentos ya
  trackeados del modelo).
- Los cerca de 640 documentos escritos a mano además recomponen el grafo de reglas en cada
  entrada (`api/rules/rules_on_write.py:29-39`, `api/rules/rule_graph.py:94-112`).
- Cada track vuelve a parsear `store.yaml` (49 KB, YAML en Python puro) y a correr los globs de
  la declaración (`api/stores/declaration_sync.py:63-70`).
- El generador de símbolos reconstruye su registro después de cada escritura (`cli/selfdoc_write.py:37`).
- La copia temporal tiene una ruta nueva en cada corrida, y las cachés de usuario se indexan
  por `sha256(ruta)` (`store/user_cache.py:40-51`): siempre arrancan frías.

Para `PythonSymbolDoc` eso suma unas 3,0 M visitas por tipo de pasada. Que eso llegue a los
50 min es una estimación [I]: `--check` no se perfiló.

## Por qué `update` en caliente no es instantáneo

- `untrack_losers` siempre carga los documentos de los 43 modelos, aunque ninguna ruta esté
  nombrada por dos reglas (`api/stores/include_losers.py:20-27`). Es la primera composición de
  los 3.092 shards del proceso: 3,78 s de los 7,0 s del perfil.
- `_process_models` lee el texto completo y calcula SHA-256 de los 3.092 archivos antes de
  decidir si saltarse la extracción (`api/stores/index_refresh.py:22-98`). No mira antes mtime
  ni tamaño, aunque `store/edge_index/live_stale.py` ya tiene ese atajo para las aristas.
- El caché de entradas de documentos vive solo en memoria: cada proceso del CLI lo recompone.

## Otros costos fijos

- **Arranque:** `import sldb.cli.main` carga 246 módulos; con `cli.commands.stores` son 844
  módulos y 166 clases pydantic. `networkx` entra por `store/graph/analysis/__init__.py`, y
  `sldb/api/__init__.py` tiene 110 imports ansiosos.
- **YAML en Python puro** en rutas calientes: `core/handlers/yaml.py:70`,
  `models/provenance_block.py:27,55`, `api/stores/store_declaration.py:75,95`, aunque
  `store/io/utils.py` ya define el cargador en C.
- **`fsync` por escritura** (`store/io/utils.py:59`): un build hace al menos 3.092 × 4.
- **Store derivado versionado:** `.sldb/core/` (3.092 shards, 13 MB) está en git; hubo 169
  commits que lo tocaron desde el 1 de noviembre. No hay hooks, daemon ni merge driver.

## Lo que esto le pide a kimun

Para que el reemplazo sea una mejora y no un cambio lateral, `RS1` tiene que medir en kimun,
sobre el mismo store, al menos: reconstrucción completa, actualización sin cambios,
actualización con un documento cambiado y arranque del CLI.
