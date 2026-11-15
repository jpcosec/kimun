# Brief K1c — unidades de campo: identidad debajo del documento (tercer corte de K1)

Para: Pi (worker). De: Claude (diseño y revisión). Un commit (lo hago yo tras revisar).

## Reglas

- Worktree: `/tmp/kimun-k1c-wt` (detached; la base te la doy al despachar). No toques
  `/home/jp/proyectos/kimun` ni ningún store real.
- No commitees. Todo `bb` por `~/bin/memcap`. Dirigidas sí; **no** `bb test` completo.
  Antes de cada `bb`: `pgrep -x bb` vacío.
- Código y docstrings en inglés; documentos en español.
- **Pocas pruebas**: las seis de abajo. Sin variantes.
- **Sin cambios en `src/kimun/kernel/*`.** Si crees que hace falta, para y dime por qué.

## Decisiones de diseño (cerradas por el equipo; son inferencias, no palabras del dueño)

Contrato: `docs/v2/14-juego-del-lenguaje.md §20` (trackeable vs. trackeado) y `§21` (D0).

1. **Qué es una unidad debajo del documento, en este corte:** cada **campo de primer nivel
   que el modelo de un documento tipado selecciona** (las claves del payload extraído). Es lo
   *trackeado* de §20: lo que el modelo de anclaje marca. Un bloque o párrafo cualquiera es
   *trackeable*, no trackeado: no recibe identidad aquí.
2. **Su anclaje** es `(unidad documento, nombre del campo)` bajo el contrato del modelo. No es
   su identidad.
3. **Su identidad** es un ULID acuñado la primera vez que el campo se registra (regla «nace al
   marcarla»), **persistido en el store** porque no se puede recomputar: en el **descriptor del
   árbol** del documento, clave `:units {"<campo>" "<ulid>"}`. El descriptor es un objeto del
   store, versionado por revisión, y el kernel lo guarda tal cual (`register-tree-op` usa
   `(:tree op)` sin filtrar claves): no hace falta tocar el kernel. No es caché.
4. **Continuidad:** mientras el campo se sigue seleccionando, cualquier cambio de su valor es
   una mutación de la misma unidad (regla 2): el id se conserva en cada escritura.
5. **La ausencia no termina la unidad (regla 3):** un campo que deja de extraerse (el contrato
   lo quitó, o el texto ya no lo trae) **se queda en `:units`** con su id. Lo que cambia es su
   estado derivado: `:anchored` si está en el payload vigente, `:missing` si no. Si vuelve a
   aparecer con el mismo nombre, es la misma unidad. Cerrar una unidad de campo es una
   declaración que **no** entra en este corte (límite declarado).
6. **Renombrar un campo** = uno `:missing` y uno nuevo acuñado. El linaje entre ambos no se
   declara todavía (límite).
7. **Fuera de alcance:** documentos genéricos (su clase selecciona toda la estructura; sus
   bloques no tienen localizador estable hasta `S2`), campos anidados e ítems de lista,
   documentos `:inline` o de store legado (formato < 4; el árbol no pasa por `:register-tree`),
   ids de modelos, y todo G.

## Qué cambia

### 1. Superficie (`src/kimun/surface/docs.cljc`, `src/kimun/surface/markdown/plan.cljc`)

- `md/ast->tree` acepta `:units` en `opts` y lo pone en el descriptor (`cond->`, solo si no es
  nil/vacío: **un descriptor sin `:units` queda byte a byte como hoy**).
- `registry` (y por tanto `track-plan` / `update-plan` en la rama `cached?`) calcula `:units`:
  - parte de los `:units` del descriptor vigente del documento (nil al rastrear);
  - para cada clave de primer nivel del payload extraído que no esté, acuña un id. **El
    acuñador viene de fuera**: `opts` gana `:mint` (fn sin argumentos → ULID), que la CLI pasa
    como `#(ports/ulid (:ids host/host))`. Sin `:mint` no se acuña nada (y no hay `:units`
    nuevos): así los llamadores existentes de la superficie no cambian de comportamiento.
  - nunca quita una clave.
  - `track-plan` acepta además `:units` iniciales en `opts` (para el arrastre del punto 3).
- `describe` añade a la mapa del documento `:units` (el mapa `{campo id}` del descriptor).
- Función pública nueva `unit-states [store doc]` →
  `[{:field :id :status :anchored|:missing}]` ordenado por campo, comparando `:units` con las
  claves del payload vigente (`payload-of`); si el payload no extrae (`:error`), todas
  `:missing`.
- **Un documento sin `:units` (anterior a este corte) los recibe en su siguiente escritura.**
  En `update-plan`, «no tiene `:units` y hay `:mint`» cuenta como **movido** (igual que el
  precedente `:unrouted`), de modo que `docs update NAME` sobre un archivo sin cambios escribe
  una revisión que solo registra las unidades (`:unchanged?` false esa vez; la siguiente, true).
  **Verifica** que esto no degrada `stores health`/`stores sense` de un documento legado sin
  tocar (deben seguir ok/sin señales): si lo degrada, para y dime.
- Revisa quién más construye o compara el descriptor de un documento en caché
  (`rebuild-tree`, `rebuilt-root`, `tree-complete?`, K6 `rebuild-mismatch`, `route-entry`,
  `projection-hash`): `:units` **no** debe entrar en `merkle-root`, `projection-hash`,
  `content-hash` ni en la entrada de ruta (es identidad, no huella). Si alguno compara el
  descriptor entero, dímelo antes de cambiarlo.

### 2. CLI (`src/kimun/cli/commands/docs.cljc` y quien llame a `track-plan`/`update-plan`)

- `docs create`, `docs track`, `docs update` (tipado), `docs reanchor` y `stores update` pasan
  `:mint`. Busca todos los llamadores (`grep -rn "track-plan\|update-plan" src/`).
- `docs show NAME` gana `:units [{:field :id :status}]` (de `unit-states`). `docs list` no cambia.

### 3. Arrastre en `include build --rebuild` (K1a, `src/kimun/cli/commands/include.cljc`)

- `identity-registry` lee también `:units` de cada documento tipado del store viejo; al
  reconstruir, el documento arrastrado recibe esos `:units` como iniciales (los campos nuevos
  se acuñan; los que ya no se extraen se conservan como `:missing`).
- `:identities` gana `:units-carried n` (cuántos ids de campo se reutilizaron). Sin más claves.

## Pruebas (seis, `test/kimun/cli/field_units_test.cljc`; helpers como `reanchor_test`)

1. **Nacen al rastrear y sobreviven a editar**: `docs track` ⇒ `docs show` trae una unidad por
   campo, ids distintos entre sí; editar el valor de un campo en el archivo + `docs update` ⇒
   los **mismos** ids, todos `:anchored`.
2. **Dos documentos del mismo modelo** con el mismo contenido tienen ids de campo distintos
   (dos marcas son dos unidades).
3. **La ausencia no termina la unidad**: redefine el modelo sin un campo (o con otro contrato
   que ya no lo extrae; usa lo que `models define` permita, mira `typed_docs_test` para la
   sucesión de modelos) y actualiza ⇒ ese campo sigue en `:units` con el mismo id y
   `:missing`; al volver a definirlo y actualizar ⇒ mismo id, `:anchored`. Si la sucesión de
   modelos no permite quitar un campo, usa un campo nuevo (aparece con id nuevo, los demás
   intactos) y dime que el caso `:missing` quedó sin prueba.
4. **Re-anclar conserva las unidades**: `docs reanchor` ⇒ mismos ids de campo.
5. **Rebuild las arrastra**: `include build`, anota los ids, `include build --rebuild` ⇒ mismos
   ids de campo y `:identities :units-carried` = número de campos.
6. **No es huella**: tras `docs track`, `:merkle-root`, la entrada de ruta
   (`:projection-hash`, `:content-hash`) y `docs status` son los mismos que da el mismo archivo
   rastreado en otro store (ids de campo distintos, huellas iguales).

Mutación: `git stash push -- src/` ⇒ las seis fallan; `git stash pop`.

## Documentos (español, mínimos)

- `docs/v2/14-juego-del-lenguaje.md §21`, párrafo de implementación de K1: una frase para K1c
  (unidades de campo de documentos tipados, persistidas en el descriptor del árbol; estados
  `anchored|missing`) y los límites del punto 7 + cierre y linaje de campo no declarables aún.
  Rotula las decisiones 1–6 como **inferencias del concilio/equipo**.
- `docs/v2/12-ecosistema-y-pendientes.md §1`, fila `K1`: añade K1c y su evidencia
  `runs/subagents/2026-11-15-K1c/`; en pendientes deja: unidades de bloque/sección (esperan
  `S2`), campos anidados e ítems, cierre y linaje de unidades de campo, ids de modelos,
  copiar/dividir/fusionar.
- `docs/v2/06-superficies-y-cli.md`: `:units` en `docs show`; `:units-carried` en `include build`.
- `docs/v2/10-escala.md` (donde describe el descriptor del árbol / formato 4, si lo hace): una
  línea diciendo que el descriptor lleva `:units` y que es store, no caché.

## Entrega

Dirigidas en verde: `field_units_test`, `reanchor_test`, `identity_carry_test`, `docs_test`,
`typed_docs_test`, `generic_docs_test`, `contract_test`, `sense_test`, `routes_test`,
`c4_route_rebuild_test`, `doc14_contract_test`, y `bb lint`. Salidas en
`/tmp/kimun-k1c-wt/runs/subagents/2026-11-15-K1c/` (`lint.txt`, `directed.txt`,
`mutation.txt`). Reporta: archivos tocados, números, mutación, **todo test existente que
hayas ajustado y por qué** (espero que alguno fije el descriptor o el número de revisiones),
si `bb oracle` sigue en 0 diferencias, y desvíos del brief.

## Adenda 0 — base y pod

- Base: `6a1c7ba0` (worktree `/tmp/kimun-k1c-wt` ya creado).
- En un worktree el pod tree-sitter no se encuentra solo. Exporta en cada `bb`:
  `KIMUN_POD=/home/jp/proyectos/kimun/pods/treesitter/target/release/pod-kimun-treesitter`
  (solo lectura; no lo recompiles).
- Añade `tour_test` a las dirigidas.

## Adenda 1 — dos pruebas que hoy podrían pasar sin probar nada

1. `an-absent-field-keeps-its-unit`: quita la rama `(is true …)`. La prueba debe **fallar** si
   no llega a comprobar `:missing`. Afirma `(:ok d)` de `models define`; si la sucesión de
   modelos rechaza quitar un campo, consigue la ausencia por otro camino válido (por ejemplo
   un campo no requerido que el texto deja de traer, o el contrato que la sucesión sí acepte)
   y dime cuál. Si de verdad no hay ninguno, **no** dejes una prueba verde vacía: borra esa
   prueba, repórtalo como límite sin prueba y lo anoto en la evidencia.
2. `units-are-not-a-fingerprint`: antes de comparar, afirma que `routes-of` de cada store
   tiene exactamente una entrada y que sus `:projection-hash` y `:content-hash` son strings
   no vacíos (que no sean dos mapas vacíos iguales). Mira cómo lee las rutas
   `identity_carry_test` (clave `:tree-id`, raíz de rutas) y compara también la raíz de rutas
   (`:routes-root`) de ambos stores: debe ser **igual** con ids de campo distintos… salvo que
   la entrada de ruta incluya `:tree-id` (que es distinto por store); en ese caso compara las
   entradas sin `:tree-id` y dímelo.
3. Dime por qué tocaste `identity_carry_test.cljc` y `cli/commands/models.cljc`.

## Adenda 2 — invariante exacta de la prueba 6, y el caso `:missing`

**Prueba 6. No cambies la semántica del Merkle.** `merkle-root` difiere entre dos stores con
el mismo archivo **ya antes de K1c**: `registry` pone el id de documento en el AST
(`[:attrs :doc]`) antes de hashear, y por eso `projection-hash` se toma *antes* de ese id (ver
su docstring). Mi brief pedía de más. La invariante correcta es: **`:units` no AÑADE identidad
a ninguna huella**. Reescribe la prueba en dos partes:

- (a) *superficie, misma identidad de documento*: llama dos veces a `md/ast->tree` con el
  **mismo** AST (mismo `[:attrs :doc]`), mismo `:tree-id`, `:class`, `:content-hash`, y
  `:units` distintos (y una tercera sin `:units`): `:merkle-root`, `:root`, `:objects` y
  `:nodes` son **iguales** en las tres; los descriptores solo difieren en `:units`; y el
  descriptor sin `:units` es idéntico al que sale hoy (sin la clave).
- (b) *CLI, dos stores*: el mismo archivo rastreado en dos stores da ids de campo distintos y
  **la misma** `:projection-hash`, `:content-hash` y `:fingerprint` en la entrada de ruta
  (afirma antes que hay exactamente una entrada con hashes no vacíos), y el mismo `docs
  status`. **No** compares `merkle-root` ni la raíz de rutas entre stores: llevan ids de
  documento/árbol distintos por diseño previo.

Si (a) falla, **eso sí** es una fuga: para y dime dónde entra `:units` al hash.

**Caso `:missing`.** Redefinir el modelo para quitar/reponer un campo choca con la
inmutabilidad de clase: no insistas por ahí. Prueba, en este orden:

1. Fixture con un **campo opcional desde el inicio** (`:required false`): el archivo lo trae,
   se rastrea (unidad acuñada), luego el texto deja de traerlo y `docs update` ⇒ si la
   extracción omite la clave: mismo id, `:missing`; al volver el texto: mismo id, `:anchored`.
2. Si la extracción no omite la clave (devuelve nil/""), entonces **end-to-end no hay ausencia
   válida**: no la finjas. Prueba `unit-states` directamente con un doc/payload controlado
   (un mapa de documento con `:units` de dos campos y un payload con uno solo ⇒ uno
   `:anchored`, otro `:missing`, mismo id), di en el docstring de la prueba que **no** es
   end-to-end, y repórtalo como límite: «`:missing` de campo sin camino end-to-end en este
   perfil de modelos».
   Decide además qué cuenta como «el campo está»: clave presente en el payload, aunque su
   valor sea vacío. Dilo en el docstring de `unit-states`.
