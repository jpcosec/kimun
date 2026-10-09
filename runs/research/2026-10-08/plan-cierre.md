# Plan de cierre de kimun (2026-10-08)

Límite (dueño): AST-first; texto, código y otros archivos como conocimiento; conocimiento = grafo de proposiciones (R A B); núcleo en Clojure. Autorizado a descargar, instalar y decidir.

## Tanda A — en paralelo, cada una en su worktree (rama close/<id>)
| id | tarea | archivos principales | est. |
|---|---|---|---|
| A1 | contrato de documentos superseded: leer bajo el contrato con que se extrajo | src/kimun/surface/docs.cljc, diagnostics.cljc, tests | 60 min |
| A2 | field-add de opcional no reasigna el último hueco; descripción obligatoria por campo (+ --description) | src/kimun/surface/models/{edit,descriptor}.cljc, cli models, fixtures | 90 min |
| A3 | aristas y export de grafo ven los stores enlazados (ids calificados) | src/kimun/surface/edges.cljc, cli edges/graph | 60 min |
| A4 | kernel: packfiles, índice persistente por nombre, informe de inalcanzables/GC | src/kimun/kernel/store, host/fs_store, upgrade | 180 min |
| R1 | spike: motor Datalog/triples en bb (datascript, datalevin pod, otros) | solo /tmp + informe | 90 min |
| R2 | spike: pod de tree-sitter (C) para Markdown y código; diff contra corpus | solo /tmp + informe | 120 min |

## Tanda B — specs (tras R1, R2), en paralelo, docs
| id | spec | est. |
|---|---|---|
| S1 | 14-modelos-programables: derivados de render y extracción en SCI; modelo como lente sobre el AST; import multi-lenguaje (JSON Schema ⇄ descriptor) | 60 min |
| S2 | 15-capa-g: proposiciones (R A B) desde AST, modelos, reglas y adaptadores (Matrix como plugin), evidencia, motor de consulta elegido en R1 | 60 min |
| S3 | 16-parser: parser único de M (tree-sitter pod o kernel), motor de modelos sobre el árbol del kernel | 45 min |
| G  | gate de cero contexto sobre S1–S3 (lanes frescas) | 30 min |

## Tanda C — implementación de S1–S3 (worktrees)
## Tanda D — resto: selfdoc (before-image, chequeo kgdb), rutas HTTP de graph_ui, cliente Python, embeddings (adaptador + índice derivado), los 213 documentos del desk sin round-trip, docs finales

## Giro 2026-10-08 (tras el análisis del store)
- Nuevo spec `docs/v2/14-juego-del-lenguaje.md` (borrador estable): autoridad por clase, contextos, verdad/sentido, conflicto lógico, formato 3 del store, modelos como subgrafos, etapas S/A/C.
- A4 (packfiles/GC) **pausado**: comprimía copias que el formato 3 elimina. Su reporte queda como medida de referencia.
- La tanda B arranca por la **etapa S** del 14 (formato 3 + parser M por pod tree-sitter, R2 fases 1–2). 15 (capa G) y 16 (modelos como subgrafos) cuelgan del 14.
- R1: Datalevin pod único Datalog que corre en bb; respaldo semi-naive in-process. R2: pod tree-sitter viable, paridad 1099/1099 tras normalizar `\n`.

## Tanda A cerrada en main (2026-10-08)
- Mergeados en serie: A1 (647e9253), A3 (0bda6f1a), A2 (8d5b098c) + ajuste 00ecaf7a (test de A2 bajo la semántica de A1, decisión 40).
- Conflictos: solo ficheros de brechas/docs (05, 12, parity-v1, parity-gaps.edn, known/closed_gaps_test). Estado final: 518 portadas, 0 brechas, 1 corpus + 1 abierta (selfdoc).
- A4 pausado (su "formato 3" con :names/packfiles/GC queda superado por 14 §10; rescatar el empaquetado binario en streaming cuando toque).
- Pendiente inmediato: suite completa de main → push → despachar S1 y S2 (briefs listos en ~/.cache/coord-briefs/S{1,2}.md).

## Etapa S (2026-10-08, noche)
- S2 mergeado en main: pod tree-sitter en `pods/treesitter/` + `kimun.host.treesitter`; bloques por el pod (1099/1099 kinds+spans); vista SLDB-MD sobre el CST (`KIMUN_PARSER=treesitter`, AST 1099/1099) con default `native` por ~3 % de divergencias en fuzz (listas anidadas, fences indentados). Fase 3 (anclas por bytes/ids del CST) y código como superficie de modelos: pendientes (`12`).
- S1 (formato 3) en curso en `close/S1`; mergear tras S2 (debe integrar main).

## S1 entregado (2026-10-08 20:40) — y lo que falta (S1b)
- Hecho: sin copia verbatim; `:contract` en la arista root→external; log `{:id :revision}` (plan solo en CAS); `include build` = una revisión (`compose-plans`); `inline` por caso; `stores upgrade 2→3` en el sitio; `bb store-size`; docs 10/04/06/13/05/12/promises.
- Medido: 28,3 MB → 8,7 MB aparente (119 → 56 MB disco); 556 → 1 revisiones; build 38 → 13 s.
- NO cumple la promesa "< desk (433 KB)". Causas: (1) paso 2 (árbol como caché) sin hacer: tree-object 1,65 MB + nodos ~0,55 MB siguen en CAS; (2) el plan resuelto del build (4,7 MB) embebe el contenido de los nodos (`:add-node :node {...}`) = tercera copia del texto; (3) `edge/reference` subió 290 KB → 701 KB por meter `:field-hashes` en la evidencia; (4) EDN hex sin empaquetar: 4 KB por objeto en disco.
- S1b (pendiente, tras etapa A o antes si el dueño quiere la promesa ya): (a) plan resuelto referencia nodos por id (`:add-node :id`), contenido una sola vez en CAS → plan ~1 MB; (b) paso 2: árbol de documento como caché verificable; el texto viejo de un documento con contrato movido se recupera de git (historia delegada); `ast->update-plan` diffea contra el árbol reconstruido; (c) `stores update` compuesto en una revisión; (d) empaquetado binario en streaming (rescatar de A4) para el bloque de 4 KB; (e) revisar `:field-hashes` en evidencia (derivado, no debería ir en la arista).
- La promesa de `14 §14` queda como objetivo de S1b; `tests/promises.md` no debe declararla cumplida.

## R3 (2026-10-08 20:58) — lenguaje de selección
- Recomendación: patrones EDN (el recipe del template formalizado) = selector; paridad 428/428 con extract v1, 100 ms/428 docs, portable como datos. tree-sitter = substrato M (spans, section/list/pipe_table, #match?) cuando se necesite anidamiento/tabla. Datalog = solo G (decisión 30).
- Hallazgo: ninguna vista trae posiciones + anidamiento + tabla a la vez (v1 aplana listas; AST neutro sin spans y tabla opaca; CST tiene todo). El selector declara su substrato.
- Decisiones pendientes del dueño para el spec 16: (1) substrato del selector (vista v1 con spans vs CST); (2) los 5 quirks de v1 (AtomDoc sin `## Answer`, TaskDoc `## Done When` vacío): replicar o corregir con decisión registrada (R3 recomienda corregir).
- Datalevin: el pod 1.2.0 NO está en el registry; 1.1.0 tiene bugs en reglas sembradas y tras `clear`. El evaluador semi-naive in-process es el camino estable.

## 2026-10-08 21:10 — S1b y SPEC15 entregados
- S1b: plan resuelto por id (promesa cumplida: 0 `:add-node` con contenido), `stores update` en una revisión; −0,68 MB (8,75 → 8,07 MB); tx/revision ids cambian (fixture tx-001 recomputado), ids de nodo/arista/árbol intactos (oracle 9/9). El plan sigue en 4 MB por las aristas con `:field-hashes` (S1c/decisión).
- SPEC15: `docs/v2/15-capa-g.md` + `test/fixtures/g/`; 7 preguntas (Q1–Q7) y 8 dudas de interpretación del 14 (sobrecarga de `projection`, dos vocabularios de `:status`, `:object` literal, `:fact/:context` extendido, Prov en aristas, ancla S→G por cadena binding+projection, 02 §6.2, `unsinnig` humano como decisión). Gate de cero contexto lanzado (carriles A coherencia, B ejecutabilidad). No mergear hasta gate + respuestas del dueño.

## 2026-10-08 21:20 — S1b en main; gate del 15 ABIERTO
- S1b mergeado (69dedd90) y pusheado; store de main: 8,07 MB aparente, 55 MB disco, 1 revisión.
- Gate 15 ronda 1: A) bloqueantes: `:properties` de R duplicado con hechos; fixture de árbol de contexto fuera de `02 §3.1`. Graves: resolución (c) sobre anchor, arista de ruteo `semantic` G↔G no declarada en Revisa, Q3 ya decidida, `source_hash` dos formatos, fixtures (source.md, aliases, ejes). B) bloqueante: `facts.log.edn`/`index/graph/` son forma en disco (dueño `10`); graves: `p×p` por W_i :corpus, invariante hecho↔projection sin chequeo, promesas incompletas, `:upstream` fuera de `06`.
- SPEC15-R1 lanzado (corrige todo sin decidir por el dueño). Luego re-gate (ronda 2).
- Pendientes del dueño: lista 1–15 enviada (15 Q1–Q7 menos Q3; interpretaciones 8–12; spec16 13–14; S1c 15).

## 2026-10-08 21:30 — gate 15 ronda 2 ABIERTO (convergiendo) → R2
- A2: bloqueante H1 (contexts define creaba hechos de propiedad); graves H2 (source_hash sha256 vs sha-256 entre 02 y 11), H3 (binding sin :prov); menores H4–H10. B2: bloqueante 1 (candidatos = cruce de ejes ⇒ p×p); graves 2 (source_hash), 3 (kernel.plan), 4 (assert no reconstruible), 5 (anchor/resolves? necesita texto de hoja vs formato 3); menores 6–8.
- R2 lanzado con resoluciones fijadas: candidatos = pares que ocurren en hechos (lineal); assert materializa decisión; hojas ancladas por hechos se persisten inline (14 §10); propiedades como hechos con evidencia propia; sha256: en JSON ↔ sha-256: en EDN (mapeo en frontera); :prov en binding.
- R2 hecho (df7260ec); gate ronda 3 lanzado 21:42
- gate ronda 3: A cerrado con menores; B abierto (datom 10§11 vs 15§8; p×p/matrices/cierre mal dicho; ULIDs; plan --write; open; plan-propositions; anillo). R3 lanzado 21:47
- gate ronda 4: ambos ABIERTO con el mismo bloqueante (resolves? endurecido deja hechos orphan); graves: :corpus denso, unsinnig sin lugar en pool, :assert solo con ids, contextos por instrucción no reconstruibles. R4 lanzado 21:58 con resoluciones fijadas (matrices dispersas, umbral denso, Q1 default :curation aparte).
- gate ronda 5: B CERRADO CON MENORES; A abierto (ask sobre contradicción vs 14 §6 — error de mi brief R4). R5 lanzado 22:12.

## 2026-10-08 ~22:30 — gate del 15 detenido tras la ronda 6
- 6 rondas; ronda 6 abrió nuevos bloqueantes (status nil vs 14 §5, ruteo :semantic, caché de conflictos no reconstruible, bitset = cruce). Detenido según lo anunciado: deuda D1–D9 en `15 §14`, banner "BORRADOR NO CERRADO", informes en `runs/gates/spec15/`. Rama `close/spec15` pusheada, NO mergeada.
- Lección: el 15 cambia contratos del kernel (02 §2/§3/§5/§6) y cada ronda encuentra la siguiente capa; conviene cerrarlo con el código (implementación etapa A) y con las respuestas del dueño (Q1, Q2, Q4, Q6, Q8, Q9), no con más rondas de papel.
- 2026-10-09 02:27 S1c lanzado (w2A:pV): registro por documento + caché por hash (14 §18–19).

## 2026-10-09 ~03:30 — S1c en main (formato 4) + fix de journal show
- S1c (3fa932c8): store estable sin contenido; registro por documento (anchor + clase + hash raíz + content-hash en el descriptor), op `:register-tree`, caché por hash en `.kimun/cache/`, resolver `doc-tree`. Store 7,88 → 2,51 MB (12.494 → 4.781 objetos); caché 2,3 MB aparte. Verificado: suite, oracle, marcador, rebuild, borrar caché.
- Fix b4e6be0a: `journal show` fallaba (preexistente desde S1): el mapa vacío no se escribe si la primera revisión tiene contenido; loader lo resuelve como constante, reachable no lo exige ausente.
- Pendiente del store: plan del build en CAS (907 KB), mmap (708 KB), aristas (489 KB); docs sin modelo (`docs ingest`) aún persisten árbol; packfiles (A4) para el bloque de 4 KB; paso 3: mover modelos/grafo a paquete aparte (`kimun.graph.*`) con regla de anillo.

## 2026-10-09 ~15:00 — cierre de la jornada
- Auditoría completa (L1–L4) y correcciones (F1–F4) mergeadas; ramas y worktrees cerrados; spec15/A4 rescatados en runs/research y borrados.
- Revisión de codex (superset de Wittgenstein) commiteada con respuesta; nodos nuevos en 12 §1; D0 (a/b) y D-G pendientes del dueño.
- Bugs: verify enmascarado por el caché (StoredObjects), árboles cacheados por descriptor, test aleatorio inestable, /tmp inundado por corridas ad hoc. Todo con tests.
- origin/main = bd5eb4f7; suite 890/0; oracle 9/9; paridad 518, 0 brechas.
