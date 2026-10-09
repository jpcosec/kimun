# R1 — Matriz de inclusión funcional

Fecha: 2026-10-09. `W1` = `wittgenstein/matrix-shrdlu-spec`; `W2` = `wittgenstein/Matrix/Matrix`. Estado Kimun: **existente**, **parcial**, **dirección** (solo intención/spec) o **ausente** (sin equivalente encontrado en superficies actuales). No se infiere equivalencia funcional por compartir el nombre «graph», «semantic» o «projection».

## Capacidades de Wittgenstein

| ID | capacidad y fuente verificable | Kimun hoy | inclusión necesaria |
|---|---|---|---|
| C01 | W1 `populator/extract.py`, `contexts.py`: texto, chunks, signos locales y offsets | Parcial: CST/AST, documentos y spans por hoja | Adaptar estructura a contextos conservando mapa raw→NFC→hoja |
| C02 | W1 `specs/convergence.md §1`: símbolo = signo en contexto | Parcial: `node.cljc :term` exige nombre/idioma, no contexto | Identidad contextual obligatoria; mismo signo en dos mundos no se fusiona |
| C03 | W1 `pipeline/decompose.py`, `decompose_batch.py`: declaraciones heurísticas/LLM y caché offline | Dirección: adapters existentes son `symbols|cli` | Protocolo de declaraciones con source, contexto, signos y soporte verificable |
| C04 | W1 `pipeline/ingest.py`: WiGame por contexto y relación | Ausente como motor; triple G es datos | Juegos tipados por `(contexto, relación, contrato)` |
| C05 | W1 `core/li_space.py`, `system/wigame.py`: admisibilidad y ejes | Dirección en `14 §4–§8` | Firma local de R; distinguir inapplicable de false/unknown |
| C06 | W1 `ViMatrix`, `SiMatrix`: verdad separada de sentido | Dirección; `edge :status` solo sentido | Contrato de evaluación y representación de falso explícito |
| C07 | W1 `core/relations`: álgebra de relaciones | Dirección en `14 §4` | Propiedades por contexto, no flags globales por nombre |
| C08 | W1 `kernel/formulas.py`, `rule_matrix.py`: formas anidadas y conectivos | No representable en la forma actual `:proposition` | IR lógico recursivo, validado antes de evaluación |
| C09 | W1 `system/deduction.py`: modus ponens, conjunciones, punto fijo | Ausente | Derivación contextual, premisas y regla en prueba |
| C10 | W1 `s_expression_runtime.py`: retract, why, ambigüedad, define | Ausente; `06 §6` lo reconoce | API semántica observable; escritura vía plan |
| C11 | W1 `core/evidence.py`: múltiples citas, stats, historia, derived_from | Parcial: evidencia por arista y journal | Conservar múltiples soportes sin confundirlos con verdad |
| C12 | W1 `routing/context.py`, `context_route.py`: taxonomía y rutas | Árboles disponibles, evaluación ausente | Separar ruta estructural de autorización de transporte |
| C13 | W1 `pipeline/convergence.py`: contextos × rasgos | Dirección; sin CHIC integrado | Taxonomía derivada con soporte y configuración versionados |
| C14 | W1 `symbol_convergence`, `converge_in_batches`: equivalencia y proyección dirigida | Parcial: `semantic equivalent-add` es equivalencia de tags | Correspondencia contextual, dirección, corroboración y revisión |
| C15 | W1 `maintain_wigame`: gates C_k, discriminantes D_i | Ausente | Refinamiento de mundos preservando anchors y hechos |
| C16 | W1 `sia/{intensity,cohesion,aroma,hierarchy,similarity}.py` | Ausente | Reutilizar por frontera, calibrar sin presentar frecuencia como prueba |
| C17 | W1 `pipeline/query.py`: ask con ruta y cita | `graph query/path` son consulta y recorrido documental | Resolver símbolos y evaluar dentro de mundos; justificar cada transporte |
| C18 | W1 `prototypes/shrdlu`: aclaraciones, foco, anáfora, why, conceptos nuevos | Ausente; UI pospuesta | Preservar comportamiento por API/session; REPL/UI pueden venir después |
| C19 | W1 `tests`, `experiments`, `corpus/physicists`: oro, controles negativos, límites de memoria | Buena suite de bytes/v1; sin evaluación semántica equivalente | Añadir gates de conocimiento, no solo recuperación con citas |
| C20 | W1 serialización YAML de WiGames | CAS/revisiones más fuertes, sin importador de juegos | Snapshot lógico interoperable que no pierda reglas/soporte/rutas |
| C21 | W2 `core/relations/order.py`, `relation_algebra.py`: orden estricto/total | `graph order` topológico; no axiomas de R | Conservar irreflexividad, antisimetría y total sobre clausura |
| C22 | W2 `routing/middle_term.py`: objeto→sujeto entre relaciones | Ausente | Composición con término medio y sentido de ambos tramos; no inventar R nueva |
| C23 | W2 `system/shape_guard.py`: mundos degenerados, objetos empaquetados | Validación de forma EDN, no de forma lógica | Diagnóstico explicable previo a evaluación |
| C24 | W2 `system/{hypertensor,partition}.py`: ensamblado por rutas, energía, poder discriminativo | Ausente | Vista derivada acotada; aceptar capacidades sin obligar matriz global |
| C25 | W2 `kernel/rule_schema.py`: variables y grounding | Ausente | Reglas esquemáticas con universo/contexto y coste declarados |
| C26 | W2 `system/reflection.py`: relaciones/contextos reificados | Contexto es nodo; reflexión operacional ausente | Correspondencia reversible `rel:`/`ctx:` y referencias colgantes detectables |
| C27 | W2 `system/projection_emit.py`: salida determinista a S-expr | Markdown desde árbol y modelos legacy | Generación lógica y documental desde instancia con procedencia |
| C28 | W2 imports OWL/UNL y legados | Migración sldb v1; no importador equivalente | Inventariar formatos útiles; diferir los no usados explícitamente |

Fuentes: [spec W1](/home/jp/proyectos/wittgenstein/matrix-shrdlu-spec/specs/SPEC.md), [convergencia W1](/home/jp/proyectos/wittgenstein/matrix-shrdlu-spec/specs/convergence.md), [capacidades W2](/home/jp/proyectos/wittgenstein/Matrix/Matrix/docs/semantic_fields.md), [inventario CLI Kimun](../../../v2/06-superficies-y-cli.md). C28 no está probado en esta corrida y no debe promoverse a requisito de compatibilidad sin seleccionar casos de uso.

## Consistencias que conviene preservar

- **Contextualidad y ruteo:** `14 P2/P3` coincide con convergence y con W2 `context_and_wigame.md`. El problema está en la forma mínima de los datos y en las superficies, no en la intención.
- **Procedencia y límites del LLM:** ambos distinguen extracción de evaluación. Las políticas concretas de ingesta no cumplen siempre esa separación; ver F06.
- **Localidad:** árboles por posiciones, anchors y Merkle ofrecen una base adecuada para invalidación local. W1 ya limita convergencia a pares candidatos y lotes.
- **Inmutabilidad y planes:** Kimun mejora las mutaciones in-place de Matrix mediante planes, capabilities, revisiones y verificación independiente de bytes.
- **Base reconstruible y motores sustituibles:** índices derivados y protocolo fuera de proceso permiten conservar SIA/NumPy sin repartir reglas de dominio por lenguajes.
- **Anclaje/generación:** la separación de `14 §18` encaja con el paper sobre representación superficial y con W2 `projection_emit`; no obliga al round-trip textual de v1.

## Qué agrega Kimun

Almacenamiento verificable, concurrencia por heads, Markdown con perfil y regiones opacas, modelos versionados, edición de campos/secciones, importación de v1, adaptadores de código/CLI, búsqueda documental, federación de stores, journal, HTTP y bundles. Son extensiones útiles, pero ninguna sustituye por sí misma C04–C18 y C21–C27.

El superset debe preservar la **semántica contextual** de esos comportamientos. Mantener literalmente la política de `observed/confirmed` o un bug de `ask` sería compatibilidad de defectos, no inclusión del propósito.
