## REFORMULACION

TaskDoc ejecutable (no agregado). **Propósito:** cut 2 de `docs/v2/drafts/diseno-leer.md` §5: añadir readers de kimun para las clases del desk que cut 1 no cubrió. **Resultado:** `runs/subagents/YYYY-MM-DD-lectores-desk/` con `readers/*.clj`, `read.edn`, `coverage.txt`, `about.txt`, el script de cobertura y `REPORT.md` en español, tal que todo Markdown con frontmatter de una copia del desk produzca al menos una proposición. **Repo:** `/home/robocop/proyectos/kimun`, rama `main`; el desk se copia desde `/home/robocop/proyectos/SLDB_1/deskops/desk`. **Límites:** no tocar `src/` ni `test/` de kimun; nada se escribe bajo SLDB_1; no ejecutar sldb ni deskops; sin caché; no push.

## AMBIGUEDADES

1. "`runs/subagents/YYYY-MM-DD-lectores-desk/readers/` (YYYY-MM-DD = the day of the run)": no fija qué día si la ejecución cruza medianoche; obligaría a inventar la fecha de la carpeta.
2. "with a short bb or python3 script kept in the run folder": no elige lenguaje ni nombre del script; obligaría a inventar cuál.
3. "read id plus every key that names another document" (rituals, roles, runtimes): no enumera las claves; obligaría a inventar qué claves "nombran otro documento".
4. "one helper function defined in base.clj and used by every reader": no aclara si "every reader" incluye los cuatro copiados de cut 1; obligaría a decidir si se editan esos.
5. "change the tasks reader's glob so a drawer task is not also a Task": no dice si otros globs (atoms) requieren ajuste análogo; se asumiría que no.

## DRY-RUN

**Lecturas:** `docs/v2/drafts/diseno-leer.md` §§3 y 7; `runs/subagents/2026-11-16-leer/REPORT.md`; los cuatro readers de cut 1. **Preparación:** `git pull --ff-only`, comprobar status limpio; `mktemp -d`, copiar desk a `$D/desk` y readers a `$D/kimun`, `git init` + commit. **Cambios previstos:** nuevos `readers/*.clj` para Pill, DrawerTask, InboxNote, LifecycleRule, LifecycleRelation, Repository, Review, Ritual, Role, Runtime; helper de referencias en `base.clj`; glob de tasks corregido. **Dependencias:** `bin/kimun`, pod treesitter, `memcap`, `flock`, PATH con `$HOME/.local/bin`. **Validaciones futuras:** `declare read` (tiempo, `:read-propositions`, `:reading-errors`), script de cobertura → `coverage.txt`, un `declare about` por clase → `about.txt`, y `git status` de kimun y de SLDB_1 antes/después. Nada ejecutado.

## FUTURO

El coordinador revisa y hace push. El número de segundos de `declare read` (>10 s) alimenta la tarea `task-cache-of-readings-by-git-blob-hash`. Un humano decide si un bloqueo de kimun justifica detener una clase.
