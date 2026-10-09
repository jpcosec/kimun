# GATE15 — Carril A (coherencia), ronda 4

Auditor sin contexto previo. Solo lectura. Documento auditado: `docs/v2/15-capa-g.md` +
`test/fixtures/g/*.edn`. Autoridades superiores usadas: `14`, `02`, `05`, `README`.
No se han leído informes previos ni `SPEC15-*-report.md`.

## Hallazgos

### 1. `bloqueante` — El endurecimiento de `resolves?` para hechos exige una pertenencia que el 15 no define

- **15**: §11, fila `02 §6.2`: «un símbolo y un hecho resuelven por pertenecer a un árbol
  `:context` (se endurece la concesión "resuelve por existir en el CAS" del hito 7)»; y fila
  kernel: «`kimun.kernel.anchor/resolves?` (endurecer a "pertenece a un árbol `:context`")».
- **15**: §2, «Un `W_i` es un nodo `:fact/:context` **y** un árbol-índice `{:kind :context}`
  cuyos hijos son los términos y relaciones admisibles».
- **02 §2**: «sus términos y relaciones admisibles son sus hijos».
- **15**: §4, «La pertenencia de un hecho a su `W_i` no depende de ese ULID: la reconstruye el
  **nodo `:fact/:context`** … que es lo que el hecho cita en `:content :context`».
- Consecuencia: si los hijos del árbol `:context` son solo términos y relaciones, un `:fact`
  nunca ocupa posición en ese árbol; endurecer `resolves?` a «pertenece a un árbol `:context`»
  para hechos deja **todo hecho en `orphan`**. Además contradice la propia §4 (pertenencia por
  el nodo, no por el árbol). `02` ya arrastra la tensión en §6.2 y `15`, dueño de la capa G,
  no la resuelve ni la declara como revisión.
- **Corrección mínima**: acotar el endurecimiento a `:symbol` (los hechos resuelven por su
  `:content :context`) **o** declarar en §11 que el árbol `:context` pasa a incluir los hechos
  como posiciones (revisión explícita de `02 §2`).

### 2. `grave` — Los fixtures violan la regla de `:tiers` que el propio 15 fija

- **15 §1**: «`:tiers` es el subconjunto admisible en ese contexto … Afirmar un tier fuera de
  `:tiers` se rechaza».
- **`vocabulary.edn`**: relación `es` → `:tiers #{:observed :confirmed}` (líneas 17-19);
  relación `property` → `:tiers #{:observed :confirmed :inferred}` (líneas 20-22).
- **`facts.edn`**: `:edge/p2` y `:edge/p2b` (hechos `:fact/f2`/`:fact/f2b`, predicado
  `:term/property`) llevan `:tier :statistical` (líneas 109, 124); `:edge/p3` (hecho `:fact/f3`,
  predicado `:term/es`) lleva `:tier :statistical` (línea 155). Los tres tier quedan **fuera**
  de los `:tiers` declarados para su relación.
- **Corrección mínima**: añadir `:statistical` a los `:tiers` de `es` y `property`, o cambiar
  el `:tier` de `f2`/`f2b`/`f3`.

### 3. `menor` — `graph rule` no se declara como revisión de `06`

- **15 §9**, fila `graph`: «`query -f FILE.json` (ya existe) · `rule -f FILE.json --rules
  R.edn` (Datalog con `:in $ %`) · `rebuild` (propuesta nueva…)».
- **15 §11**, fila `06`: «inventario de los grupos nuevos (`facts`, `contexts`, `ask`,
  `conflicts`, `graph rebuild`)». No menciona `graph rule`.
- **Corrección mínima**: añadir `graph rule` a la fila `06` de §11 (o quitarlo de §9).

### 4. `menor` — Comentario de `facts.edn` describe un conflicto de tres hechos que no existe

- **`facts.edn`** línea 145: «f3 (referido por decision.edn y por conflict.edn del caso de tres
  hechos)».
- **`conflict.edn`**: `:facts [:fact/f2 :fact/f2b]` (dos hechos), `:edges [:edge/p2 :edge/p2b]`.
- **Corrección mínima**: corregir el comentario o añadir el tercer hecho al informe.

### 5. `menor` — Faltan posiciones de taxonomía para verificar dos promesas desde los fixtures

- **15 §1**: «Un término ocupa una posición en un árbol-índice `{:kind :taxonomy}` … la
  **ascendencia es la posición**: los ancestros de un término son los de su path en cada
  taxonomía».
- **15 §10**: `g/taxonomy-admits-one-position-per-node` y `g/vocabulary-axis-respects-taxonomies`
  («`:axis` cita raíces de taxonomías declaradas **y los hechos usan términos bajo ellas**»),
  que §13 remite a `test/kimun/g/fixtures_test.cljc`.
- **`context.edn`**: declara los descriptores `:tree/terms|:tree/relations|:tree/literals`
  (líneas 21-23) pero **ninguna** arista `ownership` para esos árboles; todas las `ownership`
  son de `:tree :tree/ctx-doc`. Sin posiciones, no hay ascendencia que comprobar.
- **Corrección mínima**: incluir las posiciones (`ownership`) de las tres taxonomías en el
  anexo de §13, o declarar esas promesas no verificables desde fixtures.

### 6. `menor` — `V`/`S` «nunca como nodo» choca con la prosa de `02 §2` no listada en §11

- **15 §3**: «`V`/`S` derivados. Se calculan por el evaluador (§8) y viven en
  `index/graph/`, **nunca como nodo**»; **§11** fila `02 §10` declara la invariante pero no toca
  la prosa de §2.
- **02 §2**: «Hechos (G): `(sujeto, predicado, objeto)` validados en un contexto `W_i`, con su
  estado sinnvoll / sinnlos / unsinnig». El sentido aparece como propiedad del hecho.
- **Corrección mínima**: añadir a la tabla de §11 la revisión de la prosa de `02 §2` (el hecho
  no lleva estado de sentido; el sentido es índice derivado).

### 7. `menor` — El default de Q1 no es el enum de `14 §5`

- **15 §3**: «`:status :proposed|:observed|:confirmed|:rejected|:retracted`» (y §12 Q1, default
  (a): «ampliar `:status` con `:proposed`»).
- **14 §5**: «evidencia de la arista `projection` … con `:status ∈ #{observed confirmed rejected
  retracted}`».
- El 15 lo marca como pregunta pendiente, pero el default contradice un enum que `14` fijó.
- **Corrección mínima**: hasta que Q1 se responda, mantener el enum de `14 §5` (con `proposed`
  en campo aparte) o declarar explícitamente que el default revisa `14 §5`.

### 8. `menor` — Traza errónea del rol `source`

- **15 §2**: «`source` es el rol que `01`/decisión 21 dan al archivo raíz».
- **05 decisión 21**: «**autoridad por clase** (2026-10-08): bytes → archivo (+git); estructura →
  pool como caché verificable; hechos → pool, decisiones humanas además como documentos». No
  asigna ningún rol `source`; `14 §2/§10/§12` usa «documento `source`» sin remitirlo a 21.
- **Corrección mínima**: citar `14` (decisiones 22/23/27) en vez de `05` decisión 21.

## Ambigüedades

1. **Señal de ambigüedad en `ask`.** §5: «`ask` devuelve `:ambiguous` con candidatos y nunca
   elige, exit 2»; §8 contrato de respuesta: `{:answer :truth :sense :justification
   :citations}` con `:truth :TRUE|:FALSE|:UNKNOWN`. Lectura (i): `:ambiguous` es el error
   `ask/ambiguous` del envelope (§9). Lectura (ii): `:ambiguous` es un valor de respuesta, pero
   no cabe en `:truth`.
2. **Forma de la proposición de un hecho.** §1 define la proposición del hecho como
   `:form [<pred-id> <subj-id> <obj-id>]` y, en el mismo párrafo, «`:form` admite formas
   anidadas (reglas `(if …)`…)». Lectura (i): los hechos solo proyectan formas planas y el
   anidamiento es para otras M. Lectura (ii): un hecho puede proyectar una forma anidada (y
   entonces no es `(R A B)`).
3. **Cota `p×p` en `W_i :granularity :corpus`.** §8: «El veto `p×p` es contra matrices sobre el
   corpus o la taxonomía entera … no prohíbe la representación por contexto» y «Un `W_i
   :granularity :corpus` sigue esa misma regla». Lectura (i): la densidad por contexto siempre
   es admisible. Lectura (ii): un `W_i` de corpus *es* «el corpus entero», luego su matriz
   `|sujetos|·|objetos|` choca con `14 §8`.
4. **`--strict`.** §4: «un `--strict` las rechaza» (hablando del aviso de pendientes que la CLI
   emite tras `open`); §9: `facts consolidate [--dry-run] [--strict]`. Lectura (i): `--strict`
   es de `facts consolidate`. Lectura (ii): es una opción global de apertura/escritura.
5. **`:name` de contextos finos.** §2: `:name "doc:<path>#<path>"`. Las dos ranuras `<path>`
   (ruta de fichero y path del bloque) no se distinguen.

## Veredicto

**ABIERTO**.

- Bloqueante: **#1** (el endurecimiento de `resolves?` para hechos, tal como lo fija §11, deja
  todo hecho en `orphan` y contradice §2 y §4 sin declararlo).
- Antes de cerrar deben corregirse también los graves/menores **#2** (fixtures contra la regla
  de `:tiers` de §1) y **#3–#8**.
- Preguntas al dueño: las 5 de §12 (Q1, Q2, Q4, Q6, Q8) son legítimas; ninguna está ya decidida
  en `14`/`05`. Q6 y Q8 son las más próximas a decidido: Q6 queda fijada como norma en §2 con
  el default (b), y Q8 tiene su subconjunto mínimo (store, model) ya fijado por `05` decisión 34
  (el resto, `context fact edge sign tree`, sí es ampliación).
