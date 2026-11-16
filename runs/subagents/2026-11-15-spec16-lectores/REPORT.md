# spec16-lectores: caminos que M1g dejo sin probar, con documentos y clases spec 16

Worktree `/tmp/kimun-s16chk-wt` (sin commit). Evidencia en `runs/subagents/2026-11-15-spec16-lectores/`: `matrix.md`, `lint.txt`, `directed.txt`, `mutation.txt`, `oracle.txt`.

## Matriz (sin arreglar, luego arreglada)
Fixture: clase `ReportDoc` (base `BaseDoc`) y documento `r`, en dos estados (intacto y `:superseded` por redefinir la base). Corrieron unos 100 comandos CLI de `main.cljc` y `commands/*.cljc`, y 47 rutas HTTP con `serve` en 127.0.0.1 y puerto efimero. `matrix.md` es el estado final con la columna «antes». Cada comando corrio sobre un store fresco.

Revientan o callan ANTES del arreglo, todos por leer `:template` o `(:fields d)`:
- `stores update` con el documento `:superseded`: NPE. Con el documento intacto ya respondia.
- `models field-add`: NPE. `models field-remove`: error declarado pero engañoso («no tiene el campo»).
- `models template`: ok con `nil`. Calla.
- `extract`, `render` y `validate` con el nombre de una clase del store: NPE.
- `fields schema ReportDoc`: ok con `fields []`. `fields schema ReportDoc title`: «no declara title». Callan.
- HTTP: `/schema` (`fields []`), `/render?model=` (`template nil`), `/models/fields-add` (500), `/document/segments` (500) y `/extract/segments` (500).
- `include build` en un repo git: fallaba declarado pero FALSO (`docs/structural-noise`, el chequeo de ruido de la plantilla v1). No se podia construir ningun documento spec 16.

Respondian bien desde el principio, intactos y `:superseded`:
- CLI: `fields get|query`, `models list|show|history|check`, `docs compose|recover|render|anchors|marks|show|list|status`, `edges *`, `graph *`, `semantic *`, `corpus refresh`, `rebuild-indexes`, `journal *`, `stores pack|unpack|unreachable|verify|health|sense|heartbeat`, `find` (con `st.{ReportDoc}.r.title`). `corpus rank` da `index/not-indexed` declarado hasta correr `corpus refresh`.
- HTTP: `/graph`, `/document`, `/ast`, `/fields`, `/extract`, `/render?doc=`, `/sections`, `/find` y las demas.

## Arreglos (cambio minimo, misma regla de M1g)
- `src/kimun/surface/fields.cljc`: `with-schema-fields` da a una clase spec 16 los campos de todo su linaje (`subgraph/compose-fields`, ancestro primero). Lo usan `fields schema` (`cli/commands/fields.cljc`) y `/schema` (`http/views.cljc`).
- `src/kimun/cli/commands/models.cljc`: `field-add`, `field-remove` y `template` (lectura y escritura) sobre una clase spec 16 dan `cli/usage` que dice «spec 16» y apunta a `models define -f`.
- `src/kimun/cli/commands/direct.cljc`: `extract`, `render` y `validate` sobre una clase spec 16 dan `cli/usage` «spec 16».
- `src/kimun/surface/http/views.cljc` y `routes.cljc`: `/document/segments`, `/extract/segments` y `/render?model=` dan 400 `cli/usage` «spec 16» en vez de 500 o `template nil`.
- `src/kimun/cli/commands/include.cljc`: los chequeos de ruido y de ida-vuelta del `include build` se saltan para spec 16 (leen la plantilla v1), igual que M1g hizo en `docs track`.
- `src/kimun/cli/commands/stores.cljc`: `stores update` omite, diciendolo, a un documento spec 16 `:superseded` por clase redefinida.

Documentos y modelos v1 no cambian: las suites v1 corren sin cambios y no se ajusto ninguna prueba existente.

## `stores update` sobre un spec 16 `:superseded` (punto 3)
- Hoy con v1: `stores update` lo migra solo. `move-text` vuelve a renderizar el payload registrado bajo el contrato nuevo y reescribe el archivo. Eso necesita el renderizador.
- Para spec 16 no hay renderizador (M2). Releer el archivo bajo la clase nueva puede cambiar lo que ancla (titulo «Hello World» a «Hello»), y eso es una decision, no una reparacion sin decision.
- Ahora: lo omite. `:updated []` y `:skipped [{:document "r" :category :contract-moved :action "kimun docs update r" :reason "spec 16 class redefined: no renderer migrates the text (M2); re-anchor it with `docs update`"}]`. No toca el archivo ni el documento, que sigue `:superseded` y `stores health` sigue `:degraded` con su accion. `docs update r` sigue siendo la via explicita (probado).
- Decision que queda para el dueño: si `stores update` debe releer el archivo para spec 16 como «reparacion sin decision». Yo lo dejo fuera.

## `include build` con una clase spec 16 (punto 4)
- Con el arreglo funciona en un repo git temporal. Un `include build` deja el documento `:intact` con `:class` y `:ancestors` registrados, y `include check` da ok.
- `include build --rebuild` arrastra la identidad (`carried 1`, `units-carried 2` con campo propio y heredado) y conserva `:ancestors`, con el documento `:intact`.
- NO probe `--rebuild` cuando la base cambio entre el build y el rebuild. Ahi el documento se re-ancla de cero con los ancestros vigentes, y no revise que se arrastre bien. Queda sin probar y no lo toque.

## Pruebas (5 funciones, 0 fallos en las dirigidas)
Archivo nuevo `test/kimun/cli/spec16_readers_test.cljc`. Reutiliza el fixture de `spec16_docs_test`.
1. `no-reader-breaks-or-stays-silent-on-a-spec16-store`: tabla de 21 lectores x 2 estados sin `cli/unexpected`, y 6 comandos que deben dar `cli/usage` con «spec 16».
2. `fields-schema-reads-the-composed-fields-of-a-class`: `["title" "summary"]` en orden heredado primero, `BaseDoc` solo `title`, fila de un campo.
3. `stores-update-skips-a-superseded-spec16-document-and-says-so`: omitido con motivo, archivo y payload intactos, `docs update` posterior lo deja `:intact`.
4. `http-reads-a-spec16-class-and-refuses-the-segments`: con servidor real, `/schema` compone, segments y `/render?model=` dan 4xx «spec 16», `/document` y `/graph` dan 200, en los dos estados.
5. `include-build-tracks-a-spec16-document-and-a-rebuild-carries-it`: repo git, build, check y rebuild con identidad arrastrada.

Los numeros (`directed.txt`, `lint.txt`, `oracle.txt`):
- Dirigidas: 187 pruebas, 1591 aserciones, 0 fallos y 0 errores. Son las pedidas: las 5 nuevas, `spec16_docs`, `fields`, `models`, `sections`, `include`, `model_carry`, `identity_carry`, `stores`, `typed_docs`, `find` (todas CLI) y las 6 de `surface/http/`.
- `bb lint`: ok, 110 archivos. `bb oracle`: 9 filas, 0 diferencias.
- No corri `bb test` completo.

Mutacion (`mutation.txt`):
- Con `src/` a HEAD, la prueba nueva da 47 fallos: 25 en la 1, 4 en la 2, 4 en la 3 y 14 en la 4.
- Con solo `include.cljc` a HEAD, la 5 falla (10 lineas FAIL/ERROR).
- Restaurado con parche, sin `git stash`, y `diff -r` vacio.

## Limites y puntos abiertos
- `GET /stores/check` responde 500 con un documento `:superseded`. Es el error declarado `store/invalid` («1 contract moved»). No comprobe si con un documento v1 `:superseded` responde igual. Se ve igual en `/health` (`degraded`). Lo dejo, no es de spec 16.
- `models list` cuenta solo los campos propios de la clase (`fields 1` para `ReportDoc`). Es el criterio v1 de campos declarados, no lo cambie.
- `fields get` da `owning_section nil` para spec 16 (no hay plantilla). Es de M1g.
- Esta tanda no tocó `docs/v2/06`. Las filas de `models field-add|field-remove|template`, `extract|render|validate`, `fields schema`, `stores update` e `include build` quedan sin actualizar; las propongo para el coordinador.
- `migrate --from-v1` no aplica a un documento spec 16. Solo verifique que con una ruta inexistente da un error declarado.
- No toque `docs.cljc`, `parse_contract`, `diagnostics`, `signals`, `selector*`, `subgraph` ni el kernel. Ningun arreglo lo exigio.
