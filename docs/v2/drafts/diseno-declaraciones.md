# Declaraciones como formas de Clojure; el store como índice derivado

Borrador, 2026-11-15, desde `docs/v2/drafts/sesion-2026-11-15.md`. Entre «» va el dueño, textual;
entre "" texto de documentos del repo (las reglas de `14 §21` son del concilio, no del dueño). El
resto es **propuesta**, salvo lo que cito con su ruta.

## 0. Un ejemplo para empezar

Un proyecto chico: `docs/spec.md`, `src/app/config.py` y `src/ui/dashboard.py`. Kimun guarda lo que
declara en un directorio propio, `kimun/`, que se commitea. Así se vería el archivo:

```clojure
;; kimun/proyecto.clj — kimun lo LEE con el lector de Clojure; no lo evalúa
(defproyecto mi-proyecto
  "Lo que kimun mira de este repo. Reemplaza a kimun.include.edn."
  {:indice {:ruta ".kimun" :git :ignore}
   :documentos [{:glob "docs/**/*.md"} {:glob "src/**/*.py"}]})

(defclase config            "Archivos que configuran."    {:eje :funcion})
(defclase UI.view           "Vistas de la interfaz."      {:eje :contenido})
(defclase UI.view.dashboard "El tablero principal."       {:eje :contenido}) ; ⊂ UI.view por el nombre
(defclase zettel            "Fichas al modo zettelkasten." {:eje :forma})

(defancla spec-md      "La spec."      {:id "01JD3P4K…" :ruta "docs/spec.md"})
(defancla config-py    "Config."       {:id "01JD3P7M…" :ruta "src/app/config.py" :clases [config]})
(defancla dashboard-py "El tablero."   {:id "01JD3P9A…" :ruta "src/ui/dashboard.py" :clases [UI.view.dashboard]})

(defficha cache-no-es-fuente
  "Lo que se recalcula no tiene autoridad: se puede borrar sin perder conocimiento."
  {:id "01JD3QB2…" :clases [zettel]})

(defmarca decision-cache
  "El párrafo donde la spec decide no versionar la caché."
  {:id "01JD3QF0…" :en spec-md :localizador {:section [{:title "Caché" :n 0}] :type :paragraph :n 1}
   :huella "9f2c…" :quien "jp" :cuando "2026-11-15"})

(defrel resume-decision
  "La ficha resume lo que la spec decide."
  {:id "01JD3QK7…" :r :resume :a cache-no-es-fuente :b decision-cache :quien "jp" :cuando "2026-11-15"})

(reancla decision-cache
  "Al reescribir la spec, la sección «Caché» pasó a llamarse «Caché derivada»."
  {:id "01JD3R02…" :previo "01JD3QF0…" :huella "9f2c…" :quien "jp" :cuando "2026-11-16"
   :localizador {:section [{:title "Caché derivada" :n 0}] :type :paragraph :n 1}})
```

Esto es lo que kimun derivaría. Va en `.kimun/`, no se commitea y se puede borrar:

```clojure
{:nodos {"01JD3P9A…" {:tipo :ancla :ruta "src/ui/dashboard.py"
                      :clases #{:doc/py UI.view.dashboard UI.view}} ; :doc/py lo observa por extensión
         "01JD3QF0…" {:tipo :marca :en "01JD3P4K…" :estado :anchored
                      :localizador {:section [{:title "Caché derivada" :n 0}] …}}  ; resultado de plegar los eventos
         "01JD3QK7…" {:tipo :relacion :r :resume :a "01JD3QB2…" :b "01JD3QF0…"}
         "01JD3R02…" {:tipo :reanclaje :sobre "01JD3QF0…" :porque "Al reescribir…"}}
 :aristas [["01JD3QF0…" :contenida-en "01JD3P4K…"] ["01JD3R02…" :reancla "01JD3QF0…"] …]}
```

Y `kimun/observado.edn`, generado por kimun: `{"01JD3QF0…" {:huella "9f2c…" :visto "2026-11-16"}}` (§6).

La idea: lo que **alguien declara** (qué es, por qué, quién, cuándo) es una forma y se commitea; lo que
**cambia solo con la fuente** (huella vigente, estado, candidatos) se observa y se deriva; la historia
la lleva git.

## 1. Cómo se ve una declaración

- **Forma = `(defX nombre "docstring" {metadatos})`.** Es la misma forma que `defn`: símbolo,
  docstring y mapa de atributos. Esa cabeza ya es Clojure y no invento sintaxis. Lo que define la
  forma es el símbolo de cabeza (`defmarca`, `defrel`…), que es la **clase de la declaración**.
- **Clase**, `defclase`: lleva un `:eje` (`:funcion`, `:contenido`…); la jerarquía va por el nombre con
  puntos, sin `:base`. El eje del tipo de documento (`:doc/py`, `:doc/md`) no se declara, se observa:
  «un documento fuente tiene una clase según qué tipo de documento es» (dueño).
- **Nodo estructural**, `defancla`: un documento (`:ruta`), o dentro de uno (`:en` + `:localizador`, el
  que imprime hoy `docs anchors`): sección, bloque, rango (`{:block … :range [s e]}`, S2b-r). Una
  ejecución (`:ejecucion {:cmd …}`) queda sin diseñar: su contrato sigue abierto (`14 §21`). `defmarca`
  es una `defancla` con `:en`; el nombre aparte es comodidad.
- **Ficha**, `defficha`: nodo puro, su contenido es el docstring. **Relación**, `defrel`: nodo con id y
  `:r :a :b` («lo importante no es el nodo mismo, son sus relaciones», que «a su vez son nodos»).
- **Lo que pasó**: `reancla`, `cierra`, `traslada` (`{:de doc-a :a doc-b}`). Sin `def`: no nombran una
  cosa nueva, nombran un evento sobre una unidad, y llevan `:previo` (§2).
- **Docstring tipado**, propuesta en dos capas. (1) Lo que se valida a máquina es el **mapa de
  atributos**: cada clase de declaración exige sus claves (todas `:id`; `defmarca` además
  `:en :localizador :huella`; los eventos `:previo :quien :cuando`). "qué" es la cabeza de la forma;
  "por qué" es el docstring, obligatorio en los eventos como hoy `--reason` (`cli/commands/docs.cljc:429`,
  `:699`, `:747`). (2) Después, el docstring puede seguir la **plantilla de su clase**, la idea del dueño:
  cada clase permite «un "template" donde podemos documentar semánticamente (y que en el caso de los
  archivos de código por ejemplo puede ir dentro del docstring)». Se leería como Markdown con el modelo
  de anclaje de esa clase (spec 16); depende de M1 y no entra en el primer corte.

## 2. Dónde vive la identidad

- El id va **escrito en `:id`**, opaco, un ULID como hoy (`cli/commands/docs.cljc:86`, `:760`). El
  símbolo **no** es la identidad: es un nombre local para citarse (`:en spec-md`), como una var;
  renombrarlo no cambia la unidad. Lo exige D0: el id es "opaco al contenido, a la ruta" y "estable de
  por vida" (`14 §21` regla 7, l. 464; regla del concilio).
- **Quién lo acuña:** el escritor de formas de kimun (§4). Quien escribe a mano deja `:id` vacío y
  `kimun decl fix` lo completa. Nunca se recalcula.
- **Choques:** el mismo `:id` en dos formas `def…` es un error al leer (`:decl/id-duplicado`), como
  `:include/conflict`; un símbolo sin definir también, como una var que no resuelve. Dos eventos con el
  mismo `:previo` son una **bifurcación** (dos ramas de git re-anclaron la misma unidad): kimun la
  informa y no elige. Es la regla 4, "una unidad no se bifurca", aplicada a las declaraciones.
- También el **id de documento** (hoy K1a lo arrastra por ruta, `include.cljc:306`) queda escrito, en
  `defancla` con `:ruta`; las de un glob las **genera kimun** al ver el archivo por primera vez (§4).

## 3. Dónde viven los archivos

- **Propuesta:** un directorio `kimun/` en la raíz, configurable. Al proyecto no se le pide nada más
  (ni se tocan sus fuentes); si no quiere ni eso, el directorio vive **fuera**
  (`KIMUN_DECL=~/kimun/mi-proyecto`): kimun solo necesita una ruta («no debería exigir nada al resto»).
- **`kimun.include.edn` se convierte en `defproyecto`**, primera forma del directorio, con las mismas
  claves (`13 §1`): no quedan dos fronteras para "qué mira kimun". En la transición se leen las dos y es
  error que existan ambas.
- **Organización, propuesta:** el criterio del dueño es «clean_code, NO duplicar fronteras». Se agrupa
  por la cosa de la que hablan las declaraciones, no por su tipo de forma: `kimun/proyecto.clj` (el
  proyecto y las clases); `kimun/docs/<ruta-del-documento>.clj` (sus anclas, marcas y eventos: es lo
  que más se toca, y un archivo por documento deja los conflictos de merge locales);
  `kimun/fichas/<nombre>.clj`; las relaciones, en el archivo de su término `:a`; y
  `kimun/observado.edn`, que genera kimun (§6).
- **Código Clojure del propio proyecto:** el dueño preguntó si no bastaría con «usar docstrings tipados
  dentro del código mismo». Propongo que kimun **lea** también los metadatos `:kimun/*` de un `defn`
  (por ejemplo `(defn f "…" {:kimun/clases [UI.view]} …)`) como declaraciones del mismo tipo. Es lectura
  opcional: kimun nunca escribe en las fuentes. Ver la decisión 3.

## 4. Quién escribe las formas

- **Los comandos dejan de escribir en el store y pasan a generar o editar formas.** Kimun «genera».
  `docs mark` agrega una `defmarca`; `docs mark --unit … --reason`, una `reancla`; `docs unmark`, una
  `cierra`; `docs mark --from`, una `traslada`; `docs reanchor`, una `reancla` sobre la `defancla`;
  luego se actualiza el índice. Una persona puede escribir lo mismo a mano; `kimun decl check` valida
  (ids, símbolos, claves, `:previo`, bifurcaciones) sin escribir.
- **Lo que hoy se acuña solo también genera forma** en el archivo de su documento: el id de documento
  al entrar por glob y las unidades de campo de K1c (`surface/docs.cljc:718`, `:928`). Ver R3.
- **Formato y comentarios:** se edita con `rewrite-clj` (árbol que conserva espacios y comentarios;
  creo que viene en babashka, sin verificar; el repo no lo usa hoy). Kimun casi siempre **agrega** al
  final; solo edita en el lugar `observado.edn`, que es suyo. Si el archivo cambió desde que lo leyó, se
  niega (`:decl/stale`), como `:store/stale` hoy (`10 §7`).

## 5. Qué es el store entonces

- **Índice derivado** = f(declaraciones, `observado.edn`, fuentes): se borra y se reconstruye
  (`00-nucleo` 6.3). Desaparece `identity-registry` (`include.cljc:306`): los ids están en las formas.
- **Costo:** reconstruir es reparsear cada documento trackeado, del orden de un latido: ≈38 s con 2000
  documentos sintéticos (`runs/subagents/2026-11-15-MED1/REPORT.md`; la reconstrucción en sí no está
  medida). Por eso **tiene que ser incremental**: el índice guarda la huella de cada archivo de
  declaración y de cada fuente y recalcula solo lo que cambió. La caché por `(fingerprint, clase,
  parser)` (`10 §11`, l. 371) ya cubre las fuentes; falta lo mismo por archivo de declaración.
- **Kernel (plan, revisión, log, replay):** en el primer corte, **detalle interno** del índice
  (`include build` ya arma todo en una revisión, `include.cljc:346`). Después: el plan sirve como diff a
  aplicar al índice; log, replay y `journal` dejan de ser registro (la fila `PLANES` pierde su objeto);
  `:declared` sale de `meta-engines` (`surface/docs.cljc:608`, `:955-958`). Si el log queda como
  garantía contra caídas se decide con el código delante; no es decisión del dueño.

## 6. Qué observa el sensor: declaración frente a observación

Este es el centro del diseño. `14 §21` (l. 441-446) ya separa cuatro cosas: **unidad**, **anclaje**
(localizador + contrato), **observación** ("afirmación fechada de lo leído (huella…)") y realidad.
Propongo esta correspondencia:

| qué | ejemplo | quién lo cambia | dónde vive |
|---|---|---|---|
| unidad, anclaje y lo que pasó | `:id`, `:localizador`, `reancla` | alguien, al declarar | forma (se commitea) |
| huella **al declarar** | `:huella` de `defmarca` | nadie: es parte de la evidencia del acto | forma |
| huella **vigente registrada** | la de la última escritura aceptada | kimun, con cada `docs update` (hoy `advance-marks`, `anchors.cljc:355`) | `observado.edn` o el índice (decisión 1) |
| estado y candidatos | `:mutated`, `[{:locator … :reason :hash}]` | nadie: se derivan siempre | índice (`mark-states`, `anchors.cljc:313`) |

Por qué importa: hoy una escritura normal **reescribe** la huella de una marca `mutated`
(`surface/docs.cljc:932-936`) sin que nadie declare nada. Así manda la regla 2: "mientras el anclaje
resuelve, un cambio es una mutación". Esa huella no es una declaración. Pero si solo vive en un índice
que no se commitea, un clon nuevo reconstruye leyendo el texto actual, todo sale `anchored` y una
mutación ocurrida entretanto **queda oculta**. Eso choca con `00-nucleo` 2.4 ("ninguna pérdida de
soporte se oculta"). También choca con la cadena que describe el dueño: conocimiento en concordancia
con las specs «gracias al merkle_tree que debería detectar cualquier variación».

Hoy ya pasa a nivel de documento: `include build --rebuild` re-registra el fingerprint actual
(`include.cljc:436`); las marcas arrastradas conservan su huella vieja (`:439`).

Tres opciones (decisión 1). **(A)** Huella vigente dentro de la forma: cada `docs update` que avanza
una marca reescribe la forma; mezcla declaración con observación y ensucia el diff de lo declarado.
**(B)** Solo en el índice: lo más simple, pero rompe 2.4 tras un clon o un `--rebuild`. **(C)** La forma
lleva la huella del acto de declarar, inmutable, y la vigente vive en `kimun/observado.edn`, que genera
kimun (una línea por id, ordenado, apto para diff); el proyecto decide si lo commitea
(`:observado {:git :track|:ignore}`, como `:store :git` hoy). Si lo commitea, `git diff` muestra justo
lo que mutó y se aceptó; si no, tras un clon la referencia es la huella de la declaración y lo mutado
desde entonces sale `mutated`: honesto, nada se oculta. **Recomiendo (C):** cada dato tiene un único
dueño y no se duplica frontera.

## 7. Qué pasa con la historia

**Se pierde:**
- `journal verify` por replay, que prueba que cada revisión sale del log (`kernel/store.cljc:346`,
  `surface/journal.cljc:106`). Git da una cadena de hashes, pero solo de lo commiteado: lo que no se
  commitea no tiene historia, y un proyecto sin git no tiene ninguna.
- **Ver el grafo en una revisión vieja** sin trabajo: hoy es leer una revisión; ahora es
  `git checkout <c>` y reconstruir el índice (del orden de un latido completo), salvo que se guarden
  índices por commit.
- **Validar al escribir:** hoy el kernel rechaza un plan inválido antes de que exista. Una forma escrita
  a mano puede ser inválida, y el error aparece más tarde, al leer (`decl check`, hook pre-commit).
- **Atomicidad:** un traslado es hoy **una** revisión que toca dos árboles (`surface/docs.cljc:1321`).
  Ahora es una forma; si una caída deja algo a medio escribir, la lectura lo detecta (unidad en dos
  lugares) pero no lo evita.
- **Actor y fecha garantizados:** hoy los sella el plan; en una forma son texto que cualquiera edita.
- **La serie de observaciones:** hoy cada mutación aceptada es una revisión. Con (C) queda solo en git,
  y solo si `observado.edn` se commitea.

**Se gana:** lo declarado se lee, se revisa en un PR, se fusiona y se revierte con las herramientas de
siempre; el store se puede tirar; se acaban el arrastre de identidades, los respaldos `.bak-…` y las
migraciones de formato del registro (el formato del índice pasa a ser asunto de caché); y kimun se lee
a sí mismo (§8).

## 8. Homoiconicidad

- Las declaraciones son datos: kimun las lee con `clojure.tools.reader` (el lector que ya usa
  `scripts/check_rings.clj:8`), sin evaluar nada ni necesitar las macros `def…`. Un `kimun.decl` mínimo
  puede dar macros que hagan `def` de una var con el mapa como metadatos, para usar `doc` en un REPL:
  la misma forma, leída de dos maneras (decisión 4).
- **Para Clojure, el parser es el propio lector** (el dueño: «no puede tree-sitter armar el AST de
  todo?»): el AST ya es el dato. `tools.reader` da línea y columna en metadatos; `rewrite-clj`, el árbol
  concreto con comentarios cuando hace falta un span exacto.
- **Lo que falta hoy** (informe del grafo, §2 y H8): un lector de fuentes `.clj/.cljc` que emita nodos
  `ns`/var con docstring y metadatos `:kimun/*` y aristas `:require` resueltas; que esos hechos sean
  nodos y aristas del índice, no payload de un documento con modelo (informe §2, punto 2); y que
  `kimun/` sea un documento fuente más (`:doc/clj`). Entonces el análisis del informe es un comando, y
  kimun indexa sus declaraciones y su código con el mismo lector.

## 9. Camino: primer corte (D1)

**Objetivo:** las marcas y sus declaraciones de hoy se leen y se escriben como formas, y un índice
reconstruido desde cero a partir de formas y fuentes **es igual** al de hoy, sin `identity-registry`.

1. **`kimun.decl.read`, puro:** lee `kimun/**/*.clj`, valida y **pliega** los eventos. Da, por ruta,
   los mismos `:units {campo id}`, `:marks {id {:kind :locator :hash :label}}`, id de documento y de
   árbol que hoy `track-plan` recibe del registro (`include.cljc:436-441`); solo cambia su origen.
2. **`kimun.decl.write`** (`rewrite-clj`): agrega formas y regenera `observado.edn`.
3. **`include build` toma la identidad de `decl.read`**, no de `identity-registry`. `docs mark`,
   `unmark`, `reanchor` y `mark --from` escriben la forma **y además** el store, detrás de una opción.
   Reversible: si se borra `kimun/`, todo queda como antes.
4. **Prueba de equivalencia:** con los escenarios de `unit_close_move_test`, `nested_units_test` y
   `range_marks_test`, se construye por comandos, se vuelca la proyección (ids de documento y árbol,
   `:units`, `:marks`, estados y candidatos de `docs marks`), se borra `.kimun/`, se reconstruye desde
   las formas y se compara. Se comparan **valores, no bytes**: los ids de revisión cambian con la
   fecha. Un caso más: mutar la fuente con el índice borrado tiene que dar `mutated`, no `anchored` (§6).
5. Cuando la prueba pase, en un corte aparte, se retira `identity-registry` y `:declared` deja de ir en
   `meta-engines`.

**Se conserva de lo hecho el 2026-11-15:** `surface/markdown/anchors.cljc` entero (localizadores,
`mark-states`, `advance-marks`, candidatos, spans), `markdown/offsets`, los selectores de spec 16, los
rechazos tipados de los comandos; `mark-plan`/`move-unit-plan` pasan a aplicar el pliegue al índice.

**Se reubica:** la acuñación (`mint-unit`) pasa al escritor de formas; `:declared` y `--reason` pasan
al docstring y los atributos de la forma; el arrastre de identidades desaparece; las entradas
`:declared` de `journal` pasan a ser historia de git.

## 10. Qué no resuelve

- **Capa G y D-G:** `defrel` da la forma de una relación-nodo, no su álgebra, contexto ni verdad
  (`00-nucleo` 4.2, abierta): una relación declarada es un registro, no un hecho evaluado.
- **Clases por varios ejes:** `defclase` es solo la sintaxis; faltan qué ejes existen, cómo se asignan
  solos (selectores) y cómo conviven con spec 16 (`:base`, `derive`).
- **Fichas y materialización:** cómo se materializa una `defficha` (UI, CLI, plantillas de SLDB; filas
  `F10`, `M1e`) va aparte.
- **Linaje:** copiar, dividir, fusionar (`LINAJE`, `RENOMBRAR`) serían `defrel` (`:continua`,
  `:deriva-de`); su semántica espera esas filas.
- **Borrado:** «se marca como "borrado"», con política «por cada proyecto y según cada tipo de dato»
  (dueño). Encaja como `:retiro` en `defproyecto` más una forma `retira`; no está diseñado.
- **Latido incremental** (`LATIDO`): este diseño lo necesita y no lo resuelve.

## Decisiones para el dueño

1. **Dónde vive la huella vigente de una marca (§6).** (A) en la forma, reescrita; (B) solo en el
   índice; (C) la huella del acto en la forma más `observado.edn` generado, cuyo commit decide el
   proyecto. **Recomiendo C:** es la única que no oculta una mutación después de un clon sin mezclar lo
   declarado con lo observado.
2. **Lo que pasó: eventos o estado.** (E) cada `reancla`, `cierra` y `traslada` es una forma que se
   queda en el archivo, con su porqué, y el estado se obtiene plegándolas; (S) la `defmarca` se edita
   en el lugar y el porqué queda en el mensaje del commit. **Recomiendo E:** el dueño pidió que «el
   porqué de cada una» viva en la forma; además `:previo` detecta las bifurcaciones y nada se reescribe
   (`00-nucleo` 6.4). Costo: los archivos crecen.
3. **Dónde viven las declaraciones.** (a) solo en un directorio `kimun/` (o fuera del repo); (b) además
   se leen los metadatos `:kimun/*` del código Clojure del proyecto. **Recomiendo (a) siempre y (b) solo
   para leer:** (b) responde a la pregunta del dueño sobre docstrings en el código, pero kimun nunca
   escribe en las fuentes.
4. **Leer o evaluar.** (L) se leen como datos; (V) se cargan como código. **Recomiendo L:** es seguro,
   no depende de nada y la homoiconicidad lo permite. Las macros quedan como comodidad para el REPL.
5. **`:quien` y `:cuando` en la forma o desde git.** Sacarlos de git evita duplicarlos, pero exige git
   y `blame` cambia al reformatear. **Recomiendo ponerlos en la forma** y que kimun los escriba.

## Lo que se pierde y los riesgos

- **R1, pérdidas reales:** las de §7.
- **R2, merge:** dos ramas que declaran sobre el mismo documento chocan en su archivo (poco, al final,
  pero chocan); una bifurcación por `:previo` se detecta y no se resuelve sola.
- **R3, volumen generado:** una `defancla` por documento de glob más una forma por unidad de campo de
  K1c son miles de líneas en el repo a 2000 documentos. La salida sería que K1c acuñe por declaración,
  como las marcas; eso toca su contrato (inferencia del equipo, no del dueño).
- **R4, costo:** sin índice incremental, cada clon o `--rebuild` reparsea todo (latido ≈38 s/2000 en
  MED1; la reconstrucción no está medida).
- **R5, mano humana:** un `:id` copiado duplica una unidad; `decl check` lo ve, pero después.
- **R6, tercer artefacto:** sin commitear `observado.edn` se pierde la serie de observaciones; si se
  commitea, kimun escribe en el repo en cada aceptación.
- **R7, sin verificar:** que `rewrite-clj` venga en la babashka fijada.
