## REFORMULACION

Encargo: producir la “cut 2” de lectores kimun para el desk de deskops. Resultado: carpeta runs/subagents/YYYY-MM-DD-lectores-desk/ con readers/*.clj (copia de los cuatro de cut 1 más clases nuevas), read.edn, coverage.txt, about.txt, script de cobertura y REPORT.md en español, commit solo de esa carpeta en main de /home/robocop/proyectos/kimun. Límites: no tocar src/ ni test/ de kimun, no escribir en SLDB_1, no comparar con el índice de sldb, sin caché.

## AMBIGUEDADES

1. “read id plus every key that names another document” (rituals, roles, runtimes): no enumera las claves; obliga a inventar cuáles “nombran otro documento”.
2. “subject or subject_ref” (reviews): no fija precedencia ni si pueden coexistir; obliga a inventar la regla.
3. “desk/lifecycle (two shapes: rules ... relation documents ...)”: no dice cómo distinguir una forma de otra; obliga a inventar el criterio de detección.
4. “desk/contexts (pills: id, tags)”: no indica qué proposiciones declarar; obliga a inventar el esquema.
5. Goal “every Markdown file ... is the source of at least one proposition” frente a Validación “or names each remaining file with the reason”: obliga a decidir si excepciones son aceptables.

## DRY-RUN

Un ejecutor futuro leería diseno-leer.md §§3 y 7, el REPORT de cut 1 y los cuatro readers; inspeccionaría frontmatter de las carpetas nuevas; comprobaría git pull y estado limpio. Crearía $D temporal con copia del desk y repo git con readers bajo $D/kimun/, añadiría el helper de normalización de referencias en base.clj, escribiría readers nuevos y el script de cobertura. Ejecutaría declare read y declare about bajo flock/memcap, re-copiando readers tras cada edición. Dependencias: binario kimun, pod treesitter, bb/python3, git, PATH. Validaciones futuras: cobertura sin huérfanos, errores 0, métricas por reader, ejemplos antes/después de normalización, about con filas, git status intacto en kimun y SLDB_1, commit con trailer.

## FUTURO

El coordinador revisa y hace push; el tiempo de declare read decide la tarea task-cache-of-readings-by-git-blob-hash; la comparación con el índice de sldb queda para otra tarea; un bug de kimun se detiene y se reporta a decisión humana.
