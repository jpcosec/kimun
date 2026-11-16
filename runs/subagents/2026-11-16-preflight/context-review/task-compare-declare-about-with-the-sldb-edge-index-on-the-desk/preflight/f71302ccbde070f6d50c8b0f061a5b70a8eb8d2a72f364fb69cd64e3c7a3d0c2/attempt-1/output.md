REFORMULACION

Encargo ejecutable (task.code, no agregado): comparar de forma medida las relaciones que kimun lee de “the desk” con el índice de aristas de sldb. Propósito: obtener evidencia para el “Criterion 2” frente a lo que sldb/deskops responden hoy, no contra demos sintéticas. Resultado: exportación de aristas de sldb para el desk, exportación de proposiciones de kimun, diff y explicación de cada diferencia, depositado en `runs/`. Repo: `/home/jp/proyectos/kimun`; TaskDoc en `desk/tasks/task-compare-declare-about-with-the-sldb-edge-index-on-the-desk.md`. Límites: solo lectura sobre `SLDB_1` o copias; no tocar el store real.

AMBIGUEDADES

1. “Criterion 2”: no se define en el input. Obliga a inventar qué criterio, umbral o contexto de aceptación se mide.
2. “relations kimun reads from the desk” frente a “export kimun's propositions”: no queda claro si son el mismo conjunto ni cuál es la consulta o ruta. Obliga a inventar el alcance y el mapeo.
3. “SLDB_1 or on copies”: no se indica instancia, conexión, réplica ni cómo garantizar lectura. Obliga a inventar acceso y selección.
4. “Export sldb's edges for the desk”: faltan formato, destino y clave de selección. Obliga a inventar esquema y comando de exportación.
5. “export kimun's propositions”: faltan API, consulta y formato. Obliga a inventar el mecanismo de extracción.
6. “diff, explain every difference”: no define clave de igualdad, normalización ni nivel de explicación. Obliga a inventar criterios.
7. “explained or fixed” frente a “Read-only”: obliga a decidir si se permite corregir y dónde, en contradicción aparente.
8. “in runs/”: no concreta subruta ni nombres. Obliga a inventar el artefacto exacto.

DRY-RUN

Lecturas: TaskDoc, código de lectura del desk en kimun, documentación/esquema de sldb, copias de `SLDB_1`. Dependencia: `task-readers-for-every-class-of-the-deskops-desk` debe aportar el lector. Cambios previstos: solo artefactos bajo `runs/` (exportaciones y diff); ninguna escritura en stores. Validaciones futuras: confirmar solo lectura, reproducibilidad, conteos y que cada diferencia esté explicada o marcada como corregida.

FUTURO

Otro nodo o un humano debe definir “Criterion 2”, el acceso a `SLDB_1`, la política de correcciones y el formato aceptable. El resultado alimenta la validación posterior de aceptación.
