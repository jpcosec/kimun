# REPORT preguntar (corte 3b)

## Archivos
Nuevos: `src/kimun/surface/propositions.cljc`, `test/kimun/surface/propositions_test.cljc`,
`test/kimun/cli/declare_ask_test.cljc`.
Editados: `src/kimun/host/git.cljc` (+`list-files`, bloque al final), `src/kimun/cli/commands/declare.cljc`
(require, `spec` con `:exact`, bloque nuevo antes de `commands`, mapa y mensaje de error),
`src/kimun/cli/main.cljc` (3 líneas de ayuda), `docs/v2/06-superficies-y-cli.md` (3 filas),
`docs/v2/drafts/diseno-evaluar.md` (§10 «Decidido al implementar el corte 3b»).
No toqué `surface/declare.cljc`, `declared_anchors.cljc` ni `include.cljc` (reutilicé `include/compile-glob` y `normalize-path`, ya públicos y suficientes).

## Respuestas (EDN real, `declare about UI d.clj` con `(zork (files "src/**/*.py") UI.view.dashboard)` y `(zork card UI.view)`)
`{:data {:term "UI" :exact false :levels 1 :results [{:via :term :proposition {:r "zork" :a {:files "src/**/*.py"} :b {:term "UI.view.dashboard"} … :id …} :side :b :matched "UI.view.dashboard" :other {:files "src/**/*.py"}} {:via :term … :side :b :matched "UI.view" :other {:term "card"}}] :files ["d.clj"] :summary {:results 2}}}`
(`UI` nunca se escribió y aun así se puede preguntar por él.)
- `declare terms`: `{:terms [{:term :count :ancestors [..] :implicit bool}] :files :summary {:terms :implicit}}`; un ancestro no escrito: `:count 0 :implicit true`.
- `declare about T`: filas `{:via :term :proposition :side :matched :other}` y después `{:via :proposition :proposition :side :about id :other}` (un nivel, `:levels 1`).
- `declare about --path R`: `{:path :results [{:via :anchor :proposition :side :locator :other} | {:via :files :pattern :proposition :side :other}]}`.
- `declare members 'P'`: `{:pattern :members [rutas] :files-from :git|:walk :summary {:members}}`.

## Pruebas (10 deftests, 64 aserciones; directed 46 tests/325; joint 154 tests/1996, mis 2 ns + 20 de `test/kimun/cli/`)
Puras: jerarquía y ancestros implícitos, conteo una vez por proposición; `about` con/sin `--exact`, ambos lados, relaciones con nombres arbitrarios (`zork`, `qux/blip`), prefijo de texto no es ancestro; proposición sobre proposición a un nivel (no sigue el segundo, no repite); `about-path` por ancla y por patrón con `*`/`**`, dos clases por dos patrones al mismo archivo, `./` inicial; `members`. CLI: repo git temporal (seguidos + no seguidos, no ignorados, borrados fuera) y directorio sin git (`:walk`, sin `.git`/`.kimun`), cwd en subdirectorio, errores de uso, y que no se escribe nada (hash de todo el árbol antes/después, sin `.kimun`).
Mutación (`mutation.txt`, parche propio en /tmp, sin stash, restaurado con `diff`): sin ancestros implícitos (3 fallos), sin los más específicos (4), patrón por igualdad de cadena (7), repetir proposiciones (1).
`bb lint` ok (108 archivos), `bb oracle` ok (0 discrepancias). Directed: propositions, declare-ask, declare, declare-anchors, changed, include (cli), main, lint-test. No corrí `surface.include-test` (no toqué el comparador).

## Límites
- Sin FILE, `terms` y `about` dan `cli/usage`; la obtención está en la función privada `propositions-of-files` (cwd, files, nombre-de-comando) → proposiciones: ahí engancha el corte 3a.
- Glob solo `*`, `**`, `?` (el de include; sin `{a,b}` ni clases). No hay `--exact` para `--path`. Una ancla coincide solo por ruta exacta; no filtra por localizador.
- `about` por un término que sea `{:proposition}` no existe (solo por nombre); un literal no es término.
- Los términos se comparan como cadena exacta con el nombre; mayúsculas importan.
- `members` no lee declaraciones (el patrón se da a mano); lista git = `ls-files --cached --others --exclude-standard` filtrada a los que existen. Con cwd en un subdirectorio las rutas son relativas a él (y los patrones de las declaraciones se comparan contra eso).
- `walk` incluye ocultos (salvo `.git`/`.kimun`) y no sigue .gitignore.

## Ambigüedades / incomodidades
1. `--path` ya existe como opción global (`docs create`, «File of the new document»): reutilicé la clave sin redefinirla, así la ayuda automática de `--path` describe otra cosa. Habría que renombrar la de `docs create` o aceptar un desc genérico.
2. El enunciado pide `declare about --path RUTA` relativa al cwd, pero las rutas de las declaraciones (`(at …)`, `(files …)`) son relativas a la raíz del proyecto; desde un subdirectorio no coinciden (la prueba lo fija: 0 resultados y `--path ../x` es `cli/usage`). `declare anchors` ya tiene el mismo comportamiento (rutas relativas al cwd). Si el 3a lee `kimun/` desde la raíz del proyecto, conviene decidir si el cwd debe subir a la raíz.
3. «Un nivel» de proposición sobre proposición: elegí que una fila sea por proposición ya presente y no se repita; si una proposición habla de dos resultados aparece una vez por el primero. Una proposición que nombra el término y además habla de otro resultado se queda solo como `:via :term`.
4. «Cuántas proposiciones lo nombran» lo cuento por término exacto, sin sumar descendientes (`UI` tiene 0 aunque `UI.view` tenga 3); no estaba dicho. Un ancestro explícito también puede tener `:implicit false` y conteo propio.
5. `--exact` con `--path` se ignora en silencio; `members` ignora archivos extra.
6. `main-test` existe y pasa; `spec` del grupo `declare` ahora no es `{}`: el 3a/otros que lo editen deberán fusionar esa línea.

---
# Junta con el corte 3a (rebase sobre a6273fe0)

Conflictos: `declare.cljc` (reescrito juntando `read`, `anchors`, `add`, `terms`, `about`, `members`; `spec` con `:context` y `:exact`; mapa y mensaje con los seis), `main.cljc` y `06` (líneas de ambos; filas de `terms`/`about`/`members` ajustadas), `diseno-evaluar.md` (§10 = 3a, mi sección pasa a §11, con la decisión de la raíz).
Cambios de la junta:
- `propositions-of-files` desapareció: `terms` y `about` usan `read-set`; sin FILE leen `kimun/`. `:r` es cadena opaca.
- **Raíz** (`project-root`, una función en `commands/declare.cljc`, usada por los seis): el directorio más cercano, subiendo desde el cwd (symlinks resueltos), que contiene `kimun/` (`project-dir`); si ninguno, la raíz del repo git (`git/repo-root`, nueva, `rev-parse --show-toplevel`); si no, el cwd. Rutas de `(at/files)`, `--path`, `members` y salida: relativas a la raíz. FILE, `--to`, `--path`: relativos al cwd; un FILE fuera de la raíz sale con ruta absoluta; `--path` fuera es `cli/usage`. Toda respuesta lleva `:root` (ruta absoluta). El puntero `.kimun/seen.edn` de `anchors` ahora se busca en la raíz.
- `members` con más de un argumento y `about --path --exact`: `cli/usage`. `--path`: descripción global en `docs.cljc` válida para ambos usos (solo texto).
- Pruebas: ajusté `declare_ask_test` (`:r` calificado, errores de uso nuevos, subdirectorio con raíz git); nuevo `declare_root_test` (seis comandos desde un subdirectorio = desde la raíz, git sin `kimun/`, sin nada, `terms`/`about` sin FILE con dos contextos). Las de 3a no necesitaron cambios.
- Mutación nueva: raíz = cwd (22 fallos); sin proyecto sin FILE (4 fallos).
- Verificación: lint ok (108), oracle 9 filas 0 discrepancias, dirigidas 93 tests/756 aserciones 0 fallos (todos los de declare, propositions, declared-anchors, changed, main, golden, docs, typed-docs, lint-test), joint 168 tests/2163 aserciones 0 fallos (mis 3 + proyecto de 3a + 20 de `cli/`).
Nota: sigue en pie el límite de que un FILE nombrado que requiere un contexto no nombrado falla (3a).
