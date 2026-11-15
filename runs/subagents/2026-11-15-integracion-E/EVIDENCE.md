# Integración E — validación completa de dos cortes apilados

Apilados sobre `103a5327` y validados juntos, una vez, en el árbol final:

| commit | corte | evidencia propia |
|---|---|---|
| `0c2964ae` | M1f — descriptores spec 16 en el store y `models list --isa` | `runs/subagents/2026-11-15-M1f/` |
| `926bfdd8` | S1 — auditoría de seguridad de `serve` (solo pruebas y evidencia) | `runs/subagents/2026-11-15-S1-serve-security/` |

Resultado de `bb test` (con pod): `{:test 1192, :pass 14153, :fail 0, :error 0, :namespaces 147}`. `bb lint` ok; dirigidas 37 tests, 1430
aserciones, 0 fallos; `bb oracle` 9 filas, 0 diferencias; `include check` ok. Salidas en
este directorio.

Abierto (decisiones del dueño): los vectores de seguridad no especificados de
`runs/subagents/2026-11-15-S1-serve-security/vectors.md`; si `models define` debe exigir que
las bases existan para un descriptor spec 16.
