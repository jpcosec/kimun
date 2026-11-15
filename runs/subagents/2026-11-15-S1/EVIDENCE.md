# S1 — cierre de la divergencia del CST en Markdown libre

Base: `main` `b6d9541f`. Worktree `/tmp/kimun-s1-wt` (detached). Autoridad:
`docs/v2/04 §11.2`, `12 §1` (S1), `14 §13` etapa S.2.

## Resultado

El ~3 % **no es una divergencia actual**: era la medición de `66ff1e9c`
(`runs/research/2026-10-08/S2-report.md §Fase 2`, 96/3000) con la vista
**pod-driven**. Desde `65ca4b5f` (en `main`, restaurado en `6930afa8`) la vista
solo adopta un bloque del pod si span y kind coinciden con el perfil, así que el
AST es idéntico por construcción; no hay defecto abierto con caso mínimo.

## Medición

`sample.clj` (alfabeto de `gen-free-md` de `cst_ast_parity_test`, seed 1, una
invocación por lote de 100, `memcap 3`, pod construido, RSS ≈ 93 MB):

| muestras | AST idénticos | divergentes | docs con ≥1 celda rechazada | celdas rechazadas |
|---:|---:|---:|---:|---:|
| 500 | 500 | **0** | 239 | 619 |
| 3000 | 3000 | **0** | 1453 | 3798 |

Con la paridad ya medida en corpus + desk (**1099/1099**), 500 libres son
suficientes; las 3000 estaban ya lanzadas y se agregan. Se conserva el resumen
(`aggregate.txt`) y una tabla de casos mínimos por clase extraída de ese lote
(`cases-min.txt`); los logs por lote y el volcado por celda de las 500 no se
commitean (12 KB y 116 KB, regenerables con el script).

Una «celda rechazada» es un nodo del pod que la vista no adopta (span o kind
distinto del perfil). Es el trabajo pendiente de una vista pod-driven, **no** una
divergencia de AST.

## Clases de celda rechazada (500 docs, 619 celdas; 3000 docs, 3798 entre paréntesis)

| clase | regla | 500 | 3000 | caso mínimo | pod | perfil |
|---|---|---:|---:|---|---|---|
| A | §3.1 regla 8, tabla hasta blanco | 47 | 282 | `\|---\|---\|` | `paragraph` (fila delimitadora sin cabecera) | `:table` 0–1 |
| B | §3.1 regla 6, límites de lista | 159 | 966 | `1) paren` | `list` | `:paragraph` (`)` no es marcador) |
| C | §3.1 regla 5, cita | 29 | 182 | `>> q3\na` | `block_quote` 0–2 | `:quote` 0–1 + `:paragraph` |
| D | §3.1 regla 2, info sin backticks | 32 | 174 | `~~~ \`bad\`` | `fenced_code_block` | `:paragraph` |
| E | §11 / §11.1 fuera de perfil | 47 | 297 | `    indented4`, `b\n===` | `indented_code_block`, `setext_heading` | `:paragraph` |
| F | frontmatter con blanco antes del cierre | 10 | 49 | `---\nb\n\n---` | `minus_metadata` | `:thematic-break` en línea 0 |
| G | CommonMark: lista con inicio ≠ 1 no interrumpe párrafo | 22 | 115 | `a\n10. b` | `paragraph` 0–1 | `:paragraph` 0–2 |
| H | §3.1 regla 7, html hasta blanco | 43 | 268 | `<!-- c -->\n- x` | `html_block` 0–1 | `:html` 0–2 |
| I | consecuencia de A/H: el bloque del perfil absorbe líneas donde el pod abre nodos | 230 | 1465 | `\|---\|---\|\n>> q3` | `block_quote` en línea 1 | cubierto por `:table` 0–2 |

Todas son diferencias **documentadas** entre CommonMark (pod) y el perfil cerrado
SLDB-MD (autoridad). Ninguna es un fallo de `cst_ast.cljc`, de `ast.cljc` ni de
`cst.cljc`.

Chequeos de defecto, negativos:

- Ningún tipo de nodo del pod queda sin mapear salvo los dos previstos
  (`indented_code_block`, `setext_heading`, `unknown?` los re-segmenta): 0 casos
  de tipo no mapeado y 0 `ERROR`/`:has_error` en las 500 muestras.
- Ningún bloque adoptado produce un AST distinto (imposible por construcción:
  mismos `:lines` y misma función `from-blocks`), confirmado en 3000 documentos.
- Ningún documento de corpus/desk cambia: la paridad `1099/1099` sigue en verde.

## Cierre documental

El pendiente de `S1` era «cerrar el ~3 %». Se cierra como lectura histórica: no
queda divergencia de AST que reparar. Lo que resta es **cobertura pod-driven**
(reducir las 619 celdas de 500 documentos: clases A–I), que es trabajo de la fase
3 (`S2`, anclas por rangos/nodos del CST) y no de una reparación de divergencia.
`04 §11.2`, `12 §1` y `14 §13` se actualizan con esta medición.

Sin cambios de comportamiento en `src/` ni cambios en `test/`. `sample.clj` es el muestreador reutilizable
(`cells <ini> <n>` imprime cada celda rechazada con sus líneas).

## Validación (slot liberado por K5, 2026-11-15)

| comando | resultado | salida |
|---|---|---|
| `memcap 3 bb lint` | `rings and docstrings ok: 104 files` | `lint.txt` |
| `kimun.doc14-contract-test` + `kimun.lint-test` | 9 tests / 198 aserciones, 0 fallos | `directed-docs.txt` |
| `memcap 3 bb oracle` | `9 rows, 0 mismatches` | `oracle.txt` |
| `memcap 3 bin/kimun include check` | `ok: true`, seis clases a 0, sin warnings | `include-check.json` |

Sin full: el único cambio en `src/` es el docstring de `ast/parse` (texto, sin
comportamiento). Solo existe contrato documental para `14` (DOC1); `04` y `12` no
tienen test propio, así que su corrección se valida con `lint` y revisión.

## Reproducir

```
KIMUN_POD=<pod> memcap 3 bb -cp src:test:resources runs/subagents/2026-11-15-S1/sample.clj 500 100 1
KIMUN_POD=<pod> memcap 3 bb -cp src:test:resources runs/subagents/2026-11-15-S1/sample.clj cells 10 500
```
