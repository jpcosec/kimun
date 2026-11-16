# Matriz de lectores sobre un store con una clase spec 16 (ReportDoc, base BaseDoc) y un documento `r`

Estado final (con los arreglos). Columnas: comando, intacto, `:superseded` (se redefinio la base), antes del arreglo (solo si cambio). Cada comando corrio sobre un store fresco. Limites: se clasifico por el tipo del error y a ojo el dato; "responde bien" para comandos que no leen el documento (stores pack, graph cycles...) significa que no revientan ni cambian por el documento.

## CLI

| comando | intacto | superseded | antes |
|---|---|---|---|
| `stores check` | responde bien | error declarado (store/invalid) |  |
| `stores verify` | responde bien | responde bien |  |
| `stores show` | responde bien | responde bien |  |
| `stores health` | responde bien | responde bien |  |
| `stores unreachable` | responde bien | responde bien |  |
| `stores unreachable --ids` | responde bien | responde bien |  |
| `stores pack` | responde bien | responde bien |  |
| `stores unpack` | responde bien | responde bien |  |
| `stores sense --once` | responde bien | responde bien |  |
| `stores heartbeat --beats 1` | responde bien | responde bien |  |
| `stores update` | responde bien | responde bien | REVENTABA solo con :superseded (NPE) |
| `stores links` | responde bien | responde bien |  |
| `stores reconcile` | responde bien | responde bien |  |
| `stores upgrade` | responde bien | responde bien |  |
| `docs list` | responde bien | responde bien |  |
| `docs status` | responde bien | responde bien |  |
| `docs show r` | responde bien | responde bien |  |
| `docs show r --ast` | responde bien | responde bien |  |
| `docs show r --format md` | responde bien | responde bien |  |
| `docs render r` | responde bien | responde bien |  |
| `docs compose r` | responde bien | responde bien |  |
| `docs recover r` | responde bien | responde bien |  |
| `docs anchors r` | responde bien | responde bien |  |
| `docs marks r` | responde bien | responde bien |  |
| `docs reanchor r r.md --reason x` | error declarado (cli/usage) | error declarado (cli/usage) |  |
| `docs update r` | responde bien | responde bien |  |
| `docs untrack r` | responde bien | responde bien |  |
| `docs delete r --yes` | responde bien | responde bien |  |
| `find Hello` | responde bien | responde bien |  |
| `find Hello --in semantic` | responde bien | responde bien |  |
| `find st.ReportDoc.r.title` | error declarado (find/empty) | error declarado (find/empty) |  |
| `find st.{BaseDoc+}` | responde bien | responde bien |  |
| `find st.{ReportDoc}.r.title` | responde bien | responde bien |  |
| `find st.{BaseDoc+}.r.title` | responde bien | responde bien |  |
| `find Hello --fuzzy` | responde bien | responde bien |  |
| `find --where title = "Hello" Hello` | responde bien | responde bien |  |
| `corpus rank Hello` | error declarado (index/not-indexed) | error declarado (index/not-indexed) |  |
| `corpus refresh` | responde bien | responde bien |  |
| `rebuild-indexes` | responde bien | responde bien |  |
| `edges show` | responde bien | responde bien |  |
| `edges check` | responde bien | responde bien |  |
| `edges node sldb://document/ReportDoc:r` | responde bien | responde bien |  |
| `edges nodes` | responde bien | responde bien |  |
| `edges types` | responde bien | responde bien |  |
| `edges init` | responde bien | responde bien |  |
| `edges rebuild` | responde bien | responde bien |  |
| `predicates list` | responde bien | responde bien |  |
| `predicates validate` | responde bien | responde bien |  |
| `graph list` | responde bien | responde bien |  |
| `graph get sldb://document/ReportDoc:r` | responde bien | responde bien |  |
| `graph cycles` | responde bien | responde bien |  |
| `graph order` | responde bien | responde bien |  |
| `graph components` | responde bien | responde bien |  |
| `graph central` | responde bien | responde bien |  |
| `graph isolated` | responde bien | responde bien |  |
| `graph similar sldb://document/ReportDoc:r` | responde bien | responde bien |  |
| `graph neighborhood sldb://document/ReportDoc:r` | responde bien | responde bien |  |
| `graph export` | responde bien | responde bien |  |
| `fields get r title` | responde bien | responde bien |  |
| `fields get r summary` | responde bien | responde bien |  |
| `fields schema ReportDoc` | responde bien | responde bien | CALLABA (fields []) |
| `fields schema ReportDoc title` | responde bien | responde bien | CALLABA (error "no declara title") |
| `fields schema BaseDoc` | responde bien | responde bien | CALLABA (fields []) |
| `fields query title` | responde bien | responde bien |  |
| `fields query summary --model ReportDoc` | responde bien | responde bien |  |
| `fields query title --where title = "Hello"` | responde bien | responde bien |  |
| `sections list r` | responde bien | responde bien |  |
| `sections find Summary` | responde bien | responde bien |  |
| `journal show` | responde bien | responde bien |  |
| `journal verify` | responde bien | responde bien |  |
| `semantic show` | responde bien | responde bien |  |
| `semantic tags` | responde bien | responde bien |  |
| `semantic export` | responde bien | responde bien |  |
| `models list` | responde bien | responde bien |  |
| `models show ReportDoc` | responde bien | responde bien |  |
| `models show ReportDoc --format md` | responde bien | responde bien |  |
| `models history ReportDoc` | responde bien | responde bien |  |
| `models history BaseDoc` | responde bien | responde bien |  |
| `models check` | responde bien | responde bien |  |
| `models check ReportDoc` | responde bien | responde bien |  |
| `models template ReportDoc` | error declarado (cli/usage) | error declarado (cli/usage) | CALLABA (ok con nil) |
| `models field-add ReportDoc --field x --type string --description d --dry-run` | error declarado (cli/usage) | error declarado (cli/usage) | REVENTABA (NPE) |
| `models field-remove ReportDoc --field summary --dry-run` | error declarado (cli/usage) | error declarado (cli/usage) | error declarado engañoso ("no tiene el campo") |
| `extract ReportDoc --input r.md` | error declarado (cli/usage) | error declarado (cli/usage) | REVENTABA (NPE) |
| `render ReportDoc --data d.json` | error declarado (cli/usage) | error declarado (cli/usage) | REVENTABA (NPE) |
| `validate ReportDoc --input r.md` | error declarado (cli/usage) | error declarado (cli/usage) | REVENTABA (NPE) |
| `include check` | error declarado (include/invalid) | error declarado (include/invalid) |  |
| `include status` | error declarado (include/invalid) | error declarado (include/invalid) |  |
| `include build` | error declarado (include/invalid) | error declarado (include/invalid) | (en repo git) falla declarado FALSO: structural noise |
| `include gitignore` | error declarado (include/invalid) | error declarado (include/invalid) |  |
| `migrate --from-v1 /nonexistent --dry-run` | error declarado (migrate/not-v1-store) | error declarado (migrate/not-v1-store) |  |
| `adapters list` | responde bien | responde bien |  |
| `fields set r title "X"` | error declarado (cli/usage) | error declarado (cli/usage) |  |
| `fields unset r title` | error declarado (cli/usage) | error declarado (cli/usage) |  |
| `fields append r title "X"` | error declarado (cli/usage) | error declarado (cli/usage) |  |
| `fields clear r title` | error declarado (cli/usage) | error declarado (cli/usage) |  |
| `fields clean r title` | error declarado (cli/usage) | error declarado (cli/usage) |  |
| `sections fields r summary` | error declarado (cli/usage) | error declarado (cli/usage) |  |
| `docs create ReportDoc z --set title=a` | error declarado (cli/usage) | error declarado (cli/usage) |  |
| `docs update r --set title=a` | error declarado (cli/usage) | error declarado (cli/usage) |  |

## HTTP (serve en 127.0.0.1, puerto efimero)

| ruta | intacto | superseded | antes |
|---|---|---|---|
| `:get /health` | 200 | 200 |  |
| `:get /stores` | 200 | 200 |  |
| `:get /stores/check` | 200 | 500 |  |
| `:get /stores/links` | 200 | 200 |  |
| `:get /stores/sense` | 200 | 200 |  |
| `:get /stores/health` | 200 | 200 |  |
| `:get /schema` | 200 | 200 | CALLABA (fields []) |
| `:get /graph` | 200 | 200 |  |
| `:get /lint` | 200 | 200 |  |
| `:get /ast?doc=r` | 200 | 200 |  |
| `:get /docs` | 200 | 200 |  |
| `:get /docs/render?name=r` | 200 | 200 |  |
| `:get /document?id=r` | 200 | 200 |  |
| `:get /document/ir?id=r` | 200 | 200 |  |
| `:get /document/segments?id=r` | 400 | 400 | REVENTABA (500) |
| `:post /extract/segments` | 400 | 400 | REVENTABA (500) |
| `:get /fields?doc=r` | 200 | 200 |  |
| `:get /fields?doc=r&path=title` | 200 | 200 |  |
| `:get /extract?doc=r` | 200 | 200 |  |
| `:get /render?doc=r` | 200 | 200 |  |
| `:get /render?model=ReportDoc` | 400 | 400 | CALLABA (template nil) |
| `:get /sections?doc=r` | 200 | 200 |  |
| `:get /find?q=Hello` | 200 | 200 |  |
| `:get /edges` | 200 | 200 |  |
| `:get /edges/nodes` | 200 | 200 |  |
| `:get /graph/cycles` | 200 | 200 |  |
| `:get /graph/components` | 200 | 200 |  |
| `:get /graph/central` | 200 | 200 |  |
| `:get /graph/isolated` | 200 | 200 |  |
| `:get /similar?id=sldb://document/ReportDoc:r` | 200 | 200 |  |
| `:get /graph/neighborhood?node=sldb://document/ReportDoc:r` | 200 | 200 |  |
| `:get /corpus/rank?q=Hello&refresh=1` | 200 | 200 |  |
| `:get /models` | 200 | 200 |  |
| `:get /models/detail?model=ReportDoc` | 200 | 200 |  |
| `:post /models/validate` | 200 | 200 |  |
| `:post /models/fields-add` | 400 | 400 | REVENTABA (500) |
| `:post /models/fields-remove` | 400 | 400 |  |
| `:post /models/template-edit` | 400 | 400 |  |
| `:get /journal` | 200 | 200 |  |
| `:get /journal/verify` | 200 | 200 |  |
| `:post /save` | 400 | 400 |  |
| `:post /docs/create` | 400 | 400 |  |
| `:post /docs/track` | 422 | 422 |  |
| `:post /docs/ingest` | 200 | 200 |  |
| `:post /docs/untrack` | 200 | 200 |  |
