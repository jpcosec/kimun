# Informe del Concilio (Agy) — Frontera S–M: Límites y contraejemplos de identidad temporal/topológica

**Fecha:** 2026-11-14
**Rol en el concilio:** Agy — Límites y contraejemplos de identidad temporal/topológica, separación entre identidad / unidad / estado / localizador / contrato en los niveles entre S y M.
**Insumos principales:** `/home/jp/.cache/coord-briefs/kimun-2026-11-14.md`, `AGENTS.md`, `docs/research/autopoiesis/README.md` y fuentes locales (`sources/varela-maturana-uribe-1974.txt`, `sources/maturana-1975-reprint-1999.txt`, `sources/varela-intencionalidad-traduccion-2003.txt`), `docs/v2/00-nucleo.md`, `docs/v2/01-orden-filosofico.md`, `docs/v2/02-sustrato-computacional.md`, `docs/v2/05-estado.md`, `docs/v2/10-escala.md`, `docs/v2/12-ecosistema-y-pendientes.md`, `docs/v2/14-juego-del-lenguaje.md`, `docs/v2/drafts/{preguntas-abiertas,fuentes-identidad,ids-desde-matrix,capa-g-dudas}.md`, `docs/reviews/wittgenstein-superset/{reviews/02-findings.md,proposals/02-identity-and-anchors.md,proposals/03-store-and-authority.md}`.

---

## 1. Marco autopoiético y corrección del dueño (2026-11-14)

En la teoría autopoiética (`docs/research/autopoiesis/sources/maturana-1975-reprint-1999.txt` pp. 151–156; `sources/varela-maturana-uribe-1974.txt` §§2–4, §9; `sources/varela-intencionalidad-traduccion-2003.txt` §§2.1–2.2, §3.1):

1. **Organización vs. estructura (unidad vs. estado):** Una unidad compuesta se constituye y conserva su **identidad** mientras se mantenga invariante su *organización* (y la membrana/frontera que la distingue del fondo), aunque su *estructura* (componentes actuales, relaciones locales, posición espacial) cambie plásticamente en el tiempo (*ontogenia* = historia de cambios estructurales sin pérdida de identidad).
2. **Sistema nervioso como sensorización y regulación:** El sistema nervioso (`maturana-1975-reprint-1999.txt` pp. 154–157; `varela-intencionalidad-traduccion-2003.txt` §3.1) acopla perturbaciones/discontinuidades con estados internos que permiten compensación y regulación. En `kimun`:
   - Los **nodos** representan **qué tenemos sensorizado** (topologías marcadas en documentos, código y eventualmente ejecuciones).
   - El **árbol Merkle** es el aparato de **sensorización**: propaga señales cuando una topología marcada muta.
   - Las **dos verdades** operativas en la frontera S–M son:
     1. **Verdad de huella (inmutabilidad de lo sensorizado):** lo marcado no cambió desde que se tomó su huella; cualquier mutación en lo marcado la invalida.
     2. **Verdad de resultado (si se implementa marcar ejecuciones):** vigencia del resultado de una ejecución marcada bajo sus entradas y contrato; invalidada si muta el soporte o el contrato.

---

## 2. Contradicciones con la documentación y el kernel actuales

La corrección del dueño («**Identidad NO es hash NI anclaje estructural/topológico: hay que definir otra cosa que identifique topologías marcadas y cambios en el tiempo**») refuta tanto la tesis original del kernel (`identidad = hash`) como la tesis alternativa de la sesión del 2026-10-09 (`identidad en S = lugar/anclaje estructural`):

| Tema | Qué dicen los docs / código hoy | Dónde | Contradicción con la corrección 2026-11-14 |
|---|---|---|---|
| **Identidad = Hash (CAS)** | `id(nodo) = H(canonical-bytes {:class :kind :content})`. Dos signos con igual texto son el mismo nodo; editar un byte cambia la identidad. | `docs/v2/01-orden-filosofico.md` §4.3; `docs/v2/02-sustrato-computacional.md` §1, §2.1, §6; `src/kimun/kernel/node.cljc:75-84` | Confunde **igualdad de contenido/estado** con **identidad de la unidad**. Colapsa ocurrencias distintas con igual byte-stream (`F07` en `docs/reviews/wittgenstein-superset/reviews/02-findings.md` y `src/kimun/kernel/revision.cljc` `replace-op`/`re-anchor`) e impide que una topología marcada tenga estados sucesivos sin cambiar de identidad. |
| **Identidad en S = Lugar / Anclaje estructural** | Propone que para S la identidad sea el lugar taxonómico estructural (`src:desk/atoms/x.md › section:Answer › para:2`) o `source + locator`. | `docs/v2/14-juego-del-lenguaje.md` §21; `docs/v2/05-estado.md` dec. 46; `docs/v2/drafts/preguntas-abiertas.md` (D0); `docs/v2/drafts/fuentes-identidad.md` §§4–5; `docs/v2/drafts/ids-desde-matrix.md`; `docs/reviews/wittgenstein-superset/proposals/02-identity-and-anchors.md` | Confunde el **localizador topológico/estructural en un instante** con la **identidad temporal de lo marcado**. Si el archivo se mueve en git, o se inserta un párrafo antes (`para:2 → para:3`), o se renombra un heading, el localizador cambia pero la topología marcada sigue siendo la misma unidad. |
| **ID estructural roto = borrado** | Afirma que «un ID estructural que se rompe es un borrado o una mutación; si es borrado, no se mantiene el nodo». | `docs/v2/14-juego-del-lenguaje.md` §21 (líneas 511–516); `docs/v2/drafts/fuentes-identidad.md` §4 punto 3; `docs/v2/12-ecosistema-y-pendientes.md` §1 (K1) | Trata un **desplazamiento topológico** (mover archivo, reordenar secciones, refactorizar función de archivo) como destrucción ontológica de la unidad, porque ata la identidad a la coordenada estructural. |
| **Identidad de documento por stem o ULID de árbol atado a path** | `include` identifica documentos por stem (`a/README.md` y `b/README.md` chocan); el kernel usa ULID nominal por árbol (`:document`) pero `reconcile/by-fingerprint` busca el mismo `:locator` exacto. | `docs/v2/13-include.md` §1; `docs/v2/02-sustrato-computacional.md` §3.1, §6.5; `src/kimun/kernel/tree.cljc:29-60`; `src/kimun/kernel/reconcile.cljc:78-111` | `by-position` exige el mismo `(tree-id, path)` y `by-fingerprint` exige `(= loc (:locator c))`. Si un archivo cambia de ruta *y* se edita un carácter (caso habitual en git `mv` + edit), ambos métodos deterministas fallan y caen en heurística léxica (`by-sample` Dice) o `orphan`. |
| **Verdad y sentido en aristas `projection` y estado de afirmación** | `02 §3.2` y `edge.cljc:19-21` fijan `:status ∈ #{:sinnvoll :sinnlos :unsinnig}`; `14 §5` y dec. 24 usan `observed/confirmed/rejected/retracted`; `14 §7` y dec. 31 usan `proposed`. | `docs/v2/02-sustrato-computacional.md` §3.2; `src/kimun/kernel/edge.cljc:19-32`; `docs/v2/14-juego-del-lenguaje.md` §§5, 7, 12; `docs/v2/drafts/capa-g-dudas.md` | En la frontera S–M no se evalúa G: existen **dos verdades** de sensorización (1: vigencia de huella sobre lo marcado; 2: verdad del resultado de ejecución marcada). Mezclar estados de curación de G en el anclaje S–M contamina el sustrato. |
| **Autoridad del pool vs. sistema nervioso** | `01 §4.1` declara al pool «la única fuente de verdad»; `14 §2` y `§19` lo corrigen parcialmente pero `14 §2` aún dice para M «identidad = hash del contenido canónico». | `docs/v2/01-orden-filosofico.md` §4.1; `docs/v2/14-juego-del-lenguaje.md` §2 (tabla fila M), §19 | `kimun` no reemplaza al medio (archivos/git) como almacén primario de bytes ni congela M como hash de contenido: es el sistema nervioso que sensoriza topologías del medio y mantiene el desacople estructural/semántico S–M. |

---

## 3. Límites y contraejemplos de identidad temporal y topológica (Foco Agy)

A continuación se demuestran formalmente, mediante seis contraejemplos en la frontera S–M, por qué **ni el hash de contenido** ni el **anclaje estructural/topológico (ruta, path en AST, ordinal de bloque)** pueden ser la identidad de una unidad marcada:

### Contraejemplo 1 — Invarianza de identidad bajo cambio de localizador y de hash (`git mv` + edición mínima)
- **Escenario:** Un archivo `docs/spec.md` se mueve a `docs/v2/spec.md` y en el mismo paso se corrige una errata.
- **Fallo de `identidad = hash`:** El fingerprint del archivo y el hash del párrafo editado cambian (`H₁ ≠ H₂`). Para un CAS puro, es otro objeto sin relación intrínseca.
- **Fallo de `identidad = localizador topológico` (`src:docs/spec.md › ...`):** La coordenada raíz cambió (`src:docs/spec.md ≠ src:docs/v2/spec.md`). Bajo la regla de `14 §21` («ID estructural roto = borrado»), toda la red de anclajes hacia el documento se declara borrada aunque la unidad documental y su membrana sigan intactas.
- **Límite demostrado:** La identidad de la unidad marcada es ortogonal tanto a su coordenada espacial en el árbol de directorios (`locator`) como a su huella de bytes (`state/hash`).

### Contraejemplo 2 — Desplazamiento topológico interno por inserción de hermanos (Efecto Dominó en paths `[i j k]`)
- **Escenario:** En un documento o archivo de código, se marca el párrafo/función en la posición `[0 3]` (`section:Intro › para:3`). Luego se inserta un párrafo nuevo al principio de `section:Intro`. El párrafo marcado no sufrió ningún cambio interno (su texto y su hash son idénticos), pero ahora vive en `[0 4]` (`para:4`), y el antiguo `[0 2]` pasó a `[0 3]`.
- **Fallo de `identidad = path/anclaje estructural`:**
  - El ID `section:Intro › para:3` sigue existiendo en el árbol, pero ahora apunta a **otra unidad** (secuestro silencioso de identidad si solo se mira la existencia del path, o falsa alarma de mutación en `para:3` y `para:4` en cascada para todos los hermanos posteriores $k \ge 3$).
  - En `src/kimun/kernel/reconcile.cljc:78-93` (`by-position`), la reconciliación asume que quien ocupa el mismo `[tid path]` con el mismo `class/kind` es el candidato de reemplazo con confianza `0.9–1.0`, proponiendo erróneamente que el párrafo desplazado fue «reemplazado» por su vecino anterior.
- **Límite demostrado:** Un ordinal o coordenada en un árbol (`path`) es un **localizador efímero relativo a una revisión del contenedor**, nunca la identidad de la sub-topología marcada.

### Contraejemplo 3 — Homonimia estructural y colisión de contenido (Gemelos idénticos en topologías distintas)
- **Escenario:** Dos módulos tienen una cláusula estándar idéntica (p. ej. `"Ninguna pérdida de soporte se oculta."` o un bloque `try/catch` canónico), o una misma tabla repite dos celdas `"PENDIENTE"`. Luego se edita solo la primera ocurrencia.
- **Fallo de `identidad = hash` (reproducido en `F07`, `docs/reviews/wittgenstein-superset/reviews/02-findings.md`):** Ambas ocurrencias comparten el mismo `node-id` en `src/kimun/kernel/node.cljc`. Cuando `revision.cljc` aplica `:replace` en la posición A, emite `supersedes(new, old)` sobre el `node-id` compartido y re-ancla globalmente todas las aristas `reference`/`binding` que apuntaban a `old`, arrastrando las referencias que apuntaban a la posición B (que no cambió).
- **Límite demostrado:** La igualdad byte a byte (nivel de contenido) no implica ser la misma unidad sensorizada. Dos topologías marcadas pueden compartir exactamente el mismo estado/contenido en $t_0$ y divergir en $t_1$.

### Contraejemplo 4 — Refactorización trans-nivel (Extracción de función / promoción de sección a documento)
- **Escenario:** Una sección `## Sustrato` dentro de `01.md` se extrae a su propio archivo `02-sustrato.md` (o una función interna `parse-block` en `markdown.cljc` se mueve al namespace `cst.cljc`).
- **Fallo de `identidad = árbol/contenedor`:** En el kernel actual (`02 §3.1`), los árboles tienen un ULID nominal por documento, pero los nodos internos no tienen identidad de unidad propia fuera de su hash o de su path `[tid path]` dentro de ese único `tid`. Al cruzar la frontera de un árbol a otro (`tid₁ → tid₂`), la topología marcada pierde su contenedor original.
- **Límite demostrado:** Las topologías marcadas existen a **múltiples niveles de abstracción** (corpus/repo, archivo, sección/módulo, bloque/declaración, span/expresión, ejecución). Una sub-topología marcada debe poder conservar su identidad en M cuando se re-aloja en otro contenedor S (cambio de contenedor topológico sin pérdida de organización).

### Contraejemplo 5 — Mutación superficial vs. mutación de proyección (Reflow, formato o azúcar bajo el mismo contrato)
- **Escenario:** Un documento Markdown se reformatea (saltos de línea internos en párrafos, espacios en tablas, o se edita un párrafo de cortesía no trackeado según `14 §18.4, §20`).
- **Tensión temporal/topológica:**
  - Los bytes crudos de S cambiaron (`fingerprint` del archivo cambió; los offsets de bytes/líneas posteriores se desplazaron).
  - La topología marcada en la frontera S–M (la proyección trackeada bajo el contrato de parseo/anclaje) **no cambió** en su hash proyectado, aunque su localizador de bytes (`[byte-start byte-end]`) sí se desplazó.
- **Límite demostrado:** Si la verdad 1 («lo marcado no cambió desde la huella») se evalúa sobre el localizador crudo sin separar **localizador**, **contrato de sensorización** y **huella de la proyección marcada**, cualquier cambio en zonas no marcadas o de formato invalida falsamente los nodos sensorizados.

### Contraejemplo 6 — Mutación del contrato de sensorización con fuente inmutable (`F08`)
- **Escenario:** El archivo fuente no cambia ni un byte (`fingerprint` idéntico), pero cambia la versión de la gramática tree-sitter, las reglas de normalización o el selector del modelo de anclaje (`docs/v2/10-escala.md` §11; `F08` en `02-findings.md`).
- **Límite demostrado:** El estado sensorizado de una unidad en S–M es una función $\text{Estado} = f(\text{ Fuente en localizador}, \text{Contrato})$. Si el contrato no forma parte explícita de la huella del estado, el sistema nervioso confunde una mutación de sus propios órganos sensoriales (cambio de parser/selector) con una mutación del medio externo.

---

## 4. Propuesta por nivel: Identidad, Unidad, Estado, Localizador y Contrato (Frontera S–M)

Siguiendo la tabla de `docs/research/autopoiesis/README.md` («Unidad / estado / contenido», «Organización / estructura», «Membrana / ubicación») y la corrección del dueño (2026-11-14), toda referencia en la frontera S–M debe separar **cinco dimensiones**:

1. **Unidad y Membrana (Qué cosa se sensoriza):** La demarcación que distingue a la topología marcada de su fondo (ej. un archivo versionado, una sección/función delimitada por su rol estructural, un campo extraído, una ejecución acotada).
2. **Identidad de la topología marcada (Quién es en el tiempo):** Aquello que permanece invariante a través de sus cambios de estado y desplazamientos topológicos mientras no se destruya su membrana/organización, permitiendo trazar su historia ontogénica (ej. el linaje de esa unidad a través de revisiones, independiente de su hash actual y de su ruta actual). *Nota: el mecanismo concreto de representación —sea token/handle de linaje, tabla de continuidad, declaración explícita o grafo de historia— queda como decisión abierta para el dueño; no se impone UUID ni ruta ni CAS.*
3. **Estado / Huella (Cómo está en $t$ — Verdades 1 y 2):** La configuración concreta de la unidad en un instante, resumida por su huella Merkle (`digest` del contenido o de la proyección trackeada en Verdad 1; `digest` de entradas + salida en Verdad 2). Cambia en cada mutación relevante; **verifica vigencia, nunca identifica**.
4. **Localizador (Dónde se encuentra en $t$):** La coordenada física o topológica para ir a leer o re-sensorizar la unidad en una revisión dada (ruta de archivo, path en CST/AST, rango de grafemas/bytes). Es un atributo **mutable por revisión**, no la identidad.
5. **Contrato de sensorización (Con qué lente se mide):** Especificación inmutable (por digest) del parser/gramática, normalización, perfil de granularidad o selector de anclaje (y, en Verdad 2, el comando/entorno de ejecución) que define qué cuenta como señal y qué es ruido/azúcar fuera de la membrana.

### Tabla por niveles de abstracción entre S y M

| Nivel de abstracción | Unidad / Membrana (qué se marca) | Identidad temporal (propuesta conceptual) | Estado y Huella (Verdad 1 / Verdad 2) | Localizador en $t$ (mutable) | Contrato de sensorización |
|---|---|---|---|---|---|
| **L0 · Contenido puro (Bytes / Valor)** | Secuencia inmutable de bytes o valor EDN canónico (sin membrana temporal). | No tiene identidad temporal propia; solo igualdad extensional byte a byte (`README.md` autopoiesis l. 61). | El propio digest criptográfico `H(bytes)`. | Dirección en caché/almacén si se materializa. | Algoritmo de hash (`sha-256`) + reglas `canonical-bytes` (`02 §2.1`). |
| **L1 · Fuente / Artefacto (S externo)** | Archivo o recurso externo delimitado como unidad en el repositorio/ambiente (ej. archivo en git). | Continuidad del artefacto marcado a través de renombres y ediciones. | **Verdad 1 (nivel fuente):** `fingerprint` de bytes crudos + `merkle-root` de su proyección trackeada. | `{:path "rel/path.md" :git-rev "..."}` (puede cambiar por `mv`). | Perfil de archivo / adaptador + versión de gramática (`14 §18.1`, `19`). |
| **L2 · Sub-topología marcada en S (Nodos sensorizados)** | Subárbol del CST/AST, sección, bloque, función, declaración o span que ha sido **marcado (trackeado)** para alimentar M (`14 §20`). | Continuidad de la sub-topología marcada aunque cambie su posición entre hermanos, su contenedor o su texto interno. | **Verdad 1 (nivel nodo):** hash Merkle del subárbol/proyección dentro de su membrana (excluyendo azúcar no marcado). | Coordenada topológica en el árbol actual: `( unidad-fuente, path-AST / selector-match, span )`. | Selector de anclaje / perfil de granularidad + normalización NFC (`14 §18.5, §20`). |
| **L3 · Unidad de desacoplamiento S–M (Estructura / Símbolo local)** | Instancia de modelo de anclaje, campo estructurado o símbolo contextual naciente del documento (`14 §18`, `§21`). | Identidad estructural/semántica en su contexto local, desacoplada de la redacción exacta en S. | Estado de extracción/vinculación: vigencia de los nodos L2 que le dan soporte (derivado por Merkle de sus soportes). | Ruta en el espacio de modelos / contexto del documento (`ctx` local + rol/campo). | Modelo de anclaje (selector + esquema de campos + herencia de clase, `14 §18.2–7`). |
| **L4 · Ejecución marcada (Soporte procedural, opcional)** | Corrida determinista o acotada de un programa/test/rutina sobre topologías marcadas (`README.md` autopoiesis l. 66; corrección 2026-11-14). | Continuidad del check/evaluación marcada sobre sus sujetos a lo largo del tiempo. | **Verdad 2:** `(hash-inputs-marcados, hash-contrato-ejecución, resultado/exit/output-hash)`; se invalida si muta algún input L1–L3 o la rutina. | Descriptor de invocación / target del runner. | Código/binario de la rutina + entorno declarado + firma de entradas. |

*(La capa **G** —proposiciones `(R A B)` evaluadas en matrices por `W_i`, sentido `sinnvoll/sinnlos/unsinnig`, inferencia y curaduría entre contextos— queda explícitamente fuera de esta tabla y viene después de cerrar S–M).*

---

## 5. Semántica de las seis operaciones topológicas/temporales sobre unidades marcadas

Cómo debe comportarse el «sistema nervioso» (sensorización Merkle + identidades temporales + Verdad 1/2) ante las seis transformaciones clásicas:

| Operación | Qué cambia en Localizador / Huella | Qué pasa con la Identidad de la unidad | Efecto en Verdad 1 (y Verdad 2) / Regulación |
|---|---|---|---|
| **1. Mover (`move` / `rename` / desplazar bloque o archivo)** | **Localizador:** cambia (nueva ruta de archivo, nuevo ordinal de hermano `[i → i+1]`, o nuevo contenedor).<br>**Huella (contenido marcado):** intacta (`H_nuevo == H_viejo`). | **Se conserva la misma identidad solo mediante continuidad declarada o aceptada** (acción controlada o aceptación explícita de linaje). Ante un cambio externo sin registro previo, el sensor únicamente reporta `no localizado en L_viejo` + candidato(s) por huella en `L_nuevo`: la igualdad de hash **no prueba movimiento** (puede ser copia + borrado o colisión entre gemelos). | **Verdad 1 sigue vigente solo tras confirmar continuidad** (lo marcado no mutó internamente, solo cambió su localizador). Hasta entonces queda pendiente/en revisión. El Merkle del contenedor padre detecta reordenamiento si el orden es significativo para el padre; confirmada la continuidad, el nodo movido no invalida lo que cuelga de su contenido interno. |
| **2. Editar (`edit` / mutación interna)** | **Localizador:** igual (o desplazado en offsets si cambió el largo).<br>**Huella:** cambia (`H_nuevo ≠ H_viejo`) si la edición tocó la proyección trackeada; **igual** si solo tocó azúcar/formato fuera de lo marcado. | **Se conserva la misma identidad** (transición de estado ontogénica $E_t \to E_{t+1}$ mientras conserve su membrana/clase). | Si tocó lo marcado: **Verdad 1 queda invalidada por mutación** (y **Verdad 2** de cualquier ejecución que dependa de este nodo queda invalidada). El Merkle propaga la señal por la rama afectada para reconciliar/revalidar dependencias en M sin tocar ramas hermanas intactas. |
| **3. Copiar (`copy` / duplicar fragmento o archivo)** | **Localizador:** aparece un segundo localizador $L_2$ además del original $L_1$.<br>**Huella:** en el instante de copia, $H(L_1) == H(L_2)$. | **La original conserva su identidad $I_1$; la copia (si se marca) nace con una identidad nueva $I_2 \neq I_1$** (opcionalmente con traza de procedencia `copied-from I_1@t`). | $I_1$ mantiene su **Verdad 1** intacta. Una edición posterior en $I_2$ invalida solo a $I_2$, jamás a $I_1$ (evita el bug de sucesión global `F07`). |
| **4. Dividir (`split` / fisión: un archivo o sección se parte en dos)** | **Localizador:** el localizador original desaparece o retiene una parte; surgen localizadores $L_a, L_b$.<br>**Huella:** ninguna de las partes tiene el hash del todo original; pero las **sub-topologías hijas** dentro de cada parte pueden conservar su huella intacta. | **En el nivel padre (lo dividido):** la membrana original se fragmenta $\to$ transición explícita `split`: $I_0 \to \{I_a, I_b\}$ (o $I_0$ continúa en una parte y nace $I_b$, según declaración/regla).<br>**En el nivel hijo (sub-nodos movidos a $I_a$ o $I_b$):** **conservan su identidad** (fue un `move` de contenedor para los hijos). | **Verdad 1 del nodo padre** se invalida y exige re-anclaje de lo que dependía del todo indiviso. **Verdad 1 de los sub-nodos hijos** cuyas huellas no cambiaron **permanece vigente** tras actualizar su contenedor a $I_a$ o $I_b$. |
| **5. Fusionar (`merge` / unión: dos unidades $I_a, I_b$ se integran en una)** | **Localizador:** $L_a$ y $L_b$ convergen en un único contenedor/bloque $L_m$.<br>**Huella:** nueva huella compuesta en el nivel fusionado; sub-nodos internos pueden mantener su huella. | **En el nivel fusionado:** transición explícita `merged`: $\{I_a, I_b\} \to I_m$ (o absorción de $I_b$ en $I_a$). Ambas identidades predecesoras quedan registradas como absorbidas.<br>**En el nivel hijo:** los sub-nodos marcados que pasan intactos al nuevo contenedor conservan su identidad. | Lo que dependía de la frontera separada de $I_a$ o $I_b$ recibe señal de revisión; los anclajes a sub-nodos internos intactos mantienen su **Verdad 1** redirigidos al contenedor resultante. |
| **6. Borrar (`delete` / desintegración de la unidad)** | **Localizador:** desaparece (`nil`).<br>**Huella:** ausente. | **La unidad se desintegra** (pierde su membrana y su soporte en el medio). Su identidad pasa a estado terminal (`deleted`/`disintegrated` en la historia), sin eliminar el registro de que existió. | **Verdad 1 (y Verdad 2 dependiente) se invalida de forma dura.** Cumple `00-nucleo.md` 2.4 y `14 §21`: ninguna pérdida de soporte se oculta; todo nodo en M que dependía de la unidad borrada queda explícitamente sin soporte (`orphan`/declarado), sin mantener un nodo S vivo apuntando a la nada. |

---

## 6. Delimitación estricta: Qué está fijado por el dueño vs. Qué es propuesta

Para no convertir mecanismos técnicos (UUID, rutas, CAS) en decisiones aprobadas ni invadir el diseño de G:

### 6.1 Fijado por el dueño (Autoridad directa)
1. **Rol de `kimun` (2026-11-14):** Sistema nervioso de un sistema autopoiético de conocimiento; detecta señales (mutaciones de topología y quizás acciones) y permite regulación y otras acciones.
2. **Merkle y nodos (2026-11-14, `14 §20`):** El árbol Merkle es la sensorización (sobre lo trackeado); los nodos representan qué tenemos sensorizado (normalmente documentos y código).
3. **Qué NO es la identidad (2026-10-09, 2026-10-10, 2026-11-14):**
   - La identidad **NO es el hash** (el hash solo verifica mutación; a nivel de contenido solo da igualdad byte a byte).
   - La identidad **NO es el anclaje estructural/topológico** (corrige la formulación previa de `14 §21` / `D0` que reducía la identidad en S al lugar estructural).
   - Hay que definir otra cosa que identifique topologías marcadas y sus cambios en el tiempo a través de niveles de abstracción (inspirado en cómo git sigue archivos, pero generalizado a múltiples niveles).
4. **Unidad, membrana y estados (2026-10-10, `docs/research/autopoiesis/README.md`):** Distinguir referencias al contenido (igualdad byte a byte), a la unidad y su membrana, y a sus estados.
5. **Dos verdades en esta etapa (2026-10-10, 2026-11-14):**
   - **Verdad 1:** lo marcado no cambió desde la huella; se invalida por mutación.
   - **Verdad 2:** si se implementa marcar ejecuciones, la verdad del resultado de esa ejecución.
6. **Transparencia ante pérdida de soporte (`00-nucleo.md` 2.4, `14 §21`):** Si lo marcado muta o desaparece, se declara y se revisa lo que depende de él; ninguna pérdida de soporte se oculta.
7. **Alcance de la etapa actual (2026-11-14):** Seguimos en la frontera entre **S** (texto como signo) y **M** (desacople estructural/semántico); **G** viene solo después.

### 6.2 Qué es propuesta del concilio (Requiere decisión del dueño)
1. **La representación técnica de la identidad temporal de una topología marcada:**
   - *No está aprobado* que sea un UUID/ULID persistido por nodo, ni una ruta cualificada, ni un CAS híbrido. Un identificador opaco/UUID por sí solo **nombra** la unidad, pero **no explica ni prueba su continuidad** entre observaciones cuando muta el medio; lo que sostiene la continuidad es el registro explícito de linaje y evidencia entre observaciones.
   - *Opciones abiertas para el dueño:*
     - **(Opción A — Identificador de linaje nominal asignado al marcar + registro explícito de observaciones/linaje):** cada topología marcada recibe un identificador de unidad al registrarse; la continuidad entre revisiones la establece la cadena explícita de linaje (`continues`/`moved`/`split`/`merged`/`retired`) que enlaza `{localizador, contrato, huella-estado}`.
     - **(Opción B — Identidad por declaración explícita en fuente/modelo + continuidad asistida tipo git):** la unidad se nombra por clave semántica/declarativa del modelo o ancla nombrada, y los movimientos/renombres sin marca en el texto se resuelven por correspondencia estructural registrada en la revisión (como la detección de renames en git, pero confirmada en la transacción de tracking y nunca inferida automáticamente por igualdad de hash).
     - **(Opción C — Grafo de continuidad de estados sobre huellas/snapshots):** los estados se verifican por digest, pero la «unidad» es la cabeza/cadena declarada de transiciones topológicas (`moved`, `edited`, `split`, `merged`, `deleted`) separada de la igualdad de bytes y sin copiar el contenido completo del ambiente en el store estable.
2. **La descomposición en 5 tuplas `(Identidad, Unidad/Membrana, Estado/Huella, Localizador, Contrato)` y los 5 niveles L0–L4** de la Sección 4 como contrato conceptual S–M.
3. **El tratamiento diferenciado de `move`, `edit`, `copy`, `split`, `merge`, `delete`** (Sección 5), en particular la exigencia de continuidad declarada/aceptada en `move` y la preservación de identidad y Verdad 1 de las sub-topologías hijas cuando su contenedor padre se mueve, divide o fusiona tras confirmar su linaje.
4. **La inclusión formal del digest del contrato de sensorización (`F08` / K6)** dentro de la verificación de Verdad 1 para distinguir mutación del medio (`source-changed`) de cambio de instrumento sensorial (`contract-changed`).

---

## 7. Contraste con la propuesta de Pi (`runs/research/2026-11-14/concilio/pi.md`)

La propuesta de Pi acierta en el diagnóstico del kernel (`node-id`/`edge-id`/`revision-id` actuales confunden estado con identidad) y en la separación estructural `Unit / Observation / Lineage / locator / state / contract` más la regla «señal ≠ acción». Sin embargo, desde el análisis de límites temporales y topológicos (Agy), presenta **tres brechas críticas** que deben corregirse antes de llevarla a síntesis:

### 7.1 Conservar contenido CAS no debe reinstalar copia durable del ambiente
- **Qué propone Pi (`pi.md` §0, §3.1, §5):** «dejar el CAS como almacenamiento de contenido/observaciones» y «el CAS guarda objetos de contenido para deduplicar/verificar».
- **Límite / contradicción:** Reintroduce en el store estable copias durables de texto/contenido del ambiente bajo el argumento de deduplicar o verificar. Esto choca frontalmente con `00-nucleo.md` 6.2 («no conserva una copia del ambiente como respaldo»), `14 §19` regla 2 y decisión 43 («el store estable nunca guarda el contenido») y la eliminación de copias verbatim desde el formato 3/4 (`10-escala.md §11`). Para **verificar** Verdad 1 basta conservar la **huella** (`fingerprint` / `merkle-root` / hash de proyección) junto con `{unidad, localizador, contrato}`.
- **Ajuste exigido para la síntesis:** El store estable debe conservar exclusivamente metadatos propios del sistema nervioso (`Unit`, `Lineage`, `Observation` con huellas, localizadores y contratos). Si el CAS persiste objetos, debe hacerlo solo para objetos propios de `kimun` o en `<store>/cache/` borrable y recomputable desde la fuente externa (salvo el modo explícito `:content :inline` de `13-include.md §1` cuando la fuente no pueda releerse barato).

### 7.2 Un UUID / ID opaco no explica continuidad
- **Qué propone Pi (`pi.md` §2, §3.1, §4):** Define `Unit.id` como «opaco, estable, asignado al marcar», afirma que «debe sobrevivir a editar y mover» y en la tabla de casos (§4) da por hecho que en `Mover` y `Editar` la unidad sigue siendo «misma unidad», aun cuando deja abierta la codificación (UUID, ULID, etc.) en §5.
- **Límite / contradicción (Contraejemplos 1, 2 y 4):** Asignar un UUID/ID opaco en el instante $t_0$ solo acuña un **nombre**; no provee ningún criterio ni mecanismo de **continuidad** cuando en $t_1$ cambia el archivo, cambia el path `[i j k]` por inserción de hermanos o cambia el contenido. Si el sistema nervioso solo ve una topología nueva en $L_2$ y un localizador vacío en $L_1$, el UUID almacenado en el registro no sabe a qué objeto de $t_1$ engancharse. Además, pre-comprometer que `Unit.id` es «opaco asignado» descarta sin decisión del dueño alternativas derivadas del acto de marcar o declaradas en el modelo/fuente.
- **Ajuste exigido para la síntesis:** La identidad no reside en un token suelto sino en la **cadena verificada de marca + observaciones + transiciones de linaje con actor/evidencia**. El UUID (si llegara a elegirse) sería a lo sumo una etiqueta serial; jamás la explicación de la continuidad. En la tabla de casos, `Editar` conserva identidad mientras el localizador y la membrana sigan resolviendo bajo el contrato; `Mover` solo la conserva **después** de que una acción o aceptación explícita registre la transición de linaje.

### 7.3 Igualdad de hash no prueba movimiento
- **Qué propone Pi (`pi.md` §3.2):** Define la señal del sensor `moved` como: «el localizador anterior no resuelve y el nuevo resuelve con el mismo estado».
- **Límite / contradicción (Contraejemplo 3 y `F07`):** Inferir `moved` por igualdad de estado/hash comete dos errores lógicos graves y contradice la propia regla de Pi en §4 («copiar y borrar el original no equivale automáticamente a mover»):
  1. **Indistinguibilidad entre `move` y `copy + delete`:** Si el usuario copió una sección o cláusula estándar en otra ruta/posición y borró la original, el localizador viejo no resuelve y el nuevo tiene exactamente el mismo hash; sin embargo, puede tratarse de una nueva unidad con retiro de la vieja, no de un traslado.
  2. **Colisión entre gemelos idénticos (homonimia):** Si existen dos ocurrencias con igual contenido (dos `try/catch`, dos cláusulas `"PENDIENTE"`, archivos vacíos o plantillas) y una desaparece mientras aparece otra copia igual en otro sitio, la igualdad de hash es ambigua y no permite atribuir cuál se movió ni justificar continuidad sin actor.
- **Ajuste exigido para la síntesis:** El sensor Merkle **nunca** debe emitir `moved` como hecho consumado basándose en igualdad de hash. Ante un localizador viejo ausente, el sensor solo debe emitir **`missing` (con candidato(s) por huella como evidencia)** o **`ambiguous`**. La transición `moved` pertenece exclusivamente a la capa de **acción / linaje declarado** (por quien ejecutó el movimiento o por un actor que acepta el candidato, conforme al invariante 7 de `02 §10` y decisión 10).
