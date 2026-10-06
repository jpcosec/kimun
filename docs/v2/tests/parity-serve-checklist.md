# Paridad de `serve` — rutas pendientes (checklist)

> Lo que falta para cubrir `tests/test_serve.py` (52), `test_serve_corpus_similar.py` (6) y
> `test_serve_edges_docs.py` (3) de sldb v1. El esqueleto (`kimun.surface.http`: tabla de
> rutas como datos, auth por bearer, CORS, mapeo exit→status, `command-route`) ya existe;
> cada ruta de abajo es **una entrada** en `kimun.surface.http/routes` sobre el comando de
> CLI correspondiente. Los números son líneas del archivo de v1; la respuesta exacta está
> ahí. En v1 el cuerpo era el payload desnudo; en kimun va bajo `data` del envelope y los
> errores son el envelope de fallo. Los status coinciden con v1.

Cómo agregar una ruta:
`{:method :get :path "/fields" :handler (command-route ["fields" "get"] :query {:doc "--doc" :path "--path"} :required [:doc])}`.
Mapeo: exit 0→200, 1→404, 2→409, 3→422, 4→409, 5→500, 6→400, 7→403.

## A. Modelos y documentos con campos (necesita B1, B2)
- `test_serve_endpoints_use_real_store` (27): `GET /schema` → modelos con `id, model_ref, fields[{name kind required description}], containment, references, semantics, template`; `GET /graph` → `documents[{id model_name path version payload semantic_tags}]`, `version` = sha256 del payload JSON con claves ordenadas; `POST /save {doc, payload}` → 200 `{ok, doc}` y reescribe el archivo; doc desconocido → 404.
- `test_batch_save_create_update_delete` (185): `POST /save` por lotes → `{ok, saved [...]}`.
- `test_batch_save_stale_expected_conflict` (211): `expected` viejo → 409, payload intacto.
- `test_batch_save_duplicate_create_rejected` (226): 409 documento existente; 409 archivo destino existente.
- `test_batch_save_invalid_payload_422` (245): 422, error de validación.
- `test_batch_save_partial_failure_reports_completed` (260): 500 con `completed`.
- `test_batch_save_delete_removes_the_file_it_created` (1091): 200 y el archivo desaparece.
- `test_schema_includes_model_graph_dunders` (361): `containment`, `references`, `semantics` del modelo.
- `test_schema_declares_models_that_cannot_load` (1049), `…reports_no_unavailable…` (1079), `…degrades_per_model…` (1313): `unavailable[{model, model_ref}]` por modelo que no carga, sin perder la respuesta.
- `test_graph_declares_models_whose_documents_are_missing` (1340), `…no_unavailable…` (1370).
- `test_models_endpoints_use_native_api` (442): `GET /models`, `/models/detail?model=`, `/models/fields-add`, `POST /models/validate`, `POST /models/promote`.
- `test_models_endpoints_resolve_relative_imports_by_package_name` (771).
- `test_lint_endpoints_use_real_api` (510): `GET /lint` → `count`, `problems[{kind severity doc detail}]`. (`lint` se retira en `06 §8`; decidir si la ruta queda sobre `stores check`.)
- `test_fields_list_and_single_path` (867): `/fields?doc=` → `doc, model, fields`; `?path=title` → `value`.
- `test_reader_routes_validate_params` (935): falta `doc` → 400; doc inexistente en `/sections`, `/ast`, `/render` → 404.
- `test_extract_and_render` (919): `/extract?doc=` → `{doc, payload}`; `/render?doc=` → `markdown`; `/render?model=` → `template`.

## B. IR del documento, create, track, untrack (necesita B2)
- `test_document_endpoint_returns_full_ir` (535), `…ir_endpoint_returns_only_ir` (557), `…unknown_doc_returns_404` (571), `…missing_id_returns_400` (585), `…unbuildable_ir_returns_422` (596): `/document?id=`, `/document/ir?id=`.
- `test_docs_create_tracks_and_returns_version` (614), `…duplicate_conflict` (632, 409), `…invalid_payload_422` (643), `…target_file_conflict` (656, 409), `…unregistered_model_404` (668): `POST /docs/create`.
- `test_docs_track_existing_file` (680), `…missing_path_404` (696): `POST /docs/track`.
- `test_docs_untrack_keeps_file_on_disk` (708), `…missing_doc_404` (725): `POST /docs/untrack`.

## C. Secciones y AST (necesita B2)
- `test_sections_from_ir_context_index` (888): `/sections?doc=` → secciones con `path "my-book/setup"`.
- `test_ast_exposes_full_ir_and_sections` (902): `/ast?doc=` → `name, model, payload, sections`.

## D. Find (necesita C1)
- `test_find_physical_search` (959), `test_find_semantic_search` (972): `/find?q=&in=physical|semantic&type=doc`.
- `test_find_where_predicate_filters_and_invalid_where_is_400` (984).
- `test_find_limit_and_select` (1000): `limit`, `select`.
- `test_find_flags_and_param_validation` (1015): `fuzzy`; `in`, `type`, `limit` inválidos → 400; falta `q`.

## E. Aristas (necesita C2)
- `test_edges_endpoints_use_real_api` (100): `/edges?from=`, `?to=`, `&relation=`, `/edges/node?id=`, `/edges/nodes?type=`.
- `test_edges_lists_document_edges_and_filters_by_id` (edges_docs 49): `/edges`, `/edges?id=`.
- `test_docs_creates_a_tracked_document` (edges_docs 63), `test_docs_reports_each_failure_with_its_status` (edges_docs 77): `POST /docs`.

## F. Grafo (necesita C2)
- `test_graph_endpoints_use_real_api` (142): `/graph/cycles`, `/graph/components`, `/graph/central?limit=`, `/graph/isolated`, `/graph/neighborhood?node=`, `/graph/path?source=&target=`.

## G. Corpus y similares (necesita C1/C2)
- `test_rank_without_index_is_an_explicit_409_not_an_empty_list` (24): `/corpus/rank?q=` sin índice → 409 `kind "not_indexed"`.
- `test_rank_refresh_builds_the_index_then_plain_calls_read_it` (35), `test_rank_among_and_fields` (52), `test_rank_rejects_bad_parameters_with_400` (64).
- `test_similar_lists_nodes_sharing_targets` (75), `test_similar_reports_missing_and_unknown_id` (89): `/similar?id=&limit=`.

## H. Segmentos (necesita T1/B2)
- `test_document_segments_returns_literals_and_field_spans` (1121), `…unknown_doc_returns_404` (1143), `…missing_id_returns_400` (1154), `…unrenderable_doc_returns_422` (1165): `/document/segments?id=`.
- `test_extract_segments_locates_fields_in_raw_markdown` (1193), `…reports_text_the_template_does_not_explain` (1217), `…rejects_a_malformed_body_with_400` (1234), `…distinguishes_unknown_model_from_bad_markdown` (1250): `POST /extract/segments {model, markdown}`.

## Ya cubierto por el esqueleto
`test_serve_auth.py` 5/5; servidor: salud, ruta desconocida 404, método 405, JSON inválido 400, CORS, content-type; `/stores`, `/stores/check`, `/stores/links`, `/docs`, `/docs/render`, `/journal`, `/journal/verify`, `POST /docs/ingest`.
