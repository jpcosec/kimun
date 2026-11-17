# Leer las fuentes por clase: ingerir un proyecto sin tocarlo

Borrador de Claude, 2026-11-16. Sigue a `diseno-evaluar.md` y `diseno-generar.md`. Entre «» va el
dueño; lo demás son elecciones mías. Nada está probado.

## 1. Por qué

El dueño, 2026-11-16: «esto no está listo hasta que no sea capaz de 1- Ser el nucleo nuevo de sldb y
2- Usar algun proyecto ya avanzado como deskops y ingestarlo.»

Este documento trata el punto 2. El punto 1 está en §6, sin diseñar.

El terreno, medido el 2026-11-16 en solo lectura (el informe previo está en
`runs/research/2026-11-15/reemplazo-sldb/`):

- El desk de deskops (`/home/jp/proyectos/SLDB_1/deskops/desk`): 675 Markdown, 27 tipos de
  documento, 563 documentos en su store. Átomos 169, reviews 117, contratos de materialización 104,
  notas de inbox 54, pills 31.
- Las relaciones ya están escritas, casi todas en el frontmatter: `depends_on`, `atoms`, `pills`,
  `files`, `current_node`, `source_atoms`, `subject`, `tags` con namespace (`topic:…`, `system:…`), y
  documentos que ya son proposiciones (`RelationDoc`: `source_id`, `target_id`, `relation_type`).
- Hoy kimun no puede verlas: un ancla `(at …)` llega a secciones y bloques, no a campos del
  frontmatter, y nada traduce documentos a proposiciones.

## 2. La idea

«Kimun solo ve, sintetiza y genera, no debería exigir nada al resto.» «Los documentos fuentes son los
documentos fuentes de cualquier otro proyecto tal y como si no usara NADA de kimun/sldb/deskops.» «La
gracia de poder hacer clases es que […] indexar muchas cosas iguales sea una declaración en lista
nomás.»

Ingerir no es copiar los documentos adentro ni escribir una declaración por documento. Es declarar,
una vez por clase, **cómo se lee** esa clase de archivo; las proposiciones salen de leer los archivos
tal como están. Si un archivo cambia, cambian sus proposiciones. Nada del proyecto observado se toca.

## 3. Un lector es una función

```clojure
(ns desk.tareas)

(relation isa "A es de la clase B.")
(relation depends-on "A no puede cerrarse antes que B.")
(relation uses "A se apoya en el átomo B.")
(relation tagged "A lleva la etiqueta B.")

(reading (files "desk/tasks/task-*.md")
  (fn [doc]
    (let [task (symbol (get (:front doc) "id"))]
      (isa task Task)
      (doseq [d (get (:front doc) "depends_on")] (depends-on task (symbol d)))
      (doseq [a (get (:front doc) "atoms")]      (uses task (symbol a)))
      (doseq [t (get (:front doc) "tags")]       (tagged task (symbol t))))))
```

- **`(reading conjunto lector)`** es vocabulario, como `at`, `files` y `generated`. Registra el lector
  para ese conjunto de archivos. Evaluar las declaraciones no abre ningún archivo.
- **El lector recibe `doc`**, una vista del archivo: `:path`, `:front` (el frontmatter como mapa, o
  nil), `:sections` (título, nivel y texto de cada sección), `:text`. Es lo único que ve.
- **Dentro, se declara como siempre**: las relaciones del contexto, términos, `(at …)`. Un término
  sale de un dato con `symbol`, que ya funciona (un símbolo ligado vale por su valor).
- **Cada proposición leída sabe de dónde viene**: lleva `:source {:path …}` además del `:at` de la
  declaración del lector. Así se puede preguntar qué dice un archivo y qué archivo dice algo.
- **Un archivo puede tener varios lectores** (varias clases por nodo): cada uno aporta lo suyo.
- **Un lector que falla en un archivo** no tumba a los demás: ese archivo queda fuera y se dice cuál
  y por qué.

## 4. Cuándo se lee

Al preguntar (`declare about|terms|…`, `declare generate`), no al evaluar. Sin caché en el primer
corte: se mide sobre una copia del desk de deskops y se decide con el número.

La caché, cuando haga falta, es la «comprimida» de `diseno-declaraciones.md §6`: las proposiciones
leídas de un archivo se guardan por el hash que git ya tiene de ese archivo. Git dice qué cambió;
kimun relee solo eso.

## 5. Cortes

1. **Leer** (este): `reading`, la vista `doc`, `:source`, y los comandos de preguntar incluyendo lo
   leído. Solo Markdown con frontmatter YAML. **Hecho 2026-11-16** (`kimun.surface.reading`, ver el final).
2. **Lectores para el desk de deskops**, escritos como declaraciones en una copia del proyecto:
   átomos, tareas, pills, reviews, contratos de materialización, relaciones, etiquetas. El criterio
   es que `declare about` responda sobre el desk real lo que hoy responde `sldb`/`deskops`, medido
   contra su índice de aristas.
3. **Caché por hash de git**, si la medición la pide.
4. **Las vistas de deskops como recetas** (`declare generate`): el Board, la vista de una tarea.

## 6. El otro criterio: ser el núcleo de sldb (sin diseñar)

Hechos, del mismo informe: sldb son 51 000 líneas de Python; sus consumidores (deskops, spec2viz,
pron, IR, las UIs) lo usan **importándolo**, 527 sitios, y los modelos son clases pydantic con
validadores que viven en el código de cada consumidor. La frontera pública es `sldb.api` (183
nombres). Por debajo no hay una interfaz de persistencia: 139 archivos de `api/` importan los
internos del store. El problema medido de sldb es de rendimiento («reprocesar lo que no cambió»:
`stores build --check` 50 min; `deskops doctor` 10–14 s, de ellos 7,8 s en dos subprocesos).

El dueño, 2026-11-16 (`sesion-2026-11-15.md`, sección de ese día): «No, puta, para todo lo que se pueda implementar facilmente, implementemoslo en kimun. Pero muy posiblemente haya cosas "perifericas" para las que sea mas facil hacer adaptadores a python o sdk»

Lo que sigue de eso, y es lectura mía: mi lectura anterior (sldb sigue como fachada para quienes lo
importan y kimun trabaja solo por debajo) queda descartada. Lo que sldb hace y es fácil de hacer en
kimun se implementa en kimun; lo periférico puede ir por adaptadores a Python o un SDK. Falta, antes
de diseñar: un inventario de lo que sldb hace y de lo que sus consumidores usan de verdad, partido en
«kimun ya lo hace», «fácil en kimun», «difícil» y «periférico (adaptador o SDK)». El dueño no fijó
dónde pasa la línea entre fácil y periférico.

## 7. Decidido al implementar (corte 1, 2026-11-16)

Elecciones de quien implementó, no del dueño; lo medido está en `runs/subagents/2026-11-16-leer/REPORT.md`.

- **Qué se lee.** Los archivos del proyecto que calzan con el patrón, según la lista de git (o el
  recorrido si no hay repositorio). Nunca los generados por `(generated …)` ni los de `kimun/`.
- **La vista `doc`.** `:front` tiene claves de texto en todos los niveles; las fechas YAML llegan como
  cadena (`"2026-11-15"` si es medianoche UTC, si no el instante ISO), nunca como objeto Java. Es nil si no
  hay frontmatter, si no es YAML válido o si no es un mapa. `:sections` son los títulos ATX fuera de
  código; su `:text` llega hasta el siguiente título de cualquier nivel. Un archivo que no es Markdown
  entra con `:front nil`, `:sections []` y su contenido en `:text`.
- **Fallos.** Un lector que lanza, tarda más del límite (10 s) o emite algo inválido deja fuera solo ese
  par (lector, archivo) y su fila va en `:reading-errors` con `:path :pattern :at :type :message`. Un
  `(reading …)` mal formado es el error `declare/bad-reading`.
- **Misma proposición.** Declarada a mano y leída es una sola (mismo `:id`); conserva el `:at` de la
  declaración y acumula `:sources`.
- **`--declared-only`** no corre lectores. El resumen de `declare read` trae `:readings
  :read-propositions :reading-errors` solo si el proyecto declara lectores.
- **Sin caché**: se relee todo cada vez que se pregunta; el número está en el REPORT.
