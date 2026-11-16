# Integración H — validación completa de dos cortes apilados

Apilados sobre `013ecd4d` y validados juntos, una vez, en el árbol final:

| corte | evidencia propia |
|---|---|
| M1a-pasos — un descriptor spec 16 puede usar los pasos de selector sobre el CST | `runs/subagents/2026-11-15-M1a-pasos/` (el informe del ejecutor llegó por mensaje; aquí están sus salidas y la reproducción) |
| M1j — una captura lleva coordenadas, sin identidad | `runs/subagents/2026-11-15-M1j/` |

`bb test` con pod: `{:test 1211, :pass 14251, :fail 0, :error 0, :namespaces 149}`; `bb lint` ok; `bb oracle` 9 filas, 0 diferencias;
`include check` ok.

Después de esa corrida este commit añade ambos cortes a la fila `M1` de `12 §1` y dos
ambigüedades del spec 16 a la fila `16-det` de `preguntas-abiertas.md`; sobre ese árbol se
corrieron `bb lint` y las pruebas documentales (`lint-final.txt`, `directed-docs.txt`).
