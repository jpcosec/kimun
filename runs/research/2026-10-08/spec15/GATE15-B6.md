# GATE15 — Carril B (ejecutabilidad) — ronda 6

Auditor sin contexto. Lectura y ejecución con `memcap`. Repo `spec15` (`close/spec15`).
Documento auditado: `docs/v2/15-capa-g.md` + `test/fixtures/g/*.edn`. Autoridad: `14`, `02`,
`05`, `03`, `11`, `10`.

## Respuestas directas a las cinco preguntas del carril

1. **Implementable con el kernel y anillos actuales:** sí, si se implementa como declara §11
   (cambios a `node/shapes`, `edge/statuses`, `edge/required-evidence`, `plan`,
   `revision/apply-plan`, `anchor/resolves?`, nuevo `kimun.kernel.datalog`); todo lo demás es
   host (ring 1) o CLI/superficie (ring 3). **Pero §11 está incompleto** (hallazgos 5–7) y
   una de sus asignaciones es inimplementable tal cual (hallazgo 6).
2. **Reconstruible desde el pool:** el log `facts.log.edn` sí (caché; el hecho vive en el
   documento de decisión, §4/`10 §11`); el esquema de datoms de `index/graph/` sí (derivado de
   las aristas activas con evidencia, `10 §11`); **la caché de conflictos no**, y §5 lo admite
   contra `02 §10` inv.10 sin declarar la revisión (§11). Hallazgo 2.
3. **Tabla de promesas:** hay fila para casi todo enunciado, pero (a) ninguna fila está
   registrada en `docs/v2/tests/promises.md`, que se declara trazador solo de `02`/`04`
   (§10/`03 §5`); (b) faltan filas para varios enunciados normativos; (c) las filas existen
   como "deferred"; el único test G hoy es `test/kimun/g/fixtures_test.cljc` (7 `deftest`,
   695 aserciones, verde). Hallazgos 10–11.
4. **Protocolo `propositions` vs protocolo 1:** compatible y aditivo; ningún adaptador
   `symbols|cli` cambia. El único riesgo real es que `validate` usa `profile-models` como
   registro de perfiles conocidos y `plan-documents` ramifica sobre el mismo mapa: añadir
   `propositions` ahí enruta los hechos por `plan-symbols`. Hallazgo 3.
5. **Costes `p×p`:** sí, dos sitios: la fila "bitset" de §8 (por construcción
   `|sujetos|·|objetos|`) y la materialización del cierre fuera de `:corpus` con
   `O(pares alcanzables)`, que para un `:transversal` no queda vetada. Hallazgos 1 y 4.

---

## Hallazgos

1. **`bloqueante` — La representación "bitset" de §8 es el cruce `|sujetos|·|objetos|` que el
   propio §8 prohíbe.**
   - 15 §8: «cada fila un bitset o lista ordenada de objetos observados … Memoria
     `O(hechos de R en W_i)`, **nunca** el cruce `|sujetos|·|objetos|`».
   - Choca con `14 §8`: «**nunca crecen `p×p` sobre el corpus entero**» y con `05` decisión 26
     («nunca `p×p` global»). Un bitset indexado por el espacio de objetos cuesta `|objetos|` por
     fila ⇒ `|sujetos|·|objetos|`, aunque las celdas presentes sean pocas.
   - Corrección mínima: eliminar "bitset" (dejar solo lista/par ordenado, o bitset comprimido
     cuya cota sea `O(celdas presentes)`).

2. **`bloqueante` — La caché de conflictos no es reconstruible desde log+pool, contra
   `02 §10` inv.10, y §11 no declara esa revisión.**
   - 15 §5: «El caché solo se reconstruye **re-ejecutando el motor de convergencia** para los
     candidatos no promovidos … así que no basta log + pool (inv. 10 de `02 §10`)».
   - Choca con `02 §10` inv.10: «Lo derivado se reconstruye desde log + pool; no tiene
     autoridad». §11 lista como revisión de `02 §10` solo «el estado de anclaje de un hecho es
     derivado … `V`/`S` nunca son nodos», no inv.10.
   - Corrección mínima: declarar inv.10 revisada en §11 (la caché de conflictos se re-deriva
     con el motor) o hacer la caché descartable y nunca autoridad de `ask`.

3. **`grave` — `validate`/`profile-models` mezcla "perfiles conocidos" con "perfil → modelos de
   documento", y §7 pide ambas cosas.**
   - 15 §7: «`kimun.surface.adapters/validate` **ya rechaza un perfil desconocido**
     (`:adapters/profile`; comprueba `(contains? profile-models …)`) y se extiende con el perfil
     `propositions`» + «`plan-documents` … es para **perfiles de documento** y no sirve aquí».
   - Código: `src/kimun/surface/adapters.cljc:109` usa `profile-models` (def en `:11`) para el
     rechazo y `:286` ramifica `(if (= profile "cli") (plan-cli …) (plan-symbols …))`. Añadir
     `"propositions"` a `profile-models` hace que `plan-documents` acepte el perfil y llame a
     `plan-symbols`.
   - Corrección mínima: separar `known-profiles` (registro de perfiles) de `profile-models`
     (perfil → modelos de documento).

4. **`grave` — El veto de densidad de §8 solo cubre `:corpus`; un `:transversal` puede crecer
   `p×p`.**
   - 15 §8: «La forma densa queda **prohibida** para todo `W_i :granularity :corpus`» y «Fuera
     de `:granularity :corpus` se **materializa** … con memoria `O(pares alcanzables)`».
   - Choca con `05` decisión 26 («nunca `p×p` global») y `14 §8`; §2 fija el transversal
     `context` (todos los `W_i`, decisión 34) y `:transversal` ≠ `:corpus`, así que su cierre se
     materializa `O(pares)`.
   - Corrección mínima: extender la prohibición de densa/no-materialización a
     `:granularity :transversal`, o calcular su cierre bajo demanda.

5. **`grave` — La lista de cambios al kernel de §11 está incompleta: no asigna la detección de
   conflicto, ni `V`/`S`/`I` y cierre, ni el cálculo de datoms del índice.**
   - 15 §11 lista, en kernel (código), `node/shapes`, `edge/statuses`, `edge/required-evidence`,
     `plan`, `revision/apply-plan`, `datalog`, `anchor/resolves?`.
   - 15 §5 dice que el informe lo produce «el evaluador de sentido (§8)» y §8 reparte
     `V_i`/`S_i`/`I`/cierre entre «evaluador in-process (ring 0)» y host; ninguna de esas
     responsabilidades tiene namespace asignado en §11.
   - Corrección mínima: nombrar en §11 el/los namespaces ring 0 que evaluan `V`/`S`/`I`, cierre
     y conflicto (o decir que son parte de `kimun.kernel.datalog`).

6. **`grave` — §11 sitúa el chequeo "`:object`/hojas de `:form` son ids de término" en
   `kimun.kernel.plan`, que es puro y no tiene pool.**
   - 15 §11: «el chequeo «`:object`/hojas de `:form` son ids de término» vive aquí [`plan`],
     **no** en `node/shapes`, que no tiene pool».
   - Código: `plan/check-schema` (`src/kimun/kernel/plan.cljc:44`) solo ve el plan; comprobar que
     un id referenciado es `:symbol/:term` exige el pool. Hoy `apply-plan` solo comprueba
     existencia (`require-node`, `revision.cljc:336`), no clase/kind.
   - Corrección mínima: mover el chequeo a `revision/apply-plan` (o a una función pura que
     reciba el pool).

7. **`grave` — Las sustituciones de alias no cubren el contenido de `:node` ni `:prov`, y el plan
   resuelto de la transacción deja de describir lo aplicado (evidencia ejecutada).**
   - 15 §4: «**`facts assert` siempre materializa un documento de decisión `:action :assert`**,
     con el **`:fact/entry` canónico completo**»; `10 §11` define `:fact/entry` con
     `:nodes [{:as … :node <…>}]` y `:edges [{:type :binding :from :s :to :a …}]`.
   - Código: `resolve-edge` (`revision.cljc:370-376`) solo resuelve `:from`, `:to`, `:tree`,
     `:evidence :ref-hash`, `:evidence :context`; `add-node-op` (`:349`) pasa `(:node op)` sin
     `substitute-aliases`.
   - Ejecutado (memcap): un `:add-node :proposition` con `:form ["pred" :a]` se **almacena** con
     `{:form [pred :a]}` mientras `(:plan (:transaction …))` dice `{:form [pred <id-del-término>]}`
     (el id y el objeto aplicado discrepan); un `:fact/:triple` con `:subject :a` se rechaza
     (`content does not match shape :fact/:triple`); un `:sign/:span` con `:leaf :l` se rechaza;
     una arista `:binding` con `:prov [{… :span {:leaf :x …}}]` se almacena con `:leaf :x`.
   - Corrección mínima: sustituir alias en `:node :content` (`add-node-op`) y en toda la
     `:evidence` incluido `:prov` (`resolve-edge`), o fijar en `10 §11` que el `:fact/entry`
     lleva ids resueltos.

8. **`grave` — Un contexto `source` no reproduce "el mismo `W_i`" porque su árbol es un ULID
   nominal nuevo por build.**
   - 15 §2: «un `rebuild` con los mismos archivos produce el mismo contexto y los mismos hechos»
     y 15 §10: «`g/rebuild-reproduces-contexts-and-facts` … el mismo `W_i` y los mismos hechos».
   - Choca con `14 §3` («Un contexto `W_i` es … un nodo `:fact/:context` **y** un árbol-índice»)
     y con el propio 15 §4: «un árbol … tiene un **id nominal**, un ULID acuñado al crearlo …
     re-crear un árbol de contexto **no es idempotente**». El nodo de contexto y los hechos son
     estables; el árbol no.
   - Corrección mínima: acotar la promesa a "el mismo nodo de contexto y los mismos hechos", o
     registrar el ULID del árbol en la decisión de contexto.

9. **`grave` — §2 usa `:semantic` (M↔M en `02 §3`) para ruteo entre contextos y §11 no declara
   ese cambio de clase.**
   - 15 §2: «La forma de la arista de ruteo: §12 Q6 (default (b)) … `{:type :semantic :evidence
     {:relation :equivalent|:projection :context <W_i> :context-target <W_j> …}}`».
   - `02 §3` tabla: «`semantic` | M ↔ M | relación de significado afirmada»; §11 solo declara la
     ampliación de **evidencia** (`:relation`/`:context-target`/`:phi`), no que `semantic` pase a
     unir `:fact/:context`.
   - Corrección mínima: declarar en §11 la revisión de la fila `semantic` de `02 §3` (endpoints)
     o usar un tipo de arista propio y declararlo.

10. **`menor` — La forma del warning `:data {:pending n}` no está en `06 §1`.**
    - 15 §4: «El aviso es una `:warnings` del envelope de `06 §1` (tipo `facts/pending-upstream`,
      `:data {:pending n}`».
    - `06 §1`: «`:warnings [{:type … :message …}]`». §11 declara el warning pero no la clave
      `:data` dentro de él.
    - Corrección mínima: declarar en §11 que el warning lleva además `:data`, o quitar `:data`.

11. **`menor` — Faltan filas de promesa para varios enunciados normativos y las filas no están
    registradas en `docs/v2/tests/promises.md`.**
    - 15 §10: «Todo `deferred` hasta la etapa A/C (implementación)».
    - Sin fila, entre otros: «El árbol `:context` **no cuelga hechos**» (§2), «un término puede
      vivir en varias taxonomías» (§1), «`--strict` las rechaza» (§4), «leer nunca escribe» (§8),
      «los transversales son árboles persistidos … (`14 §16 P13`)» (§2). Y `03 §5` exige que la
      tabla `docs/v2/tests/promises.md` registre cada promesa; su encabezado dice que traza solo
      `02` y `04`.
    - Corrección mínima: añadir filas para esos enunciados y registrar el bloque G en
      `docs/v2/tests/promises.md`.

12. **`menor` — El "chequeo de `:prov`" de §11 solo exige presencia; §6 pide un vector ≥ 1 con
    span.**
    - 15 §6: «la clave `:prov` **obligatoria** —un vector de spans, ≥ 1—»; 15 §11 lo traduce a
      `edge/required-evidence`.
    - `edge/validate` (`edge.cljc:59-66`) solo comprueba presencia/`:status`; no hay chequeo de
      forma de `:prov`.
    - Corrección mínima: añadir en §11 el chequeo de forma (vector de spans no vacío) en
      `edge/validate` o en `plan`.

*(Nota de verificación: `test/kimun/g/fixtures_test.cljc` corre verde en `bb -cp
src:resources:test`: 7 tests, 695 aserciones, 0 fallos. Todas las corridas se hicieron con
`memcap`.)*

---

## Ambigüedades

- **`open` consolida o solo avisa.** 15 §4: «Al abrir, las entradas del flujo no consolidadas se
  informan y **nunca se descartan en silencio**» y §12 Q2 default «(b) automático en cada acción
  y al abrir (informando pendientes)».
  - Lectura (a): `open` consolida las pendientes (aplica plan + `commit!`) y además avisa.
  - Lectura (b): `open` solo avisa; la consolidación queda para la próxima acción explícita.
  Impacta si `open` puede escribir (hoy `10 §6` dice que `open` «no replaya ni lee el log»).
- **Presencia de `:status`.** 15 §3: «`:status` es obligatorio-presente y no nulo en cuanto hay
  decisión».
  - Lectura (a): la clave está **siempre** presente en la evidencia de `projection` (nula sin
    decisión, como en los fixtures `:status nil`).
  - Lectura (b): la clave solo se exige **cuando** hay decisión; sin decisión puede faltar.
  Impacta si `edge/required-evidence` debe listar `:status` (hoy lo lista y rechaza `nil`,
  `edge.cljc:30,65`).
- **"bitset" disperso o denso.** 15 §8: «cada fila un bitset o lista ordenada de objetos
  observados».
  - Lectura (a): bitset denso indexado por el espacio de objetos ⇒ `O(|sujetos|·|objetos|)`.
  - Lectura (b): bitset comprimido/disperso cuya cota es `O(celdas presentes)`.
  Bajo (a) contradice el propio §8 y `14 §8`; bajo (b) es admisible.

---

## Veredicto

**ABIERTO** — bloqueantes:

1. Hallazgo 1: la representación "bitset" de las matrices de §8 vuelve `p×p` sobre el `W_i`,
   prohibido por `14 §8` y `05` decisión 26.
2. Hallazgo 2: la caché de conflictos de §5 se declara no reconstruible desde log+pool, contra
   `02 §10` inv.10, sin declarar esa revisión en §11.

El resto son `grave` (3–9) y `menor` (10–12), corregibles sin rehacer el diseño.
