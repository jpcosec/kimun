# kimun (SLDB v2) — documentos de dirección (2026-10-08)

Índice y tabla de autoridad de la documentación de dirección. `01`–`04` fijan el contrato
técnico; `06`–`08`, `10`, `11` y `13` describen el producto; `05` es el estado; `12`, los
pendientes. La historia está en `docs/archive/`.

## Empieza aquí

`00-nucleo.md` — el núcleo de kimun en una página (proposiciones numeradas al modo del *Tractatus*):
lo que, si cambia, hace que kimun deje de ser kimun. Resume y ordena lo que el dueño fijó; no agrega
decisiones ni corrige a los documentos de abajo, y nombra como abierta D-G (D0 dejó de serlo el 2026-11-15: contrato S–M derivado por el concilio, `14 §21`; pendiente su implementación, `K1`).

## Autoridad

De mayor a menor. Ante contradicción gana el número menor.

1. `01-orden-filosofico.md`
2. `14-juego-del-lenguaje.md` (borrador estable 2026-10-08; revisa `01 §4` (principio 1), `07`, `11 §1` y
   `12 §3`: mientras esas revisiones no se integren en sus documentos, gana)
3. `02-sustrato-computacional.md`
4. `03-estandares-de-codigo.md`
5. `04-superficie-markdown.md`
6. `06-superficies-y-cli.md`, `07-modelos-como-nodos.md`, `08-distribucion.md`,
   `10-escala.md`, `11-adaptadores.md`, `13-include.md` (cada uno dueño de su tema)
7. `05-estado.md` (estado y decisiones vigentes)
8. `tests/promises.md` y `tests/parity-v1.md` (evidencia)
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
| `05-estado.md` | estado actual fechado y la tabla de decisiones 1–46 |
| `06-superficies-y-cli.md` | inventario real de la CLI, envelope, códigos de salida y rutas de `serve` |
| `07-modelos-como-nodos.md` | modelos como descriptores EDN en el árbol `models`; frontmatter y segmentos |
| `08-distribucion.md` | topología de repos, toolchain, `bb jar`/`bb release`, layout e instalación |
| `10-escala.md` | formatos 2 a 4 del store, coste de abrir/leer/escribir, `stores upgrade` |
| `11-adaptadores.md` | protocolo 1 de adaptadores (código → documentos) y el grupo `adapters` |
| `12-ecosistema-y-pendientes.md` | pendientes de kimun, consumidores de v1 y la capa G (`wittgenstein`) |
| `13-include.md` | `kimun.include.edn`: qué versiona un repo en git y el grupo `include` |
| `14-juego-del-lenguaje.md` | **reordenado por tema (`DOC1` / `F18`)**: separa contrato objetivo, estado implementado, decisiones abiertas (`D-G`; `D0` es desde el 2026-11-15 contrato S–M derivado) e historia; integra `P1–P15`, tres capas (`§19`), clases/anclaje vs. generación (`§18`), trackeable vs. trackeado (`§20`), identidad vs. huella Merkle (`§21`, contrato `D0` derivado por el concilio; implementación `K1`) y decisiones `21–46` |
| `16-modelos-como-subgrafos.md` | **spec M1 (Etapa A, diseño objetivo)**: modelos de anclaje vs. generación, selectores EDN sobre substrato tree-sitter (`R3`), herencia de clases e inmutabilidad de contrato (`14 §18`, pendiente de `P1` y `K1` para implementar) |
| `tests/promises.md` | promesa normativa de `02`/`04` → test que la prueba |
| `tests/parity-v1.md` | marcador de paridad con sldb v1, DAG y desviaciones deliberadas |
| `drafts/` | **no autoritativo**: registro de la sesión 2026-10-08/09, **preguntas abiertas al dueño**, análisis previo del 14, dudas de la capa G, IDs a partir de Matrix |
| `runs/research/2026-10-08/` | evidencia de la sesión: spikes R1–R3, dossiers del store, reportes de las tandas A y S |
| `09-evaluador-anclado.md` | archivado en `docs/archive/v2/09-evaluador-anclado.md` (no implementado) |

Diagramas: fuente YAML en `docs/architecture/spec2viz/v2-*.yml`, render en
`docs/architecture/spec2viz/rendered/v2/*.mmd`; se editan en YAML y se vuelven a renderizar
(`spec2viz diagram render … --renderer mermaid`).

## Orden de lectura

Newcomer:

1. `01` (por qué) → `02` (qué) → `04` (la superficie Markdown).
2. `05` (estado y decisiones).
3. `06` (la CLI), `07` (modelos) y `10` (el store en disco).
4. `13` si vas a versionar un repo con kimun; `11` si vas a escribir un adaptador.
5. `tests/promises.md` y `tests/parity-v1.md` para la evidencia.
6. `12` para lo que falta; `docs/archive/` para la historia.

Agente que retoma: `AGENTS.md` (orden operativo) → `05` (estado) → `12` (pendientes) → el
documento dueño del tema de la tarea.

Lo retirado del diseño anterior está en `01 §7`.
