# Concilio 2026-11-14 — Claude: interpretación y autoridad documental (frontera S–M)

Solo investigación. No se modificó producto ni `docs/`, no se ejecutaron tests, no hay commit.
Brief: `runs/research/2026-11-14/concilio/brief.md`. Lectura del coordinador: `runs/research/2026-11-14/concilio/coordinador.md`.
No leí código: el aterrizaje en el kernel es de Pi; lo que digo del kernel sale de `docs/v2/02-sustrato-computacional.md`.

## 0. Resumen

1. La corrección del 2026-11-14 **retira la mitad de D0**: "identidad = lugar estructural para S" ya no vale.
   La otra mitad ("el hash no es identidad") queda confirmada por tercera vez (10-09, 10-13, 11-14).
2. D0, tal como está escrita en `docs/v2/drafts/preguntas-abiertas.md`, **ya no es una pregunta bien formada**:
   sus opciones (a) y (b) presuponen identidad por lugar. Hay que reformularla antes de desbloquear K1, K2b y S2.
3. La corrección **no está registrada en ningún documento con autoridad**. Hoy vive en un brief y en `runs/`.
   Mientras no entre a `docs/v2/`, la tabla de autoridad (`docs/v2/README.md`) sigue dando por vigente lo contrario.
4. Propuesta (mía, no aprobada): la identidad la funda el **acto de marcar** y la conserva la **continuidad registrada**;
   el lugar pasa a ser **localizador** y el hash, **huella de un estado**. La forma del identificador queda abierta.

## 1. Qué dice la corrección y cómo la leo

| # | texto del dueño (brief) | lectura | grado |
|---|---|---|---|
| 1 | kimun es como el sistema nervioso de un sistema autopoiético de conocimiento | el sistema autopoiético es el conocimiento con sus fuentes y sus actores; kimun es un subsistema suyo, no el sistema | literal |
| 2 | detecta señales (mutaciones de topología y quizás acciones); permite regulación y otras acciones | sensar es el núcleo; "acciones" como señal es condicional ("quizás") | literal; alcance de "acciones" abierto |
| 3 | Merkle es la sensorización | el Merkle no identifica ni guarda: detecta y localiza el cambio | literal |
| 4 | los nodos representan qué tenemos sensorizado, normalmente documentos y código | el nodo es la unidad sensada, no el contenido ni el símbolo | literal |
| 5 | identidad NO es hash NI anclaje estructural/topológico | ni huella ni lugar identifican | literal |
| 6 | hay que definir otra cosa que identifique topologías marcadas y cambios en el tiempo (ej. archivo en git) | falta un concepto; el dueño no lo fijó | literal: **es un encargo, no una decisión** |
| 7 | git inspira, pero aquí hay niveles de abstracción | la misma mutación es un hecho distinto según el nivel | literal |
| 8 | dos verdades: (1) lo marcado no cambió desde la huella; (2) si se marcan ejecuciones, verdad del resultado | dos verificaciones de la frontera S–M; la (2) es condicional | literal |
| 9 | seguimos entre S y M; G viene solo después | nada de esto decide G ni D-G | literal |

Dos cuidados de interpretación:

- **"Archivo en git" no es un UUID.** Git no le da identidad al archivo: tiene huellas (blob), lugares (ruta en un tree)
  y observaciones fechadas (commit); "el mismo archivo" a través de un rename lo **infiere** por similitud. El ejemplo
  nombra justo lo que git deja sin objeto propio: la cosa que persiste mientras cambian su ruta y sus bytes. Leerlo
  como "usar rutas" o "usar un id acuñado" es poner más de lo que dice.
- **El sistema nervioso no es el organismo.** En Maturana el sistema nervioso es una red cerrada, subsistema
  componente de la unidad autopoiética (`docs/research/autopoiesis/sources/maturana-1975-reprint-1999.txt`, líneas 33–41 y 245–254).
  La advertencia de `docs/research/autopoiesis/README.md` ("no establece que un documento sea un sistema autopoiético")
  sigue en pie y la corrección la respeta: un documento no se produce a sí mismo; su unidad la distingue quien marca.

## 2. Qué aportan las fuentes (y qué no)

Maturana, definiciones (txt líneas 110–114, 133–138, 150–163, 177; el README las ubica en pp. 150–152 del PDF, que
prevalece para citar; no verifiqué la paginación contra el PDF):

- **Unidad**: lo que una operación de distinción separa de un fondo.
- **Organización**: las relaciones que la constituyen como unidad y definen su clase.
- **Estructura**: los componentes y relaciones concretos; determina "el espacio en que existe y puede ser perturbada".
- Si cambia la estructura sin cambiar la organización, "la unidad sigue siendo la misma y su identidad no cambia"; un
  **cambio de estado** es un cambio estructural sin pérdida de identidad.
- Dos unidades separadas pueden tener la misma organización y distinta estructura; reproducir una unidad **particular**
  exige organización y estructura.

Aplicación a kimun (interpretación de diseño, no dicho por las fuentes):

| distinción | en kimun | consecuencia |
|---|---|---|
| operación de distinción | marcar: el modelo de anclaje selecciona qué se trackea (`14 §18.7`, `§20`) | la unidad nace al marcarla; la "membrana" es el contrato de selección |
| organización | clase y contrato de observación (`14 §18.1–3`) | misma clase ≠ misma unidad (dos atoms no son un atom) |
| estructura | bytes y topología actuales | el **lugar** pertenece a la estructura: por eso no identifica |
| estado | la estructura en una observación | el **hash** es la huella de un estado: por eso no identifica |

La corrección del dueño (ni hash ni lugar) coincide con esta lectura. Las fuentes no dicen nada de IDs, Merkle ni
persistencia, y la identidad autopoiética de Varela (§2.1–2.2 de la traducción) es autoproducida: la de una unidad de
kimun es **atribuida por quien marca**. Esa diferencia hay que mantenerla escrita.

## 3. Contradicciones con los documentos actuales

Orden: de mayor a menor autoridad (`docs/v2/README.md`).

| # | documento | dice | corrección 11-14 | situación |
|---|---|---|---|---|
| C1 | `docs/v2/01-orden-filosofico.md §4.3` (l. 72–74), `§2` (l. 45–48) | identidad de un nodo = su contenido; dos signos idénticos son el mismo nodo; "el nodo es el símbolo, no el signo" | el nodo es lo sensorizado; contenido igual no hace una unidad | contradice. El recuadro de revisión (l. 7–11) solo cubre la mitad del hash |
| C2 | `docs/v2/14-juego-del-lenguaje.md §21` (l. 510–516), respuestas del dueño 10-09 | IDs **estructurales** para lo que ancla; ID estructural roto = borrado o mutación | identidad no es anclaje estructural | **el dueño revisa su propia respuesta**. Sobrevive: la pérdida de soporte se declara; el hash detecta la mutación |
| C3 | `14 §21` tabla (l. 493–498) | ID del nodo = "nombre, ruta, símbolo" | la ruta no identifica | contradice en "ruta" |
| C4 | `14 §18.8` (l. 430–431) | dos documentos idénticos tienen los mismos nodos | dos marcas son dos unidades | contradice (tensión ya anotada en `§21`, ahora resuelta contra `§18.8`) |
| C5 | `14 §2` (l. 77–79) frente a `01 §3` (l. 57–59) | `14`: S = bytes, M = **estructura**, identidad = hash. `01`: S = signos y árboles sintácticos, M = **símbolos** | S = texto como signo; M = desacople estructural/semántico | **S y M tienen dos definiciones vigentes** y ninguna es la de hoy. Hay que fijar una antes de escribir el contrato S–M |
| C6 | `14 §7` (l. 155–156) | "como la identidad es el hash, solo lo que cuelga de hojas cambiadas pierde el anclaje" | — | la regla de granularidad puede sobrevivir; su fundamento no |
| C7 | `docs/v2/02-sustrato-computacional.md §6` (l. 463), `§2.1`, `§1`, invariantes 2, 13 y 17 | identidad = hash; todo objeto se nombra por su hash | no | contradice; es el contrato que el kernel implementa. El aviso de cabecera (l. 3–5) remite a "ids estructurales para lo que ancla", que también cae |
| C8 | `02 §2.1` (`:sign/:external`), `§6.5` | el localizador va **dentro** del contenido hasheado; el método `:fingerprint` exige el mismo localizador | localizador ≠ identidad ≠ huella | mover un archivo cambia el id del nodo y deja sin método exacto la reconciliación. Para Pi |
| C9 | `docs/v2/05-estado.md §1` (l. 9–11), decisiones 1, 4 y 46 | "motor content-addressed"; 46 (pendiente): ids estructurales para S, semánticos para M/G | — | la 46 queda partida: mitad confirmada, mitad retirada |
| C10 | `docs/v2/12-ecosistema-y-pendientes.md §1`, filas D0, K1 y S2 | "identidad = lugar"; K1 = "ids por lugar"; S2 = anclas por rangos de bytes | — | D0 y K1 mal formulados. K2a, K3, K6 y P1 no dependen de esto. S2 queda como trabajo de **localizador** |
| C11 | `docs/v2/drafts/preguntas-abiertas.md` D0; `drafts/fuentes-identidad.md §4.2–4.3, §5`; `drafts/ids-desde-matrix.md` (fila S) | opciones (a) taxonómico puro y (b) CAS + ocurrencias con IDs, ambas sobre identidad por lugar | — | pregunta mal formada. Las fuentes de Matrix tratan identidad **semántica** (M/G): no cubren la unidad sensada |
| C12 | `runs/research/2026-10-13/concilio/g-profundo/integracion-d0-g-completo.md §1, §3.2`; `pi-d0.md` | "D0 resuelto: la identidad es el lugar"; opciones A (posicional) y B (ULID + localizador) | — | la atribución al dueño de "identidad = lugar" queda desmentida. A cae como identidad; B se parece a la corrección pero **no está aprobada** |
| C13 | `AGENTS.md` ("Resuming work", puntos 2–3) | D0 = identidad de nodo; principio "hash ≠ identity" | — | apunta a una D0 que ya no se puede responder como está |

Lo que **no** contradice, y conviene decirlo para no reescribir de más:

- `docs/v2/00-nucleo.md` 2.3, 2.4, 2.41, 6.2, 6.3: anchor verificable, revisar al mutar, declarar la pérdida, no copiar
  el ambiente. Son ya la descripción de un sensor. 3.3 habla de identidad **semántica** (símbolos) y no se toca; solo
  cambia el enunciado de D0 que cita. Falta en el núcleo una proposición sobre la unidad sensada: es una adición, y el
  núcleo "no agrega decisiones", así que entra después de registrada la corrección.
- `01 §8` (bucle autopoiético: `drifted`/`orphan` como señales) y `01 §4.3`, segunda frase: "la identidad a través de
  ediciones es sucesión, y se registra, no se adivina". Esa frase, el invariante 7 de `02 §10` y la decisión 10
  (responde quien acepta, no el heurístico) son el precedente directo de la continuidad registrada.
- `14 §19–§20` (decisiones 43–44): store estable sin contenido, Merkle sobre lo trackeado, organizado por ruta. Que
  el **sensor** se organice por ruta no hace de la ruta una identidad. K2a sigue en pie.
- `02 §3.1`: los árboles ya tienen id nominal y head, "porque un id por contenido cambiaría con cada edición". El
  kernel ya separa unidad y estado a nivel de documento. Es un precedente, no una aprobación de ULID para lo demás.
- Verdad 1 ya existe como estados de anclaje (`02 §6.2`, decisión 9) y verdad 2 como anchor de evaluación (`14 §7`,
  `00-nucleo` 2.3; evaluador S4 no implementado, `docs/archive/v2/09-evaluador-anclado.md`). Ninguna de las dos es
  el eje de verdad de G: D-G y las decisiones 24 y 31 siguen como estaban.
- `docs/research/autopoiesis/README.md`, contexto del 10-10 ("un nodo refiere a la cosa y su membrana; otro, a sus
  estados"; el soporte puede ser un fragmento o una ejecución): coincide con la corrección y la antecede.

## 4. Propuesta: identidad, unidad, estado, localizador y contrato

**Todo este apartado es propuesta.** Vocabulario mínimo:

| término | qué es | puede cambiar sin que cambie la unidad |
|---|---|---|
| **unidad marcada** | lo que se sensa; nace de un acto de marcar registrado (qué se distinguió, bajo qué contrato, en qué observación) | — |
| **contrato de observación** | cómo se distingue y se lee la unidad: clase, perfil, parser, selector, normalización (K6 de `12 §1`) | no en silencio: cambiarlo es otro contrato y se declara aparte del cambio de fuente |
| **localizador** | dónde está hoy en la topología (ruta, posición estructural, rango) | sí; es evidencia para encontrarla, no su nombre |
| **huella** | hash de la proyección trackeada bajo el contrato (`14 §20.3`) | sí; cada huella es un estado |
| **observación** | (unidad, localizador, contrato, huella, revisión): un hecho fechado | se acumulan; no se reescriben (`00-nucleo` 6.4) |
| **continuidad** | relación entre observaciones que dice "es la misma unidad", "viene de" o "terminó" | se registra por quien hizo el cambio o la acepta un actor; un heurístico solo propone |

Identidad = referencia a la unidad marcada, sostenida por su cadena de observaciones y continuidades. Cómo se escribe
esa referencia (acuñada, derivada del acto de marcar, compuesta) **queda abierto**: lo que la propuesta fija es de qué
depende y de qué no. No depende de la huella ni del localizador; ambos son atributos de cada observación.

Por nivel (los niveles son mi lectura de "aquí hay niveles de abstracción"; el dueño no los enumeró):

| nivel | unidad | la distingue (contrato) | localizador | huella de | verdad 1 se invalida cuando |
|---|---|---|---|---|---|
| S0 fuente | un archivo marcado (documento, módulo de código) | la inclusión: `kimun.include.edn` y su clase (`docs/v2/13-include.md`) | ruta en el ambiente | sus bytes | cambian los bytes |
| S1 parte marcada | sección, campo, función: una topología dentro de una fuente | modelo de anclaje sobre la estructura (`14 §18.7`) | posición en la estructura de su fuente | su proyección trackeada | cambia la proyección; el resto del archivo puede cambiar sin tocarla |
| S–M | el vínculo entre una parte marcada y lo que expresa | el contrato de lectura que desacopla forma y sentido | las unidades S que lo soportan | las huellas de esos soportes | muta cualquier soporte: se revisa lo que depende (`00-nucleo` 2.4) |
| ejecución (condicional) | una corrida marcada de programa o rutina | expresión, entradas, motor y versión | — | entradas y resultado | muta una entrada (verdad 1); además su resultado tiene verdad propia (verdad 2) |

Tres reglas que se siguen:

1. **La mutación es relativa al nivel.** Mover una función a otro archivo y tocar su cuerpo son dos ediciones en S0,
   un traslado con edición en S1 y quizá nada en S–M. El sensor informa por nivel; no hay un único "qué pasó".
2. **Huella intacta acredita solo lo que cubre.** Una proyección intacta no dice nada del resto del archivo, y una
   huella bajo un contrato viejo no dice nada bajo el nuevo.
3. **Igualdad de bytes nunca prueba continuidad** y la falta de evidencia se muestra. Estados que debe poder dar la
   verificación: vigente, mutada, no localizada, contrato cambiado, continuidad ambigua. Se corresponden con
   `intact`/`orphan`/`drifted`/`:ambiguous?` de `02 §6.2` y con los cuatro de K6; la correspondencia exacta es de Pi.

## 5. Casos

"Hoy" = lo que se sigue de `02 §6` y `14 §21`. "Propuesta" = bajo el apartado 4. Los casos se leen en un nivel a la vez.

| caso | qué ve el sensor | hoy | propuesta |
|---|---|---|---|
| **editar** | mismo localizador, huella distinta | el nodo cambia de id; `orphan` y reconciliación (`02 §6.5`). En `14 §21`: "mutación, el hash la detecta" | misma unidad, estado nuevo. Verdad 1 invalidada; se revisa lo que depende. Si la edición saca a la unidad de su contrato (deja de calzar con la clase o el selector), es fin de la unidad, no edición |
| **mover** | localizador viejo vacío; misma huella en otro lugar | `14 §21`: ID estructural roto = borrado, se declara; en el kernel, nodo externo nuevo y `orphan` (C8) | cambia el localizador. La unidad continúa **solo** con continuidad registrada (lo dijo quien movió) o aceptada por un actor; si no, "no localizada, con candidato". Nunca continúa en silencio |
| **mover y editar** | localizador y huella nuevos | dos sucesos sin relación, salvo heurística `:sample` | igual que mover, con evidencia más débil: propuesta, no hecho |
| **copiar** | dos localizadores, misma huella | un solo nodo con dos ocurrencias (`14 §18.8`, `02 §6`) | el original sigue. La copia es otra unidad **si se marca**; si no, es trackeable y no existe para el store. "Copiada de" es procedencia explícita, cuando se sabe |
| **dividir** | huella vieja ausente; dos regiones nuevas | sin regla. Dos sucesores posibles dan `:ambiguous? true` (`02 §6.2`) | no hay sucesor automático. Se declara una de dos: la unidad continúa en una parte y la otra es unidad nueva, o termina y nacen dos con procedencia "división de". Lo que dependía de ella va a revisión entero |
| **fusionar** | dos huellas ausentes; una región nueva | sin regla | simétrico: N a 1 con procedencia "fusión de". Los dependientes de cada origen se revisan por separado; no heredan el soporte por estar el texto |
| **borrar** | localizador vacío, sin candidato | `14 §21`: no se mantiene el nodo; se declara si soportaba conocimiento | fin de la unidad, declarado (`00-nucleo` 2.4). Su registro queda como historia (`00-nucleo` 6.4); lo que dependía queda sin soporte, visible |
| **cambiar el contrato** (no pedido; lo agrego porque se confunde con editar) | misma fuente, huella distinta por otro parser o selector | no se distingue (K6 abierto) | no es mutación de la fuente: es `contract-changed`. La unidad no cambió; cambió cómo se la mira |

## 6. Qué está fijado y qué es propuesta

**Fijado por el dueño** (con fecha y dónde consta):

- El hash no es identidad; detecta mutaciones (10-09, `14 §21`; 10-13, `runs/research/2026-10-13/concilio/g-profundo/README.md`; 11-14, brief).
- La identidad tampoco es el anclaje estructural o topológico (11-14, brief). **Solo consta en el brief.**
- kimun como sistema nervioso; Merkle como sensorización; los nodos representan lo sensorizado (11-14, brief).
- Dos verdades de este tramo: vigencia desde la huella; verdad del resultado si se marcan ejecuciones (11-14; antecedente 10-10 en `docs/research/autopoiesis/README.md`).
- El alcance es S–M; G después (11-14; antes en `docs/v2/drafts/capa-g-dudas.md`).
- La pérdida de soporte se declara, nunca se oculta (10-09, `14 §21`; `00-nucleo` 2.4).
- Store estable sin contenido, Merkle sobre lo trackeado (10-09, `14 §19–§20`).

**Encargado y sin decidir**: qué identifica las topologías marcadas y sus cambios en el tiempo.

**Propuesta de este informe**: identidad fundada en el acto de marcar y conservada por continuidad registrada; el
vocabulario del apartado 4; los cuatro niveles; las tres reglas; el tratamiento de cada caso.

**No aprobado, aunque aparezca en documentos**: UUID o ULID por unidad (opción B del 10-13), rutas como ID
(`drafts/ids-desde-matrix.md`), conservar el CAS para contenido (opción (b) de D0). Tampoco "el nodo borrado no se
mantiene" leído como "se borra su registro".

**Preguntas que solo el dueño puede responder** (propongo que sustituyan a D0):

1. ¿La unidad nace al marcarla, o lo trackeable no marcado ya tiene identidad?
2. Ante un traslado sin continuidad registrada, ¿el default es "pérdida declarada con candidato"?
3. ¿La unidad de una parte depende de la de su fuente (la función deja de ser la misma al cambiar de archivo), o los niveles son independientes?
4. La respuesta del 10-09 "ID estructural roto = borrado o mutación": ¿se retira, o se relee como regla del localizador?
5. ¿"Marcar ejecuciones" y "acciones como señal" entran en el contrato S–M, o se difieren?
6. ¿Qué definición de S y de M vale: la de `01 §3`, la de `14 §2` o la del brief (C5)?

## 7. Autoridad documental: qué habría que registrar (no hecho)

1. **Registrar la corrección donde tiene autoridad.** Una sección nueva en `docs/v2/14-juego-del-lenguaje.md` que
   marque como superadas la tabla y las respuestas de `§21` en lo que toca al lugar, y avisos en `01 §4.3` y en la
   cabecera de `02`. Hasta entonces, un agente que siga `AGENTS.md` leerá "identidad = lugar" como principio vigente.
2. **Reformular D0** en `docs/v2/drafts/preguntas-abiertas.md` y en la fila D0 de `12 §1`; reescribir K1 sin
   "ids por lugar". K1, K2b y S2 siguen bloqueados por la D0 nueva; G1 además por D-G.
3. **Partir la decisión 46** de `05-estado.md`: confirmada la parte del hash, retirada la del id estructural.
4. **Fijar S y M** (C5) en un solo lugar antes de redactar el contrato S–M; DOC1 de `12 §1` es el sitio natural.
5. **`docs/research/` no está en git** (`git status`: sin seguimiento), igual que `runs/research/2026-10-13/`. La
   aclaración del dueño del 10-10 y el concilio del 10-13 no tienen historia ni autoridad. Decidir si entran.
6. **Marcar el concilio del 10-13** (`integracion-d0-g-completo.md`, `pi-d0.md`) como superado en "identidad = lugar",
   para que nadie lo cite como dicho por el dueño.
7. `00-nucleo.md` no se toca hasta que 1 esté hecho.

## 8. Contraste con la propuesta de Pi (`runs/research/2026-11-14/concilio/pi.md`)

Coincidimos en lo principal: separar unidad, observación, localizador, estado y contrato; linaje explícito;
`contract-changed` aparte del cambio de fuente; señal ≠ acción; D0 y K1 mal formulados. Su `Unit / Observation /
Lineage` es mi unidad / observación / continuidad. Tres puntos de su propuesta no pasan los criterios del dueño tal
como están escritos:

| criterio | qué dice Pi | problema | ajuste para la síntesis |
|---|---|---|---|
| **Conservar el CAS no debe reinstalar una copia durable del ambiente** | §0 y §5: "dejar el CAS como almacenamiento de contenido/observaciones"; §3.1: "el CAS guarda objetos de contenido para deduplicar/verificar" | "objetos de contenido" durables es lo que el dueño retiró: `00-nucleo` 6.2 (no conserva copia del ambiente), `14 §19` regla 2 y decisión 43 (el store estable **nunca** guarda contenido), formato 3 (sin copia verbatim, `drafts/sesion-2026-10-08-09.md §3`). Para verificar basta la huella; deduplicar solo hace falta si se guarda el contenido | lo durable es lo propio de kimun: unidades, linaje y observaciones (localizador, huella, contrato). El contenido del ambiente solo en caché borrable. El direccionamiento por hash puede seguir nombrando esos registros y el caché; no fuentes. Excepción ya existente y explícita: `:content :inline` (`docs/v2/13-include.md §1`). A vigilar: el `:sample` del anchor externo es un fragmento durable del ambiente (`02 §2.1`) |
| **Un UUID no explica la continuidad** | §2 y §3.1: `Unit.id` "opaco, estable, asignado al marcar"; la identidad "debe sobrevivir a editar y mover"; §4: mover y editar = "misma unidad" | un id opaco nombra; no dice por qué la observación de hoy es de la misma unidad. Si el localizador no resuelve, el id no tiene a qué asirse: lo que lo reata es un registro de `Lineage` con actor y evidencia. La tabla de casos da la continuidad por hecha. Además "opaco asignado" figura como propuesta y la codificación como abierta: la primera ya descarta opciones (p. ej. derivarlo del acto de marcar) | la identidad es la cadena marca + observaciones + linaje; el id es solo su nombre y su forma sigue abierta. En la tabla: editar conserva la unidad mientras el localizador resuelva bajo el contrato; mover la conserva **solo** con linaje registrado |
| **Igualdad de hash no prueba movimiento** | §3.2: señal `moved` = "el localizador anterior no resuelve y el nuevo resuelve con el mismo estado" | el sensor infiere movimiento de una huella igual. No distingue mover de copiar y borrar el original, ni elige entre dos candidatos idénticos (duplicados, cabeceras de licencia, archivos vacíos). Contradice sus propias notas de §2, §3.4 y el matiz de §4 ("copiar y borrar el original no equivale a mover") | el sensor emite `missing` con candidatos y su evidencia ("misma huella en L′"), o `ambiguous`. `moved` existe solo como registro de linaje, declarado por quien movió o aceptado por un actor. Es la regla vigente: la sucesión se registra, no se infiere (`02 §10` inv. 7, decisión 10) |

Dos diferencias menores, para decidir en la síntesis:

- **Fila M.** Pi da para M unidad = símbolo, localizador = ruta de contextos, estado = hash de la forma canónica. Eso
  entra en la identidad semántica (`00-nucleo` 3.3, fuentes de Matrix), que la corrección de hoy no tocó, y presupone
  la definición de M de `01 §3`. Yo dejo en este tramo solo el vínculo S–M y pido fijar antes qué es M (C5).
- **"Última observación aceptada".** Si toda mutación exige aceptación para volver a estar vigente, hay que decir
  quién acepta una edición ordinaria; si no, V1 se compara contra la huella registrada al marcar o al reconciliar.

Su punto 16 (`:sample` de 80 caracteres en `src/kimun/surface/docs.cljc` frente a 200 grafemas en `02`) es un hallazgo
de código que no verifiqué.

Límites de este informe: no contrasté contra el código ni contra los PDF; las citas de Maturana y Varela son de los
`.txt` derivados. No se diseñó G ni se tocó D-G.
