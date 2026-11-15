# Mapa de frentes de kimun hacia «producto funcional» (2026-11-15)

Alcance: los frentes de `docs/v2/12-ecosistema-y-pendientes.md §1` que NO son K1e/S2b, M1/M2 ni K5.
Solo lectura. No corrí `bb` ni pruebas. Convención: **[doc]** = lo dice un documento (cito);
**[visto]** = lo comprobé leyendo código o prueba; **[infiero]** = deducción mía, sin comprobar.

Confirmación sobre G: nada de lo que sigue depende de D-G ni de G1–G3/K7, con una salvedad: si los
embeddings por unidad se quedan colgados de `G3` (`12 §1`, fila G3: depende de G1) heredan D-G; ver
pregunta Q7.

---

## 1. Qué significa «producto funcional» según los documentos

No existe una definición única. Lo que hay:

- `docs/faq.md` («Out of the current slice»): solo las superficies base S/M están en el corte actual;
  la capa G contextual (`C01–C28`) queda fuera y «Visualization as a whole is not out of scope: a
  minimal view of the circuit and signals remains required under `Operación y vista`».
- `docs/v2/12 §1`, fila «Operación y vista» (l. 42), evidencia de cierre: «suite, oráculo, include
  check, fallos inyectados, bundle limpio y recorrido visible del cambio a la respuesta». Es el único
  criterio de cierre operativo escrito. Avance declarado: T-V1 (`scripts/tour_mutation.sh`); «la vista
  del circuito más allá de eso sigue pendiente».
- `AGENTS.md` («Validation», «Commits»): una tarea se cierra con `bb lint`, `bb test`, `bb oracle`,
  evidencia en `runs/subagents/` y un commit. Eso cierra tareas, no el producto.
- Invariantes de `12 §1` («Qué bloquea qué»): lint y test verdes, paridad v1 sin brechas, `include
  check` limpio, `bb oracle` solo cambia dentro de K1.
- `README.md`: «At the product horizon, kimun targets a contextual knowledge superset…» (incluye G),
  mientras `faq.md` dice que G está fuera del corte. No es contradicción dura (horizonte vs corte), pero
  nadie dice cuál de los dos es «el producto funcional».

Huecos y contradicciones, con cita:

1. No hay criterio de alcance operativo (plataformas, escala, SLO, retención, soporte). Solo aparece como
   `OP-1`/`PROD-0` «[DECISIÓN DUEÑO]» en `runs/research/2026-11-14/concilio/backlog-agy.md:101` y
   `PROD-0` en `backlog-pi.md` (ambos no autoritativos; `docs/v2/README.md` pone `12` y `runs/` por debajo
   de todo). Pregunta para el dueño (Q1).
2. `docs/v2/08 §5` declara 5 plataformas y dice «la que prueba CI» solo para linux-amd64; no dice si las
   otras cuatro son requisito del producto.
3. `docs/v2/05 §2` (marcador de paridad «1 corpus y 1 abierta», 2026-10-08) contradice a
   `docs/v2/tests/parity-v1.md` (l. 14–25: `520/1/14/55/0/0/0/0`, cerrado el 2026-11-14) y a `12 §1`
   (paridad «sin brechas»). El vigente es parity-v1 **[doc]**; 05 §2 está desactualizado.
4. `python/README.md` («Client library») dice que el snippet `from kimun import Client` está en
   `06 §6`; **`06 §6` no tiene ese snippet** (solo dice que el cliente no existe, l. 567) y ningún
   documento de `docs/` ni de `runs/research/2026-10-08` define `Client` (grep vacío). Referencia colgante.
5. `12 §1` C2 nombra `/models/promote` y `/kgdb/snapshot`; `docs/v2/tests/parity-v1.md:75` dice que
   `/models/promote` «se retira (`POST /models/define {edn}`)» y `06 §5` retira «la materialización de
   kgdb»; `docs/archive/v2/tests/parity-serve-checklist.md:1` aún los lista como «pendientes reales».
6. `docs/v2/08 §5` (ítem de `bb release`) dice que el pod sale de `target/pods/<plataforma>/` «(staging de
   CI)», pero **ni `ci.yml` ni `release.yml` construyen o stagean el pod** [visto: no hay `cargo` ni
   `target/pods` en `.github/workflows/*`]; `release.edn` tiene `:sha256 nil` para los 5 pods.

---

## 2. Estado por frente

Etiqueta de especificación: **ESP** (especificado), **PARC** (parcial), **SIN** (sin especificar: no se
puede implementar fielmente sin decisión).

### 2.1 Operación y vista

| ítem | estado real [visto] | falta | spec |
|---|---|---|---|
| C2, rutas HTTP | `src/kimun/surface/http/routes.cljc` tiene 46 entradas de ruta; no existen `/kgdb/snapshot` ni `/models/promote`; `test/kimun/surface/http/models_fields_test.cljc:57` lo declara («/models/promote has no counterpart») | decidir si se reponen (ver §1.5) | **SIN** (contradicción, Q2) |
| C2, cliente Python | `python/kimun/` solo tiene `models.py` (exportador) y `adapters/`; `python/README.md`: «There is no Python client library yet» | `client.py`, `errors.py`, wheels, `_launcher.py` | **PARC**: `08 §7` lista artefactos y la resolución `$KIMUN_BIN > _bin/<plat>/ > PATH`; **no** hay API del `Client` (§1.4) |
| seguridad | `serve`: token Bearer con comparación en tiempo constante (`kimun.surface.http`, `constant-time=`), bind por defecto `127.0.0.1`, CORS opt-in (`06 §3 serve`). No encontré límite de tamaño de cuerpo ni timeouts en `http.cljc`/`routes.cljc`; `POST /docs/track|create` aceptan `path` del JSON (`routes.cljc` `track`, `create`) y no hay prueba negativa de rutas fuera del proyecto en `test/kimun/surface/http_test.cljc` | modelo de amenazas, controles, pruebas negativas | **SIN**: no hay threat model en `docs/v2/`; solo la propuesta `OP-5` del concilio (no autoritativa) |
| CI/release reproducible | `ci.yml`: lint, test, oracle, jar, bundle local linux-amd64. `release.yml`: bundles de 5 plataformas con bb pinado y sha256. `test/kimun/cli/release_test.cljc`: 2 pruebas (bundle corre sin bb en PATH; rechaza plataforma/versión desajustada). `scripts/release.clj` arma el tar con `tar -czf` sin orden, mtime ni dueño fijados [visto, l. ~165] → dos builds no darán el mismo SHA256SUMS [infiero; no lo corrí]. Pod: no se construye en CI (§1.6) | determinismo de archivos y jar; pod en CI (cargo, 5 targets) y su sha256 pinado; qué se prueba por plataforma | **PARC**: `08 §5–6` ya fija bb/pod/sha256 y gate humano; «reproducible» solo aparece en `12 §1` y en `OP-7` (no autoritativo) |
| piloto | no existe | informe sobre copia del desk/corpus | **SIN**: sin criterio de éxito (Q1) |
| vista mínima del circuito | `scripts/tour_mutation.sh` + `test/kimun/cli/tour_test.cljc` (`the-tour-of-one-mutation`) a nivel documento; `stores sense|health|heartbeat` por CLI. `serve` **no** expone sense ni health (rutas `/stores`, `/stores/check`, `/stores/links` solamente) | vista más allá del documento; forma de la vista | **SIN** (forma) / **PARC** (exponer sense por HTTP: la regla de `06 §4` «cada ruta delega en un comando» alcanza) |
| fallos inyectados (parte de la evidencia de `12 §1`) | solo la guarda `before-image` de `adapters sync` (`C1/F25`, cerrada). `docs/v2/tests/parity-v1.md` («Documentos»): «no hay outbox, el comando escribe el archivo tras el commit». No hay prueba de fallo entre commit y escritura para `docs update`/`fields set` | caracterizar y probar | **PARC**: `10 §11` («Ningún cambio silencioso») y la lista de evidencia de `12`; no hay protocolo F25 general |

### 2.2 Sensor y store

| ítem | estado real [visto] | falta | spec |
|---|---|---|---|
| K2a, fan-out dentro de un directorio | `src/kimun/kernel/route_tree.cljc`: un nodo por directorio, cada nodo del camino se lee, rehashea y reescribe entero (docstring y `10 §11.2 «Límites»`); prueba `a-wide-directory-is-rewritten-whole` (`route_tree_test`) | otra forma de nodo = otro esquema de rutas | **SIN**: `10 §11.2`: «Si K2a lo exige, es un pendiente»; no hay umbral ni forma |
| K2a, medición en store real | `scripts/bench/{gen_store,run_bench,scale_point}`; `~/.cache/kimun-bench/{k4-600,k4b-copy,real-f2}` (sintéticos: `real-f2` son `note-NNNNN.md`) [visto]. El store «real» de 1.551 docs (`05 §2`) no sé si existe hoy | medir bytes escritos/lectura por escritura de ruta, plano vs anidado | **ESP** como medición (`14 §20`, `10 §11.2`); qué store es «real» no está dicho |
| K2b | `kimun.kernel.mmap` + `store.cljc` (`heads-root`: mmap tree-id→rev-id) siguen siendo el mecanismo de cabeceras; 708 KB + 489 KB de aristas según la medición histórica de `14 §20` | retirar mmap por ids internos y 3 aristas/doc | **PARC**: dice qué retirar (`14 §20`), no con qué reemplazar las cabeceras por árbol ni la migración; condición «cuando los IDs de nodo estén cerrados» ambigua: ¿todo K1 o basta K1a–c? Bloqueada por K1 (`12 §1`) |
| latido: integración asíncrona | `kimun.surface.heartbeat` + `kimun.cli.commands.stores/heartbeat`; `test/kimun/cli/heartbeat_test.cljc` (7 pruebas: NDJSON, señal de mutación, sin escrituras, error transitorio, fatal exit 70, SIGINT/SIGTERM). Contrato (`06 §3 «El latido»`): estado completo por latido, `changed` solo en memoria, «No hay activación por eventos» | cursores/épocas, causalidad, consumidor | **SIN**: `14` cabecera l. 28–29 «los contratos operativos del heartbeat y de activación siguen por especificar»; `ambiguedades.md` A3.1/A3.2 abiertas |
| latido: coste incremental | cada latido abre el store y corre `stores check` con verificación de hashes: «su costo crece con el store» (`06 §3`). `route_tree/diff` existe pero el latido no lo usa [visto] | descenso por ramas | **SIN**: ver Q4 |
| cobertura topológica | `kimun.surface.signals/category->type`: 11 categorías de diagnóstico; nada de unidades. `kimun.surface.diagnostics` y `signals` no mencionan `unit` [visto]; los estados `anchored|missing` de K1c salen solo en `docs show` (`commands/docs.cljc` l. ~499, `tdocs/unit-states`) | señal de unidad ausente en `sense`/`heartbeat` | **PARC**: `14 §21` regla 3 («el sensor informa `missing`»); no define tipo de señal ni severidad |
| origen de señales | solo aparece como nombre en la fila «Sensor y store» de `12 §1`; no hay definición en ningún documento (grep) | — | **SIN** (¿qué es?: ver Q5) |

### 2.3 Ejecuciones marcadas

Estado: nada implementado (no hay `eval`/`derive`, `06 §6`). Spec: **SIN**. `14` cabecera: «El contrato
preciso de ejecuciones queda por especificar»; `d0-contrato-sintesis.md §7`: «Diseño pendiente aparte».
Opciones abiertas en `ambiguedades.md` A1.2 (recibo pasivo / predicado reproducible / posponer). Depende
del canal asíncrono. No hay corte despachable.

### 2.4 Relaciones y continuo

Estado: hay 14 tipos de arista builtin y `predicates` (árbol `relations`), `graph similar` con Dice y
`:score-fn` («no se persisten vectores», `parity-v1.md` «Índice y búsqueda»). No existe taxonomía de
relaciones jerárquicas/horizontales/métricas/recursivas, ni consulta por unidad, ni embeddings [visto:
`src/kimun/graph/*` no los contiene]. Spec: **SIN**. `14` cabecera l. 28 dice que «representación de
identidad, relaciones y embeddings… siguen por especificar»; `ambiguedades.md` A4.1–A4.3 abiertas; el
contrato `D0` derivado no cubre relaciones. Además depende de K1 (unidades bajo el documento). Sin corte
despachable.

### 2.5 Deuda declarada

| ítem | estado | spec |
|---|---|---|
| consumidores de caché fuera de `doc-tree` | `runs/subagents/2026-11-15-cache-verificable/EVIDENCE.md` («Limits») lista `kimun.surface.edges` (`relations-tree`, `entries`), `docs show --ast` (`plan/store->ast`), `edges-of`/`target`/`hidden-trees` de `surface.docs` y `kimun.surface.models`, y dice «on a missing object they fail instead of rebuilding… The worker reports they read persisted taxonomies today; not verified». Yo vi que `relations-tree` carga un árbol de taxonomía persistido (`edges.cljc:86`), así que puede no afectar a cachés [infiero]. `docs show --ast` primero llama `tdocs/document-ast` y recién después `md/store->ast` (`commands/docs.cljc:490–496`) | **PARC**: `10 §11` define a `doc-tree` como el resolver y `00-nucleo 6.3` («Lo reconstruible puede descartarse»); falta saber qué comandos realmente fallan con `cache/` borrada |
| `docs show` y documentos genéricos | `06 §3 «Documentos sin modelo»`: `docs show NAME` (sin `--ast`), `fields`, `sections`, `find` «siguen sin verlo»; con `--ast` sí. Es una limitación descrita, no un requisito | **PARC**: no dice qué debe devolver `docs show` de un genérico |
| paridad v1 | `parity-v1.md`: 520/1/14/55/0/0/0/0; invariante vigente. Nada que implementar salvo mantenerlo y corregir `05 §2` | **ESP** (documental) |
| Deuda de diseño y experimentos (`12 §1` final) | parser: fase 3 = S2 (S2a sin identidad; S2b ya cubierto por otros); composiciones del descriptor `:py/compositions` vs `{:model :field :order}` (`07 §2`): decisión pendiente de M1; concesiones `:symbol`/`:fact`: G; fuera del perfil Markdown (`_`, tablas, footnotes, imágenes): perfil cerrado por decisión 7; tree-sitter para código: M1; board legado: no editable | S2a **PARC** (una línea en `12`/`14 §13`: «localizador + huella por nodo estructural, recomputable, sin cambio de formato»; R2 §5 fase 3 habla de rangos de bytes e ids de nodo del CST); el resto depende de M1/G |

---

## 3. Cortes despachables ya

Reglas comunes: `/home/jp/.cache/coord-briefs/kimun-reglas-ejecutor.md`. Todos «pruebas discriminantes»
significan que fallan sin el cambio (regla 5). Un solo `bb` a la vez en la máquina: aunque dos cortes sean
paralelos por archivos, sus corridas se serializan por `flock`.

### V1 · HTTP del circuito (`/stores/sense`, `/stores/health`)
- Objetivo: exponer por `serve` las dos observaciones read-only que hoy solo existen por CLI, con el mismo
  `:data` (regla `06 §4`). Es la mitad de la «vista mínima» que no exige decidir una UI.
- Archivos: `src/kimun/surface/http/routes.cljc` (dos entradas `command-route ["stores" "sense"|"health"]`),
  `test/kimun/surface/http_test.cljc` (o archivo nuevo en `test/kimun/surface/http/`), `docs/v2/06 §4`.
- Pruebas: (1) `GET /stores/sense` sobre un store con una fuente editada devuelve 200 y una señal
  `source/mutated`; (2) el cuerpo `:data` es igual al de `stores sense` por CLI salvo `:mode` y
  `:observed-at`; (3) el árbol del store tiene el mismo sha antes y después (no escribe, como en
  `tour_mutation.sh`); (4) con `--token`, sin Authorization da 401 y con él 200; (5) `POST` da 405;
  (6) `GET /stores/health` de un store con contrato cambiado da `degraded` y `:next`.
- Choca con: V4 y S1 (mismo `routes.cljc`; conflicto de merge trivial, hacer en serie).
- Ambigüedad a reportar: si la ruta debe llamarse `/stores/sense` o `/sense` (no hay convención escrita
  para rutas nuevas más allá de `/stores/*`).

### C1 · Caché fría en todos los lectores («borrable» es una promesa)
- Objetivo: demostrar y arreglar que ningún comando lector depende de que `cache/` exista, o que falla
  de forma declarada. Promesa: `00-nucleo 6.3`, `10 §11` («borrable: todo se recalcula»).
- Fase 1 (obligatoria, sin tocar `src/`): matriz de comandos (`docs show|render|list|show --ast`,
  `fields get`, `sections list`, `find`, `edges …`, `graph …`, `models show|check`, `stores check|verify|
  health|sense`) sobre un store formato 4 con y sin `rm -rf cache/`, un documento tipado y uno genérico.
- Fase 2: arreglar solo lo que la matriz muestre roto, pasando por `kimun.surface.docs/doc-tree`.
- Archivos probables: `src/kimun/surface/edges.cljc`, `src/kimun/surface/docs.cljc` (`edges-of`, `target`,
  `hidden-trees`), `src/kimun/surface/markdown/plan.cljc` (`store->ast`), `src/kimun/surface/models/*`;
  pruebas nuevas `test/kimun/cli/cold_cache_test.cljc`.
- Pruebas: (1) por cada comando de la matriz, salida con caché vacía == salida con caché llena; (2) con
  un objeto de caché dañado (no hashea a su id) el lector no devuelve bytes dañados y no escribe en
  `cache/` (contrato de C1 de `10 §11`); (3) `docs show --ast` de un documento sin árbol en caché y con
  archivo presente; (4) lo mismo con el archivo ausente: error declarado, no excepción; (5) un `edges`
  sobre documentos con campos de referencia con caché fría.
- Choca con: cualquier corte de K1e/M1 (tocan `surface/docs.cljc` y `surface/models/*`), y con U1 y D1
  (`docs.cljc`). Hacer **antes** de U1/D1 y avisar a los dueños de K1e/M1.
- Ambigüedad a reportar: qué debe pasar con caché fría y archivo ausente (spec: `10 §11` solo cubre «el
  caché no está»).

### R1 · Archivos de release deterministas
- Objetivo: que dos corridas de `bb release --local-bb --platforms linux-amd64` en la misma máquina den
  el mismo `SHA256SUMS`, y que `release_test` lo compruebe. Es la parte de «CI/release reproducible» que
  se puede verificar sin red ni runners.
- Archivos: `scripts/release.clj` (tar/zip con orden, mtime, uid/gid y `gzip -n` fijados; mtime = el de
  `SOURCE_DATE_EPOCH` o un valor fijo), `test/kimun/cli/release_test.cljc`; el jar (`bb jar`, `bb.edn`) si
  resulta no determinista (se mide primero).
- Pruebas: (1) dos builds consecutivos con `--out` distintos → mismos sha256 de tar.gz y `SHA256SUMS`;
  (2) mismo resultado tocando mtimes de los archivos de entrada; (3) el archivo lista las entradas en
  orden lexicográfico y con uid/gid 0; (4) el bundle sigue corriendo sin bb en PATH (prueba existente);
  (5) un jar construido dos veces es idéntico o la prueba documenta qué byte difiere.
- Choca con: nadie en `src/`; con S1 solo si ambos tocan `release.edn`.
- Ambigüedad a reportar: el sha256 de bb (descargado) y del pod entran al bundle; reproducible aquí = mismo
  input binario. Qué es «reproducible entre plataformas» no está definido.

### S1 · Auditoría de seguridad de `serve` (solo evidencia)
- Objetivo: convertir «seguridad» en una tabla de vectores con resultado medido, sin cambiar `src/`.
  Entrega: sondas reproducibles y una prueba negativa por cada comportamiento que **ya** está especificado
  (`06 §3 serve`: token, Bearer, 405 por método, CORS permite `Authorization`, bind por defecto 127.0.0.1).
- Vectores a sondar (los no especificados solo se miden y se reportan): cuerpo enorme, `path` absoluto o
  `..` en `/docs/track`, `/docs/create`, `/save`; `--host 0.0.0.0` sin token; token vacío; cabecera
  Authorization mal formada; timing del token; CORS sin `--cors`; symlink fuera del proyecto en `docs
  ingest`.
- Archivos: `test/kimun/surface/http/security_test.cljc` (nuevo, solo lo especificado) y
  `runs/subagents/2026-11-15-S1/` con la tabla.
- Pruebas: (1) sin token y servidor con token → 401 sin tocar el store; (2) token correcto en
  `Authorization: Bearer` pasa, con esquema distinto no; (3) método equivocado → 405; (4) preflight CORS
  solo con `--cors`; (5) el cuerpo del 401 no revela el token ni la ruta del store.
- Choca con: V1 (`routes.cljc`, no si solo agrega pruebas). Sin choque de `src/`.
- Ambigüedad: todo vector no especificado se reporta, no se arregla (Q8).

### M1 · Medición de K2a y del coste del latido (sin cambiar `src/`)
- Objetivo: poner números a lo que `10 §11.2` admite sin medir: bytes y nodos escritos por registro de
  una ruta, y coste de un latido, según N de rutas y forma (plano vs anidado), esquema 1 vs 2. Sirve para
  decidir si el fan-out (§4, B1) es necesario.
- Archivos: ninguno de `src/`; usa `scripts/bench/gen_store.clj` y `run_bench.sh`; salida en
  `runs/subagents/2026-11-15-M1/`.
- Pruebas/entregables: (1) tabla N∈{500,2000,10000} × {plano, 20 dirs, anidado} × {esquema 1, 2}: bytes
  del nodo raíz y de cada nodo del camino, tiempo de `docs update` de un documento; (2) tiempo de un
  latido (`stores sense --once`) con store frío/caliente a las mismas N; (3) crecimiento verificado (¿lineal
  en N?) con regresión simple; (4) todo sobre copias en `/tmp`, nunca el store real.
- Choca con: nadie. Cuidado con memoria (usar `memcap`, regla 2).
- Ambigüedad: «store real» (§2.2); usar un store sintético con la distribución de rutas del desk y decirlo.

### U1 · Señal de unidad ausente en `sense`/`heartbeat`
- Objetivo: que las unidades de campo `missing` (K1c) lleguen al sensor, con la misma forma de señal que el
  resto (`14 §21` regla 3). Hoy solo las ve `docs show`.
- Archivos: `src/kimun/surface/diagnostics.cljc`, `src/kimun/surface/signals.cljc` (`category->type`),
  uso de `tdocs/unit-states`, `docs/v2/06 §3` (`stores sense`).
- Pruebas: (1) campo quitado del archivo → señal de unidad `missing` con `:field` e `:id`; (2) la misma
  unidad no pasa a otra id si el campo vuelve (id estable, K1c); (3) `sense` no escribe (sha del store);
  (4) documento sin `:units` (formato <4/inline) → sin señal, sin error; (5) latidos repetidos dan la misma
  señal; (6) `stores health` sigue `ok` si no hay mutación.
- Choca con: C1 y D1 (`surface/docs.cljc`) y con K1e. Serializar tras C1.
- Ambigüedad a reportar: tipo, severidad y si una unidad `missing` hace `needs-human` o solo `info`.

### D1 · `docs show` de un documento genérico
- Objetivo: que `docs show NAME` resuelva un `generic/md` y devuelva su `:markdown`, `:class` y `:units`
  vacío, sin cambiar `fields`/`sections`/`find`.
- Archivos: `src/kimun/cli/commands/docs.cljc` (`show`), `src/kimun/surface/docs.cljc`; prueba en
  `test/kimun/cli/generic_docs_test.cljc`; `docs/v2/06 §3` («siguen sin verlo»).
- Pruebas: (1) `docs show README` de un genérico → ok, `:markdown` igual al archivo; (2) un alias compartido
  tipado/genérico sigue siendo `docs/ambiguous`; (3) `docs show` de un tipado queda byte a byte igual;
  (4) archivo ausente → error declarado (no excepción); (5) `--ast` no cambia.
- Choca con: C1, U1 (`docs.cljc`), K1e. Serializar tras C1.
- Ambigüedad a reportar: la forma exacta del `:data` de un genérico (`06` no la fija).

### F1 · Caracterización de fallos entre commit y escritura del archivo
- Objetivo: probar que si `docs update`/`fields set` falla entre commit y archivo, el estado resultante lo
  nombra `stores health` y lo repara `stores update` sin pérdida silenciosa (`10 §11`). Cierra una pata de
  «fallos inyectados».
- Archivos: solo tests (`test/kimun/cli/fault_injection_test.cljc`), con inyección por `with-redefs` del
  escritor de archivos; `src/` solo si la prueba revela pérdida silenciosa (y entonces se reporta antes).
- Pruebas: (1) fallo de escritura tras commit → `stores health` `needs-human` o `degraded` con acción;
  (2) la revisión de store existe y el archivo conserva su contenido anterior; (3) `docs update` repetido
  converge; (4) disco lleno simulado en `cache/` no corrompe el store; (5) ningún `*.tmp` queda sin
  limpiar en `objects/`.
- Choca con: nadie en archivos si se queda en tests.
- Ambigüedad: no hay protocolo F25 general; el corte describe lo que hay, no inventa uno.

### Paralelismo (por archivos)

- Sin overlap, **pueden ir a la vez**: R1, S1, M1, F1 (+ V1 si S1 solo añade tests).
- V1 y S1 comparten `routes.cljc`/`http_test.cljc` solo si S1 toca `src/`; como S1 es audit, ok.
- En **serie**, mismo worktree: C1 → U1 → D1 (todos tocan `surface/docs.cljc`; el orden es del más corto
  al más largo según tu regla: D1 es el más corto, pero C1 es el que descubre qué falla; recomiendo C1
  primero porque U1 y D1 se apoyan en un `doc-tree` ya sano). Los tres chocan con K1e/M1 (otros).
- Estimación (inferida): V1 ~1 h, R1 ~2 h, S1 ~2 h, M1 ~2 h (corridas pesadas), F1 ~2 h, D1 ~1,5 h,
  U1 ~2,5 h, C1 ~3 h.

---

## 4. Cortes bloqueados

| corte | motivo exacto |
|---|---|
| B1 fan-out en K2a | **Falta decisión**: `10 §11.2` no fija si K2a lo exige ni qué forma de nodo (sería otro esquema de rutas con paso explícito). Primero M1 (medición). |
| K2b | **Dependencia + spec parcial**: espera K1 (`12 §1`); no dice con qué reemplazar el mmap de cabeceras ni la migración; ambiguo si basta K1a–c (Q6). |
| C2 rutas HTTP | **Contradicción documental** (§1.5, Q2). |
| C2 cliente Python | **Sin spec de API** (§1.4); `08 §7` da artefactos, no `Client`. Depende de la respuesta de Q2 y de wheels por plataforma (Q1). |
| Seguridad (arreglos) | **Sin threat model**; S1 mide, el dueño decide (Q8). |
| CI: pod en CI, 5 targets, sha256 del pod | **No verificable offline** y depende del alcance de plataformas (Q1); requiere runners y revisión humana del workflow (`08 §6`). |
| Vista del circuito más allá del documento | **Sin spec de forma** (Q3) y, por debajo del documento, espera K1e/S2b (otros). |
| Piloto | **Sin criterio de éxito** (Q1) y depende de lo anterior. |
| Latido: coste incremental | **Pregunta de diseño** sobre cómo se detecta el cambio del ambiente (Q4). M1 da la base. |
| Latido: cursores/épocas/causalidad | **Sin spec** (`14` cabecera) y **sin productor de acciones internas** (no existen S4/S5, `06 §6`): no hay bucle que amortiguar (Q5/Q6). |
| Origen de señales | **Término sin definición** (Q5). |
| Ejecuciones marcadas | **Sin spec** (A1.2 abierta; Q5). |
| Relaciones tipadas, consulta por unidad, embeddings | **Sin spec** (A4.1–A4.3 abiertas) y dependen de K1 en unidades bajo el documento; embeddings además en `G3` ← D-G (Q7). |
| S2a (anclas CST como evidencia) | PARC: una línea de spec; no definida la forma del localizador/huella. Despachable solo si el dueño del parser acepta redactar primero un mini-spec (está en el frente «Parser»; no lo incluyo como corte). |

---

## 5. Preguntas para el dueño (solo ambigüedades reales)

**Q1. ¿Qué es «producto funcional»: alcance y límites?** Cita: `faq.md` (corte S/M, G fuera) vs `README.md`
(horizonte con G) vs `12 §1` fila «Operación y vista» (evidencia) y ausencia de `PROD-0`. Opciones:
(a) base S/M + CLI + `serve` + bundle linux-amd64 + tour; (b) (a) + 5 plataformas + cliente Python;
(c) (b) + G. Mi lectura: (a), con 5 plataformas como siguiente hito. Cambia: qué cortes de operación son
obligatorios y si hay piloto.

**Q2. C2: ¿se reponen `/models/promote` y `/kgdb/snapshot` y se construye el cliente Python, o se retira C2?**
Citas: `12 §1` C2; `parity-v1.md:75` (promote retirado); `06 §5` (kgdb retirado) y §6 (cliente «no existe»);
`05` decisión 18 (consumidores siguen en v1). Opciones: (a) retirar C2 de `12` y marcar graph_ui como
consumidor v1; (b) alias HTTP: `/models/promote`→`POST /models/define`, `/kgdb/snapshot`→`graph snapshot`
solo si un consumidor real lo pide; (c) C2 completo con API del `Client` especificada. Mi lectura: (a) hoy;
no hay consumidor en código (`12 §2`: «kimun no tiene consumidores en código»).

**Q3. ¿Qué forma tiene la «vista mínima del circuito» y a qué nivel?** Citas: `faq.md` (requerida),
`12 §1` (T-V1 no la cierra). Opciones: (a) JSON por HTTP (`V1`) + el tour; (b) página estática que lee
`/stores/sense`; (c) vista por unidad (espera K1e). Mi lectura: (a) cierra el requisito para el nivel
documento; (b) solo si el dueño quiere algo que mirar. Cambia si se construye UI.

**Q4. Latido incremental: ¿cómo se detecta qué rama del ambiente cambió sin leer los archivos?** Cita:
`14 §20` punto 2 («bajar solo por la rama que cambió») y `06 §3` («costo crece con el store»). El árbol de
rutas (`route_tree`) guarda lo **registrado**; un cambio externo de un archivo no lo altera, así que hay
que mirar el ambiente [infiero]. Opciones: (a) hash de todos los archivos trackeados en cada latido (hoy);
(b) pista por mtime/tamaño y hash solo si cambia (puede perder ediciones que conserven mtime); (c) watcher
del SO (fuera de Babashka puro). Mi lectura: (b) con verificación completa cada N latidos, declarado en el
contrato. Cambia el diseño y qué garantía de «no pérdida silenciosa» se promete.

**Q5. ¿Se especifica ya la activación asíncrona, el «origen de señales» y las ejecuciones marcadas, o se
posponen hasta que exista un productor de acciones (S4/S5)?** Citas: `14` cabecera («siguen por
especificar»), `06 §3` («No hay activación por eventos»), `ambiguedades.md` A1.2, A3.1, A3.2; «origen de
señales» (`12 §1`, sin definición). Opciones A3.1: (1) estado completo idempotente [ya es lo implementado],
(2) deltas, (3) estado + época/cursor. Mi lectura: mantener (1), definir «origen de señales» como el campo
que dice por qué se emitió (fuente, contrato, recálculo) y posponer causalidad y ejecuciones hasta que
haya acciones internas.

**Q6. K2b: ¿espera a todo K1 (unidades de bloque/sección) o basta K1a–c para retirar mmap y aristas?** Cita:
`14 §20` («cuando los IDs de nodo estén cerrados») y `12 §1` fila K2b («espera a K1»). Mi lectura: el mmap
de cabeceras indexa árboles por ULID de árbol (ya acuñado en K1a), no unidades; podría retirarse sin K1e,
pero falta el diseño del reemplazo, así que no es despachable aún.

**Q7. Embeddings por unidad: ¿S–M (cabecera de `14`) o `G3` (`12 §1`, depende de G1/D-G)?** Citas:
`14` cabecera l. 24–25 vs `12 §1` fila G3. Mi lectura: S–M con modelo y vigencia identificables, sin
inferir relaciones de la similitud; si se queda en `G3` queda tras D-G.

**Q8. `serve`: ¿debe confinar los `path` de las rutas de escritura al proyecto y negarse a bindear fuera de
loopback sin token?** Citas: `06 §3 serve` (defaults), `06 §3 «Documentos sin modelo»` (permite archivos
fuera del proyecto en CLI), `routes.cljc` `track`/`create`. Mi lectura: sí a ambos por HTTP, manteniendo la
CLI como está. Cambia el comportamiento de rutas de escritura.

---

## 6. Los tres cortes que más acercan a «producto funcional» (briefs)

Reglas: `/home/jp/.cache/coord-briefs/kimun-reglas-ejecutor.md` (léelas primero). Tu TAREA id va en
`runs/subagents/2026-11-15-<TAREA>/`.

### Brief 1 · TAREA = `cold-cache` (corte C1)
Contexto: `00-nucleo 6.3` y `10 §11` prometen que `cache/` se puede borrar. `runs/subagents/2026-11-15-
cache-verificable/EVIDENCE.md` («Limits») lista lectores que no pasan por `kimun.surface.docs/doc-tree` y
dice que no verificó si fallan. Haz primero la matriz de comandos con y sin `cache/` (documento tipado y
genérico; documento sano y con un objeto de caché dañado) y entrega su tabla; solo después arregla lo que
la matriz muestre roto, siempre pasando por `doc-tree`, sin escribir en `cache/` desde un lector. Pruebas:
las cinco de §3 C1; cada una debe fallar sin tu cambio. Si la matriz sale limpia, entrega solo la tabla y
la prueba de regresión, y dilo. No toques `kimun.kernel.*`. Reporta: qué comandos fallaban, qué debe
hacerse con caché fría y archivo ausente (spec silencioso), y colisiones con K1e/M1 en `surface/docs.cljc`
y `surface/models/*`.

### Brief 2 · TAREA = `http-sense` (corte V1)
Contexto: `docs/v2/06 §4` («cada ruta delega en un comando»), `06 §3` (`stores sense`, `stores health`).
Agrega `GET /stores/sense` y `GET /stores/health` a `src/kimun/surface/http/routes.cljc` con
`command-route`, sin handler nuevo. No añadas `heartbeat` (es flujo). Actualiza la lista de rutas de
`06 §4`. Pruebas: las seis de §3 V1. Mide también que el árbol del store no cambia tras las llamadas (usa
el mismo criterio de sha que `scripts/tour_mutation.sh`). Reporta si `command-route` filtra algún campo
distinto al CLI y la ambigüedad del nombre de ruta.

### Brief 3 · TAREA = `release-determinism` (corte R1)
Contexto: `docs/v2/08 §3–§6`, `scripts/release.clj`, `test/kimun/cli/release_test.cljc`, `release.edn`.
Objetivo: dos `bb release --local-bb --platforms linux-amd64` en la misma máquina dan el mismo
`SHA256SUMS`. Mide primero qué difiere (tar, gzip, jar) y entrega esa tabla; luego fija orden, mtime
(`SOURCE_DATE_EPOCH` o constante), uid/gid y `gzip -n`. Pruebas: las cinco de §3 R1. No toques
`.github/workflows/*` ni `release.edn` más allá de lo que tu prueba exija; reporta aparte que los
workflows no construyen el pod (`08 §5` dice «staging de CI») y que `:sha256 nil` en los 5 pods. No hay
red: usa `--local-bb`.

---

## 7. Resumen de riesgos de este mapa

- Verifiqué por lectura, no por ejecución: ninguna afirmación de «funciona» o «falla» viene de correr
  algo; las marcadas [infiero] necesitan la fase de medición/verificación de sus cortes (C1, R1, M1).
- Mis estimaciones de tiempo son orientativas, sin evidencia.
- La tabla de 12 §1 mezcla estado histórico (2026-10-09) y notas de 2026-11-14/15; donde chocan, usé la
  nota más reciente y el código.
