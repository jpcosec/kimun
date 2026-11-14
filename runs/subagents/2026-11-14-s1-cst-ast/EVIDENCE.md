# S1 — Cierre de divergencia CST→AST en Markdown libre (`docs/v2/04 §11.2`, `12 §1`)

## Resumen de cambios

Se cerró la divergencia entre `kimun.surface.markdown.cst-ast/parse` (vista sobre el pod tree-sitter) y el oráculo in-process `kimun.surface.markdown.ast/parse-native` sin tocar `src/kimun/surface/markdown/ast.cljc` (preservando `*parser*`, `requested-parser` y `effective-parser` de K6):

1. **Subárboles con `ERROR` anidado (`src/kimun/surface/markdown/cst_ast.cljc`):**
   - `tree-sitter-md 0.5.3` marca `:has_error true` en el ancestro (`list`, `block_quote`, `root`) sin marcar `:error true` en ese nodo cuando un HTML block u otra construcción dentro de una lista produce un nodo `ERROR` interno y corrompe los spans.
   - `unknown?` ahora incluye `(:has_error node)` y `parse` verifica `(or (:has_error root) (some unknown? nodes))` para caer de forma segura al oráculo nativo.

2. **Reconciliación de límites de bloque con el perfil cerrado (`reconcile-blocks` en `src/kimun/surface/markdown/cst_ast.cljc`):**
   - Reemplaza `blocks-of` + `lazy-continuation` por `reconcile-blocks` (`profile-block-at`, `pod-node-block`, `trim-end-blanks`), consumiendo el nodo del pod cuando su span `[i j)` (sin blancos finales fuera de código/frontmatter) y su `:kind` coinciden con las reglas de `docs/v2/04 §3.1`, y re-sincronizando por la extensión del perfil cuando CommonMark diverge:
     - Listas ordenadas que cruzan ancho de dígito (`8./9. -> 10.`, donde SLDB-MD mantiene ancho `w = 3` del primer marcador y tree-sitter exige `4` espacios).
     - Fences `~~~` con backticks en el info string (`~~~ \`bad\``, párrafo en SLDB-MD §3.1 regla 2).
     - Citas con continuación perezosa (`> a\nlazy`), marcadores con paréntesis (`1) `), tablas de una línea (`| a | b |`) o comentarios HTML seguidos de línea no blanca (`<!-- c -->\n...`), y bloques `setext_heading` / `indented_code_block` adyacentes a párrafos.

## Bloqueo documentado para cambiar el default de `KIMUN_PARSER`

- `src/kimun/surface/markdown/ast.cljc` no se modificó por coordinación explícita con K6 (`effective-parser` / `parse_contract.cljc`). Conforme a `docs/v2/04 §11.2`, el cambio del valor por defecto de `KIMUN_PARSER` espera a que K6 registre el contrato de parser en el store.

## Archivos modificados / creados

- `src/kimun/surface/markdown/cst_ast.cljc`
- `test/kimun/surface/markdown/cst_ast_parity_test.cljc`
- `test/fixtures/markdown/freeform_divergences.md`

## Validación ejecutada (`validation.log`)

Comandos bajo `~/bin/memcap` (ver salida completa en `runs/subagents/2026-11-14-s1-cst-ast/validation.log`):

- `~/bin/memcap bb lint` → `rings and docstrings ok: 103 files` (exit 0).
- `~/bin/memcap bb oracle` → `9 rows, 0 mismatches` (exit 0).
- Suite Markdown completa (`cst-test`, `inline-test`, `frontmatter-test`, `cst-ast-parity-test`, `roundtrip-test`, `drift-test`, `plan-test`) con `KIMUN_PARSER` por defecto:
  - `pod-ast-parity`: `735/735 identical (divergent 0, errors 0)`, `by class: {}`.
  - Total: `Ran 25 tests containing 199 assertions. 0 failures, 0 errors.`
- Misma suite Markdown con `KIMUN_PARSER=treesitter`:
  - `pod-ast-parity`: `735/735 identical (divergent 0, errors 0)`, `by class: {}`.
  - Total: `Ran 25 tests containing 199 assertions. 0 failures, 0 errors.`

Nota de alcance: un intento previo de correr `bb test` completo sobre todo el repo fue cancelado al observar fallos en `kimun.cli.diagnostics-test` originados por cambios concurrentes en curso en `src/kimun/surface/diagnostics.cljc` / `src/kimun/cli/commands/stores.cljc` (fuera del alcance de S1).
