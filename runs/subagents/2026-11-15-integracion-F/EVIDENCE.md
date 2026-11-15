# Integración F — validación completa de M1d

`5548eff9` (M1d, modo `:strict` del selector; evidencia propia en
`runs/subagents/2026-11-15-M1d/`) sobre `2678342c`.

`bb test` con pod: `{:test 1196, :pass 14201, :fail 0, :error 0, :namespaces 147}`. `bb lint` ok; `bb oracle` 9 filas, 0 diferencias;
`include check` ok. Salidas en este directorio.

Abierto para el dueño (texto del spec 16 §3.5): el quirk de `TaskDoc` del punto 2 no se
reproduce; la l. 178 describe mal qué capturaba v1; un ancla compara por tipo y nivel, no
por el texto del encabezado.
