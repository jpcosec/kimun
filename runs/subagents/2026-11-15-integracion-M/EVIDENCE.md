# Integración M — K1f y K2b-0

| corte | evidencia propia |
|---|---|
| K1f — cierre de unidades de campo y traslado de una unidad a otro documento | `runs/subagents/2026-11-15-K1f/` |
| K2b-0 — medición e inventario del mmap y las tres aristas (sin `src/`) | `runs/subagents/2026-11-15-K2b-0/` |

K1f rebasado sobre `bbcbb442` y validado ahí, con pod: `bb lint` ok; `bb test`: `{:test 1258, :pass 15063, :fail 0, :error 0, :namespaces 157}`;
`bb oracle` 9 filas, 0 diferencias; `include check` ok. K2b-0 (solo scripts de medición y
evidencia) se apiló después; sobre el árbol final corrieron `bb lint` y las pruebas
documentales. Este commit anota ambos en las filas `K1` y `K2b` de `12 §1`.
