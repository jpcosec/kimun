# sin-legado — REPORT

Worktree `/tmp/kimun-nolegacy-wt` (detached sobre `main` ec901ac7). Sin commit. Evidencia en este directorio:
`inventory.md`, `lint.txt` (verde, 107 archivos), `directed.txt`, `oracle.txt` (9 filas, 0 diferencias),
`store-compare.txt`.

## Qué se borró

Números: `src/` +299 / −1.524 líneas (neto −1.225); `test/` +216 / −1.222; `docs/` +69 / −145.

**Archivos borrados (src):** `kernel/legacy/revision.cljc` (408), `kernel/upgrade.cljc` (96),
`kernel/heads.cljc` (56), `kernel/pool.cljc` (34), `kernel/standoff.cljc` (88).
**Archivos borrados (test):** `kernel/upgrade_test`, `cli/upgrade_test`, `kernel/heads_test`,
`kernel/pool_test`, `kernel/standoff_test`.

1. **Formatos 1, 2 y 3 y esquema de rutas 1**: el motor legado y sus 18 ramas `legacy?`; la rama de mapa plano de
   `apply-plan`; `upgrade-routes!`, `HeadsSwap`/`swap-heads!`, `RouteCache`/`cache/routes/`; `init!` ya no acepta
   `opts` de formato; `stores upgrade` entero (1→2, 2→3, 3→4/`:needs-rebuild`, esquema 1→2); `route.cljc` pierde
   `root`, `canonical-map`, `map-object`, `entries-digest`, `lookup`, `diff` y `schema`; `reachable` pierde las
   formas de formato 1; `pack-only!`. Un store de otro formato o sin `:route-schema 2` falla al abrir con **un**
   error, `store/unsupported-format` (exit 5): «format N is no longer supported: rebuild with `include build
   --rebuild`». Lo fijan `hardening_test` (`open`, `replay-open`, `replay` con formatos 1, 2, 3 y 4/esquema 1),
   `cli/route_schema_test` (por la CLI) y `cli/pack_test`.
   Comprobado antes de borrar el esquema 1: `init!` escribe siempre `:format-version 4` y `:route-schema 2`; nada del
   presente crea otro (solo lo hacían las opciones de prueba/upgrade).
2. **Copiado**: las 16 privadas de `legacy.revision` se van con el namespace; `sha256-hex` del host queda una sola
   (`store-cache/sha256-hex`, bytes; `fs-store` la usa); `kernel.edge` usa `tree/path?`.
3. **Muerto**: namespaces `heads`, `pool`, `standoff` (+ `host.default/layer-cache`), `node/classes`, `node/kinds`,
   `store-cache/read-object`, `store-cache/object-ids`, el concepto `:held` y la clase `:plan` del inventario de
   `stores unreachable` (los planes en el CAS eran de formatos 1–3), tres `:require`/imports sin uso. Evidencia por
   ítem en `inventory.md`.

Lo que cambia en la salida de la CLI: `stores check` ya no informa `:routes-schema`; `stores unreachable` ya no
devuelve `:held` (ni `:ids :held`); desaparece el comando `stores upgrade` (el subcomando desconocido lista los demás).

## Qué deja de poder hacerse

Abrir un store de formato 1/2/3, o formato 4 sin `:route-schema 2`; `stores upgrade` y todos sus caminos; escribir en
esquema 1. El camino único es `include build --rebuild` (o `migrate --from-v1` para un store de sldb v1, que no se
tocó y **no dependía** de `kernel.legacy`). Si hay un store real de formato viejo (el desk de otros proyectos), hay que
reconstruirlo.

## Pruebas que se fueron o cambiaron

- Borradas: `kernel/upgrade_test`, `cli/upgrade_test` (formatos/migraciones), `heads_test`, `pool_test`,
  `standoff_test`; `concurrent-commits-on-disjoint-trees-all-land` (hardening, usaba `heads/commit!`);
  `the-flat-route-map-of-schema-1…`, `a-plan-the-log-names-is-held…`, `a-held-plan-is-needed…`
  (`kernel/unreachable_test`); `format-3-keeps-its-resolved-plan-in-the-cas`; `a-format-1-store-opens-commits-…`; los
  seis tests de upgrade/esquema 1 de `route_tree_store_test` y `route_schema_test`; los de caché del mapa plano de
  `route_store_test` (quedan register/update/remove/replay/coverage/untrack sobre el árbol).
- Reducidas: `revision_test` (sin caso del motor de formato 1), `revision_format2_test` (el oráculo «motor legado vs
  formato 2» pasa a una propiedad de coherencia de vistas e incidencia sobre historias aleatorias, `random-histories-
  keep-the-views-and-the-incidence-consistent`), `store_test` (dobles `fv [1 2]` colapsados; el log de la prueba «log
  que no termina en las cabezas» ahora deja los planes en la caché de planes porque la línea ya no lleva `:plan`),
  `route_test` (sin `root`/`lookup`/`diff` planos; la raíz de oro de la fixture ahora es la del árbol esquema 2,
  `29412cd5…`, fijada desde la salida de este código: es un pin de regresión, no un valor independiente),
  `route_tree_test` (el oráculo `route/diff` pasa a un `flat-diff` local; sin comparación con la raíz de esquema 1),
  `hardening_test`, `unreachable_test` (kernel y cli), `pack_test` (host y cli), `stores_test`, `routes_test`.
- Ajustada la prueba de contrato: `test/kimun/doc14_contract_test.cljc` fijaba frases de `docs/v2/14` sobre «esquema
  1», `stores upgrade` y `2→3 en el sitio`; esas frases se quitaron del documento y de la prueba (los marcadores
  C2a/C2b/C4/C5, `b4931b7c`, `esquema 2`, `fan-out` se conservan).
- Paridad con v1: ninguna prueba de paridad dependía de un formato viejo de kimun.

## Verificación

- `bb lint` verde (107 archivos). `bb oracle`: 9 filas, 0 diferencias.
- Pruebas dirigidas verdes, 0 fallos/errores (`directed.txt`): todo `test/kimun/kernel/` y `host/` (219 tests, 1211
  aserciones), `stores`, `include`, `identity_carry`, `model_carry`, `routes`, `route_schema`, `migrate`, `docs`,
  `typed_docs`, `generic_docs`, `cold_cache`, `journal`, `pack`, `cache_pack`, `unreachable`, `doc14_contract` y
  `c4_route_rebuild`, `cache_verified`, `fault_injection`, más una tanda de 104 tests de superficie/CLI.
- Store nuevo igual byte a byte (`store-compare.txt`): store determinista (ids de árbol y timestamp fijos, 5 documentos
  con ruta + 1 commit) construido con `main` antes de editar y con este árbol; `diff -r` sin diferencias (42 archivos:
  `store.edn`, `heads.edn`, `log.edn`, `objects/`, `cache/`). Una construcción por CLI no sirve para comparar porque los
  ULID de árbol dependen del reloj; por eso el script fija ids.
- No hay `mutation.txt`: no añadí comportamiento nuevo salvo el error declarado, y sus pruebas (`hardening_test`,
  `route_schema_test`, `pack_test`) fallan en `main` porque el store de formato viejo se abría o devolvía otro error.

## Documentos

`docs/v2/10-escala.md` (§8 pasa a «Formatos anteriores»; fuera `stores upgrade`, esquema 1, `:held`, motor legado),
`06` (sin `stores upgrade`; `unreachable` sin `:held`; `check` sin `:routes-schema`), `12 §1` (K2a, K5) y `14` (§10
nota, §12, §13, §20, decisión 44, fila de `10`) en frases cortas. Quedan sin tocar, fuera de la lista de documentos que
se me pidió: `docs/v2/02-sustrato-computacional.md:314`, `docs/v2/05-estado.md:15`, `docs/v2/README.md:44` (menciones sueltas a `stores upgrade`); y las descripciones históricas del formato 2/3 de `10 §1–§10`
(son medidas de época).

## Qué NO se borró y por qué

- **`src/kimun/cli/main.cljc:78`**: la ayuda sigue listando `stores upgrade` (no es mío tocarlo). Hay que borrar esa
  línea; si no, la ayuda promete un comando que ya no existe.
- **`kernel.reconcile` (`orphans`, `proposals`, `accept-plan`) y `kernel.anchor` (`endpoint-state`, `anchored-in`,
  `report`, `diff`)**: nada de `src/` los llama (solo `reconcile/dice` y `anchor/state|states|placements`), pero son la
  reconciliación de `docs/v2/02 §6.5` y sus pruebas viven en `test/kimun/surface/markdown/drift_test.cljc`, fuera de mi
  alcance. ~170 líneas más sus pruebas. Decisión del dueño.
- **`tree/paths-of`, `tree/hash-at`, `route-tree/from-entries`, `route-tree/diff`, `store/replay`,
  `fs-store/cached-ids`**: solo los llaman pruebas, pero son los accesores con los que esas pruebas inspeccionan; borrarlos
  obliga a reescribir decenas de aserciones sin ganancia. Si se quiere, se hace en otra pasada.
- **`removal` de `mmap` y `route-tree`**: el informe los da por idénticos, pero son `::remove` de dos espacios de nombres
  (`:kimun.kernel.mmap/remove` ≠ `:kimun.kernel.route-tree/remove`); unirlos haría que un centinela de rutas valga como
  borrado de mmap. **`dirname`** (`"cache"`/`".kimun"`/`"index"`): valores distintos, falso positivo.
- **`:legacy-v1-template`** (modo de compatibilidad v1 del selector de modelos, `surface/models/*`) y `legacy` de
  `surface/docs` (documento «legacy» de `:shared-path :allow`): no son formatos de store de kimun y están fuera de
  kernel/host. Son lo siguiente que la suspensión de paridad con v1 haría borrable.
- Los dos ciclos de `requiring-resolve` están fuera del kernel; en kernel/host no hay ninguno.

## Para decidir (dueño)

1. ¿Se borra la reconciliación de anclas (`reconcile` + parte de `anchor`, `02 §6.5`) y `drift_test`? Hoy solo se usa
   `dice` y `anchor/state*`.
2. Confirmar que `kernel.standoff` (capas UAX #29 de `02 §4`, ya borrado por estar sin llamadores), `heads` y `pool`
   no eran una promesa de diseño: `02`/`03` aún los nombran (`git show ec901ac7:src/kimun/kernel/standoff.cljc` los
   recupera).
3. ¿Se retira ya `:legacy-v1-template` y el modo `:shared-path :allow`?
4. Un store real de formato viejo (desk o copias de trabajo) ya no abre: hay que reconstruirlo con `include build
   --rebuild` antes de usar esta rama.
