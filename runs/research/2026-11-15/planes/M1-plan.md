# Plan de implementación de M1 — modelos como subgrafos (spec 16)

Estado base leído: `main` en `3ce5ebc5` (K1a–K1c dentro; K1d y S2a en worktrees `/tmp/kimun-k1d-wt` y `/tmp/kimun-s2a-wt`, aún no en `main`; en `/tmp/kimun-s2a-wt` ya existe `src/kimun/surface/markdown/anchors.cljc`, en `main` no). Solo lectura: nada del repo fue tocado.

Convención: **[spec]** = lo dice el documento; **[inf]** = lo infiero yo (del código o de otros documentos). Líneas de `docs/v2/16-modelos-como-subgrafos.md` salvo indicación.

---

## 1. Qué pide el spec 16 y qué hay hoy

| # | Requisito | Dónde lo dice | Estado hoy |
|---|---|---|---|
| 1 | Todo documento es instancia de una clase (genérica del perfil, o gobernada = modelo) | L17-33 (`14 §18.1`) | **Parcial.** Documento tipado con modelo: `kimun.surface.docs/track-plan`. Genérico (`generic/md`): `docs/generic-class`, `generic-plan` (K3). Falta el perfil fino por tipo de archivo y código (L26 «Primer corte operativo… K3»). |
| 2 | Modelo = terna (símbolo-tipo con herencia, selector EDN, proyecciones de generación) | L30-33 | **Ausente.** Hoy un modelo es `{:model/name :fields :template :base …}` (`models/descriptor.cljc validate!` exige `:template` string; no existen `:anchoring`, `:generation`, `:substrate`). |
| 3 | Anclaje: función sobre el CST bajo contrato K6; decide qué es trackeado; lo no capturado queda fuera | L53-62 | **Parcial.** `models/extract.cljc extract` captura campos (el `:content-hash` del payload es lo «seleccionado»), pero `docs/projection-hash` y el `merkle-root` de `markdown/plan.cljc ast->tree` cubren **todo** el AST (docstring de `projection-hash`). Lo no indexado no se excluye del Merkle. **[inf]** |
| 4 | Generación: proyecciones nombradas (Markdown, S-exp, EDN/JSON, código) desde datos de instancia; varias por clase; sin inversión línea a línea | L64-72 | **Ausente.** Solo `models/render.cljc render` (un template, el de v1, byte-compatible). `compose` (`07`) es otra cosa. |
| 5 | Azúcar se observa en el nodo (anclaje: nada anclado; generación: literal/fórmula) | L60-62, L71-72 | **Implícito** en el comportamiento actual de extract (lo no capturado no entra al payload), sin prueba ni modelo de datos. |
| 6 | Documentos `source` (autoridad: archivo) frente a `generado` (autoridad: datos de instancia) | L74-84 (`14 §18.9`) | `source`: **implementado** (por referencia, `docs/registry`, contrato en evidencia `:contract`). `generado`: **ausente** y bloqueado por F10 (L260-262). |
| 7 | Lenguaje de selección = patrones EDN; `:substrate` explícito | L102-109 | **Ausente en `src/`.** Solo spike R3: `runs/research/2026-10-08/R3-selectors/edn/*.edn` (datos) y `~/.cache/spikes/R3/edn_engine.clj` (motor, fuera del repo). |
| 8 | Parser de la capa M = tree-sitter | L106-108 | **Implementado** como pod: `host/treesitter.cljc`; vista SLDB-MD sobre el CST: `markdown/cst_ast.cljc parse`. El selector de hoy corre sobre `models/blocks.cljc` (vista v1), no sobre el CST. |
| 9 | `:template` v1 = azúcar que compila a `:anchoring :selector` + `:generation :projections :markdown/v1` | L111-117 | **Parcial.** `models/recipe.cljc recipes` produce ya el vector de pasos (es el «recipe», vista derivada, nunca persistida). No hay compilación a la forma spec 16 ni proyección `:markdown/v1`. |
| 10 | C1 secuencia exacta; C2 sección opcional (`:want :opt`) | L125-126 | **Implementado** vía recipe (`:anchor`, `:optional-block`, `optrev`) en `extract.cljc`; sin la sintaxis `:want`. |
| 11 | C3 sección por heading/regex en cualquier posición (`:want :find :where`) | L127 | **Ausente.** |
| 12 | C4 lista anidada conservando jerarquía (sustrato CST) | L128 | **Ausente**: `blocks.cljc` aplana listas (R3 §1). |
| 13 | C5 tabla estructurada (`:want :table`, `pipe_table`) | L129 | **Parcial**: handler `:table` sobre vista v1 (`extract.cljc table-data`); no sobre CST. |
| 14 | C6 toda captura emite `{:value :path :spans :leaf-hashes}` | L130 | **Ausente** (S2a en curso aporta localizadores/huellas sin identidad). |
| 15 | Esquema declarativo del modelo (`:model/name :version :base :substrate :contract/digest :anchoring :generation`) | L132-162 | **Ausente** (existe solo como fixture: `test/fixtures/models/spec16_examples.edn`). |
| 16 | Contrato K6: `:contract/digest` por anclaje; `:source-changed` / `:contract-changed` / `:parser-unavailable` | L164-168 | **Parcial.** `surface/parse_contract.cljc` (componentes `:parser :grammar :normalization :extractor :class`; estados `source/*`, `contract/changed`, `contract/rebuild-mismatch`, `:parser-unavailable`). Su docstring dice «no cubierto: perfil de indexación y selector EDN (spec 16)». |
| 17 | Modo `:strict` (ausente=`:missing`, vacío=`""`) por defecto en modelos nuevos; `:legacy-v1-template` reproduce el oráculo | L170-186 | **Ausente.** El comportamiento actual de `extract` ES el legacy (reproduce los 5 quirks R3). |
| 18 | `:base` = DAG acíclico; `derive`/`isa?` para filtros, consultas e índices (P13) | L196-200 | **Parcial.** `semantic.cljc lineage/model-tags` recorre `:base` (MRO exportado de v1); `models check` informa bases ausentes. No hay `make-hierarchy`, ni `isa?` expuesto, ni detección de ciclos. Los helpers existen **solo en el test** `spec16_models_contract_test.cljc` (`build-hierarchy`, `ancestors-of`). |
| 19 | Herencia en anclaje: `(into pasos-padre pasos-propios)` + reindexar `:outer-index`; la subclase no elimina restricciones heredadas | L202-207 | **Ausente en src** (helper `compose-selector` solo en el test). |
| 20 | Herencia en generación: hereda proyecciones nombradas, añade o sobreescribe | L208-210 | **Ausente en src** (`compose-projections`, solo test). |
| 21 | Una clase cambiada es otra clase; el documento conserva la clase exacta con que fue anclado hasta reclasificar | L212-219 (`14 §18.3`, decisión 40) | **Implementado**: `models/store.cljc define-plan` (`:replace` + `supersedes`, versión+1), `descriptor-by-id`, evidencia `:contract` (`docs/contract-descriptor`), `docs update`/`models define --backfill`. |
| 22 | Fronteras de capa: grafo en paquete aparte; store estable solo `{anchor class-ref contract/digest tracked-root-hash}` y no interpreta modelos; caché recomputable | L223-235 | **Parcial.** Anillo base→grafo ya en `scripts/check_rings.clj` (`graph-package`, `pending-graph-crossings`). `kimun.graph.*` existe pero **sin** modelos (`P1` pendiente; `surface.semantic → graph.io` declarado). Hoy los modelos viven en el store (árbol `models`), no en el paquete grafo. No hay `:tracked-root-hash` separado. |
| 23 | Merkle de reconciliación solo sobre la proyección trackeada | L233-235 (`14 §20`) | **Ausente para documentos tipados** (ver 3). |
| 24 | Decisiones abiertas: D0→K1, A1.1, F10, strict/legacy en M2 | L239-265 | D0: derivado (`14 §21`), K1a–K1c hechos; A1.1, F10: abiertas; strict/legacy por modelo: es de M2. |
| 25 | Gate de cero contexto (6 preguntas y su prueba mínima) | L269-281 | Solo la fila 6 y la forma del fixture tienen prueba (`spec16_models_contract_test.cljc`, que verifica **el fixture y el documento**, no código). Filas 1-5: ausentes. La parte «`kernel.*` cero dependencias hacia `graph.*`» de la fila 5 ya la cumple `bb lint`. |

---

## 2. Grafo de cortes

Ubicación del código **[inf]**: todo en `kimun.surface.models.*` (ring `kimun.` catch-all, que permite `kimun.host.*`). **No** en `kimun.graph.*`: su anillo no admite `kimun.host.` (solo `kernel`, `surface`, `graph`, `cheshire`) y `kimun.surface.docs` (base) usa `models.*`, así que mover hoy rompería la regla «la base no depende del grafo» (ver §3.7). Mover es la parte restante de P1, fuera de M1.

Regla de edición para poder paralelizar **[inf]**: cada corte solo añade su fila en `runs/subagents/<fecha>-M1x/` y **no** edita `docs/v2/12` ni `docs/v2/16`; el coordinador actualiza la fila M1 de `12 §1` tras integrar (esos dos archivos serían el único solape). M1a, que va solo, sí los edita.

```
M1a ──┬─ M1b (compile template→selector, derivado)        ─┐
      ├─ M1c ── M1d (strict) ──┬─ M1i (C3-C5 sobre CST)     ├─ M1g ── M1h (K6)
      │    └──── M1j (C6; espera S2a)                       │      │
      ├─ M1e (proyecciones de generación)                   │      │
      └─ M1f (descriptores spec 16 en el store + jerarquía) ┘      │
                         M1f ── M1k (id de modelo; espera K1d + dueño §3.1) ── M1l (class-ref por id, isa en consultas)
fuera de M1: generado (F10), Merkle trackeado (formato), mover a kimun.graph (P1)
```

Estimación (horas de worker, una vuelta con revisión): a 2, b 1.5, c 3, d 1.5, e 2, f 3, g 4, h 3, i 5, j 3, k 3, l 3.

### M1a — Forma, jerarquía y composición del descriptor spec 16 (puro)
- **Objetivo.** Llevar a `src/` lo que hoy solo vive en el test: validar la forma de un descriptor spec 16, construir la jerarquía `derive`/`isa?` desde `:base` y componer selector, campos y proyecciones. Sin store, sin CLI, sin extraer nada.
- **Depende de:** nada (ni K1e, S2a, P1).
- **Archivos.** Nuevo `src/kimun/surface/models/subgraph.cljc`; nuevo `test/kimun/surface/models/subgraph_test.cljc`; ajuste de `test/kimun/spec16_models_contract_test.cljc` (usa las funciones de src en vez de helpers privados) y `test/fixtures/models/spec16_examples.edn` (`:implementation` deja de decir `:blocked`/`code-changes false`); docs mínimos de `12`/`16`.
- **No incluye.** Persistir, `models define`, extraer, compilar templates, proyecciones, K6, ids, rol `:generated` (se valida su forma pero se declara bloqueado por F10).
- **Pruebas discriminantes.**
  1. El fixture entero valida; un descriptor al que se le quita `:anchoring`, o con `:compat` fuera de `#{:strict :legacy-v1-template}`, o `:document-role` fuera de `#{:source :generated}`, falla con `:models/invalid` que nombra la clave.
  2. `isa?` transitivo (`ResumenTecnicoDoc` → `ResumenDoc` → `PrimitiveDoc`) y no simétrico; un ciclo (`A→B→A`) y una base ausente se rechazan nombrando el nodo.
  3. `compose-selector` de `ResumenTecnicoDoc` = 8 pasos, `:outer-index` 0..7, orden padre→hijo; con un diamante (D→B,C→A) el ancestro A aparece **una vez**.
  4. Una subclase que redeclara un campo requerido del padre como no requerido o con otro tipo falla (§4.2 «no puede eliminar restricciones»); con `:base []` la misma redeclaración es válida.
  5. `compose-projections`: la hija añade y sobreescribe sin tocar el selector compuesto (se compara el selector antes/después).
  6. `(blocked-by descriptor-generado)` = `#{:F10}` y `blocked-by` de un `:source` = vacío.

### M1b — Compilar `:template` v1 a selector (vista derivada)
- **Objetivo.** Función pura `template->spec16` que, a partir de un descriptor v1, devuelve `:anchoring {:fields :selector}`, `:substrate` con `:compat :legacy-v1-template` y `:generation {:projections {:markdown/v1 …}}`. Es una vista, **nunca se persiste ni se mezcla en el descriptor guardado** (§4 riesgos).
- **Depende de:** M1a (forma).
- **Archivos.** Nuevo `src/kimun/surface/models/compile.cljc` (usa `recipe/recipes`); test nuevo. No toca `descriptor.cljc`, `store.cljc`.
- **No incluye.** Ejecutar el selector (M1c), CLI, persistencia, el modo strict.
- **Pruebas.** (1) La salida valida con M1a. (2) Para `ChecklistDoc`, `AtomDoc`, `TaskDoc` de `runs/research/2026-10-08/R3-selectors/edn/`, el `:selector` compilado de la plantilla equivalente es igual al archivo R3 (o la diferencia se documenta campo a campo). (3) `serialize` del descriptor v1 original es byte a byte igual antes y después de llamar a `template->spec16` (no muta). (4) Un descriptor con `⸢py•` sigue rechazándose. (5) Determinismo: compilar dos veces da `=`. (6) Un `render` marker queda en la proyección, no en el selector (§3.2).

### M1c — Motor de selectores EDN sobre el sustrato SLDB-MD (modo legacy)
- **Objetivo.** Separar de `extract.cljc` el intérprete de pasos (`extract-raw` ya recibe `recipes`): nueva función `extract-selector steps text`, y `extract-raw` delega en ella. Paridad exacta con `extract` en modo legacy.
- **Depende de:** M1a (forma de pasos). Independiente de M1b (se prueba con `recipe/recipes` directo).
- **Archivos.** `src/kimun/surface/models/extract.cljc` (refactor), nuevo `src/kimun/surface/models/selector.cljc` (donde se muevan `match-at`, `section-body`, `regular`), tests. 
- **No incluye.** Strict, C3-C6, CST, store, CLI. Cero cambio de comportamiento observable.
- **Pruebas.** (1) `extract` y `extract-selector` sobre `recipes` de cada descriptor de `test/` y de los goldens de `store_test` son `=`; (2) los 5 casos R3 siguen reproduciendo el quirk legacy (documentos sintéticos mínimos); (3) pasos serializados a EDN y leídos de vuelta (`pr-str`/`edn/read-string`) dan la misma extracción (portabilidad §3.1); (4) un paso sin `:outer-type` falla nombrando el paso; (5) `blocks_parity_test`, `parity_test`, `corpus_test`, `docs_parity_test` siguen verdes (mutación: quitar la delegación los rompe); (6) añadir un párrafo estilístico no capturado no cambia el `content-hash` del payload (fila 3 del gate, parte de extracción).

### M1d — Modo `:strict`
- **Objetivo.** `:compat :strict`: campo requerido sin su sección = `:missing` (no captura desde el inicio); sección presente vacía = `""` (no el heading). `:legacy-v1-template` queda como hoy.
- **Depende de:** M1c.
- **Archivos.** `selector.cljc`, `extract.cljc` (parámetro de modo), tests.
- **No incluye.** Elegir el modo por cada modelo del desk (es de M2), cambiar el default de los modelos v1 existentes (siguen legacy).
- **Pruebas.** (1) AtomDoc sin `## Answer`: strict → `:validate/missing` (campo requerido), legacy → el quirk; (2) TaskDoc con `## Done When` vacío: strict `""`, legacy el heading; (3) documento sin quirk: strict ≡ legacy; (4) modo desconocido falla; (5) `parity_test` verde sin cambios (el default no se movió); (6) mutación: strict que no corrige falla (1) y (2).

### M1e — Proyecciones de generación nombradas
- **Objetivo.** `project descriptor projection-id payload` para `:format :markdown|:edn|:json`, filtrando por `:fields`, y `:markdown/v1` delegando en `render/render`. Una clase, dos proyecciones, salidas distintas sin tocar lo capturado.
- **Depende de:** M1a.
- **Archivos.** Nuevo `src/kimun/surface/models/generate.cljc`; test. Sin CLI.
- **No incluye.** `:mode :sections`, `:sugar` (el spec no los define: ver §3.5), documentos generados, escribir archivos.
- **Pruebas.** (1) `ChecklistDoc` con `:markdown/default` y `:summary/short` da dos textos distintos y el `content-hash` del payload no cambia (gate fila 1); (2) `:edn/brief` produce EDN legible con solo `["id" "title" "summary"]`; (3) proyección inexistente falla nombrando las disponibles; (4) `:markdown/v1` de un descriptor v1 es byte a byte `render/render`; (5) la subclase hereda proyecciones y sobreescribe una sin afectar la base; (6) un campo de `:fields` que el payload no tiene falla, no queda vacío en silencio.

### M1f — Descriptores spec 16 en el store y jerarquía consultable
- **Objetivo.** `models define` acepta un descriptor spec 16 (sin `:template`) además del v1; `models check` valida bases (y ciclos); `models list --isa BASE` consulta la jerarquía.
- **Depende de:** M1a.
- **Archivos.** `models/descriptor.cljc` (validar «v1 xor spec 16»), `models/store.cljc` (`same-contract?` y `serialize` sin cambios para v1), `cli/commands/models.cljc`, tests (`models_test`, `store_test`).
- **No incluye.** Extraer documentos con la clase nueva (M1g), ids de modelo (M1k), composición en tiempo de lectura.
- **Pruebas.** (1) definir `PrimitiveDoc` y `ResumenDoc` spec 16: `models list --isa PrimitiveDoc` los trae; (2) redefinir con el mismo contrato = `:unchanged`; con otro selector = versión+1 y `supersedes`; (3) ciclo o base ausente → `models check` falla; (4) un descriptor v1 existente conserva su `node-id` byte a byte (guardia del oráculo y del contrato); (5) `:document-role :generated` se rechaza con mensaje «F10»; (6) descriptor con `:template` y `:anchoring` a la vez se rechaza.

### M1g — Documentos tipados con una clase spec 16 (ancla compuesta)
- **Objetivo.** `docs/payload-of` y `track-plan`/`update-plan` extraen con el selector compuesto de la clase (resolviendo `:base`), y el documento registra la clase exacta (revisión) de la que fue anclado.
- **Depende de:** M1c, M1d, M1f **y la decisión técnica de §3.4** (cómo se fija el padre al componer).
- **Archivos.** `src/kimun/surface/docs.cljc`, posiblemente `cli/commands/docs.cljc`; tests (`typed_docs_test`, nuevo).
- **No incluye.** K6 del selector (M1h), CST, ids de modelo.
- **Pruebas.** (1) un documento `ResumenTecnicoDoc` extrae campos propios + heredados; (2) editar el selector del padre no cambia silenciosamente el payload del hijo ya anclado (queda `:superseded`/según §3.4); (3) añadir un párrafo no anclado no cambia `content-hash` ni la ruta `:content-hash`; (4) `docs update` tras redefinir la clase reancla; (5) `stores health`/`sense` de un documento v1 sin tocar siguen sin señales; (6) `docs show` incluye `:class`.

### M1h — K6 para selectores spec 16
- **Objetivo.** El contrato de lectura (`parse_contract/record`) incorpora substrato, perfil, `:compat` y digest del selector compuesto **solo** para descriptores spec 16; el digest de los v1 no cambia.
- **Depende de:** M1g.
- **Archivos.** `surface/parse_contract.cljc`, tests (`parse_contract_test`, `contract_test`).
- **No incluye.** Selectores de código, grammars nuevas.
- **Pruebas.** (1) digest de un documento v1 igual antes y después (guardia); (2) editar un paso del selector → `contract/changed`, no `source/changed`; (3) editar el archivo → `source/changed`; (4) sin pod con selector CST → `:parser-unavailable`; (5) `rebuild-mismatch` funciona para clase spec 16; (6) el `:contract/digest` declarado en el descriptor se ignora y se recalcula (ver §3.3).

### M1i — Selectores C3-C5 sobre el CST
- **Objetivo.** `:want :find`, lista anidada y tabla estructurada contra el sustrato CST (`markdown/cst_ast` + pod).
- **Depende de:** M1d; S1 (cumplida); pod disponible (`KIMUN_POD`).
- **Archivos.** `selector.cljc` (despacho por substrato), nuevo `selector_cst.cljc`, tests con fixtures de lista anidada y `pipe_table`. **Solapa con M1d** en `selector.cljc`: va en serie, después.
- **No incluye.** Identidad de unidades, C6.
- **Pruebas.** (1) lista de dos niveles conserva la jerarquía (hoy se aplana); (2) la sección C3 se captura aunque cambie el orden de las vecinas; (3) tabla → filas/celdas tipables; (4) sin pod el paso falla con `:parser-unavailable`, no en silencio; (5) los mismos pasos C1/C2 dan igual en ambos sustratos; (6) `bb test` sin pod sigue verde (las pruebas con pod se saltan declaradamente).

### M1j — C6: capturas con coordenadas
- **Objetivo.** Toda captura entrega `{:value :path :spans :leaf-hashes}` usando el vocabulario de localizador/huella que fije S2a; sin identidad.
- **Depende de:** M1c; **S2a** (no se inventa un segundo formato de ancla).
- **Archivos.** `selector.cljc`/`selector_cst.cljc` (serial con M1i), tests.
- **No incluye.** Asignar ids a las unidades capturadas (K1e, A1.1).
- **Pruebas.** (1) una edición fuera de la captura no cambia sus `:leaf-hashes`; (2) una edición dentro cambia solo la hoja afectada; (3) los spans reproducen el valor al cortar el texto; (4) la salida no lleva ningún campo `:id` (sin identidad); (5) cambia igual que S2a ante mismas ediciones; (6) formato estable (golden).

### M1k — Identidad propia de los modelos
- **Objetivo.** Cada modelo (por nombre) recibe un ULID acuñado al definirlo, persistido en el store (no en el CAS por contenido), estable entre versiones y arrastrado en `include build --rebuild`.
- **Depende de:** K1d (versiones de modelo conservadas en rebuild; aún no en `main`), M1f (`store.cljc`), y **la confirmación del dueño de §3.1**.
- **Archivos.** `models/store.cljc`, `cli/commands/models.cljc`, `cli/commands/include.cljc` (`identity-registry`; **solapa con K1d y K1e**), tests.
- **No incluye.** Que los documentos referencien el id (M1l), linaje al partir/fusionar clases.
- **Pruebas.** (1) `models define` acuña id; redefinir (versión 2) conserva el id; (2) dos stores, mismo modelo: ids distintos, mismo `node-id` de revisión; (3) rebuild arrastra el id y `:identities` lo cuenta; (4) el id no entra a ninguna huella (`node-id`, `projection-hash`); (5) renombrar un modelo = id nuevo, el viejo `:missing` (como K1c con campos); (6) store anterior sin ids: se acuñan en la siguiente escritura sin degradar `stores health`.

### M1l — Referencia por id y `isa?` en consultas
- **Objetivo.** El documento registra la unidad de su clase además de la revisión; `docs list --isa BASE` e índice transversal (P13).
- **Depende de:** M1k, M1g.
- **Archivos.** `surface/docs.cljc`, `cli/commands/docs.cljc`, `surface/index/*`.
- **No incluye.** Reclasificación automática, G.
- **Pruebas.** (1) `--isa` devuelve subclases transitivas; (2) redefinir la clase no cambia la unidad referenciada; (3) cambia la revisión, el documento queda `:superseded` hasta `docs update`; (4) clase borrada → documento huérfano declarado; (5) rebuild conserva referencia; (6) sin la clave, store viejo sigue ok.

### Orden y paralelismo
- Serie obligatoria: M1a → (M1c → M1d → M1i → M1j) y (M1f → M1g → M1h) y (M1f → M1k → M1l).
- Paralelo sin archivos en común tras M1a: **M1b**, **M1c**, **M1e**, **M1f** (cuatro archivos principales distintos: `compile.cljc`, `extract.cljc`+`selector.cljc`, `generate.cljc`, `descriptor/store/cli models`). M1g espera a c, d y f.
- Serie por solape: M1d → M1i → M1j (todos tocan `selector*.cljc`; por tiempo estimado, d (1.5) < j (3) < i (5)); M1k comparte `store.cljc` con M1f e `include.cljc` con K1d/K1e: después de ambos.
- No incluidos en M1: roles generados (F10), Merkle solo sobre lo trackeado (cambio de formato), mover a `kimun.graph` (P1), regenerar el desk (M2), selectores de código (K6 para código, «M1» en `12 §1` fila K6 pero el spec 16 no los especifica: solo menciona código como ejemplo, L26, L66-67).

---

## 3. Ambigüedades y contradicciones que cambian el diseño

### Para el dueño (cambian qué es kimun)
**3.1 Identidad de una clase entre revisiones.** Texto: «Una clase cambiada es otra clase: editar campos, selectores o herencia crea una nueva revisión/id de descriptor; la versión previa continúa existiendo» (L214-216; `14 §18.3`, decisión 40) frente al contrato D0: «mientras el anclaje resuelve, un cambio es una mutación de esa unidad» (`14 §21`, regla 2) y «los ids de modelos los acuña de nuevo el rebuild» (`14 §21`, pendientes). «revisión/id» no dice si la clase es **una unidad con ULID y revisiones** (el descriptor direccionado por contenido sería un valor/huella) o **cada revisión es una unidad distinta** unidas por `supersedes`. Cambia: qué referencia un documento, qué significa `isa?` entre versiones y si `:base` apunta a una unidad o a una revisión. Mi propuesta **[inf]**: unidad = linaje por nombre con ULID; revisión = valor inmutable; el documento ancla a la revisión (como hoy) y registra también la unidad. Es una pregunta corta, pero toca texto del dueño (decisión 40), por eso no la cierra el equipo.

**3.2 A1.1 — ¿un elemento capturado y su sensor `S` son un nodo o dos?** (L267-270). No bloquea M1a–M1j (el selector entrega capturas sin id, y C6 sin identidad). Sí bloquea convertir una captura en unidad (K1e/S2b) y por tanto cualquier corte que asigne identidad a lo seleccionado.

**3.3 F10 — dónde vive la verdad de un documento generado** (L260-262, L79). El spec dice que en generado «la verdad son los datos de la instancia» guardados en el store, pero `14 §19` regla 2 dice que el store estable «no guarda el contenido». Solo es consistente si esos datos viven en la capa grafo; formato y ubicación durable están sin decidir. Cambia qué es kimun (¿guarda conocimiento que no está en ningún archivo?). Bloquea `:document-role :generated` y M2 más allá de `source`; M1f debe rechazarlo explícitamente.

### Técnica de equipo (decidir al implementar, no preguntar al dueño)
**3.4 Cómo se fija el padre al componer.** `:base ["PrimitiveDoc"]` es un nombre; `define-plan` admite bases no definidas (`models/store.cljc` docstring) y la composición es «ancestor-first» (L205). Si el padre cambia, ¿el payload de un hijo ya anclado cambia en silencio? Contradice «clase inmutable» (L212) si `:base` es por nombre. Opciones: (a) resolver en `define` y guardar `:base-revisions`/selector compuesto en el hijo; (b) resolver al leer y que el digest K6 incluya el selector compuesto, de modo que cambiar el padre = `contract/changed` en los hijos. Recomiendo (b) + exigir bases definidas al definir una clase spec 16. Condiciona M1g/M1h.

**3.5 Herencia por concatenación de pasos.** `(into pasos-padre pasos-propios)` (L205) obliga a que las secciones del hijo vayan **después** de las del padre en el archivo; el propio texto dice «no se fuerza el árbol» (L192-194). Solo `:want :find` (C3, orden libre) escapa. Además, el ejemplo de L145-158 repite en `ChecklistDoc` los pasos de front matter y h1 aunque declara `:base ["PrimitiveDoc"]`, mientras el fixture los deja solo en el padre (composición sin duplicados). Se resuelve tomando el fixture como canónico **[inf]**; hay que decirlo en el spec.

**3.6 Vocabulario del selector frente al sustrato.** Los pasos de L145-158 y del fixture usan tipos SLDB-MD (`front_matter`, `bullet_list`, `paragraph`) y L128-129 pide `:substrate :cst/markdown` con `list`/`pipe_table`; L140 declara `:substrate {:parser :treesitter …}` en otra forma. Son dos vocabularios y dos formas de `:substrate`. `kimun.surface.markdown.cst-ast` ya traduce CST→SLDB-MD, así que **[inf]** el vocabulario de pasos de M1c-M1d es SLDB-MD servido por cualquiera de los dos sustratos, y M1i añade solo lo que SLDB-MD no ve (anidamiento, tabla). La reserva 1 de R3 (L263-265) queda así resuelta por el equipo.

**3.7 Dónde vive el código de modelos (P1).** L5 y L229 ponen las clases en `kimun.graph.*`; hoy `kimun.surface.docs` (base) importa `models.*` para extraer. Moverlo viola «la base no depende del grafo» (`scripts/check_rings.clj`, `graph-package`) y el anillo `kimun.graph.` no admite `kimun.host.`. Hace falta invertir (extractor inyectado en `docs`) antes de mover. M1 se queda en `surface.models`.

**3.8 `:contract/digest` declarado dentro del descriptor** (L139, L164-166) cubre «descriptor de clase»: se contiene a sí mismo. Debe ser calculado (`parse_contract/record`), no declarado; el fixture usa cadenas de relleno (`sha256:spec16-example-…`). Cerrado en M1h: se ignora y se recalcula.

**3.9 `:mode :sections`, `:sugar`, `:requires-open-decision`, etc.** (L160-161, fixture) no están definidos; el contrato solo fija formato y `:fields`. M1e implementa solo esos y rechaza claves desconocidas.

**3.10 Regex `#"…"` en C3** (L127) no es EDN portable (R3 vende «EDN/JSON puro», L98); el fixture usa cadena en `:regex`. Se usa cadena.

**3.11 Strict frente a «100 % de paridad».** R3 midió 428/428 porque el EDN *es* el recipe, incluidos los 2 quirks (L98 frente a L183-186): «compilar el template» (L111-117) da legacy por construcción; strict necesita comportamiento distinto. Resuelto en M1d con un modo explícito; la elección por modelo del desk queda para M2.

---

## 4. Riesgos para los invariantes

- **`bb oracle` / ids congelados.** El oráculo (`scripts/canon_oracle.py`) comprueba bytes canónicos y node ids. Riesgo real: el id del nodo `edn/model` es el hash de `store/serialize` del descriptor; **cualquier clave nueva persistida en un descriptor v1** (p. ej. guardar la forma compilada de M1b) cambia su id, hace `supersede` y desplaza `:contract` de todos sus documentos. Regla: la forma spec 16 de un v1 es vista derivada, nunca se guarda. Ningún corte toca `kernel/canon`, `node`, `pool`; confirmar `bb oracle` = 0 diferencias por corte.
- **Paridad v1** (`docs/v2/tests/parity-v1.md`, marcador `520/1/14/55/0/0/0/0`). M1c/M1d tocan `extract`: el default debe seguir siendo el comportamiento legacy; dirigidas obligatorias `parity_test`, `blocks_parity_test`, `segments_parity_test`, `corpus_test`, `docs_parity_test`, `golden_test`. Strict es opt-in.
- **K6 (digest).** Añadir componentes a `parse-contract` para todos los documentos cambiaría el digest de **cada** documento y los marcaría `contract/changed` en masa. M1h debe hacerlos condicionales a spec 16 y probarlo con un digest v1 congelado.
- **Anillos.** Nuevo código en `kimun.surface.models.*` puede usar `host`; si alguien lo coloca en `kimun.graph.*` falla `bb lint` (no admite `host`). Tampoco puede importar `kimun.kernel` hacia `graph`. `docs.cljc` no debe requerir `graph` (§3.7).
- **Formato del store.** No cambia ningún corte de M1a–M1j: cero claves nuevas en el kernel, descriptor del árbol, ruta ni arista. M1k añade ids en el store (mismo patrón que K1c: clave del descriptor del árbol; verificar que `:new-tree` del árbol `models` admite claves extra, a diferencia de `:register-tree` — no lo comprobé). La proyección **trackeada** del Merkle (L233-235; hoy el Merkle cubre todo el AST) implicaría subir de formato: excluida de M1 y debe decidirse como corte aparte de K2b.
- **Salud de documentos legados.** M1g/M1k no deben degradar `stores health`/`sense` de documentos v1 intactos (mismo cuidado que K1c); incluido en pruebas.
- **Pod.** Pruebas de M1i/M1j dependen de `KIMUN_POD`; sin él deben saltarse de forma declarada, no pasar vacías.
- **Colisiones de integración.** `include.cljc` lo tocan K1d, K1e y M1k; `docs.cljc` lo tocan K1e, S2a y M1g/M1l: pedir pausa de `dev`/`main` y rebasar antes del CI final.

---

## 5. Brief listo para despachar — M1a

Archivo sugerido: `/home/jp/.cache/coord-briefs/kimun-M1a-forma-y-herencia.md`. Contenido:

````markdown
# Brief M1a — forma, jerarquía y composición del descriptor spec 16 (primer corte de M1)

Para: Pi (worker). De: Claude (diseño y revisión). Un commit (lo hago yo tras revisar).

## Reglas

- Worktree: `/tmp/kimun-m1a-wt` (detached; la base te la doy al despachar). No toques
  `/home/jp/proyectos/kimun` ni ningún store real.
- No commitees. Todo `bb` por `~/bin/memcap`. Dirigidas sí; **no** `bb test` completo.
  Antes de cada `bb`: `pgrep -x bb` vacío.
- Código y docstrings en inglés; documentos en español.
- **Pocas pruebas**: las seis de abajo. Sin variantes.
- **Sin cambios en `src/kimun/kernel/*`, `src/kimun/surface/docs.cljc`, `models/extract.cljc`,
  `models/descriptor.cljc`, `models/store.cljc` ni en `cli/`.** Este corte es puro: solo agrega
  un namespace nuevo. Si crees que hace falta tocar otro, para y dime por qué.
- Un `defn`/`def` público sin docstring rompe `bb lint`.

## Contexto y decisiones (cerradas por el equipo; son inferencias, no palabras del dueño)

Contrato: `docs/v2/16-modelos-como-subgrafos.md` §1, §2, §3.4, §4 y §7; fixture
`test/fixtures/models/spec16_examples.edn`; helpers hoy privados en
`test/kimun/spec16_models_contract_test.cljc` (`model-tag`, `ancestors-of`, `build-hierarchy`,
`compose-selector`, `compose-projections`). El spec 16 dice qué es un descriptor spec 16; **no**
está implementado nada en `src/`. M1 se parte en cortes; este es el primero y no depende de
nada abierto.

1. **Namespace nuevo** `kimun.surface.models.subgraph` (`src/kimun/surface/models/subgraph.cljc`).
   Ring `kimun.`: solo `clojure.*`, `kimun.kernel.*` (`err`), nada de `host`, nada de `graph`.
   Puro: no abre stores, no lee archivos.
2. **Forma.** `validate!` `[descriptor]` → el descriptor, o `:models/invalid` (usa
   `kimun.kernel.err/raise`, con `{:model nombre :problem why}` como `descriptor/validate!`)
   nombrando el primer problema. Reglas: `:model/name` identificador capitalizado
   (`[A-Z_][A-Za-z0-9_]*`); `:model/version` entero ≥ 1; `:base` vector de strings;
   `:document-role` en `#{:source :generated}`; `:substrate` mapa con `:parser` en
   `#{:treesitter :sldb-md/blocks}` y `:compat` en `#{:strict :legacy-v1-template}`;
   `:anchoring` mapa con `:fields` (vector de mapas con `:name` string único y `:description`
   no vacía, `:type` válido según `kimun.surface.models.descriptor/type-valid?` — **requiérelo**,
   se permite el require dentro de `kimun.surface.models.*`) y `:selector` (vector; cada paso un
   mapa con `:outer-index` entero, `:outer-type` string, `:handler` keyword);
   `:generation` mapa con `:projections` mapa `keyword → mapa con :format` en
   `#{:markdown :edn :json}`. **No** se exige `:template` ni `:contract/digest` (el digest se
   calcula en M1h; si viene, se acepta e ignora). Claves desconocidas: se aceptan (el spec las
   usa, p. ej. `:requires-open-decision`).
3. **Rol generado.** `:generated` valida su forma pero es un cuerpo bloqueado: `blocked-by`
   `[descriptor]` → `#{:F10}` para `:generated`, `#{}` para `:source`. Nada más (F10 sigue abierta).
4. **Jerarquía** (`14 §18.2`, §4.1 del spec): `hierarchy` `[models]` → un `make-hierarchy`
   con `derive` por cada arista `:base`, claves `(keyword "kimun.model" name)`; `isa-model?`
   `[h child parent]`. Un ciclo o una base que no está entre `models` lanza `:models/invalid`
   nombrando el nodo. Los nombres de modelos van como strings en la API pública.
5. **Composición** (§4.2): `ancestors-of` `[by-name name]` en orden ancestro-primero, **cada
   ancestro una sola vez** (el primero que se alcanza; es una decisión nuestra para diamantes, el
   spec no la fija). `compose-selector` = `(into pasos-ancestros pasos-propios)` con
   `:outer-index` reindexado de 0 y consecutivo; `compose-fields` análogo (por nombre);
   `compose-projections` = `merge` ancestro-primero (la hija añade y sobreescribe una por una).
6. **No eliminar restricciones heredadas** (§4.2): `check-composition!` `[by-name name]` lanza
   `:models/invalid` si la subclase redeclara un campo heredado con otro `:type` o con
   `:required` menor. Con `:base []` no hay herencia y no hay restricción.
7. **Composición por arreglo del fixture, no del ejemplo de §3.4:** el ejemplo de L145-158 repite
   los pasos del padre dentro del hijo; el fixture los deja solo en el padre. El fixture manda.
8. **Fuera de alcance:** persistir, `models define`, extraer, compilar un `:template`, proyecciones
   ejecutables, K6, ids, `:want :find`, CST, el rol generado más allá de su forma.

## Qué cambia

### 1. `src/kimun/surface/models/subgraph.cljc` (nuevo)
Funciones públicas: `validate!`, `blocked-by`, `hierarchy`, `isa-model?`, `ancestors-of`,
`compose-selector`, `compose-fields`, `compose-projections`, `check-composition!`. Todas con
docstring y todas puras.

### 2. Tests
- Nuevo `test/kimun/surface/models/subgraph_test.cljc` con las seis pruebas de abajo.
- `test/kimun/spec16_models_contract_test.cljc`: quita los helpers privados y úsalos desde
  `subgraph`. **Ajusta** las aserciones que hoy dicen `:implementation :status :blocked`,
  `code-changes false` y `blocked-by #{:P1 :K1}`: la fixture pasa a
  `:implementation {:status :in-progress :slices [:M1a] :code-changes true :blocked-by [:K1e]}`
  (deja `:K1` fuera; K1 ya cubre el contrato). Dime exactamente qué cambió y por qué.
  `:open-decisions` queda igual.

## Pruebas (seis, `subgraph_test.cljc`)

1. **Forma:** los 7 modelos del fixture validan; quitar `:anchoring`, poner `:compat :loose` o
   `:document-role :other` falla con `:models/invalid` y el mensaje nombra la clave.
2. **Jerarquía:** `isa-model?` transitivo (`ResumenTecnicoDoc` → `ResumenDoc` → `PrimitiveDoc`),
   no simétrico; un ciclo `A→B→A` y una base ausente se rechazan nombrando el nodo.
3. **Composición del selector:** `ResumenTecnicoDoc` da 8 pasos con `:outer-index` 0..7, el paso 2
   es el `Summary` del padre y el 4 es `Complexity`; con un diamante D→(B,C)→A, A aparece una vez.
4. **Restricciones heredadas:** una subclase que redeclara un campo requerido del padre como no
   requerido, o con otro tipo, falla; con `:base []` la misma redeclaración valida.
5. **Proyecciones:** la hija hereda las de la base, añade una y sobreescribe otra; el selector
   compuesto antes y después de componer proyecciones es `=`.
6. **Bloqueo:** `blocked-by` de `ResumenDigestView` (generado) = `#{:F10}`; de `ChecklistDoc` = `#{}`.

Mutación: `git stash push -- src/` ⇒ las pruebas de `subgraph_test` fallan (el namespace no
existe); `git stash pop`.

## Documentos (español, mínimos)

- `docs/v2/12-ecosistema-y-pendientes.md §1`, fila `M1`: añade «M1a: forma, jerarquía y
  composición del descriptor spec 16 (`kimun.surface.models.subgraph`, puro; sin store ni CLI)» y
  su evidencia `runs/subagents/2026-11-15-M1a/`; deja pendiente el resto (compilar template,
  motor de selector, strict, proyecciones, store, K6, ids de modelo). No toques otras filas.
- `docs/v2/16-modelos-como-subgrafos.md`: una línea en el encabezado con el estado («M1a
  implementada; resto de M1 pendiente») y, en §4.2, una nota que dice que cada ancestro entra una
  vez en un diamante (**inferencia del equipo**, no del spec).

## Entrega

Dirigidas en verde: `subgraph_test`, `spec16_models_contract_test`, `store_test`
(models), `semantic_test`, `doc14_contract_test`, y `bb lint`. `bb oracle` debe seguir en 0
diferencias. Salidas en `/tmp/kimun-m1a-wt/runs/subagents/2026-11-15-M1a/` (`lint.txt`,
`directed.txt`, `mutation.txt`, `oracle.txt`). Reporta: archivos tocados, números, mutación,
**todo test existente que hayas ajustado y por qué** (esperado: el contract test y el fixture),
y desvíos del brief.

## Adenda 0 — base

- Base: la que te dé al despachar (worktree `/tmp/kimun-m1a-wt` ya creado). No hace falta el pod.
````

---

## Notas de verificación

- No ejecuté nada. No comprobé: si `:new-tree` admite claves extra en el descriptor del árbol `models` (relevante a M1k); el estado de integración de K1d y de S2a en `main` más allá de lo visible en `git log` y en los worktrees.
- `~/.cache/spikes/R3/edn_engine.clj` existe; no se versiona en el repo (el brief M1c podría pedir copiar a `runs/research/` lo que valga la pena).
