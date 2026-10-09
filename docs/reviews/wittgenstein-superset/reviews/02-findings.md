# R2 — Consistencias e inconsistencias

Prioridad: **P0** bloquea el contrato de inclusión; **P1** puede producir respuestas o reconstrucciones incorrectas; **P2** deuda de coherencia, calidad o escala. «Reproducido» significa salida local guardada; «lectura» significa hallazgo en spec/código, sin reproducción completa. Las intenciones pendientes no se presentan como bugs implementados.

## Contrato y semántica

### F01 · P0 · Kimun no declara aún inclusión funcional de Wittgenstein

`docs/faq.md` deja motores M/G fuera de alcance; `05` y `06 §6` listan evaluación/derivados como no implementados; `12 §3` describe un adaptador futuro. Eso es compatible con la fase actual, pero insuficiente como contrato del objetivo que el dueño acaba de fijar. `graph query`/`graph path` recorren relaciones documentales, no implementan `WiGame`, `S_i`, deducción ni `ask`.

**Acción:** distinguir objetivo del producto, alcance del slice actual y estado de capacidades. Adoptar C01–C28 como checklist, con excepciones explícitas. Fuentes: [FAQ](../../../faq.md), [CLI §6](../../../v2/06-superficies-y-cli.md), [R1](01-coverage.md).

### F02 · P0 · Identidad de símbolos no es obligatoriamente contextual · reproducido

`node.cljc :term` exige `{:name :lang}`. Dos `she` con el mismo idioma y sin otra clave generan el mismo ID; el contexto vive después en evidencia de bindings. W1 exige `she@Curie ≠ she@Franklin`; `14 P2` rechaza símbolos universales. El mapa abierto permite añadir contexto, pero ninguna regla actual lo exige: no falta capacidad de agregar una clave, falta el invariante.

**Acción:** separar deduplicación de signo, identidad contextual de símbolo y equivalencia revisada. No eliminar CAS para conseguirlo. Fuente: [node.cljc](../../../../src/kimun/kernel/node.cljc), W1 `specs/convergence.md §1`, [probe Kimun](../../../../runs/subagents/2026-10-09-wittgenstein-superset/probe-kimun.log).

### F03 · P0 · Reglas anidadas declaradas, rechazadas por la forma actual · reproducido

`14 §4` dice que `:symbol/:proposition {:form …}` admite `(if …)`. `node.cljc` solo admite argumentos atómicos después del primer string; un vector con subformas dispara `:node/invalid`. W1 usa `if/and`; W2 además esquemas con variables.

**Acción:** definir IR recursivo y validación de operadores/aridad/tipos/contexto antes de ampliar el motor. Fuente: [14 §4](../../../v2/14-juego-del-lenguaje.md), [probe Kimun](../../../../runs/subagents/2026-10-09-wittgenstein-superset/probe-kimun.log).

### F04 · P0 · Verdad, sentido, soporte y curación comparten vocabulario incompatible

`02 §3.2` y `edge.cljc` usan `:status` para `sinnvoll/sinnlos/unsinnig`; `14 §5` lo redefine como `observed/confirmed/rejected/retracted`. `05` marca decisión 24 en revisión, mientras `14` declara §5 vigente. El draft del dueño pide verdad «anclado/derivado», mientras W1 distingue valor `TRUE/FALSE/UNKNOWN`, origen epistémico y curación. No es válido convertir automáticamente «derivado» en TRUE ni «retractado» en FALSE.

**Acción:** separar ejes y registrar una decisión de vocabulario. «Anclado/derivado» puede describir cómo se justifica el conocimiento sin borrar la distinción operacional false/unknown. Esto es propuesta, no interpretación ya aprobada. Fuentes: [edge.cljc](../../../../src/kimun/kernel/edge.cljc), [dudas G](../../../v2/drafts/capa-g-dudas.md), W1 `core/evidence.py` y `truth_value.py`.

### F05 · P1 · Autoridad humana y sentido estructural no están delimitados

`14 §5` reserva `unsinnig` al humano, pero Matrix considera inadmisibilidad estructural de firmas/ejes/contexto. W2 `middle_term.py` corta un tramo por sentido; el paper distingue tipo inválido de falso. El humano puede elegir fuentes, reglas y resolver ambigüedad sin que su aprobación vuelva admisible una forma inválida ni haga verdadera una proposición sobre la realidad.

**Acción:** separar autoridad para cambiar contratos de la evaluación bajo el contrato; explicar qué prueba el sistema y qué asume como source. Fuentes: [14 §5](../../../v2/14-juego-del-lenguaje.md), W2 `docs/context_and_wigame.md`, paper `sections/03_proposed_representation.md`.

### F06 · P1 · W1 ingesta LLM como observado y confirmado · reproducido

`pipeline/ingest.py::_evidence` pone `tier=observed`, `curation=confirmed`, `confidence=1.0` para toda declaración, incluso `source='llm'`, y la pasa a `assert`. W1 grounding declara que los hechos son observados **del juego del documento**, pero el límite entre «la fuente lo dice» y «ocurre en la realidad» no viaja como dato explícito. `14 §7` dice que prompt es siempre proposed; la decisión 31 está suspendida por el dueño.

**Acción:** guardar extracción y soporte verificado sin conceder verdad externa por procedencia LLM. Preservar la posibilidad de evaluar el mundo declarado por una fuente. Fuente: [probe W1](../../../../runs/subagents/2026-10-09-wittgenstein-superset/probe-wittgenstein.log).

## Anclaje, datos y reconstrucción

### F07 · P1 · Sucesión global sobre edición de una ocurrencia · reproducido

`revision.cljc::replace-op` reemplaza por `(tree,path)`, pero crea `supersedes(new,old)` y `re-anchor` opera sobre todas las aristas incidentes en `old`. La prueba usa el mismo texto en A/B, edita A y observa referencia nueva al sucesor mientras la ocurrencia B sigue viva. La arista no puede expresar que su intención era B. Es la semántica documentada del kernel, no una desviación accidental de implementación.

**Acción:** distinguir referencia a contenido de referencia a ocurrencia. Preservar deduplicación de bytes; acotar sucesión al sujeto lógico correcto. Fuente: [revision.cljc](../../../../src/kimun/kernel/revision.cljc), [probe Kimun](../../../../runs/subagents/2026-10-09-wittgenstein-superset/probe-kimun.log).

### F08 · P1 · Reconstrucción con parser/perfil actual no equivale al contrato original · lectura

`10 §11` nombra versión del parser en caché, pero el registro mínimo solo clase/anchor/raíces; `store_cache.cljc` usa versión global `"1"`. `doc-tree` recompone con `ast/parse` actual. No queda fijado allí todo el contrato de gramática, perfil, normalización y selector. Misma fuente con parser distinto puede producir otra raíz; `10` equipara erróneamente ese caso a fingerprint distinto.

**Acción:** registrar contrato completo por digest; distinguir `source-changed`, `parser-unavailable`, `contract-changed` y `rebuild-mismatch`. No diagnosticar todo como edición externa. Fuentes: [10 §11](../../../v2/10-escala.md), [doc-tree](../../../../src/kimun/surface/docs.cljc), [store-cache](../../../../src/kimun/host/store_cache.cljc).

### F09 · P1 · Caché borrable requiere precisar el alcance temporal · lectura

`14 §19` permite borrar cachés sin perder nada; `18.6` recompone de archivo+clase. Si se editó o borró el source, esos inputs ya no reconstruyen el árbol histórico. El formato 4 conserva revisiones y estados, pero eso no garantiza texto histórico disponible. No guardar copias es una decisión válida; prometer reconstrucción histórica incondicional no lo sería.

**Acción:** declarar garantía sobre fuente vigente, y distinguir lectura actual de revisión histórica. Para historia textual: locator de git/artefacto conservado o respuesta explícita de fuente histórica no disponible. No guardar texto por defecto «por si acaso».

### F10 · P0 · Datos generados sin contrato durable común · lectura

`18.9` pone autoridad en datos de instancia+modelo; `19` prohíbe contenido en la base y llama caché a campos extraídos. Puede resolverse haciendo durable el grafo, pero falta el contrato de dónde viven los valores y cómo se reconstruyen al ignorar `.kimun/`. Además, llamar al desk «generado» por haber sido escrito por deskops no prueba que existan otros datos fuente versionados de sus campos.

**Acción:** definir autoridad por documento y durable inputs. En ausencia de instancia persistida independiente, tratar Markdown existente como source de importación. Actualización durante la revisión: los modelos del desk se retiraron y el include quedó sin modelos/documentos hasta M2; no se propone restaurar ese legado. El riesgo de autoridad persiste para la regeneración futura. Fuente: [14 §18–§19](../../../v2/14-juego-del-lenguaje.md), [13 include](../../../v2/13-include.md), [FAQ](../../../faq.md).

### F11 · P1 · Base independiente, Merkle dependiente del modelo · lectura

`19.3` dice base sin dependencia de modelos; `20` define trackeado por selector del modelo. Falta la frontera que entrega la proyección a la base. También el hash de datos trackeados no basta si cambian contexto, regla o equivalencia mientras sus hojas siguen iguales.

**Acción:** base recibe registros de proyección opacos y contrato de productor; grafo registra dependencias semánticas además de anchors S. Fuentes: [14 §19–§20](../../../v2/14-juego-del-lenguaje.md), [propuesta store](../proposals/03-store-and-authority.md).

### F12 · P1 · Conversión de spans pierde soporte si se asume un offset único · lectura

W1 usa code points absolutos sobre texto limpio que conserva offsets; Kimun usa grafemas NFC de hojas que eliminan markup y unen líneas. No hay conversión general `offset-documento → offset-hoja` por suma/resta; una oración puede cruzar hojas y NFC puede cambiar cardinalidad.

**Acción:** mapa de correspondencias explícito con segmentos múltiples y spans raw conservados. Validar cita contra source hash y substring original. Fuente: W1 `specs/populator.md`, [04 §4](../../../v2/04-superficie-markdown.md), [02 §4.1](../../../v2/02-sustrato-computacional.md).

## Discrepancias del sistema que se pretende incluir

### F13 · P1 · W1 ask no aplica Si · reproducido

`pipeline/query.py::ask` filtra `fact.truth == true`, no sentido. Marcando `Si(a,b)=unsinnig`, la consulta devuelve TRUE. W2 `_leg_holds` sí exige TRUE+sinnvoll. Una suite verde de W1 no cubre la garantía del paper `W*=V⊙S` en esta superficie.

**Acción:** un único predicado de visibilidad/admisibilidad para consulta, composición, deducción y generación. Fuente: [probe W1](../../../../runs/subagents/2026-10-09-wittgenstein-superset/probe-wittgenstein.log), W2 `routing/middle_term.py`.

### F14 · P1 · Falso explícito de símbolo calificado se devuelve como UNKNOWN · reproducido

En W1 `_is_explicitly_false`, se compara `normalize(column)` con signo pedido. `column='b@ctx'`, consulta `'b'`, celda FALSE: no coincide. La ruta de positivos usa signos y no tiene la misma política. El probe obtiene `∅`.

**Acción:** resolver signo a símbolo contextual una vez; no interpretar IDs como lenguaje superficial en cada rama. Fuente: [probe W1](../../../../runs/subagents/2026-10-09-wittgenstein-superset/probe-wittgenstein.log).

### F15 · P1 · Trazabilidad total no es invariante de W1 · reproducido

`Fact` acepta evidencia vacía y la rellena sin `prov`; `validate_evidence` permite `prov` ausente. El probe agrega un hecho sin cita. Es razonable para mundos construidos a mano, pero contradice «ningún hecho entra sin cita» si no hay categoría separada de axioma/regla/input manual.

**Acción:** source anclado o decisión/axioma registrado; justificación puede ser cadena de reglas hasta raíces, no necesariamente una cita directa por cada derivado.

### F16 · P1 · Proyección lógica no es backup completo · lectura

W2 `matrix_to_s_expressions` filtra TRUE+sinnvoll, pero `wigame_to_s_expressions` emite todo fact TRUE sin revisar Si. La salida de sistema no conserva FALSE/UNKNOWN, citas, flags algebraicos, contextos, reglas ni proyecciones. Su docstring de «rebuild» necesita alcance preciso.

**Acción:** separar generación de lenguaje, export de verdades visibles y snapshot durable completo. Fuente: W2 `system/projection_emit.py`, `tests/test_projection_emit.py`. Los 64 tests seleccionados pasan; no prueban fidelidad de backup de todo el mundo.

### F17 · P1 · Justificación alternativa y retractación necesitan truth maintenance · lectura

W1 `deduction.py::_source_ids` junta fuentes en una lista; no distingue «todas necesarias» de «varias pruebas alternativas». `retract` invalida dependientes encontrados por ID en una pasada. Se debe comprobar punto fijo y alternativas, no asumir que toda retractación vuelve UNKNOWN ni que un recálculo recuperará siempre el estado correcto.

**Acción:** representaciones explícitas AND/OR de pruebas; recálculo acotado por dependencias y ciclos. Fuente: W1 `system/{deduction,s_expression_runtime}.py`. No se afirma aquí haber reproducido todos los casos de retractación transitiva.

## Coherencia documental y oportunidades perdidas

### F18 · P2 · Autoridad documental no integra las adendas

`14` conserva P15 de idioma/plantilla fija junto a decisión 38 que lo niega; describe upgrade 2→3 por rebuild frente a `10`/`05` que lo documentan en sitio; `07` pone contrato en arista derived, `10 §11` lo mueve a reference. La tabla de autoridad evita algunos choques, pero no reemplaza integración por tema.

**Acción:** separar contrato objetivo, contrato implementado y registro histórico. Cada decisión con dueño, estado y tests asociados; no reescribir hechos históricos como si fueran actuales.

### F19 · P0 · El inventario de Wittgenstein omite capacidades de W2

`12 §3` privilegia triples/procedencia/matrices. No inventaría órdenes, término medio, shape guards, partición, esquemas, reflexión ni generación de W2. Portar solo W1 perdería capacidades reales; portar el bloque global de W2 sin límites violaría la política de escala de W1.

**Acción:** inclusión por capacidad y fixtures, no por «elegir una rama entera». Fuentes: [R1 C21–C27](01-coverage.md), W2 `docs/semantic_fields.md`.

### F20 · P2 · Resultados de aislamiento no prueban verdad ni mejora universal

W1 reporta basura aislada 1.0 frente a hechos reales aislados 0.96 en modo LLM; convergencia 5/79; QA con salto 0.048. Aislar casi todo no demuestra verificación factual. El paper usa operaciones de bits para sostener costes `O(1)`, pero búsqueda, grounding, clausura y construcción no son constantes respecto del tamaño del mundo.

**Acción:** medir cobertura junto a falsos positivos, soporte alcanzable, QA, coste y casos fallidos. Separar comprobación de celda preindexada de coste total de formalizar/consultar. Fuentes: W1 `specs/convergence.md §7`, `docs/methodology.md`; paper `sections/04_discussion.md`.

### F21 · P2 · Límites cuadráticos/combinatorios necesitan alcance operacional

W1 prohíbe `p×p` global pero reconoce jerarquía O(p²) por nivel con `max_features`; W2 ensambla bloque global y expande variables sobre productos cartesianos. «Matrices por contexto» no basta si un contexto crece sin límite.

**Acción:** budgets de ejes, pares, grounding, hops y memoria; límites con respuesta parcial explícita. Un snapshot global útil para inspección puede ser opt-in sin volverse representación obligatoria.

### F22 · P1 · Equivalencia de tags y enlace de stores no autorizan cruces de contextos

Kimun incorpora stores enlazados por defecto a `graph`; `semantic equivalent-add` opera sobre tags globales. En Wittgenstein el transporte requiere proyección/equivalencia contextual. Poder encontrar un nodo remoto no significa que una premisa remota valga en el mundo local.

**Acción:** separar alcance de búsqueda física, autoridad de source y licencia de inferencia/ruteo. Cualificar stores además de contextos; exigir justificación por salto. Fuentes: [06 graph](../../../v2/06-superficies-y-cli.md), [semantic.cljc](../../../../src/kimun/surface/semantic.cljc), W1 `specs/convergence.md`.

### F23 · P2 · Colisiones de nombres limitan corpus reales

`13` identifica documentos por stem y rechaza nombres iguales aunque las rutas difieran. W1 chunking también usa stem como ctx_id. Un corpus con `a/README.md` y `b/README.md` necesita identidad por source/ruta, no el nombre corto compartido. La propuesta de Merkle por ruta ofrece una oportunidad directa para corregirlo.

**Acción:** nombre corto es alias, source y contexto son cualificados; un alias ambiguo devuelve candidatos. No convertir ruta mutable en identidad permanente sin política de moves.

### F24 · P2 · Composición inversa y conocimiento como código están pospuestos

El paper conecta AST con representación lógica; W2 ya tiene reflexión y esquemas. Kimun dispone de adapters de código, pero los transforma principalmente en documentos legacy y deja el evaluador anclado archivado. Esto pierde un caso de uso fuerte del superset: justificar hechos sobre código con tests y versionar cambios de reglas/contratos.

**Acción:** incluir un segundo fixture vertical de código tras el fixture textual, usando hashes de input, resultado de prueba, contrato y alcance de lo probado; un test verde no valida cualquier afirmación sobre el programa.

### F25 · P1 · Atomicidad del store no cubre escritura de source · lectura

`fields.cljc::mutation` y `docs.cljc::create/update-typed` hacen commit antes de escribir archivo. La escritura posterior puede fallar y devolver warning; además, `spit` no compara before-image bajo la misma acción. Esto no viola necesariamente el contrato vigente de warning de CLI, pero se vuelve crítico cuando el archivo es autoridad y el árbol cacheado deja de ser durable: la revisión puede observar texto nuevo que el source no recibió, o sobreescribir una edición externa posterior a la lectura.

**Acción:** especificar transacción de producto con before-image, intent durable, escritura por rename y recuperación. Distinguir commit de conocimiento de proyección pendiente; una acción no se anuncia como source actualizado si el archivo falló. Fuente: [fields.cljc](../../../../src/kimun/cli/commands/fields.cljc), [docs.cljc](../../../../src/kimun/cli/commands/docs.cljc). No se simuló crash/edición concurrente en esta revisión.

### F26 · P2 · Bundle de base no acredita instalación del superset

`08` distribuye bb+jar+pod y declara cliente Python pendiente; un wrapper semántico de SIA/Matrix añadirá runtime y dependencias distintas. Protocolo externo es una buena frontera, pero no resuelve por sí solo descubrimiento de capacidades, versión compatible, instalación offline ni disponibilidad en cada plataforma.

**Acción:** declarar paquete de base y paquetes/motores semánticos soportados con manifests, smoke tests y respuesta explícita de capability unavailable. No prometer «sin dependencias» para el producto completo por heredar el bundle actual. Fuente: [08 distribución](../../../v2/08-distribucion.md), [release.edn](../../../../release.edn).
