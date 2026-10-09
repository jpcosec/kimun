# SPEC15-R4 — Reporte

Worktree `/home/jp/proyectos/kimun-worktrees/spec15`, rama `close/spec15`.
Commit: (ver `git log`). Sin push. No se tocó `14`.

Archivos: `docs/v2/15-capa-g.md`, `docs/v2/10-escala.md`,
`test/fixtures/g/{vocabulary,context,facts,decision}.edn`,
`test/kimun/g/fixtures_test.cljc`.

## Hallazgo → cambio

| hallazgo (A4/B4) | resolución del brief | cambio |
|---|---|---|
| A-1 / B-1 (bloqueante): `resolves?` endurecido a un árbol `:context` que no cuelga hechos | 1 | §2: «El árbol `:context` **no cuelga hechos**»; §6 bullet nuevo «Qué resuelve un hecho (fija A-1/B-1)»: endurecimiento **solo `:symbol/*`**, un hecho resuelve por (a) `:content :context` → nodo `:fact/:context` existente y (b) su cadena `binding`/`projection`; §11 fila `02 §6.2` reescrita |
| B-2 / B-amb 2 (`p×p` en `:corpus`) | 2 | §8 reescrita: representación **dispersa** por `R`/`W_i` (filas = sujetos de `R` en el `W_i`, memoria `O(hechos de R en W_i)`); densa **solo** bajo umbral `:graph/dense-terms` de `store.edn` (default 4096) y **prohibida** para `:granularity :corpus`; cierre por **alcanzabilidad** sobre la forma dispersa (`O(pares alcanzables)`); `V`/`S`/`I`/cierre los calcula el evaluador **in-process ring 0**, Matrix pasa a motor externo **opcional**; §10 filas nuevas `g/candidate-pairs-are-sparse-not-dense` y cierre reescrito; `10 §11` declara `:graph/dense-terms` y `:curation` en `:prov` |
| B-3: `unsinnig` sin lugar en el pool | 3 | §3: la marca es un hecho `(sense <fact-term> unsinnig) @ W_i` con `:status :confirmed`/`:curation :accepted` + documento `:action :unsinnig`; `S` deriva de ese hecho. §1: relación `sense` como **vocabulario base** de todo `L_i`; `vocabulary.edn` la declara |
| B-4 / B-amb 4: decisión `:assert` solo con ids | 4 | §4: `:action :assert` (y todo `--write`) lleva el **`:fact/entry` canónico completo** (términos, proposición, hecho, `binding`, `projection`, spans, hoja); `decision.edn` reescrito a `:action :assert` con `:fact/entry` + `:leaves {path, range, fingerprint}`; `10 §11` documenta esa forma |
| B-5 / B-6: contextos por instrucción no versionados | 5 | §2 y §4: `contexts create` de `:section`/`:corpus`/`:transversal` materializa documento **`:action :create-context`** con el nodo completo y su descriptor; §7: `plan-propositions` **crea** el `:fact/:context` y su árbol en el mismo plan, o falla `:facts/missing-context` si la salida no trae `context` |
| B-7 / B-8: chequeos y `required-evidence` | 6 | §11 kernel: el chequeo «`:object`/hojas de `:form` son términos» vive en `kimun.kernel.plan` (**no** en `node/shapes`, sin pool); se añade `kimun.kernel.edge/required-evidence` (`:tier`/`:curation` en `projection`, `:prov` en `binding`, `:relation`/`:context-target`/`:phi` en `semantic`) |
| B-amb 3: hecho sin proyección | 7 | §1: invariante **del pool**: `:remove-edge` de la última `projection` activa rechazado salvo retractar el hecho en el mismo plan; §10 fila `g/fact-cannot-lose-its-last-active-projection`; §11 kernel |
| A-7 / B-amb 1: default de Q1 revisaba `14 §5` | 8 | §3/§7/fixtures/`fixtures_test`: `:status` = enum de `14 §5`, `proposed` pasa a **`:curation :proposed|:accepted`** (salida de motor siempre `:proposed`); Q1 ahora pregunta si ampliar `:status`; tests verifican `:curation` no nulo y coherencia con `:status` |
| B-9: `ask` sobre contradicción | 9 | §5: sobre contradicción interna, `ask` devuelve `:conflict` con los hechos (**sin** `:candidates`); `:ambiguous` con candidatos **solo al unir contextos**; §10 fila renombrada `g/ask-on-open-conflict-returns-conflict` |
| B-10: promesas no falsables | 10 | §8/§10: «una consulta **funciona sin el pod Datalevin**» (test `g/query-works-without-datalevin-pod`); `--describe` marcado **test de integración de subproceso** |
| A-2 / A-4 / A-5: fixtures | 11 | `vocabulary.edn`: `:tiers` de `es`/`property` incluyen `:statistical`; `facts.edn`: comentario de f3 corregido; `context.edn`: **posiciones `ownership`** de las tres taxonomías; `fixtures_test` añade `facts-tiers-are-admitted-by-their-relations` y verifica posiciones de taxonomía |
| A-3 / A-6 / A-8: menores | 12 | §11 fila `06`: añadido `graph rule`; nueva fila `02 §2` (prosa del sentido revisada); rol `source` citado a `14` decisiones 22/23/27 (ya no a `05` 21) |
| A-4 amb 2 (forma de la proposición) | — | §1: un **hecho** proyecta forma **plana** `(R A B)`; el anidamiento es de otras M |
| A-4 amb 4 (`--strict`) | — | §9: `--strict` es **opción compartida** de los comandos que abren el store, no exclusiva de `facts consolidate` |
| A-4 amb 5 (`:name` fino) | — | §2: `:name "doc:<ruta-del-archivo>#<path-del-bloque>"` (las dos ranuras distinguidas) |

## Preguntas finales al dueño

Q1 (reformulada: `:curation` aparte vs ampliar `:status`; default `:curation`), Q2, Q4, Q6, Q8.
Q3 siguen eliminadas; Q5/Q7 ya decididas o remitidas.

## Pruebas

```
$ memcap timeout 600 bb lint
rings and docstrings ok: 98 files

$ memcap timeout 900 bb test
Ran 110 namespaces in 103 s
{:test 892, :pass 10094, :fail 0, :error 0, :namespaces 110}

$ memcap timeout 300 bb -e "(require 'kimun.g.fixtures-test) …"
Ran 6 tests containing 542 assertions.
0 failures, 0 errors.
```

## Lo que no corregí y por qué

- **`14`**: no se toca (orden del brief).
- **`02`, `05`, `06`, `11`, `13`**: `15` solo **declara** las revisiones en §11 («Revisa»); no se
  editan los documentos dueños (salvo `10`, que es el dueño del formato en disco y donde vive
  `:graph/dense-terms`/`:curation`).
- **`12 §1`** (quitar el pendiente de capa G): edición de implementación posterior, no de spec.
- **`tier` y `status` obligatorios = no nulos (res. 8)**: lo aplico a `:tier` y `:curation`; `:status`
  queda obligatorio-presente y no nulo **cuando hay decisión**, y `nil` en la salida de motor. Motivo:
  el enum de `14 §5` no tiene valor para un hecho sin decidir, y poner `proposed` en `:status` sería
  justo lo que la resolución prohíbe (revisar `14`). Si el dueño quiere `:status` siempre no nulo,
  hay que ampliar el enum (Q1(b)) o admitir que `:status` sea nulo sin decisión; queda anotado.
- **Ambigüedades B no listadas en las 12 resoluciones** (p. ej. `:projection` homónimo, «peor de su
  cadena», `:relations` vs predicates, Datalog «in-process»): sin cambios.
