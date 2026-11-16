## REFORMULACION

Encargo del nodo: implementar el stage 4 del plan de producto en el repo `/home/jp/proyectos/kimun`. Propósito: permitir fichas de términos con contenido pero sin ancla, consultas `graph.*` sobre proposiciones, y que `declare about` acepte una proposición como término. Resultado esperado: código y pruebas que materialicen esas tres capacidades, con cierre observable cuando una ficha relacionada con una región anclada se encuentra a través de la relación. Límites: solo lo descrito en `plan-producto.md` stage 4; el TaskDoc no aporta archivos, átomos, dependencias ni validación.

## AMBIGUEDADES

1. «Terms with content and no anchor»: no define término, contenido, ancla ni su representación. Obliga a inventar el esquema de datos.
2. «graph.* queries the propositions»: no especifica API, sintaxis, parámetros ni forma de resultado. Obliga a inventar la interfaz de consultas.
3. «declare about accepts a proposition as term»: no define `declare about`, ni la semántica de “aceptar”. Obliga a inventar el comando y sus efectos.
4. «A ficha related to an anchored region is found through the relation»: no define ficha, región anclada ni “la relación”. Obliga a inventar el criterio de búsqueda y el test de cierre.
5. «plan-producto.md stage 4» (Scope e Implementation Path): el archivo no está en el input. Obliga a inventar requisitos y superficie afectada.
6. «task_type: task.code» con `files: []`, `atoms: []`, `depends_on: []` y Validation vacía: obliga a inventar qué módulos tocar y cómo verificar.

## DRY-RUN

Sin ejecutar: un ejecutor leería el TaskDoc, `plan-producto.md`, el modelo de proposiciones/grafo y los tests existentes. Cambios previstos: modelo o serialización de ficha sin ancla; consultas `graph.*` sobre proposiciones; aceptación de proposición en `declare about`; relación o índice para localizar fichas por región anclada. Dependencias: plan aprobado, esquema y API vigentes, framework de pruebas. Validaciones futuras: ficha sin ancla, consultas de grafo, `about` con proposición, y prueba de cierre (ficha hallada vía relación).

## FUTURO

Un humano debe aportar `plan-producto.md` stage 4 o precisar el alcance; otro nodo debe completar validación y criterios de aceptación. El ejecutor entregará código y tests para revisión humana.
