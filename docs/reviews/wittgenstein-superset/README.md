# Kimun como superset de Wittgenstein

Revisión del 2026-10-09, solicitada por el dueño. **Kimun todavía no es un superset funcional de Wittgenstein.** Tiene una base documental y transaccional más desarrollada, pero le faltan la evaluación contextual, convergencia, deducción, consultas ruteadas y varias capacidades presentes en la segunda línea de Matrix. El objetivo de inclusión se toma de esta solicitud; estos documentos no cambian unilateralmente las decisiones normativas de `docs/v2`.

## Qué significa superset en esta revisión

Para cada capacidad observable de Wittgenstein debe existir un camino en Kimun que conserve contexto, resultado, sentido, justificación y procedencia, o una desviación deliberada documentada con su prueba. Puede ser un módulo Clojure o un motor por protocolo. No exige copiar el código Python, sus bugs, todas sus interfaces, el legado ni las hipótesis de los papers.

La cobertura se compara contra **ambas líneas** (`matrix-shrdlu-spec` y `Matrix/Matrix`), no contra el mapa aspiracional del README raíz. La segunda contiene capacidades que no aparecen en el inventario de Kimun. Superset de capacidades no significa aprobar automáticamente todas las políticas de verdad o curación del sistema anterior.

## Lectura

1. [Cobertura de capacidades](reviews/01-coverage.md): matriz de inclusión y consistencias aprovechables.
2. [Hallazgos](reviews/02-findings.md): inconsistencias internas y entre repos, con prioridad, evidencia y consecuencias.
3. [Oportunidades](reviews/03-opportunities.md): qué se desaprovecha y cómo medir su valor.
4. [Alcance y evidencia](reviews/04-scope-and-evidence.md): fuentes, validación y límites reales de la revisión.
5. [Contrato de inclusión](proposals/01-superset-contract.md).
6. [Identidad y anclaje](proposals/02-identity-and-anchors.md).
7. [Store y autoridad](proposals/03-store-and-authority.md).
8. [G y frontera de motores](proposals/04-contextual-graph.md).
9. [Plan y gates](proposals/05-roadmap-and-gates.md).

## Dependencias sugeridas para la integración

| orden | decisión o trabajo | motivo |
|---|---|---|
| 1 | Contrato de inclusión, vocabulario de verdad/sentido/soporte y autoridad | Evitar construir una colección de afirmaciones cuando se busca formalización anclada |
| 2 | Identidad de ocurrencia/contexto y dependencias versionadas | Sin ellos, la deduplicación mezcla mundos o invalida conocimiento ajeno |
| 3 | Autoridad durable, reconstrucción y frontera base/grafo | Poder borrar cachés sin perder decisiones, datos generados o contratos |
| 4 | Slice vertical source → contexto → hecho → consulta → cambio → reconciliación | Probar que la arquitectura sirve al propósito antes de optimizar otra vez el store |
| 5 | Convergencia, reglas, generación e interacción | Cerrar inclusión funcional con corpus y oráculos independientes |

Esta secuencia expresa dependencias de la propuesta, no sustituye el backlog de `12 §1`, que permite trabajos independientes en paralelo. P0/P1/P2 en los reviews son severidad de hallazgos, no prioridades impuestas a ese backlog.

Las proposals son propuestas revisables, con ejemplos y criterios de aceptación; no implementación ni decisiones confirmadas. Los casos reproducidos y las corridas están en [el run de revisión](../../../runs/subagents/2026-10-09-wittgenstein-superset/README.md). La suite actual en verde demuestra la base existente, **no** la inclusión de Wittgenstein.

## Trazabilidad de hallazgos a propuestas

| propuesta | hallazgos que aborda |
|---|---|
| P1 inclusión | F01, F19; clasificación de capacidades y excepciones |
| P2 identidad/anclaje | F02, F07, F12, F22, F23 |
| P3 autoridad/store | F08, F09, F10, F11, F25 |
| P4 G/motores | F03, F04, F05, F06, F13, F14, F15, F16, F17, F19, F21, F22 |
| P5 integración/gates | F18, F20, F24, F26; conformidad de todas las capacidades required |
