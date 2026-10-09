# Preguntas abiertas al dueño (2026-10-09)

> Lo que bloquea seguir. Cada respuesta se registra en `05-estado.md` (decisión) y se borra de aquí.
> Contexto: `drafts/sesion-2026-10-08-09.md`. Sin respuesta, **no se aplica default** a Q1–Q3.

| # | pregunta | opciones | recomendación | desbloquea |
|---|---|---|---|---|
| Q1 | ¿Se confirma `14 §21` (hash ≠ identidad) para todo el kernel? Es reescribir nodos, aristas y revisiones. ¿Es lo siguiente, o primero se cierra el store estable con los ids actuales? | confirmar ahora / confirmar después del store / no | — | decisión 46; rediseño del kernel |
| Q2 | Cuando cambia la estructura (sección renombrada, archivo movido), el ID estructural de lo anclado cambia. | (a) anchor roto que se reancla, el hash ayuda a ubicarlo; (b) el ID sobrevive | (a) | IDs estructurales de S |
| Q3 | Un símbolo nuevo nace dentro del contexto del documento; subirlo o declararlo `equivalent` es curaduría. | sí / otra regla | sí | IDs semánticos de M/G |
| Q4 | Documentos generados (el desk): sus datos se siguen reconstruyendo leyendo el `.md` con su modelo de anclaje, o pasan ya al grafo. | esperar al rediseño de deskops / ahora | esperar | registro de documentos generados |
| Q5 | Qué sigue | (a) store estable: Merkle por ruta, plan fuera del CAS, sin índice ni aristas por hash; (b) separar el grafo en paquete aparte | (a) | `12 §1` puntos 1 y 4 |
| Q6 | Ramas huérfanas `close/spec15` (capa G, previa a la corrección del dueño) y `close/A4` (packfiles a medias) | conservar como referencia / borrar | conservar | limpieza |

Suspendidas hasta retomar la capa G (ver `drafts/capa-g-dudas.md`): Q1, Q2, Q4, Q6, Q8 y Q9 del spec 15.
