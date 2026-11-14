# Preguntas abiertas al dueño (2026-10-09)

> **Actualización 2026-11-14:** las alternativas D0/D-G de la tabla inferior son históricas
> y ya no deben presentarse como elecciones vigentes. El dueño aclaró que identidad no es hash
> ni anclaje topológico y encargó definirla mediante revisión documental con el concilio.
> Exige seguir topologías marcadas y cambios temporales, con niveles de abstracción.
> Las dos verificaciones son vigencia respecto de una huella y, condicionalmente, verdad del
> resultado de ejecuciones marcadas. Estamos en S–M; G se aborda después.
> Dirección registrada en la cabecera de `docs/v2/14-juego-del-lenguaje.md`.
> Pendiente de diseño: contrato de unidad/continuidad a través de cambios y contrato de ejecución;
> no se considera aprobada una representación técnica ni desbloqueada su implementación.

> Lo que bloquea seguir. Cada respuesta se registra en `docs/v2/05-estado.md` (decisión) o en
> `docs/v2/14-juego-del-lenguaje.md`, y se borra de aquí. Qué depende de cada una: `docs/v2/12 §1`.

| # | pregunta | material para decidir | bloquea |
|---|---|---|---|
| D0 | ¿Se adopta para todo el kernel "identidad = lugar (estructural para S, semántico para M/G); hash = verificación de mutación"? Revisa `01 §4` principio 3 y las decisiones 1, 3 y 4. Opciones: (a) IDs taxonómicos puros, ningún CAS como identidad; (b) conservar el CAS para objetos de contenido y añadir ocurrencias S, símbolos M contextuales y hechos G en contexto con IDs propios (propuesta P2 de codex). | `docs/v2/drafts/fuentes-identidad.md` (fuentes con direcciones), `docs/v2/drafts/ids-desde-matrix.md`, `docs/v2/14-juego-del-lenguaje.md §21` | K1, K2b, S2, G1 (`12 §1`) |
| D-G | Eje de verdad en G: (a) literal del dueño — verdad = anclado/derivado, sentido = sinnvoll/sinnlos/unsinnig; (b) P4 de codex — anclado/derivado es el eje de *soporte*, y además existe un valor operacional TRUE/FALSE/UNKNOWN para falso explícito y desconocimiento | `docs/v2/drafts/capa-g-dudas.md`, `docs/reviews/wittgenstein-superset/proposals/04-contextual-graph.md`, `docs/reviews/wittgenstein-superset/respuesta-claude.md` (Matiz 1) | G1 |

Respondidas el 2026-10-09 (registradas en `14 §21`): ID estructural roto = borrado (se declara) o
mutación (lo detecta el hash); un símbolo nuevo nace en el contexto del documento y subirlo o
declararlo `equivalent` es curaduría. Los modelos del desk se borran para dejar una versión estable
(se regeneran en M2). Las ramas `close/spec15` y `close/A4` se cerraron (contenido en
`runs/research/2026-10-08/`).

Suspendidas hasta la capa G (G1): las preguntas del borrador del spec 15
(`runs/research/2026-10-08/spec15/15-capa-g.md §12`), que deben revisarse a la luz de
`docs/v2/drafts/capa-g-dudas.md`.
