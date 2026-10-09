# SPEC15-R5 — Reporte

Worktree `/home/jp/proyectos/kimun-worktrees/spec15`, rama `close/spec15`. Commit `b46ac4b6`. Sin push. No se tocó `14`.

Archivos: `docs/v2/15-capa-g.md`, `docs/v2/10-escala.md`, `test/fixtures/g/{vocabulary,context,facts,conflict,decision}.edn`, `test/kimun/g/fixtures_test.cljc`.

## Hallazgo → cambio

| # (brief) | hallazgo (A5/B5) | cambio |
|---|---|---|
| 1 | A-1 (bloqueante): la consulta sobre una contradicción interna devolvía un informe sin candidatos | `15 §5` «Consultas»: toda consulta que toca un `W_i` con conflicto abierto devuelve `:kind :ambiguous` con `:candidates` y no elige (`14 §6`, exit 2); en contradicción interna los candidatos son `{:fact <id> :context <W_i>}` y sin `:phi`; al unir contextos llevan `:phi`; el informe de detección sigue pudiendo ser `:kind :contradiction`. `conflict.edn` pasa a `:kind :ambiguous` con candidatos (lo que devuelve `ask`). `§10` promesa renombrada `g/ask-on-open-conflict-returns-ambiguous` y reescrita. `fixtures_test`: candidatos = hechos, con `:context` y sin `:phi`. |
| 2 | A-2: primacía motor/respaldo invertida | `15 §8` «Motor» reordenado: **Datalevin (pod) motor candidato** y **evaluador semi-naive in-process como respaldo offline** (además lo único que corre sin el pod). Primacía de `05` decisión 30 / `14 §8` conservada; no se editó `05`. |
| 3 | A-3: el hecho `sense` no era admisible con el vocabulario declarado | `15 §1`: vocabulario base con taxonomías `facts` (raíz `fact`; el término-hecho designa el `:fact/:triple`) y `sense` (raíz `sense`) con `sinnvoll`/`sinnlos`/`unsinnig`, y relación `sense :axis ["fact" "sense"]`. `vocabulary.edn`, `context.edn` (5 taxonomías y sus posiciones) y `facts.edn` (términos base) actualizados; `fixtures_test` añade `base-vocabulary-admits-sense-marks`. |
| 4 | A-4: el documento de decisión no tenía forma de nodo/perfil | `15 §4`: nodo `:sign/:opaque {:format "edn/decision" :blob …}`, perfil `edn/decision`, análogo a `edn/vocabulary`/`edn/model`; lo lee la superficie (ring 3) vía `kimun.surface.include` → `kimun.surface.decisions` (nuevo). `15 §11`: filas nuevas de revisión de `04` y `13`. |
| 5 | B-1: `propositions` sin camino de ejecución | Punto de entrada `facts plan --adapter NAME ROOT [--write]` en `15 §7` (corre el adaptador, valida, llama a `plan-propositions`), `§9` y filas `06`/`11` de `§11`. |
| 6 | B-2: `resolves?` circular | `15 §6`: un hecho resuelve por (a) `:content :context` → `:fact/:context` en el CAS **y** (b) existir en el CAS; la cadena de anclaje se evalúa aparte por la regla de estado, **excluyendo el extremo del propio hecho**. `§11` fila `02 §6.2` y fila kernel reescritas. |
| 7 | B-3: cierre `I*` en corpus puede crecer `p×p` | `15 §8`: fuera de `:corpus` se materializa por alcanzabilidad; en `:granularity :corpus` `I*` **no se materializa** (bajo demanda por consulta, memoria `O(aristas)`). |
| 8 | A-5: umbral denso sin fuente | `15 §8`: 4096 es **propuesta**; `14 §8` única fuente del veto. `§12` Q9 nueva; `10 §11` marca el default como propuesto. |
| 9 | B-4: promesas de memoria no observables | `§10` reformuladas: no se materializa estructura densa por encima de `:graph/dense-terms`; densa rechazada en `:corpus`; `I*` en corpus no se materializa. |
| 10 | B-5: `:phi` no reconstruible | `15 §5` «Dónde vive»: el caché solo se reconstruye re-ejecutando el motor de convergencia para candidatos no promovidos (`:phi` no vive en aristas del pool). |
| 11 | B-6: chequeo de última `projection` atribuido a `plan` | `§11` fila kernel: el chequeo vive en `kimun.kernel.revision/apply-plan` (tiene el store); `plan` es puro y no ve las aristas activas. |
| 12 | menores A-6…A-10 y resto de B | `:type :ownership` en las posiciones del fixture (`context.edn` + `fixtures_test`); `decision.edn` con `:target {:fact :fact/f3 :edge :edge/p3}`; `10 §11` renombra la clave de versión a `:fact-log/version` (y anida `:fact/entry`); eliminados todos los tags de resolución (`Am*`, `A-H*`, `B-*`, `A-1`) de `15`, `10`, fixtures y `fixtures_test`. |

## Preguntas finales al dueño

Q1, Q2, Q4, Q6, Q8 y **Q9** (nueva: umbral denso `:graph/dense-terms`; default propuesto 4096). Q3/Q5 eliminadas; Q7 remitida a `13`.

## Pruebas

```
$ memcap timeout 600 bb lint
rings and docstrings ok: 98 files

$ memcap timeout 900 bb test
Ran 110 namespaces in 104 s
{:test 893, :pass 10247, :fail 0, :error 0, :namespaces 110}

$ memcap bash -lc 'bb -e "(require (quote kimun.g.fixtures-test) (quote clojure.test)) (clojure.test/run-tests (quote kimun.g.fixtures-test))"'
Ran 7 tests containing 695 assertions.
0 failures, 0 errors.
```

## Lo que no corregí y por qué

- **`14`**: orden del brief (no se cambia). Las resoluciones que citan `14 §6`/`§8` se aplican en `15`.
- **`02`, `04`, `05`, `06`, `11`, `13`**: `15` solo **declara** las revisiones en §11; no se editan los documentos dueños. Solo se editó `10`, dueño del formato en disco (umbral y `:fact-log/version`).
- **`05` decisión 30**: ya dice la primacía correcta (`Datalevin (pod) candidato con respaldo in-process`); no hacía falta tocarla.

## Decisiones de interpretación (para el gate)

- `conflict.edn` se dejó como el **retorno de `ask`** (`:kind :ambiguous` con candidatos) porque el brief pedía ajustarlo junto a `§5`/promesas y `ask` es lo observable; el informe interno `:contradiction` queda documentado en `§5`.
- El término-hecho del fixture es `:term/f2b` (taxonomía `facts`), que designa `:fact/f2b`; no se añadió un hecho `(sense …)` completo (bastaba con admitir el vocabulario y cubrirlo en `fixtures_test`).
