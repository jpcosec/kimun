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
del proceso), con el vocabulario (`relation`, `at`, `files`; `generated` está en `diseno-generar.md`) como único punto de partida. Las
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

## 11. Decidido al implementar el corte 3b

- `kimun.surface.propositions` es puro: recibe proposiciones y, donde hace falta, la lista de rutas del proyecto. No interpreta el nombre de ninguna relación; las devuelve como están escritas.
- `declare terms`: cuenta proposiciones (una vez cada una aunque el término esté en los dos lados) por término exacto; un ancestro por puntos que nadie escribió sale con `:count` 0 e `:implicit true`. Un nombre con tramos vacíos (`a..b`) no tiene ancestros.
- `declare about TÉRMINO`: una fila por lado donde aparece (si aparece en ambos, dos filas). Por defecto incluye los términos más específicos (`:exact true` no). Los resultados «a través de proposiciones» (`:via :proposition`) son un solo nivel (`:levels 1`) y no repiten una proposición que ya entró por sus términos. Un término que es una proposición (`{:proposition id}`) no se busca por nombre.
- `declare about --path RUTA`: ancla por ruta exacta (sin `./` inicial) o `(files patrón)` con el comparador de `kimun.surface.include/compile-glob` (`*` dentro de un segmento, `**` entre ellos; sin llaves ni clases). No abre el archivo ni mira si existe. Dos patrones que llegan al mismo archivo dan dos filas. `--path` es la opción compartida con `docs create` (su descripción global sirve a los dos usos); con `--exact` es error de uso.
- `declare members 'PATRÓN'` no lee declaraciones: expande un patrón contra los archivos del proyecto (git: seguidos más los no seguidos no ignorados, solo los que existen; fuera de git, el recorrido del directorio sin `.git` ni `.kimun`); la respuesta dice `:files-from :git|:walk`. Más de un argumento es error de uso.
- Sin FILE, `terms` y `about` leen el proyecto (`read-set` del corte 3a), igual que `read` y `anchors`.
- **La raíz del proyecto** (decisión del coordinador): los seis comandos de `declare` trabajan desde ella, no desde el cwd. Es el directorio más cercano, subiendo desde el cwd (con los enlaces simbólicos resueltos), que contiene la carpeta `kimun/` (`project-dir`); si ninguno la tiene, la raíz del repositorio git del cwd (`git rev-parse --show-toplevel`); si tampoco, el cwd. Las rutas de `(at …)`/`(files …)`, `--path`, `members` y las de salida son relativas a la raíz; FILE, `--to` y `--path` se interpretan relativos al cwd (un FILE fuera de la raíz sale con su ruta absoluta; `--path` fuera, `cli/usage`). Toda respuesta lleva `:root` (ruta absoluta). El puntero de `anchors` y la lista de `members` también parten de la raíz.

## 12. Decidido al arreglar (2026-11-16)

Hechos de usar `declare` de verdad; cambian lo dicho en §8, §10 y §6 donde se indica.

- **Opciones después de un posicional** (`declare add --to F 'FORMA' --format edn`): babashka.cli deja de leer opciones en el primer posicional que viene después de una opción con valor y entrega el resto, tal cual, como posicionales. `kimun.cli.args/parse` vuelve a leer ese resto (`parse-all`), salvo que haya un `--`. Valía para todos los grupos (`--opt V POSICIONAL --otra`), no solo `declare`; el `:default` de `--format` se aplica ahora al final.
- **`declare add`** recibe exactamente una forma (se cuenta con el lector de sci, no con una expresión regular): cero o varias es `cli/usage` y no se escribe. Con el archivo ya existente, `--context` debe ser su `ns`; si no, `cli/usage`. Una forma que no lee sigue dando `declare/syntax` con el archivo y la línea.
- **Contextos reservados**: `clojure`, `user`, `sci`, `kimun.declare` y todo bajo `clojure.`, `sci.` o `kimun.declare.` es `declare/reserved-context` (archivo y línea del `ns`).
- **Dependencias fuera de los FILE**: si todos los FILE están bajo `kimun/`, los contextos que requieren y ninguno define se buscan en el resto de `kimun/` (transitivamente) y se evalúan con ellos; `:required` lista sus rutas y `:files` solo los nombrados. Fuera de `kimun/` sigue `declare/unknown-context`. Un archivo de `kimun/` que no lee se ignora al buscar (su error sale si es el que se necesita y entonces no se halla: `unknown-context`).
- **Relación propia con el nombre de una importada con `:refer`** es `declare/relation-conflict` (archivo, línea, `:contexts [propio importado]`); con `:as` no hay choque.
- **La línea de un `:require`** es la del libspec: el lector de sci da línea a vectores y símbolos si se lo pide (`:location?`), así que se acabó buscar el nombre en el texto (§10, párrafo de la línea): dos libspecs del mismo contexto tienen cada uno la suya.
- **Tope de tiempo** por archivo: 10 s (`kimun.surface.declare/default-timeout-ms`), opción `:timeout-ms` del evaluador puro; el comando la lee de `$KIMUN_DECLARE_TIMEOUT_MS` (no entero positivo: `cli/usage`). Al excederlo, `declare/timeout` con el archivo (línea 1). El archivo se evalúa en un hilo daemon de prioridad mínima; al vencer se le hace `interrupt` y se lo abandona: ni la JVM ni el binario de Babashka pueden detener un hilo que no coopera, así que un bucle infinito sigue gastando un núcleo hasta que el proceso termina. La CLI termina igual (el hilo es daemon y `-main` sale con `System/exit`); en un proceso de larga vida (`serve`, MCP) cada timeout deja un hilo así.
- **`declare read` valida la forma de los `(at …)`** al evaluar, con `declared-anchors/translate` (pura, sin ciclo de requires: `declare` requiere a `declared-anchors`, no al revés): `declare/bad-locator` también en `read`, `terms`, `about` y `add`. Un localizador mal formado ya no se acepta en silencio (§8, cuarto punto).
- Requerir un contexto que no define ninguna relación sigue siendo `declare/eval` («Could not find namespace»): no se tocó.

