## REFORMULACION

Propósito: saldar la deuda declarada en `docs/v2/drafts/traspaso-2026-11-17.md §8`, cerrando o manteniendo explícitamente cada límite listado. Resultado: por cada ítem, un commit o una decisión registrada de conservarlo. Repo: `kimun` (`/home/jp/proyectos/kimun`), TaskDoc `desk/tasks/task-declared-debt-of-the-new-path.md`. Límites: solo los ítems enumerados; no rediseño general ni nueva deuda; el documento está `draft`, sin validaciones ni historial definidos.

## AMBIGUEDADES

1. «One cut per item»: no define qué es un “corte” ni su granularidad; obligaría a inventar si es un commit, un PR o un cambio aislado.

2. «Abandoned thread after a timeout in serve and mcp»: no indica timeout, condición ni limpieza esperada; obligaría a inventar el comportamiento correcto.

3. «surface.http and surface.index.cache requiring cli»: no especifica la dependencia ni el resultado deseado; obligaría a inventar si se elimina, invierte o documenta.

4. «hidden cycle http.views to cli.main»: no define cómo demostrar que está roto ni el arreglo; obligaría a inventar criterio y solución.

5. «dead routes-shared in stores check»: no identifica la ruta ni el check; obligaría a inventar qué código se considera muerto.

6. «measure declare on large projects»: no fija métrica, proyecto ni umbral; obligaría a inventar el método de medición.

7. «Validation — -»: no hay checks; obligaría a inventar validaciones de cierre.

## DRY-RUN

Lecturas: TaskDoc, §8 del traspaso, código de `serve`/`mcp`, superficies `http`, `index.cache`, `cli`, `stores` y medición de `declare`. Cambios previstos: un corte por ítem; localizar causa, implementar arreglo o registrar decisión de conservar. Dependencias: contenido real de §8, acceso al código y entorno de medición. Validaciones futuras: no están definidas; habría que acordar prueba, commit o decisión por ítem. Sin ejecutar nada.

## FUTURO

Un humano debe aportar o confirmar el contenido concreto de §8 y aceptar las decisiones de “mantener”. Otro nodo ejecutor producirá los commits o registros de decisión; la validación conjunta queda pendiente de definición.
