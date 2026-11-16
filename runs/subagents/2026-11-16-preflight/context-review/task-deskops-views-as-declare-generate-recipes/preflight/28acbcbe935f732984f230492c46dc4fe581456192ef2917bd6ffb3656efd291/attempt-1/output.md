## REFORMULACION

Encargo: crear recetas de *declare generate* en una copia del repo deskops, de modo que el Board y la vista de una tarea de ese desk se materialicen desde modelos. Propósito: avanzar hacia que todo documento que SLDB genere sea materialización de modelos. Resultado observable: ficheros generados que coinciden con las vistas de deskops (o diferencias explicadas) y dos ejecuciones con bytes idénticos. Límite: solo recetas sobre proposiciones aportadas por los readers del desk; no se defining aquí el mecanismo de readers (dependencia `task-readers-for-every-class-of-the-deskops-desk`). Referencia normativa citada: `diseno-leer.md §5 cut 4`.

## AMBIGUEDADES

1. "Recipes in a copy of deskops": no se indica ruta, nombre ni cómo se obtiene la copia → obligaría a inventar ubicación y método de copia.
2. "come out of declare generate": no se define el comando, versión ni sintaxis de `declare generate` → obligaría a inventar la interfaz.
3. "diseno-leer.md §5 cut 4": el fichero no está en el input → obligaría a inventar su contenido y el significado de "cut 4".
4. "the propositions the readers give": no se especifica formato ni contrato de esas proposiciones → obligaría a inventar el esquema de entrada.
5. "the view of one task": no se identifica qué tarea concreta ni qué vista → obligaría a elegir una tarea arbitraria.
6. "compare with deskops' own output": no se define el criterio de comparación ni tolerancias → obligaría a inventar el diff aceptable.
7. Validation: lista vacía → obligaría a inventar los checks.
8. "two runs give the same bytes": no se aclara si incluye timestamps/orden → obligaría a inventar el criterio de determinismo.

## DRY-RUN

Lecturas previas: el task doc, el output de la dependencia (readers), `diseno-leer.md §5` y la copia de deskops. Cambios previstos: añadir recetas declare en la copia; generar Board y vista de una tarea; comparar contra salida nativa. Dependencias: task de readers cerrada y disponible. Validaciones futuras (no ejecutadas): coincidencia o diferencias explicadas, doble ejecución byte-idéntica, checks de la sección Validation (hoy vacía).

## FUTURO

Otro nodo debe entregar las proposiciones de los readers y localizar `diseno-leer.md §5`; un humano decide qué tarea concreta sirve de muestra y qué diferencias son aceptables.
