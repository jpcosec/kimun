## REFORMULACION

Propósito: obtener del propietario las decisiones pendientes que bloquean trabajo, a partir de `docs/v2/drafts/traspaso-2026-11-17.md` §6, items 2–5.

Resultado: cuatro respuestas del propietario registradas en `docs/v2/drafts/preguntas-abiertas.md`.

Repo: `/home/jp/proyectos/kimun`.

Límites: es una tarea de decisión, no de implementación. Se abordan cuatro temas: crate de gramática Clojure para el pod; body y cap del parser en serve; ubicación actual de `--reason` sin journal; restauración o no de los tests store-promise eliminados con parity. Se pregunta de una en una, en conversación, y solo se registran respuestas; no se modifica código.

## AMBIGUEDADES

1. “items 2 to 5”: no se explicita qué item corresponde a cada tema del Scope; obligaría a inventar el mapeo.
2. “Clojure grammar crate for the pod”: no se define “pod” ni las opciones disponibles; obligaría a inventar contexto técnico y alternativas.
3. “body and parser cap in serve”: “body” y “cap” son opacos; obligaría a inventar qué se limita y qué decisión se espera.
4. “One question at a time, in conversation”: no indica interlocutor, canal, orden ni forma de respuesta; obligaría a inventar el protocolo.
5. “The owner's answer to each, recorded in docs/v2/drafts/preguntas-abiertas.md”: no fija formato, plantilla ni si el archivo ya existe; obligaría a inventar estructura y criterio de cierre.
6. “Four answers recorded”: no exige evidencia, justificación ni aceptación; obligaría a inventar el nivel de detalle suficiente.

## DRY-RUN

No es un agregado: `atoms`, `depends_on` y `files` están vacíos. Un ejecutor futuro leería la taskdoc, `traspaso-2026-11-17.md` §6 y `preguntas-abiertas.md`; consultaría código solo para formular cada pregunta. Cambios previstos: únicamente registrar cuatro respuestas en `preguntas-abiertas.md`, sin tocar código. Dependencias: disponibilidad del propietario y documentos fuente. Validaciones futuras: que existan cuatro respuestas, una por tema, trazables a §6 y sin implementación asociada.

## FUTURO

El propietario responde y acepta el registro; otro nodo podrá consumir esas respuestas para planificar o implementar. La decisión de cerrar la tarea corresponde a un humano.
