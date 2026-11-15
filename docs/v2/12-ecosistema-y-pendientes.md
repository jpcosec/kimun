# Ecosistema y pendientes — inventario (2026-10-09)

> **Actualización 2026-11-15:** D0 dejó de ser una pregunta al dueño. El concilio derivó de sus
> premisas el contrato de identidad y continuidad para S–M
> (`runs/research/2026-11-15/concilio/d0-contrato-sintesis.md`; `14 §21`). K1 es ahora
> **implementación pendiente** de ese contrato, con decisiones técnicas del equipo, y lo que
> esperaba a D0 espera a K1. G sigue esperando la base S–M y D-G.

> **Actualización 2026-11-14 (estado histórico, superado por la nota del 2026-11-15 de arriba):** las filas D0 y K1 de §1 que dicen «identidad =
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
Este complemento no fija prioridades ni decide la identidad por inferencia. *(Estado histórico
del 2026-11-14. Desde el 2026-11-15 la identidad en S–M sí está derivada: el dueño aclaró que sus
premisas ya estaban fijadas y el concilio dedujo de ellas el contrato de `14 §21`, rotulando
sus reglas como inferencias del concilio.)*

| frente | tareas para cerrarlo | depende de | evidencia de cierre |
|---|---|---|---|
| Autoridad y contratos | integrar la dirección 2026-11-14 en `01`, `02`, `05`, `12`, `14`; cerrar D0 en positivo y casos de continuidad; distinguir nodo, observación y ancla | corrección del dueño; mapa de ambigüedades | contrato único S–M con casos de editar, mover, copiar, dividir, fusionar, borrar y cambio de contrato |
| Sensor y store | K2a/K2b (cortes C2a/C2b/C4/C5 implementados), K6, K8, K3, anclas CST, caché verificable, origen de señales, coste incremental | contrato S–M; K2b y anclas dependen de D0 | árbol Merkle por ruta sobre lo trackeado (`C5`: esquema de rutas 2, un nodo por directorio; los stores anteriores siguen en esquema 1 hasta `stores upgrade`); mutación local visible y reconciliable sin pérdida silenciosa; pendiente el coste dentro de un directorio ancho |
| Modelos y código | P1, M1/spec 16, M2, S1, superficie de código; separar anclaje de generación | contrato S–M; implementación de M1 depende de P1 y D0 | documento y función de código clasificados, anclados, consultables y regenerables |
| Relaciones y continuo | tipar relaciones jerárquicas, horizontales, métricas y recursivas; consulta por unidad; embeddings por unidad y actualización asíncrona | D0 y contrato de relaciones | relaciones declaradas recorribles; vecindad continua visible con modelo y vigencia identificables |
| Heartbeat y respuestas | estabilizar señal, daemon de recálculo, cursores/épocas y consumo asíncrono, causalidad sin bucles | `stores sense` es primer corte; contrato del heartbeat y sensor S–M | cambios externos y resultados internos se observan sin ejecutar acciones en el daemon; reinicio y repetición cubiertos |
| Ejecuciones marcadas | definir recibo, resultado, dependencias, verificador e invalidación | contrato S–M y canal asíncrono | resultado histórico y vigencia actual distinguibles en un caso reproducible |
| G | T01, D-G, G1–G3, K7 y consultas justificadas sobre contextos | cierre de S–M; fuentes de `wittgenstein` | fixtures contextuales y trazas de soporte; sin inferencia entre contextos por coincidencia de texto |
| Operación y vista | C1/F25, K4/K5, C2/API, seguridad, CI/release reproducible, piloto y vista del circuito | contratos de alcance y capas correspondientes | suite, oráculo, include check, fallos inyectados, bundle limpio y recorrido visible del cambio a la respuesta |

**D0, actualización 2026-11-14:** el dueño fijó dos etapas, `anchoring` y
`hecho detectado`, y distinguió realidad de hecho. La separación conceptual
permite especificar y avanzar en la sensorización de la primera etapa (K2a,
K6, K8 y S1) sin cambiar identidad del kernel. **2026-11-15:** el contrato positivo
de identidad y continuidad ya está derivado por el concilio (`14 §21`); K1, K2b y S2
esperan su implementación, no una respuesta del dueño.
Los informes del concilio están en
`runs/research/2026-11-14/concilio/d0-dos-etapas-{claude,agy,pi}.md`.

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
| D0 | **contrato S–M derivado por el concilio (2026-11-15); no hay pregunta pendiente al dueño**: la unidad es el hecho detectado con identidad propia; el anclaje es su referencia vigente; la continuidad uno a uno conserva la unidad y lo demás crea unidades nuevas con linaje declarado. Reglas = inferencias del concilio. La fila anterior («identidad = lugar») quedó sustituida por la corrección del dueño del 2026-11-14 | — | `14 §21`, decisión 46, `runs/research/2026-11-15/concilio/d0-contrato-sintesis.md` |
| K1 | **primer corte (K1a, 2026-11-15)**: arrastre de identidad de documentos al reconstruir (`include build --rebuild` reutiliza id de documento e id de árbol por ruta cualificada; `:identities` declara lo que no se puede seguir); pendiente: unidades debajo del documento, re-anclaje y cierre declarados, ids de modelos | D0 (contrato S–M derivado) | `14 §21`, `runs/subagents/2026-11-15-K1a/` |
| K2a | **parcial (cortes `C2a`, `C2b`, `C4` y `C5` implementados; quedan límites de coste)**: planes fuera del CAS (`f837069d`), mapa durable de rutas (`3b2d6d5d`..`790007e2`), mantenimiento desde `surface.docs` (`ff9d44c3`, `b776ae42`), cobertura (`2c56e680`) y **árbol Merkle por ruta `C5` (`b4931b7c`)**. El mapa tiene dos esquemas (`10 §11.2`): **esquema 1, legado** — un objeto plano y `route-root` como digest plano `O(n)` de entradas ordenadas; así sigue todo store creado antes de `C5`, sin cambios, hasta `stores upgrade` o `include build --rebuild`; **esquema 2, stores nuevos** — un nodo por directorio, una escritura reescribe los nodos de su camino, `open` valida solo el nodo raíz y el diff baja por donde los hashes difieren. **Pendiente:** no hay fan-out dentro de un directorio (cada nodo del camino se rehashea y reescribe entero: un directorio plano cuesta en proporción a sus hijos, como el esquema 1); enumerar rutas y la cobertura leen el árbol entero; sin medición en un store real; un binario anterior a `C5` informa un store de esquema 2 como `store/corrupt-head`. Las hojas por campo (`14 §20`) son de `K1`/`D0`, no de este corte | — | `14 §19–§20`, decisiones 43–44, `10 §11`, `10 §11.2`, `runs/subagents/2026-11-14-k2a-*`, `2026-11-14-C2b/`, `2026-11-15-C5/` |
| K2b | store estable: sin índice mmap por ids internos ni las tres aristas redundantes por documento (espera a K1, no al dueño) | K1, K2a | `14 §20` (diagnóstico del formato 4) |
| K3 | **cerrada (2026-11-14)**: documentos sin modelo registrados bajo la clase genérica `generic/md`, `untrack|delete` y resolución conjunta con tipados (`076be35b`, `43d9db6d`) | — | `14 §18`, decisión 41, `06 §3`, `runs/subagents/2026-11-14-K3/`, `2026-11-14-K3-untrack/` |
| K4 | **implementado (2026-11-15)**: `stores pack\|unpack` del store y `stores pack --cache` de la caché derivada (formato con pie + digest, lectura verificada por hash, un lector ignora en memoria un pack malo); pendiente reempaquetado y GC sobre packs | K2a | `10 §11.4`, `10 §11.5`, `runs/subagents/2026-11-15-K4/`, `runs/subagents/2026-11-15-K4b/` |
| K5 | índice de nombres y GC. **Primer corte implementado: inventario read-only de objetos no alcanzables** (`stores unreachable`: candidatos por clase, raíces reemplazadas, revisiones perdidas, planes retenidos por el log). **Pendiente:** borrar (exige lock de escritura y volver a mirar), política de retención de historia (hito 8 de `02`), índice de nombres | K2a | `10 §4`, `10 §5`, `10 §7`, `10 §11.3`, `runs/subagents/2026-11-15-K5/` |
| P1 | **parcial (primer corte `70b8abe9`, `runs/subagents/2026-11-15-P1/` + fix `971b6b2c`)**: `kimun.graph.*` en paquete aparte con regla de anillo en `bb lint` y cruce declarado `kimun.surface.semantic` → `kimun.graph.io`; mover `models.*`, `semantic` y `edges` espera `M1`/`D0` | — (corte restante ← `M1`/`D0`) | `14 §19`, decisión 43, `03 §1` |
| M1 | **spec 16 redactado** (`16-modelos-como-subgrafos.md`): modelos de anclaje y de generación, selector EDN sobre tree-sitter, herencia de clases; features de v1 sí, bugs no | — (spec redactado); implementación ← P1, K1 (contrato D0 ya derivado) | `16-modelos-como-subgrafos.md`, `14 §18`, decisiones 41–42, `runs/research/2026-10-08/R3-report.md`, `test/fixtures/models/spec16_examples.edn` |
| M2 | regenerar los modelos del desk (borrados el 2026-10-09 para dejar una versión estable) | M1 | `desk/`, `kimun.include.edn` |
| S1 | **cerrada (2026-11-15)**: base del parser M fase 2 en SLDB-MD (`65ca4b5f`, `6930afa8`, `735/735` idénticos); el ~3 % en Markdown libre era la medición histórica de `66ff1e9c` (vista pod-driven). Con la reconciliación conservadora la divergencia de AST es **0/500 y 0/3000** documentos muestreados, igual que `1099/1099` en corpus + desk; lo que queda es cobertura pod-driven (619 bloques de 500 documentos no adoptados, clases medidas) y pasa a `S2` fase 3 | — | `04 §11.2`, `14 §13`, `runs/subagents/2026-11-15-S1/` |
| S2 | parser M fase 3: anclas por rangos de bytes / nodos del CST (espera a K1, no al dueño) | K1 | `runs/research/2026-10-08/R2-report.md §5`, `14 §13` |
| G1 | capa G: leer fuentes (paper, wittgenstein, Tractatus) y reescribir el spec sobre sentido × verdad | K1 (contrato D0 ya derivado), D-G, M1, T01 (cerrada) | `docs/v2/drafts/capa-g-dudas.md`, `runs/research/2026-10-08/spec15/` (borrador previo, solo referencia) |
| G2 | consulta Datalog (Datalevin pod + respaldo in-process) | G1 | `runs/research/2026-10-08/R1-report.md` |
| G3 | embeddings sobre anchors (modelo multilingüe) | G1, K2a | decisión 38 |
| C1 | **cerrada (2026-11-14)**: selfdoc: guarda de before-image al escribir (`C1/F25`) y chequeo de actualidad de nodos fuente (`source-graph-check` / `python-check` sin KGDB externo) | — | `tests/parity-v1.md`, `11-adaptadores.md`, `kimun.cli.adapters-test`, `kimun.parity.adapters-parity-test` |
| C2 | cliente Python y rutas HTTP de graph_ui (`/kgdb/snapshot`, `/models/promote`) | K2a, P1 | `python/README.md`, `06` |
| T01 | **cerrada (2026-11-14)**: manifest de inclusión `C01–C28` (Wittgenstein `W1` + `W2`) con `required`/`deferred`/`excluded`, fixture Curie/Franklin y controles negativos | — (documentación) | `docs/v2/tests/wittgenstein-manifest.md`, `test/fixtures/wittgenstein/manifest.edn` (`test/kimun/wittgenstein_manifest_test.cljc`) |
| K6 | **implementada para Markdown (digest `555b2dbc` + `rebuild-mismatch`)**: `10 §11.1` completo para documentos tipados y genéricos (`source/*` vs `contract/changed` vs `contract/rebuild-mismatch` con recálculo puro en memoria sin escribir en `<store>/cache/`); solo queda la extensión a documentos de código cuando se implemente `M1` | — (`M1` para selectores de código) | `10-escala.md §11.1`, `docs/reviews/wittgenstein-superset/reviews/02-findings.md` F08, `proposals/03-store-and-authority.md`, `runs/subagents/2026-11-14-K6/`, `runs/subagents/2026-11-15-k6-rebuild-mismatch/` |
| K7 | licencia de inferencia/ruteo separada del alcance de búsqueda (stores enlazados y tags globales no autorizan cruces de contexto) | G1 | F22 |
| K8 | **cerrada (2026-11-14)**: nombres cualificados por ruta; el nombre corto es alias (`docs/ambiguous` ante colisión; `bcd85a53`, rutas en `ff9d44c3`) | — | F23, `06 §3`, `runs/subagents/2026-11-14-K8/` |
| DOC1 | **cerrada (2026-11-15)**: `14-juego-del-lenguaje.md` reordenado por tema (`Tema I–VI`, adendas `§16–§21` integradas, separación por estratos e histórico) | — | F18 (`14-juego-del-lenguaje.md`, `test/kimun/doc14_contract_test.cljc`, `runs/subagents/2026-11-15-doc1-spec14-por-tema/`) |

Insumo de G1: la revisión `docs/reviews/wittgenstein-superset/` (propuestas P4 grafo contextual y P5 T02–T13), `tests/wittgenstein-manifest.md` (`T01`) y la respuesta `respuesta-claude.md`.

En paralelo hoy, sin esperar al dueño: `K2a` (cortes `C2a`/`C2b`/`C4`/`C5` en `main`; pendiente el coste dentro de un directorio ancho y medir en un store real). Ya implementadas en `main` para su alcance actual: `K3`, `K6` (digest + `rebuild-mismatch` en Markdown; código espera `M1`), `K8`, `C1`, `T01`, `DOC1`, `M1` (spec), `S1` (2026-11-15) y primer corte de `P1` (`70b8abe9`). `K1` está disponible como implementación del contrato D0 (derivado por el concilio el 2026-11-15; no espera al dueño). Esperan a `K1`: `K2b`, resto de `P1`, `S2` (fase 3) y la implementación de `M1`. `G1` espera además `D-G`, que sí sigue abierta para el dueño.

### Deuda de diseño y experimentos

- **Parser Markdown**: `kimun.surface.models.blocks/parse` segmenta con el pod tree-sitter
  (decisión 29, fase 1 de R2 §5) y `parse-native` queda como oráculo diferencial (paridad de
  kinds y spans 1099/1099 en corpus + desk, `blocks-parity-test`). La fase 2 existe
  (`kimun.surface.markdown.cst-ast`, bandera `KIMUN_PARSER=treesitter`): AST idéntico
  1099/1099 en corpus + desk y 0 divergencias en 500 y 3000 documentos libres muestreados
  (`runs/subagents/2026-11-15-S1/`); el default sigue siendo `native` porque la vista
  reconcilia con el perfil en vez de dejar conducir al CST (619 bloques de 500 documentos), y
  eso se resuelve en la fase 3 (rangos de bytes e ids de nodo del CST), no con una reparación
  de divergencia (ver `04 §11.2`).
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
dos líneas del motor (`W1`: `matrix-shrdlu-spec`, `f13c67d`, capacidades `C01–C20`; `W2`:
`Matrix/Matrix`, `f016c75`, capacidades `C21–C28`) no están reconciliadas (sí comparten historia vía
`origin/main`, contra lo que dice el README); persistencia mínima (un YAML por WiGame).

El contrato completo de inclusión entre ambas líneas está fijado en
[`docs/v2/tests/wittgenstein-manifest.md`](tests/wittgenstein-manifest.md) (respaldo legible por
máquina en `test/fixtures/wittgenstein/manifest.edn`, 28 capacidades: `25 required`, `3 deferred`,
`5` exclusiones transversales `X01–X05`):

- **Línea `W1` (`C01–C20`):** extracción con offsets, símbolo contextual (`s = (w, c)`), descomposición
  offline (`:proposed`), `WiGame` por `(contexto, relación)`, `Li`/`Vi`/`Si`, álgebra relacional,
  fórmulas anidadas, deducción, runtime (`retract`/`why`/`ambiguity`/`define`), evidencia multi-cita,
  rutas de contexto, `CHIC`, convergencia/proyección dirigida, mantenimiento (`C_k`/`D_i`), métricas
  SIA tras protocolo externo, `ask` con `Si`, arnés corpus (`memcap`) y snapshot lógico (`C20`).
  Solo el diálogo REPL completo en prosa (`C18`) queda diferido; su contrato API/sesión pasa a
  `T13` y la vista mínima del circuito y de las señales sigue siendo requerida en *Operación y vista* (§1).
- **Línea `W2` (`C21–C28`):** axiomas de orden estricto y total (`C21`), enrutamiento por término medio
  objeto→sujeto (`C22`), `shape_guard` lógico (`C23`), ensamblado hipertensor/partición (`C24`,
  diferido como opt-in bajo `memcap` sin obligar matriz global `p×p` por decisión 26), esquemas de
  reglas con variables y grounding finito (`C25`), reflexión controlada `rel:`/`ctx:` (`C26`), emisión
  determinista desde instancia (`C27`) e importadores OWL/UNL/legacy (`C28`, diferidos hasta
  seleccionar consumidor).
- **Exclusiones deliberadas (`X01–X05`):** kernels globales legacy, causalidad experimental
  (`appendix_causality`), layout YAML ad-hoc e IDs aleatorios como autoridad, equivalencia textual de
  prosa REPL y defectos conocidos de `W1`/`W2` (`F06`, `F13`, `F14`, `F15`, `F16`, `F17`).

Pendiente de kimun: el adaptador que lee S/M de kimun y escribe hechos G con evidencia en
aristas, y el diseño de esa frontera (decisión 19, `05-estado.md §3`).

## 4. Ecosistema sldb v1

- **v1 sigue en v1** (decisión 18): los consumidores de la tabla de §2 no se migran por ahora
  y kimun no está desplegado, así que sus stores e historial se pueden regenerar. Detalle de
  la decisión: `05-estado.md §3`.
- **Pendiente en el repo de sldb v1** (su propia inbox): el store `.sldb` no se versiona; hay
  que sacar shards e índice de git (`f9047b5`).
- `~/.sldb` ya no es un inventario de decenas de stores enlazados.
