# Evidencia — M1 Spec 16 (`docs/v2/16-modelos-como-subgrafos.md`)

- **Base integrada:** `main` en `790007e2` (tras `T01` y `C2a`).
- **Alcance:** especificación documental `M1` (Spec 16), actualización de índices/backlog, fixture declarativo EDN y test dirigido de contrato; **cero cambios en `src/` y cero especulación o resolución sobre `D0`**.

## Entregables

1. `docs/v2/16-modelos-como-subgrafos.md` — Spec 16 (`§1`–`§7`) con gate de cero contexto:
   - Clases genéricas por perfil vs. clases gobernadas (`14 §18.1`, decisión 41).
   - Separación ortogonal entre **modelo de anclaje** (lectura/selectores) y **modelo de generación** (proyecciones), azúcar observada en el nodo y autoridad `source` vs. `generado` (`14 §18.7–9`, decisión 42, `F10`).
   - Adopción de **patrones EDN sobre substrato `tree-sitter`** (`kimun.host.treesitter`) con cifras medidas de `runs/research/2026-10-08/R3-report.md` (`428/428` = `100 %` en `100 ms` / `89 MB` RSS vs. `423/428` en tree-sitter/Datalog), las 6 capacidades (`C1–C6`), contrato `K6` (`F08`) y tratamiento `:strict` vs. `:legacy-v1-template` de las 5 discrepancias de v1 (`3× AtomDoc` sin `## Answer`, `2× TaskDoc` con `## Done When` vacío).
   - Herencia de clases como DAG `:base` (`derive`/`isa?`), composición separada de selectores y proyecciones e inmutabilidad de contrato (`14 §18.2–3`, decisión 40).
   - Frontera entre paquete grafo (`P1` / `kimun.graph.*`), store estable (`K2a`, `14 §20`) y `<store>/cache/`.
   - Explicitación de decisiones abiertas sin resolver `D0`: `D0`/`A2.1–A2.2` (identidad positiva y continuidad), `A1.1` (sensor `S` ↔ unidad `M`), `F10` (instancias durables para generados antes de `M2`) y elección de quirks v1 en `M2`.
2. `docs/v2/README.md` — entrada para `16-modelos-como-subgrafos.md` en la tabla de documentos.
3. `docs/v2/07-modelos-como-nodos.md` — aviso y enlace en cabecera hacia `16-modelos-como-subgrafos.md`.
4. `docs/v2/12-ecosistema-y-pendientes.md` — fila `M1` actualizada indicando `spec 16 redactado` en `16-modelos-como-subgrafos.md` y manteniendo `implementación ← P1, K1/D0`.
5. `test/fixtures/models/spec16_examples.edn` — fixture declarativo EDN (`PrimitiveDoc`, `ChecklistDoc`, `ResumenDoc`, `ResumenTecnicoDoc`, `ResumenDigestView`), capacidades `C1–C6`, cifras R3, casos de discrepancia `R3` y declaraciones explícitas de `:open-decisions`.
6. `test/kimun/spec16_models_contract_test.cljc` — 4 `deftest` dirigidos (`278` aserciones) sobre esquema, jerarquía `make-hierarchy`/`derive`/`isa?`, composición determinista de selectores y proyecciones, compatibilidad con `runs/research/2026-10-08/R3-selectors/edn/*.edn` y presencia de secciones/decisiones abiertas en Spec 16.

## Validación final (sobre `HEAD 790007e2`, con pod tree-sitter activo)

- **Suite completa (`~/bin/memcap bb test` en turno exclusivo)** — log completo en `runs/subagents/2026-11-14-m1-spec16/full-test.txt`:
  - `rings and docstrings ok: 103 files`
  - `tree-sitter pod: available`
  - `pod-ast-parity: 735/735 identical (divergent 0, errors 0)`
  - `pod-blocks-parity: 735/735 identical (divergent 0, no-counterpart 0, error 0)`
  - `Testing kimun.spec16-models-contract-test`: `Ran 4 tests containing 278 assertions. 0 failures, 0 errors.`
  - Resultado global: **`{:test 988, :pass 11221, :fail 0, :error 0, :namespaces 121}`** en `156 s` (exit `0`).
  - Notas de skips condicionales del entorno: `SKIP kimun.adapters.treesitter-test: tree_sitter javascript grammar not available (set KIMUN_ADAPTER_PYTHON)`, `SKIPPED kimun.cli.migrate-test/real-store-sample-extracts-as-in-v1` (ruta externa `.sldb` ausente) y `python unittest: Ran 32 tests in 3.082s | OK (skipped=1)`.
- **`~/bin/memcap bb oracle`:** `9 rows, 0 mismatches` (exit `0`).
- **`~/bin/memcap bin/kimun include check --format text`:** `ok: true`, `findings: {:declared-missing 0, :declared-not-in-store 0, :declared-not-tracked 0, :store-tracked 0, :store-undeclared 0, :tracked-undeclared 0}` (exit `0`).
- *(Referencia previa)* `runs/subagents/2026-11-14-m1-spec16/light-checks.log` conserva el chequeo dirigido y verificación de enlaces relativos Markdown previos al rebase sobre `790007e2`.
