# Integración O — validación completa de S2b-r

S2b-r (marcas de rango dentro de un bloque; evidencia propia en
`runs/subagents/2026-11-15-S2b-r/`) sobre `5f0c0a90`, con pod: `bb lint` ok; `bb test`:
`{:test 1272, :pass 15311, :fail 0, :error 0, :namespaces 159}`; `bb oracle` 9 filas, 0 diferencias; `include check` ok. Este commit lo anota en la
fila `S2` de `12 §1`; sobre ese árbol corrieron las pruebas documentales.

El ejecutor se apartó del brief en un punto, con razón: los candidatos de un rango se buscan
en todo el documento y no solo en el mismo bloque (el brief del coordinador, acotado al
bloque, habría hecho que una inserción registrara en silencio la huella de otro texto).

Es el último corte que se podía despachar sin respuesta del dueño: lo que queda de `12 §1`
espera filas de `docs/v2/drafts/preguntas-abiertas.md`, la decisión D-G o RS1/RS2.
