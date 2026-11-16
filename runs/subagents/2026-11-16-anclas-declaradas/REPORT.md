# anclas-declaradas (corte 2 de diseno-evaluar.md §5)

## Archivos (worktree /tmp/kimun-dmarks-wt, sin commit)
- NUEVO `src/kimun/surface/declared_anchors.cljc` (puro: `translate`, `states`).
- `src/kimun/cli/commands/declare.cljc` (+ `declare anchors`), `src/kimun/cli/main.cljc` (1 línea de ayuda).
- NUEVOS `test/kimun/surface/declared_anchors_test.cljc` (6 pruebas, 74 aserciones) y `test/kimun/cli/declare_anchors_test.cljc` (7 pruebas, 47).
- Docs: fila en `docs/v2/06-superficies-y-cli.md`; corte 2 hecho + §8 "Decidido al implementar el corte 2" en `docs/v2/drafts/diseno-evaluar.md` (el brief decía §7, que ya existía para el corte 1; usé §8).
- No tocados: stores.cljc, changes.cljc, git.cljc, seen.cljc, declare.cljc (surface), diagnostics, heartbeat, signals.

## Formas de `(at …)` -> localizador de anchors
| escritura | kind | localizador de `anchors` |
|---|---|---|
| `(at "r")` | `:file` | hash `canon/digest` del texto completo (cualquier tipo de archivo; no pasa por anchors) |
| `(at "r" ["T" "S"])` | `:section` | `[{:title "T" :n 0} {:title "S" :n 0}]` |
| `(at "r" ["T" ["S" 2]])` | `:section` | `… {:title "S" :n 1}` (K 1-based -> n = K-1) |
| `(at "r" [camino] {:type :paragraph :n 1})` | `:block` | `{:section <camino> :type :paragraph :n 0}` (n 1-based -> n-1; `[]` = antes del primer título) |
| `(at "r" [camino] {:type … :n …} [s e])` | `:span` | `{:block <loc del bloque> :range [s e]}` (graphemes, fin excluido, como `span-anchor`) |
Tipos de bloque: heading paragraph list code quote opaque. `(at "r" [])` solo es mal formado (la sección raíz no es un ancla de anchors). Mal formado, ruta absoluta o con `..`: `declare/bad-locator` (exit 3) con `:file` y `:line` (de la declaración).

## Ejemplo real (`declare anchors decl.clj --since HEAD`, párrafo 2 editado; recortado)
`{:ok true :command "declare anchors" :store nil :data {:anchors [{:role :b :r "anchors" :other {:term "p2"} :anchor {:path "docs/spec.md" :locator [["Spec"] {:type :paragraph :n 2}]} :kind :block :declared-at {:file "decl.clj" :line 3} :status :mutated :hash "3cc9…" :before "1d3f…" :candidates [] :opened true} {… :other {:term "w"} :kind :span :locator [["Spec"] {:type :paragraph :n 1} [0 2]] :status :anchored :opened true}] :summary {:files 1 :anchors 2 :opened 2 :by-status {:mutated 1 :anchored 1}} :since "f4ae…" :pointer nil :head "f4ae…"}}`

## Pruebas
- Puras (read-now/read-then inyectados): cada forma resuelve y da hashes distintos; hermano con título repetido; ordinales 1-based; `:file-missing`; archivo entero `.txt` (hash = digest del texto) y `:unsupported` con y sin comparación; mutated/anchored/missing/ambiguous (candidatos `:locator` y `:hash`, ninguno elegido); rango; `:unknown` (`:file-not-at-since`, `:unresolved-at-since`); un archivo no reportado no llama a read-now ni read-then (contador) y sale `:anchored :opened false`; 10 localizadores mal formados con archivo y línea.
- CLI sobre repos git temporales: sin store ni commit -> `:resolved` (también fuera de git); editar el párrafo anclado -> `:mutated` y los otros `:anchored`, `data.txt` no abierto; sin commitear == commiteado; desde el commit nuevo no se abre nada; mover (subir de nivel) y borrar sección -> `:missing`; archivo borrado; con store+puntero usa el puntero (`:pointer`), `--since` lo gana, store sin puntero -> sin comparación, puntero dañado (sha desconocido y basura) -> `git/bad-pointer`; `git/not-a-repository` y `git/unknown-commit`; `declare/bad-locator` con línea por CLI; sin FILE -> usage (6); solo lectura (árbol de trabajo idéntico + status/head/stash/refs/index de git idénticos).
- Mutación (`mutation.txt`, sin stash; src restaurado y verificado con diff -r): 7 mutaciones, todas detectadas (abrir archivos no cambiados, ordinal 0-based, invertir anchored/mutated en archivo entero, ignorar el puntero, permitir `..`, perder candidatos, quitar `:unsupported`).

## Resultados
`bb lint` ok (107 files); `bb oracle` 9 filas, 0 mismatches; dirigidas (mis 2 ns + declare-test x2 + changed + seen + main + lint-test): 50 pruebas / 411 aserciones, 0 fallos; anchors-test + range-marks-test: 9 / 162, 0 fallos; **conjunta** (mis 2 ns + 20 de test/kimun/cli en una sola invocación, `joint.txt`): 162 pruebas / 1860 aserciones, 0 fallos. No corrí `bb test` completo. Ningún test existente ajustado.

## Límites
- Sin `:lines` en las filas ni en candidatos: se leen las anclas sin los `:spans` de la capa de bloques (como hace `docs`).
- Sin comparación no hay candidatos jamás (anchors los propone desde un hash y no hay).
- Solo Markdown con localizador interno; código = etapa 3. Un `.md` se detecta por extensión.
- `declare read` no valida la forma de los localizadores (lo hace `declare anchors`/`states`).
- Puntero: solo `<cwd>/.kimun/seen.edn` (no sube directorios). `:head` solo con comparación.
- `(files …)` no se expande ni se evalúa aquí. Un renombrado reporta ruta nueva y vieja como cambiadas; un ancla con la ruta vieja sale `:missing`, con la nueva `:unknown :file-not-at-since`.
- Solo se ven las anclas en `:a`/`:b` directos; las de una proposición usada como término ya aparecen en su propia proposición.

## Ambigüedades / incomodidades del diseño
1. «adapta a como anchors numera»: anchors cuenta desde 0 y el ejemplo del diseño usa `:n 1` para «el primero»; elegí 1-based en la declaración (títulos y bloques) y resto uno. Si el dueño prefiere 0-based, es una línea (`block-part`, `path-element`).
2. `:term-otro-lado` lo llamé `:other` (el nodo etiquetado completo del otro lado, no solo el término). Añadí `:kind` y `:hash`/`:before`.
3. «Sin comparación … con candidatos si anchors los propone»: en la práctica nunca los hay (ver Límites).
4. Un archivo no Markdown con localizador interno sale `:unsupported` incluso si git no lo reporta; elegí priorizar eso sobre «no cambiado -> anchored».
5. Orden de errores git: con `--since` fuera de git llamo primero `repo-head` (para `git/not-a-repository`); `resolve-commit` solo daría `unknown-commit`.
6. Mover una sección dentro del mismo archivo no se distingue de borrarla salvo que el hash coincida en otro lugar (entonces `:missing` con candidato `:hash`); al subir de nivel el hash del encabezado cambia, así que no hay candidato. Es la tabla de `mark-states`, no algo nuevo.
7. El brief pedía añadir a §7 de diseno-evaluar.md; §7 ya era del corte 1, así que creé §8.
8. `git/git.cljc` no necesitó cambios.
