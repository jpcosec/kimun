# Declarar es escribir proposiciones; la realidad vive afuera

Borrador de Claude, 2026-11-15, segunda versión, tras la revisión del dueño. Entre «» va el dueño,
textual (`drafts/sesion-2026-11-15.md`); con número va `00-nucleo.md`. Lo demás es mi lectura. Nada
de esto está probado ni medido, y solo el §9 está despachado.

## 1. Las ideas

- **1** «El conocimiento de kimun consiste en proposiciones `(R A B)` en contexto.» **1.1** «Las cosas
  no significan solas; significan por sus relaciones.»
- **7** «Clojure es el lenguaje constitutivo de kimun.» **7.1** «declarar estructuras y operar sobre
  ellas comparten un mismo lenguaje.»
- «Kimun solo ve, sintetiza y genera, no debería exigir nada al resto.»
- «la regla más importante de este repo, la realidad existe NO en nuestro sistema, sino en el código
  de afuera.»

`(R A B)` ya es una forma de Clojure. **Declarar algo es escribir una proposición, como código.** Y
como es código que corre, no datos con forma de código, declarar y operar son lo mismo: una relación
está definida, una proposición se evalúa, y una declaración se puede generar con otra.

## 2. Marcar

«tenemos muchas formas de marcar los nodos ("taggear") y esas formas permiten hacer relaciones.
Pongámosle contexto, namespace, clase, tipo, etc. da lo mismo.»

No hay una estructura privilegiada. Contexto, clase y tipo son etiquetas sobre nodos; lo que importa
es que permiten relacionar. Un mismo nodo lleva varias, por ejes distintos, y los nombres con puntos
son jerarquía (`UI.view.dashboard` también es `UI.view`).

## 3. Dos niveles de monitoreo

«1- es el monitoreo "hasta" el nivel de archivo, que es básicamente lo que hace git. 2- el monitoreo
"dentro" de los archivos a nivel de fields. Solo lo que está anchored importa a nivel de fields.»

| nivel | quién lo hace | qué responde |
|---|---|---|
| hasta el archivo | **git** | qué archivos cambiaron desde lo último que kimun miró, incluidos los que kimun nunca ingirió |
| dentro del archivo | kimun | si cambió una región **anclada**; lo no anclado no existe para kimun |

«aprovechemos por ejemplo a git para detectar mutaciones sobre las que no tenemos conocimiento
ingestado.»

**Rehacer git queda EXPLÍCITAMENTE diferido** (dueño, 2026-11-15): «tenemos 2 opciones. Rehacer git y
utilizar git. Creo que tenemos que hacer ambas porque responden cosas distintas, pero por ahora
dejemos el rehacer para después.» Lo que kimun ya tiene de eso —el árbol Merkle por ruta, la huella
por archivo, las revisiones encadenadas y el log— es una primera versión de rehacer git: no es el
camino de ahora.

## 4. Persistencia

«estamos teniendo un problema en la persistencia.» Si la realidad y su historia viven en git, kimun
persiste muy poco:

- las **declaraciones que importan**, compactas (§5);
- un puntero: **«miré hasta este commit»**. Es un dato del índice derivado, no una declaración: vive en `<store>/seen.edn` (`{:commit sha}`), fuera del log, los planes y el journal, porque no es una transacción y ese registro propio está diferido.

Para saber si cambió algo anclado no hace falta haber guardado su huella: git conserva la versión
anterior, y se compara la región de antes con la de ahora. Tras un clon no se pierde nada.

## 5. Las clases comprimen

«La gracia de poder hacer clases es que así podemos reutilizar código, lo que haría que indexar
muchas cosas iguales sea una declaración en lista nomás.» Y: «Estamos fallando en aprovechar la
homoiconicidad para comprimir las declaraciones que SÍ importan.»

Una clase se declara una vez; después, decir qué cosas son de esa clase es una lista o un patrón, no
una declaración por cosa. No hay miles de unidades que escribir: hay una clase, una lista, y lo poco
que alguien ancló.

```clojure
(isa (files "src/ui/**/*.py") UI.component)          ; una declaración, muchos archivos
(anchors decision (at "docs/spec.md" [:section "Caché"] [:paragraph 1]))
(summarizes cache-is-not-a-source decision)
```

El ejemplo es ilustrativo: los nombres de las relaciones no están decididos.

## 6. Dos cachés

«Tenemos 2 tipos de cache.» «el "cache" generado TIENE que separarse entre código comprimido (piensa
en git) y código explícito.» Confirmado por el dueño:

- **comprimido**: lo que kimun genera al indexar en masa. Estructura derivada, para máquinas,
  compacta y direccionada por contenido como hace git. Se borra y se rehace. Nadie lo lee.
- **explícito**: lo que importa, expandido y legible: las proposiciones sobre lo anclado y sus
  relaciones.

## 7. Ver, sintetizar, generar

- **Ve**: le pregunta a git qué cambió; descompone en estructura lo que tiene algo anclado
  (tree-sitter; «NO todo el documento fuente necesita ser ingestado»).
- **Sintetiza**: el grafo de proposiciones.
- **Genera**: materializa desde el grafo con modelos. Cuando un comando declara algo, genera código.

Los archivos de declaración viven «en el disco. Kimun los puede indexar o no, lo importante es que lo
indexado pasa a ser parte de la "topología" que estamos controlando.»

## 8. Lo que todavía no está pensado

- Cómo se escribe el porqué, el quién y el cuándo de una declaración (el «docstring tipado»).
- Cambios sin commitear: decidido, el puntero siempre es a un commit y el diff es contra el árbol de trabajo, así que lo sin commitear se ve siempre y sigue viéndose hasta que se commitea; avanzar el puntero a HEAD con el árbol sucio es válido y no lo esconde.
- Lo que hoy existe y deja de usarse: se borra cuando el camino por git lo reemplace, no antes.
- Fichas y su materialización; relaciones entre lo anclado; la capa G y `D-G`.

## 9. Primer corte (despachado)

Que el sensor responda «qué cambió» preguntándole a git, al lado de lo que hay, sin borrar nada:
qué archivos cambiaron desde un commit dado, cuáles de ellos tienen algo anclado, y para esos, si la
región anclada cambió comparando la versión de antes (que git guarda) con la de ahora. Medido contra
los ≈38 s que hoy tarda un latido con 2000 documentos (`runs/subagents/2026-11-15-MED1/`).
