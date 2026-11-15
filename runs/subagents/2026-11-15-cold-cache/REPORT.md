# C1 · cold-cache — fase 1 (matriz, sin arreglos)

Worktree `/tmp/kimun-c1-wt` (base `ad3b2541`). **`src/` intacto** (la mutación se restauró; `git status` solo muestra
archivos nuevos y una línea en docs).

## Archivos tocados
- NUEVO `test/kimun/cli/cold_cache_test.cljc` (2 `deftest`, 17 aserciones; ~14 s).
- `docs/v2/10-escala.md` (+1 párrafo tras «borrable: todo se recalcula», en §11) que apunta a la prueba.
- Evidencia: `lint.txt` (`bb lint` ok, 104 archivos), `directed.txt` (0 fallos), `mutation.txt`.

## Qué prueba
Fixture: proyecto temporal con `docs/a.md` (TicketDoc, tipado) y `notes/plain.md` (genérico, ingestado), índice derivado
escrito, `cache/` poblada (21 objetos + 2 entradas `docs/` + 3 `plans/`). Tabla `matrix` de 49 comandos de lectura
(label → argv). Para cada uno, la respuesta (`:ok :command :data :error :revision :warnings`, sin `:observed-at`/`:timestamp`)
con caché caliente se compara con la respuesta tras:
1. `rm -rf .kimun/cache` (`:cold`);
2. borrar solo `cache/objects` y `cache/docs`, dejando `cache/plans` (`:cold-no-plans`, aísla la causa);
3. corromper UN objeto de `cache/objects`, uno cada vez, los 21 (`:corrupt`; segundo `deftest`).
La caché se restaura de una copia antes de cada comando. Además se afirma que con caché caliente todos los comandos de la
matriz responden `ok` y con `:data` no vacío (así no se comparan dos errores ni dos vacíos).
`known-cold-cache-gaps` = `{:cold #{"journal show" "journal verify"} :cold-no-plans #{"journal show"} :corrupt #{"journal show"}}`;
la prueba exige igualdad exacta de conjuntos: falla ante una brecha nueva y falla cuando una listada deja de divergir.

## Matriz (comando → por dónde lee → caché fría → un objeto corrupto)
Resultado "= " significa respuesta idéntica a la caliente, sin error.

| Comando(s) | Dónde lee el árbol/caché | cache/ borrada | objeto corrupto |
|---|---|---|---|
| `docs render typed/generic` | `cli/commands/docs.cljc:render` → `surface/docs.cljc:document-markdown` → `doc-tree` (:411) → `markdown/plan.cljc:store->markdown/store->ast` | = (reconstruye desde el archivo) | = |
| `docs show --ast typed/generic` | `cli/commands/docs.cljc:show` (:494) → `document-ast` → `doc-tree` → `store->ast`; el fallback `(md/store->ast store tree)` solo corre si `document-ast` da nil | = | = |
| `docs show typed`, `docs list` (+`--model`), `docs status`, `docs compose`, `docs recover` | no leen el árbol: `surface/docs.cljc:describe` (:145) usa `:read-file` + descriptor | = | = |
| `fields get/schema/query`, `sections list/fields/find`, `find` (físico/semántico/campo/modelo) | idem: payload extraído del archivo vía `tdocs/document`/`find-document`; `find` usa `.kimun/index` | = | = |
| `edges show/check/node/nodes/types`, `graph get/list/neighborhood/path/cycles/order/components/central/isolated/similar/export` | `surface/edges.cljc` (:88 `relations-tree`) solo lee el árbol taxonomía `relations` (persistido, no caché); los nodos de documento salen de descriptores y archivos | = | = |
| `models list/show/check/history` | `surface/models/store.cljc` (:49, :145): árbol de modelos persistido | = | = |
| `stores check/verify/health/show/sense --once/unreachable`, `semantic tags` | kernel/descriptores; `sense` y `health` no tocan `cache/` | = | = |
| **`journal show`** | `surface/journal.cljc:node-texts` (:22) `revision/get-object` de nodos añadidos/quitados (cache/objects) y `:ops` de `cache/plans` | **DIFIERE**: `:ops {}` y `:text` y `:nodes-added` vacíos/rebajados (con solo objects/docs borrados: pierde `:text`) | **DIFIERE**: el objeto dañado se descarta (no devuelve bytes dañados) pero la lista `:text` pierde ese texto en silencio, sin aviso (18 de 21 objetos cambian la respuesta) |
| **`journal verify`** | `journal.cljc` / replay del log: exige el plan resuelto de cada línea del log en `cache/plans` | **FALLA**: `store/corrupt` «the log does not replay … missing resolved plan for a log line» | = |

Hallazgo mayor: `<store>/cache/plans/` **no es caché borrable**: sin él el log no se reproduce (`journal verify` falla con
`store/corrupt`). Contradice la promesa de `00-nucleo`/`10 §11` («borrable: todo se recalcula») para `cache/` entera
(`stores check`/`health` no lo detectan: dan igual).

## Lo que NO se reprodujo
La deuda de `cache-verificable/EVIDENCE.md` (consumidores fuera de `doc-tree`: `kimun.surface.edges`, fallback de
`docs show --ast`, `kimun.surface.models`) **no produce divergencia con este fixture**: `edges` y `models` no leen árboles
de documento; el fallback `md/store->ast` de `docs show --ast` es inalcanzable mientras `document-ast` resuelva. Límites del
fixture: sin campos de referencia entre documentos (el brief pide un `edges` sobre campos de referencia, prueba (5) de la
fase 2), sin store de 600 documentos, y `find` no indexa el genérico (`find Para` → `find/empty`, no relacionado con caché).

## Medido aparte (no está en la prueba; sonda en /tmp)
Con `cache/` borrada **y el archivo ausente**: `docs show --ast`/`docs render` → `markdown/not-a-document` «store->ast:
missing node {:id nil}» (error no declarado, engañoso); `docs show a` y `fields get a title` → `cli/unexpected`
NullPointerException; `docs status` → `:missing` (correcto); `stores check` → `store/invalid … 2 missing`;
`stores health` → `needs-human` (correcto).
También: `docs show plain` (genérico por nombre) da `docs/missing` con caché caliente; solo `render`/`--ast` resuelven genéricos.

## Mutación (`mutation.txt`)
En `src/kimun/surface/docs.cljc`, `doc-tree` rama fría: `(rebuild text)` → `(do text nil)` (restaurado). Resultado: las 3
pruebas de brechas fallan con «new gaps: docs render generic/typed, docs show --ast generic/typed»; sin mutación: 0 fallos.
No se probó el sentido contrario (brecha listada que se arregla) por mutación, solo por construcción (igualdad exacta de
conjuntos; lo vimos al ejecutar con la lista vacía: falló con las brechas nuevas listadas).

## Ambigüedades
- `10 §11` solo cubre «caché no está»; no dice qué pasa con caché fría **y** archivo ausente (arriba: hoy error engañoso o NPE).
- ¿`cache/plans` es caché (borrable) o parte del store? El código lo trata como requisito del replay; el doc lo llama caché.
  Decisión del dueño antes de arreglar `journal verify`.
- ¿Qué debe hacer `journal show` cuando el texto no se puede leer: omitir `:text` con una marca, o reconstruir desde los documentos?

## Brief de la fase 2 (despachar cuando `surface/docs.cljc` quede libre)
1. `src/kimun/surface/journal.cljc` (`node-texts`, `change-detail`): no depender en silencio de `cache/objects`. Si el nodo no
   es legible, marcar `:text :unavailable` (o reconstruir); jamás devolver una lista mutilada. Prueba: quitar `"journal show"`
   de los tres conjuntos de `known-cold-cache-gaps` en `cold_cache_test.cljc`.
2. `journal verify` / replay (`kernel`, solo si el dueño decide que `cache/plans` es caché): regenerar el plan desde el log o
   declarar que `plans/` es parte del store y sacarlo de `cache/`. Prueba: quitar `"journal verify"` de `:cold`. Si el dueño
   decide lo segundo, documentarlo en `10 §11` y mover la ruta (cambia formato: coordinar con K1e).
3. `surface/docs.cljc/doc-tree` + `cli/commands/docs.cljc:show`: con archivo ausente y caché fría devolver un error declarado
   (p. ej. `docs/file-missing`) en `show`/`render`/`--ast`/`fields get`, no `not-a-document {:id nil}` ni NPE. Pruebas nuevas en
   `cold_cache_test.cljc`: (a) archivo ausente + `cache/` borrada → `:ok false` con `:error :type` declarado para cada comando
   de la lista; (b) `cache/` intacta tras un lector con objeto corrupto (contrato `10 §11` de no escribir: aún no verificado aquí).
4. Ampliar el fixture con campos de referencia y `[[links]]` resolubles y añadir `edges`/`graph` sobre ellos (prueba 5 del plan).
5. Si algún día `edges`/`models` consumen árboles de documento, añadir su comando a `matrix`.

## No verificado
- No se comprobó que un lector con objeto corrupto no escriba en `cache/` (solo que su respuesta no cambie salvo `journal show`).
- No se corrompieron entradas `cache/docs/*.edn` ni `cache/plans/*.edn` ni packs de caché (`cache/packs`): solo objetos sueltos.
- Solo `ktu/temp-dir` locales; formato del store el que crea `stores init` hoy (cache/ presente tras `docs track`).
- `bb test` completo no se corrió (lo hace el coordinador).
