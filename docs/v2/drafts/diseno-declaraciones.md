# Declarar es escribir proposiciones

Borrador de Claude, 2026-11-15, para revisión del dueño. Reemplaza un primer borrador hecho por un
agente, que el dueño rechazó: convertía cada cosa nombrada en la conversación en una forma especial
(`defclase`, `defmarca`, `reancla`…) en vez de partir de las ideas del proyecto. Entre «» va el
dueño, textual (`drafts/sesion-2026-11-15.md`); con número va `00-nucleo.md`. Lo demás es mi lectura
y está sin aprobar, sin probar y sin medir.

## 1. La idea

`00-nucleo` ya dice qué es el conocimiento de kimun y en qué lenguaje se escribe:

- **1** «El conocimiento de kimun consiste en proposiciones `(R A B)` en contexto.» **1.1** «Las cosas
  no significan solas; significan por sus relaciones.»
- **7** «Clojure es el lenguaje constitutivo de kimun.» **7.1** «Las formas de sus instrucciones
  pueden tratarse como datos: declarar estructuras y operar sobre ellas comparten un mismo lenguaje.»

`(R A B)` **ya es** una forma de Clojure. Entonces no hace falta un formato de declaraciones, ni un
plan, ni un journal: **declarar algo es escribir una proposición, como código, en un archivo.** Es lo
que el dueño preguntó: «¿No será simplemente cosa de usar docstrings tipados dentro del código mismo?»

De ahí sale el resto:

1. **Hay un solo tipo de cosa: la proposición.** Una clase, un ancla, una marca, un re-anclaje, un
   cierre, un linaje, un modelo: ninguna es una forma especial. Son proposiciones con distintas `R`.
   «Lo importante no es el nodo mismo, son sus relaciones», y las relaciones «a su vez son nodos».
2. **Un archivo de declaraciones es un documento fuente más**, de tipo Clojure. Kimun lo lee con el
   mismo mecanismo con que lee cualquier fuente. No hay un «subsistema de declaraciones» con su
   lector propio: sería duplicar una frontera. Por eso kimun «se puede indexar "gratuitamente" a sí
   mismo».
3. **El store es un índice derivado** de las fuentes, incluidas esas. Se borra y se rehace (**6.3**).

## 2. Cómo se ve

```clojure
(ns spec.cache
  "Lo que la spec dice sobre la caché.")          ; un namespace es un contexto (1.2)

(def decision
  "El párrafo donde la spec decide no versionar la caché.")

(def cache-is-not-a-source
  "Lo que se recalcula no tiene autoridad: se puede borrar sin perder conocimiento.")

(anchors decision (at "docs/spec.md" [:section "Caché"] [:paragraph 1]))
(summarizes cache-is-not-a-source decision)

(isa (at "src/ui/dashboard.py") UI.view.dashboard)
(isa UI.view.dashboard UI.view)
```

- Un **término** se introduce como en Clojure: un nombre con su docstring. El docstring dice qué es.
  **Tipado** quiere decir que, si el término es instancia de una clase, su docstring sigue el modelo
  de esa clase (**5**): es donde entra «lo que hacen los modelos de SLDB», y es lo que después se
  materializa.
- Una **proposición** es una lista `(R A B)`. `A` y `B` son términos, lugares de una fuente
  (`(at …)`) u otras proposiciones.
- `R` también es un término, declarado en algún contexto con su álgebra, igual que hoy lo pide
  `14 §4`: `(property summarizes transitive)`.

## 3. Lo de hoy, dicho así

Todo lo que el 2026-11-15 se construyó como comandos y campos dentro del store se dice con el mismo
mecanismo. No hay una fila que necesite otra maquinaria.

| hoy | como proposición |
|---|---|
| clase de un documento; varias por ejes; jerarquía | `(isa X C)`, tantas como ejes; `(isa C D)` |
| marcar una sección, un bloque, un rango | `(anchors U (at doc lugar))` |
| re-anclar con motivo | otra `(anchors U (at …))`, con su porqué; la anterior no se borra |
| cerrar una unidad | `(closed U)`, con su porqué |
| trasladar a otro documento | otra `(anchors U (at otro-doc …))` |
| linaje (copiar, dividir, fusionar) | `(derived-from U V)` |
| ficha | un término con docstring y sin ancla |
| modelo | un tipo de subgrafo (`14` P10): proposiciones sobre proposiciones |

El «porqué» de una declaración es su docstring o un metadato de la forma; quién y cuándo, lo mismo.
No lo diseño más: depende de la pregunta 1 de §8.

## 4. Identidad

**3** «El signo no es el símbolo.» El nombre escrito (`decision`) es un signo: sirve para citar dentro
del contexto y se puede cambiar. La identidad de la unidad es propia y se acuña al marcarla
(`14 §21`). Como el índice es derivado, esa identidad tiene que estar escrita junto al término, como
metadato. La acuña kimun cuando genera la forma; una persona no la inventa.

## 5. Declarado y observado

Es el punto delicado, y `00-nucleo` ya lo separa:

- **2.3** un anchor es «un vínculo verificable entre un conocimiento y su soporte en una fuente».
  Eso se **declara**: lo escribe alguien y es estable.
- **2.4** «Si el soporte muta o desaparece, se revisa lo que depende de él; si se pierde soporte, se
  declara. Ninguna pérdida de soporte se oculta.» Para saber si mutó hace falta lo que se **observó**:
  la huella de lo que había.

Una observación también es una proposición: `(observed U "9f2c…")`, fechada. La escribe kimun, no una
persona. Aceptar que una fuente cambió es escribir una observación nueva; la anterior queda (**6.4**
«sin reescribir su historia»). Así, tras un clon, el sensor compara la fuente con la última
observación escrita y una mutación no se pierde. Esto encaja con lo que el dueño dijo del borrado:
nada se quita solo; «se marca».

**6.2** pone el límite: kimun conserva «su conocimiento propio y los vínculos necesarios para
verificar su dependencia del ambiente; no conserva una copia del ambiente». Una huella es un vínculo
de verificación, no una copia.

## 6. Ver, sintetizar, generar

«Kimun solo ve, sintetiza y genera, no debería exigir nada al resto.»

- **Ve**: descompone las fuentes en estructura (tree-sitter; para Clojure, el lector del lenguaje) y
  compara con lo observado. Sus propios archivos de declaración entran por aquí, como cualquier otro.
- **Sintetiza**: el grafo de proposiciones, derivado.
- **Genera**: materializa desde el grafo con modelos. Cuando un comando declara algo (`docs mark`),
  lo que hace es **generar código**: escribe la proposición en un archivo. Escribir declaraciones no
  es un mecanismo aparte; es generación.

Nada de esto toca las fuentes del proyecto observado ni les pide un formato.

## 7. Lo que se pierde, y los riesgos

- La historia paso a paso dentro del store y su verificación por repetición. La historia pasa a ser
  la de los archivos, en el control de versiones.
- La validación en el instante de escribir: una persona puede escribir una proposición mal formada;
  se detecta al leer.
- **Cantidad.** Hoy cada campo de cada documento tipado recibe una unidad sola (K1c). Como
  proposiciones escritas serían miles de líneas generadas. O eso deja de acuñarse solo, o esas
  proposiciones se derivan y no se escriben.
- **Costo.** Rehacer el índice tiene que ser incremental: hoy un latido tarda unos 38 s con 2000
  documentos (`runs/subagents/2026-11-15-MED1/`).
- Kimun hoy no puede leer Clojure (`runs/research/2026-11-15/grafo-propio/informe.md`). Es lo primero
  que falta.

## 8. Lo que no sé

No son opciones para elegir; son cosas que no entiendo todavía.

1. **¿Las declaraciones se leen o se evalúan?** Si solo se leen, cualquier forma sirve. Si además son
   Clojure que corre —`R` es algo definido, y una proposición se puede evaluar—, entonces «declarar y
   operar comparten un mismo lenguaje» (**7.1**) se cumple al pie de la letra, y el diseño es otro.
2. **¿Un contexto es un namespace?** Lo supuse en el ejemplo porque calza con **1.2** y con las
   jerarquías de **1.3**, pero `14 §3` dice que cada documento fuente abre su propio contexto.
3. **¿Dónde viven esos archivos?** En el proyecto, en un directorio de kimun, o fuera. Solo sé que no
   deben exigirle nada al proyecto.
4. **¿Qué se escribe y qué se deriva?** La línea entre lo que alguien declara y lo que kimun puede
   recalcular decide cuánto hay en esos archivos.

## 9. Primer paso que propongo

Uno solo, chico, para probar la idea antes de mover nada: que kimun **lea Clojure** como fuente, y que
un archivo con proposiciones `(anchors U (at …))` produzca en el índice las mismas marcas, con los
mismos estados y candidatos, que hoy produce `docs mark`. Lo construido hoy para anclas, estados y
candidatos se conserva; cambia de dónde viene la declaración. Sin tocar el journal ni los comandos
todavía.
