# S1 — Ajuste quirúrgico CST→AST (`:has_error` + transición `9→10`) y evidencia de uso pod vs fallback

## Veredicto técnico

El recorte quirúrgico en `src/kimun/surface/markdown/cst_ast.cljc` conserva intactos `blocks-of` y `lazy-continuation` sin duplicar `cst/parse`, limitando la intervención exactamente a las dos divergencias observadas en propiedad/Markdown real:

1. **Subárboles con `ERROR` anidado (`unknown?` + `:has_error root`):** `tree-sitter-md 0.5.3` marca `:has_error true` en ancestros (`list`, `block_quote`, `root`) sin propagar `:error true` cuando un HTML block u otra construcción anidada entra en recuperación de error.
2. **Transición de ancho en listas ordenadas (`coalesce-list-spans` / `extend-list-end` / `list-continues?`):** cuando el marcador pasa de `9.` (`w = 3`) a `10.`, CommonMark exige 4 espacios y corta la lista a mitad de ítem; `extend-list-end` solo extiende spans `"list"` que continúan bajo las reglas de ancho del primer marcador (`docs/v2/04 §3.1`) y absorbe/reparsea nodos posteriores dislocados (incluidos fences internos mal cerrados por el corte prematuro).

De esta forma el rol de `tree-sitter` se mantiene íntegro sobre toda la estructura normal: `100%` de corpus+desk y `96.7%` de los documentos `gen-ast` sin error pasan directo por `blocks-of` sobre los nodos del pod (`:pod-direct`), sin re-segmentación global.

## Medición directa: pod directo vs coalescencia vs fallback

Medido sobre `cst_ast.cljc` (corpus+desk de 735 documentos reales + muestra de 2,000 documentos generados por `gen-ast`):

| conjunto | `:pod-direct` | `:pod-with-list-coalesce` | `:fallback-s2-error` | `:fallback-nested-has-error` | divergencias |
|---|---|---|---|---|---|
| Corpus + desk (`735` docs) | `735` (`100.0%`) | `0` (`0.0%`) | `0` (`0.0%`) | `0` (`0.0%`) | `0` |
| `gen-ast` (`2,000` docs) | `1,199` (`59.95%` total; `96.7%` sin error) | `41` (`2.05%` total; `3.3%` sin error) | `614` (`30.70%`) | `146` (`7.30%`) | `0` |

## Alcance, pruebas sin error y brecha documentada

- **Casos sin `ERROR` probados explícitamente por el camino del pod:** `test/fixtures/markdown/freeform_divergences.md` y los casos dirigidos `9→10` y `9→11` en `cst_ast_parity_test/freeform-divergence-classes-parity` verifican `(not (:has_error (first (ts/parse-block s))))` antes de comparar `cst-ast/parse` vs `ast/parse-native`. El caso `- <div class="w">\n  **x**\n  </div>\n  \n  after\n` verifica `(:has_error root)` verdadero y paridad vía fallback.
- **No se afirma paridad 100% en Markdown libre arbitrario:** este corte cierra únicamente `:has_error` anidado y transiciones `9→10`.
- **Brecha detectada en `plan-test/golden-outside-profile` bajo `KIMUN_PARSER=treesitter`:** en `test/fixtures/markdown/outside-profile.md`, la línea `<span>html inline</span> en párrafo.` es parseada por tree-sitter como nodo `"paragraph"` (lo que en `blocks-of` produce `"markdown/unparsed"`), mientras que SLDB-MD regla 7 (`cst/classify`) la clasifica como `:html` (`"markdown/html"`). Se deja documentado fuera de estas dos clases para no reintroducir duplicación de `cst/parse`.
- **`src/kimun/surface/markdown/ast.cljc` intacto:** preserva `*parser*`, `requested-parser` y `effective-parser` de K6.

## Validación (`validation.log`)

- **Suite completa aislada de S1 (reportada por coordinador):** `942` tests, `9.883` aserciones (`0` fallos, `0` errores).
- `~/bin/memcap bb lint` → `rings and docstrings ok: 103 files`
- `~/bin/memcap bb oracle` → `9 rows, 0 mismatches`
- `kimun.surface.markdown.*` (default `KIMUN_PARSER`, 7 namespaces incluyendo `plan-test`): `Ran 24 tests containing 199 assertions. 0 failures, 0 errors.` (`pod-ast-parity`: `735/735 identical`).
- `cst-ast-parity-test`, `roundtrip-test`, `frontmatter-test`, `drift-test` bajo `KIMUN_PARSER=treesitter`: `Ran 13 tests containing 95 assertions. 0 failures, 0 errors.` (`pod-ast-parity`: `735/735 identical`).
