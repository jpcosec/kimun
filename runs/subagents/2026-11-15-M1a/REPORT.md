# REPORT M1a — forma, jerarquía y composición del descriptor spec 16

## Archivos tocados (sin commit; worktree /tmp/kimun-m1a-wt, base ad3b2541)
- NUEVO `src/kimun/surface/models/subgraph.cljc`: `validate!`, `blocked-by`, `index-models`, `hierarchy`, `isa-model?`, `ancestors-of`, `compose-selector`, `compose-fields`, `compose-projections`, `check-composition!`. Puro; requiere solo `kernel.err` y `models.descriptor/type-valid?`.
- NUEVO `test/kimun/surface/models/subgraph_test.cljc`: 6 tests (forma, jerarquía, composición del selector + diamante, restricciones heredadas, proyecciones, bloqueo).
- AJUSTADO `test/kimun/spec16_models_contract_test.cljc`: se quitan los helpers privados (`model-tag`, `ancestors-of`, `build-hierarchy`, `compose-selector`, `compose-projections`) y se usa `subgraph`. Aserciones de `:implementation` cambiadas a `:status :in-progress`, `:slices [:M1a]`, `:code-changes true`, `:blocked-by #{:K1e}` (antes `:blocked`, `false`, `#{:P1 :K1}`), según el brief. `:open-decisions` intacto.
- AJUSTADO `test/fixtures/models/spec16_examples.edn`: bloque `:implementation` igual que arriba.
- `docs/v2/12`: solo la fila M1 (texto M1a + evidencia). `docs/v2/16`: línea «Implementación» en el encabezado y viñeta de diamante en §4.2 (marcada inferencia del equipo).
- No se tocó kernel, docs.cljc, extract, descriptor, store ni cli.

## Números
- `bb lint`: ok, 105 archivos (`lint.txt`).
- Dirigidas (`directed.txt`): subgraph_test, spec16_models_contract_test, store_test (models), semantic_test (surface), doc14_contract_test: 30 tests, 725 aserciones, 0 fallos. Corrida final tras limpiar el contract test: 14 tests/492 aserciones (subgraph, contract, doc14), 0 fallos. Nota: `semantic_test` también existe en `test/kimun/cli/`; corrí el de `surface`.
- `bb oracle`: 9 filas, 0 diferencias (`oracle.txt`).
- Mutación (`mutation.txt`): (1) `git stash push -u -- src/` => subgraph_test no carga (FileNotFoundException), exit 1. (2) Mutación semántica (quitar la deduplicación del ancestro) => 3 fallos en `selector-composition` (A aparece dos veces). Restaurado.

## Qué prueba cada test
1. forma: los 7 modelos del fixture validan; sin `:anchoring`, `:compat :loose` y `:document-role :other` fallan nombrando la clave.
2. jerarquía: isa transitivo y no simétrico; ciclo A→B→A y base ausente rechazados nombrando el nodo.
3. composición: 8 pasos, índices 0..7, Summary en 2 y Complexity en 4; diamante D→(B,C)→A da A una vez, pasos a b c d, índices 0..3.
4. restricciones: `:required` menor o tipo distinto fallan; campo nuevo OK; con `:base []` la redeclaración es válida.
5. proyecciones: la hija hereda, añade y sobreescribe sin afectar a la base; el selector compuesto es `=` antes y después.
6. bloqueo: `#{:F10}` para el generado, `#{}` para `ChecklistDoc`.

## Decisiones del equipo y ambigüedades del spec (para el dueño)
- **Diamante (cada ancestro una vez): NO está resuelto en el spec.** §4.1 (docs/v2/16:198) dice «DAG acíclico serializado y linealizado» (sugiere una linealización, sin decir cuál ni qué pasa con un ancestro repetido); §4.2 (docs/v2/16:204-207) solo da `(into pasos-padre pasos-propios)` y dice «pasos de sus ancestros» sin tratar repeticiones. Implementado: primer alcance, ancestro primero. Alternativas: C3-linealización de Python/Clojure-style, o rechazar diamantes.
- **El fixture manda sobre el ejemplo de §3.4: tampoco está resuelto.** El ejemplo (docs/v2/16:134-158) declara `:base ["PrimitiveDoc"]` pero repite en `ChecklistDoc` los pasos de front matter y h1 (índices 0-1) y lista en `:fields` también `id` (heredado). El fixture (`spec16_examples.edn`, ChecklistDoc) solo lleva los propios. Con el ejemplo, la regla `(into padre propio)` (:205) duplicaría los pasos. El spec no dice si los descriptores guardan solo lo propio o el selector completo.
- **«Unión por clave … cuando usa `:want :find`» (docs/v2/16:205-206)** no está implementado (C3 fuera de M1a); `compose-selector` solo concatena.
- **«Salvo declarando una clase raíz distinta» (docs/v2/16:207)** se interpretó como `:base []`: sin herencia no hay restricción.
- **Qué es «restricción obligatoria» (docs/v2/16:206)**: no se define; implementé `:required` true→no-true y cambio de `:type`. No cubre el regex/pasos del selector ni conflictos entre dos bases distintas de un diamante (solo se compara con la unión de ancestros, el último gana en `inherited`).
- `:mode :sections`, `:sugar`, `:composition`, `:inherits-projections`, `:requires-open-decision`: usadas en el fixture pero no definidas por el spec; se aceptan como claves desconocidas.
- `:contract/digest` (docs/v2/16:139) se acepta e ignora; no se exige.

## Desvíos y límites
- Sin desvíos de las reglas de `validate!` del brief frente al spec/fixture: el fixture pasa entero. Validación de campos no exige que `:type` del fixture `[:list :string]` etc. falle: `type-valid?` lo acepta.
- API: las funciones de composición reciben un índice nombre→descriptor (`index-models`), no una lista; el brief decía `by-name`.
- `validate!` no valida la existencia de bases (eso lo hacen `hierarchy`/`ancestors-of`), ni `:markers`/`:props` de los pasos, ni `:fields` de las proyecciones.
- No ejecuté `bb test` completo ni pruebas con el pod.
