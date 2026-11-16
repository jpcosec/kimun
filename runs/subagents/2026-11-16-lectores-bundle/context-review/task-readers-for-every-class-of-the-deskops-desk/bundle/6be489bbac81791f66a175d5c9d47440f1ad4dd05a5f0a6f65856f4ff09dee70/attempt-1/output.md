REFORMULACION

Encargo ejecutable (no agregado): ampliar kimun con readers para las clases del desk de deskops no cubiertas por el cut 1. Propósito: que todo Markdown con frontmatter genere al menos una proposición. Resultado: `runs/subagents/YYYY-MM-DD-lectores-desk/` con `readers/*.clj`, `read.edn`, `coverage.txt`, `about.txt`, script de cobertura y `REPORT.md`. Repo de trabajo: clon `/home/robocop/proyectos/kimun`, rama main, commit solo del run folder, sin push. Límites: no tocar `src/` ni `test/` de kimun; `/home/robocop/proyectos/SLDB_1` es solo lectura; sin caché ni comparación con el edge index de sldb.

AMBIGUEDADES

1. «about.txt has one declare about per new class and each returns at least one row»: no dice qué ID usar por clase; obligaría a inventar el criterio de selección.
2. «declare for the document three propositions, relation-source, relation-target and relation-type»: no aclara si son nombres literales de relación o `has-source-id`/`has-target-id`/`has-relation-type` según la regla (c); obligaría a inventar el nombrado.
3. «if a bug or limit of kimun blocks a class, stop that class and describe it»: no dice si eliminar el reader o dejarlo parcial; obligaría a inventar el estado aceptable.
4. «use the file name without .md as the id»: no distingue basename de ruta relativa ni resuelve colisiones entre carpetas; obligaría a inventar la normalización.
5. «read id plus every key that names another document»: qué claves “nombran otro documento” queda al juicio, aunque la regla (c) parece absorberlo; obligaría a inventar el conjunto.

DRY-RUN

Lecturas: `docs/v2/drafts/diseno-leer.md` (secciones 3 y 7), `runs/subagents/2026-11-16-leer/REPORT.md` y los cuatro readers del cut 1. Cambios previstos: copiar readers a `readers/`, añadir en `base.clj` el helper de referencias y las relaciones `has-KEY`, crear los readers de las diez clases, ajustar el glob de tasks, script de cobertura y `REPORT.md`. Dependencias: `git pull --ff-only`, binario kimun, pod treesitter, memcap, flock, bb/python3 y PATH. Ejecución hipotética: copiar desk a `$D`, `git init`/commit, `declare read` cronometrado, cobertura, iterar errores, `declare about`, commit. Validaciones futuras: las seis del TaskDoc (cobertura, errores, métricas por reader, normalización con tres ejemplos, about por clase, git limpio fuera del run folder y SLDB_1 sin cambios).

FUTURO

El coordinador revisa y hace push; un humano decide si un bug de kimun justifica parar una clase; si `declare read` supera 10 s, REPORT.md alimenta la decisión sobre `task-cache-of-readings-by-git-blob-hash`.
