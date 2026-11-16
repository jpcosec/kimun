## REFORMULACION
Propósito: producir, bajo `kimun/`, declaraciones de una copia de `deskops` que lean todas las clases del desk y las conviertan en proposiciones. Resultado: readers declarativos que cubran cada clase, resuelvan referencias heterogéneas, interpreten tags namespaced y distingan fuente de materialización. Repo: `/home/jp/proyectos/kimun`. Límites: el input es solo este TaskDoc; no hay herramientas, historial ni otras fuentes; no se ejecuta nada. Depende de `task-finish-wip-leer-readers-by-class`.

## AMBIGUEDADES
1. “Declarations under kimun/ of a copy of deskops”: no define origen, destino ni si “copia” implica copiar archivos. Obligaría a inventar ruta y procedimiento de copia.
2. “read every class of the desk into propositions”: no define “class”, esquema de “propositions” ni salida esperada. Obligaría a inventar modelo y contrato de lectura.
3. “resolving references that mix ids, paths, Model:name and doc:Model:id@sha256:”: no fija gramática, prioridad ni tratamiento de errores. Obligaría a inventar parser y reglas.
4. “namespaced tags; telling source from materialization”: no da criterio para clasificar fuente/materialización. Obligaría a inventar heurística.
5. “declare read over the copy of the desk reports no unread class and every reading error is explained”: no define “explained” ni formato de reporte. Obligaría a inventar criterio de aceptación.
6. “diseno-leer.md §5 cut 2; docs/v2/drafts/traspaso-2026-11-17.md §5”: no da rutas completas ni versión exacta. Obligaría a inventar ubicación o asumir contenido.
7. “depends_on: task-finish-wip-leer-readers-by-class”: no indica su estado ni salida. Obligaría a asumir que está completada y qué transfiere.

## DRY-RUN
Un ejecutor futuro leería este TaskDoc, la salida de la dependencia, los documentos citados, las clases/frontmatter y `RelationDoc` del desk copiado, y las declaraciones existentes en `kimun/`. Cambios previstos: crear/modificar declaraciones de readers por clase; implementar resolución de ids, paths, `Model:name` y `doc:Model:id@sha256:`; interpretar tags namespaced; separar fuente/materialización. Dependencias: prerequisito, documentos de diseño, copia del desk y `RelationDoc`. Validaciones futuras: ejecutar “declare read” sobre la copia, comprobar ausencia de clases no leídas y explicación de cada error. Nada ejecutado.

## FUTURO
Otro nodo debe entregar la salida de `task-finish-wip-leer-readers-by-class` y los documentos referenciados. Un humano debe decidir semántica de copia, esquema de proposiciones, criterio de errores explicados y aceptación final.
