# Brief K1e — unidades de sección y bloque, marcadas por declaración

Para: Pi (worker). De: Claude (diseño y revisión). Un commit (lo hago yo tras revisar).
**Depende de S2a integrado** (`kimun.surface.markdown.anchors`): base en la Adenda 0.

## Reglas

- Worktree: `/tmp/kimun-k1e-wt` (detached). No toques `/home/jp/proyectos/kimun` ni ningún
  store real. No commitees.
- Todo `bb` por `~/bin/memcap`, con
  `KIMUN_POD=/home/jp/proyectos/kimun/pods/treesitter/target/release/pod-kimun-treesitter`
  exportado. Dirigidas sí; **no** `bb test` completo. Antes de cada `bb`: `pgrep -x bb` vacío.
- Código y docstrings en inglés; documentos en español. **Ocho pruebas**, sin variantes.
  Ninguna prueba puede pasar comparando valores vacíos ni con ramas `(is true …)`.
- **Sin cambios en `src/kimun/kernel/*`.** Si crees que hace falta, para y dime.

## Contrato que este corte implementa (`docs/v2/14 §20–§21`; reglas = inferencias del equipo)

- `§20`: del árbol de un documento, los anclajes más finos (sección, párrafo) **se declaran
  por instrucción**. Nada se marca solo.
- `§21`: la unidad nace **al marcarla**, con identidad propia; el anclaje (localizador +
  contrato de lectura) no es identidad; mientras el anclaje resuelve, un cambio es mutación
  de esa unidad; la ausencia no la termina; **el sensor propone, la continuidad se declara**;
  una unidad no se bifurca ni se funde.
- S2a mostró que el localizador por ordinal **se desplaza al insertar**. Por eso este corte
  tiene una regla central:

> **kimun nunca decide solo que una unidad «se movió», «se partió» o «es esta otra».**
> Calcula un **estado** y, si hay evidencia, **candidatos**. El cambio de anclaje lo declara
> un actor.

## Modelo

Una **marca** (unidad estructural) vive en el descriptor del árbol del documento (store, no
caché; igual que `:units` de K1c), clave `:marks`:

```clojure
:marks {"<ulid>" {:kind :section|:block
                  :locator <localizador de S2a>
                  :hash "<huella de S2a al registrar>"
                  :label "<texto opcional>"}}
```

`:hash` es la **última observación registrada** de lo anclado: evidencia para detectar
mutación y proponer candidatos. No es identidad y **no entra** en `merkle-root`,
`projection-hash`, `content-hash` ni en la entrada de ruta.

### Estado de una marca (derivado, solo lectura, contra las anclas actuales del texto)

Sea `A` el ancla que el localizador resuelve hoy (o nada) y `H` las anclas **del mismo
`:kind`**, distintas de `A`, cuya huella es igual a la `:hash` registrada.

| `A` | huella de `A` | `H` | estado | candidatos |
|---|---|---|---|---|
| resuelve | = registrada | — | `:anchored` | — |
| resuelve | ≠ registrada | vacío | `:mutated` | — |
| resuelve | ≠ registrada | ≥ 1 | `:ambiguous` | `A` (motivo `:locator`) + cada una de `H` (motivo `:hash`) |
| no resuelve | — | vacío | `:missing` | — |
| no resuelve | — | ≥ 1 | `:missing` | cada una de `H` (motivo `:hash`) |

- `:mutated`: el lugar resuelve y lo registrado no está en otra parte ⇒ por defecto es una
  edición en el sitio (regla 5 de `§21`: el sensor no distingue editar de reemplazar; el
  reemplazo se declara).
- `:ambiguous`: el lugar resuelve **a otra cosa** y lo registrado aparece en otro lugar ⇒
  probablemente un desplazamiento de ordinal, pero **no se decide**.
- **1:n**: si `H` tiene dos o más, se listan **todas**; nunca se elige una, ni «la más
  cercana», ni «la primera».
- Un candidato es `{:locator … :reason :locator|:hash :lines […]}`. Es una **propuesta**.

### Qué hace una escritura normal (`docs update`, `stores update`, `docs reanchor`)

- Marca `:anchored`: queda igual.
- Marca `:mutated`: se **registra la nueva huella** (misma unidad, mismo id, mismo
  localizador). Es la regla 2: mientras el anclaje resuelve, el cambio es mutación.
- Marca `:ambiguous` o `:missing`: **no se toca** (conserva localizador y huella viejos). Sigue
  así, con su id, hasta que alguien declare.
- Nunca se quita una marca en una escritura normal.

### Declaraciones (las tres quedan en el journal, `:declared`, como K1b)

- **Marcar**: `docs mark NAME --locator L [--label TEXT] [--reason TEXT]` ⇒ acuña ULID,
  registra localizador y huella actuales. Rechazos sin revisión: el localizador no resuelve
  (`docs/anchor-unresolved`), ya hay una marca con ese mismo localizador (`docs/exists`),
  documento sin fuente legible / no por referencia (`docs/not-anchored`).
- **Re-anclar una unidad**: `docs mark NAME --unit ID --locator L --reason TEXT` (`--reason`
  **obligatorio**) ⇒ misma id, localizador y huella nuevos. Rechazos: id desconocido
  (`docs/missing`), localizador que no resuelve, localizador ya tomado por **otra** marca.
  Vale desde cualquier estado.
- **Cerrar**: `docs unmark NAME ID [--reason TEXT]` ⇒ quita la marca.
  `:declared {:kind "unit-closure" …}` con el estado que tenía.
- `L` se acepta como EDN o JSON (lo que imprime `docs anchors`); normaliza claves y el valor
  de `:type`/`:kind` a keyword. Un `L` mal formado es `cli/usage`.

### Ver

`docs marks NAME` (tipado o genérico, **solo lectura**: ni store ni caché) ⇒
`[{:id :kind :label :locator :status :candidates [...]}]` ordenado por id, más
`:parse-contract` con el que se calculó. `docs show NAME` (tipado) gana la misma lista en
`:marks`.

### Arrastre

- `update-plan`, `generic-update-plan` y `docs reanchor` conservan `:marks` (con la regla de
  escritura de arriba). **Tipados y genéricos.**
- `include build --rebuild` (K1a) arrastra `:marks` como arrastra `:units`;
  `:identities :marks-carried n`.
- Un descriptor sin `:marks` queda byte a byte como hoy.

## Dónde

- `src/kimun/surface/markdown/anchors.cljc`: si falta, una función pura
  `mark-states [anchors marks]` con la tabla de arriba (o en un ns nuevo
  `kimun.surface.marks` si los anillos lo piden). **La lógica de estado y de escritura
  (`advance-marks [anchors marks]` → marcas tras una escritura normal) debe ser pura y
  probarse directamente.**
- `src/kimun/surface/markdown/plan.cljc`: `ast->tree` acepta `:marks` (como `:units`).
- `src/kimun/surface/docs.cljc`: `registry`/`generic-ops` pasan `:marks`; `describe` y
  `describe-generic` lo exponen; planes `mark-plan`, `unmark-plan` (reutiliza
  `update-plan`/`generic-update-plan` con el mismo texto y una opción `:marks`, y `:declared`).
  Ojo con `base-same?`: un cambio solo de `:marks` debe escribir.
- `src/kimun/cli/commands/docs.cljc`: `mark`, `unmark`, `marks`; spec `:locator`, `:unit`,
  `:label`; subcomandos conocidos en `docs_test`.
- `src/kimun/cli/commands/include.cljc`: arrastre.

## Fuera de alcance (escríbelo como límites)

- Mover una unidad a **otro documento**, copiar, dividir y fusionar con linaje declarado.
- Que el sensor (`stores sense|health`) informe estados de marcas: hoy solo `docs marks`.
- Marcas dentro de un bloque (ítems de lista, rangos de texto: eso es `S2b`).
- Documentos `:inline` o de formato < 4; código (espera `M1`).
- Si el contrato de lectura cambió (K6 `contract/changed`), las huellas se calculan con el
  parser en vigor y una marca puede verse `:mutated` sin que el texto cambie: `docs marks`
  lleva `:parse-contract` para que se vea; no se resuelve aquí.
- Un cambio de **título** de una sección marcada la deja `:missing` sin candidatos (su huella
  incluye el encabezado): hay que re-anclarla a mano.

## Pruebas (ocho)

Puras (`test/kimun/surface/marks_test.cljc` o junto a `anchors_test`), con un documento fijo:

1. **Tabla de estados**: un caso por fila de la tabla (cinco), con los candidatos exactos.
2. **1:n no se decide**: el bloque marcado aparece duplicado en dos lugares y su sitio
   original lo ocupa otro ⇒ `:ambiguous` con **tres** candidatos (uno `:locator`, dos
   `:hash`), y `advance-marks` deja la marca **idéntica**.
3. **Escritura normal**: `advance-marks` actualiza la huella solo de las `:mutated`; deja
   iguales `:anchored`, `:ambiguous` y `:missing`; nunca cambia un id ni un localizador ni
   quita una marca.

CLI (`test/kimun/cli/marks_test.cljc`):

4. **Marcar y editar**: marca una sección y un párrafo de un documento **genérico**; edita
   otro bloque + `docs update` ⇒ mismas ids, `:anchored`. Edita el párrafo marcado ⇒
   `:mutated` antes de `docs update`; después `:anchored`, misma id, huella nueva.
5. **Insertar desplaza y no se infiere**: inserta un párrafo antes del marcado ⇒
   `:ambiguous` con dos candidatos; tras `docs update` la marca sigue **igual** (misma
   huella y localizador viejos, sigue `:ambiguous`); `docs mark --unit ID --locator <el del
   candidato :hash> --reason …` ⇒ `:anchored`, **misma id**, y el journal trae `:declared`.
6. **Borrar no termina la unidad; cerrar se declara**: borra el bloque marcado (que era el
   último de su tipo en la sección) ⇒ `:missing`, la id sigue tras `docs update`;
   `docs unmark` la quita y el journal lo registra.
7. **Arrastre**: en un documento **tipado**, las marcas sobreviven a `docs reanchor` y a
   `include build --rebuild` (`:marks-carried`), con las mismas ids.
8. **No es huella y es solo lectura**: (a) `ast->tree` con el mismo AST y `:marks` distintas
   o ninguna ⇒ mismo `merkle-root`, `:root`, `:objects`, `:nodes`; el descriptor sin `:marks`
   es el de antes; (b) marcar no cambia `:projection-hash`/`:content-hash`/`:fingerprint` de
   la entrada de ruta (afirma que la entrada existe y son strings no vacíos); (c) `docs marks`
   no cambia el sha256 de los archivos de `.kimun` ni el head.

Rechazos: añade a la prueba 4 las tres aserciones de rechazo de `docs mark` (no resuelve, ya
marcado, `--unit` sin `--reason`) comprobando que el head no cambia.

Mutación: sin tus cambios de `src/` fallan las ocho.

## Documentos

- `docs/v2/14-juego-del-lenguaje.md §21` (párrafo de implementación de K1): K1e, la tabla de
  estados en una frase, la regla «kimun no decide continuidad», y los límites. Rotula las
  reglas como **inferencias del equipo**.
- `docs/v2/12-ecosistema-y-pendientes.md §1`: fila `K1` (añade K1e; pendientes: campos
  anidados e ítems, cierre y linaje de unidades de campo, mover entre documentos,
  copiar/dividir/fusionar, identidad de modelos como unidades, estados de marcas en el
  sensor) y fila `S2` (`K1e` hecho; `S2b` pendiente). **No** digas que K1 está cerrado.
- `docs/v2/06-superficies-y-cli.md`: `docs mark|unmark|marks`, estados y candidatos.
- `docs/v2/10-escala.md`: el descriptor lleva `:marks` (store, no caché, fuera de las huellas).

## Entrega

`lint.txt`, `directed.txt` (las dos nuevas + `anchors_test`, `field_units_test`,
`reanchor_test`, `identity_carry_test`, `generic_docs_test`, `typed_docs_test`, `docs_test`,
`tour_test`, `doc14_contract_test`), `mutation.txt` en
`/tmp/kimun-k1e-wt/runs/subagents/2026-11-15-K1e/`. Reporta: archivos tocados, números,
mutación (cuántas de las ocho fallan), tests existentes ajustados y por qué, `bb oracle`, y
cualquier punto donde la tabla de estados no se pudo implementar tal cual.

## Adenda 0 — base, bb y lo que S2a entregó

- Base: `63952267` (worktree `/tmp/kimun-k1e-wt` ya creado). Trae S2a (`kimun.surface.markdown.anchors`,
  `docs anchors`), K1c (`:units`), K1d y M1a/M1b.
- **Todo `bb` con flock** (hay más ejecutores en paralelo), no con pgrep:
  `flock /tmp/kimun-bb.lock env KIMUN_POD=/home/jp/proyectos/kimun/pods/treesitter/target/release/pod-kimun-treesitter /home/jp/bin/memcap 3 bb …`
- S2a dejó `anchors` **puro**: recibe `:spans` y `:parse-contract` del llamador (el anillo de
  `kimun.surface.markdown.*` no deja requerir la capa de bloques). Los estados de las marcas
  se calculan con **localizadores y huellas**, que existen siempre; **no dependas de
  `:lines`/`:bytes`** (un documento con cita de continuación perezosa no los tiene). En los
  candidatos, `:lines` va solo si está.
- `mark-states` y `advance-marks` van donde los anillos lo permitan (probablemente junto a
  `anchors.cljc`, que es puro). Dime dónde quedaron.
- Otros ejecutores editan `docs/v2/12` en paralelo: toca **solo** las filas `K1` y `S2`.
- El dueño pidió seguir el spec y preguntar ante ambigüedad: si `14 §20–§21` contradice algo
  de la tabla de estados o de las reglas de escritura de este brief, **no decidas**: sigue con
  lo que no dependa de eso y repórtalo con cita.
