# Evaluar declaraciones

Borrador de Claude, 2026-11-16. Sigue a `diseno-declaraciones.md` y abre la etapa 2 de
`plan-producto.md`. Entre «» va el dueño (`sesion-2026-11-15.md`); con número, `00-nucleo.md`. Lo
demás es mi lectura y mis elecciones, marcadas como tales. Nada está probado.

## 1. Qué se evalúa

Un archivo de declaración es un archivo de Clojure cualquiera, en el disco del proyecto. Kimun lo
**evalúa**; no lo interpreta como datos. Evaluarlo produce proposiciones `(R A B)` en contexto (**1**),
y nada más: evaluar no escribe en ninguna parte.

```clojure
(ns proyecto.decisiones)                       ; el contexto

(relation anchors "A está en B.")              ; una relación se define
(relation isa "A es de la clase B.")
(relation summarizes "A resume B.")

(isa (files "src/ui/**/*.py") UI.component)    ; una declaración, muchos archivos
(anchors cache-decision (at "docs/spec.md" ["Caché"] {:type :paragraph :n 1}))
(summarizes cache-is-not-a-source cache-decision)
(supports (summarizes cache-is-not-a-source cache-decision) owner-2026-11-15)
```

## 2. Las piezas (elecciones mías)

- **El contexto es el `ns`.** Es una etiqueta más («contexto, namespace, clase, tipo, etc. da lo
  mismo»); Clojure ya la trae y no hay que inventar otra forma.
- **Una relación se define** con `(relation nombre "docstring")`. Desde ahí `nombre` es una forma que,
  evaluada, produce una proposición. Usar una relación no definida es un error: es código que corre
  (**7.1**), no texto.
- **Los términos no se declaran.** Un símbolo desnudo en una proposición es un término: `cache-decision`,
  `UI.component`. No hay que definirlo antes («no debería exigir nada al resto»); existe porque algo
  lo relaciona (**1.1**). Los puntos son jerarquía: `UI.view.dashboard` también es `UI.view`.
- **Lo que va entre paréntesis se evalúa** y debe dar un nodo: otra proposición, un ancla `(at …)`, un
  conjunto `(files …)`. Así una proposición puede ser término de otra sin mecanismo aparte: las
  relaciones «a su vez son nodos».
- **Nodo estructural**: `(at ruta localizador…)` es un ancla a una región de un archivo, con los
  localizadores que ya existen (`kimun.surface.markdown.anchors`). `(files patrón)` es un conjunto de
  archivos: queda como **una** proposición; expandirla es trabajo del índice, no de quien declara.
- **Nodo puro**: un término sin ancla. Su contenido, si lo tiene, es otra proposición sobre él.
- **Literales** (cadenas, números, keywords) son literales.

## 3. Qué sale

Una lista de proposiciones, en orden, como datos. Todo nodo sale etiquetado, igual en EDN y en JSON:

```clojure
{:r "isa" :a {:files "src/ui/**/*.py"} :b {:term "UI.component"}
 :context "proyecto.decisiones" :at {:file "kimun/decisiones.clj" :line 7} :id "…"}
```

Un término es `{:term "nombre"}`; una proposición usada como término, `{:proposition id}`; un ancla,
`{:anchor {:path :locator}}`; un conjunto, `{:files patrón}`; un literal es un escalar (cadena, entero,
keyword, booleano) y nada más. `:id` es el hash canónico de `(R A B)` más el contexto
(`kimun.kernel.canon`): la misma proposición escrita dos veces es una (la segunda posición queda en
`:also-at`). `:at` dice dónde está escrita; no entra en el `:id`.

## 4. Cómo

Con `sci`, que viene en Babashka: evalúa Clojure de verdad, aislado (sin disco, sin red, sin `eval`
del proceso), con el vocabulario (`relation`, `at`, `files`) como único punto de partida. Las
relaciones son macros de ese evaluador, para que los símbolos desnudos lleguen como términos.

## 5. Cortes

1. **Leer** (hecho, 2026-11-16: `kimun.surface.declare`, `declare read ARCHIVO…`): el evaluador puro y
   un comando de solo lectura que muestra las proposiciones de uno o más archivos. Sin store.
2. **Marcas desde declaraciones** (hecho, 2026-11-16: `kimun.surface.declared-anchors`, `declare anchors FILE… [--since COMMIT]`): las proposiciones con `(at …)` se resuelven contra los archivos y, con un commit, dan los estados de `mark-states` comparando el texto que git guarda con el de ahora. Sin store.
3. **Los comandos generan formas** en vez de escribir en el store; el índice se deriva.
4. **Borrar** lo que eso reemplaza.

## 6. Lo que no está pensado

- El porqué, quién y cuándo de una declaración (el «docstring tipado»). Con proposiciones sobre
  proposiciones (`supports`, arriba) quizás no haga falta nada más; no está decidido.
- Dónde busca kimun los archivos de declaración por defecto.
- Qué pasa cuando dos contextos definen la misma relación con distinto sentido.
- El `:id` no cambia si cambia el docstring de la relación.
- El evaluador no tiene tope de tiempo: una declaración con un bucle infinito cuelga el proceso.

## 7. Decidido al implementar el corte 1

- El `ns` es la primera forma y la única; solo lleva nombre (y docstring). Sin `ns` no hay proposición.
- Un símbolo ligado en el código (`let`, parámetro, `def` del archivo) vale por su valor; así se
  generan declaraciones con código. Los nombres de `clojure.core` no ligan: desnudos, son términos.
  Consecuencia: un `def` hace que ese nombre deje de ser término en el resto del archivo.
- Redefinir una relación en el mismo archivo con otro docstring es un error.
- El nombre de una relación no se puede generar con código (es una macro); sus usos sí.
- Aislamiento: el evaluador no expone nada del anfitrión (`slurp`, `System`, clases, `require`, shell).
  No es un aislamiento del sistema operativo.

## 8. Decidido al implementar el corte 2

- Formas de `(at …)`: `(at "r")` archivo entero (hash del texto, cualquier tipo), `(at "r" [camino])` sección, `(at "r" [camino] {:type T :n N})` bloque, `(at "r" [camino] {:type T :n N} [s e])` rango en graphemes. Un elemento del camino es `"Título"` o `["Título" K]`.
- Los ordinales de la declaración son de 1 (`:n 1` es el primer párrafo, `["Sub" 2]` el segundo hermano); `anchors` numera desde 0 y la traducción resta uno. `0` es `declare/bad-locator`.
- `(at "r" [])` solo (la sección raíz) es mal formado: `anchors` no la lista; se ancla un bloque de ella con `[] {:type … :n …}`.
- La forma se valida al calcular los estados (`declared-anchors/states`), no al evaluar: `declare read` no la comprueba. Una ruta absoluta o con `..` también es `declare/bad-locator`.
- Sin comparación no hay candidatos: `anchors` los propone a partir de un hash y no hay ninguno con qué comparar. Tampoco hay `:lines`: las anclas se leen sin los spans de la capa de bloques.
- Un archivo no Markdown con localizador interno es `:unsupported` aunque git no lo reporte cambiado (es una propiedad de la declaración, no del archivo).
- Un renombrado se reporta con las dos rutas (`:path` y `:from`) como cambiadas; un ancla escrita con la ruta vieja sale `:missing`.
- Sin `--since`, el puntero se busca solo en `<cwd>/.kimun/seen.edn` (no sube por los directorios); un puntero dañado es `git/bad-pointer`.

## 9. El proyecto: varios archivos, contextos que se usan entre sí (cortes 3a y 3b)

Elecciones mías, 2026-11-16.

- **Dónde están las declaraciones.** Por defecto, los `*.clj` bajo `kimun/` en la raíz del proyecto.
  Es una convención, no una exigencia: un proyecto sin esa carpeta no tiene declaraciones y nada
  falla. Los comandos `declare` sin archivo leen esa carpeta; con archivos, leen esos.
- **Un contexto usa relaciones de otro como en Clojure**: `(ns b (:require [a :refer [isa]]))` o
  `[a :as a]` y `(a/isa …)`. `a` es el contexto de otro archivo de declaración del proyecto. No hay
  vocabulario de relaciones que venga con kimun: cada proyecto define las suyas.
- **Una relación es de su contexto.** `:r` sale como `"a/isa"`: el contexto que la define y su nombre.
  Dos contextos pueden definir `isa` con sentidos distintos sin confundirse; esto responde la
  pregunta de §6.
- **Los comandos que declaran escriben código.** `declare add --to ARCHIVO 'FORMA'` agrega la forma al
  archivo si el archivo sigue evaluando y la forma aporta algo nuevo. No hay otra escritura: no hay
  store que actualizar.
- **Preguntar es recorrer relaciones** (**1.1**): `declare about TÉRMINO` da las proposiciones donde
  aparece el término (o uno más específico: `UI.view` incluye `UI.view.dashboard`), y `declare about
  --path RUTA` da lo que se sabe de un archivo: las anclas que caen en él y las clases que le llegan
  por un `(files patrón)`. `(files …)` se expande al preguntar, contra los archivos que git conoce;
  nunca se guarda expandido.
- Todavía sin caché: evaluar es rápido. La caché explícita se agrega cuando una medición la pida.


## 10. Decidido al implementar el corte 3a

2026-11-16. Hechos, no diseño nuevo (`kimun.surface.declare/read-project`, `declare read|anchors` sin archivos, `declare add`).

- **Varios contextos con sci.** Cada archivo se evalúa en su propio contexto sci, fijado a su namespace. Antes de evaluarlo, para cada contexto que requiere se definen en el namespace sci de ese contexto un `defmacro` por relación suya (que emiten con `:r` `"contexto/nombre"`); el `(ns b (:require …))` del archivo lo resuelve el propio `ns` de sci contra esos namespaces ya existentes (`:refer` y `:as` son los de sci). Solo se pasan relaciones: los `def`/`defn` de otro contexto no existen en el sci del que requiere.
- Orden: recorrido en profundidad por los requires, empates por orden de entrada; cada contexto se evalúa una vez. Los resultados salen en el orden de entrada. Se comprueba, en este orden y antes de evaluar nada: sintaxis de cada archivo, `duplicate-context`, `unknown-context`, `require-cycle`; `unknown-relation` al llegar al archivo que la pide.
- `:r` es `"contexto-que-define/nombre"`, también para las relaciones del propio archivo; `:context` sigue siendo el que escribe la proposición. Cambian los `:id` (incluyen `:r`). Las claves de `:relations` siguen siendo los nombres sin calificar. Solo se importa una relación definida por ese contexto, no una que él mismo importó.
- La línea de un error de `:require` es la del libspec: el lector no da línea a símbolos ni vectores, se busca el nombre del contexto como ficha entera desde la línea del `ns`; si se repite el mismo contexto en dos libspecs, ambos dan la primera aparición.
- Cualquier otro `ns` (cadenas, `:refer :all`, `:rename`, prefijos, `:use`, `:import`, `:refer-clojure`) sigue siendo `declare/ns-clauses`.
- Una relación propia con el nombre de una importada con `:refer` es `declare/eval` (sci: «isa already refers to #'a/isa»), con archivo y línea; no tiene tipo propio.
- El proyecto es `kimun/` (una sola definición, `kimun.cli.commands.declare/project-dir`), `*.clj` recursivo ordenado por ruta; con archivos, solo esos forman el conjunto (un archivo que requiere otro no nombrado es `declare/unknown-context`).
- `declare add`: añade el texto recortado de la forma más `\n`; no comprueba que sea una sola forma (si trae varias se evalúan todas). «Nueva» = id de proposición ausente antes, o nombre de relación ausente antes; un archivo que ya no evalúa antes de agregar da su error. `--to` es la opción compartida con `edges`.
