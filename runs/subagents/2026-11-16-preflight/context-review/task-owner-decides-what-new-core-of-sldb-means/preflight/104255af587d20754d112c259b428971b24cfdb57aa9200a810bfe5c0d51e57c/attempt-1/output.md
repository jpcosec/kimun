## REFORMULACION

Encargo: obtener y registrar la decisión del owner sobre qué significa el "nuevo core" de sldb. Propósito: desbloquear todo el diseño, bloqueado por una pregunta hecha dos veces sin respuesta (referencia: `docs/v2/drafts/traspaso-2026-11-17.md` §6.1). Resultado esperado: la respuesta del owner, literal, en un fichero de `docs/v2/drafts/`, y la actualización de `docs/v2/drafts/diseno-leer.md` §6. Repo: `/home/jp/proyectos/kimun`. Límites: una sola pregunta en conversación, con las cifras de la ingesta de desk a mano; no incluye diseño ni implementación. Es una tarea `task.decision`: no es ejecutable por un ejecutor automático, la resuelve un humano. No tiene hijos (`atoms: []`, `depends_on: []`), así que no hay agregación que recorrer.

## AMBIGUEDADES

1. "The owner's answer, verbatim, in docs/v2/drafts/" — no fija nombre de fichero; obligaría a inventar la ruta exacta.
2. "with the numbers of the desk ingestion in hand" — no define qué cifras ni de dónde salen; obligaría a inventar su fuente.
3. "Either sldb keeps existing... or kimun replaces sldb" — no aclara si es binario excluyente o admite híbridos; obligaría a inventar el espacio de respuestas válidas.
4. "docs/v2/drafts/diseno-leer.md §6 is updated" — no dice el contenido actual de §6 ni el criterio de actualización; obligaría a inventar qué escribir.
5. "Criterion of done 1" y lista de Validation vacía — no hay checks; obligaría a inventar criterios de cierre.
6. Referencia a `traspaso-2026-11-17.md` con fecha posterior al día actual (2026-11-16); obligaría a inventar si el fichero ya existe.

## DRY-RUN

Un ejecutor futuro: (1) leería esta TaskDoc, `traspaso-2026-11-17.md` §6.1 y `diseno-leer.md` §6; (2) reuniría las cifras de ingesta de desk; (3) formularía la pregunta al owner; (4) registraría la respuesta literal en un fichero nuevo de `docs/v2/drafts/`; (5) actualizaría §6. Dependencias: respuesta humana y cifras de ingesta. Validaciones futuras: respuesta registrada, §6 actualizado. Nada de esto se ejecuta aquí.

## FUTURO

El owner responde (humano); un nodo de diseño posterior consume la respuesta y las cifras; un humano confirma el cierre.
