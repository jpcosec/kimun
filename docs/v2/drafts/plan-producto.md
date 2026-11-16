> Plan aprobado por el dueño el 2026-11-16 (copia sin cambios del plan; el texto sigue).

# Plan: de kimun hoy a un producto terminado

## Contexto

El 2026-11-15 el dueño reencuadró el proyecto (`docs/v2/drafts/sesion-2026-11-15.md`,
`docs/v2/drafts/diseno-declaraciones.md`, `docs/v2/00-nucleo.md`):

- El conocimiento son proposiciones `(R A B)`; declarar es escribir Clojure **que corre**.
- Kimun «solo ve, sintetiza y genera»; la realidad vive afuera, en las fuentes.
- Dos niveles: hasta el archivo lo hace **git** (rehacer git, diferido); dentro del archivo
  solo importa lo **anclado**.
- Contexto, clase y tipo son etiquetas; las clases comprimen (una clase + una lista).
- Nodos estructurales (anclas) y nodos puros (fichas); lo importante son las relaciones,
  que también son nodos.
- El conocimiento se materializa afuera (CLI/MCP/API/UI, documentos exportados con modelos).
- Dos cachés: comprimido (masivo, derivado) y explícito (lo que importa, legible).
- Borrar la grasa sin pedir permiso; paridad v1 suspendida.

El código de hoy (21 223 líneas, 1 176 pruebas) se construyó con otro marco. Tres
exploraciones del 2026-11-16 dan el punto de partida:

- **Rehace git:** árbol de rutas, packs, inventario de sueltos, journal, `docs status`
  (≈1 600 líneas enteras; ≈22 % de las pruebas). Revisiones y store no se pueden sacar de
  un tirón: 12 namespaces cada uno, y guardan a la vez historia de archivos y grafo.
  `surface/docs.cljc` (1 317 líneas) mezcla los dos niveles en las mismas funciones.
- **Sirve tal cual:** estados de marcas y anclas (`surface/markdown/anchors.cljc`:
  `mark-states`, `advance-marks`, `field-anchors`), `kernel/canon`, `offsets`, el DAG de
  etiquetas con jerarquía por puntos (`surface/semantic.cljc`), `hierarchy`/`derive`
  (`surface/models/subgraph.cljc`), el paquete `graph.*`, el render de plantillas
  (`surface/models/render.cljc`), el envelope/CLI/HTTP, `cache/` e `index/` derivados, el pod
  con su `query` genérico, y Babashka trae `sci`, `rewrite-clj` y `tools.reader`.
- **No existe:** leer o evaluar Clojure; ninguna proposición `(R A B)` viva (los nodos
  `:proposition`/`:triple` del kernel no los crea nadie); relaciones como nodos; varias
  clases por nodo; nodos de código; sensor por git; caché explícita; generar desde el grafo;
  MCP; ninguna release (113 commits sin subir, pod fuera del release, solo linux probado,
  `serve` inseguro sin token).

## Qué es «terminado»

Un kimun instalable que, sobre cualquier repositorio git y sin exigirle nada:

1. **ve**: le pregunta a git qué cambió y vigila por dentro solo lo anclado;
2. **sintetiza**: un grafo de proposiciones, derivado de declaraciones escritas en Clojure;
3. **genera**: materializa ese grafo (documentos con modelos, CLI, HTTP, MCP);

y que se usa sobre sí mismo. Fuera de este plan, diferido explícitamente: rehacer git, la
capa G (espera `D-G`), paridad v1, y el reemplazo de sldb con su wrapper (`RS1`/`RS2`/`C2`).

## Etapas

Cada etapa termina en algo que se puede correr y mostrar. Dentro de cada una, cortes de un
commit; los que no comparten archivos van en paralelo.

### 0. Cerrar lo abierto y terminar de borrar

- Retomar `sensor-git` (`/tmp/kimun-gitsense-wt`: `stores changed --since`, falta validar y medir).
- Borrar: paridad v1 (`test/kimun/parity/`, `*_parity_test`, `scripts/parity/`, 139 pruebas),
  el modo `:legacy-v1-template` y `:shared-path :allow`, la reconciliación de anclas sin
  llamadores, el resto del código muerto fuera del kernel.
- Podar `preguntas-abiertas.md` (10 filas sin objeto) y poner al día README, `AGENTS.md`,
  FAQ y `05-estado.md` con la dirección del 2026-11-15.

### 1. Ver con git

- Persistir el puntero «miré hasta este commit» (un dato, junto a las declaraciones).
- Pasar `sense`, `health` y el latido al camino de git; medir contra los ≈38 s / 2 000 docs.
- Entonces borrar lo que rehace git a nivel de archivo: huella por archivo y `docs status`,
  árbol de rutas, registro de documentos genéricos, packs, inventario de sueltos, y el eje
  de archivo de `surface/diagnostics.cljc`. `surface/docs.cljc` se parte al borrar.

### 2. Declarar es escribir Clojure

- Evaluar archivos de declaración con `sci` (corre, aislado, viene en Babashka): una
  relación es algo definido; evaluarla produce una proposición. Un solo tipo de dato.
- Primer vocabulario, el mínimo: `anchors` e `isa`. Las marcas de hoy salen de ahí con los
  mismos estados y candidatos (`mark-states` se reutiliza).
- Los comandos que declaran **generan formas** (`rewrite-clj`) en vez de escribir en el store.
- El índice pasa a ser derivado: caché **explícita** = proposiciones expandidas y legibles;
  caché **comprimida** = el `cache/` de hoy.
- Entonces borrar: journal, planes, log y replay como registro propio; `identity-registry`
  de `include`; lo que quede del núcleo transaccional que solo servía a eso.

### 3. Etiquetas y clases que comprimen

- Etiquetas sobre cualquier nodo, varias por ejes, jerarquía por puntos (soltar el DAG de
  `semantic.cljc` de «solo documentos»).
- `(isa (files "…") Clase)`: una declaración, muchos archivos (generalizar la regla
  glob→modelo de `surface/include.cljc`).
- Nodos de código: gramática de Clojure en el pod y un consumidor de `query`.
- **Kimun se indexa a sí mismo**: el grafo de su propio código sale de kimun y reemplaza a
  `scripts/check_rings.clj` y a los scripts sueltos del análisis.

### 4. Relaciones y fichas

- Una proposición puede ser término de otra (relaciones como nodos).
- Fichas: términos con contenido y sin ancla. Relaciones entre lo anclado.
- `graph.*` consulta el grafo de proposiciones (sin ids `sldb://`).

### 5. Generar

- Un modelo es una receta de materialización desde el grafo (reutiliza
  `surface/models/render.cljc`); el docstring tipado sigue el modelo de su clase.
- Los documentos que generaba SLDB (átomos, tareas…) salen como materializaciones.
- Salidas: CLI, HTTP y MCP sobre el mismo envelope.

### 6. Producto

- Release: pod compilado y fijado en CI; linux-amd64 primero, las otras plataformas después.
- `serve`: rutas confinadas al proyecto y token obligatorio fuera de loopback.
- Instalación documentada, README verdadero, primera versión etiquetada.

## Transversal

- **Fronteras desde el grafo.** Al cerrar cada etapa se repite el análisis del grafo del
  propio código (`runs/research/2026-11-15/grafo-propio/`); lo que se lea mal se corrige en
  la etapa siguiente. `cli/commands/*` y `surface/docs.cljc` se ordenan al borrar, no antes.
- **Borrar al reemplazar.** Nada se borra hasta que su reemplazo corre; entonces se borra
  entero, con sus pruebas.
- **Método.** Ejecutores en paralelo (Sonnet); yo coordino, reviso, valido e integro. Un
  solo `bb` a la vez (`flock /tmp/kimun-bb.lock`), suite completa con pod por tanda,
  evidencia en `runs/subagents/`, un corte un commit. Los diseños los escribo yo, desde
  `00-nucleo` y las palabras del dueño; no los delego.
- **Decisiones.** Decido lo técnico y lo dejo escrito. Al dueño solo va lo que cambia qué es
  kimun. No subo nada al remoto ni toco stores reales sin que lo pida.

## Verificación

- Por corte: `bb lint`, pruebas dirigidas que fallan sin el cambio, `bb oracle`.
- Por tanda: `bb test` completo con `KIMUN_POD`, `bin/kimun include check`.
- Por etapa, de punta a punta sobre un repositorio git temporal:
  1. editar un archivo y commitear → `stores changed` lo ve sin releer el resto;
  2. escribir una declaración → aparece en el grafo; borrar `.kimun/` y reconstruir da lo mismo;
  3. una clase + un patrón cubre N archivos; kimun responde ciclos y capas de su propio código;
  4. una ficha relacionada con una región anclada se consulta por la relación;
  5. un documento sale generado desde el grafo y se regenera igual;
  6. instalar el bundle en una máquina limpia y repetir 1–5.
- `scripts/tour_mutation.sh` se reescribe en cada etapa como demostración viva.

## Avance al 2026-11-16

El plan de arriba no cambió. Esta sección dice qué entró a `main` el 2026-11-16 y qué falta, con los límites que cada informe declara (`runs/subagents/2026-11-16-*/REPORT.md` y `runs/subagents/2026-11-15-sensor-git/REPORT.md`). Última corrida completa con el pod: 1 053 pruebas, 10 064 aserciones, 129 namespaces, 0 fallos (`8e5780a6`). Los diseños de `declare` (`diseno-declaraciones.md`, `diseno-evaluar.md`, `diseno-generar.md`) son borradores del coordinador, no decisiones del dueño.

| etapa | qué entró (commit) | qué falta | límites declarados que importan |
|---|---|---|---|
| 0. Cerrar y borrar | sensor `stores changed --since` (`ba54db45`); paridad v1 y modos de compatibilidad borrados (`5ed1deed`); código muerto, copias y dos ciclos ocultos borrados (`967288ca`); documentos de entrada al día (`f7fd1d7a`, y el corte `docs-al-dia-2`) | las dependencias que suben de `surface.http` y `surface.index.cache` hacia `cli.*` (informe `sin-muerto`: exigen decidir la frontera); `routes/shared-path` sigue en `stores.cljc` | se fueron con la carpeta de paridad también promesas propias del almacén (`durability`, `merkle_promises`, `shards_promises`, `cache_promises`, `closed_gaps`, `optional_field_hash`) y la comparación del CST nativo contra el pod sobre corpus (`sin-paridad`); el selector tiene un solo modo y los contratos v2 ya registrados cambian de digest |
| 1. Ver con git | puntero en `<store>/seen.edn` (`6386af3c`); `sense`, `health` y latido abren solo lo que git dice que cambió (`3c905b18`); borrados packs, inventario de sueltos, journal y `docs status` (`e1fccc5b`) | árbol de rutas, huella por archivo, revisiones, log, planes y `mmap` siguen (informe `sin-rehacer-git-1`, «Qué NO se borró») | ahorro medido de 41 % (N=500) y 50 % (N=2000) pero el camino por git sigue siendo O(N) (`store/verify` y el contrato por documento); **no ve** `:noise`, `:rebuild-mismatch` ni `:unextractable` en archivos sin cambios (el veredicto puede diferir de `--full`); un archivo ignorado por git no se ve cambiar; **`--reason` y `:origin` ya no se registran en ningún sitio** (`kimun/meta` dejó de escribirse); un puntero que avanza solo a mano puede quedar atrás sin aviso en `changed` (`:behind` lo muestra en `seen`) |
| 2. Declarar es escribir Clojure | evaluación con `sci` de archivos bajo `kimun/` a proposiciones (`dbdb6f87`); anclas de declaraciones resueltas contra los archivos con git (`71dec71f`); varios contextos que se requieren y `declare add` (`d16265ef`); arreglos tras usarlo (`0849b296`). Las relaciones las define cada proyecto con `relation`; no hay vocabulario predefinido | caché explícita y comprimida (no entró); borrar planes, log y replay como registro propio y el `identity-registry` de `include`; `declare add` añade el texto de la forma, no usa `rewrite-clj` | **un archivo que no coopera sigue consumiendo un núcleo tras un timeout**: el hilo se abandona, no se detiene (en `serve` o MCP cada timeout deja uno); `declare anchors` ve solo anclas directas en `:a`/`:b`, solo Markdown con localizador interno, y no ve archivos sin cambios que git no reporta; sin `--since` ni puntero no hay comparación ni candidatos; el puntero se busca en la raíz del proyecto (`.kimun/seen.edn`); JSON y EDN comparten la forma etiquetada de los nodos; sin tope de memoria al evaluar |
| 3. Etiquetas y clases | `declare terms`, `declare about TERM\|--path`, `declare members 'PATRÓN'`; `(files "glob")` como nodo, expandido al preguntar y nunca guardado (`858e3f46`) | etiquetas sobre cualquier nodo con varios ejes; nodos de código (la gramática de Clojure no está en el pod, que trae Markdown y Python); que kimun se indexe a sí mismo y reemplace a `scripts/check_rings.clj` | el glob solo admite `*`, `**` y `?`; los términos se comparan como cadena exacta; `declare about --path` y `members` usan la raíz del proyecto; `--path` es opción compartida con `docs create` |
| 4. Relaciones y fichas | una proposición puede ser término de otra (`{:proposition id}`, dentro de `dbdb6f87`); `declare about` sigue un nivel de proposición sobre proposición | fichas (términos con contenido y sin ancla); que `graph.*` consulte el grafo de proposiciones | `about` no puede preguntar por una proposición como término |
| 5. Generar | diseño borrador `diseno-generar.md` (`9b7243f8`); `kimun mcp` con nueve herramientas sobre los mismos envelopes (`e7433d94`) | **`declare generate` está en curso (otro ejecutor), no en `main`**; los documentos que generaba SLDB como materializaciones; HTTP sobre proposiciones | MCP no se probó contra un cliente real y sus supuestos del protocolo no se contrastaron con la especificación (sin red); atiende en serie, sin cancelación; un valor que empiece por `-` se rechaza |
| 6. Producto | `serve` seguro por defecto: rutas confinadas, token fuera de loopback (`f9847416`); el bundle de release lleva el pod de su plataforma o el build falla, y `--without-pod` lo declara en `MANIFEST.edn` (`39948de4`) | publicar el pod de CI y fijar su hash (`release.edn` sigue con `:sha256 nil`); ninguna etiqueta de release; instalación documentada de punta a punta | **el CI de release no se ha ejecutado** (YAML solo parseado); solo linux-amd64 se construyó y probó (macOS, aarch64 y el `.cmd` de Windows no); el pod estático que construiría CI no se construyó aquí; sin tope de tamaño de cuerpo ni de costo del parser en `/docs/ingest` (abierto, `preguntas-abiertas.md` `SERVE`); TOCTOU de enlaces simbólicos fuera del modelo |

Sin evidencia en los informes de este día, no se afirma: medición del tiempo de `declare *` sobre proyectos grandes, ni de `sense` con muchos archivos cambiados.
