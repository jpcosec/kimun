# Integración P — el adaptador de símbolos en Python, retirado

Corte `sin-adaptador-python` (evidencia propia en
`runs/subagents/2026-11-15-sin-adaptador-python/`) rebasado sobre `93e0069f` y validado ahí,
con pod: `bb lint` ok; `bb test`: `{:test 1270, :pass 14834, :fail 0, :error 0, :namespaces 158}`; `bb oracle` 9 filas, 0 diferencias;
`include check` ok. La suite baja de 1272 a 1270 pruebas: se quitaron las que solo probaban
el adaptador y se añadió una que fija su retiro.

Este commit además registra en `12 §1` y en `drafts/sesion-2026-11-15.md` la decisión del
dueño de suspender la paridad con v1 mientras dure la rearquitectura; sobre ese árbol
corrieron las pruebas documentales.
