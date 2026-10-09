# R1 — Spike: motor de triples/Datalog usable desde Babashka

Estado: **completo**. Sin cambios en ningún repo (solo lectura); todo el trabajo en
`/home/jp/.cache/spikes/R1/`. Regla de memoria cumplida: **todos** los comandos `bb` corrieron con
`memcap` (default 3 GB; ninguno necesitó `memcap 6`). Ningún run murió por OOM (exit 137).

Conclusion corta: **el único motor evaluado que carga y corre de verdad bajo Babashka es el pod
`huahaiy/datalevin`**. Datascript, Asami, clojure.core.logic y hiredman/datalog **no cargan** en
SCI/bb (detalles y trazas abajo). Recomendación: **pod de Datalevin**, con un evaluador Datalog
semi-naive in-process (ya escrito y medido) como camino de respaldo sin pod.

---

## 0. Entorno

```
bb v1.13.219        (host definitivo del CLI, decisión 14)
memcap (systemd-run MemoryMax=3G, MemorySwapMax=0)
12 cores / 15 GiB RAM
pod instalado: huahaiy/datalevin 1.1.0  (~/.babashka/pods, binario nativo 278 MB, auto-descarga)
fuente datalevin clonada: HEAD 4285f2a (2026-10-06), version "1.2.0"
```

---

## 1. Resultado por motor: ¿carga en bb?

Prueba ejecutada y guardada en `load_failures.log`.

| motor | versión | ¿corre en bb? | por qué |
|---|---|---|---|
| **Datalevin (pod)** | pod **1.1.0** (fuente 1.2.0) | **SÍ** | pod nativo GraalVM con protocolo de pods; `pods/load-pod` |
| Datascript | 1.8.1 (tag; HEAD 2026-08-15) | **NO** | SCI no soporta sus `deftype` (interfaces `Associative`/`ILookup`) → muere en `datascript.lru`/`datascript.db`. **No existe pod** de datascript en `babashka/pod-registry` |
| Asami | 2.3.4 (HEAD 2024-09-26) | **NO** | mismo límite SCI en `asami.datom`/`asami.common-index`. No hay pod |
| clojure.core.logic | jar 1.0.0 (2022) | **NO** | `Unable to resolve symbol: definterface` (SCI). Además miniKanren, no Datalog; sin persistencia |
| hiredman/datalog | 2012 | **NO** | `Unable to resolve classname: java.util.concurrent.ForkJoinTask`; proyecto muerto (Clojure 1.5-SNAPSHOT) |
| datalog puro SCI tipo "26 loc" | — | — | no hay lib pura lista; `minikusari` depende de Datascript (no corre), `dedukt` no tiene reglas/recursión. Se cubre con el baseline a mano (§4) |
| Datahike (pod) | pod-registry 0.8.1903 | no probado | registrado como pod **JVM** (usa `datahike cli`); no hay `clojure` CLI instalado aquí. Alternativa viable, no benchmarkeada |

Trazas clave (`load_failures.log`):

```
### datascript
Babashka's deftype supports full map types ... at datascript/lru.cljc
### asami
Babashka's deftype supports full map types ... at asami/datom.cljc
### core.logic
Unable to resolve symbol: definterface  (clojure/core/logic/protocols.clj:13)
### hiredman
Unable to resolve classname: java.util.concurrent.ForkJoinTask
```

Setup del pod (auto-descarga una vez; luego offline):

```bash
bb -e "(require '[babashka.pods :as pods]) (pods/load-pod 'huahaiy/datalevin \"1.1.0\")"
# deja el binario en ~/.babashka/pods/repository/huahaiy/datalevin/1.1.0/linux/x86_64/dtlv
```

Script mínimo y funcionando: `dtlv_min.clj` (setup + schema + transact + regla recursiva +
negación + reapertura). Salida:

```
ancestors: ([a b] [a c] [a d] [b c] [b d] [c d])   ;; regla recursiva (semi-naive)
leaves (no children): ([a])                          ;; negación (not ...)
reopened datoms: 16                                  ;; persistencia en disco
```

---

## 2. Lenguaje de consulta, persistencia, licencia, mantenimiento

| motor | Datalog | reglas | recursión | negación | persistencia | licencia | mantenimiento |
|---|---|---|---|---|---|---|---|
| **Datalevin pod** | sí (flavor Datomic) | sí (`:in $ %`) | sí (semi-naive + magic sets) | sí (`not`, `not-join`) | **LMDB en disco** (WAL, async), KV, full-text, vectores | **EPL-2.0** | muy activo (2026-10-06) |
| Datascript | sí | sí | sí | sí (`not`/`not-join`) | in-memory (protocolo de storage externo; inútil en bb) | EPL | activo-ish |
| Asami | sí | sí | sí | sí | in-memory (+ almacenes durables nativos, no en bb) | EPL | bajo (2024) |
| core.logic | no (miniKanren) | sí (relaciones) | sí | no aplica | ninguna | EPL | bajo (2022) |
| hiredman/datalog | parcial | no robusto | — | — | ninguna | EPL | muerto (2012) |

Notas del pod:
- API expuesta (subconjunto): `q`, `transact!`, `get-conn`, `datoms`, `count-datoms`, `pull`,
  `entity`, `schema`, `transact-kv`, full-text (`search`), vectores (`add-vec`/`search-vec`).
- Reglas: `(d/q '[:find ... :in $ % :where (rule ?x ?y)] db rules)` — verificado.
- **Bug encontrado en el pod 1.1.0**: NPE en `datalevin.rules/project-rule-result` cuando una
  cláusula de regla va **después** de otras cláusulas con variables ligadas (late rule clause con
  tuplas semilla). La regla standalone sí funciona. La fuente ya está en 1.2.0; conviene usar/esperar
  el pod 1.2.0. (En `dtlv_min.clj` la recursión se demostró en forma standalone por esto.)

---

## 3. Benchmark — store real de kimun

Fuente: `desk.graph.json` (export de `kimun graph`; 3 100 nodos) → `triples_real.json`
= **47 349 triples** (`gen_triples.py`). Predicados top: `semantic_tag/tagged_as` 16 206,
`rdf:type` 3 100, `path` 2 801, `has_section` 2 262, `depends_on` 295, `semantic_parent` 23.

Esquema de triples: cuádruple `{:e :a :v :prov}` → **189 396 datoms** en Datalevin (4 por triple).

| | load total | 2-hop `depends_on` | 2-hop `tagged_as`·`semantic_parent` | recursión `semantic_parent` | RSS |
|---|---|---|---|---|---|
| **Datalevin pod** | **1.99–3.07 s** (open 113–348 ms, transact 1.8–2.6 s) | 35–55 ms (**521** filas) | 121–205 ms (**11 264**) | 11–15 ms (40) | pod ~**210 MB** |
| **baseline a mano** | **0.29 s** (parse 34 + index 49 ms) | ~1 ms (**566**) | 13 ms (11 264) | ~0 ms (40) | **101 MB** |

Diferencia de filas 521 vs 566: Datalevin deduplica datoms idénticos; el grafo real tiene triples
`(e,a,v)` repetidos que el índice a mano cuenta. Los 11 264 cuadran exactos.

Detalle (logs `dtlv_real.log`):

```
triples: 47349
open-ms 348 build-ms 159 transact-ms 2560 total-ms 3067
datoms: 189396
2hop-depends-rowcount 521 ms 55
2hop-tag-sp-rowcount 11264 ms 205
recursive-sp-closure-rows 40 ms 15
reopen-ms 14 datoms: 189396
peak dtlv pod RSS (kB): 215636
```

## 4. Benchmark — 1 M de triples (sintético balanceado, `gen_synth.py`)

Árbol binario `parent` (→ cierre 1 468 946 filas) + 300k `depends_on` DAG + `tagged_as` = 1 000 000.

| | load total | 2-hop `parent` | cierre recursivo `parent` | RSS pico | disco |
|---|---|---|---|---|---|
| **Datalevin pod** | 29.6 s (máquina ociosa) / 62.7 s (con carga) | 677 ms / 1 793 ms (99 997) | **1 468 946 filas** en 15.1 s / 37.4 s | ~**1.15 GB** | **200 MB** LMDB |
| **baseline a mano** | 26.4 s (parse 0.43 + index 1.9 + queries) | 1 351 ms (899 548 `depends_on`) | **1 468 946 filas** en 19.7 s | **1.10 GB** | — |

Los dos motores coinciden en el cierre (**1 468 946 filas**), lo que valida la comparación.
Logs: `dtlv_1m.log`, y salida de `hw_bench.clj`.

Notas de memoria (regla memcap):
- El baseline a mano pico **1.10 GB** y el pod **~1.15–1.18 GB** en 1 M: ambos **caben en el tope de
  3 GB**; no hizo falta `memcap 6`.
- El RSS del pod es un **proceso aparte** del bb; se midió por muestreo de `ps` (`dtlv`).
- El pod gasta 4 datoms por triple (`e`,`a`,`v`,`prov`) y el índice a mano 3 posiciones: de ahí
  también la diferencia de tiempos de carga.

## 5. `wittgenstein` / Matrix como motor G o como productor (párrafo)

Leídos `README.md` y `matrix-shrdlu-spec/specs/convergence.md`: Matrix/Wittgenstein **no es un
motor G** y no debe serlo. Es un pipeline Python que produce proposiciones `(R a b) @contexto` con
evidencia, y su aporte único —`WiGame` por `(contexto, R)` con matrices de verdad/sentido `V_i`,
`S_i` (Sinn/Sinnlos/Unsinnig), deducción `(if …)` y sobre todo la **convergencia** (CHIC/SIA:
identidad por proyección entre configuraciones, nunca por string)— es un *productor y promotor de
hechos*, no un almacén de triples con lenguaje de consulta. Su propia spec lo dice: los índices
`V_i`/`S_i`/proyecciones "son índices derivados, no nodos", exige no crecer con `p×p` (tres OOM en
2026-09-20) y su estado es frágil (root sin commits, dos líneas del motor sin reconciliar,
persistencia mínima en YAML por `WiGame`). Por tanto: **Matrix = plugin productor externo** que
emite hechos `(R a b)` + `Prov{document_id, span, extractor, confidence}` hacia la capa G, y kimun
es el que persiste, indexa y consulta (la frontera S/M→G ya está descrita en `12 §3`). Al revés
—meter Matrix como motor de consulta/derivados— chocaría con su modelo booleano por contexto, su
falta de Datalog y su límite de memoria.

---

## 6. Recomendación (UNA): **pod de Datalevin**

Razones:
1. Es **el único** de los candidatos que carga y ejecuta bajo Babashka (los demás fallan en SCI o
   están muertos), y encaja en la decisión 19 de kimun: *pods con frontera de proceso*.
2. Cubre la pregunta completa: almacena millones de triples **durables** (LMDB, 200 MB/1M triples),
   y ofrece Datalog con **reglas, recursión, negación**, optimizador por coste+magic sets.
3. Licencia **EPL-2.0** (compatible con Clojure/EPL) y mantenimiento **muy activo** (HEAD 2026-10-06).
4. El índice G es **derivado y reconstruible**, exactamente el régimen que ya usa kimun para su
   índice EAV (`kimun.surface.index`): el store del pod es un caché por cabecera, no autoridad.

Reservas y mitigaciones:
- El pod va **una versión por detrás** de la fuente (1.1.0 vs 1.2.0) y tiene el NPE de reglas
  semilladas (§2): usar 1.2.0 en cuanto salga; mientras, evaluar reglas en forma standalone o
  caer al evaluador in-process.
- Introduce descarga de red la primera vez y binario por OS/arch (~278 MB): mantener **fallback**
  in-process para el camino offline.

## 7. Sketch de integración (anillos de kimun)

- **ring 2 (puro, `.cljc`, sin I/O)** — `kimun.surface.graph.triples`: convierte el índice EAV
  derivado (`kimun.surface.index.build`) en triples `(R A B)` con procedencia, determinista y
  reconstruible desde `(store, head)`. Y `kimun.surface.graph.query`: el **puerto** (`query`,
  `query-rules`) sobre valores; sin I/O.
- **ring 1 (host, único con I/O)** — `kimun.host.graph.datalevin`: implementa el puerto con
  `(babashka.pods/load-pod 'huahaiy/datalevin "1.2.0")`; abre/cierra el conn y transacciona. Es el
  único ns con `pods/load-pod` e I/O de fichero.
- **caché derivado por cabecera**: `<store>/index/graph/` (junto al índice actual, gitignored),
  invalidado/reconstruido por `rebuild-indexes` y por cambio de head (02 inv. 10: "el log es
  inmutable; los índices no"). Nuevo verbo `kimun graph rebuild` (ring 3).
- **ring 3 (producto/CLI)** — `kimun graph query` y `kimun graph rule` (Datalog con `:in $ %`),
  registrados bajo la capacidad **`kimun/derive`** con `:engine :datalevin` (ya reservada en 06 §6 /
  12 §1). Si el pod no está (sin red/arch), cae al **evaluador semi-naive in-process** sobre el EAV
  (baseline §3–4) para no romper el CLI.
- **documentación a actualizar en S2/S3** (fuera de este spike): `02 §8` ("sin Datascript" pasa a
  "índice EAV + pod de Datalevin"), `06 §6` (nuevos verbos `graph`), `12 §1` (quitar el pendiente
  "Lenguaje de consulta general / Capa G").

## 8. Artefactos (en `/home/jp/.cache/spikes/R1/`)

- `triples_real.json` (47 349 triples del desk real), `triples_real.tsv`, `gen_triples.py`.
- `triples_1m.json`, `gen_synth.py` (1 M sintético).
- `dtlv_min.clj` (pod: setup/reglas/negación/persistencia), `dtlv_bench2.clj` + `dtlv_real.log`,
  `dtlv_bench_1m.clj` + `dtlv_1m.log`.
- `hw_bench.clj` (baseline EAV + semi-naive), `load_failures.log`.
- `engines/` (clones datascript/asami/datalevin/core-logic/hiredman/minikusari/dedukt/pss/extend-clj),
  `pods/`, `dtldb*` (stores LMDB de prueba).

## 9. Lo que queda / límites

- El store real dio **47 349 triples** (borde inferior del rango 50–100k pedido); el cierre
  recursivo real es trivial (`semantic_parent` solo 23 aristas), así que la evidencia de recursión
  a escala viene del sintético de 1 M.
- No se benchmarkeó el pod de **Datahike** (necesita toolchain JVM/`clojure` CLI no instalado).
- El NPE de reglas semilladas del pod 1.1.0 no se parcheó (es binario); se documentó y se evitó.
- El 1 M con pod tuvo variabilidad por carga de máquina (29.6 s vs 62.7 s); las tablas dan el rango.
