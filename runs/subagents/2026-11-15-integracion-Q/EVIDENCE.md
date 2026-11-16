# Integración Q — todo el Python y el subsistema de adaptadores, borrados

Corte `sin-python` (evidencia propia en `runs/subagents/2026-11-15-sin-python/`), validado
sobre `58684fce` con pod: `bb lint` ok; `bb test`: `{:test 1216, :pass 12950, :fail 0, :error 0, :namespaces 153}`; `bb oracle` 9 filas, 0
diferencias; `include check` ok. Después se rebasó sobre un commit que solo cambia un
borrador de `docs/v2/drafts/`.

165 archivos borrados, unas 10.500 líneas. El ejecutor borró también `migrate --from-v1`
(quedaba inoperante sin el exportador en Python), que el coordinador había dicho al dueño que
se conservaba: queda dicho aquí y se le informó. Quedan rotos a propósito, fuera de la suite,
`scripts/parity/compare_store.clj` y dos generadores de fixtures de paridad.
