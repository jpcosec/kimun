# GATE15 — Carril A (ronda 5): coherencia de `docs/v2/15-capa-g.md`

Auditoría sin contexto previo, solo lectura. No he leído informes anteriores ni `SPEC15-*-report.md`.
Artefacto auditado: `docs/v2/15-capa-g.md` + `test/fixtures/g/*.edn`, commit `7e08ef40` (rama `close/spec15`).
Autoridad: `14` > `02` > `05`; `15` por debajo de `14` (`README.md` §Autoridad).

## Hallazgos

1. **bloqueante** — La consulta sobre una contradicción interna devuelve un informe distinto y sin candidatos.
   - 15 §5: «Sobre una contradicción interna al `W_i`, `ask` devuelve un informe `:conflict` con **los hechos en conflicto** y `:candidates` ausente; `:ambiguous` con candidatos aparece **solo al unir contextos**».
   - 14 §6: «el `W_i` afectado se marca y las consultas que lo tocan devuelven `:ambiguous` con candidatos, nunca eligen».
   - 15 §11 no lista `14 §6` como revisado (no puede: `15` < `14`). Corrección mínima: que `ask` sobre una contradicción interna devuelva `:kind :ambiguous` con `:candidates` como manda `14 §6`, eliminando el `:conflict` sin candidatos.

2. **grave** — Se invierte la primacía motor/respaldo de la decisión 30 sin declararlo.
   - 15 §8: «**Camino estable: evaluador Datalog semi-naive in-process** sobre el índice… Es la única vía offline» y «**Datalevin opcional**».
   - 05 decisión 30: «Datalog para la consulta de G; Datalevin (pod) candidato con respaldo in-process; el índice G es derivado»; 14 §8: «con un evaluador semi-naive in-process como respaldo offline».
   - Corrección mínima: mantener la redacción de `05`/`14` («in-process como respaldo, pod Datalevin como motor candidato») o abrir una pregunta en §12 y no fijarlo.

3. **grave** — El hecho `sense` que marca `unsinnig` no es admisible con el vocabulario declarado (eje `["thing" "thing"]`) ni con los fixtures.
   - 15 §3: «La marca es un **hecho del pool**: `(sense <fact-term> unsinnig) @ W_i`»; 15 §1: `sense` «`:axis ["thing" "thing"]`» y «su extensión son hechos `(sense <fact-term> <sense-term>) @ W_i`».
   - 14 §4: «`R`, `A` y `B` son símbolos … que ocupan posiciones en árboles de taxonomía»; ningún término para `<fact-term>` ni para `sinnvoll/sinnlos/unsinnig` aparece en `vocabulary.edn` ni en `context.edn`.
   - Corrección mínima: declarar en `L_i` (o en un vocabulario base) los términos del árbol `thing` para el término-hecho y para los valores de sentido, de modo que el hecho `sense` cumpla la regla de `:axis`.

4. **grave** — El documento de decisión (`source`) no tiene forma de nodo/perfil declarada, y `include build` depende de él.
   - 15 §4: «se materializan como documentos `source` … `include build` reaplica las decisiones y reproduce los mismos hechos»; la forma se fija en §4 pero no se declara `:format`/perfil de ingesta (contrástese con §1, que sí declara `:sign/:opaque {:format "edn/vocabulary"}`).
   - 02 §7 / `04`: la ingesta de un `source` pasa por el perfil de documento; 14 §12 decisión 27 solo dice «documentos `source`».
   - Corrección mínima: declarar la forma del nodo de la decisión (p. ej. `:sign/:opaque {:format "edn/decision"}`) y qué parser/perfil la lee en `include build`.

5. **menor** — La autorización de matriz densa bajo umbral es una regla sin fuente en `14`/`05`.
   - 15 §8: «la materialización **densa** se permite solo si el número de términos de `R` en ese `W_i` está bajo un umbral medido y declarado (… default **4096**)».
   - 14 §8: «Representación vinculante (P9): … matrices booleanas por relación (`A × B`) … **nunca crecen `p×p` sobre el corpus entero**».
   - Corrección mínima: citar `14 §8` como única fuente del veto y marcar el umbral como propuesta (§12) en vez de regla fijada.

6. **menor** — Fixture: las posiciones de `ownership` omiten `:type :ownership`.
   - 15 §13: «las **aristas `ownership`** del árbol de contexto como posiciones `{:tree :parent :to :order}`»; `context.edn` usa exactamente esas cuatro claves.
   - 02 §3.2: «`Edge {:type :ownership  :tree <tree-id>  :parent <path> :to <id> :order <n>}`» y §11 no revisa `02 §3.2` para `ownership`.
   - Corrección mínima: añadir `:type :ownership` a las posiciones del fixture o declarar la abreviatura en §11.

7. **menor** — Fixture: `decision.edn` omite `:target`, que §4 incluye en la forma.
   - 15 §4: «`:target {:fact <id> :edge <projection-edge-id>}` ; hechos: un hecho y su arista».
   - `decision.edn` no tiene clave `:target` (solo `:fact/entry`).
   - Corrección mínima: añadir `:target` a `decision.edn` o decir en §4 que `:assert` lo omite porque el `:fact/entry` lo porta.

8. **menor** — Homónimo `:fact/entry` (versión vs contenido) entre `10 §11` y `15 §4`/fixture.
   - 15 §4: «`:fact/entry {…}` ; solo `:assert`/`--write`; contenido completo»; 10 §11: «`{:fact/entry 1 :op :assert …}`»; `decision.edn` usa `:fact/entry {:op … :nodes […] :edges […]}`, sin `:timestamp` interno (va en la raíz del documento).
   - Corrección mínima: renombrar la clave de versión en `10 §11` o usar una clave distinta para el contenido embebido.

9. **menor** — Tags de resolución (`Am1–Am5`, `A-H1–A-H9`, `B-1…B-7`, `A-1`) se citan sin definirse en el documento.
   - 15 §1: «Fija Am1»; §2: «Fija A-H5»; §5: «fija A-H9»; §7: «fija A-H2/B-2»; §8: «fija B-1/B-Amb B».
   - Ninguna lista del 15 define esos identificadores, así que un lector de cero contexto no puede verificar a qué hallazgo previo responden.
   - Corrección mínima: anexo corto que mapee cada tag a su enunciado, o eliminar los tags y dejar el enunciado.

10. **menor** — La premisa del gate («las 7 preguntas al dueño») no coincide con el documento.
    - 15 §12 tiene cinco filas: Q1, Q2, Q4, Q6, Q8; Q3, Q5 y Q7 están eliminadas/remitidas (texto tras la tabla).
    - Corrección mínima: ninguna en 15; constatar la discrepancia de premisa (o confirmar que el gate espera 5).

## Ambigüedades

- **`:axis`**: 15 §1 «cita la **raíz** de una taxonomía declarada en `:taxonomies`» con ejemplo `:axis ["thing" "thing"]`, frente a `:taxonomies [{:name "terms" :root "thing"}]`. Lecturas: (i) el eje cita la etiqueta de raíz (`"thing"`); (ii) el eje cita el nombre de la taxonomía (`"terms"`). El propio §1 («No admite nombres de taxonomía») apoya (i), pero la frase es ambigua.
- **Tipo del literal**: 15 §1 «el tipo lo da su posición en esa taxonomía» con `:literals` como árbol único. Lecturas: (i) el árbol `literals` tiene sub-posiciones por tipo EDN (string/entero/fecha); (ii) el tipo se infiere del formato de `:name`. 15 no declara ninguna; además `:symbol/:term` exige `:lang` también para literales numéricos.
- **Admisibilidad de términos**: 15 §1 solo declara `:taxonomies` con nombre y raíz, pero §2 dice que `contexts define` crea «los términos y relaciones admisibles como hijos» y `context.edn` enumera términos individuales. Lecturas: (i) todo término bajo las taxonomías es admisible; (ii) solo los enumerados en el documento de vocabulario lo son.
- **`inline` forzado**: 15 §6 «inline obligatorio para ellas» (hojas referenciadas por hechos) frente a 14 §10 «`inline` opcional por store» y `05` decisión 27 («inline por caso»). Lecturas: (i) 15 concreta «se persiste lo que alguien referencia» (`14 §2`); (ii) 15 contradice la opción por store.
- **`términos de R`**: 15 §8 «el número de términos de `R` en ese `W_i` está bajo un umbral». Lecturas: (i) nodos que ocupan posiciones en la matriz (sujetos+objetos); (ii) pares observados `(A,B)`.
- **Exit de contradicción interna**: 15 §5 no elige («Nunca elige, exit 2 (§9)») pero el informe se llama `:conflict`, y §9 mapea `conflicts/conflict`→4 y `ask/ambiguous`→2. Lecturas: (i) exit 2 (informe ambiguo); (ii) exit 4 (informe de conflicto).

## Veredicto

**ABIERTO**. Bloqueante: hallazgo 1 (contradicción no declarada con `14 §6` sobre el retorno de una consulta que toca un `W_i` con contradicción interna).
