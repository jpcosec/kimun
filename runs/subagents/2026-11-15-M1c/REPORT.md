# M1c — motor de selectores EDN (modo legacy)

Nota del coordinador: el ejecutor no pudo escribir este archivo (la herramienta se lo
bloqueó); su informe llegó por mensaje y se resume aquí.

- `src/kimun/surface/models/selector.cljc` (nuevo): `extract-selector steps text`; el
  intérprete se movió sin cambios desde `extract.cljc`, que ahora delega.
- Único cambio de comportamiento: un paso sin `:outer-type` falla con `:models/invalid`
  nombrando el paso (antes no capturaba nada, en silencio). `recipe/recipes` siempre lo pone.
- Dirigidas: 16 namespaces, 119 tests, 1749 aserciones, 0 fallos (`directed.txt`); oracle 0
  diferencias; lint ok.
- Mutación (`mutation.txt`): sin la delegación, 581 fallos y 2 errores (`selector_test`,
  `parity_test`, `corpus_test`, `index.corpus_test`); `blocks_parity_test` y
  `docs_parity_test` no pasan por ese camino y no la detectan. Sin la validación del paso,
  falla su prueba.

## Límites

- **Sin prueba del quirk de `TaskDoc`** que cita el spec 16 (l. 182): no se reprodujo con
  documentos mínimos (unas 25 variantes, comparadas a mano contra el `extract.cljc` original:
  coinciden). Los dos documentos reales que lo producían no están en el repo.
- El quirk de `AtomDoc` se probó como «un campo requerido se captura aunque falte su sección»,
  no como «captura desde el principio del documento» (spec 16 l. 178): lo medido es que
  captura desde donde terminó el último bloque alineado. Imprecisión del texto del spec.
- Qué pasa si a un paso le falta `:outer-type` no lo dice el spec (l. 127): es decisión del
  ejecutor.
