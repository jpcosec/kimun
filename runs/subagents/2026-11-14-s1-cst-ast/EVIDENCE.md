# S1 — Restauración de código verde (`65ca4b5f`), medición pod vs fallback y limitación

## Estado restaurado

Se restauraron `src/kimun/surface/markdown/cst_ast.cljc`, `test/kimun/surface/markdown/cst_ast_parity_test.cljc` y `test/fixtures/markdown/freeform_divergences.md` exactamente al estado de `65ca4b5f`, dejando `src/kimun/surface/markdown/ast.cljc` intacto (con `*parser*`, `requested-parser` y `effective-parser` de K6).

## Medición directa sobre `65ca4b5f` (pod vs reconciliación vs fallback)

Medido sobre los `735` documentos reales (corpus de paridad + desk) y una muestra de `2,000` documentos de `gen-ast`:

| conjunto | docs `:pod-all-blocks` | docs `:pod-with-reconciled-blocks` | docs `:fallback-s2-error` | docs `:fallback-nested-has-error` | bloques pod vs reconciliados |
|---|---|---|---|---|---|
| Corpus + desk (`735` docs) | `735` (`100.0%`) | `0` (`0.0%`) | `0` (`0.0%`) | `0` (`0.0%`) | `3,292` pod (`100.0%`) / `0` reconciliados (`0.0%`) |
| `gen-ast` (`2,000` docs) | `1,185` (`59.25%` total; `97.37%` sin error) | `32` (`1.60%` total; `2.63%` sin error) | `620` (`31.00%`) | `163` (`8.15%`) | `2,446` pod (`98.08%`) / `48` reconciliados (`1.92%`) |

## Nota de limitación arquitectónica y alcance

- **Por qué `65ca4b5f` queda como línea base verde:** en el corte alternativo quirúrgico (`coalesce-list-spans` + `blocks-of`), la suite de pruebas de superficie falló en `plan-test/golden-outside-profile` bajo `KIMUN_PARSER=treesitter` (en `test/fixtures/markdown/outside-profile.md`, `<span>html inline</span> en párrafo.` es `"paragraph"` para tree-sitter —produciendo `"markdown/unparsed"`— pero `:html` según regla 7 de `docs/v2/04 §3.1`), además de requerir un caso más específico para disparar `:has_error` en lista. Restaurar `65ca4b5f` mantiene `100%` verde en las pruebas de perfil.
- **Limitación conocida de `reconcile-blocks` en `65ca4b5f`:** aunque en ejecución normal acepta los nodos del pod en `3,292/3,292` bloques reales (`100%`) y `2,446/2,494` bloques generados sin error (`98.08%`), para decidirlo evalúa `profile-block-at` en cada bloque tope, replicando las reglas de corte de `cst/parse`. Si en un paso posterior se quiere eliminar esa evaluación por bloque sin romper `golden-outside-profile`, habrá que cubrir además el mapeo `"paragraph"` → `:html` (regla 7) además de `9→10` y `:has_error`.
- **No se afirma paridad universal:** `gen-free-md` en `cst_ast_parity_test` prueba 200 casos sobre un generador acotado de líneas, no todo CommonMark libre arbitrario.

## Validación verde (`validation.log`)

- **Suite completa aislada de `65ca4b5f` (reportada en coordinación):** `942` tests, `9.883` aserciones, `0` fallos, `0` errores.
- `~/bin/memcap bb lint` → `rings and docstrings ok: 103 files`
- `~/bin/memcap bb oracle` → `9 rows, 0 mismatches`
- `kimun.surface.markdown.*` (7 namespaces: `cst-test`, `inline-test`, `frontmatter-test`, `cst-ast-parity-test`, `roundtrip-test`, `drift-test`, `plan-test`), **default `KIMUN_PARSER`:**
  - `pod-ast-parity`: `735/735 identical (divergent 0, errors 0)`
  - `Ran 25 tests containing 199 assertions. 0 failures, 0 errors.`
- Mismos 7 namespaces bajo **`KIMUN_PARSER=treesitter`**:
  - `pod-ast-parity`: `735/735 identical (divergent 0, errors 0)`
  - `Ran 25 tests containing 199 assertions. 0 failures, 0 errors.`
