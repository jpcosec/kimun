# K2a — plan de implementación por commits

> **Solo lectura.** No se editó producto ni tests, no se ejecutó suite. Insumos:
> `docs/v2/10-escala.md §11`, `14 §19–§20`, `src/kimun/kernel/{revision,store,plan,tree}.cljc`,
> `src/kimun/host/{fs_store,store_cache}.cljc`, `src/kimun/surface/docs.cljc`,
> `src/kimun/cli/commands/{include,docs,stores}.cljc`, tests de format 4/cache/include.
> K6 está en curso: K2a usa un contrato de parse opaco y no fija su digest final.

## 0. Lo que hay hoy

- Formato 4: cada documento con archivo se registra con `:register-tree` (descriptor + `merkle-root`)
  y un anchor externo; árbol, bloques y textos viven en `<store>/cache/objects/`; el índice por
  documento en `cache/docs/<key>.edn` con `(fingerprint, class, parser-version)`.
- `Revision :trees` es un mmap `tree-id -> [descriptor-id merkle-root]`. La ruta del documento no
  está en ese mmap: se deriva del anchor externo (`docs/documents`) y del descriptor.
- El plan resuelto del build **sí** se persiste en el CAS (`revision/apply-plan` añade
  `tx-id -> resolved-plan` a `:new-objects`; `store/commit!` lo escribe en `objects/`).
- `store/verify-full` reconstruye por replay y exige el plan (`plan-of` lo lee del CAS).
- `include build` compone todos los planes en una revisión; no calcula un Merkle de rutas.
- `tree-id` es un ULID nominal por documento; se usa también al reconstruir. No hay una raíz estable
  sobre el conjunto de rutas.

## 1. Diseño objetivo (sin identidad nueva)

- **Route entry** = la unidad de tracking de una ruta, usando IDs actuales:
  `{:path :fingerprint :class :projection-hash :content-hash :tree-id}`.
  `:tree-id` es **locator/transitorio** y **no entra** en la raíz Merkle. `:path` es localizador, no
  identidad. No se crea unit ID ni se reemplaza `tree-id`, nodo, arista o revisión.
- **Route root** = `H(canonical-bytes(entries ordenadas por path))`, con path normalizado y NFC;
  entrada de hash = `[path fingerprint class projection-hash content-hash]`. La raíz debe ser igual
  tras `include build --rebuild` con los mismos archivos.
- **Route cache** = `<store>/cache/routes/<root>.edn` (derivado, borrable, verificable). La raíz
  estable se guarda en `heads.edn` como `:routes-root`; no se toca `store.edn` ni la revisión.
- **Plan build** = `<store>/cache/plans/<tx-id>.edn`. `:tx` de la revisión sigue siendo el digest,
  pero deja de ser un objeto del CAS. El plan es auditoría/caché, no autoridad.
- **Backward compatible:** un `heads.edn` sin `:routes-root` se acepta; su store está “route-unbuilt”.
  Los planes viejos en `objects/` se toleran y no se migran en sitio.

## 2. Commits

### C1 — Merkle de rutas puro

**Archivos**
- `src/kimun/kernel/route.cljc` (nuevo)
- `test/kimun/kernel/route_test.cljc` (nuevo)
- `test/fixtures/kernel/route-entries.edn` (nuevo, golden)

**Contenido**
- `entry`, `root`, `lookup`, `diff`, `valid-path?`, `canonical-entries`.
- Orden por path, NFC, rechazo de path duplicado o con `..`, raíz sobre la tupla sin `tree-id`.
- `diff` por path (`added/removed/changed`) para reconciliación local.

**Aceptación**
- Golden root reproducible; cambiar `tree-id` no cambia la raíz; cambiar fingerprint/proyección sí.
- Duplicados fallan `:route/ambiguous`; ningún ID nuevo.
- Sin cambios en store, include ni revisiones.

### C2 — Registrar ruta y raíz estable

**Archivos**
- `src/kimun/kernel/plan.cljc` (doc/schema de `:register-tree :route`)
- `src/kimun/kernel/revision.cljc` (`register-tree-op`, `apply-plan`, `lazy-store`)
- `src/kimun/kernel/store.cljc` (`heads-record`, `checked-heads`, `open-from-heads`, `verify`)
- `src/kimun/host/store_cache.cljc` (`routes-dir`, `read-route`, `write-route!`)
- `src/kimun/surface/markdown/plan.cljc` (`ast->tree` opts `:route`)
- `src/kimun/surface/docs.cljc` (`registry`/`track-plan`: pasar `:route` = path normalizado)
- `src/kimun/cli/commands/stores.cljc` (`stores show`/`check` exponen `:routes-root` y `:route-count`)
- `test/kimun/kernel/{revision,store,heads}_test.cljc`
- `test/kimun/cli/{include,diagnostics,stores}_test.cljc`

**Contenido**
- `:register-tree` acepta `:route` (string) y actualiza un mapa de rutas en `apply-plan`.
- `:routes-root` se añade a `heads.edn`; `well-formed-heads?` acepta la forma vieja (sin la clave) y
  la nueva. `open` no exige el objeto de rutas: raíz ausente = store route-unbuilt.
- `apply-plan` devuelve `:routes-root`; `commit!` lo escribe en heads.
- `docs track/update/include build` siguen usando `:register-tree`; solo añaden `:route`.

**Aceptación**
- Un `include build` produce `:routes-root` y `:route-count`; `stores show` lo muestra.
- `include build --rebuild` con los mismos archivos da la misma raíz.
- `docs show/list`, `stores check` y `include check` no cambian de respuesta.
- Un store formato 4 sin `:routes-root` abre y reporta `:route-unbuilt`, no corrupción.

### C3 — Plan del build fuera del CAS

**Archivos**
- `src/kimun/kernel/store.cljc` (`commit!`, `references`, `plan-of`, `verify-full`, `tx-line` docs)
- `src/kimun/host/store_cache.cljc` (`plans-dir`, `read-plan`, `write-plan!`)
- `src/kimun/host/fs_store.cljc` (si hace falta fallback explícito de plan; no cambiar `read-object`)
- `src/kimun/cli/commands/stores.cljc` (`verify` documenta plan ausente como no-corrupción)
- `test/kimun/kernel/store_test.cljc`
- `test/kimun/parity/merkle_promises_test.cljc`
- `test/kimun/cli/include_test.cljc`

**Contenido**
- `commit!` escribe el plan resuelto en `cache/plans/<tx-id>.edn`; no lo añade a `:new-objects`.
- `references` deja de seguir `:tx` como objeto; `verify`/`reachable` no dependen del plan.
- `plan-of` intenta CAS (legado) y luego cache; `verify-full` en formato 4 verifica sin exigir replay;
  si hay plan cache, puede replay; si falta, `:plan-unavailable` es nota, no `:bad`.
- `journal`, `open`, `docs show` no cambian.

**Aceptación**
- `objects/<tx-id>` no existe para un commit nuevo; el plan está en `cache/plans/`.
- `stores verify` pasa con `cache/plans/` borrada; `stores verify-full` no la declara corrupción.
- Un store viejo con plan en CAS sigue abriendo; no se reescribe.
- `bb oracle` y `bb lint` no cambian.

### C4 — Include build y migración/rebuild

**Archivos**
- `src/kimun/cli/commands/include.cljc` (`build-plan!`: validar raíz antes de commit, verificar después)
- `src/kimun/cli/commands/stores.cljc` (`upgrade`/`check`: `:route-unbuilt` y acción
  `include build --rebuild`)
- `src/kimun/surface/include.cljc` (si el plan necesita rutas normalizadas)
- `scripts/store_size.clj` (desglose `objects/`, `cache/objects/`, `cache/plans/`, `cache/routes/`)
- `test/kimun/cli/include_test.cljc`
- `test/kimun/cli/upgrade_test.cljc`
- `test/kimun/parity/cache_promises_test.cljc`

**Contenido**
- `include build` calcula las route entries desde los documentos declarados y verifica que
  `head :routes-root` coincide con la raíz calculada antes de responder.
- `include build --rebuild` reconstruye desde archivos y no copia `cache/`; es la migración fuerte
  para stores sin `:routes-root` o con planes en CAS.
- `stores upgrade` en formato 4 no reescribe revisiones: si falta `:routes-root`, reporta
  `:needs-rebuild true`, `:action "kimun include build --rebuild"`; si existe, no-op.
- No se migran planes viejos en sitio; se toleran y `store-size` los cuenta aparte.

**Aceptación**
- Temp repo: build → show root → rebuild → misma root.
- Store viejo sin root: `stores check` lo dice; `--rebuild` lo repara; no hay pérdida de documentos.
- Borrar `cache/plans` y `cache/routes`: `stores check/verify` y `docs show` siguen funcionando;
  `include build --rebuild` regenera.
- `include check` limpio; `git status` no muestra store trackeado.

### C5 — Evidencia visible y docs

**Archivos**
- `docs/v2/10-escala.md §11` (formato 4: route root, plan cache, límites)
- `docs/v2/06-superficies-y-cli.md` (`stores show/check`: `:routes-root`, `:route-count`)
- `runs/subagents/<run>/` (no producto): `store-size-before/after.txt`, `routes-root.txt`,
  `plan-objects-absent.txt`, `verify-after-cache-delete.txt`

**Aceptación**
- Un revisor reproduce desde checkout limpio:
  `include build` → `stores show` → `bb store-size` → `include build --rebuild` → misma root →
  borrar cachés → `stores verify` y `docs show`.
- Evidencia con JSON de `stores show/check`, tamaño por directorio y diff de root.
- No se afirma que el plan sea autoridad ni que la ruta sea identidad.

## 3. Invariantes

1. **Sin identidad nueva:** se usan `tree-id`, `descriptor-id`, path, `content-hash` actuales; no se
   crean unit IDs ni se cambia el id de nodo/arista/revisión.
2. **La raíz no depende de ULID, revisión, timestamp ni `tree-id`.** Rebuild con los mismos archivos
   da la misma raíz.
3. **Path es localizador, no identidad.** Duplicados o colisiones fallan visiblemente; no se resuelven
   por hash.
4. **Route cache es derivada y borrable.** La raíz en heads es el punto estable; el mapa se reconstruye.
5. **Plan no es autoridad.** Vive en cache, no en CAS; su ausencia no invalida el store.
6. **Lectura no escribe.** `stores check/show`, `docs show/list`, `stores verify` no crean cache.
7. **Formato 4 compatible:** heads viejo sin `:routes-root` se acepta; no se reescribe en sitio.
8. **Un documento con archivo entra; inline/sin archivo no inventa ruta.** Queda fuera del route set.
9. **K6:** `:contract` del route entry es opaco; K2a no fija el digest final ni la versión del parser.
10. **Un solo build, una revisión:** la raíz se calcula sobre el resultado del plan, no por documento
    en commits separados.

## 4. Migración y rebuild

- **Formato 4 actual:** `heads.edn` con `:head/:heads-root`; no hay route root. `open` acepta; `stores
  check` reporta `:route-unbuilt`. No se toca el store.
- **Store nuevo / rebuild:** `include build --rebuild` mueve el store a `.bak-*`, reconstruye desde
  archivos, calcula route root y escribe planes solo en cache.
- **Plan viejo en CAS:** se deja; `verify` no lo exige. `store-size` lo cuenta como legado.
  No se borra automáticamente.
- **Cache borrada:** `cache/routes` y `cache/plans` se regeneran con `include build --rebuild` o
  `stores check` (solo reporta); `stores verify` no falla.
- **Rollback:** el `.bak-*` de `--rebuild` es el rollback; no hay migración en sitio de format 4.

## 5. Prueba visible (definición)

`runs/subagents/<run>/` debe contener:

- `stores-show.json` con `:routes-root`, `:route-count` y `:head`.
- `store-size-before.txt` / `store-size-after.txt` con bytes de `objects/`, `cache/objects/`,
  `cache/plans/`, `cache/routes/` y total.
- `rebuild-root.txt`: dos `include build --rebuild` y la misma raíz.
- `plan-objects-absent.txt`: `find .kimun/objects -name '<tx-id>'` vacío y presencia en
  `cache/plans/<tx-id>.edn`.
- `verify-after-cache-delete.txt`: `stores verify` ok tras borrar `cache/plans` y `cache/routes`.
- `include-check.txt`: `include check` limpio.

Sin suite en esta revisión. La primera implementación debe empezar por C1; C2–C4 esperan a que K6
estabilice el campo de contrato y a que Claude libere `stores.cljc`.
