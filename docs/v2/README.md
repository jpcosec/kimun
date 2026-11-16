# kimun (SLDB v2) — documentos de dirección (al día 2026-11-16)

Índice y tabla de autoridad de la documentación de dirección. `01`–`04` fijan el contrato
técnico; `06`–`08`, `10` y `13` describen el producto; `05` es el estado; `12`, los
pendientes. La historia está en `docs/archive/`.

**Dirección vigente (borradores):** el 2026-11-15 el dueño cambió el marco (kimun ve, sintetiza y genera; la
realidad vive en las fuentes; git para el nivel de archivo) y el 2026-11-16 aprobó un plan. Eso está en
`drafts/sesion-2026-11-15.md` (sus palabras, textuales), `drafts/diseno-declaraciones.md` (el diseño que
sale de ellas) y `drafts/plan-producto.md` (el plan por etapas). Son borradores (van en la
tabla de autoridad como punto 0), y son lo vigente para la dirección: donde un documento numerado describe lo contrario, vale el borrador (ver «Mecanismos diferidos o reemplazados»).

## Empieza aquí

`00-nucleo.md` — el núcleo de kimun en una página (proposiciones numeradas al modo del *Tractatus*):
lo que, si cambia, hace que kimun deje de ser kimun. Resume y ordena lo que el dueño fijó; no agrega
decisiones ni corrige a los documentos de abajo, y nombra como abierta D-G (D0 dejó de serlo el 2026-11-15: contrato S–M derivado por el concilio, `14 §21`; pendiente su implementación, `K1`).

## Autoridad

De mayor a menor. Ante contradicción gana el número menor.

0. (borradores, pero lo vigente para la dirección) `drafts/sesion-2026-11-15.md`, `drafts/diseno-declaraciones.md`, `drafts/plan-producto.md`: palabras del dueño, diseño y plan; ver arriba.
1. `01-orden-filosofico.md`
2. `14-juego-del-lenguaje.md` (borrador estable 2026-10-08; revisa `01 §4` (principio 1), `07` y
   `12 §3`: mientras esas revisiones no se integren en sus documentos, gana)
3. `02-sustrato-computacional.md`
4. `03-estandares-de-codigo.md`
5. `04-superficie-markdown.md`
6. `06-superficies-y-cli.md`, `07-modelos-como-nodos.md`, `08-distribucion.md`,
   `10-escala.md`, `13-include.md` (cada uno dueño de su tema)
7. `05-estado.md` (estado y decisiones vigentes)
8. `tests/promises.md` (evidencia)
9. `desk/atoms/` (`epoch:v2` prevalece sobre los atoms anteriores)
10. `12-ecosistema-y-pendientes.md`
11. `docs/archive/` — material histórico, no autoritativo; nunca corrige a `docs/v2/`

## Documentos

| doc | qué posee |
|---|---|
| `00-nucleo.md` | el núcleo de kimun en una página (Claude y Astra, 2026-10-09; borradores en `drafts/nucleo-*.md`) |
| `01-orden-filosofico.md` | qué es el objeto persistido, las capas S/M/G, las garantías y lo retirado del diseño anterior |
| `02-sustrato-computacional.md` | estructuras, invariantes, runtime, roadmap y el contrato de revisión |
| `03-estandares-de-codigo.md` | anillos de dependencia (`bb lint`), compartimentación, docstrings y tests |
| `04-superficie-markdown.md` | perfil SLDB-MD: CST, AST neutro, render canónico, mapeo al pool, opacos |
| `05-estado.md` | estado actual fechado y la tabla de decisiones 1–47 y las del dueño del 2026-11-15 |
| `06-superficies-y-cli.md` | inventario real de la CLI, envelope, códigos de salida y rutas de `serve` |
| `07-modelos-como-nodos.md` | modelos como descriptores EDN en el árbol `models`; frontmatter y segmentos |
| `08-distribucion.md` | topología de repos, toolchain, `bb jar`/`bb release`, layout e instalación |
| `10-escala.md` | formato del store (el vigente es el 4; los anteriores ya no se leen), coste de abrir/leer/escribir |
| `12-ecosistema-y-pendientes.md` | pendientes de kimun, consumidores de v1 y la capa G (`wittgenstein`) |
| `13-include.md` | `kimun.include.edn`: qué versiona un repo en git y el grupo `include` |
| `14-juego-del-lenguaje.md` | **reordenado por tema (`DOC1` / `F18`)**: separa contrato objetivo, estado implementado, decisiones abiertas (`D-G`; `D0` es desde el 2026-11-15 contrato S–M derivado) e historia; integra `P1–P15`, tres capas (`§19`), clases/anclaje vs. generación (`§18`), trackeable vs. trackeado (`§20`), identidad vs. huella Merkle (`§21`, contrato `D0` derivado por el concilio; implementación `K1`) y decisiones `21–46` |
| `16-modelos-como-subgrafos.md` | **spec M1 (Etapa A, diseño objetivo)**: modelos de anclaje vs. generación, selectores EDN sobre substrato tree-sitter (`R3`), herencia de clases e inmutabilidad de contrato (`14 §18`, pendiente de `P1` y `K1` para implementar) |
| `tests/promises.md` | promesa normativa de `02`/`04` → test que la prueba |
| `drafts/sesion-2026-11-15.md`, `drafts/diseno-declaraciones.md`, `drafts/plan-producto.md` | **borradores, pero lo vigente para la dirección** (2026-11-15/16): las palabras del dueño (textuales), el diseño de las declaraciones y el plan aprobado por etapas |
| `drafts/` (resto) | **no autoritativo**: registro de la sesión 2026-10-08/09, **preguntas abiertas al dueño**, análisis previo del 14, dudas de la capa G, IDs a partir de Matrix |
| `runs/research/2026-10-08/` | evidencia de la sesión: spikes R1–R3, dossiers del store, reportes de las tandas A y S |
| `09-evaluador-anclado.md` | archivado en `docs/archive/v2/09-evaluador-anclado.md` (no implementado) |

Diagramas: fuente YAML en `docs/architecture/spec2viz/v2-*.yml`, render en
`docs/architecture/spec2viz/rendered/v2/*.mmd`; se editan en YAML y se vuelven a renderizar
(`spec2viz diagram render … --renderer mermaid`).

## Orden de lectura

Newcomer:

0. Los tres borradores vigentes de arriba (`drafts/sesion-2026-11-15.md`, `drafts/diseno-declaraciones.md`,
   `drafts/plan-producto.md`).
1. `01` (por qué) → `02` (qué) → `04` (la superficie Markdown).
2. `05` (estado y decisiones).
3. `06` (la CLI), `07` (modelos) y `10` (el store en disco).
4. `13` si vas a versionar un repo con kimun.
5. `tests/promises.md` para la evidencia.
6. `12` para lo que falta; `docs/archive/` para la historia.

Agente que retoma: `AGENTS.md` (orden operativo) → `00` → los tres borradores vigentes → `05` (estado) →
`12` (pendientes, leídos a través del plan) → el documento dueño del tema de la tarea.

## Mecanismos diferidos o reemplazados

Una frase por documento; los textos de abajo siguen en sus documentos y no se han reescrito.

- `02 §5` (revisiones encadenadas, historia de archivos en el store) y `10` (árbol Merkle por ruta, packs,
  recolección): describen mecanismos que **rehacen git**; el dueño los difirió explícitamente el 2026-11-15
  (`drafts/sesion-2026-11-15.md §11`); kimun usa git para el nivel de archivo.
- `14 §20` en lo del nivel de archivo (Merkle por ruta de lo trackeado): mismo caso; vale en lo de
  «solo lo anclado importa a nivel de campo».
- `16` en lo de leer como fuentes los Markdown del desk con modelos regenerados (`M2`): reemplazado; lo que
  generaba SLDB pasa a ser materialización desde modelos (`drafts/sesion-2026-11-15.md §4`). Su modo de
  compatibilidad con v1 (`:legacy-v1-template`, `§3.5`) se está borrando (en curso).
- `07` (modelos como nodos con la plantilla de v1): el modelo como receta de materialización desde el grafo
  está en `drafts/plan-producto.md`, etapa 5, y en `16`.
- La paridad con v1 está suspendida (`drafts/sesion-2026-11-15.md §8`); sus pruebas y su marcador se borraron el 2026-11-16.
- No existe un documento `11`: el diseño de adaptadores se retiró y el subsistema se borró el 2026-11-15.

Lo retirado del diseño anterior está en `01 §7`.
