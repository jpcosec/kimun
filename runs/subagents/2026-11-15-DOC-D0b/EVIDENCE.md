# DOC-D0b — saneamiento: los documentos ya no llaman «abierta» a D0

Base `648adb02`. Solo documentos, un fixture y dos pruebas documentales; sin código de `src/`.

Criterio único: donde decía «D0 abierta / decisión del dueño» ahora dice «contrato S–M derivado
por el concilio el 2026-11-15 (`14 §21`, reglas = inferencias del concilio); pendiente su
implementación, `K1`». La única decisión abierta del dueño es `D-G`.

Tocado: `README.md`, `docs/faq.md`, `docs/v2/README.md`, `00-nucleo.md §3.3` (solo la cláusula
«D0, abierta»; la primera frase de 3.3 no se tocó), `03 §1`, `06` (archivo movido → `docs
reanchor`), `10 §11.2`, `12 §1` (tabla de frentes, fila P1, párrafo de lo implementado: añade
K4, caché verificable, K5 y K1a/K1b), `14 §18`, `16 §6` (nota fechada), fixture
`spec16_examples.edn` (`:blocked-by [:P1 :K1]`, `:D0 :status :derived`) y las pruebas
`spec16_models_contract_test`, `wittgenstein_manifest_test` que fijaban el texto viejo.

Validación (bajo memcap): `lint.txt` ok; `directed.txt` — doc14, spec16, manifest y lint tests
en verde. No se corrió la suite completa: no cambia código de `src/`.

No tocado, a propósito: las menciones de `D0` en spec 16 fuera de §6 (las cubre la nota
fechada), las notas históricas fechadas de `12` y `preguntas-abiertas.md`, y los informes de
`runs/`.
