# Integración K — spec16-lectores y M1h, apilados

| corte | evidencia propia |
|---|---|
| spec16-lectores — ningún comando revienta ni calla ante un store spec 16 | `runs/subagents/2026-11-15-spec16-lectores/` (con nota del coordinador) |
| M1h — el contrato de lectura de un documento spec 16 cubre su selector | `runs/subagents/2026-11-15-M1h/` |

Los dos cortes resolvían de forma opuesta qué hace `stores update` con un documento spec 16
`:superseded`; se siguió `10 §11.1` (lo repara) y se retiró el cambio contrario, con su
prueba reescrita. Lo validado es el árbol apilado y conciliado, con pod: `bb lint` ok;
dirigidas 63 tests, 584 aserciones, 0 fallos; `bb test`: `{:test 1244, :pass 14708, :fail 0, :error 0, :namespaces 154}`; `bb oracle` 9 filas, 0
diferencias; `include check` ok.

Después de la corrida este commit anota ambos en la fila `M1` de `12 §1` y la pregunta que
dejan en `preguntas-abiertas.md`; sobre ese árbol corrieron las pruebas documentales.

Sin probar: `include build --rebuild` cuando la clase base cambió entre dos builds;
`GET /stores/check` responde 500 con un documento `:superseded` (no se comprobó si pasa
igual con uno v1); `docs/v2/06` no describe aún los rechazos «spec 16» de
`models field-add|template`, `extract|render|validate`.
