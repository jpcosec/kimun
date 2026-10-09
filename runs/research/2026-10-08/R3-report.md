# R3 — Spike: lenguaje de selección sobre el AST (`16-modelos-como-subgrafos`)

Estado: **completo**. Solo lectura sobre `/home/jp/proyectos/kimun`; todo el trabajo en
`/home/jp/.cache/spikes/R3/`. Regla de memoria cumplida: **todos** los comandos `bb` corrieron con
`memcap` (default 3 GB), en streaming (un documento a la vez, solo contadores + primeros 5 ejemplos
de discrepancia). RSS pico observado **254 MB** (Datalevin); ningún run murió por OOM (exit 137).

Pregunta (documento 14 §9, decisión 28; lenguaje de selección, decisión 39): un modelo pasa a ser
**símbolo-tipo + selector + proyecciones**. ¿Con qué lenguaje se escriben los selectores? Se
evaluaron los tres candidatos con el mismo banco de pruebas.

---

## 1. Banco de pruebas

- 5 modelos: `AtomDoc` (219 docs), `ChecklistDoc` (189), `TaskDoc` (18), `RitualDoc` (1), `PillDoc` (1).
  = **428 documentos** del desk (de los 543). Cubren frontmatter YAML, `optrev`, sección opcional,
  listas, sección final y `render`.
- Referencia (ground truth): `kimun.surface.models.extract/extract` con el descriptor.
  Línea base sobre el desk completo: **543 docs, 542 ok, 1 error** (`desk/tasks/Board.md`,
  `TaskDoc` sin `status`), 165 ms.
- Comparación: se llevan las dos salidas a `descriptor/finalize` (defaults + tipos) y se comparan
  payloads campo a campo.
- Substrato medido por candidato: `blocks/parse` (vista v1 con `:map`), `markdown.ast/parse` (AST
  neutro), y CST del pod tree-sitter.

**Hallazgo de substrato (transversal):** ninguna vista existente trae a la vez **posiciones +
anidamiento + tabla**. La vista v1 (`models/blocks`) tiene spans y tablas pero **aplana las listas
anidadas**; el AST neutro conserva el anidamiento pero **no tiene spans** y su tabla es `:opaque`;
el CST sí tiene los tres (byte/line + `section`/`list`/`pipe_table`). Un selector debe declarar su
substrato.

---

## 2. Tabla candidato × criterio

| criterio | 1. Datalog sobre AST-triples | 2. Patrones EDN | 3. Consultas tree-sitter |
|---|---|---|---|
| **Expresividad** (6 capacidades) | 6/6, pero 3 con post-proceso en el host (regex, comparación de orden, spans) y YAML no expresable | 6/6 nativo sobre la vista v1 (c4 necesita el AST neutro) | 6/6 nativo (regex con `#match?` dentro de la query) |
| **Correctitud** (428 docs) | **423/428 = 98.8 %** (idéntico a TS; el Datalog localiza y el host ensambla) | **428/428 = 100 %** (por construcción: el selector *es* la receta) | **423/428 = 98.8 %** |
| **Rendimiento** (428 docs) | in-process **343 ms** (p50 1 ms), 92 MB; Datalevin **0.47 ms/doc** transact + **0.55 ms/doc** query, 254 MB | **100 ms** (p50 0 ms), 89 MB | **2939 ms** (p50 5 ms), 95 MB + pod |
| **Portabilidad** | query = data, pero flavor de motor (Datalevin), y necesita host para YAML/regex/spans | EDN puro → JSON; lo evalúa un intérprete de ~90 líneas; legible por humano/LLM | S-expresiones = data; requiere runtime tree-sitter + gramática; `#match?` lo evalúa el motor |
| **Herencia** (P3/P12) | hijo = reglas del padre ∪ reglas propias (demostrado) | hijo = `(into pasos-padre pasos-hijo)` (demostrado); reindexar `:outer-index` para la receta | hijo = queries del padre ∪ queries propias (demostrado) |
| **Tamaño selector** (5 modelos) | 0.6–2.1 KB | 1.0–4.4 KB | 0.3–1.8 KB |

Las 5 discrepancias de Datalog y TS son **las mismas** y son peculiaridades de v1:
- 3 × `AtomDoc` sin `## Answer`: v1 captura desde el inicio del documento (bucle de `section-body`
  con `search` sin avanzar) y el selector correcto no produce `answer` (campo requerido).
  [git-orchestration-in-python, immutable-append-only-database, relation-ast-extensibility]
- 2 × `TaskDoc` con `## Done When` vacío: v1 devuelve `"## Done When\n\n_…_"` (incluye el heading);
  el selector devuelve `""`. [task-specify-diagram-traceability-contract, task-specify-specyaml-schema-contract]

## 3. Matriz de capacidades (evidencia ejecutada)

| capacidad | EDN (`caps_edn.clj`) | tree-sitter (`caps_ts.clj`) | Datalog (`caps_dl.clj`) |
|---|---|---|---|
| C1 secuencia exacta | pasos `:block` en orden | captura de `section` en orden | join `kind`+`order`, orden en host |
| C2 sección opcional (`optrev`) | `:want :opt` (presente/ausente) | 0/1 capturas | 0/1 filas |
| C3 "heading /regex/ donde esté" | `:want :find :where {:re …}` | `(#match? @title "…")` nativo | **GAP**: Datalog no tiene regex → filtro en host |
| C4 lista anidada | AST neutro (v1 aplanan) | `(list (list_item) @item)` + profundidad por columna | requiere AST neutro + recursión `:parent` |
| C5 tabla tras heading | `:want :table` (tabla opaca en el AST) | `(section (atx_heading) (pipe_table) @tbl)` | joins + comparación de orden en host |
| C6 captura valor + path | `:path`/`:lines` de la vista v1 | `start_byte`/`start_point` del capture | `:order` = path; spans como atributos |

---

## 4. Selectores escritos para los 5 modelos (3 lenguajes)

- **EDN** — `selectors/edn/{AtomDoc,TaskDoc,RitualDoc,ChecklistDoc,PillDoc}.edn`.
  El selector es el *recipe* del template (vector de cláusulas; p. ej. AtomDoc:
  `[{:handler :yaml :markers [{:key "id" …} {:key "provenance" :marker {:kind "optrev"}}]}
    {:handler :text :anchor true …} {:capture :section-body :props ["answer"]}]`).
  Intérprete: `edn_engine.clj` (adaptación de `extract.cljc`).
- **tree-sitter** — `selectors/ts/*.edn`. Cada archivo lleva el mapa `{:yaml :h1 :sections}` y las
  queries resueltas, p. ej. TaskDoc:
  `((section (atx_heading (inline) @title)) @sec (#match? @title "^Goal$"))` y, para listas,
  `((section (atx_heading (inline) @title) (list (list_item (paragraph (inline) @item)))) (#match? @title "^Validation$"))`.
  Motor: `ts_engine.clj`.
- **Datalog** — `selectors/datalog/*.edn`. Esquema de datoms
  `[:kind :tag :text :order :line-start :line-end :level :parent]` y una query por campo,
  p. ej. `[:find ?h ?o :where [?h :kind "heading"] [?h :text "Goal"] [?h :order ?o]]`, con
  `:post` describiendo lo que el host debe hacer. Motor: `dl.clj` (semi-naive in-process).

Herencia (demostrada en `inheritance.clj`): EDN `child = (into parent-steps extra)` captura
`[:answer :related]`; Datalog/TS `child = parent ∪ extra` (`:extends true`).

---

## 5. Rendimiento y motor (memoria)

```
EDN ENGINE:   {:docs 428 :fields 2994 :ms 100  :p50 0 :hwm 89404kB}
PARITY EDN:   {:n 428 :same 428 :diff 0   :ms 235  :hwm 89MB}
PARITY TS:    {:n 428 :same 423 :diff 5   :ms 2939 :p50 5 :hwm 95MB}   (+ pod 3.2 MB)
PARITY DL:    {:n 428 :same 423 :diff 5   :ms 343  :p50 1 :hwm 92MB}
IN-PROCESS:   {:docs 428 :heading-rows 2053 :ms 116 :hwm 96MB}
DATALEVIN:    {:docs 428 :datoms 4414 :transact-ms 200 (0.47/doc)
               :query-ms 235 (0.55/doc) :datoms-counted 26484 :hwm 112MB}  /usr/bin/time: 254 MB max RSS
```

Notas del pod Datalog (R1 confirmado y extendido):
- El pod **1.2.0 no está publicado en el registry**: `pods/load-pod 'huahaiy/datalevin "1.2.0"`
  → `FileNotFoundException .../manifests/huahaiy/datalevin/1.2.0/manifest.edn`. Solo existe 1.1.0
  (~278 MB de binario). No se pudo evitar el bug de R1 por bump de versión.
- En 1.1.0: regla recursiva *standalone* funciona (`(anc ?x ?y)`), pero una query **sembrada**
  (cláusula ligada antes de la llamada a la regla) lanzó `ClassCastException: Long cannot be cast
  to String` (R1 reportó un NPE); y `d/clear` seguido de `d/q` lanzó NPE en `datalevin.db/cache-token`.
  El respaldo in-process no tiene estos problemas.

---

## 6. Portabilidad

- **EDN**: 1.0–4.4 KB por modelo, EDN/JSON puro; lo evalúa un intérprete de ~90 líneas. Un plugin
  Python/TS puede **emitirlo como datos** sin ejecutar Clojure. Máxima legibilidad humano/LLM.
- **tree-sitter**: 0.3–1.8 KB; la query es data, pero evaluarla exige el runtime tree-sitter + la
  gramática (`tree-sitter-md`); los predicados `#match?` los evalúa el motor. Emitible por un plugin,
  no evaluable sin el motor.
- **Datalog**: 0.6–2.1 KB; la query es data pero *flavor*-específica (Datalevin) y, medido, no
  resuelve sola ninguna de las tres operaciones críticas (regex, YAML, spans): el host siempre
  participa.

---

## 7. Recomendación (una)

**Adoptar patrones EDN como lenguaje de selección**, formalizando el *recipe* del template como el
selector de datos (`símbolo-tipo + selector + proyecciones`). Es el único candidato con **paridad
100 %** con la extracción v1, el más barato (100 ms/428 docs) y el único portable como datos puros
sin motor externo. **Usar tree-sitter como parser de la capa M** (spans, `section`/`list`/`pipe_table`,
regex `#match?`) y como substrato cuando el selector necesite anidamiento o tabla estructurada;
**Datalog queda para la consulta de G** (decisión 30), no para seleccionar sobre el AST.

Reservas:
1. **Substrato único pendiente**: v1 aplana listas anidadas; el AST neutro no tiene spans y su tabla
   es opaca. Hay que decidir si el selector lee la vista v1 (spans) o el CST (todo), y cómo anclar
   la primera.
2. **Quirks de v1 heredados**: los 5 casos clasificados (sección final ausente/vacía) hoy son el
   contrato de paridad; si se adopta el selector, o se replican a propósito o se corrigen con una
   decisión registrada (recomendado: corregir, declarándolo en `07`).
3. **tree-sitter** ata el regex y el anidamiento al pod nativo (3–4 MB por plataforma; R2).
4. **Datalog 1.1.0** tiene bugs de reglas sembradas y `1.2.0` no está en el registry; el respaldo
   in-process es el camino estable.

---

## 8. Qué cambiaría en `07` si se adopta

- `07 §2` (modelo = plantilla) → **modelo = símbolo-tipo + selector (EDN) + proyecciones**; el
  template pasa a ser **azúcar que compila al selector** (el *recipe*).
- Documentar el **substrato del selector**: vista v1 con `:map` (spans) + CST/`section` para
  anidamiento y tablas; `:opaque` (tabla/html/frontmatter del AST neutro) no es suficiente para anclar.
- `optrev` = paso opcional; `render` = proyección (no selección); la "receta por bloque" deja de ser
  deuda: **el selector es la receta**.
- Herencia (`:base`): selector del hijo = **composición** del selector del padre + cláusulas propias
  (reindexar `:outer-index`).
- Registrar como decisión: EDN para selectores; tree-sitter para la capa M; Datalog para G.

---

## 9. Reproducir

```bash
cd /home/jp/.cache/spikes/R3
CP=/home/jp/proyectos/kimun/src:/home/jp/proyectos/kimun/resources:.
POD=/home/jp/proyectos/kimun/pods/treesitter/target/release/pod-kimun-treesitter
memcap bb --classpath $CP common.clj                 # línea base (543 docs)
memcap bb --classpath $CP parity2.clj                # EDN 428/428
memcap bb --classpath $CP perf_edn.clj               # EDN 100 ms
KIMUN_POD=$POD memcap bb --classpath $CP parity_ts.clj   # tree-sitter 423/428
memcap bb --classpath $CP parity_dl.clj              # Datalog 423/428
memcap bb --classpath $CP cand1_perf.clj             # Datalog in-process + Datalevin
memcap bb --classpath $CP caps_edn.clj               # capacidades
KIMUN_POD=$POD memcap bb --classpath $CP caps_ts.clj
memcap bb --classpath $CP caps_dl.clj
memcap bb --classpath $CP inheritance.clj            # herencia + tamaños
```

Artefactos: `selectors/{edn,ts,datalog}/*.edn`, `*.clj` por candidato, `dl.clj`, `edn_engine.clj`,
`ts_engine.clj`, `blocktable.clj`.
