# sensor-git: `stores changed --since COMMIT`

## Elección
Subcomando aparte `stores changed --since COMMIT`, no una opción de `stores sense`: la forma de la respuesta es otra y sense existe para rehashear todos los documentos; con `--since` saltaría ese trabajo y engañaría. `stores sense` queda intacto (verificado: sense_test, heartbeat_test, stores_test pasan; y la prueba 7 compara la respuesta de sense antes y después en un directorio sin git).

## Archivos
Nuevos: `src/kimun/host/git.cljc` (anillo 1, único que ejecuta git), `src/kimun/surface/changes.cljc` (compone los dos niveles; recibe lo que dijo git como datos), `test/kimun/cli/changed_test.cljc` (7 pruebas, 84 aserciones).
Tocados: `src/kimun/cli/commands/stores.cljc` (comando `changed`, opción `:since`), `src/kimun/cli/errors.cljc` (namespace `git` sale con 3), `src/kimun/cli/main.cljc` (línea de ayuda), `docs/v2/06-superficies-y-cli.md` (una fila), `test/kimun/cli/stores_test.cljc` (la lista de subcomandos conocidos ahora incluye `changed`; 1 línea).

## Comandos de git (solo lectura, `GIT_OPTIONAL_LOCKS=0`)
- `rev-parse --is-inside-work-tree` y `rev-parse --verify -q HEAD^{commit}`: ¿es un repo y tiene cabeza? Si no, `git/not-a-repository` o `git/no-commits`.
- `rev-parse --verify -q X^{commit}`: resolver X a sha completo; si no, `git/unknown-commit`.
- `diff --name-status -z -M --relative X -- .`: archivos cambiados entre X y el árbol de trabajo (commiteado o no, incluye lo en el índice), con renombres si git los detecta; `-z` para nombres con espacios; `--relative` para rutas respecto del directorio del proyecto.
- `ls-files -z --others --exclude-standard -- .`: nuevos sin seguimiento que git no ignora.
- `show X:./ruta`: el texto de antes. Es la única lectura de contenido, y solo de archivos cambiados con marca.
- No se usa `git status` (puede refrescar el índice); `:dirty` sale de comparar HEAD con el árbol de trabajo con los mismos dos comandos. Nunca se abre ni hashea un archivo por cuenta propia en el nivel de archivo. No hay add/commit/stash/checkout/gc.
- El directorio del store (`.kimun`) se excluye de la lista de cambiados.

## Forma de la respuesta (`:data`)
`{:since sha-completo :head sha :dirty bool :changed [{:path :change :from? :known :documents [nombres] :opened}] :marks [{:id :kind :label :locator :document :path :status :candidates :reason?}] :summary {:changed :known :opened :marks :by-status}}`.
`:change` = `:modified :added :deleted :renamed :copied :type-changed :untracked`. `:known` = el store registra un documento en esa ruta (aunque sin marcas); `:opened` = se leyó el archivo (solo cambiado y con marca). `:status` = `:anchored | :mutated | :ambiguous | :missing` con todos los candidatos de `mark-states`; o `:unknown` con `:reason` `:file-not-at-since` (el archivo no existía en X) / `:unresolved-at-since` (el localizador no resolvía en X). «Lo de antes» = el hash que el localizador tenía en el texto de X (anclas calculadas con las mismas funciones), no la huella guardada en la marca; después `mark-states` contra las anclas de ahora. Archivo borrado ⇒ `:missing` sin candidatos.

## Pruebas (cada una falla sin el cambio)
Mutación (`mutation.txt`): quitados `host/git.cljc`, `surface/changes.cljc` y los cambios de `src/` ⇒ `changed_test`: 30 fallos; restaurado con `git apply` y `diff -r src` idéntico (sin `git stash`).
1 nada cambió: sin cambiados, sin marcas y 0 llamadas a `anchors/anchors` (con el mismo contador sí se ve ≥1 cuando cambia un archivo marcado). 2 git ve lo que kimun no conoce: modificado no registrado y nuevo sin seguimiento salen `:known false`, otro registrado sin marcas `:known true`, ninguno abierto, 0 análisis. 3-4 anchored / mutated / ambiguous (con candidatos) / missing. 4b la huella guardada está atrasada respecto de X (`docs marks` propio da `:mutated`; contra X sale `:anchored`). 5 cambios sin commitear y commiteados dan las mismas marcas, `:dirty` correcto; archivo ausente en X ⇒ `:unknown :file-not-at-since`. 6 solo lectura: sha256 de todo `.kimun`, `git status --porcelain`, HEAD, stash, refs e índice idénticos. 7 sin git ⇒ `git/not-a-repository` y sense igual antes y después.

## Validación
`lint.txt`: `rings and docstrings ok: 105 files`. `oracle.txt`: 9 rows, 0 mismatches. `directed.txt`: changed, sense, heartbeat, marks (cli y surface), stores, tour y lint_test: 65 tests, 609 aserciones, 0 fallos (la primera tanda falló en `stores_test` por la lista de subcomandos; se ajustó la expectativa). No corrí `bb test` completo.

## Medición (`measurement.md`)
Arranque de la CLI (`stores show`) ≈ 0,5 s. N=500 (mínimo/mediana): sense 3,21/3,24 s (nada), 3,21/3,30 (1 sin marca), 3,43/3,47 (1 con marca); changed 0,92/0,94, 0,89/0,92, 0,94/1,02. N=2000: sense 11,67/12,91, 11,57/12,56, 11,52/11,55; changed 2,27/2,31, 2,22/2,28, 2,17/2,21. changed no depende de cuántos archivos cambiaron pero sí de N (abrir el store y listar documentos; inferencia, no medida aparte). Máquina cargada por otros ejecutores (load 1,5–3,1). Mis 12 s de sense a N=2000 no coinciden con los ≈38 s de MED1 y no lo reconcilio. `scripts/bench/gen_store.clj` está roto en este árbol (`tdocs/create-plan` ya no existe); usé un generador desechable en /tmp (borrado) con un include build en bloque.

## Límites y sin resolver
- Nada se persiste: X llega como argumento; el puntero «miré hasta este commit» es el corte siguiente.
- Un renombre solo se enlaza con un documento cuya ruta registrada es la vieja o la nueva.
- Un archivo borrado con marcas da `:missing` sin candidatos (no se busca si la marca se movió a otro archivo).
- Marcas `:item`/`:nested` de documentos tipados se leen con el payload extraído de ambos textos; no tengo prueba específica de esas.
- Si el directorio es un subdirectorio de un repo, funciona (rutas relativas); un directorio de pruebas dentro de otro repo rompería la prueba 7.
- Cambios sin commitear: se ven, pero el puntero sigue siendo un commit (pendiente de diseño, `diseno-declaraciones.md §8`).
- `stores changed` no cubre ninguna señal de `sense` (contrato, parser, corrupción): no sustituye a sense.
- Lo que sigue sin decidirse: nada que bloquee este corte; el código de salida 3 para errores `git/*` es mi elección.
