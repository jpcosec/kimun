# Kimun como núcleo nuevo de sldb: qué va en kimun y qué va por adaptador

Borrador de Claude, 2026-11-16. Sigue a `diseno-leer.md §6`. Entre «» va el dueño; lo demás son
elecciones mías. La partición de §3 es una estimación hecha leyendo inventarios: nada de ella está
probado, y no leí el código de sldb por dentro.

> **Mal planteado; no construir sobre §3 ni §5 (2026-11-16, mismo día).** El dueño corrigió el marco
> dos veces después de escrito: «kgdb mas que congelado, está integrado en sldb. Creo que conocer eso
> te va a desbloquear lo de las reglas» y, sobre el núcleo como grafo más evaluación de predicados,
> «bueno, eso y entender (y migrar bien) los modelos». El núcleo de sldb son dos mitades (el grafo con
> su lenguaje de predicados, y los modelos), no la lista de funciones de API que reparte la tabla de
> §3; las reglas no son «difíciles» y las escrituras no son «la decisión». Se rehace desde ahí.

## 1. Lo que dijo el dueño

«esto no está listo hasta que no sea capaz de 1- Ser el nucleo nuevo de sldb y 2- Usar algun proyecto
ya avanzado como deskops y ingestarlo.»

Sobre qué significa el punto 1, el mismo día: «No, puta, para todo lo que se pueda implementar
facilmente, implementemoslo en kimun. Pero muy posiblemente haya cosas "perifericas" para las que sea
mas facil hacer adaptadores a python o sdk».

De ahí sale la regla de este documento: cada cosa que sldb hace cae en una de cuatro cajas, **ya lo
hace kimun**, **fácil en kimun**, **difícil** o **periférico (adaptador a Python o SDK)**. El dueño
no dijo dónde pasa la línea; la de §3 es mía y él la corrige.

## 2. De dónde salen los datos

- Qué usan de sldb sus cinco consumidores (deskops, pron, spec2viz, graph_ui, IR): 527 sitios de
  import, `runs/research/2026-11-15/reemplazo-sldb/superficie-consumidores.md`.
- Qué había en kimun frente a eso el 2026-11-15: `…/reemplazo-sldb/preparacion.md`. Ese informe
  mide el kimun anterior (store propio, motor de modelos); buena parte se borró desde entonces.
- Qué lee kimun hoy del desk de deskops: `runs/subagents/2026-11-16-lectores-desk/REPORT.md` (874
  archivos con frontmatter, 13 lectores, 6133 proposiciones, 0 errores, 4 a 8 s).

## 3. La partición

Agrupo lo que sldb hace por función, no por nombre de API. Entre paréntesis, los nombres de
`sldb.api` más usados de cada grupo.

| Qué hace sldb | Caja | Por qué |
|---|---|---|
| Saber qué documentos hay y de qué clase (`load_runtime_documents`, `tracked_documents`, `is_model_registered`) | ya lo hace | Un lector por clase; medido sobre el desk de deskops |
| Saber qué cambió (`update_store_indexes`, `stores update`, `stores build --check`) | ya lo hace | Lo dice git; no hay índice que reconstruir. Es el problema de rendimiento medido de sldb |
| Aristas entre documentos (`load_edge_index`, `edges_from`, `edges_to`, `rebuild_edges`, `RelationDoc`) | ya lo hace, **sin comparar** | Son proposiciones; `declare about` responde en los dos sentidos. Falta medir contra el índice de aristas de sldb |
| Etiquetas con namespace (`tag_tree`, `tag_namespaces`, `tag_check`) | fácil | Ya se leen como proposiciones `tagged`; falta el árbol y el chequeo |
| Resolver una referencia (`doc_status`, `doc_exists`, `unresolved_references`, `resolve_indexed_document`) | fácil | Es preguntar si un término tiene fuente, ninguna o varias |
| Seleccionar y filtrar (`select_documents`, `where_matches`, consultas de grafo de pron) | fácil | Consultas sobre proposiciones; el lenguaje de `--where` de sldb habría que portarlo o reemplazarlo |
| Vistas y documentos generados (`run_view`, `list_views`, el Board) | fácil | Son recetas de `declare generate`; el dueño ya lo dijo: «TODOS los documentos que genera SLDB […] pasarían a ser MATERIALIZACIONES DESDE MODELOS» |
| Chequeo del store (`check_store`, `stores check --format json`, que deskops lee en tres lugares) | fácil | Un comando que reporta lo mismo desde las proposiciones; la forma exacta del JSON es de adaptador |
| Stores enlazados (`link_store`, `store_links`, `include_linked`) | fácil o difícil, no lo sé | Depende de cómo kimun lea un segundo proyecto; no está pensado |
| Reglas (`rules_check`, `transition_findings`, `edges_holding`, `edges_blocked`, `RuleDoc`) | difícil | Las reglas son documentos con una condición en un lenguaje propio de sldb; hay que evaluarlo o traducirlo |
| Modelos tipados: extraer y renderizar contra una plantilla (`extract_model_data`, `render_model_markdown`, `validate_model_input_roundtrip`: 75 referencias, lo más usado) | difícil | El motor de modelos de kimun quedó a medias y el plan nuevo lo reemplaza por recetas; ida y vuelta exacta de Markdown es trabajo grande |
| Escribir documentos (`create_document`, `save_document_payload`, `transaction`, `actor`, `replace_text`, `rewrite_references`) | difícil, y **es una decisión** | Kimun «solo ve, sintetiza y genera, no debería exigir nada al resto». Escribir fuentes cambia qué es kimun (§5) |
| Clases pydantic con validadores propios (50 modelos en los consumidores) | periférico | Es código Python de cada consumidor; kimun no lo puede ejecutar |
| `StructuredNLDoc`, la jerarquía `SLDBError`, `main` en proceso, `init_store`, `open_store`, `add_model` | periférico | Forma de la librería Python; un SDK la conserva o la reemplaza |
| Notaciones y diagramas (IR), corpus, matching y embeddings (pron), `model_create` (pron), extensiones de MCP (IR) | periférico | Los usa un solo consumidor y no tienen que ver con ver, sintetizar o generar |
| Archivos que los consumidores escriben dentro de `.sldb/runtime/` | periférico | No son de sldb; cada consumidor decide dónde los deja |

## 4. Qué hace falta para que algo en Python use kimun

Hoy kimun se usa por CLI (un proceso por llamada), por HTTP (`serve`) y por MCP (`kimun mcp`). Los
consumidores llaman a sldb dentro del proceso, miles de veces. Un adaptador que lance un proceso por
llamada no sirve.

Mi elección: un **SDK de Python delgado** que mantiene abierto un `kimun mcp` (o `serve`) y le
pregunta. No reimplementa nada: traduce preguntas a kimun y respuestas a objetos Python. Lo que es
Python puro del consumidor (sus clases pydantic, sus validadores) se queda en Python y recibe los
datos del SDK.

No probado: cuánto tarda una pregunta por esa vía, ni si las 4 a 8 s de `declare read` sobre el desk
se pagan en cada pregunta. Si se pagan, la caché por hash de git deja de ser opcional.

## 5. Lo que tiene que decir el dueño

Una sola cosa cambia el diseño entero: **las escrituras**. sldb crea y modifica documentos con
transacciones y con autor; deskops vive de eso (`deskops add task`, `edit`, `advance`).

- Si escribir es núcleo, kimun deja de ser solo lectura y hay que diseñar cómo escribe fuentes sin
  exigirles nada.
- Si escribir es periférico, lo hace el SDK de Python (o el consumidor) editando archivos, y kimun
  se entera por git como de cualquier otro cambio.

Mi recomendación: periférico. Es lo que dice `00-nucleo.md` y es lo más barato. Las plantillas de
modelos (extraer y renderizar) quedarían del mismo lado mientras no haya una razón para traerlas.

## 6. Orden propuesto

1. Comparar las aristas que lee kimun con el índice de aristas de sldb sobre el desk de deskops
   (entrada del drawer `task-compare-declare-about-with-the-sldb-edge-index-on-the-desk`). Convierte
   la fila «ya lo hace, sin comparar» en un número.
2. Corregir el frontmatter con línea en blanco y dejar que los archivos de declaración compartan
   funciones (los dos límites que encontró la corrida de lectores).
3. Las preguntas fáciles: resolver referencias, referencias sin resolver, seleccionar y filtrar,
   árbol de etiquetas.
4. El Board de deskops como receta de `declare generate`, comparado byte a byte con el real.
5. El SDK de Python sobre un proceso de kimun abierto, con una sola ruta de lectura de deskops
   cambiada a kimun (candidata: lo que `deskops doctor` lee de `stores check`), medido contra los 10
   a 14 s de hoy.
6. Recién entonces, reglas; y lo que el dueño diga de las escrituras.

El criterio 1 no se da por cumplido con nada de esto por separado. Se da cuando un consumidor real
(deskops) corre apoyado en kimun para lo que esta tabla pone en kimun.
