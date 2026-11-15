# Integración G — M1i y el registro de preguntas abiertas

| commit | corte | evidencia propia |
|---|---|---|
| M1i | pasos de selector C3–C5 sobre el CST | `runs/subagents/2026-11-15-M1i/` |
| DOC-preguntas | las decisiones que esperan al dueño, escritas en `preguntas-abiertas.md` | `runs/subagents/2026-11-15-DOC-preguntas/` |

- M1i sobre `6cf18730`, suite completa con pod: `{:test 1201, :pass 14214, :fail 0, :error 0, :namespaces 148}`; lint ok; oracle 9 filas, 0
  diferencias; include check ok (`full-test.txt`, `oracle.txt`, `include-check.txt`).
- DOC-preguntas se apiló **después** de esa corrida. Es solo documentación; sobre el árbol
  final se corrieron `bb lint` (`lint-final.txt`) y las pruebas documentales
  (`directed-docs.txt`), en verde. La suite completa no se repitió.
- Este commit añade M1i a la fila `M1` de `12 §1`.

## Rebase sobre `c3b2d150` (decisión 47, integrada por otra sesión)

Mientras corría esta tanda entró en `main` la decisión 47 del dueño (kimun reemplaza el
núcleo de sldb; `C2` pasa de retirada a **diferida**; filas nuevas `RS1` y `RS2`). La tanda se
rebasó encima: un conflicto en la fila «Operación y vista» de `12 §1` se resolvió conservando
el texto de la decisión 47 y añadiendo el avance de la auditoría de `serve`; y la nota de
`preguntas-abiertas.md` que decía «C2 se retiró» se corrigió a «diferida (decisión 47)».

Sobre el árbol rebasado se corrieron `bb lint` y las pruebas documentales más
`selector-cst-test` (`lint-final.txt`, `directed-docs.txt`), en verde. **La suite completa de
M1i corrió sobre la base anterior (`6cf18730`)**; el commit de la decisión 47 solo toca
documentos y `python/README.md`.
