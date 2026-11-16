## REFORMULACION

**Propósito:** ejecutar la etapa 5 del plan aprobado (`diseno-generar.md §4`) para que `declare` cubra tres restos: servir proposiciones por HTTP, detectar ficheros generados cuya declaración desapareció y materializar una clase mediante docstring tipado.
**Resultado:** código y pruebas que hagan que `declare generate --check` informe de un fichero huérfano, además de los dos comportamientos citados.
**Repo:** no se declara repo ni ruta; solo `workspace:desk` y el TaskDoc. CWD declarado: `/home/jp/proyectos/kimun`.
**Límites:** el input es únicamente este TaskDoc; `diseno-generar.md §4` no se adjunta. No se ejecuta nada.

## AMBIGUEDADES

1. «diseno-generar.md §4.» aparece como Rationale, Scope e Implementation Path. Obligaría a inventar el contenido del diseño (endpoints, formato de huérfano, generación de docstring).
2. «HTTP serves the propositions; a generated file whose declaration disappeared is detected; typed docstring as materialization of a class.» Son tres resultados en un solo Goal; no se especifica si son un entregable o tres, ni su orden.
3. «Validation» está vacío («- »). Obligaría a inventar los checks de cierre.
4. «declare generate --check reports an orphan file.» no define qué es huérfano ni el formato de salida; obligaría a inventar criterio y mensaje.
5. «typed docstring as materialization of a class» no indica sintaxis, lenguaje ni convención; obligaría a inventar el mecanismo.
6. `references: []`, `depends_on: []`, `files: []`: no hay superficie afectada; obligaría a inventar módulos y dependencias.

## DRY-RUN

**Lecturas previas:** este TaskDoc; `diseno-generar.md §4` (ausente en el input); árbol del repo para localizar `declare`, generación y servidor HTTP.
**Cambios previstos:** handler/ruta HTTP que sirva proposiciones; lógica en `generate --check` que compare ficheros generados con declaraciones vigentes y reporte huérfanos; emisión de docstring tipado al materializar una clase; pruebas asociadas.
**Dependencias:** el diseño §4 y el estado actual de `declare`; sin ellos no hay contrato verificable.
**Validaciones futuras:** ejecutar `declare generate --check` sobre un caso con declaración borrada y comprobar el reporte; prueba de integración HTTP de proposiciones; prueba unitaria del docstring. Nada de esto se ejecuta ahora.

## FUTURO

Un humano debe aportar `diseno-generar.md §4` o ampliar el TaskDoc; decidir si los tres resultados se separan en tareas; definir validaciones y criterio de huérfano. Otro nodo posterior ejecutará los cambios y correrá las pruebas.
