# Integración J — validación completa de M1g

M1g (documentos de una clase spec 16; evidencia propia en `runs/subagents/2026-11-15-M1g/`)
sobre `39ed8170`. Con pod: `bb lint` ok; `bb test`: `{:test 1231, :pass 14520, :fail 0, :error 0, :namespaces 152}`; `bb oracle` 9 filas, 0
diferencias; `include check` ok.

Después de la corrida este commit añade M1g a la fila `M1` de `12 §1` y dos puntos del spec
16 a la fila `16-det` de `preguntas-abiertas.md`; sobre ese árbol corrieron las pruebas
documentales (`directed-docs.txt`).

No probado por el ejecutor, pendiente de comprobar: `stores update` sobre un documento spec
16 `:superseded`; `include build` con una clase spec 16; `fields schema|query`,
`models list|show` y las vistas HTTP, que leen `:template`.
