## REFORMULACION

Propósito: producir un documento de diseño que explique cómo kimun puede ser el núcleo de sldb, partiendo de la respuesta del owner sobre qué significa “nuevo núcleo” y del terreno medido. Resultado: un diseño escrito por el coordinator en `docs/v2/drafts/`, sin implementación. Repo: `kimun`, con la tarea en `desk/tasks/` y el trabajo documental en `docs/v2/drafts/`. Límites: no tocar código ni ejecutar implementación; depende de `task-owner-decides-what-new-core-of-sldb-means`; la validación está vacía; el cierre exige que el owner lea el diseño y diga “what stands”.

## AMBIGUEDADES

1. “A design written by the coordinator”: no se define formato, extensión, estructura ni quién es el coordinator. Obligaría a inventar plantilla y autoría.
2. “from the owner's answer”: no se incluye la respuesta ni una referencia concreta. Obligaría a inventar las decisiones del owner sobre “new core”.
3. “the measured terrain”: se apunta a un informe privado y a `runs/research/2026-11-15/reemplazo-sldb/`, pero no se da contenido, nombre de archivo ni criterio de precedencia. Obligaría a inventar métricas y restricciones.
4. “kimun has no in-process Python surface, rules, views, transactional writes with actor or linked stores”: no queda claro si es estado actual, problema a resolver o lista de capacidades exigidas. Obligaría a inventar el alcance técnico del diseño.
5. “docs/v2/drafts/; no implementation”: no se indica nombre de archivo ni si se admite más de un documento. Obligaría a inventar el entregable exacto.
6. “The owner has read it and said what stands”: criterio de aceptación subjetivo. Obligaría a inventar qué significa aprobar o qué observaciones bloquean el cierre.
7. “From the private terrain report … and runs/research/…”: no se aclara si ambas fuentes son obligatorias ni qué hacer si faltan. Obligaría a inventar la selección de fuentes.

## DRY-RUN

Lecturas previstas: esta TaskDoc, la tarea dependiente, la respuesta del owner, el informe privado de terreno y `runs/research/2026-11-15/reemplazo-sldb/`; revisión de `docs/v2/drafts/`. Cambios previstos: crear únicamente el documento de diseño, sin código. Dependencias: respuesta del owner y fuentes de terreno. Validaciones futuras: comprobar que el documento existe, distingue estado actual y objetivo, cita el terreno, no implementa y que el owner lo ha leído y aceptado. No ejecutable ahora: faltan respuesta, fuentes y criterios de validación.

## FUTURO

La tarea dependiente debe aportar la decisión del owner; otro nodo o fuente externa debe aportar el terreno medido y el research; el owner decide la aceptación final. No es un agregado ni un paquete ejecutable.
