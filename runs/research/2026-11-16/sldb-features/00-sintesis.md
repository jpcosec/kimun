# Features de sldb: síntesis de los siete informes (2026-11-16)

Escrita por el coordinador (Claude) sobre los informes `01` a `07` de esta carpeta, hechos por siete
subagentes en solo lectura sobre `/home/jp/proyectos/SLDB_1/sldb` en `34b26a50` (el remoto ya iba en
`b60b02b7`). No es un diseño. Lo que dice «comprobé» lo verifiqué yo contra el repositorio; lo demás
viene de los informes, que marcan cada afirmación como verificada `[V]` o inferida `[I]`.

Comprobé por muestreo: 16 reglas en `deskops/desk/lifecycle/` (14 `on_transition`, 2 `on_write`), 13
de sus condiciones con `^subject` y ninguna con `rel:`, clausura, `count`, `none`, regex ni
sustitución; 189 nombres en `__all__` de `sldb.api` (los informes del 15 decían 183); 627 commits
desde el 2026-10-01.

## 1. Qué es sldb hoy

Un producto con dos capas (ADR `sldb-absorbs-kgdb.md`, 2026-09-20):

- **Texto.** El documento Markdown es la fuente. Un modelo dice cómo se lee y cómo se escribe.
- **Grafo.** Nodos y aristas derivados de los documentos; nunca es una segunda verdad. Sobre él corre
  un solo lenguaje de predicados.

Tamaño: unas 52 000 líneas, más de 120 subcomandos de CLI, unas 45 rutas HTTP, 14 herramientas MCP,
189 nombres de API. Creció sobre todo en tres ráfagas: 18 de octubre (150 commits), 6 de noviembre
(149) y 16 de noviembre (64).

## 2. El modelo: una clase que amarra once cosas (`02`, `01`)

| Pieza | Qué hace | Uso real (49 modelos de consumidores) |
|---|---|---|
| Campos con descripción obligatoria | esquema del documento | todos |
| `__template__` | formato del archivo: sirve para leer **y** para escribir (ida y vuelta) | casi todos |
| `__semantics__` | etiquetas por ejes con jerarquía (`type.knowledge.atom`) | casi todos |
| `__references__`, `__reference_models__` | campos que son aristas `references` | 10 y 1 |
| `__containment__` | aristas `contains`; no toca el texto | 2 |
| `__compositions__` | un campo que se arma leyendo otros documentos o campos | 2 |
| `__doc_anchors__` | referencia fijada por hash; estado intacta, cambiada o perdida | 1 (las reviews) |
| `__contract__` | qué campos cuentan para el hash que fijan las anclas | 1 (las tareas) |
| `__authority__` | autoridad del documento (fuente, escrito, generado, caché) | 1 |
| `__indexed__` | qué campos se proyectan al grafo | 0 |
| Validadores pydantic | invariantes de un solo documento | 18 de campo, 6 de modelo |

- La plantilla usa cuatro marcadores (`rev`, `optrev`, `render`, `py`) y cuatro rasgos con efecto
  (`list`, `dict`, `table`, `table[a,b]`). Hay rasgos escritos en plantillas que nadie lee.
- «Contrato» nombra tres cosas distintas: hash de la clase, hash de un documento y firma de un shard.
- Lo que cuenta como familia en reglas y consultas es la herencia de clases; `__family__` no lo lee
  nada dentro de sldb.
- Casi todos los validadores miran un solo documento. La excepción es `TaskDoc`, que al validar lee
  los archivos de ciclo de vida de deskops.

## 3. El grafo (`04`)

- **Ocho tipos de nodo:** documento (tipado por su modelo), sección, modelo, campo, etiqueta, tipo de
  relación, ancla (alias) y store. Un `RelationDoc` no es nodo: solo produce una arista.
- **Quince tipos de relación incorporados**, más los que escribe el usuario como `RelationDoc`.
- **Por documento se guarda** un shard: sus nodos, sus aristas y la proyección de sus campos
  escalares. **Al componer se calcula, sin guardar,** todo lo que cruza documentos: resolver
  referencias, heredar condiciones, aristas inversas, descartar colgantes, estado de las anclas.
- **Archivos y menciones no están en el índice.** Viven en el «project graph», un grafo aparte que
  deskops arma en proceso.
- **Costura única:** `GraphBackend` (`load`, `eval`, `analyze`), con networkx detrás y once
  operaciones de análisis.

## 4. Predicados, reglas, anclas, etiquetas y vistas (`05`)

- Un solo lenguaje desde el 2026-11-06. Lo evalúan los `--where`, `select_documents`, las reglas, las
  condiciones de las aristas y las secciones de las vistas.
- **Lo que deskops usa de verdad es poco:** 16 reglas, 3 condiciones de arista y 1 vista. Caminos
  `^subject`, `depends_on` y `references`; `any`/`all`; igualdad; `anchor_state = "intact"`.
- **Transiciones:** el ciclo de vida de deskops son 3 aristas entre rituales, con condición y efecto.
- **Anclas a documento:** `doc:Modelo:nombre@sha256:…`; se fijan al guardar y su estado se calcula al
  componer. **Anclas a código:** `code:ruta::nombre`, con cuatro huellas por símbolo y ocho estados.
- **Etiquetas:** namespaces declarados en `store.yaml`, comprobados como reglas incorporadas.
- **Vistas:** un `ViewDoc` con parámetros y secciones; su revisión sirve de ETag.

## 5. El store (`03`)

- Cadena de hashes: texto → payload y hash por campo → lista del modelo → modelo → store.
- **Dónde recalcula de más:** `stores update` lee y hashea todo documento en cada corrida;
  `stores check` vuelve a extraer todo; el conjunto de documentos sucios vive solo en memoria del
  proceso. Los hashes por campo no se usan para recalcular.
- Fuera de una transacción las escrituras no son atómicas (archivo escrito antes del chequeo o del
  lock). `transaction` y `apply_batch` sí deshacen (entraron el 2026-11-16).
- Hay estado declarado a mano dentro de la caché ignorada por git (`semantic_dag.yaml`).

## 6. Lo que sirve a un solo consumidor (`06`)

Notaciones y specs de diagramas (IR), corpus y matching (pron). De toda esa área deskops solo importa
`check_doc_anchors`. sldb tiene dos lectores de Python que no coinciden (`sldb.code` con tree-sitter
y `sldb.selfdoc` con `ast`); solo el primero lee TS y JS, desde el 2026-11-16.

## 7. Documentación desfasada y código muerto (todos los informes)

- `current-cli-tree.md` (septiembre) omite unos 45 subcomandos y 11 grupos; el documento de `serve`
  nombra 5 rutas de unas 45.
- Dos documentos de arquitectura describen como pendiente algo ya hecho (federación de modelos) o
  como vigente algo cancelado (retiro de aristas).
- `incomplete`, el tercer valor del lenguaje de predicados, es inalcanzable: nada marca una arista
  como no disponible.
- Cáscaras vacías en la API (`journal`, `verify_journal`), `undo_changes` declarada y no servida,
  residuos de kgdb en los tipos de nodo, copias vivas en spec2viz de módulos ya migrados.
- El parámetro `actor` se acepta y se ignora.
- 46 tareas abiertas en el desk de sldb, todas en `draft`, varias con trabajo ya integrado.

## 8. Lo que los informes no cubren

- Ningún subagente ejecutó nada: todo es lectura de código. Las pruebas se citan por nombre de
  archivo; casi nadie las abrió.
- No se abrieron `field_kinds`, `source_guard`, `composition_targets` ni el interior de `run_view` y
  de la caché de `compose_edge_index`.
- Los consumidores fuera de `SLDB_1` (TraderBot, legos y otros) no se miraron.
