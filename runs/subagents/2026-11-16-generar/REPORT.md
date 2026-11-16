# REPORT generar (2026-11-16)

Worktree `/tmp/kimun-gen-wt` (sin commit). Evidencia en este directorio: `example.txt` (sesión real), `lint.txt` (ok, 110 archivos), `directed.txt` (105 pruebas, 875 aserciones, 0 fallos), `joint.txt` (143 pruebas, 1848 aserciones, 0 fallos: mis 2 namespaces + 20 de `test/kimun/cli/`), `mutation.txt`, `oracle.txt` (9 filas, 0 desajustes).

## Archivos
- Nuevo `src/kimun/surface/generate.cljc` (puro): `ask-for`, `run-one`, `run`, `forbidden`.
- `src/kimun/surface/declare.cljc`: macro `generated` en el vocabulario, `declare/recipes`, comprobación de conflictos, `run-limited` pasa a pública (la usa `generate`), docstring.
- `src/kimun/surface/propositions.cljc`: `about-path` da filas `:via :generated`.
- `src/kimun/cli/commands/declare.cljc`: `declare generate [--check]`, opción `--check`.
- `src/kimun/surface/mcp.cljc`: `kimun_declare_generate_check` (solo lectura) y `kimun_declare_generate` (escribe). `src/kimun/cli/main.cljc`: fila de ayuda.
- Docs: `docs/v2/06-superficies-y-cli.md` (fila `declare generate`, dos filas de MCP, «los seis» pasa a «los siete» comandos en el párrafo de la raíz, y «la única que escribe» de `declare_add` pasa a «escribe en un archivo de declaración»), `docs/v2/drafts/diseno-generar.md` (§5 «Decidido al implementar»), `docs/v2/drafts/diseno-evaluar.md` (una frase en §4 que apunta a `diseno-generar.md`).
- Pruebas nuevas: `test/kimun/surface/generate_test.cljc` (12), `test/kimun/cli/generate_test.cljc` (9). Ajustada: `test/kimun/cli/mcp_test.cljc` (lista y cuenta de herramientas 9 a 11; «solo declare_add escribe» pasa a «declare_add y declare_generate»; dos filas de argv). Ninguna prueba de paridad tocada.

## Cómo viaja la receta (sci)
`(generated ruta receta)` es una macro de sci (solo para conocer su línea) que expande a una función de `kimun.declare.impl`; esta valida la ruta, comprueba `fn?`, apunta `{:path :at :context :fn}` en el estado de ese archivo y devuelve el nodo `{:generated {:path p}}` (con la marca interna de nodo). Al terminar el archivo, el resultado lleva `:generated [{:path :at :context}]` como dato y las entradas con su `:fn` en los **metadatos** del mapa del resultado (`::declare/recipes`); `declare/recipes` las lee, una por ruta, la primera declaración. Entre archivos se comprueba el conflicto al final de `read-project`. `generate/run` crea `ask` y llama cada función con `declare/run-limited` (el hilo daemon con tope de tiempo del corte de arreglos) **después** de terminar la evaluación. Comprobado: una receta (sci `fn` creada durante `eval-string+`) se invoca fuera del `eval`, en otro hilo, con `*ns*` ligado a otro namespace (`a-recipe-called-after-the-evaluation-and-from-another-namespace-works`) y por la CLI real (`example.txt`): funciona en Babashka. Las funciones nunca salen en datos.

## `ask`, con un ejemplo real
Mapa de funciones: `:about`, `:about-path`, `:members`, `:terms`, `:text`. Del ejemplo de `example.txt` (`kimun/vistas.clj`):
```clojure
(defn decisiones [ask]
  (str "# Decisiones\n\n"
       (apply str (for [p ((:about ask) 'decision)
                        :let [t (:term (:other p))
                              src (first (for [q ((:about ask) t) :when (:anchor (:other q))] (:other q)))]]
                    (str "- " t (when src (str ": " ((:text ask) src))) "\n")))))
(isa (generated "docs/decisiones.md" decisiones) doc.indice)
```
genera `# Decisiones\n\n- cache-no-es-fuente: La caché no es una fuente.\n- generar-es-vista\n` (el texto del ancla sale de `docs/spec.md`). Cada fila de `:about` es `{:via :term :proposition p :side :b :matched "decision" :other {:term "…"}}`. `:about` recibe símbolo o cadena y opcionalmente `{:exact true}`; `:text` recibe un nodo `(at …)`/`{:anchor …}` y da archivo entero, sección, bloque (sus líneas) o rango (texto plano), o nil.

## Código de salida de `--check`
**3** cuando algún archivo es `:missing` o `:differs`, como fallo `declare/generated-differs` con los mismos datos que el éxito. Es la convención de `include check` (`include/drift`, 3: «lo declarado y lo que hay discrepan»), y `declare` ya es 3 por su namespace en `errors.cljc`. `stores health` no sirve: su veredicto va en `:status` de un `ok`. No se inventó ningún código. Todo igual: ok, exit 0.

## Qué cubren las pruebas
Puras: el ejemplo de §2 con el texto escrito a mano; el nodo `{:generated}` en una proposición y su `:id` igual con otra receta y distinto con otra ruta; evaluar no corre recetas (contador con `with-redefs` sobre `run-one` y una receta que lanzaría); `ask` con cada función; `:text` de archivo, sección, bloque, rango, no resuelve y archivo ausente; receta no-cadena / `nil` (`bad-recipe`), que lanza (`recipe`), que no termina (`timeout`, 300 ms); aislamiento (`slurp`, `spit`, `System/exit`, `java.io.File`, `require` rechazados por sci y sin efecto, y `ask` tiene solo cinco claves); conflicto entre archivos y dentro de uno (ambas posiciones) y la misma función dos veces (un registro); `doseq` con N generados; forma de la ruta (absoluta, `..`, `//`, carpeta, vacía, no cadena, `\`) y receta que no es función; invocar la receta con otro `*ns*`. CLI en proyectos temporales con y sin git: crea; segunda corrida `:unchanged` con árbol idéntico y sin temporales; cambiar la declaración da `:updated`; `--check` dice `:missing` / `:same` / `:differs` (exit 3), no escribe (árbol idéntico) y `generate` repone; receta que falla (no-cadena o lanza) no escribe ninguna aunque otra sea buena; rutas bajo `kimun/`, `.git/`, `.kimun/`, `../`, enlace que sale de la raíz, enlace dentro de la raíz que apunta a `.git`: rechazadas sin escribir nada; solo se tocan rutas declaradas (todo lo demás byte a byte igual); `:members` no cuenta los archivos generados; `about --path` del generado; `anchors` ignora `{:generated}`; FILE es `cli/usage`; proyecto sin declaraciones.
Mutación (`mutation.txt`, parche en `/tmp/generar-src.patch`, copia `/tmp/generar-src-backup`, sin `git stash`, `diff -r` idéntico): M1 sin conflicto, M2 receta corre al evaluar, M3b `--check` escribe, M4b sin comprobación de rutas, M5 sin comprobación de enlace, M6 generados en `members`, M7 `about --path` sin generated, M8 receta en el nodo, M9 acepta no-cadena, M11 escribe aunque sea igual, M12 `--check` ok aunque difiera: todas detectadas. M3 y M4 originales **sobrevivieron** (equivalentes: en check el estado nunca es `:created/:updated`; la comprobación de ruta real repite la de la declarada) y se reemplazaron por M3b/M4b. M10 (sin tope de tiempo por receta) no se corrió hasta el final: sin tope la prueba de la receta infinita no vuelve; se mató a mano, detectada por no terminación, no por aserción.

## Límites
- Un archivo generado cuya declaración desapareció no se detecta ni se borra (§4 de diseño, no hecho).
- Receta que no termina: el hilo se abandona (límite ya conocido de `diseno-evaluar.md §12`); en procesos largos (`mcp`, `serve`) cada timeout deja un núcleo ocupado. `kimun_declare_generate` por MCP escribe en disco: es la segunda herramienta que escribe.
- `:text` de sección/bloque depende de que la capa de bloques alinee con el AST (`aligned-spans`, el pod si está); si no alinea devuelve nil (no inventa rangos).
- La escritura es atómica por archivo, no entre archivos: si falla el tercer `rename` los dos primeros ya están (las validaciones y las recetas ocurren antes, así que solo un error de disco lo provoca).
- Rutas sin comprobar contra sistemas de archivos insensibles a mayúsculas (`Kimun/`).
- `declare generate` no está en `serve` (HTTP); no se pidió.
- Las dependencias de `read-set` no aportan recetas (el comando lee todo el proyecto, no hay FILE).

## Ambigüedades, incomodidades y errores del diseño
1. **`ask :about` y «misma forma de resultados»**: el ejemplo de §2 itera `((:about ask) 'decision)` y usa `(:other p)`, que solo cuadra con las **filas**; `props/about` devuelve un mapa envoltorio (`:term :exact :levels :results`). Entregué las filas (`:results`). Quien espere el envoltorio no lo tiene.
2. **`:members` y los archivos generados**: el diseño no dice si los generados cuentan. Contarlos hace que la primera y la segunda corrida difieran (la segunda ve el archivo escrito por la primera), contra «dos corridas dan los mismos bytes». Los excluí. Consecuencia: una receta no puede listar sus propios hermanos generados vía `:members`.
3. **Receta y el resto del archivo**: la receta es un cierre sci del archivo, así que ve sus `def`/`defn` y también el vocabulario (`at`, `files`, `relation`, `generated`) del namespace del archivo, no solo `ask`; «solo ve `ask`» vale respecto del anfitrión (disco, red, JVM), no del propio archivo. No probé qué pasa si una receta llama `(generated …)` (no lo bloqueé).
4. **Dónde falla `slurp` en una receta**: sci analiza el cuerpo al evaluar el archivo, así que `(fn [ask] (slurp …))` es `declare/unresolved` al **leer** (incluido `declare read`), no al generar. Cuadra con el aislamiento pero el error sale antes de lo que sugiere «error declarado al correr la receta».
5. **Conflicto con `identical?`**: dos `fn` literales con el mismo cuerpo son «distintas»; no hay noción de receta igual salvo la misma función. Un `doseq` que repita una ruta con un `(fn …)` nuevo cada vez es conflicto aunque produzca el mismo texto.
6. **Rutas prohibidas**: el diseño dice «nunca bajo `kimun/`…» pero no si se rechaza al declarar o al generar. Lo hice al generar (declarar un `generated "kimun/x"` evalúa bien y `about --path` lo muestra); `bad-generated` en declarar solo comprueba la forma. Quizá convenga rechazarlo ya al leer.
7. **`:generated` siempre presente** (vacío) en el resultado de cada archivo: ninguna prueba existente lo notó, pero cambia la forma de `declare read`.
8. **`:text` sobre secciones**: el camino debe incluir el título raíz (`["Spec" "Caché"]`, no `["Caché"]`); es la forma de `at` ya existente, pero tropezó en mi primer ejemplo y devuelve nil sin decir por qué. Un nil mudo es incómodo para quien escribe recetas.
9. **«Nada se borra solo»** deja huérfanos silenciosos: renombrar una ruta generada deja el archivo viejo; `--check` no lo ve.
10. **Mensajes de `--check` con exit 3**: el éxito de `--check` con diferencias es un *fallo* de envoltorio (`ok false`) para poder dar exit 3; el JSON tiene los datos en `error.data`, no en `data`. Es lo que hace `include check`, pero obliga al cliente a mirar dos sitios.
