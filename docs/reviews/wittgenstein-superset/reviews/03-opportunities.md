# R3 — Oportunidades desaprovechadas

Estas oportunidades amplían capacidades concretas. No son motivos para reescribir de una vez el kernel, portar todo a Clojure o adoptar JAX.

| ID | oportunidad y activos existentes | propuesta | señal de valor / riesgo |
|---|---|---|---|
| O01 | W1 tiene SIA, pares sparse, AROMA y corpus offline | Conservar motor Python detrás de frontera semántica versionada; Kimun posee identidad, autoridad, commit y validación | Reproducir intensidad contra fixtures y medir memoria; riesgo: repartir política de promoción entre cliente y motor |
| O02 | Kimun tiene CST/AST, clases y selectors en spike; W1 pierde markup al limpiar | Producir chunks desde estructura con mapa raw→hojas; permitir contextos anidados sin fabricar independencia de fuentes | Más soportes válidos, no simplemente más chunks; riesgo: contar secciones del mismo source como corroboraciones independientes |
| O03 | W1 mide que lexicalización de objetos frena convergencia | Resolver configuraciones con equivalencias contextualizadas y versionadas antes de bajar umbrales | Subir cobertura de pares de oro sin nuevas falsas fusiones; no canonicalizar globalmente por string |
| O04 | Contextos, modelos y relaciones pueden reificarse (W2) | Autodescripción de Kimun como juego: contratos, tipos, operaciones y procedencia consultables | Preguntar «qué permite R aquí» con respuesta verificable; riesgo: autoaprobación circular de una regla |
| O05 | Árboles Merkle y dependencias derived ya existen | Caché de evaluación por inputs reales: proyección S, contrato, reglas, rutas y políticas | Cambio de azúcar no reevalúa G; cambio de regla sí; medir trabajos evitados sin falsos intactos |
| O06 | Separación anclaje/generación + `projection_emit` | Generar informe/JSON/Markdown desde una instancia con prueba y citations, manteniendo strings fijos fuera del conocimiento | Cambiar estilo sin mutar hechos; edición externa de generado pide acción explícita |
| O07 | Shapes de W2 detectan relaciones comodín y objetos empaquetados | Gate previo a carga de G, con findings que señalen el argumento oculto | Caso `(* pedrito es_hermano_de_maria)` queda explicado; un buen score estadístico no blanquea mala forma |
| O08 | Término medio y orden lógico existentes en W2 | Rutas con witnesses y secuencias desde orden declarado | Auditar receta/flujo de código sin inventar equivalencia entre relaciones distintas |
| O09 | Corpus Curie/Franklin, vegetales, físicos y KB curada | Banco de conformidad de inclusión independiente de sldb v1 | Controles contra falsa identidad, falso explícito, máscara y omisión de soporte; no confundir recuperar citas con conocer |
| O10 | Kimun adapters inspecciona código; W2 admite metanivel | Knowledge sobre código anclado a símbolo/firma/test, con contexto de ejecución | Un cambio de función invalida solo las conclusiones dependientes; resultado de test lleva versión y alcance |
| O11 | W1 REPL ya ofrece pending/focus/clarificación | API con preguntas pendientes y elección ligada a revisión; UI posterior | Tras editar la KB, no ejecutar una elección obsoleta; mejora de interacción medible sin memoria implícita del chat |
| O12 | Kimun federación y W1 convergencia multifuente | Recuperación física federada + transporte semántico explícito | Dos stores con `she` no se mezclan; ruta remota justifica identidad y contrato |
| O13 | Reglas esquemáticas W2 + consulta prevista Datalog | Usar Datalog para selección y candidatos, evaluador para admisibilidad/verdad | Misma regla con negación/falso/unknown conserva significado; no sustituir Si por ausencia de triple |
| O14 | Benchmarks de store y tests differential existentes | Presupuesto del slice vertical completo: lectura, extracción, convergencia, evaluación, invalidación | Mejora de tamaño sin sacrificar respuestas o pruebas; separar cold/warm y costes de reconstrucción |

## Experimentos prioritarios

1. **Correspondencia Unicode.** Source con acentos descompuestos, emoji ZWJ, links, CRLF y oración que cruza hojas. Evaluar si toda cita recupera el fragmento original y qué casos quedan explícitamente no representables por una sola hoja.
2. **Cambio local.** Dos documentos comparten párrafo; uno cambia. Medir anchors afectados y repetir tras borrar caché. Cambiar luego regla y contexto sin cambiar bytes para verificar invalidación semántica.
3. **Canonicalización contextual.** Mismo corpus/cache de W1, solo añadir correspondencias revisadas de objetos. Reportar condiciones de alcance, coverage, fusiones falsas, MRR sobre todas las consultas y QA con salto.
4. **Source vs generado.** Instancia pequeña produce dos estilos. Borrar caché y archivo generado; debe regenerar desde datos durables. Editar source; debe conservar evidencia vieja como stale, sin inventar texto histórico.
5. **Inclusión W2.** Un ejemplo de orden, uno de término medio, una regla con variable y una relación reificada; conservar resultado y justificación al cambiar motor.

Para cada experimento: pregunta y decisión que habilita, control positivo/negativo, oro externo o construido, parámetros fijados antes, coste acotado, red desactivada y resultados versionados. El método de W1 `docs/methodology.md` ya tiene esta disciplina; aprovecharlo evita medir el sistema contra sí mismo.
