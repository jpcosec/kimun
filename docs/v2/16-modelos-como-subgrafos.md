# kimun — Modelos como clases, selectores y generación (Spec 16, Etapa A)

> **Estado:** especificación de diseño objetivo (**Etapa A**, tarea `M1`). Sustituye conceptualmente al
> modelo como plantilla única de [`07-modelos-como-nodos.md`](07-modelos-como-nodos.md), que sigue
> describiendo lo implementado hoy para compatibilidad de v1 hasta la ejecución de `P1`, `K1`/`D0` e
> implementación de `M1`.
>
> **Implementación:** M1a implementada (forma, jerarquía y composición del descriptor, `kimun.surface.models.subgraph`); resto de M1 pendiente.
>
> **Autoridad:** subordinado a [`01-orden-filosofico.md`](01-orden-filosofico.md) y
> [`14-juego-del-lenguaje.md`](14-juego-del-lenguaje.md) (`§9`, `§13`, `§18–§21`), decisiones
> `28`, `29`, `39`, `40–44` de [`05-estado.md`](05-estado.md), y evidencia medida en
> [`runs/research/2026-10-08/R3-report.md`](../../runs/research/2026-10-08/R3-report.md).
> **No resuelve ni especula sobre decisiones abiertas (`D0` identidad/continuidad, `D-G`, `A1.1`,
> `F10`, quirks v1 de `R3`)**, que quedan declaradas en `§6`.

---

## 1. Tesis: todo documento es instancia de una clase

Principio de [`14 §18.1`](14-juego-del-lenguaje.md#18-clases-instancias-e-indexación-2026-10-08)
(decisión 41): **todo artefacto observado (Markdown, código u otro formato estructurado) entra como
instancia de una clase.** No existen documentos sin clase ni archivos metidos enteros "en un zapato
chino" dentro del store estable.

| clase | origen estructural | granularidad y qué define |
|---|---|---|
| **Clase genérica del perfil** | Parser tree-sitter del formato (Markdown SLDB-MD, Python, Clojure, etc.) | Se fija **por store** y **por tipo de archivo**. Ejemplo en código (`14 §18.1`): clases, funciones y variables globales; no variables locales. Primer corte operativo sin modelo explícito: `K3`. |
| **Clase gobernada (modelo)** | Descriptor EDN inmutable en el paquete del grafo | Subclase (directa o por herencia) que declara un **modelo de anclaje** (qué extraer/anclar), restricciones de campo y cero, uno o varios **modelos de generación**. |

Un modelo pasa a definirse como terna de datos (`14 §9`, decisión 28):

```text
Modelo = (símbolo-tipo con herencia, selector de anclaje [EDN], proyecciones de generación)
```

### Lo que ya existe hoy vs. lo que especifica este documento

- **Implementado hoy:** [`07-modelos-como-nodos.md`](07-modelos-como-nodos.md) (descriptores
  `edn/model` con `:template` único bidireccional, regresión v1 cerrada en
  [`tests/parity-v1.md`](tests/parity-v1.md)), [`K3`](12-ecosistema-y-pendientes.md) (documentos sin
  modelo registrados bajo clase genérica) y el pod tree-sitter (`kimun.host.treesitter`, decisión 29).
- **Especificado aquí (Etapa A / implementación post-`P1` y `D0`/`K1`):** separación anclaje/generación,
  selector en patrones EDN sobre substrato tree-sitter, herencia por composición de selectores y contrato
  versionado (`K6`) por documento.

---

## 2. Separación entre anclaje y generación (`14 §18.7–9`)

El descriptor histórico v1 usaba un único `:template` para extraer texto y volver a renderizarlo. Ese
acoplamiento obligaba a simular round-trip textual estricto sobre prosa libre (`docs/faq.md`, `14 §18.7`).
Spec 16 separa ambas direcciones en dos modelos ortogonales sobre una misma clase:

### 2.1 Modelo de anclaje (lectura / clasificación)

- Es una función sobre el árbol/CST de un documento `source` bajo un contrato `K6` declarado.
- Decide **qué partes de la estructura son conocimiento trackeado** (`14 §20`) y entran al árbol
  Merkle del store estable, y qué fragmentos permanecen fuera del store.
- **No todo el texto se indexa (`14 §18.4`):** encabezados retóricos, fórmulas de cortesía o párrafos
  estilísticos no capturados por ningún paso anclado del selector se quedan en el archivo.
- **Azúcar en anclaje observado en el nodo (`14 §18.8`):** un fragmento del archivo es azúcar respecto al
  anclaje cuando no existe ningún campo ni hecho anclado sobre él. No hace falta anotar manualmente en el
  archivo qué párrafos ignorar.

### 2.2 Modelo de generación (escritura / proyección)

- Define cómo producir una salida textual o estructural (**Markdown**, **S-expresión**, **EDN/JSON**,
  **código**) a partir de los **datos de una instancia** y de las relaciones del grafo.
- Una misma clase puede tener **un modelo de anclaje** y **múltiples modelos de generación**
  (por ejemplo, vista auditable completa, ficha resumida o esquema de validación), sin exigir que
  ninguna proyección sea inversible línea a línea contra el `source` original.
- **Azúcar en generación (`14 §18.8`):** un nodo generado es azúcar cuando proviene de un literal fijo
  de plantilla o de una fórmula determinista sobre la instancia/AST, no de un campo editable anclado.

### 2.3 Autoridad: documentos `source` frente a documentos `generados` (`14 §18.9`)

| dirección / clase | dónde vive la autoridad | qué registra el store | política ante edición externa |
|---|---|---|---|
| **Documento `source` (anclado)** | En el archivo en disco (+ git) | Anchor del archivo, contrato/clase y hashes de lo trackeado (`14 §18.6`, `§20`) | El árbol se invalida localmente; los hechos/anclajes afectados pasan a señal de deriva para reconciliar |
| **Documento `generado`** | En los **datos durables de la instancia** (grafo/campos) + su modelo de generación | Datos de instancia y referencia al modelo de generación; no el archivo entero como verdad primaria | Si cambia el archivo generado, no muta en silencio la instancia (control negativo `F10`/`F-W09`); se regenera o se promueve un cambio explícitamente |

> **Nota explícita (`F10` / `M2`):** el *desk* de este repositorio fue escrito históricamente por
> `deskops` y retirado como modelos el 2026-10-09 (`kimun.include.edn`). Mientras no exista una
> instancia durable persistida independiente de los archivos `.md`, una reingesta en `M2` debe tratar
> esos archivos como **`source`** (`14 §18.9`), nunca asumir autoridad en una instancia inexistente.

---

## 3. Lenguaje de selección: Patrones EDN sobre substrato tree-sitter

En cumplimiento de la decisión 39 (`14 §9`), el spike [`R3-report.md`](../../runs/research/2026-10-08/R3-report.md)
midió los tres candidatos sobre **428 documentos reales** de 5 modelos del desk (`AtomDoc` 219,
`ChecklistDoc` 189, `TaskDoc` 18, `RitualDoc` 1, `PillDoc` 1) bajo `memcap` (3 GB), tomando
`kimun.surface.models.extract/extract` como línea base (**543 docs, 542 ok, 1 error en
`desk/tasks/Board.md` por falta de `status`, 165 ms**):

| candidato | paridad medida (428 docs) | tiempo (428 docs) | memoria RSS pico | portabilidad y reservas |
|---|---|---|---|---|
| **Patrones EDN** (`edn_engine.clj`) | **428 / 428 (100 %)** | **100 ms** (`p50` 0 ms) | **89 MB** (89.404 kB HWM) | EDN/JSON puro; ~90 líneas de intérprete; emitible por plugins Python/TS sin Clojure |
| **Consultas tree-sitter** (`ts_engine.clj`) | 423 / 428 (98,8 %) | 2.939 ms (`p50` 5 ms) | 95 MB + pod (3,2 MB) | S-expr nativo con regex `#match?`, listas anidadas y `pipe_table`; requiere pod tree-sitter |
| **Datalog sobre triples AST** (`dl.clj` / Datalevin 1.1.0) | 423 / 428 (98,8 %) | 343 ms in-process / 200+235 ms Datalevin | 92 MB / **254 MB** Datalevin | Sin regex nativo ni YAML; Datalevin 1.2.0 ausente y bugs en 1.1.0 con reglas sembradas y `d/clear` |

### 3.1 Decisión de arquitectura de selección (`R3 §7`)

1. **Lenguaje oficial de selectores: Patrones EDN.** El selector del modelo se expresa como datos EDN
   puros, declarando explícitamente el `:substrate` sobre el que opera.
2. **Parser de la capa M: `tree-sitter` (`kimun.host.treesitter`).** Se usa como substrato común
   cuando el selector o la clase genérica requiere **spans de bytes/líneas, anidamiento real de listas,
   tablas `pipe_table`, gramáticas de código o predicados `#match?`**.
3. **Datalog permanece reservado para la capa G** (decisión 30), no para el matching estructural sobre AST.

### 3.2 El `:template` de v1 como azúcar compilable

Para preservar la compatibilidad hacia atrás (`tests/parity-v1.md`), todo descriptor clásico de v1
(`07 §2`) es un subconjunto del descriptor de spec 16: sus marcadores (`⸢rev•…⸥`, `⸢optrev•…⸥`,
`⸢render•…⸥`, traits `|list`, `|table`, `|dict`) compilan determinísticamente a:
- un vector de pasos EDN en `:anchoring/:selector` (secuencia exacta equivalente al *recipe*), y
- una proyección de salida `:generation/:projections :markdown/v1`.

### 3.3 Capacidades exigibles al selector (`R3 §3`, `C1–C6`)

Un selector EDN soporta seis capacidades estructurales verificables:

| cap. | operación | forma EDN en `:selector` | ejemplo de sustrato / captura |
|---|---|---|---|
| `C1` | Secuencia exacta de bloques (caso v1) | Pasos ordenados con `:outer-type` / `:handler` y `:anchor true` | `{:outer-type "heading" :outer-tag "h2" :regex "^\\QAnswer\\E$" :anchor true}` |
| `C2` | Sección u hoja opcional (`optrev`) | `:want :opt` (0 o 1 coincidencia sin consumir bloques ajenos) | `{:key "provenance" :marker {:kind "optrev" :name "provenance"}}` |
| `C3` | Sección por heading/regex en cualquier posición | `:want :find :where {:outer-tag "h2" :re #"…"}` | Independiente del orden relativo respecto a secciones vecinas (`P8`) |
| `C4` | Lista anidada conservando jerarquía | Substrato `tree-sitter` (`:substrate :cst/markdown`) | Captura nodos `list`/`list_item` por nivel/indentación, no aplanados |
| `C5` | Tabla estructurada tras heading | `:want :table` sobre `pipe_table` del CST (`:substrate :cst/markdown`) | Captura filas/celdas tipables además de su span bruto |
| `C6` | Captura de valor + coordenada de anclaje | Toda captura emite `{:value … :path … :spans […] :leaf-hashes […]}` | Habilita invalidación local por hoja/rango tras ediciones externas (`F12`) |

### 3.4 Esquema declarativo del modelo en Spec 16

Ejemplo mínimo (sin presuponer representación interna de `D0`, con las claves de `R3-selectors/edn/ChecklistDoc.edn` y contrato `K6`):

```clojure
{:model/name      "ChecklistDoc"
 :model/version   2
 :base            ["PrimitiveDoc"]          ; DAG inmutable de herencia
 :substrate       {:parser    :treesitter   ; :treesitter | :sldb-md/blocks (legacy)
                   :grammar   "markdown"
                   :profile   "sldb-md/v2"
                   :compat    :strict}      ; :strict | :legacy-v1-template
 :contract/digest "sha256:…"                ; digest K6: gramática+perfil+NFC+selector+clase
 :anchoring
 {:fields   [{:name "id" :type :string :required true :description "…"}
             {:name "summary" :type :text :required true :description "…"}
             {:name "items" :type [:list :string] :required true :description "…"}]
  :selector [{:outer-index 0 :outer-type "front_matter" :handler :yaml
              :markers [{:key "id" :marker {:kind "rev" :name "id"}}]}
             {:outer-index 1 :outer-type "heading" :outer-tag "h1"
              :handler :text :props ["title"] :regex "^(.*)$"}
             {:outer-index 2 :outer-type "heading" :outer-tag "h2"
              :handler :text :anchor true :regex "^\\QSummary\\E$"}
             {:outer-index 3 :outer-type "paragraph" :handler :text
              :props ["summary"] :capture :section-body :regex "^(.*)$"}
             {:outer-index 4 :outer-type "bullet_list" :outer-tag "ul"
              :handler :list :name "items"}]}
 :generation
 {:projections {:markdown/default {:format :markdown :mode :sections}
                :summary/short    {:format :markdown :fields ["id" "summary"]}}}}
```

> **Contrato `K6` (`F08`):** todo anclaje registra `:contract/digest` (gramática, versión de parser,
> perfil, normalización NFC, selector y descriptor de clase). Al reconstruir el caché, un cambio en el
> archivo produce `:source-changed`, un cambio en el descriptor produce `:contract-changed`, y una
> ausencia del pod o gramática produce `:parser-unavailable`, evitando diagnosticar todo desacuerdo como
> edición externa del usuario.

### 3.5 Tratamiento de las 5 discrepancias de v1 medidas en `R3`

En los 428 documentos medidos por `R3`, Datalog y tree-sitter divergieron de `extract` v1 exactamente en
los mismos 5 documentos por dos peculiaridades de la implementación heredada de v1:

1. **3 × `AtomDoc` sin `## Answer`** (`git-orchestration-in-python`, `immutable-append-only-database`,
   `relation-ast-extensibility`): el bucle de `section-body` de v1 capturaba desde el principio del
   documento cuando faltaba el heading `Answer`, mientras el selector estructural declara `answer`
   ausente (`:missing`).
2. **2 × `TaskDoc` con `## Done When` vacío** (`task-specify-diagram-traceability-contract`,
   `task-specify-specyaml-schema-contract`): v1 devolvía `"## Done When\n\n_…_"` (incluyendo el heading);
   el selector estructural devuelve `""`.

**Regla de Spec 16 ("features de v1 sí, bugs no"):** el modo `:strict` (por defecto para modelos nuevos de
spec 16) reporta campo obligatorio ausente como `:missing` y sección presente vacía como `""`. Los
descriptores migrados que deban reproducir exactamente el oráculo histórico de `parity-v1.md` declaran
explícitamente `:compat :legacy-v1-template`.

---

## 4. Herencia de clases e inmutabilidad (`14 §18.2–3`)

Cuando un documento agrega estructura legítima que excede una clase base (ejemplo del dueño:
`documento.resumen.resumen_tecnico` hereda de `resumen` y agrega lo suyo), **no se fuerza el árbol ni
se rompe la clase base**: se deriva una subclase mediante `:base`.

### 4.1 Semántica de jerarquía (`derive` / `isa?`)

- La relación de herencia entre clases se declara en `:base` (DAG acíclico serializado y linealizado).
- La consulta de pertenencia sigue semántica de taxonomía de Clojure (`isa?`): toda instancia de
  `ResumenTecnicoDoc` es también `isa?` `ResumenDoc` para filtros, consultas e índices transversales (`P13`).

### 4.2 Composición separada para anclaje y generación

- **En anclaje (`R3 §4`):** el selector del hijo compone los pasos de sus ancestros más sus propios
  pasos (`(into pasos-padre pasos-propios)` con reindexación determinista de `:outer-index`, o unión
  por clave de campo/sección cuando usa `:want :find`). Una subclase **no puede eliminar** restricciones
  obligatorias heredadas salvo declarando una clase raíz distinta.
- **En generación:** la subclase hereda las proyecciones nombradas de la base y puede añadir nuevas
  proyecciones o sobreescribir una proyección específica sin afectar el modelo de anclaje ni el selector
  heredado.
- **Diamante (inferencia del equipo, no del spec):** si un ancestro es alcanzable por varios caminos, entra
  una sola vez, en la posición de su primer alcance (ancestro primero).

### 4.3 Inmutabilidad de clase y lectura bajo contrato (`14 §18.3`, decisión 40)

- **Una clase cambiada es otra clase:** editar campos, selectores o herencia crea una nueva revisión/id de
  descriptor; la versión previa continúa existiendo como objeto inmutable mientras algún documento la
  referencie.
- Un documento ya clasificado conserva en su evidencia `:contract` la referencia a la clase exacta con la
  que fue anclado hasta que un actor o migración lo reclasifique explícitamente (`docs update` /
  `--backfill`).

---

## 5. Frontera entre capas (`14 §19–§20`, decisiones 43–44)

Spec 16 respeta la arquitectura en tres capas fijada el 2026-10-09 (`P1` + `K2a`):

| capa | paquete / ubicación | qué contiene respecto a modelos | dependencia permitida |
|---|---|---|---|
| **Grafo (modelos y G)** | Paquete aparte (`kimun.graph.*`, `P1`) | Descriptores de clase, herencia `derive`/`isa?`, selectores EDN, proyecciones de generación y datos de instancia | Depende de la base y del parser M; **nunca** a la inversa |
| **Store estable (base)** | Kernel (`kimun.kernel.*`, `K2a`) | Registro por ruta de documentos trackeados: `{:anchor … :class-ref … :contract/digest … :tracked-root-hash …}` | **No interpreta modelos ni lee selectores EDN**; solo compara hashes de proyecciones entregadas |
| **Caché** | `<store>/cache/` (borrable) | Árboles CST/AST completos, valores extraídos de campos, recetas compiladas y vistas proyectadas | Recomputable a partir de `archivo + clase/contrato` (si `source`) o `instancia + proyección` (si `generado`) |

Consecuencia para `14 §20` (trackeable vs. trackeado): el Merkle de reconciliación del store estable se
calcula **únicamente sobre la proyección trackeada** que selecciona el modelo de anclaje (en árbol por
ruta, no sobre texto no indexado ni sobre planes del build en el CAS).

---

## 6. Decisiones abiertas explicitadas (sin especular `D0`)

> **Actualización 2026-11-15.** `D0` dejó de ser una decisión abierta del dueño: el concilio derivó
> de sus premisas el contrato S–M de identidad y continuidad (`14 §21`, reglas rotuladas como
> inferencias del concilio). Donde este spec dice «`D0`» como bloqueo, léase «`K1`», su
> implementación, que hoy solo cubre el nivel documento. Sigue valiendo que Spec 16 no fija ningún
> esquema de ids. El punto 1 de abajo queda como registro de lo que `K1` y `S2` tienen que aterrizar.

La especificación queda completa a nivel de contrato documental; **la implementación en código queda
bloqueada** donde depende de las siguientes decisiones abiertas del dueño (`preguntas-abiertas.md` y
`runs/research/2026-11-14/concilio/ambiguedades.md`):

1. **`D0` — Identidad positiva del nodo y continuidad temporal (`14 §21`, `ambiguedades.md A2.1–A2.2`):**
   - El selector de Spec 16 entrega unidades extraídas con `{:value :path :spans :content-hash}`.
   - Qué identificador permanente recibe cada unidad o instancia al crearse, y cuándo una edición/mudanza/
     `split`/`merge` conserva esa identidad frente a emitir candidato `ambiguous`, **espera el aterrizaje del
     contrato `D0` en `K1` y `S2`**. Spec 16 no fija ni infiere ningún esquema de IDs para `D0`.
2. **Correspondencia sensor `S` ↔ unidad `M` (`ambiguedades.md A1.1`):**
   - Está abierto si un elemento capturado por el selector (por ejemplo, una sección Markdown o función de
     código) y su sensor topológico en `S` comparten un único nodo con facetas de anclaje o son dos
     nodos distintos vinculados por arista de soporte.
3. **Contrato durable de instancias generadas (`F10`, `14 §18.9`):**
   - Para que un documento deje de considerarse `source` y pase a modo `generado`, falta fijar dónde y en
     qué formato durable (fuera del caché) residen los valores de instancia antes de `M2`.
4. **Sustrato único CST vs. compatibilidad v1 y quirks en `M2` (`R3 §7 reservas 1–2`):**
   - Falta decidir para cada modelo del desk al regenerar en `M2` si aplica el modo `:strict` (corrigiendo
     las 5 divergencias de `AtomDoc`/`TaskDoc` medidas en `R3`) o `:legacy-v1-template`.

---

## 7. Gate de cero contexto (criterios de aceptación de Spec 16)

Cualquier agente que implemente o audite `M1` debe poder responder estas preguntas únicamente leyendo este
documento y sus autoridades citadas:

| pregunta de control | respuesta verificable en Spec 16 | prueba mínima en implementación (`post-P1/K1`) |
|---|---|---|
| ¿Qué diferencia un modelo de anclaje de uno de generación? | Lectura/selección desde `source` (`§2.1`) vs escritura/proyección desde instancia (`§2.2`); la autoridad difiere (`§2.3`) | Un mismo `ChecklistDoc` con 2 proyecciones distintas produce salidas diferentes sin cambiar lo trackeado |
| ¿Por qué patrones EDN + tree-sitter en vez de Datalog o S-expr puras? | R3 midió 428/428 (100 %) en 100 ms / 89 MB RSS para EDN; tree-sitter aporta spans/anidamiento/tablas (`§3`) | Suite de regresión de selector EDN sobre corpus + fixtures CST con listas anidadas y `pipe_table` |
| ¿Dónde se observa qué es azúcar? | En el nodo, por ausencia de anclajes (`§2.1`) o por fórmula/literal en generación (`§2.2`) | Añadir un párrafo estilístico no anclado en `source` no cambia el `:tracked-root-hash` |
| ¿Cómo hereda un modelo hijo? | `:base` DAG con `isa?`; selector compuesto `(into padre propio)` con reindexación y proyecciones independientes (`§4.2`) | Subclase `ResumenTecnicoDoc` de `ResumenDoc` satisface `(isa? …)` y captura campos propios + heredados |
| ¿Qué ve el store estable de un modelo? | Solo `{anchor, class-ref, contract/digest, tracked-root-hash}`; sin AST completo ni lógica de selectores (`§5`) | `bb lint` de anillos: `kimun.kernel.*` tiene cero dependencias hacia `kimun.graph.*` |
| ¿Qué sigue abierto por `D0`? | Identidad positiva, continuidad ante move/split/merge y vínculo sensor `S` ↔ unidad `M` (`§6`) | El contrato de salida del selector expone spans y hashes sin prefijar esquema de ID |
