# SPEC15 — Reporte

Tarea: **escritura de spec**, no de código. Worktree `/home/jp/proyectos/kimun-worktrees/spec15`,
rama `close/spec15`. Sin `bb` de implementación; los únicos comandos `bb` fueron lectura de los
fixtures EDN, `bb lint` y `bb test`, todos con `memcap` (default, ningún `memcap 6`).

## Cambios

- **Nuevo** `docs/v2/15-capa-g.md` (la capa G), 13 secciones + anexo.
- **Nuevos** fixtures EDN en `test/fixtures/g/`: `vocabulary.edn` (la `L_i`), `context.edn`
  (nodo + árbol `W_i`), `facts.edn` (tres hechos con aristas `binding`/`projection`),
  `conflict.edn`, `decision.edn`. Los cinco parsean con `clojure.edn`.
- `docs/v2/README.md`: `15` en la tabla de documentos y en el nivel de autoridad 6, anotado
  "por debajo de `14`".
- `docs/v2/12-ecosistema-y-pendientes.md §1`: los pendientes de "Capa G" y "lenguaje de consulta"
  remiten al diseño de `15` (quedan como implementación, etapa A/C); `12 §3` remite a `15 §7`
  para el adaptador S/M→G.

## Índice del 15

1. **Qué es un hecho en kimun** — `:fact/:triple` con `R/A/B` ids de `:symbol/:term`, la
   proposición M, taxonomías `terms`/`relations`/`literals`, y el vocabulario `L_i` exacto.
2. **Contextos `W_i`** — nodo + árbol `:context`, `:granularity`/`:of` (paths, no ids nominales),
   contexto por defecto = documento `source`, ruteo promovido, transversales de primera clase.
3. **Afirmación, verdad y sentido** — `:status`/`:tier`/`:prov` en la evidencia de `projection`;
   `V`/`S` índice derivado; reglas duras (nada `TRUE` sin decisión, `unsinnig` humano, promoción
   solo por política declarada).
4. **Escritura** — `facts.log.edn` (una línea por hecho) y su consolidación idempotente en
   `TransactionPlan`; decisiones humanas como documentos.
5. **Conflicto lógico** — forma del informe, caché fuera del pool, quién lo produce, tres
   resoluciones como transacciones, `:ambiguous` con candidatos.
6. **Anchors de hechos** — `prov` → `:sign/:span`, mapeo code points → hoja+grafemas, estado del
   hecho = peor de su cadena, sin descarte silencioso.
7. **Protocolo `propositions`** — extensión del protocolo 1, forma JSON del FACT, determinismo,
   `--describe`, conversión a plan, validación y exits.
8. **Consulta y evaluación en dos etapas** — `index/graph/` derivado, esquema de datoms, motor
   (semi-naive in-process estable; Datalevin opcional con sus límites medidos), matrices por
   `W_i`, contrato `{:answer :truth :sense :justification :citations}`.
9. **CLI** — grupos `facts`, `contexts`, `ask`, `conflicts`, `graph query|rule`, con exits
   congruentes con `06 §3`.
10. **Promesas → tests** — tabla de 21 filas (todas `deferred` hasta la implementación).
11. **Qué revisa** (`02 §2.1`, `§3.2`, `§6.2`, `§10`; `11`; `12`) y qué **no toca** (`04`, `07`,
    `10`, `13`, `02 §5`, `02 §6.5`, decisión 39).
12. **Preguntas al dueño** — Q1–Q7 (abajo).
13. **Anexo** — fixtures.

## Preguntas al dueño (Q1–Q7, con default)

| # | pregunta | default |
|---|---|---|
| Q1 | `:proposed` dentro de `:status` (14 §5 vs §7/31) o campo `:curation` aparte | ampliar `:status` |
| Q2 | cuándo se consolida el log de hechos (decisión 33) | automático en acción y al abrir, informando pendientes |
| Q3 | formato de los documentos de decisión (27/Q6) | EDN siempre |
| Q4 | precedencia de las políticas de promoción (decisión 31) | store > contexto |
| Q5 | jerarquía de `R/A/B` (14 §4) | posiciones del árbol (derivable), `hierarchy` como caché |
| Q6 | aristas de ruteo `equivalent`/`projection` (14 §3) | `semantic` con `:relation` en evidencia |
| Q7 | declaración del vocabulario `L_i` (dueño `13`) | `:vocabularies` en `kimun.include.edn` |

## Dudas de interpretación del 14 (con cita)

1. **`projection` como tipo, sobrecargado.** `02 §3` define `projection` M→G ("este símbolo se
   consolidó en este hecho"); `14 §3` y `§5` usan `projection`/`equivalent` para el ruteo entre
   contextos y para la afirmación. Las separé por extremos: `projection` sigue M→G con el
   `:status` de afirmación (`14 §5`), y el ruteo se propone como `semantic` con `:relation` (Q6).
2. **`:status` con dos vocabularios.** `14 §5` fija `:status ∈ {observed confirmed rejected
   retracted}`, pero `14 §7` y la decisión 31 exigen que la salida de un motor sea `proposed`;
   `02 §3.2` usaba `:status ∈ {sinnvoll sinnlos unsinnig}` (sentido). Resolví: `:status` =
   decisión (con `:proposed`, Q1), `:tier` = nivel, y el sentido pasa al índice `V`/`S`
   derivado; **revisa** `02 §3.2`.
3. **`:object <id|literal>` (02 §2.1) vs "R/A/B ids" (`14 §4` y el brief).** Lo estreché a ids;
   un literal es un término de `literals` con `:name` canónico. **Revisa** `02 §2.1`.
4. **`:fact/:context` sólo `{:name}` (02 §2.1) vs contextos finos/gruesos (`14 §3`).** Extendí el
   contenido con `:granularity`/`:of`; por decisión 27 (`14 §10`) el `:of` referencia paths y
   spans, nunca ids nominales de árbol (si no, un `rebuild` cambiaría los contextos y los hechos).
5. **`Prov` de wittgenstein.** `provenance.md §1` guarda `prov` **dentro** de `Fact.evidence`;
   `12 §3` y `14 §5` la quieren en **aristas** de kimun. La puse en la evidencia de `binding`
   (S→M) y `projection` (M→G), y dejé la cadena como ancla del hecho.
6. **Ancla S→G inexistente.** `14 §7` pide "aristas con evidencia hacia `:sign/:span`" para los
   hechos, pero `02 §3` no define una arista S→G. Lo resolví con la cadena `binding` S→M +
   `projection` M→G (confirmada por `atom-w-i-context-node-and-index`) y el estado del hecho =
   peor de su cadena (§6). Es la interpretación con menos formas nuevas.
7. **`02 §6.2` "M/G resuelven por existir en el CAS".** Es la concesión de los hitos 0–6 por los
   árboles `:context`. `15 §11` propone endurecerla para G (pertenecer al árbol `:context`), que
   es el hito 7; no lo apliqué retroactivamente.
8. **`unsinnig` humano vs `S` derivado.** Si `S` es índice derivado (`14 §5`), un `unsinnig`
   humano no puede vivir en el índice. Lo materialicé como decisión registrada (documento, §4) y
   el índice lo recalcula.

## Evidencia

```
bb lint  -> rings and docstrings ok: 98 files
bb test  -> {:test 886, :pass 9552, :fail 0, :error 0, :namespaces 109}
fixtures -> vocabulary.edn, context.edn, facts.edn, conflict.edn, decision.edn: OK (edn/read-string)
```

Todo con `memcap` (3 GB); ningún run murió por memoria.
