# Plan 2 — qué queda despachable de `12 §1` (2026-11-15, sobre `main` en `c8ca2f25`)

Alcance: K1 restante, S2b, resto de P1, K2b, M2, y una línea por cada otra fila. G1–G3 y K7 (esperan D-G) y RS1/RS2 (otra sesión) no se planifican. Todo lo que dice «espera al dueño» cita la fila de `docs/v2/drafts/preguntas-abiertas.md` o es una pregunta nueva (sección C).

Convenciones: **[visto]** = lo leí en el código, el commit o la evidencia; **[doc]** = lo dice un documento y no lo comprobé en el código; **[inf]** = inferencia mía.

Qué no hice: no corrí `bb`, ni pruebas, ni `git stash`. No leí el repo de `wittgenstein` (no sé en qué texto cuenta sus offsets en code points). No abrí los worktrees sin integrar (`/tmp/kimun-m1e-wt`, `/tmp/kimun-k5b-wt`). `main` se movió mientras leía (de `b0a51f0e` a `c8ca2f25`: M1h y spec16-lectores); la tabla refleja `c8ca2f25`.

Corrección a un plan anterior [visto]: `M1-plan.md §2` y `03 §1` dicen que mover `models.*`, `semantic` y `edges` al grafo «hoy no se puede». Es cierto solo para `models.*` y para la mitad «DAG de tags» de `semantic`. `edges` y la mitad «export» de `semantic` sí se pueden mover hoy (§A.P1 y corte P1b).

---

## A. Tabla de TODAS las filas de `12 §1`

Fuente de las filas: `docs/v2/12-ecosistema-y-pendientes.md` l. 76–101 (tabla principal) y l. 40–49 (complemento de frentes).

### A.1 Tabla principal

| fila | estado real hoy (evidencia) | lo que falta | clasificación |
|---|---|---|---|
| D0 | contrato derivado: `14 §21` l. 447–466, decisión 46 de `05`, `d0-contrato-sintesis.md` [visto] | nada (es documento) | cerrada |
| K1 | K1a `aac04b59`, K1b `648adb02`, K1c `c25cf65c`, K1d `9b8cc887` + `a9d5b9d5`, K1e `ed9514e2` [visto: existen; evidencia en `runs/subagents/2026-11-15-K1{a..e}/`; K1e: 66 dirigidas y suite completa 1220 tests, `EVIDENCE.md`] | ver §A.K1 (seis pendientes) | **parcial**: cierre y traslado de unidades, campos anidados e ítems, estados de marcas **despachables** (K1f, K1h); linaje y rename **esperan Q2/Q3**; sensor **espera U1**; modelos como unidades **espera conversación con el dueño** (M1k) |
| K2a | C2a/C2b/C4/C5 (`3b2d6d5d`..`b4931b7c`); MED1 `013ecd4d` midió coste en sintéticos [visto `REPORT.md`] | fan-out dentro de un directorio; medición en store real; hojas por campo (`10 l. 600`: «dependen de K1») | **espera al dueño: Q5** (¿exige K2a el fan-out?, MED1 dice que a ≤2000 rutas no hace falta por tiempo) |
| K2b | nada hecho. `mmap` es la estructura de la Revisión (`:trees`/`:edges`/`:incidence`, `kernel/revision.cljc` l. 12–24, `heads.edn` en `kernel/store.cljc`) [visto] | todo; el spec no dice con qué se reemplaza | primer corte seguro: **K2b-0** (medir e inventariar, sin tocar `src/`). El resto **espera al dueño: Q4** |
| K3 | `076be35b`, `43d9db6d` [visto] | — | cerrada |
| K4 | `d2dae59d`, `db497ca7`, `c0fe5aff` [visto] | reempaquetado y GC sobre packs (`10 l. 751, 798`: «No hay reempaquetado ni GC») | **espera al dueño: fila `GC`** de `preguntas-abiertas` |
| K5 | primer corte `04cf04b8` (`stores unreachable`) [visto] | borrar; política de retención; índice de nombres | borrar: **espera fila `GC`**; retención: hito 8 de `02` sin spec [doc `02 l. 844`] → **Q6**; índice de nombres: `10 l. 108` lo lista como **alternativa descartada** → **Q6** |
| P1 | `70b8abe9` + `971b6b2c`; anillo en `scripts/check_rings.clj` [visto] | `edges` y export de `semantic`: **despachable (P1b)**. `models.*`: choca con el anillo (§A.P1) → **Q1** | parcial |
| M1 | a, b, c, d, f, g, h, i, j hechos (`runs/subagents/2026-11-15-M1{a,a-pasos,b,c,d,f,g,h,i,j}/`, `spec16-lectores/`) [visto] | M1e (retenido por fila `M1e`), M1k y M1l (esperan la conversación con el dueño, bloque «No es una pregunta abierta» de `preguntas-abiertas`), selectores de código (sin spec; `16` solo los menciona de ejemplo) | **espera al dueño** (filas `M1e`, `16-det`, `16§3.5`, conversación pendiente) |
| M2 | nada hecho; `kimun.include.edn` sigue vacío [visto]. Lo que M2 necesita ya existe: `models define` y `docs track` con clases spec 16, `include build` las construye (`spec16-lectores/REPORT.md`) | M2a (anclaje del desk): **despachable**. M2b (generación: `docs create`, `fields set`): rechazadas declaradas `docs.cljc l. 263–270`, `fields.cljc l. 170–172` | M2a despachable; M2b **espera filas `M1e` y `F10/A1.1`** y **Q8** |
| S1 | `65ca4b5f`, `6930afa8`, evidencia `2026-11-15-S1/` [doc + commit existe] | — | cerrada |
| S2 | S2a `63952267` [visto]; K1e hecho | S2b: marcas dentro de un bloque con rango en grafemas NFC y mapeo code points→grafemas: **despachables (S2b-r, S2b-cp)**. «Anclas que referencian ids de unidad» como extremo de arista: **espera fila `F10/A1.1`** y **Q9**. Ids de nodo del CST (`R2 §5` fase 3 punto 4): sin spec de estabilidad | parcial |
| G1, G2, G3 | — | — | **espera D-G** (no se planifica) |
| K7 | — | — | **espera D-G** (depende de G1) |
| C1 | `cdce1cb9`, `3902731d` [visto] | — | cerrada |
| T01 | `9e106029` [visto] | — | cerrada |
| K6 | `555b2dbc`, `b6d9541f`, M1h `690e0314` [visto] | extensión a código | **espera M1** (selectores de código sin spec) |
| K8 | `bcd85a53` [visto] | — | cerrada |
| DOC1 | `5def87de` [visto] | — | cerrada |
| RS1, RS2, C2 | otra sesión / diferida (decisión 47) | — | fuera de alcance |

### A.2 Complemento de frentes (`12 l. 40–49`)

| frente | estado | clasificación |
|---|---|---|
| Autoridad y contratos | contrato D0 integrado; casos de continuidad en K1 | ver K1 |
| Sensor y store: coste incremental, origen de señales | `stores heartbeat` `86a0ba42` [visto]; MED1: latido ≈38 s a 2000 docs, crece lineal con N, independiente del esquema de rutas (`MED1/REPORT.md`). «Origen de señales»: sin definición en `docs/v2` (`grep` vacío fuera de `12 l. 43`) [visto] | **espera al dueño: Q7** |
| Sensor: unidades `missing`/`mutated` | solo `docs show`/`docs marks` las ven; `diagnostics.cljc` y `signals.cljc` no mencionan unidades [visto] | **espera al dueño: fila `U1`** (cubre también las marcas, ver §A.K1) |
| Relaciones tipadas, consulta por unidad, embeddings por unidad | sin spec (`ambiguedades A4.1–A4.3` abiertas [doc]) | espera spec; embeddings por unidad además en G3 (D-G) |
| Heartbeat: cursores, épocas, causalidad | sin spec (`14` cabecera l. 31, `06 §3`) y no hay productor de acciones | **espera al dueño: Q7** |
| Ejecuciones marcadas | «contrato por especificar» (`14 l. 13`, l. 424) | espera spec del dueño (misma Q7) |
| Operación y vista | V1, R1, S1-serve, F1, C1-fase 1, TV1 integrados; pendientes: pod en CI y sha256, windows, arreglos de `serve`, piloto, vista del circuito | arreglos de `serve`: fila `SERVE`; F1-H3: fila `F1-H3`; caché fría fase 2: fila `PLANES`; piloto y vista: sin spec (no los cubro, no estaban en mi encargo) |

### A.K1 — pendientes de K1, uno por uno, contra el contrato D0

Fuente de lo pendiente: `12 l. 79`; `14 §21` l. 468–510. Reglas: `14` l. 447–466 (I1–I13 en `d0-contrato-sintesis.md §3`).

| pendiente | ¿qué dice el contrato D0? | estado | corte |
|---|---|---|---|
| Cierre declarado de una unidad de campo | I7 / regla 3 (l. 451–454): cerrar es declaración con actor y evidencia. K1b y K1e ya fijaron el patrón (`--reason`, `:declared` en el journal) [visto en `K1e/EVIDENCE.md`] | especificado | **K1f** |
| Mover una unidad a otro documento | I7 (re-anclarla en otro lugar = misma unidad); caso «mover» (`§4` de la síntesis): misma unidad, anclaje nuevo, queda quién y qué pasó en medio | especificado | **K1f** |
| Campos anidados e ítems de lista como unidades | I11 (la regla vale en cada nivel). No dice si se acuñan solos (como K1c) o por declaración. `14 l. 531`: «los anclajes más finos… se declaran por instrucción» | **lectura mía [inf]**: por declaración, como K1e; el volumen y el desplazamiento de ordinal de un ítem descartan acuñar solo | **K1h** (con esa lectura declarada en el brief) |
| Estados de marcas y de unidades en el sensor | regla 3: «el sensor informa `missing`»; no fija tipo de señal ni severidad (`14` l. 452–453) | falta decisión | **espera fila `U1`** |
| Renombrar un campo (misma unidad, otro nombre) | caso «mover» dice misma unidad. Pero K1c acuña una unidad nueva en cuanto el campo nuevo aparece, antes de cualquier declaración | falta decisión | **espera Q3** |
| Copiar, dividir, fusionar con linaje declarado | I8 (l. 455–459): unidades nuevas con linaje declarado. Los nombres de las relaciones son «ilustrativos: fijarlos es diseño de K1» (síntesis `§4`). **Dónde vive el linaje** (I13: la relación lleva id propio; las unidades no son nodos del kernel: viven en `:units`/`:marks` del descriptor) no está dicho | falta decisión | **espera Q2** |
| Modelos como unidades (M1k) | `14 l. 497–500`: «cuestión abierta para M1»; `preguntas-abiertas` l. 43–49: conversación pendiente con el dueño | espera | **espera al dueño** |

### A.P1 — verificación de los `:require` reales [visto]

Anillo: el grafo puede requerir `kernel`, `surface`, `graph`, `cheshire`; no `kimun.host.` (`check_rings.clj` l. 16–23). Solo `graph.*` y `cli.*` pueden requerir `graph.*` (l. 26–29). Todo lo demás de `kimun.surface.*` (incluidos `index.*` y `http.*`) es base.

| namespace | quién lo requiere | veredicto |
|---|---|---|
| `kimun.surface.edges` | solo `cli/commands/{edges,links,predicates}` y `graph/store.cljc` | **se puede mover ya** a `kimun.graph.edges`. Él requiere `docs`, `links`, `models.segments`, `models.store`, `semantic`, `kernel.*`: todo permitido desde el grafo |
| `kimun.surface.semantic`, mitad **export** (`export`, `export->snapshot`, `contract-version`, l. 270–306; usa `graph.io`) | `cli/commands/semantic` y `graph/store` | **se puede mover ya** a un namespace del grafo; elimina el cruce `semantic → graph.io` de `pending-graph-crossings` y la lista queda vacía |
| `kimun.surface.semantic`, mitad **DAG de tags** (`model-tags`, `tags-of`, `dag-of`, `reach`, `in-scope?`…) | `index/build`, `index/search`, `index/address`, `http/views` (base) | **no se puede mover**: la base lo requiere |
| `kimun.surface.models.*` | `surface.docs`, `fields`, `sections`, `diagnostics`, `index.*`, `http.views` (base). Además `selector_cst` requiere `kimun.host.treesitter`, que el anillo del grafo no admite | **no se puede mover** sin que la base dependa del grafo o sin cambiar el anillo |
| `models.compile`, `models.capture` | solo CLI | movibles, pero dejan a `compile` en el grafo y a `descriptor`/`recipe` (que requiere) en la base: es cosmética, no la recomiendo suelta |

Contradicción de spec que sostiene Q1: `16 §5` l. 229–236 pone «descriptores, selectores EDN, proyecciones» en el grafo y dice que el grafo «depende de la base y del parser M», pero `surface.docs` calcula payload y huellas con el selector (K6 lo exige en la base) y el anillo del grafo prohíbe `kimun.host.`.

---

## B. Cortes despachables ya

Estimaciones en horas de ejecutor, una vuelta con revisión [inf].

### K1f — cierre y traslado declarados de unidades (brief completo en D1)
- **Objetivo.** Dos de las tres declaraciones de I7: cerrar una unidad de campo y trasladar una unidad (marca o campo) a otro documento conservando su id. Sin rename dentro del documento, sin linaje.
- **Spec.** `14` l. 451–454 (regla 3), l. 460–466 (reglas 5–7), l. 503–505 («Quedan fuera: mover…»); `d0-contrato-sintesis.md §3 I7, I12` y tabla de casos (`mover`, `borrar`); patrón de `K1b`/`K1e`.
- **Archivos.** `src/kimun/surface/docs.cljc` (`units-for`, `mark-plan`), `src/kimun/cli/commands/docs.cljc` (`unmark`, `mark`), `src/kimun/cli/commands/include.cljc` (arrastre de `:units`/`:marks`), `src/kimun/surface/markdown/plan.cljc` si hace falta. Pruebas nuevas `test/kimun/cli/unit_close_move_test.cljc`.
- **NO incluye.** Rename dentro del documento (Q3), linaje (Q2), campos anidados e ítems (K1h), sensor (U1), modelos como unidades, cambios en `src/kimun/kernel/*`.
- **Pruebas (6).** (1) cerrar un campo que sigue en el payload y volver a actualizar: el campo recibe una id **distinta**, la cerrada no reaparece, el journal trae `:declared {:kind "unit-closure"}`; (2) cerrar un campo ausente (`:missing`) lo quita y no vuelve tras `include build --rebuild`; (3) trasladar una marca de A a B: misma id en B, ya no en A, **una sola revisión**, journal `:declared` con origen y destino; (4) trasladar sin `--reason` o a un localizador/campo que ya tiene unidad en B se rechaza y el head no cambia (no hay fusión, regla 4); (5) la unidad trasladada de A a B sobrevive a `docs reanchor` y a `include build --rebuild` (`:marks-carried`/`:units-carried`); (6) `docs marks`/`docs show` de A y B no escriben (sha256 de `.kimun` igual).
- **Choca con.** K1h, S2b-r (mismos `docs.cljc` y `cli/.../docs.cljc`). Va en serie con ellos.
- **Estimación.** 4 h.

### K1h — campos anidados e ítems de lista, por declaración
- **Objetivo.** Marcar una clave anidada de un campo `:yaml` o un ítem de un campo `[:list …]` como unidad con id propio, con los estados `anchored|mutated|ambiguous|missing` y candidatos de K1e.
- **Spec.** `14` l. 531 (anclajes finos por instrucción), regla 5 (l. 460–462), I11; mecanismo: `K1e/EVIDENCE.md` y `anchors.cljc` (`mark-states`, `advance-marks`).
- **Archivos.** `markdown/anchors.cljc` o ns nuevo `kimun.surface.markdown.field_marks`; `surface/docs.cljc`; `cli/commands/docs.cljc` (`mark --field F [--index N | --path a.b]`); tests.
- **NO incluye.** Acuñar ids solas para ítems (lectura [inf]; si el dueño prefiere lo contrario, hay que cambiar el corte), sensor, documentos genéricos (no tienen campos).
- **Pruebas (5).** insertar un ítem antes del marcado → `:ambiguous` con candidato por huella (sin decidir); borrar el ítem → `:missing` y la id sigue; editar su texto → `:mutated`, y tras `docs update` misma id; dos ítems idénticos → dos unidades, un candidato 1:n listado entero; las marcas no entran a `content-hash` ni a la entrada de ruta.
- **Choca con.** K1f, S2b-r. **Depende de** K1f (comparte la tabla de cierre/traslado). Estimación 5 h.

### S2b-r — marcas dentro de un bloque, con rango en grafemas NFC
- **Objetivo.** Una marca cuyo localizador es un bloque de S2a más un rango `[inicio fin)` en grafemas sobre el texto plano NFC de esa hoja; mismos estados y candidatos que K1e (candidatos por huella del subtexto).
- **Spec.** `14 §7` l. 356 y 373 (hoja + `[start end]` en grafemas NFC), `05` decisión 32 (l. 122), `02 §4` y `§4.1` (l. 247–286: «anclaje primitivo `(hoja, rango, hash)`», dirección virtual), `K1e/EVIDENCE.md` («Quedan fuera: marcas dentro de un bloque (S2b)»).
- **Archivos.** `markdown/anchors.cljc`, `markdown/inline.cljc` (texto plano y offsets de grafema ya existen, l. 38–41, 139–153), `surface/docs.cljc`, `cli/commands/docs.cljc`; tests.
- **NO incluye.** Anclas como extremo de aristas (Q9, `F10/A1.1`), párrafos fuera del perfil (`inline` devuelve `:unparsed`: se rechaza declarado), el mapeo desde code points (S2b-cp).
- **Pruebas (5).** rango sobre grafema compuesto/emoji ZWJ conserva los límites; un rango que parte un grafema se rechaza declarado (no se ajusta en silencio, `14 §7`: «nunca se descarta ni se sobreescribe en silencio»); editar fuera del rango → `:anchored`; el mismo subtexto aparece dos veces → `:ambiguous` con las dos candidatas; párrafo con markup inline: el rango es sobre el texto plano, no sobre el fuente.
- **Choca con.** K1f, K1h. **Depende de** K1f solo por archivos (serie). Estimación 5 h.

### S2b-cp — mapeo de code points a grafemas en la frontera
- **Objetivo.** Función pura que, dado un texto y un rango en code points, devuelve el rango en grafemas del texto NFC; rechaza declarado un rango que parte un grafema.
- **Spec.** `14` l. 356 y 373, `05` decisión 32, `12 l. 209–212` («offsets en code points sobre el documento entero vs grafemas por hoja»), `ports/graphemes` (`kernel/ports.cljc` l. 23–26), `host/text.cljc`.
- **Archivos.** ns nuevo bajo `kimun.surface.markdown.*` (anillo: kernel + host) y su test. No toca `docs.cljc`.
- **NO incluye.** Adaptador de `wittgenstein`, mapeo documento→hoja (necesita saber qué texto cuenta wittgenstein: no lo verifiqué), CLI.
- **Pruebas (4).** ASCII = identidad; carácter astral cuenta uno en code points y uno en grafemas; secuencia de normalización (`e` + U+0301 vs `é`) da el mismo rango NFC; rango que parte un cluster → error declarado.
- **Choca con.** nadie. **En paralelo con todo.** Estimación 2 h.
- **Supuesto [inf]:** los offsets se miden sobre el texto que se le pasa a la función, no sobre «el documento»; qué texto es lo decide el adaptador (G).

### P1b — `edges` y el export de `semantic` al paquete del grafo
- **Objetivo.** Mover `kimun.surface.edges` a `kimun.graph.edges` y la mitad export de `kimun.surface.semantic` a un namespace del grafo; vaciar `pending-graph-crossings`.
- **Spec.** `14 §19` regla 3 (l. 202), `03 §1` (l. 27–36), `12 l. 85`, `P1/EVIDENCE.md` («Limits»).
- **Archivos.** `src/kimun/surface/edges.cljc`, `semantic.cljc`, `graph/store.cljc`, `cli/commands/{edges,links,predicates,semantic}.cljc`, `scripts/check_rings.clj`, `test/kimun/lint_test.cljc`, `test/kimun/surface/edges_test.cljc` y `edges_parity_test.cljc` (pasan a `test/kimun/graph/`), `docs/v2/03 §1`, `14 §19`, `12` fila P1.
- **NO incluye.** `models.*` (Q1), el DAG de tags de `semantic`, cambios de comportamiento.
- **Pruebas (4).** `bb lint` falla si una base requiere `kimun.graph.edges` (ya cubierto por la regla; agregar el caso); `pending-graph-crossings` vacío y una entrada sobrante falla; la salida de `edges *`, `links *`, `semantic export` y `graph *` es byte a byte la de antes sobre un store de prueba (patrón de `P1/cli-parity.txt`); `graph/store` carga un export v2.
- **Choca con.** nadie por archivos de código; solo con `12`/`03`/`14` (documentos). **En paralelo con todo.** Estimación 2 h.

### K2b-0 — medir e inventariar (sin cambiar `src/`)
- **Objetivo.** Dar al dueño los números actuales que decidirían Q4: bytes del `mmap` (`:trees`/`:edges`/`:incidence`) y de las tres aristas por documento en un store de formato 4 hoy, y la lista de lectores de cada una.
- **Spec.** `14 §20` l. 344–349 (diagnóstico histórico: 708 KB de mmap y 489 KB de aristas sobre 539 docs), `10 §9` y l. 496–502.
- **Lectores ya localizados [visto].** Arista `reference` raíz→clase: la lee `docs.cljc describe` (`anchor-edge`: `:contract`, `:ancestors`, `:parse-contract`, `:content-hash`; son la evidencia del K6, no se pueden quitar sin reubicarla). Arista `derived` raíz→clase: la lee `describe` para `:state` (`extracted`, l. 274) y `parse_contract.cljc` l. 61–63 registra su motor. Arista `reference` raíz→archivo: `ext-edge`, fingerprint y locator. `:edges`/`:incidence` de la Revisión: `kernel/revision.cljc`, `kernel/anchor.cljc`, `kernel/upgrade.cljc`, `graph/*`, `surface/edges.cljc`.
- **Archivos.** solo `runs/subagents/2026-11-15-K2b-0/` y, si hace falta, `scripts/bench/` (ya existe `gen_store.clj`).
- **NO incluye.** Ningún cambio de formato, ni retirar nada.
- **Pruebas.** No aplica código; entregables verificables: tabla de bytes por clase de objeto (`store/object-class`) sobre un store sintético de 600 docs y sobre el desk de este repo (cuando M2a lo llene), comparada con la medición del 2026-10-09; cada lector con archivo:línea.
- **Choca con.** nadie. Estimación 2 h.

### M2a — descriptores spec 16 de anclaje para el desk (brief completo en D2)
- **Objetivo.** Recrear los 13 modelos del desk como descriptores spec 16 (anclaje, `:compat :legacy-v1-template`), declararlos en `kimun.include.edn` con sus globs y hacer que `include build` y `include check` den un store del desk sano.
- **Spec.** `12 l. 87`, `docs/archive/desk/README.md` («`desk/models/*.edn` (todos, 2026-10-09)»), `16 §2.3` nota (l. 83–86: reingesta de M2 = `source`), `16 §3.2`, `16 l. 267–268`, `test/fixtures/models/deskops/`.
- **NO incluye.** Proyecciones de generación, `docs create`, `fields set` (esperan `M1e` y `F10/A1.1`; hoy rechazados declarados), pasar algún modelo a `:strict` (Q8).
- **Choca con.** nadie en `src/`; toca `kimun.include.edn`, `desk/models/`, docs. Estimación 4 h.

### Orden y paralelismo
- **Serie K1** (mismos archivos): K1f (4 h) → K1h (5 h) → S2b-r (5 h). Por la regla de ordenar de más corto a más largo: K1f, luego K1h y S2b-r (empatan en 5 h; por orden alfabético K1h antes que S2b-r).
- **Paralelos entre sí y con la serie K1** (sin archivos en común): **P1b**, **S2b-cp**, **K2b-0**, **M2a**. Único solape: P1b, M2a y K2b-0 editan filas de `12 §1`; el coordinador las actualiza tras integrar (regla de M1-plan §2).
- Nota: K2b-0 se enriquece si corre después de M2a (mide el desk), pero no depende de él.

---

## C. Preguntas nuevas para el dueño (no están en `preguntas-abiertas.md`)

**Q1 — ¿Dónde pasa la línea entre la base y el grafo para los modelos?**
Cita: `14 §19` regla 3 (l. 202) y `16 §5` (l. 229–236, 284) frente a `check_rings.clj` (el grafo no admite `kimun.host.`) y `surface/docs.cljc` (la base ejecuta el selector para calcular payload, K6 y huellas; requiere `models.{descriptor,extract,render,selector,store,subgraph}`).
Opciones: (a) **invertir la dependencia**: la base recibe un puerto «extractor» que el grafo/CLI le inyecta, y `models.*` pasa a `kimun.graph.*` (ampliando su anillo a `kimun.host.`); (b) **redibujar la línea**: el anclaje (descriptor, selector, extracción, contrato K6) es base y solo generación, jerarquía consultable y G son grafo; (c) dejar `models.*` en la superficie y cerrar P1 con P1b.
Mi lectura: (b), porque el store estable necesita el digest del contrato y el selector para sensar, y `16 §2` ya separa «anclaje» de «generación». Cambia qué es `kimun.graph` y reabre el texto de `16 §5`.

**Q2 — ¿Dónde vive el linaje entre unidades (copiar, dividir, fusionar) y es consultable?**
Cita: `14 l. 455–459` (regla 4), `d0-contrato-sintesis.md I8, I13 y §4` («los nombres de las relaciones son ilustrativos»). Las unidades de K1c/K1e viven en `:units`/`:marks` del descriptor, no son nodos del kernel; I13 dice que una relación de linaje lleva **id propio**.
Opciones: (a) anotación en el descriptor del documento destino + registro `:declared` en el journal (como K1b/K1e); (b) nodos/aristas del kernel (requiere que la unidad sea nodo); (c) solo journal.
Mi lectura: (a) ahora, pensado para migrar a (b) cuando exista el contrato de relaciones (`A4.x`, sin spec). Bloquea copiar/dividir/fusionar.

**Q3 — Renombrar un campo: ¿la unidad vieja continúa con el nombre nuevo, o la nueva continúa a la vieja?**
Cita: caso «mover» (`§4` de la síntesis: «la misma unidad, re-anclada por declaración») y `14 l. 455` (uno a uno conserva la unidad); K1c (`K1c/brief.md` punto 6) acuña en cuanto el campo nuevo aparece, antes de cualquier declaración.
Opciones: (a) re-anclar la vieja al nombre nuevo y **cerrar** la que se acuñó sola (conserva la letra de I7; la cerrada nunca tuvo relaciones); (b) conservar la nueva, cerrar la vieja y declarar linaje «continúa» (depende de Q2); (c) rechazar y exigir declarar antes del primer `docs update` (impracticable: el archivo cambia primero).
Mi lectura: (a).

**Q4 — K2b: ¿qué reemplaza al `mmap` de aristas e incidencia, y bastan K1a–K1e como «IDs de nodo cerrados»?**
Cita: `14` l. 344–345 («cuando los IDs de nodo estén cerrados»), `12 l. 81`; `kernel/revision.cljc` l. 12–24 (la Revisión **es** tres `mmap`: `:trees`, `:edges`, `:incidence`), `10 l. 496–502`.
Retirarlos cambia el formato de la Revisión (formato 5, `stores upgrade`) y el oráculo de bytes canónicos (`12 l. 74`: «`bb oracle` solo puede cambiar dentro de K1»). El texto no dice con qué se reemplaza ni si basta con reducir las aristas redundantes. Opciones: (a) retirar solo las tres aristas por documento (reubicando la evidencia K6 en el descriptor); (b) además rediseñar `:edges`/`:incidence`; (c) cerrar K2b con la medición K2b-0 si el costo ya no justifica el cambio.
Mi lectura: esperar K2b-0 antes de decidir; no puedo establecer una dependencia técnica real con K1 más allá de la frase del spec.

**Q5 — K2a: ¿exige la fila el fan-out dentro de un directorio?**
Cita: `10 l. 584–601` («Si K2a lo exige, es un pendiente…»), `12 l. 80`, MED1 (`REPORT.md`: a ≤2000 rutas planas son ≈2,3 s por `docs update`, 0,8 MB por escritura; N=10000 y store real **sin medir**).
Opciones: (a) cerrar K2a con la medición y dejar el límite documentado; (b) exigir un esquema de rutas 3 con fan-out; (c) medir antes un store real.
Mi lectura: (a)+(c) si existe un store real hoy; no sé si `05 §2` (1.551 docs) existe aún.

**Q6 — K5: ¿se retiran «índice de nombres» y «política de retención» de la fila?**
Cita: `10 l. 108` (índice persistente de nombres: «no hecho… ~0,1 s», en la tabla de **alternativas descartadas**) frente a `12 l. 84` y `10 l. 655` («el índice de nombres de K5 no está en este corte»); retención = hito 8 de `02 l. 844`, sin política escrita.
Opciones: quitarlos de K5 (queda solo GC, fila `GC`), o escribir su spec.
Mi lectura: el índice de nombres, descartado por el propio `10`; la retención, pregunta de producto.

**Q7 — Latido: ¿qué se especifica ahora y qué se pospone?**
Cita: `14` cabecera l. 31 («contratos de heartbeat y de activación, siguen por especificar»), `06 §3 «El latido»` (cada latido abre el store y recalcula todo; «su costo crece con el store»), `12 l. 43` («origen de señales, coste incremental», sin definición en `docs/v2`).
Piezas sin spec: (1) cómo detectar un cambio del ambiente sin releer todos los archivos (pista por mtime y tamaño con verificación completa cada N latidos, o hash total como hoy); (2) qué es «origen de señales»; (3) cursores/épocas/causalidad (no hay productor de acciones internas, `06 §6`); (4) ejecuciones marcadas.
Mi lectura: dejar (3) y (4) hasta que exista un productor; (1) y (2) son decisión suya porque cambian la garantía de «sin pérdida silenciosa». MED1 mostró 38 s a 2000 docs, que es el único dato.

**Q8 — M2: ¿legacy o strict por modelo del desk, y cómo se escribe un campo de un documento `source` de clase spec 16?**
Cita: `16 l. 267–268` («falta decidir para cada modelo del desk… `:strict` o `:legacy-v1-template`»; corrige 5 divergencias de `AtomDoc`/`TaskDoc`); `16 l. 76–88` (autoridad del `source` es el archivo). Hoy `fields set` y `docs create` sobre spec 16 se rechazan (`fields.cljc` l. 170–172); `AGENTS.md` dice que el desk se maneja con `docs`, `fields`, `find`.
Parte 1 (reversible): M2a usa legacy en todos; pasar uno a strict es otra versión del modelo. Parte 2 (cambia el diseño): permitir `fields set` sobre un `source` exige renderizar y **reescribir el archivo** con una proyección, perdiendo lo no anclado, lo que choca con `16 §2.2` («sin inversión línea a línea»); o se edita el archivo y se usa `docs update`. Mi lectura: parte 1 legacy; parte 2, mantener el rechazo hasta que decida junto con las filas `M1e` y `F10/A1.1`.

**Q9 — S2b, «anclas que referencian ids de unidad»: ¿qué significa?**
Cita: `12 l. 89` («anclas que referencian ids de unidad y el mapeo de code points a grafemas»), `14 l. 736` («K1e les dará identidad al marcarlas y S2b las referenciará»), `02 §4.1` (un `:span` es nodo con `:leaf` y `:range`; una arista lo usa como extremo).
Lectura A: las marcas con rango dentro de un bloque (S2b-r) y la salida de `docs anchors` que nombra la unidad que cubre cada ancla. Lectura B: el ancla como **extremo de una arista del kernel**, lo que exige que la unidad sea nodo (es `A1.1` de la fila `F10/A1.1`).
Mi lectura: A es lo despachable; B espera `A1.1`. Pido confirmar A.

Preguntas que no hago porque el texto ya las responde: identidad de una clase entre revisiones (`14 l. 243`), formato del id (la síntesis `§7` lo da al equipo), quién puede declarar (idem), si K1h acuña solo (`14 l. 531`, lectura mía declarada en el brief).

---

## D. Briefs listos para despachar

Las reglas comunes están en `/home/jp/.cache/coord-briefs/kimun-reglas-ejecutor.md`; no se repiten. Los briefs usan el mismo molde que `kimun-K1e-unidades-estructurales.md`.

### D1 — Brief K1f: cierre y traslado declarados de unidades

```
# Brief K1f — cerrar y trasladar unidades, por declaración

Para: ejecutor (Pi). De: Claude (diseño y revisión). Un commit (lo hago yo tras revisar).
Depende de K1e integrado (`ed9514e2`). Reglas comunes: kimun-reglas-ejecutor.md.
Worktree: /tmp/kimun-k1f-wt (detached, sobre main).

## Contrato que este corte implementa (`14 §21`; reglas = inferencias del concilio/equipo)

- Regla 3 (`14` l. 451–454): la ausencia no termina una unidad; **cerrarla** o **re-anclarla
  en otro lugar** son declaraciones, con actor y evidencia, que nombran lo que pasó en medio.
- Regla 4 (l. 455–459): una unidad no se bifurca ni se funde. Aquí: trasladar no copia, y no
  puede aterrizar sobre un sitio que ya tiene unidad.
- Regla 6 (l. 463): el sensor propone; el cierre y la continuidad se declaran.
- Caso «mover» de `d0-contrato-sintesis.md §4`: la misma unidad, anclaje nuevo.

> **kimun nunca decide solo que una unidad se cerró o se movió.** Estos dos actos se declaran
> con `--reason`; este corte no agrega ninguna detección.

## Modelo (lo que ya existe)

- Unidad de campo (K1c): `:units {"<campo>" "<ulid>"}` en el descriptor del árbol; estado
  derivado `:anchored|:missing` (`docs/unit-states`). `units-for` acuña una id para toda clave
  de primer nivel del payload que no tenga y **nunca quita**.
- Marca (K1e): `:marks {"<ulid>" {:kind :locator :hash :label}}`; `docs mark|unmark|marks`.

## Qué cambia

1. **Cerrar una unidad de campo.** `docs unmark NAME ID --reason R` (el mismo comando de
   K1e) acepta también la id de una unidad de campo: la quita de `:units`. `--reason` es
   obligatorio para unidades de campo. Journal: `:declared {:kind "unit-closure" :unit ID
   :field F :status <anchored|missing>}`.
   - Si el campo sigue en el payload, la siguiente escritura le acuña una id **nueva** (el
     reemplazo se declara: regla 5). La cerrada no se reutiliza ni vuelve (`include build
     --rebuild` no la resucita).
   - Un id desconocido da `docs/missing`.
2. **Trasladar una unidad a otro documento.** `docs mark OTHER --unit ID --from NAME
   (--locator L | --field F) --reason R`:
   - Marca: `L` debe resolver en OTHER; el localizador no puede estar tomado por otra marca
     de OTHER (`docs/exists`). Se quita de NAME y se agrega a OTHER con la misma id y la
     huella actual de OTHER.
   - Unidad de campo: `F` debe ser una clave del payload de OTHER y no tener ya unidad
     (`docs/exists`; **no se fusiona**: hay que cerrar la otra antes). Se quita de NAME y
     queda en `:units` de OTHER con la misma id.
   - Una **sola revisión** toca los dos árboles. **Verifica antes de escribir código** que un
     plan puede llevar dos `:register-tree`; si el kernel no lo admite, **para y repórtalo**
     (no tocar `src/kimun/kernel/*`).
   - Journal: `:declared {:kind "unit-move" :unit ID :from NAME :to OTHER :reason R}`.
   - NAME y OTHER pueden ser tipados o genéricos (las marcas valen en ambos; las unidades de
     campo solo en tipados: un campo en un genérico se rechaza `docs/not-anchored`).
3. Un descriptor sin cambios queda byte a byte como hoy. `:units` y `:marks` siguen sin
   entrar a `merkle-root`, `projection-hash`, `content-hash` ni a la entrada de ruta.

## Dónde

- `src/kimun/surface/docs.cljc`: `units-for` (respeta cierres), planes de cierre y traslado
  (reutilizan `update-plan`/`generic-update-plan` con `:units`/`:marks` y `:declared`).
- `src/kimun/cli/commands/docs.cljc`: `unmark` (id de campo), `mark --from/--field`; spec de
  opciones; subcomandos en `docs_test`.
- `src/kimun/cli/commands/include.cljc`: el arrastre ya conserva `:units` y `:marks`; solo
  verifica que un cierre no se pierda ni resucite.

## Fuera de alcance (escríbelo como límites)

- Renombrar un campo dentro del documento y el linaje (esperan decisión del dueño, Q3/Q2).
- Campos anidados e ítems de lista (K1h). Que el sensor informe estados (fila `U1`).
- Copiar, dividir, fusionar. Ids de modelos como unidades. Documentos `:inline` o formato < 4.

## Pruebas (seis, `test/kimun/cli/unit_close_move_test.cljc`)

1. **Cerrar un campo presente**: tras `unmark`, `docs update` acuña una id distinta; la
   cerrada no aparece en `:units`; el journal trae `unit-closure` con el estado previo.
2. **Cerrar un campo ausente**: sale de `:units`, y no vuelve tras `include build --rebuild`.
3. **Trasladar una marca A→B**: misma id en B y ausente en A, **una revisión** (cuenta de
   revisiones +1), journal `unit-move` con origen y destino; `docs marks B` la lista
   `:anchored`.
4. **Rechazos sin cambiar el head**: sin `--reason`; localizador ya tomado en B; campo de B
   que ya tiene unidad; id que no está en NAME; campo que no es clave del payload de B.
5. **Arrastre**: tras `docs reanchor` y `include build --rebuild`, la unidad trasladada
   conserva su id en B.
6. **Solo lectura y no es huella**: `docs marks`/`docs show` no cambian el sha256 de
   `.kimun`; `ast->tree` con `:units`/`:marks` distintos da el mismo `merkle-root`.

Mutación: sin tus cambios de `src/` fallan las seis (método de la regla 5 de reglas-ejecutor).

## Documentos (español, mínimos)

- `14 §21` (párrafo de implementación de K1): K1f, rotulado **inferencia del equipo**; quita
  «cierre … de unidades de campo» y «mover una unidad a otro documento» de «quedan fuera».
- `12 §1`: **solo** la fila `K1` (añade K1f; pendientes que quedan: rename, linaje, anidados
  e ítems, copiar/dividir/fusionar, sensor, modelos). **No** digas que K1 está cerrado.
- `06`: `docs unmark` con id de campo y `docs mark --from`.
- Reporta cualquier punto donde `14 §21` contradiga este brief: no decidas, cita y sigue.

## Entrega

`lint.txt`, `directed.txt` (las nuevas + `marks_test`, `field_units_test`, `reanchor_test`,
`identity_carry_test`, `generic_docs_test`, `typed_docs_test`, `docs_test`, `doc14_contract_test`),
`mutation.txt`, `REPORT.md` en `<worktree>/runs/subagents/2026-11-15-K1f/`. Incluye `bb oracle`.
```

### D2 — Brief M2a: descriptores spec 16 de anclaje del desk

```
# Brief M2a — el desk vuelve al store, con modelos spec 16 de anclaje

Para: ejecutor (Pi). De: Claude (diseño y revisión). Un commit (lo hago yo tras revisar).
Depende de M1f, M1g, M1h y spec16-lectores integrados (`main` en `c8ca2f25`).
Reglas comunes: kimun-reglas-ejecutor.md. Worktree: /tmp/kimun-m2a-wt (detached, sobre main).
**No construyas `.kimun` del repo real**: todo `include build` va a un repo temporal (copia
de `desk/` + los archivos nuevos) y se borra al final.

## Qué pide el spec

- `12 §1` fila M2: «regenerar los modelos del desk (borrados el 2026-10-09)».
- `docs/archive/desk/README.md` («`desk/models/*.edn` (todos, 2026-10-09)»): 13 modelos
  retirados: AtomDoc, ChecklistDoc, ConditionDoc, EdgeDoc, InboxNoteDoc, OperatorDoc, PillDoc,
  RepositoryDoc, RitualDoc, RoutineDoc, TaskDoc y las bases PrimitiveDoc y
  OperationalArtifactDoc. `kimun.include.edn` quedó sin `:models` ni `:documents`.
- `16 §2.3` nota (l. 83–86): una reingesta de M2 trata los `.md` del desk como **`source`**,
  nunca como generados. `16 §3.2`: el `:template` v1 es azúcar que compila a selector +
  proyección `:markdown/v1` (`kimun.surface.models.compile/template->spec16`).
- `16` l. 267–268: «falta decidir para cada modelo del desk si `:strict` o
  `:legacy-v1-template`». **Esa decisión no es tuya**: usa `:legacy-v1-template` en todos
  (es lo que emite `template->spec16`) y reporta los 5 casos de `R3` donde strict diferiría.

## Qué cambia

1. `desk/models/<Modelo>.edn` (13 archivos nuevos): cada uno es la vista
   `template->spec16` del descriptor v1 correspondiente, **más** las claves `:py/references`,
   `:py/containment` y `:py/compositions` del v1 copiadas tal cual (el subgrafo acepta claves
   desconocidas, `subgraph.cljc` l. 68; `surface/edges.cljc` l. 331–340 y `http/views.cljc`
   l. 107–116 las leen). Fuente de los v1: `test/fixtures/models/deskops/<Modelo>.edn`
   (congelados) y, para contrastar, `git show c7c426e7^:desk/models/<Modelo>.edn`.
   Genéralos con un `bb -e` descartable que NO se commitea y pega el resultado (no crear
   scripts nuevos en el proyecto). Quita `:template` de lo que escribas (v1 xor spec 16).
2. `kimun.include.edn`: `:models ["desk/models/*.edn"]` y las reglas `:documents` que tenía
   antes de `c7c426e7` (`git show c7c426e7^:kimun.include.edn`): atoms, contexts, checklists,
   conditions, edges, operators, registry, rituals, routines, tasks. Respeta lo que esa
   versión excluía (`desk/tasks/Board.md` es legado no editable por CLI y no entra) y **no
   restaures `:force`** (los chequeos de ruido y ida-vuelta se saltan para spec 16, M1g).
   Quita el comentario de «desk vacío».
3. Si algún documento no extrae bajo su clase, **no lo ocultes**: déjalo fuera con una regla
   `:exclude` solo si el archivo es legado demostrable; si no, repórtalo como hallazgo con
   archivo y campo faltante.
4. Documentos: `docs/archive/desk/README.md` (nota al final: los modelos vuelven como spec 16
   de anclaje, con fecha); `12 §1` solo la fila `M2`; `06`/`16` solo si algo dicho ahí queda
   falso.

## Fuera de alcance (escríbelo como límites)

- Proyecciones de generación, `docs create`, `fields set`, `sections fields` sobre el desk:
  siguen rechazados declarados (esperan `M1e` y `F10/A1.1`). No relajes ese rechazo.
- Pasar un modelo a `:strict`. Unidades o ids de modelos. Cambios en `src/`.
- No cambies el comportamiento de `models define` para que acepte lo que hoy rechaza.

## Pruebas (seis; pruebas de integración sobre repo temporal, `test/kimun/cli/desk_models_test.cljc`)

1. **Los 13 modelos validan** como spec 16 (`subgraph/validate!`), `models check` limpio y
   `models list --isa PrimitiveDoc` trae los que heredan de él.
2. **Diferencial contra v1 (la prueba que importa)**: para **cada** `.md` del desk, el payload
   extraído con el descriptor spec 16 compuesto es igual al extraído con el descriptor v1 de
   `test/fixtures/models/deskops/`. Un documento que difiera se reporta, no se tapa.
3. **`include check` y `include build`** sobre el repo temporal: sin `docs/structural-noise`
   ni `docs/not-idempotent`; número de documentos por modelo igual al número de archivos de su
   glob menos las exclusiones declaradas; `Board.md` ausente.
4. **Referencias y contención**: `edges`/`links` de un `TaskDoc` con `references`/`depends_on`
   da lo mismo bajo v1 y bajo spec 16 (las claves `:py/*` sobrevivieron).
5. **Salud**: tras el build, `stores health` y `stores check` dan `ok`; `find` encuentra una
   tarea por un campo; editar un párrafo no anclado de un `.md` no cambia su `content-hash`.
6. **Rechazo declarado vigente**: `fields set` y `docs create` sobre un documento del desk
   dan `cli/usage` con «spec 16» (el corte no los abre).

Mutación: con `kimun.include.edn` y `desk/models/` a `HEAD` fallan 1, 3, 4 y 5.

## Invariantes

`bb lint`; dirigidas (las nuevas + `include_test` de CLI y de surface, `spec16_docs`,
`spec16_readers`, `linked_models`, `edges_parity`, `doc14_contract`); `bb oracle` sin
diferencias; `bin/kimun include check` limpio **en tu worktree** (con los archivos nuevos). Nada
de `bb test` completo (lo corre el coordinador).

## Entrega

`lint.txt`, `directed.txt`, `mutation.txt`, `include-check.txt` y `REPORT.md` en
`<worktree>/runs/subagents/2026-11-15-M2a/`, con: lista de documentos que no extraen (si hay),
los 5 casos strict-vs-legacy de R3 aplicados al desk, tamaño y tiempo de `include build`, y
cualquier punto donde `16` contradiga este brief (cita y no decidas).
```

---

## E. Una frase honesta

De las 24 filas de `12 §1` hay 7 cerradas, 1 (K6) que espera selectores de código, 4 que esperan a D-G, 3 de otra sesión o diferidas, y 9 abiertas (K1, K2a, K2b, K4, K5, P1, M1, M2, S2): **ninguna de esas nueve se cierra del todo sin una respuesta del dueño**, pero sin ninguna respuesta más se pueden despachar ya seis cortes (K1f, K1h, S2b-r, S2b-cp, P1b, M2a) más una medición (K2b-0), que dejan hechos a ojo la mitad de K1, el resto de S2 salvo anclas como aristas, el resto de P1 salvo `models.*`, y M2 salvo generación; lo demás espera las nueve preguntas de la sección C o las filas ya abiertas (`GC`, `M1e`, `U1`, `F10/A1.1`, `PLANES`, `16-det`).
