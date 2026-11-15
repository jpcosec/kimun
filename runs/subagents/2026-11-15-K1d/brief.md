# Brief K1d — un rebuild conserva el árbol de modelos y la versión de los modelos

Para: Pi (worker). De: Claude (diseño y revisión). Un commit (lo hago yo tras revisar).

## Reglas

- Worktree: `/tmp/kimun-k1d-wt` (detached; base en la Adenda 0). No toques
  `/home/jp/proyectos/kimun` ni ningún store real. No commitees.
- Todo `bb` por `~/bin/memcap`, con
  `KIMUN_POD=/home/jp/proyectos/kimun/pods/treesitter/target/release/pod-kimun-treesitter`
  exportado. Dirigidas sí; **no** `bb test` completo. Antes de cada `bb`: `pgrep -x bb` vacío.
- Código y docstrings en inglés; documentos en español. **Cuatro pruebas**, sin variantes.
- **Sin cambios en `src/kimun/kernel/*`.** Ninguna prueba con rama `(is true …)`: si un caso
  no se puede probar, bórralo y repórtalo como límite.

## Diseño (cerrado por el equipo; inferencia, no palabras del dueño)

Un modelo es una **clase**: un valor (su descriptor) direccionado por contenido, con nombre y
versión. No es una unidad detectada, así que **no se acuña un id nuevo para modelos**: su id
sigue siendo el hash del descriptor (`mstore/node-id`), que incluye `:model/version`.

**El defecto.** `include build --rebuild` construye el store de cero: el árbol `models` recibe
un ULID nuevo y **toda versión vuelve a 1**. Un modelo que iba en la versión 3 y no cambió
sale con otro id (el descriptor serializado lleva `:model/version 1`), y los documentos
quedan ligados a un contrato con otro id sin que nada haya cambiado. Además la versión
retrocede.

**La regla.** En un rebuild, por cada modelo declarado, mirando el store viejo (leído **antes**
de moverlo, junto al registro de identidades de K1a):

- mismo nombre y **mismo contrato** (`same-contract?`: igual salvo `:model/version`) ⇒ conserva
  **la versión vieja** ⇒ mismo id de nodo;
- mismo nombre y contrato **distinto** ⇒ versión vieja **+ 1** (la versión nunca retrocede);
- nombre nuevo ⇒ versión 1, como hoy;
- el árbol `models` conserva su **id de árbol** viejo si existía.
- store viejo ilegible ⇒ todo como hoy (versión 1, árbol nuevo), y se dice.

## Qué cambia

1. `src/kimun/surface/models/store.cljc`, `define-plan`: `opts` gana `:version` (entero
   opcional). Solo se usa cuando el nombre **no existe** en el store (`current` nil): la
   versión es `(or version 1)`. Con `current` no cambia nada. Sin `:version` no cambia nada
   (los llamadores existentes quedan igual). Docstring.
2. `src/kimun/cli/commands/include.cljc`:
   - `identity-registry` añade `:models {:tree <id|nil> :by-name {nombre {:version n
     :descriptor d}}}` del store viejo (`mstore/models`, `mstore/models-tree`).
   - En `build-plan!`, al definir cada modelo: calcula la versión con la regla y pásala como
     `:version`; el primer `define-plan` (el que crea el árbol) recibe `:tree-id` = el id viejo
     si lo hay, si no uno nuevo.
   - `:identities` gana `:models {:tree-carried bool :kept n :advanced n :new n}`
     (`kept`: misma versión e id; `advanced`: contrato distinto, versión +1; `new`: nombre que
     el store viejo no tenía). En un build sin store previo o con store ilegible:
     `{:tree-carried false :kept 0 :advanced 0 :new <n>}`.
   - Un modelo que el store viejo tenía y la declaración ya no define: añade su nombre a un
     vector `:models-not-carried` dentro de `:identities` y al warning
     `include/identities-not-carried` existente (no crees otro warning).

## Pruebas (cuatro, `test/kimun/cli/model_carry_test.cljc`; fixture de include como en
`identity_carry_test` / `field_units_test`)

1. **Sin cambios**: `include build`, anota id de árbol `models` y `{nombre [versión id]}`;
   `include build --rebuild` ⇒ idénticos; `:identities :models` =
   `{:tree-carried true :kept n :advanced 0 :new 0}`; el `:contract-id`/`:model-id` del
   documento es el mismo que antes.
2. **La versión no retrocede**: tras el primer build, cambia el archivo del modelo (un campo
   opcional más), `models define -f` sobre el store (versión 2), luego `--rebuild` con ese
   archivo ⇒ versión 2 y el mismo id que tenía la versión 2 antes del rebuild (`kept`).
3. **Contrato distinto entre builds**: cambia el archivo del modelo **sin** redefinir en el
   store y `--rebuild` ⇒ versión vieja + 1, id distinto, `:advanced 1`.
4. **Store viejo ilegible** (corrómpelo como hace `identity_carry_test` en su caso
   `:available false`) ⇒ versión 1, `:tree-carried false`, y el build termina ok.

Mutación: `git stash push -- src/` ⇒ fallan las cuatro (la 4 puede pasar sin el cambio: si
pasa, dímelo, no la fuerces); `git stash pop`.

## Documentos (mínimos)

- `docs/v2/06-superficies-y-cli.md`: `:identities :models` y `:models-not-carried` en
  `include build`.
- `docs/v2/12-ecosistema-y-pendientes.md §1`, fila `K1`: añade K1d («un rebuild conserva el
  árbol de modelos y la versión de los modelos sin cambios; un modelo es una clase direccionada
  por contenido, no se le acuña id») y quita «ids de modelos» de pendientes; evidencia
  `runs/subagents/2026-11-15-K1d/`. **No** digas que K1 está cerrado.
- `docs/v2/14-juego-del-lenguaje.md §21`: sustituye «los ids de modelos los acuña de nuevo el
  rebuild» por una frase con la regla, rotulada inferencia del equipo.
- Límite a escribir: el **historial** de versiones de un modelo (`models history`) no se
  arrastra; solo la versión vigente.

## Entrega

`lint.txt`, `directed.txt` (`model_carry_test`, `identity_carry_test`, `field_units_test`,
`models_test`, `include_test`, `typed_docs_test`, `doc14_contract_test`), `mutation.txt` en
`/tmp/kimun-k1d-wt/runs/subagents/2026-11-15-K1d/`. Reporta: archivos tocados, números,
mutación, tests existentes ajustados y por qué, `bb oracle`, y desvíos del brief.


## Adenda 0 — base

Base: `3ce5ebc5` (worktree `/tmp/kimun-k1d-wt` ya creado).

## Adenda 1 — cómo se nombra lo que K1d conserva (corrección conceptual)

Mi frase «un modelo es una clase… no se acuña id» decía de más. Corrige el vocabulario en
docstrings, documentos y mensajes:

- El hash del descriptor es la **dirección de un valor** (el contrato de una versión de la
  clase), **no la identidad de una unidad**. No escribas en ningún sitio que el hash
  «identifica» al modelo ni uses «model id» para decir identidad: di «dirección del
  descriptor» / «descriptor address» (el nombre de clave existente `:id` no se cambia).
- K1d **conserva versiones y contratos** en un rebuild (la versión no retrocede; un contrato
  sin cambios conserva su dirección) y el id del árbol `models`. **No decide** si un modelo es
  una unidad del grafo.
- Deja escrito, en `14 §21` y en la fila `K1` de `12 §1`, como **cuestión abierta para `M1`**
  (del equipo, no del dueño): `07` llama a los modelos «nodos del árbol»; si en `M1` los
  modelos pasan a ser unidades del grafo, deberán recibir **identidad propia** según D0
  (acuñada al marcarlos, no su hash), y la dirección por contenido seguirá siendo solo la del
  valor de cada versión.
- En `12 §1` **no** quites «ids de modelos» de pendientes: cámbialo por «identidad de modelos
  como unidades (decisión de `M1`)».

## Adenda 2 — `:models-not-carried` tiene que quedar probado

Es comportamiento observable nuevo y ninguna de las cuatro pruebas lo cubre. **Sin añadir otra
función de prueba**: en la prueba 1 (o la 3) declara desde el inicio un **segundo** modelo que
ningún documento usa; antes del `--rebuild` quita su archivo de la declaración; afirma que
`:identities :models-not-carried` es exactamente `["<ese nombre>"]` y que el envelope trae el
warning `include/identities-not-carried`. Afirma también que, sin quitar nada (el caso
«sin cambios»), `:models-not-carried` es `[]` y **no** hay ese warning por modelos.
Si por alguna razón no se puede hacer así, dímelo: lo marco «no verificado» en la evidencia y
no lo describimos como implementado.
