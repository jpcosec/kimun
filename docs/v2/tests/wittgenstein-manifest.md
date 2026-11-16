# Manifiesto de inclusión Wittgenstein (`C01–C28`, `W1` + `W2`)

> Estado: **contrato documental de alcance (`T01`)**. Inventaría qué capacidades de ambas líneas de
> `wittgenstein` (`W1`: `matrix-shrdlu-spec`; `W2`: `Matrix/Matrix`) se exigen para declarar a `kimun`
> un superconjunto de conocimiento contextual, cuáles quedan diferidas y cuáles se excluyen.
> Fuentes normativas: [`01-superset-contract.md`](../../reviews/wittgenstein-superset/proposals/01-superset-contract.md),
> [`01-coverage.md`](../../reviews/wittgenstein-superset/reviews/01-coverage.md),
> [`02-findings.md`](../../reviews/wittgenstein-superset/reviews/02-findings.md) y
> [`05-roadmap-and-gates.md`](../../reviews/wittgenstein-superset/proposals/05-roadmap-and-gates.md).
> Representación legible por máquina: [`test/fixtures/wittgenstein/manifest.edn`](../../../test/fixtures/wittgenstein/manifest.edn).

## 1. Reglas del contrato

1. **Superconjunto por semántica observable.** Cada capacidad `C` se evalúa por input canónico, verdad,
   sentido, contexto, ruta, soporte/procedencia y efectos observados, no por copia de código Python,
   IDs aleatorios, timestamps, layout YAML ni prosa del REPL.
2. **Honestidad de estado (`direction` ≠ `implemented`).** El estado en `kimun` usa cuatro valores
   excluyentes: `implemented`, `partial`, `direction` (solo diseño en [`14-juego-del-lenguaje.md`](../14-juego-del-lenguaje.md))
   y `absent`. Ninguna intención documental se computa como implementada.
3. **Respeto a decisiones abiertas.** Este manifiesto no especula sobre `D0` (identidad positiva de nodos,
   [`preguntas-abiertas.md`](../drafts/preguntas-abiertas.md)) ni sobre `D-G` (separación verdad/sentido/curación,
   [`capa-g-dudas.md`](../drafts/capa-g-dudas.md)), ni implementa la capa `G`.
4. **Inmunidad ante defectos heredados.** Ninguna capacidad exige reproducir bugs conocidos de `W1`/`W2`
   (`F06`, `F13`, `F14`, `F15`, `F16`, `F17`). Los controles negativos (`N-F*`) forman parte obligatoria de
   la aceptación.
5. **Presupuesto de escala (`memcap`).** La base opera sin `G`, la capa `G` vive como paquete separado sobre
   la base (decisión 43) y no se exige materializar matrices globales `p×p` (decisión 26).

### Resumen cuantitativo al 2026-11-14

| Métrica | Cantidad | IDs |
|---|---|---|
| Total capacidades inventariadas | **28** | `C01–C20` (`W1`, 20) + `C21–C28` (`W2`, 8) |
| Disposición `required` | **25** | `C01–C17`, `C19–C23`, `C25–C27` |
| Disposición `deferred` | **3** | `C18` (solo diálogo REPL completo en prosa; vista mínima del circuito/señales sigue requerida), `C24` (hipertensor opt-in), `C28` (OWL/UNL sin consumidor) |
| Disposición `excluded` (elementos transversales) | **5** | `X01–X05` (ver §3) |
| Estado en `kimun` hoy: `implemented` | **0** | — (ningún ítem contextual de `G` cerrado aún) |
| Estado en `kimun` hoy: `partial` | **12** | `C01`, `C02`, `C11`, `C12`, `C14`, `C17`, `C19`, `C20`, `C21`, `C23`, `C26`, `C27` |
| Estado en `kimun` hoy: `direction` | **5** | `C03`, `C05`, `C06`, `C07`, `C13` |
| Estado en `kimun` hoy: `absent` | **11** | `C04`, `C08`, `C09`, `C10`, `C15`, `C16`, `C18`, `C22`, `C24`, `C25`, `C28` |

---

## 2. Matriz de inclusión `C01–C28`

> Convención de columnas: **Bloqueado por** usa IDs de [`12-ecosistema-y-pendientes.md §1`](../12-ecosistema-y-pendientes.md#1-pendientes-en-kimun)
> (`D0`, `D-G`, `K1`, `K6`, `K7`, `M1`, `S2`, `G1`, `G2`) y la tarea de cierre propuesta en
> [`05-roadmap-and-gates.md`](../../reviews/wittgenstein-superset/proposals/05-roadmap-and-gates.md) (`T02–T14`).

### 2.1 Línea `W1` (`wittgenstein/matrix-shrdlu-spec`, `C01–C20`)

| ID | Fuente verificable (`W1`) | Disposición | Estado hoy | Bloqueado por | Fixture · Control | Resultado esperado | Evidencia actual en `kimun` | Desviación deliberada vs `W1` |
|---|---|---|---|---|---|---|---|---|
| `C01` | `populator/extract.py`, `contexts.py`: ingesta de texto, chunks, signos locales y offsets | `required` | `partial` | `S2`, `K6` (`T05`) | `F-W01` · `N-F12` | Mapeo verificable crudo→NFC→hoja conservando spans multipunto y hash del source | CST/AST Markdown y spans por hoja (`04`, `test/kimun/surface/`) | Cita se verifica sobre bytes/grafemas NFC por hoja, no offsets planos sobre texto mutado (`F12`) |
| `C02` | `specs/convergence.md §1`: símbolo = signo en contexto (`s = (w, c)`) | `required` | `partial` | `D0`, `K1`, `G1` (`T03`) | `F-W01` · `N-F02` | Todo símbolo exige contexto; `she@Curie` y `she@Franklin` jamás se fusionan por string | `node.cljc :term` admite `{:name :lang}` sin contexto obligatorio (`F02`) | Deduplicación estructural de signos se separa de identidad contextual pendiente de `D0` |
| `C03` | `pipeline/decompose.py`, `decompose_batch.py`: declaraciones heurísticas/LLM + caché offline | `required` | `direction` | `G1` (`T07`) | `F-W02` · `N-F06` | Adaptador declarativo determinista offline que emite proposiciones con source, contexto y soporte | Solo intención en `14 §7`; no hay adaptadores (el subsistema se borró el 2026-11-15) | Rechaza `F06`: salida LLM/heurística entra como `:proposed`, nunca `:confirmed` automático |
| `C04` | `pipeline/ingest.py`: construcción de `WiGame` por `(contexto, relación)` | `required` | `absent` | `D0`, `D-G`, `G1` (`T08`) | `F-W03` · `N-F15` | Mundos tipados por `(contexto, relación, contrato)` evaluables de forma aislada | Triple `G` existe solo como clase de nodo de datos en el kernel (`02`) | Escrituras vía `TransactionPlan`/revisión inmutable en lugar de mutación in-place |
| `C05` | `core/li_space.py`, `system/wigame.py`: espacio `Li`, admisibilidad y ejes | `required` | `direction` | `D-G`, `G1` (`T02`/`T08`) | `F-W03` · `N-F13` | Firma local de `R` y espacio de argumentos distinguiendo `:unsinnig`/`:inapplicable` de `:false`/`:unknown` | Diseño en `14 §4–§8` | El carácter `:unsinnig` por estructura no se anula por aprobación humana (`F05`) |
| `C06` | `ViMatrix`, `SiMatrix`: verdad (`Vi`) separada de sentido (`Si`) | `required` | `direction` | `D-G`, `G1` (`T02`) | `F-W03` · `N-F14` | Ejes ortogonales verdad, sentido, soporte y curación; falso explícito distinguible de ausencia | `edge.cljc :status` cubre sentido; `14 §5` redefine eje (en revisión `D-G`, `F04`) | Preserva `FALSE` explícito aunque se califique el símbolo (`F14`); no iguala derivado con `TRUE` |
| `C07` | `core/relations`: álgebra de relaciones (simetría, inversión, transitividad, exclusión) | `required` | `direction` | `G1` (`T06`/`T11`) | `F-W04` · `N-F22` | Propiedades declaradas por `(contexto, R)` y aplicadas al cierre contextual | Diseño en `14 §4` | Las propiedades no son flags globales por nombre de relación (`14 P3`) |
| `C08` | `kernel/formulas.py`, `rule_matrix.py`: fórmulas anidadas y conectivos | `required` | `absent` | `G1` (`T06`) | `F-W05` · `N-F03` | IR lógico recursivo (`not`, `and`, `or`, `implies`, `iff`, `xor`) validado en aridad y alcance | `:symbol/:proposition` en `node.cljc` rechaza subformas anidadas (`F03`) | Contrato explícito de esquema/aridad antes de evaluar o persistir regla |
| `C09` | `system/deduction.py`: modus ponens, conjunciones, punto fijo | `required` | `absent` | `G1` (`T10`) | `F-W05` · `N-F17` | Derivación contextual con registro de premisas, regla y árbol AND/OR de prueba | Pendiente (`G1`/`T10`) | Separa pruebas conjuntivas y alternativas; sin ciclos infinitos (`F17`) |
| `C10` | `s_expression_runtime.py`: `retract`, `why`, `ambiguity`, `define` | `required` | `absent` | `G1` (`T10`) | `F-W05` · `N-F17` | API semántica observable: retracción con mantenimiento de verdad, explicación de prueba y resolución | Reconocido como faltante en `06 §6` | Toda escritura ocurre mediante `TransactionPlan` y revisiones enlazadas |
| `C11` | `core/evidence.py`: múltiples citas, conteos, historial, `derived_from` | `required` | `partial` | `K6`, `G1` (`T04`/`T08`) | `F-W02` · `N-F15` | Múltiples soportes anclados por hecho sin confundir frecuencia de citas con verdad | Evidencia tipada en aristas y en el log (`02 §3.2`, `06`) | Rechaza `F15`: no confirma hechos sin ancla en source o decisión/axioma registrado |
| `C12` | `routing/context.py`, `context_route.py`: taxonomía de contextos y rutas | `required` | `partial` | `K7`, `G1` (`T08`) | `F-W06` · `N-F22` | Rutas (`ancestor`, `sibling`, `bridge`) trazables con distinción entre alcance físico y licencia | Árboles y jerarquía de tags disponibles; evaluación de rutas ausente | Rechaza `F22`: enlace de stores o taxonomía no autoriza inferencia sin licencia |
| `C13` | `pipeline/convergence.py`: convergencia jerárquica `CHIC` (`contextos × rasgos`) | `required` | `direction` | `G1` (`T09`) | `F-W07` · `N-F02` | Taxonomía derivada con configuración, semilla y soporte versionados | Solo dirección; sin CHIC integrado | Resultado reproducible desde caché/inputs durables; límites de escala explícitos (`F21`) |
| `C14` | `symbol_convergence`, `converge_in_batches`: equivalencia y proyección dirigida | `required` | `partial` | `K7`, `G1` (`T09`) | `F-W01` · `N-F02` | Proyección dirigida (`project_to`, `as_seen_by`) con corroboración y revisión humana explícita | `semantic equivalent-add` opera solo sobre tags globales (`F22`) | Nunca fusiona por similitud superficial ni eleva equivalencia de tags a identidad de mundo |
| `C15` | `maintain_wigame`: gates `C_k`, discriminantes `D_i`, split/merge | `required` | `absent` | `G1` (`T10`) | `F-W07` · `N-F13` | Refinamiento de mundos conservando anclas, hechos previos y trazabilidad de compuertas | Pendiente (`G1`/`T10`) | Puertas inadmisibles se conservan explícitamente como `:unsinnig`, no como falso |
| `C16` | `sia/{intensity,cohesion,aroma,hierarchy,similarity}.py`: métricas SIA | `required` (frontera) | `absent` | `G1` (`T07`/`T09`) | `F-W07` · `N-F06` | Ejecución tras protocolo fuera de proceso o vista derivada reproducible con presupuestos | Pendiente (`14 §13`) | Frecuencia/cohesión nunca se presentan como prueba deductiva (`14 §13`, `F20`) |
| `C17` | `pipeline/query.py`: `ask` con ruta, soporte y cita | `required` | `partial` | `G1` (`T08`) | `F-W06` · `N-F13` | Resuelve signos al contexto pedido, evalúa `Vi` y `Si`, justifica transporte y cita source | `graph query/path` recorren grafo documental, no mundos `G` (`F01`) | Rechaza `F13`: un hecho con `Si = :unsinnig` jamás devuelve `TRUE` |
| `C18` | `prototypes/shrdlu`: aclaraciones, foco, anáfora, `why`, conceptos nuevos | `deferred` (solo REPL completo en prosa) / `required` (API sesión `T13` + vista mínima `12 §1`) | `absent` | `G1` (`T13`) | `F-W08` · `N-F17` | Contrato de sesión/aclaración ligado a revisión (`pending` obsoleto no muta store) | Solo el REPL completo en prosa está diferido (`docs/faq.md`); la vista mínima del circuito/señales sigue requerida (`12 §1`) | Se evalúa por semántica de estado/sesión API y vista mínima del circuito, no por prosa literal del REPL (`X04`) |
| `C19` | `tests`, `experiments`, `corpus/physicists`: oro, negativos, memoria | `required` | `partial` | `G1` (`T08`–`T14`) | `F-W01` · `N-F20` | Suite de aceptación con controles negativos, medidas cold/warm y presupuesto `memcap` | Suite fuerte de kernel/bytes/v1 (`bb test`, `bb oracle`, `memcap`) | Mide verdad, sentido y alcance alcanzable además de citas/QA (`F20`, `F21`) |
| `C20` | Serialización YAML de `WiGames` (snapshot completo) | `required` (snapshot lógico) / `excluded` (layout YAML nativo) | `partial` | `G1` (`T12`) | `F-W09` · `N-F16` | Export/import lógico determinista que conserva reglas, falsos explícitos, soportes y rutas | CAS y revisiones en EDN más fuertes que YAML; falta snapshot de mundos `G` | Sustituye layout YAML ad-hoc (`X03`) por snapshot lógico versionado sin pérdidas (`F16`) |

### 2.2 Línea `W2` (`wittgenstein/Matrix/Matrix`, `C21–C28`)

| ID | Fuente verificable (`W2`) | Disposición | Estado hoy | Bloqueado por | Fixture · Control | Resultado esperado | Evidencia actual en `kimun` | Desviación deliberada vs `W2` |
|---|---|---|---|---|---|---|---|---|
| `C21` | `core/relations/order.py`, `relation_algebra.py`: axiomas de orden estricto y total | `required` | `partial` | `G1` (`T11`) | `F-W04` · `N-F22` | Reglas algebraicas de orden estricto/total validadas sobre clausura contextual | `graph order` ejecuta orden topológico de nodos, no axiomas de relación | Propiedades ligadas al contexto del juego, sin contaminar otros contextos |
| `C22` | `routing/middle_term.py`: composición objeto→sujeto entre relaciones distintas | `required` | `absent` | `G1` (`T11`) | `F-W04` · `N-F13` | Inferencia por término medio con witness explícito y verificación de `Si` en ambos tramos | Pendiente (`G1`/`T11`) | Si un tramo es `TRUE` y el otro `:unsinnig`, corta la composición y no inventa relación nueva |
| `C23` | `system/shape_guard.py`: guarda de forma lógica (mundos degenerados, objetos empaquetados) | `required` | `partial` | `G1` (`T11`) | `F-W03` · `N-F03` | Diagnóstico explicable previo a evaluación para aridades rotas o signos empaquetados | Validación de forma EDN de modelos/nodos, no de forma lógica | Distingue error de forma estructural (`shape_guard`) de proposición falsa |
| `C24` | `system/{hypertensor,partition}.py`: ensamblado por rutas, energía, poder discriminativo | `deferred` | `absent` | `G1` (`T11`+) | `F-W07` · `N-F21` | Vista derivada/optimización opt-in ejecutable dentro del presupuesto `memcap` | Pendiente | Prohibido exigir matriz global `p×p` o dependencia obligatoria de JAX/GPU (decisión 26) |
| `C25` | `kernel/rule_schema.py`: esquemas de reglas con variables y grounding finito | `required` | `absent` | `G1`, `G2` (`T11`) | `F-W05` · `N-F21` | Reglas con variables (`?x`) acotadas a universo/contexto finito y cota de costo declarada | Pendiente | Nunca crea símbolo literal `"x"` para la variable ni expande producto cartesiano sin cota |
| `C26` | `system/reflection.py`: reflexión de relaciones y contextos reificados (`rel:`/`ctx:`) | `required` | `partial` | `G1` (`T11`) | `F-W10` · `N-F05` | Correspondencia reversible entre meta-nivel y nivel objeto; detecta referencias colgantes | Contextos existen como nodos; reflexión operativa ausente | Sujeta a contrato y revisión para no confundir reificación con verdad empírica |
| `C27` | `system/projection_emit.py`: emisión determinista de S-expresiones y documentos | `required` | `partial` | `M1`, `G1` (`T12`) | `F-W09` · `N-F16` | Generación determinista lógica y documental desde instancia con procedencia preservada | Emisión Markdown desde árbol y modelos v1 (`docs render`, `M1` en diseño) | No exige round-trip textual estricto de v1 cuando la fuente es generada (`14 §18`) |
| `C28` | Importadores OWL/UNL y formatos legacy de `W2` | `deferred` | `absent` | `G1` (fuera de corte inicial) | `F-W09` · `N-F16` | Inventariados como adaptadores posibles; requieren consumidor y fixture dedicados antes de comprometer paridad | Nada implementado (la migración desde `sldb v1` se borró el 2026-11-15) | Sin promesa de cobertura completa OWL/UNL hasta aprobación explícita de alcance |

---

## 3. Exclusiones deliberadas (`excluded`) y elementos diferidos (`deferred`)

| ID | Categoría | Elemento | Motivo explícito |
|---|---|---|---|
| `X01` | `excluded` | Kernels globales históricos, modelos sin contexto y código legacy abandonado de `W1`/`W2` | Violarían el principio contextual (`14 P2`) y la restricción de escala (decisión 26) sin aportar semántica útil |
| `X02` | `excluded` | Causalidad experimental (`appendix_causality` de `W1`) | Co-ocurrencia estadística no demuestra intervención causal; permanece fuera del contrato de conocimiento |
| `X03` | `excluded` | Layout interno YAML de `WiGames`, IDs aleatorios y timestamps como identidad o autoridad | `kimun` usa CAS, planes transaccionales y trazas verificables; la equivalencia es sobre semántica lógica (`C20` cubre snapshot lógico) |
| `X04` | `excluded` | Equivalencia textual de prosa interactiva del REPL | La compatibilidad con `C18` se mide por estado de sesión, desambiguación, foco y `why` en API |
| `X05` | `excluded` | Herencia de defectos conocidos de `W1`/`W2` (`F06`, `F13`, `F14`, `F15`, `F16`, `F17`) | Sería compatibilidad de bugs: auto-confirmación LLM, `ask` ciego a `Si`, pérdida de `FALSE` al calificar, hechos sin cita, snapshots con pérdida y retracción insegura |
| `D01` | `deferred` | Diálogo REPL conversacional completo en prosa (`C18`) | Solo el REPL completo en prosa queda diferido; **la vista mínima del circuito y de las señales (`12 §1`, frente *Operación y vista*) y el contrato de estado/sesión API (`T13`) siguen siendo requeridos** (el sistema cuaja cuando se puede ver el recorrido del cambio a la señal y a la respuesta). La visualización no está fuera de alcance. |
| `D02` | `deferred` | Aceleración JAX/GPU, hipertensor/partición global (`C24`) e importadores OWL/UNL (`C28`) | Optimizaciones o integraciones opt-in sujetas a presupuesto de memoria y selección explícita de consumidor |

---

## 4. Fixtures canónicos mínimos (`F-W01`..`F-W10`) y controles negativos (`N-F*`)

Cada fixture está especificado en forma declarativa en [`test/fixtures/wittgenstein/manifest.edn`](../../../test/fixtures/wittgenstein/manifest.edn) sin prefijar la codificación interna de `D0` ni `D-G`:

| Fixture | Propósito principal | Capacidades cubiertas | Control negativo asociado |
|---|---|---|---|
| `F-W01` | **Curie / Franklin**: dos sources con `"she"` y `"Nobel"`, símbolos contextualizados, spans multipunto y proyección revisada | `C01`, `C02`, `C14`, `C19` | `N-F02`: `"she@Curie"` y `"she@Franklin"` jamás colapsan por coincidencia de string; editar párrafo en Curie invalida solo su soporte sin tocar Franklin |
| `F-W02` | Extracción multi-cita (`observed`/`proposed`) y distinción procedencia LLM vs source anclado | `C03`, `C11` | `N-F06` y `N-F15`: una declaración LLM entra como `:proposed` (no `:confirmed`), y un hecho sin ancla ni axioma es rechazado |
| `F-W03` | Mundo tipado `WiGame` (`Vi`, `Si`, firma `Li` y `shape_guard` con falso explícito calificado) | `C04`, `C05`, `C06`, `C23` | `N-F14` y `N-F03`: `FALSE` explícito bajo calificación de símbolo sigue siendo `FALSE` (no `UNKNOWN`); objeto empaquetado dispara `shape_guard` antes de evaluar |
| `F-W04` | Álgebra de relaciones por contexto, orden estricto/total y composición por término medio | `C07`, `C21`, `C22` | `N-F22` y `N-F13`: tramo `TRUE` + tramo `:unsinnig` corta la composición; propiedad de `R` en contexto A no se filtra a contexto B |
| `F-W05` | Fórmulas anidadas, reglas esquemáticas con `?x` en universo finito y deducción + `retract`/`why` | `C08`, `C09`, `C10`, `C25` | `N-F17`, `N-F03` y `N-F21`: perder una prueba alternativa no elimina otra prueba válida; ciclo no crea raíz; `?x` no crea símbolo literal `"x"` |
| `F-W06` | Ruteo entre contextos (`bridge` licenciado vs enlace físico) y `ask` filtrado por `Si` | `C12`, `C17` | `N-F13` y `N-F22`: `ask` nunca devuelve `TRUE` cuando `Si = :unsinnig`; un enlace físico sin puente aprobado no transporta premisas |
| `F-W07` | Convergencia `CHIC`, métricas SIA acotadas, refinamiento (`C_k`/`D_i`) e hipertensor opt-in | `C13`, `C15`, `C16`, `C24` | `N-F21` y `N-F06`: puertas inadmisibles quedan `:unsinnig` (ausencia ≠ falso); tamaño acotado bajo `memcap`; frecuencia SIA nunca sustituye prueba |
| `F-W08` | Sesión/aclaración de ambigüedad ligada a revisión del store | `C18` | `N-F17`: confirmación sobre revisión obsoleta se rechaza sin mutar el estado vigente |
| `F-W09` | Emisión determinista desde instancia y round-trip de snapshot lógico completo | `C20`, `C27`, `C28` | `N-F16`: exportar e importar preserva `FALSE`, `:unknown`, `:unsinnig`, citas, reglas y rutas sin degradar a solo `TRUE` |
| `F-W10` | Reflexión controlada (`rel:`/`ctx:`) y verificación de referencias colgantes | `C26` | `N-F05`: reificar una relación inválida no la vuelve admisible; referencia colgante se reporta explícitamente |
