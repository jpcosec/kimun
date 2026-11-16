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

Una lista de proposiciones, en orden, como datos:

```clojure
{:r isa :a {:files "src/ui/**/*.py"} :b UI.component
 :context proyecto.decisiones :at {:file "kimun/decisiones.clj" :line 7} :id "…"}
```

`:id` es el hash canónico de `(R A B)` más el contexto (`kimun.kernel.canon`): la misma proposición
escrita dos veces es una. `:at` dice dónde está escrita; no entra en el `:id`.

## 4. Cómo

Con `sci`, que viene en Babashka: evalúa Clojure de verdad, aislado (sin disco, sin red, sin `eval`
del proceso), con el vocabulario (`relation`, `at`, `files`) como único punto de partida. Las
relaciones son macros de ese evaluador, para que los símbolos desnudos lleguen como términos.

## 5. Cortes

1. **Leer** (este): el evaluador puro y un comando de solo lectura que muestra las proposiciones de
   uno o más archivos. Sin store.
2. **Marcas desde declaraciones**: las proposiciones con `(at …)` dan las marcas, con los estados y
   candidatos de hoy (`mark-states`) y el sensor por git.
3. **Los comandos generan formas** en vez de escribir en el store; el índice se deriva.
4. **Borrar** lo que eso reemplaza.

## 6. Lo que no está pensado

- El porqué, quién y cuándo de una declaración (el «docstring tipado»). Con proposiciones sobre
  proposiciones (`supports`, arriba) quizás no haga falta nada más; no está decidido.
- Dónde busca kimun los archivos de declaración por defecto.
- Qué pasa cuando dos contextos definen la misma relación con distinto sentido.
