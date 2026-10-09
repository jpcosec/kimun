# SPEC15 — Borrador de `docs/v2/15-capa-g.md` (la capa G: hechos, contextos, verdad, conflicto, consulta)

Tarea de ESCRITURA DE SPEC, no de código. Worktree `/home/jp/proyectos/kimun-worktrees/spec15`,
rama `close/spec15` desde `main`. Lee primero `/home/jp/.cache/coord-briefs/close-common.md`
(si corres algo, con `memcap`).

## Insumos obligatorios (léelos enteros antes de escribir)

- `docs/v2/14-juego-del-lenguaje.md` completo: §1 (P1–P12), §3–§8, §11, §12 (decisiones 21–39),
  §16 (P13–P15), §17 (respuestas del dueño). El 15 **despliega** el 14; no lo contradice ni lo
  repite: lo cita.
- `docs/v2/05-estado.md` decisiones 22–26, 30–38, 40. `docs/v2/02-sustrato-computacional.md`
  §2 (formas de nodo: `:symbol/:term`, `:symbol/:proposition`, `:fact/:triple`, `:fact/:context`),
  §3.2 (aristas y evidencia), §6 (sucesión y anclaje). `docs/v2/11-adaptadores.md` (protocolo 1).
  `docs/v2/12-ecosistema-y-pendientes.md §3` (wittgenstein ↔ kimun, desajustes).
- `desk/atoms/atom-w-i-context-node-and-index.md`, `atom-evidence-required-per-edge-type.md`,
  `atom-canonical-projected-or-asserted.md`, `atom-drifted-is-a-reconciled-orphan.md`.
- Spikes: `/home/jp/.cache/coord-briefs/R1-report.md` (Datalog en bb; Datalevin 1.1.0 y sus bugs;
  evaluador semi-naive in-process), `R3-report.md` §5 (confirma: 1.2.0 no está en el registry;
  in-process es el camino estable).
- wittgenstein: `/home/jp/proyectos/wittgenstein/matrix-shrdlu-spec/specs/{SPEC,matrix,grounding,provenance,convergence}.md`
  (solo lectura): verdad/sentido, tiers, curación, `ambiguous`, convergencia, "nunca p×p".

## Qué debe fijar el 15 (secciones, en este orden)

1. **Qué es un hecho en kimun.** `:fact/:triple {:subject :predicate :object :context}` con
   `R`/`A`/`B` como ids de `:symbol/:term` que ocupan posiciones en árboles de taxonomía (14 §4,
   decisión 23). Cómo se declara el vocabulario `L_i` de un contexto (documento `source` EDN:
   relaciones admisibles, ejes, propiedades algebraicas por contexto). Formas EDN exactas y qué
   entra al hash.
2. **Contextos `W_i`.** Nodo `:fact/:context` + árbol-índice (ya existe). Contexto por defecto =
   documento `source` (decisión 22). Cómo se crea, cómo se nombra, cómo un hecho lo referencia,
   cómo se declaran contextos más finos/gruesos por instrucción. Ruteo entre contextos: aristas
   `projection`/`equivalent` promovidas (nunca por string). Transversales de primera clase =
   pack mínimo de autodescripción (decisión 34): defínelos como contextos/taxonomías concretos
   con nombre y forma.
3. **Afirmación, verdad y sentido** (decisión 24). `:status ∈ #{observed confirmed rejected
   retracted}` en la evidencia de la arista `projection` hecho→origen, un origen exacto
   (`:actor` o `:engine`+`:version`), tiers. `V`/`S` por `(hecho, W_i)` como índice derivado.
   Reglas: nada pasa a `TRUE` sin decisión registrada; `unsinnig` solo humano; promoción
   automática solo por política declarada del store/contexto (decisión 31, harness).
4. **Escritura: revisión por acción + log de hechos en flujo** (decisión 33, Q1=b). Forma del
   log de hechos (append-only, una línea por hecho con procedencia), cómo y cuándo se consolida
   en una revisión (`TransactionPlan`), qué pasa con hechos del flujo no consolidados al abrir el
   store. Decisiones humanas materializadas como documentos (decisión 27/Q6) vía composición
   grafo→documento: forma mínima del documento de decisión.
5. **Conflicto lógico** (14 §6, decisión 25). Forma del informe, dónde vive (fuera del pool),
   quién lo produce, las tres resoluciones como transacciones, comportamiento de consultas sobre
   un `W_i` con conflicto abierto (`:ambiguous` con candidatos).
6. **Anchors de hechos** (14 §7, decisión 32). Procedencia `prov {path source_hash span
   extractor confidence}` → aristas con evidencia hacia `:sign/:span` sobre hojas; mapeo de
   chunks/code points de wittgenstein a hoja+grafemas; qué pasa con un hecho cuando su hoja cambia
   (intact/orphan/drifted; nunca descarte silencioso).
7. **Protocolo `propositions`** (14 §11, extiende `11`): sobre JSON, FACT para proposiciones,
   determinismo, `--describe`, códigos de salida; cómo un motor (descompositor heurístico/LLM,
   Matrix, embeddings) emite hechos y cómo kimun los convierte en plan.
8. **Consulta y evaluación en dos etapas** (14 §8, decisiones 26, 30). Índice G derivado en
   `<store>/index/graph/` (reconstruible por cabecera): esquema de triples/datoms con procedencia;
   motor: evaluador Datalog semi-naive in-process como camino estable, Datalevin opcional
   (documenta sus límites medidos). Etapa 2: `W_i` → matrices booleanas por relación (`V_i`,
   `S_i`, `I`), cierre según propiedades de `R` en ese contexto; nunca `p×p` global; Matrix como
   motor externo por protocolo. Contrato de respuesta
   `{:answer :truth :sense :justification :citations}`; nada se persiste salvo `assert`.
9. **CLI** (`06` es el dueño del inventario; aquí solo la propuesta): grupos `facts`, `contexts`,
   `ask`, `conflicts`, `graph query|rule`; envelope y exit codes consistentes con `06 §3`.
10. **Promesas → tests** (formato de `tests/promises.md`): una tabla con cada enunciado normativo
    del 15 y el test que lo rompería.
11. **Qué revisa** de `02 §2` (formas nuevas o extendidas), `11`, `12 §3`, y qué NO toca.
12. **Preguntas al dueño**: lista numerada, cerrada, con opciones y default (mismo formato que
    `14 §17`), SOLO para lo que el 14 no haya decidido. No decidas tú lo que es del dueño.

## Reglas de escritura

- Español, mismo tono y densidad que `14` y `02`. Formas EDN exactas, no prosa vaga. Cita el
  documento y sección de donde sale cada regla. Nada de "podría"; o es regla o es pregunta.
- Nada de código fuente nuevo; sí fixtures EDN de ejemplo (un contexto, su `L_i`, tres hechos, un
  conflicto, una decisión) en `test/fixtures/g/` como anexo del spec.
- Registra el 15 en `docs/v2/README.md` (tabla de documentos; autoridad: nivel 6, dueño de su
  tema, debajo de `14`) y en `12 §1` quita/ajusta los pendientes que el 15 cubre.
- Un commit (o pocos), trailer `Co-Authored-By: Claude Fable 5.1 <noreply@anthropic.com>`. Sin push.

## Reporte

`/home/jp/.cache/coord-briefs/SPEC15-report.md`: índice del 15, las preguntas al dueño, y qué
dudas de interpretación del 14 tuviste (con cita).
