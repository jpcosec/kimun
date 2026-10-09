# SPEC15-R2 — Reporte de la revisión del `15-capa-g.md` tras la ronda 2 del gate

Worktree `kimun-worktrees/spec15`, rama `close/spec15` (sobre `c3551476`). Se corrigieron
todos los hallazgos y ambigüedades de `GATE15-A2` y `GATE15-B2`. El `14` no se tocó.
`bb lint` y `bb test` verdes (`runs/subagents/SPEC15-R2-2026-10-09/`): 891 tests, 9875
asserciónes, 0 fallos, 0 errores; `fixtures_test` 5 tests, 323 aserciones.

## Tabla hallazgo → cambio

### Carril A2 (coherencia)

| # | qué cambió (sección y frase) |
|---|---|
| H1 bloqueante | `15 §1` (ingesta del vocabulario): se quita «y los hechos de propiedad `(property R p) @ W_i`»; se añade «**`contexts define` no crea hechos de propiedad** … las propiedades se afirman como hechos con su propia evidencia (actor o motor)». `§1` (propiedades): «afirmados con su propia evidencia (`:actor` o `:engine`+`:version`) y su `:status`/`:tier` … Fixture: los hechos de propiedad `:fact/f2`, `:fact/f2b` y `:fact/f4`». `§9`: «no crea los hechos de propiedad, que se afirman con su propia evidencia». |
| H2 grave | `§7` (JSON): `"source_hash": "sha256:…"`. `§7` (párrafo): el protocolo usa `sha256:<hex>` (`11 §4.1`, lo que valida el regex de `adapters`) y kimun **convierte la etiqueta en la frontera** a `sha-256:<hex>` (`02 §6.4`, decisión 11); «`sha256:` solo en JSON; `sha-256:` solo en EDN interno y fixtures». `§3` (párrafo de procedencia): ídem. `§10` promesa `g/source-hash-is-single-key` reformulada. `§11` fila `11`: `11 §4.1` «se revisa como aclaración, no cambio». |
| H3 grave | `§6` (bullet de `binding`): «con **`:prov` obligatoria** y `:context`» (fija A-H3). `facts.edn`: `:prov` añadida a `:edge/b1`, `b2`, `b2b`, `b3`, `b4`. `fixtures_test`: nuevo `testing` «TODO `binding` lleva `:prov`». `§10`: fila `g/binding-requires-prov`. `§11` fila `02 §3.2`: `binding` lleva `:prov` obligatoria. |
| H4 menor | `§1` (taxonomías): «el `:root` de una taxonomía de vocabulario es un **nombre** … mientras que el `:root` del descriptor de árbol (`02 §3.1`) es un **id** de nodo; son campos homónimos de objetos distintos». |
| H5 menor | `§2` (transversales): «Fija A-H5: los contextos `:granularity :transversal` tienen su propio vocabulario `L_transversal` que **declara la relación `member`** … de modo que `member` es admisible». |
| H6 menor | `§12`: fila Q7 eliminada de la tabla; nota al pie: «Q7 … **remitida al dueño de `13`** … cómo lo declara `kimun.include.edn` es de `13`, no una pregunta al dueño del producto». |
| H7 menor | `§3`: «**hasta que se responda, `:proposed` en `:status` es el default de `15`** (así va marcado en §3, §7 y los fixtures)». `§7`: «`:status :proposed` es el default pendiente de §12 Q1». `facts.edn`: comentario «(siempre :proposed, decisión 31; default de §12 Q1)». |
| H8 menor | `§9` tabla `facts`: fila nueva `plan [FILE\|-] [--write]`; bullet reescrito («con `--write` lo aplica»). |
| H9 menor | `§5`: «`:candidates` existe **solo** en `:kind :ambiguous` … en `:contradiction` la clave está **ausente**». `conflict.edn`: quitado `:candidates []`. `fixtures_test`: aserción de claves del conflicto sin `:candidates` + chequeo «presente solo si `:ambiguous`». |
| H10 menor | `§13`: «los **hechos de propiedad** `(property R p)` (plural)». `facts.edn`: comentario plural. `fixtures_test`: `testing` pluralizado y ahora exige ≥2 propiedades y `:term/functional`. |

### Carril B2 (ejecutabilidad)

| # | qué cambió |
|---|---|
| 1 bloqueante | `§8` (etapa 2): candidato = «par `(A, B)` que **aparece en un hecho de `R` en ese `W_i`** (asertado o consultado), **nunca** el cruce de los conjuntos bajo los ejes»; matrices «filas = sujetos observados de `R`, columnas = objetos observados, no sobre la taxonomía»; cota = «el **número de hechos de `R` en el `W_i`** (lineal en hechos, no en términos)»; «un `W_i :granularity :corpus` sigue esa misma regla». Eliminada la cota `Σ_r \|A_r\|·\|B_r\|`. `§10` promesa reformulada (`g/candidate-pairs-are-bounded-per-relation`). |
| 2 grave | Igual que H2 (arriba). |
| 3 grave | `§11` fila «kernel (código)»: añadido `kimun.kernel.plan` (chequeo nuevo `:plan/rejected`: un `:add-node :fact/:triple` sin su `:projection` en el mismo plan). |
| 4 grave | `§4`: `:action` incluye `:assert`; bullet nuevo «**`facts assert` siempre materializa un documento de decisión `:action :assert`** (fija B-4) … reconstruible desde archivos + decisiones». `§9` bullet de `assert`. `§10` fila `g/assert-materializes-a-decision-document`. |
| 5 grave | `§6`: bullet «**Hojas referenciadas por hechos**» — las hojas `:sign/:text` extremo de una cadena de anclaje «se persisten **con su texto** (inline obligatorio) … `anchor/resolves?` sigue siendo **puro, sin I/O**»; cita `14 §10` y `10 §10`. `§11` fila «kernel (código)»: `anchor/resolves?` «sigue **sin I/O**». `§10` fila `g/leaves-referenced-by-facts-are-persisted`. |
| 6 menor | `§10` filas nuevas: re-anclaje al aceptar propuesta (`g/accepting-a-proposal-reanchors-and-supersedes`), contexto por defecto (`g/source-document-opens-its-own-context`), `:opaque` sin verdad (`g/opaque-signs-have-no-truth`), una posición por nodo en taxonomía (`g/taxonomy-admits-one-position-per-node`). |
| 7 menor | `§7`: «`validate` **ya rechaza un perfil desconocido** (`:adapters/profile`) … el despacho por perfil falta en `plan-documents`, que hoy ramifica `(if (= profile "cli") (plan-cli …) (plan-symbols …))`: sin la rama nueva, `propositions` cae en `symbols` (fija B-7)». |
| 8 menor | `§9`: tabla `facts` con `plan [FILE\|-] [--write]`; bullet de `facts plan`/`assert`. |

### Ambigüedades

| ambigüedad | lectura fijada |
|---|---|
| Am1 | `§1`: un literal es **siempre** término de `literals`, «también dentro de `:form`»; `§1` (proposición): «sus hojas son **ids de término** (un literal es un término de `literals`)»; `§10` fila nueva `g/proposition-form-leaves-are-terms`. |
| Am2/B-C | `§2`: «la relación de ruteo es un **valor** de `:relation` (`:equivalent\|:projection`) dentro de la evidencia de una arista `:semantic` … `:ref-hash` sigue siendo el `:to` de la arista, y `:context-target` identifica el `W_j` destino». |
| Am3 | `§2` (`:of`): «Excepción (fija Am3): la forma de transversal `{:transversal "data-class"}` nombra el transversal …, no una ruta; es la única forma de `:of` que no es ruta/span». |
| Am4 | `§2` (ruteo): «la promoción del vocabulario promueve **hechos** …; “arista promovida” significa que la arista `:semantic` lleva el registro de la confirmación por decisión (decisión 35) en su evidencia, no que la arista tenga un tier propio que promover». |
| Am5 | `§3` (reglas duras): «la **detección de contradicción no es deducción**: el evaluador ve todos los hechos del `W_i`, incluidos los `:tier :statistical`; lo que `statistical` no hace es alimentar la deducción (`I`, cierre) … dos hechos `:proposed`/`:statistical` ya presentes en un `W_i` producen informe sin promoción». `§10` fila `g/contradiction-sees-statistical-facts`. |
| B-A | `§8` (datoms): `{:e <id> :a <id> :v <id\|literal> …}` — «Fija B-A: el `:a` … es el **id del término predicado `R`** (no una keyword de atributo): `:e` es el id del sujeto `A`, `:a` el de `R`, `:v` el del objeto `B`». |

## Test de fixtures

`test/kimun/g/fixtures_test.cljc` (ring 4): los cinco EDN parsean y se comprueban campo a
campo; ahora además **todo `binding` lleva `:prov`**, el conflicto no lleva `:candidates`
(solo `:ambiguous`) y hay ≥2 hechos de propiedad. Pasa: 5 tests, 323 aserciones (antes 297).

## Preguntas finales al dueño

Q1, Q2, Q4, Q5, Q6 y Q8 (con sus defaults). **Q3 eliminada** (la decide `14 §12` decisión 27).
**Q7 remitida al dueño de `13`** (`kimun.include.edn`), fuera de la tabla.

## Lo que NO corregí y por qué

- **`14`**: no se tocó ninguna fila (orden explícita del brief).
- **`06`/`02`/`10`/`11`/`12`**: no se modifican; `15 §11` declara las revisiones/adiciones
  («Revisa»), como en R1. El brief pide `11 §4.1` **como aclaración, no cambio**.
- **Código del kernel** (`node/shapes`, `edge/statuses`, `plan`, puerto `Backend`,
  `anchor/resolves?`, despacho por perfil): cambios de implementación que la spec registra en
  `§11`; no entran en una tarea de spec.
- **`tests/promises.md`**: las filas nuevas de `§10` quedan `deferred` hasta la etapa A/C; no
  hay tests `g/*` todavía. El único test escrito es el de fixtures.
- **Ficheros de la ronda 1** (`GATE15-A`/`B`): no se revirtieron cambios previos; solo se
  corrigió lo que la ronda 2 señaló.
