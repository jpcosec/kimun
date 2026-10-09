# R4 — Alcance, fuentes y evidencia

## Alcance real

Se revisaron la dirección vigente de Kimun, los specs de Matrix-SHRDLU, las secciones filosóficas/representación/discusión y pasajes del apéndice del paper v2, el diseño contextual de W2 y sus capacidades adicionales. Se contrastaron las formas y caminos principales de datos, escritura, anclaje, consulta, deducción, evidencia y generación con código y tests.

**No se afirma una auditoría línea por línea de todo el monorepo**, de cada átomo, paper, corpus ni módulo legacy. El inventario de archivos identifica el ámbito, no significa que todo archivo inventariado haya sido leído integralmente. Se cubren todas las familias de producto activas descritas por los specs; los assets, artículos de corpus, dependencias vendorizadas, publicaciones científicas completas y sistemas legacy se inventarían o referencian cuando afectan un hallazgo. No se verifican aquí los teoremas matemáticos ni se validan externamente todas las afirmaciones bibliográficas del paper.

## Fuentes y autoridad

| fuente | rol en la revisión | lectura/contraste |
|---|---|---|
| Kimun `AGENTS.md`, `README`, `docs/faq`, Board | Operación y alcance actual | Orden obligatorio; Board es legacy, no se editó |
| Kimun `docs/v2/README`, `01–08`, `10–14` | Contratos técnicos, dirección, estado | Foco en `01/02/05/07/10/12/14`; cruces de parser, CLI, adapters, distribución e include |
| Kimun `docs/v2/tests/{promises,parity-v1}.md` | Evidencia de promesas, no nuevo objetivo de producto | Paridad v1 no sustituye conformidad Wittgenstein |
| Kimun drafts `capa-g-dudas`, `ids-desde-matrix`, `preguntas-abiertas`, `fuentes-identidad`, sesión | Correcciones y preguntas sin autoridad independiente | Decisiones 24/31 en revisión y alcance de 46 pendiente (D0); respuestas de IDs incorporadas sin reabrirlas |
| Kimun `docs/archive/v2/09` | Diseño recuperable de evaluator/dependencias | Archivado; comandos no disponibles |
| W raíz README | Intención de monorepo | Layout aspiracional; no usarlo como inventario real |
| W1 `specs/README`, `SPEC`, convergence, matrix, populator, chic, grounding, provenance, shrdlu, runtime_api, sources, appendix_causality | Baseline de capacidades | `convergence` prevalece donde revisa docs anteriores; causalidad es futuro |
| W1 `docs/{technical,methodology}.md` | Métodos, límites y resultados reportados | Números de corpus son históricos salvo suites repetidas aquí |
| W1 `pipeline/{ingest,query,convergence,decompose}.py`, `populator`, `sia` | Estructura de pipeline y contratos | Lectura concentrada en entrypoints, políticas y representación; inventory del resto |
| W1 motor `core`, `system/{deduction,s_expression_runtime,wi_game_registry,logical_system_algebra}`, `routing`, tests | Evaluación y soporte | Pruebas y lecturas específicas; suite del motor repetida |
| W2 `docs/{context_and_wigame,kernel_symbol_policy,semantic_fields}.md` | Diferencias respecto de W1 | Orden, middle term, shape, hypertensor, partition, schema, reflection, projection |
| W2 módulos homónimos y tests seleccionados | Evidencia de capacidades adicionales | 64 tests de ocho módulos repetidos; no suite entera de W2/JAX/OWL |
| Paper `Matrix/paper_v2/sections/{02,03,04,10}` | Fundamento y posibles oportunidades | Separar diseño conceptual, hipótesis, claims de rendimiento y comportamiento implementado |

Los snapshots de contenido, commits de origen y lista de paths se registran en `runs/subagents/2026-10-09-wittgenstein-superset/source-inventory.json`. La revisión refiere al árbol local observado, no solo a HEAD; eso importa especialmente en W2, cuyas capacidades adicionales pueden estar en cambios locales. Las fuentes externas no se copiaron ni modificaron.

## Qué pruebas sostienen los hallazgos

- `probe_kimun.clj`: sucesor de referencia sin cualificar ocurrencia; forma de regla anidada rechazada; forma mínima de símbolo sin contexto.
- `probe_wittgenstein.py`: hecho sin cita aceptado; respuesta TRUE con Si unsinnig; falso explícito calificado devuelto como UNKNOWN; evidencia LLM observed/confirmed.
- `bb test` + `bb oracle`: baseline de Kimun; resultados completos en el run.
- W1 tests del pipeline y tests del motor en invocaciones separadas; esa separación evita colisiones entre paquetes llamados `tests`.
- Ocho módulos de W2: middle_term, reflection, relation_algebra, shape_guard, rule_schema, partition, hypertensor y projection_emit.

La primera invocación combinó `tests` y `matrix/tests` y falló en collection por colisión de paquetes `tests`. Se conserva ese log como intento fallido, **no como fallo funcional del motor**. Se corrigió el método y se ejecutaron ambas suites por separado. W1 tiene un test skipped; `-q` no imprimió el motivo y no se acredita esa capacidad. No se llamó al LLM ni a servicios externos.

## Cambios concurrentes de Kimun

La lectura inicial partió de `89b0300da7468bb8be67e362c6d65836c0c0ef04`; durante la revisión HEAD avanzó a `3d0904110b2ea1a84c51b16555100ba6ae9c6acd`. Se releyeron AGENTS, sesión/decisiones abiertas y `12/13/14/05`; se incorporaron retiro del desk, backlog por dependencias y respuestas parciales de IDs. No hubo cambios de `src` en ese intervalo; el único cambio bajo `test` fue `include_test.cljc`, que se valida de nuevo por separado bajo memcap. La suite completa fue baseline del árbol observado durante la corrida, no de un checkout aislado e inmutable.

## Límites de conclusión

Suites verdes no resuelven las decisiones abiertas del dueño, no acreditan inclusión funcional y no descartan los controles negativos reproducidos. Los riesgos de historia, retractación alternativa, contratos de parser y federación son hallazgos por lectura; las proposals contienen los casos que deberán probarlos antes de implementar.
