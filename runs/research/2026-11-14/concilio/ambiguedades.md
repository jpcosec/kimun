# Mapa de ambigüedades abiertas: S–M, identidad, heartbeat y relaciones/embeddings

> **Premisa metodológica (distinción entre orientación del dueño e inferencias del coordinador, 2026-11-14):**
> - **Orientación textual del dueño (`docs/v2/14-juego-del-lenguaje.md`, cabecera):**
>   - La identidad del nodo **no es el hash ni su anclaje estructural en la topología**.
>   - **Autopoiesis y VSM aplican al sistema, no a la unidad**; la unidad permite recursión.
>   - Sistema concebido para LLMs: **unidad = nodo con identidad propia**, espacio mínimo de convergencia con sentido relacional discreto, semántico y estructural, además de **embeddings continuos**. Su valor proviene de relaciones declarables: jerárquicas, horizontales, métricas y/o recursivas (herencia, temporales, secuenciales).
>   - **Propuesta sobre el daemon:** el dueño propone un daemon cuya **única función** sea el *heartbeat* para recalcular señales (momento de autopercepción), en un sistema asíncrono donde mutaciones, señales externas y respuestas a acciones internas permitan activar respuestas. **La forma arquitectónica del daemon no es una decisión aprobada más allá de esa función única propuesta.**
> - **Inferencias y acotaciones del coordinador (no son mandatos textuales del dueño ni decisiones cerradas por él):**
>   - `embedding ≠ identidad` y `similitud continua ≠ relación validada` (inferencias de lectura del coordinador para no colapsar el plano continuo con el discreto).
>   - Retirar como definición general obligatoria del concilio que cada unidad deba ser una elaboración/pregunta con ciclo propio de revisión.
>   - Acotar que el *heartbeat* propuesto no se expanda a ejecutor de LLMs ni imponga hoy un esquema persistente.

---

## 1. Frontera S–M (sensorización, selección y ejecuciones)

### A1.1 — Correspondencia entre nodo sensor (`S`) y unidad (`S–M / M`)
- **Pregunta:** Cuando se marca una porción del ambiente (función, sección Markdown), ¿el sensor topológico en `S` y la unidad donde convergen relaciones y embeddings son el mismo nodo o dos nodos vinculados? *(Divergencia observada entre `unidad-sintesis.md` l. 37–38 y `heartbeat-claude.md` l. 21–22).*
- **Opciones realmente abiertas:**
  1. **Nodo único convergente:** La unidad es un solo nodo; el anclaje Merkle en `S` es una faceta estructural suya (pudiendo una unidad tener 0, 1 o $N$ anclajes en `S`).
  2. **Bipartición sensor ↔ unidad:** El sensor en `S` es un nodo topológico recomputable del árbol y la unidad es un nodo distinto que declara una relación de soporte hacia uno o más sensores.
- **Qué bloquea:** El contrato entre `src/kimun/kernel/node.cljc` y `src/kimun/kernel/anchor.cljc`, y qué vive en el store estable frente al cache recomputable (`14 §19`).
- **Prueba / caso que discrimina:** Una unidad sustentada simultáneamente por dos fragmentos en archivos distintos (una función y su test) cuando uno de los dos archivos se borra: bajo **(1)** el nodo unidad conserva su identidad y pierde una faceta de anclaje; bajo **(2)** un nodo sensor desaparece/queda huérfano y la arista de soporte hacia él cambia de estado.

### A1.2 — Contrato de la Verificación 2 (marcar ejecuciones y verdad del resultado)
- **Pregunta:** Si se incorpora marcar ejecuciones (`14-juego-del-lenguaje.md`, cabecera), ¿qué artefacto exacto sensoriza `kimun` para verificar la verdad del resultado sin volverse un orquestador de procesos?
- **Opciones realmente abiertas:**
  1. **Recibo pasivo (atestación externa):** `kimun` sólo registra y verifica un recibo determinista emitido por corridas externas: `(merkle_sujeto, comando_declarado, exit_code, hash_salida, timestamp)`.
  2. **Contrato de predicado reproducible:** `kimun` declara qué comando puro/acotado (bajo `memcap`) corresponde a la marca, pero su ejecución la dispara un trabajador asíncrono fuera del *heartbeat* cuando el *heartbeat* señala que `merkle_sujeto` cambió.
  3. **Acotar el primer corte S–M a la Verificación 1 (huella topológica):** Posponer el contrato de ejecuciones a una segunda etapa de S–M.
- **Qué bloquea:** El alcance del primer corte verificable de S–M y la estructura de señales de invalidación de pruebas.
- **Prueba / caso que discrimina:** Una función marcada `f` no cambia su AST, pero muta una función auxiliar `g` invocada por `f` y el test falla: discrimina si la señal nace sólo cuando entra un recibo externo de fallo **(1)** o si el cambio en `g` invalida preventivamente la vigencia del recibo previo **(2)**.

### A1.3 — Retención de evidencia previa en `S` ante mutaciones
- **Pregunta:** Cuando un archivo muta en el entorno, ¿qué conserva `kimun` para informar qué cambió sin reinstalar un almacén CAS de todo el ambiente (`14 §19`)?
- **Opciones realmente abiertas:**
  1. **Sólo huellas y coordenadas estructurales:** `kimun` guarda únicamente `(merkle_hash, selector, firma_estructural)`; recuperar el texto anterior depende del VCS externo (`git`) si existe.
  2. **Extracto acotado exclusivamente a nodos *trackeados*:** El cache retiene el último fragmento/AST de las unidades explícitamente marcadas (nunca de lo meramente *trackeable*, `14 §20`) hasta que la señal `drifted` sea atendida.
- **Qué bloquea:** La limpieza de `format 4` (`docs/v2/12-ecosistema-y-pendientes.md §1`, `src/kimun/store/file.clj`) y el insumo que recibe un LLM cuando atiende una señal `drifted`.
- **Prueba / caso que discrimina:** Editar dos veces una función marcada en el árbol de trabajo **sin hacer commit en `git`** y pedir al sistema explicar la deriva: **(1)** sólo puede reportar que el hash cambió; **(2)** puede entregar el diff del fragmento marcado al consumidor asíncrono.

---

## 2. Identidad de la unidad y continuidad temporal

### A2.1 — Formulación positiva de la «identidad propia» del nodo
- **Pregunta:** Sabemos qué **no** es la identidad (no es hash, posición, tema, rol ni embedding). ¿Cómo se representa y asigna en positivo la identidad propia de una unidad al crearse?
- **Opciones realmente abiertas:**
  1. **Identificador inmutable de nacimiento (opaco o secuencial por store) + tipo/clase de unidad:** Asignado en el acto explícito de declaración/marcaje e independiente de su contenido y ubicación.
  2. **Coordenada genética `(espacio, acto_de_creación, t_0)` + resolución por cadena de linaje:** La identidad es el punto de origen histórico y su estado actual se obtiene siguiendo las aristas temporales (`continues`/`moved`).
  3. **Identidad dual (ID interno inmutable + alias simbólico direccionable):** Un pivote opaco estable acompañado de un nombre jerárquico/relacional legible por humanos y LLMs (estilo Zettelkasten/folgezettel o namespace) que puede renombrarse sin romper identidad.
- **Qué bloquea:** La decisión `D0` (`docs/v2/drafts/preguntas-abiertas.md`), `src/kimun/kernel/node.cljc`, `edge.cljc` y la sintaxis de referencias en `docs/v2/04-superficie-markdown.md`.
- **Prueba / caso que discrimina:** Dos agentes marcan concurrentemente la misma función o crean una unidad equivalente en sesiones separadas antes de sincronizar: discrimina si producen dos identidades distintas que luego requieren una relación `merged`/`equivalente` o si comparten coordenada genética.

### A2.2 — Regla de transición de identidad ante mudanza, edición, `split` y `merge`
- **Pregunta:** Cuando una topología marcada cambia de lugar o se reescribe entre $t_1$ y $t_2$, ¿bajo qué condición exacta conserva la misma identidad de unidad frente a extinguirse o bifurcarse, y quién lo decide?
- **Opciones realmente abiertas:**
  1. **Continuidad siempre declarada fuera del *heartbeat*:** El *heartbeat* sólo emite señales (`drifted`, `orphan`, `candidate-move` con puntaje de evidencia estructural); sólo una declaración explícita (de usuario, CLI o agente consumidor) escribe el vínculo de continuidad.
  2. **Continuidad automática en casos deterministas unívocos + escalamiento en ambigüedad:** Si en un latido desaparece exactamente 1 subárbol marcado y aparece exactamente 1 subárbol con idéntica huella Merkle o selector inequívoco, se actualiza el anclaje registrando el desplazamiento; cualquier caso $1:N$, $N:1$ o con edición simultánea queda en `ambiguous`.
- **Qué bloquea:** `src/kimun/kernel/reconcile.cljc` y la frontera entre recálculo de señales (*heartbeat*) y mutación del grafo (`02 §10`, invariante 11).
- **Prueba / caso que discrimina:** Mover una función a otro archivo y editarle una línea en el mismo paso, existiendo además otra función parecida en el archivo destino: discrimina si el sistema se abstiene de reanclar en silencio (`ambiguous` + propuesta de candidato) o si arriesga un falso positivo.

---

## 3. Heartbeat propuesto y sistema asíncrono

### A3.1 — Forma operativa, memoria entre latidos y exposición de señales
- **Pregunta:** El dueño propone un daemon cuya única función sea el *heartbeat* (recalcular señales como momento de autopercepción), sin que su forma arquitectónica esté aún definida ni aprobada. Si el estado de anclaje es derivado (`02 §10`, invariante 18), ¿qué estado compara cada latido y cómo expone las señales recalculadas al resto del sistema asíncrono?
- **Opciones realmente abiertas:**
  1. **Vista materializada idempotente de estado actual:** Cada latido produce la fotografía actual de señales (`fresh`, `drifted`, `orphan`, `ambiguous`, `stale-embedding`); dos latidos sin cambios en las entradas producen exactamente el mismo resultado.
  2. **Snapshot previo en cache + flujo de eventos de transición (deltas):** El latido compara el estado calculado en $t_k$ contra el de $t_{k-1}$ y emite eventos discretos sólo cuando una señal cambia de estado.
  3. **Híbrido (vista idempotente + cursor/época monotónica por unidad):** Cada señal conserva el número de latido/época en que cambió por última vez, permitiendo consumo tanto por estado como por novedades desde un cursor.
- **Qué bloquea:** Definir el contrato mínimo del *heartbeat* propuesto y cómo se acoplan consumidores asíncronos sin fijar prematuramente una arquitectura de procesos.
- **Prueba / caso que discrimina:** Un consumidor asíncrono se desconecta durante 5 latidos en los que un archivo muta y vuelve a estabilizarse: bajo **(1)** lee la fotografía vigente al volver; bajo **(2)** necesita leer una cola histórica; bajo **(3)** consulta todas las señales con `epoch > ultimo_cursor`.

### A3.2 — Amortiguación y clausura de bucles ante «respuestas a acciones internas»
- **Pregunta:** En el sistema asíncrono descrito por el dueño, mutaciones, señales externas y **respuestas a acciones internas** pueden activar respuestas. ¿Cómo distingue el circuito una mutación nueva de la mutación causada por una respuesta interna, evitando oscilaciones o bucles infinitos?
- **Opciones realmente abiertas:**
  1. **Trazabilidad causal en la entrada del *heartbeat*:** Las acciones internas declaran un `causation-id` / huella esperada; cuando el *heartbeat* recalcula y observa exactamente esa huella, emite señal de `converged/ack` en lugar de una nueva alerta `drifted`.
  2. **Heartbeat agnóstico al origen + filtro en los consumidores/reguladores:** El *heartbeat* recalcula señales sin conocer quién causó el cambio; el componente que activa respuestas lleva el registro de acciones en vuelo y descarta re-disparar sobre el mismo par `(unidad, huella_resultante)`.
- **Qué bloquea:** El contrato de entradas y salidas entre el *heartbeat* propuesto y los mecanismos asíncronos de activación de respuestas.
- **Prueba / caso que discrimina:** Una acción interna actualiza una unidad o un archivo para resolver una señal `drifted`: en el siguiente latido, el sistema debe pasar la señal a `fresh` y estabilizarse, en vez de tratar esa escritura interna como una perturbación exógena que dispare otro ciclo idéntico.

---

## 4. Relaciones declarables y embeddings continuos (sistema para LLMs)

### A4.1 — Definición de «relaciones métricas» y frontera con los embeddings continuos
- **Pregunta:** El dueño incluye las relaciones **métricas** dentro de las relaciones declarables de la unidad (junto con jerárquicas, horizontales y recursivas) y añade que cada unidad tiene sentido relacional discreto, semántico y estructural, además de **embeddings continuos**. A su vez, el coordinador infiere que `embedding ≠ identidad` y `similitud ≠ relación validada`. ¿Qué es exactamente una relación métrica declarable y qué papel juega el embedding continuo frente a ella? *(Abierto en `heartbeat-claude.md` l. 57 y `heartbeat-pi.md` l. 41).*
- **Opciones realmente abiertas:**
  1. **Arista declarada con atributo cuantitativo explícito:** Una relación discreta entre dos unidades que porta un escalar o vector declarado (peso, costo, grado de acoplamiento, orden/latencia, nivel de soporte o confianza evaluada).
  2. **Métrica estructural derivada sobre el grafo discreto:** Distancia geodésica en el grafo, profundidad común de ancestros en el árbol recursivo, grado de co-citación o acoplamiento estructural, calculable determinísticamente en el *heartbeat*.
  3. **Relación declarada que registra como evidencia una medición continua congelada en el tiempo** (p. ej. divergencia o afinidad medida bajo un modelo y versión explícitos, validada por un actor).
- **Qué bloquea:** El esquema de aristas en `src/kimun/kernel/edge.cljc` y el lenguaje de consulta para LLMs.
- **Prueba / caso que discrimina:** Reemplazar el modelo de embeddings por otro distinto sin tocar las declaraciones del grafo: permite discriminar qué relaciones métricas permanecen intactas **(1 y 2)** y cuáles eran sólo vecindad vectorial transitoria.

### A4.2 — Ciclo de vida de los embeddings y su relación con el backlog (`G3`)
- **Pregunta:** En `docs/v2/12-ecosistema-y-pendientes.md §1` los embeddings figuraban como etapa tardía (`G3`, índice externo posterior a G), pero el mandato actual define la unidad con sentido discreto, semántico, estructural **y embeddings continuos**. ¿Cómo se articula su cálculo con S–M y el *heartbeat*?
- **Opciones realmente abiertas:**
  1. **Cálculo asíncrono fuera del *heartbeat*, gobernado por señal de obsolescencia (`stale-embedding`):** El *heartbeat* sólo detecta (por cambio de huella Merkle o de vecindad declarada) que el embedding de una unidad quedó desactualizado; un trabajador separado recalcula el vector y lo deposita en el cache indexado por ID de unidad.
  2. **Múltiples proyecciones continuas por unidad vs. vector único:** Un solo embedding por unidad (del texto/símbolo) frente a vectores separados por faceta (contenido semántico vs. firma estructural/relacional).
- **Qué bloquea:** La actualización del grafo de dependencias en `docs/v2/12-ecosistema-y-pendientes.md §1` y la garantía de que el *heartbeat* se mantenga ligero, determinista y bajo `memcap` (`AGENTS.md`).
- **Prueba / caso que discrimina:** Modificar 1.000 nodos en un repositorio y ejecutar un *heartbeat* sin GPU ni red disponible: bajo **(1)** el *heartbeat* completa el recálculo de señales discretas y marca `stale-embedding` sin bloquearse ni degradar la autopercepción estructural.

### A4.3 — Semántica de la recursión de unidades sobre relaciones (herencia, temporales, secuenciales)
- **Pregunta:** Si la unidad permite recursión (unidades compuestas por subunidades o ligadas por herencia), ¿cómo interactúan las relaciones declaradas en un nivel con los niveles interiores o heredados cuando un LLM consulta una unidad?
- **Opciones realmente abiertas:**
  1. **Composición explícita sin propagación implícita:** Las aristas pertenecen sólo al nodo donde se declaran; el ascenso/descenso por contención o herencia se realiza explícitamente en la consulta o al armar la ventana de contexto del LLM.
  2. **Propagación tipada según la clase de relación:** Las relaciones de `herencia` y `contención` propagan restricciones y contexto hacia abajo (salvo *override* explícito), las señales de mutación estructural (`drifted`) se propagan hacia arriba por el árbol Merkle, y las relaciones `secuenciales`/`temporales` permanecen locales a su escala.
- **Qué bloquea:** Las reglas de composición multi-escala del grafo y la construcción del «espacio mínimo de convergencia» que se entrega a un LLM al inspeccionar una unidad.
- **Prueba / caso que discrimina:** Declarar una invariante o dependencia sobre una unidad contenedora `M` (módulo/sección madre) y pedir a un LLM el espacio de convergencia de una subunidad hija `u` tras una mutación en `u`: discrimina si `u` expone por herencia el vínculo de `M` **(2)** o si aparece aislada salvo expansión explícita **(1)**.
