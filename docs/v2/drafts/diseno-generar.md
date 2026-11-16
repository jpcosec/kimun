# Generar: materializar desde las proposiciones

Borrador de Claude, 2026-11-16. Sigue a `diseno-evaluar.md` y abre la etapa 5 de `plan-producto.md`.
Entre «» va el dueño (`sesion-2026-11-15.md`); con número, `00-nucleo.md`. Lo demás son elecciones
mías. Nada está probado.

## 1. La idea

«Para poder "ver el conocimiento" hay que materializarlo en otro lado.» «TODOS los documentos que
genera SLDB […] pasarían a ser MATERIALIZACIONES DESDE MODELOS.»

Un documento generado no es una fuente: es una vista de las proposiciones, escrita en un archivo.
Se puede borrar y volver a generar. Si alguien lo edita a mano, kimun lo dice; no lo absorbe.

## 2. Una receta es una función

Las declaraciones son Clojure que corre (**7.1**). Entonces la receta de un documento es una función
de Clojure, escrita en el mismo archivo de declaración, que pregunta y devuelve texto:

```clojure
(ns proyecto.vistas (:require [proyecto.base :refer [isa]]))

(defn decisiones [ask]
  (str "# Decisiones\n\n"
       (apply str (for [p ((:about ask) 'decision)]
                    (str "- " (:term (:other p)) "\n")))))

(isa (generated "docs/decisiones.md" decisiones) doc.indice)
```

- **`(generated ruta receta)`** es vocabulario, como `at` y `files`. Da un nodo `{:generated {:path
  ruta}}` que entra en proposiciones como cualquier otro (el archivo generado se puede etiquetar,
  relacionar, anclar) y deja registrada la receta de esa ruta. Una ruta, una receta.
- **La receta recibe `ask`**: un mapa de funciones para preguntar a las proposiciones del proyecto
  (`:about`, `:about-path`, `:members`, `:terms`, las de `diseno-evaluar.md §9`) y `:text`, que da el
  texto actual de un ancla `(at …)`. Es lo único que la receta puede ver: sigue aislada del disco.
- **Devuelve una cadena.** No hay lenguaje de plantillas aparte: `str`, `for`, funciones. Las
  plantillas con marcadores de SLDB se pueden ofrecer después como una función más.
- **Muchos documentos de una clase** se declaran con código: un `doseq` que emite un `(generated …)`
  por miembro. La clase comprime también lo que se genera.

Evaluar las declaraciones no corre ninguna receta: solo las registra.

## 3. Comandos

- `declare generate` — corre las recetas y escribe cada archivo (escritura atómica). Solo escribe
  rutas declaradas con `generated`, dentro del proyecto. Dice qué creó, qué cambió y qué quedó igual.
- `declare generate --check` — no escribe: dice qué archivos generados difieren de lo que su receta
  daría hoy (cambiaron las fuentes, o alguien los editó).
- Dos corridas seguidas dan los mismos bytes.

## 4. Lo que no está pensado

- El docstring tipado como materialización de una clase.
- Generar hacia algo que no es un archivo (una respuesta de la API, una vista).
- Qué hacer con un archivo generado cuya declaración desapareció (hoy: nada; nada se borra solo).
- El porqué, quién y cuándo, igual que en `diseno-evaluar.md §6`.

## 5. Decidido al implementar

2026-11-16. Hechos, no diseño nuevo (`kimun.surface.generate`, `kimun.surface.declare/recipes`, `declare generate [--check]`, las dos herramientas de MCP).

- **`(generated ruta receta)`** es una macro del evaluador (solo para conocer su línea) que devuelve el nodo `{:generated {:path ruta}}` y registra `{:path :at :context :fn}`. La ruta se normaliza (se quita un `./` inicial) y se valida en forma: cadena no vacía, relativa, sin `..`, sin tramos vacíos ni `.`, sin barra final ni `\`; si no, `declare/bad-generated` con archivo y línea. La receta debe cumplir `fn?` (un `fn` o un `defn` del archivo): una cadena, `nil`, un mapa o una keyword es `declare/bad-generated`. Aridad distinta de dos: `declare/bad-generated`. La receta no entra en el nodo ni en el `:id`.
- **Cómo viaja la receta.** El resultado de evaluar cada archivo trae `:generated [{:path :at :context}]` (datos) y, en los **metadatos** del mapa del resultado, las recetas con su función; `declare/recipes` las junta (una por ruta, la primera declaración). Las funciones no salen en ningún dato. Las recetas son `fn` de sci creados durante `eval-string+` y se invocan después, fuera de ese `eval`, en un hilo del tope de tiempo y con otro `*ns*` ligado: funciona dentro de Babashka (probado con `*ns*` ligado a otro namespace, y por la CLI real).
- **Conflicto.** Misma ruta con funciones distintas (`identical?`), en un archivo o entre archivos, es `declare/generated-conflict` en la segunda posición, con `:positions [primera segunda]`. La misma función nombrada dos veces (suelta y dentro de una proposición) es un solo registro. Se comprueba al terminar de evaluar el proyecto entero, no al ejecutar el `generated`.
- **`ask`** entrega las **filas** (`:results`) de `about` y `about-path`, no el mapa que envuelve a `props/about`: así el ejemplo de §2 (`(for [p ((:about ask) 'decision)] (:term (:other p)))`) funciona tal cual. `:about` acepta un símbolo o una cadena y, como segundo argumento, `{:exact true}`; `:terms` es una función sin argumentos. `:members` expande sobre los archivos del proyecto **sin contar los archivos generados**: si los contara, la primera corrida y la segunda darían bytes distintos (la segunda vería el archivo que escribió la primera). `:text` da el texto del archivo entero, de una sección o de un bloque (sus líneas, tomadas de la capa de bloques alineada con el AST, `capture/aligned-spans`) o de un rango (el texto plano del rango); nil si no resuelve, si el archivo no está, si un localizador interno cae en un archivo que no es Markdown o si la capa de bloques no alinea. Un argumento que no es un `(at …)` es error de la receta.
- **Errores de receta**, todos con `:path`, `:file` y `:line` de su declaración: `declare/bad-recipe` (no devolvió una cadena; `nil` también), `declare/recipe` (lanzó; lleva el mensaje) y `declare/timeout` (el tope de tiempo de cada receta es `$KIMUN_DECLARE_TIMEOUT_MS`, o 10 s, el mismo hilo abandonado de `diseno-evaluar.md §12`). Una receta que menciona `slurp` o `System/exit` no llega a correr: sci rechaza el símbolo al evaluar el archivo (`declare/unresolved`).
- **El comando** trabaja sobre el proyecto (`kimun/`), no acepta FILE (`cli/usage`). Orden: evaluar, validar **todas** las rutas, correr **todas** las recetas, y solo entonces escribir; un fallo en cualquiera de las tres primeras deja el árbol igual. Una ruta se rechaza (`declare/bad-generated`, exit 3) si está bajo `kimun/`, `.git/` o `.kimun/`, si es un directorio, si sale de la raíz por un enlace simbólico (también uno colgante) o si un enlace que queda dentro de la raíz entra en `kimun/`, `.git/` o `.kimun/`. La comparación es de texto UTF-8; `:bytes` es el largo en bytes del texto que da la receta.
- **`--check` y el código de salida.** Con `--check` el estado es `:missing|:differs|:same` y, si algún archivo no es `:same`, la respuesta es el fallo `declare/generated-differs` con exit **3** y los mismos datos que la respuesta de éxito: es el mismo convenio de `include check` (`include/drift`, 3: «lo declarado y lo que hay discrepan»). `stores health` no sirve de modelo: su veredicto va en `:status` de una respuesta `ok`. No se añadió ningún código.
- `declare about --path RUTA` de un archivo generado trae una fila `{:via :generated :proposition p :side :other}`. `declare anchors` no cambió: solo mira los nodos `{:anchor …}`, así que ignora los `{:generated …}`.
- Sigue sin hacerse lo que §4 deja fuera. En particular: un archivo generado cuya declaración desapareció no se detecta ni se borra.
