# El núcleo de sldb en este motor: los modelos y el grafo

Borrador de Claude (coordinador), 2026-11-17. **Reemplaza** la versión del 2026-11-16, que estaba mal
planteada: repartía la API de sldb en cajas («fácil», «difícil», «periférico») sin haber leído el
código, y trataba las reglas como difíciles y las escrituras como «la decisión». Esa versión queda en
el historial de git (`005eec96`).

Entre «» va el dueño, textual (`sesion-2026-11-15.md`, secciones del 2026-11-16). Lo que va en §2 es
lo que leí en el código y los ADR de sldb (`~/proyectos/SLDB_1/sldb` en `472f1c80`, solo lectura), con
la ruta. Lo que va en §3 a §6 son **elecciones mías**, no decisiones del dueño. §7 es lo que él tiene
que confirmar. No se implementa ni se renombra nada a partir de este documento hasta que lo confirme.

Leí: los ADR `sldb-absorbs-kgdb.md`, `model-authoring.md`, `derived-store-boundary.md`,
`what-the-graph-is-not-being-asked.md` y `anchoring.md` (§1–4); en el código, `runtime/validation.py`,
`core/data_extractor.py`, `store/hashing.py`, `store/runtime_codec.py`; en deskops,
`deskops/models/task.py` y `.sldb/store.yaml`. Lo demás viene de los informes del inventario
(`runs/research/2026-11-16/sldb-features/`, `00`, `02`, `04` y `05`), que marcan qué verificaron.

## 1. Lo que dijo el dueño

- «esto no está listo hasta que no sea capaz de 1- Ser el nucleo nuevo de sldb y 2- Usar algun
  proyecto ya avanzado como deskops y ingestarlo.»
- «para todo lo que se pueda implementar facilmente, implementemoslo en kimun. Pero muy posiblemente
  haya cosas "perifericas" para las que sea mas facil hacer adaptadores a python o sdk»
- «kgdb mas que congelado, está integrado en sldb. Creo que conocer eso te va a desbloquear lo de las
  reglas»
- Sobre «el núcleo es el grafo con su evaluación de predicados»: «bueno, eso y entender (y migrar
  bien) los modelos»
- «En kimun tambien el documento es la fuente, para que crees que tenemo anchor?»
- «SLDB ya está publicado. Y kimun es un nombre que me gustaría reutilizar en otra cosa»

Y de antes, que sigue valiendo: «Solo lo que está anchored importa a nivel de fields», «la realidad
existe NO en nuestro sistema, sino en el código de afuera», «La gracia de poder hacer clases es que
así podemos reutilizar código, lo que haría que indexar muchas cosas iguales sea una declaración en
lista nomás», «TODOS los documentos que genera SLDB […] Pasarían a ser MATERIALIZACIONES DESDE
MODELOS», y que el rehacer git queda «EXPLÍCITAMENTE diferido».

## 2. Qué es sldb, leído en su código

### 2.1 Un producto, dos capas, una sola verdad

`docs/architecture/sldb-absorbs-kgdb.md` (aceptado por el dueño el 2026-09-20): sldb es «one product
with two internal layers: a text layer and a graph layer». La capa de texto es dueña de los documentos,
la extracción y el render; la de grafo, de los nodos y aristas «derived from documents», el recorrido y
las consultas. El grafo «was a projection with its own process, not a second source of truth». Es lo
que el dueño dijo: kgdb no está congelado aparte, está adentro.

### 2.2 Un modelo amarra la lectura, la escritura y las aristas

Un modelo es una clase Python que hereda de `StructuredNLDoc` (pydantic). En una sola clase declara
(informe `02 §2`; lo comprobé en `deskops/models/task.py`):

- **los campos**, cada uno con descripción obligatoria;
- **`__template__`**: el formato del archivo con marcadores `⸢rev•campo⸥`, `⸢optrev•…⸥`,
  `⸢render•…⸥`, `⸢py•…⸥`. La misma plantilla extrae (texto → datos) y renderiza (datos → texto); sldb
  valida la ida y vuelta comparando datos: renderizar y volver a extraer da lo mismo
  (`runtime/validation.py:51-57`);
- **qué campos son aristas**: `__references__`, `__reference_models__` (a qué modelos puede apuntar
  cada campo), `__containment__`, `__compositions__`;
- **etiquetas**: `__semantics__` (`{"type": ["workflow", "task"]}` da `type.workflow.task`);
- **anclas**: `__doc_anchors__` (qué campos guardan `doc:Modelo:nombre@sha256:…`) y `__contract__`
  (qué campos cuentan para ese hash);
- **validadores pydantic**: invariantes de un solo documento (18 de campo y 6 de modelo en los 49
  modelos de los consumidores);
- **la herencia**, que las reglas leen como familia (`model <= TaskDoc`).

En deskops, `TaskDoc` declara de una vez que `depends_on` apunta a `TaskDoc`, `current_node` a
`RitualDoc` y `atoms` a `AtomDoc`, y que el hash de contrato cubre siete campos.

### 2.3 El grafo se deriva de los modelos

Informe `04 §2–3`. Casi todo lo que hay en el índice de aristas sale de un atributo de modelo:

| atributo o hecho | arista o nodo |
|---|---|
| el documento es de un modelo | `has_document` (modelo → documento) |
| `__references__`, `__reference_models__`, `__doc_anchors__` | `references` (con `field` y el valor tal como se escribió) |
| `__containment__` | `contains` |
| `__compositions__` | `composes` |
| los campos de la clase | `has_field` y nodos `sldb_field` |
| la herencia | `extends` |
| `__semantics__` y `tags` del documento | `tagged_as`, nodos `semantic_tag`, `semantic_parent` por los puntos del nombre |
| un `RelationDoc` (un documento) | una arista con el verbo que el usuario escribió, su `condition` y sus efectos |
| los títulos | `has_section` |

Lo que cruza documentos (resolver `task-x` a `TaskCodeDoc:task-x`, heredar la condición del tipo de
relación, descartar aristas colgantes, el estado de las anclas) se calcula al componer y nunca se guarda
(`04 §7.1`).

### 2.4 Un lenguaje de predicados; reglas y transiciones son documentos

Informe `05 §2–4`. Desde el 2026-11-06 hay un solo lenguaje (texto, con parser propio) para `--where`,
selectores, reglas, condiciones de aristas y vistas. Una regla es un documento `RuleDoc` (sujeto
`model <= X`, `where`, `condition`, severidad, momento `on_write|on_transition|on_check`). El ciclo de
vida de deskops son tres `RelationDoc` entre rituales, con condición y `sets`; avanzar una tarea es
pedir qué aristas salen de su ritual y se cumplen (`edges_holding`) y aplicar sus efectos.

Lo que deskops usa de verdad es poco (`05 §4.5`): 16 reglas, 3 condiciones de arista y 2 vistas (`board` y `task`). Igualdad
con cadenas, `has`, `nonempty`, `not`, `or`, `and`, `model <=`, `any`/`all` sobre `depends_on`,
`references` y `^subject`, y `anchor_state = "intact"`. Nada de `rel:`, clausura `+`, `count`, `none`,
regex ni números. El tercer valor del lenguaje, `incomplete`, no lo produce ningún dato real
(`05 §2.9`).

### 2.5 Las anclas a documentos dependen de Python

`doc:Modelo:nombre@sha256:H` guarda el hash de contrato que el ancla vio del destino; su estado es
`intact`, `changed` o `lost` (`anchoring.md §6`, `05 §3.3`). Lo comprobé en el código: el hash de un
campo es `sha256(json.dumps(valor, sort_keys=True, ensure_ascii=False))` (`store/hashing.py:14`), pero el
valor no es lo que la plantilla extrae, sino lo que sale de pasarlo por la clase pydantic:
`model_type(**extraído).model_dump(mode="json", exclude_none=True)` (`runtime/validation.py:19`). Los
valores por defecto y los validadores `before` de la clase entran en el hash. En deskops, 13 de las 16
reglas exigen `anchor_state = "intact"`, y el desk tiene 275 anclas con hash en las reviews (informe
`runs/subagents/2026-11-16-lectores-desk/REPORT.md`).

### 2.6 La maquinaria del store

`derived-store-boundary.md` y el informe `03`: `.sldb/core/` (versionado: un shard por documento con
sus hashes, un nodo por modelo, la raíz Merkle) y `.sldb_cache/` (aristas por documento, secciones,
índice compuesto, caché de reglas). `stores update` relee y hashea todo; `stores check` vuelve a
extraer todo. En deskops, el `include` de `.sldb/store.yaml` dice qué archivo es de qué modelo con
**9 globs y 710 rutas escritas una por una**.

## 3. Lo que entiendo (lectura mía)

El núcleo de sldb son dos mitades que en el código ya están juntas: **los modelos** (cómo se lee y
cómo se escribe cada clase de documento, y qué de él son aristas, etiquetas y anclas) y **el grafo con
su lenguaje** (lo que se deriva de los documentos a través de los modelos, y las preguntas que se le
hacen). Los cinco conceptos que sobreviven son modelo, ancla, arista, predicado y vista.

El documento es la fuente en los dos sistemas. En sldb, el modelo dice qué partes del documento son
datos; en este motor eso es un ancla: un campo extraído por la plantilla es una región anclada del
archivo, y las proposiciones que salen de ese campo apuntan a ella. Por eso el monitoreo «dentro» de un
archivo es por campo, y solo de lo que un modelo ancla.

Hoy este repo no hace eso. El 2026-11-16 se escribieron trece lectores a mano, uno por clase del desk
(`runs/subagents/2026-11-16-lectores-desk/readers/`), que repiten en Clojure lo que las clases de
deskops ya declaran (`TaskDoc` ya dice qué campos son referencias y a qué modelo apuntan), con los
helpers copiados trece veces. Y la plantilla, que en sldb sirve para leer y para escribir, aquí son dos
cosas sin relación: `reading` lee, `generated` escribe. «Migrar bien los modelos» es, en mi lectura,
que el lector y la receta de generación salgan los dos del modelo, como en sldb, y no de escribirlos a
mano.

Lo que deja de hacer falta es la maquinaria del store (§2.6): git dice qué archivo cambió; el modelo
dice qué campo; lo que cruza documentos se calcula al preguntar, como sldb ya lo hace al componer.

## 4. Cómo queda (elecciones mías)

### 4.1 Un modelo es una declaración; el lector y la receta salen de él

Un modelo se declara una vez, con lo mismo que declara la clase de sldb. La forma es ilustrativa; los
nombres no están decididos:

```clojure
(model Task
  "Una tarea del desk."
  {:template  (slurp-template "…")            ; la plantilla de sldb tal cual: los documentos se leen tal como están
   :fields    {title {:doc "Short action-oriented task title."} …}
   :refs      {depends_on [Task] current_node [Ritual] atoms [Atom] pills [Pill]}
   :semantics {type [workflow task] workspace [desk]}
   :contract  [title goal scope implementation_path files validation done_when]})

(instances (files "desk/tasks/**/task-*.md") Task)   ; una declaración, muchos archivos
```

De esa declaración salen, sin escribir nada más:

- **la lectura**: la plantilla extrae los campos; cada campo es un ancla al archivo; salen las
  proposiciones `(isa doc Task)`, una por valor de cada campo de `:refs` (con el nombre del campo como
  relación, como hace sldb), una por etiqueta;
- **la escritura**: la misma plantilla renderiza un documento desde datos. Un documento generado es
  una receta que usa el render del modelo (lo que hoy es `generated` pasa a ser un caso del modelo);
- **las aristas de estructura** de §2.3 (`has_document`, `extends`, `has_field`, `tagged_as`) como
  proposiciones derivadas, no escritas.

El motor de plantillas ya existe en este repo: `src/kimun/surface/models/` (unas 2 400 líneas, con
pruebas en `test/kimun/surface/models/`), escrito con la semántica de sldb v1 para el camino viejo con
store. `extract-raw` toma una plantilla y un texto y no toca el store. Mi elección: reutilizarlo, no
escribir otro, y ponerlo al día con lo que sldb cambió desde v1 (anclas literales de párrafo,
fronteras por título ausente y escapado de ítems de lista entraron en sldb el 2026-10-18; informe `01
§2.2`). Cuánto difiere hoy no está medido.

### 4.2 Las clases comprimen

`(instances patrón Modelo)` reemplaza las 710 rutas escritas una por una del `include` de deskops. Es
la «declaración en lista nomás». Un archivo puede ser de varias clases por ejes distintos (dueño,
2026-11-15).

### 4.3 El grafo

Nodos son términos; aristas son proposiciones. Lo de §2.3 sale de los modelos al leer; lo que cruza
documentos (resolver referencias sin calificar, ambiguas o faltantes, que sldb informa como
`unresolved_references`) se calcula al preguntar y no se guarda. Los identificadores `Modelo:nombre` de
sldb se conservan: están escritos en las fuentes (`RitualDoc:ritual-closeout`, los `doc:…` de las
reviews) y el documento es la fuente.

Las preguntas de grafo de sldb (camino, ciclos, orden, componentes, centralidad, vecindad) son recorridos
sobre proposiciones; el repo ya tiene `kimun.graph.analysis` y `traversal` del camino viejo. Quedan para
después de las reglas: deskops no las usa.

### 4.4 El lenguaje de predicados

Las reglas de deskops son documentos con la condición escrita como texto en el lenguaje de sldb. Como el
documento es la fuente, ese texto se lee tal cual: **este motor parsea el lenguaje de sldb** y lo
evalúa sobre las proposiciones. Elección mía: el parser lo convierte en una forma de Clojure y se evalúa
esa forma (homoiconicidad); una regla nueva se puede escribir directamente como forma. La gramática es
corta (informe `05 §2.2`); se porta entera, no solo lo que usa deskops.

**El tercer valor (`incomplete`) no se porta.** Que una proposición pueda ser verdadera, falsa o
desconocida es la pregunta abierta `D-G` (`preguntas-abiertas.md`), y AGENTS.md prohíbe implementar lo
que depende de una decisión abierta. En sldb no lo produce ningún dato real, así que la evaluación de
dos valores da los mismos resultados sobre deskops.

### 4.5 Reglas, transiciones, vistas y etiquetas

- **Reglas**: un `RuleDoc` se lee con su modelo como cualquier documento; `rules check` es preguntar.
- **Transiciones**: un `RelationDoc` es una proposición con condición y efectos; «qué aristas se
  cumplen para esta tarea» es preguntar; calcular el payload resultante es una función pura.
- **Vistas**: el `ViewDoc` del Board y el de una tarea son recetas que preguntan y renderizan.
- **Etiquetas**: espacios de nombres y árbol de etiquetas son preguntas sobre proposiciones `tagged`.

Lo que hacen al **escribir** (reglas `on_write`, aplicar una transición al archivo de la tarea) depende
de §7, pregunta 2.

### 4.6 Lo que no se construye

Shards, cadena de hashes del store, índice compuesto en disco, caché de reglas y conjunto sucio,
`stores update|build|check` como reconstrucción, federación de stores enlazados. Si una medición pide
caché, es la de `diseno-declaraciones.md §6`: lo leído de un archivo, guardado por el hash que git ya
tiene de ese archivo.

## 5. Lo periférico (adaptador o SDK en Python)

| qué | por qué queda en Python |
|---|---|
| los validadores pydantic de cada modelo | son código Python del consumidor; el motor no lo ejecuta |
| el marcador `⸢py•…⸥` | evalúa Python |
| crear y editar clases de modelo (`create_model`, borradores, `promote --backfill`) | reescriben archivos `.py` por AST |
| notaciones y diagramas (IR), corpus y matching (pron) | sirven a un solo consumidor (informe `00 §6`) |
| `StructuredNLDoc`, la jerarquía de errores, `main` en proceso | forma de la librería Python |
| `__artifact__` de deskops | sldb no lo lee: es de deskops |

El SDK le pregunta a un proceso del motor que queda abierto (`mcp` o `serve`): los consumidores llaman
a sldb dentro del proceso miles de veces y un proceso por llamada no sirve. Cuánto tarda no está medido.

## 6. Lo que no sé

- **El hash de las anclas cruza la frontera.** Por §2.5, calcular `anchor_state` igual que sldb exige
  aplicar defaults y validadores `before` de la clase pydantic. Para los siete campos de contrato de
  `TaskDoc` creo que basta extraer y aplicar los defaults declarados, porque sus validadores `before`
  solo quitan claves nulas; no lo he probado. Para un modelo con validadores que cambian valores, el
  hash queda del lado de Python.
- **Dónde viven los modelos** (§7, pregunta 1). En deskops las 17 plantillas son cadenas literales y
  los atributos son literales: se pueden leer del `.py` sin ejecutarlo (el pod ya trae la gramática de
  Python). En IR la plantilla se arma con una función: no se puede leer sin ejecutar.
- **Cuánto difiere el motor de plantillas del repo del de sldb hoy.** Nadie lo midió desde que se
  suspendió la paridad.

## 7. Lo que tiene que confirmar el dueño

1. **¿Qué es «migrar bien los modelos»?** Lo leo de dos maneras y no sé cuál es:
   - (a) **los modelos pasan a ser declaraciones de este motor** (§4.1) y la clase Python se genera
     desde ellas, con los validadores escritos aparte en Python; o
   - (b) **las clases Python siguen siendo la fuente del modelo** y el motor las lee sin ejecutarlas
     (las de deskops son literales), así deskops no se toca.
   Me inclino por (b) para ingerir deskops ahora y (a) como paso siguiente, pero (b) deja el modelo en
   Python y no sé si eso es lo que no quieres.
2. **¿Escribir fuentes es núcleo?** deskops vive de crear y editar documentos (`add task`, `advance`):
   sldb renderiza con la plantilla, corre las reglas `on_write` y escribe con transacciones. Con el
   modelo como plantilla de ida y vuelta, renderizar ya es del motor; lo que no sé es si el motor
   también escribe el archivo fuente (y entonces deja de ser solo «ve, sintetiza y genera») o si lo
   escribe el SDK y el motor se entera por git como de cualquier cambio. Me inclino por lo segundo.
3. **¿Reutilizo el motor de plantillas que ya está en el repo** (§4.1), poniéndolo al día con sldb, en
   vez de escribir otro?
4. **¿Está bien conservar los identificadores `Modelo:nombre` de sldb** (§4.3) y parsear su lenguaje de
   predicados tal cual (§4.4)? Los dos están escritos en las fuentes de deskops.

## 8. Las primeras pruebas, si confirma (nada despachado)

Todas en copias, con sldb y deskops en solo lectura:

1. Leer con los modelos de deskops los 874 documentos con frontmatter y comparar cada payload con el
   que da `sldb` (`extract_model_data`) sobre la misma copia.
2. Calcular el hash de contrato de las tareas y compararlo con las 275 anclas con hash de las reviews:
   dice si §6, primer punto, se cumple.
3. Comparar las aristas leídas con el índice de aristas de sldb (entrada del drawer
   `task-compare-declare-about-with-the-sldb-edge-index-on-the-desk`).
4. Evaluar las 16 reglas de deskops y comparar con `sldb rules check`.

Ninguna de estas cumple el criterio 1 por sí sola. Se cumple cuando deskops corre con este motor debajo.
