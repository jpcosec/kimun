# GATE15 — Carril A (coherencia) — ronda 6

Auditor sin contexto. Solo lectura; citas exactas. Documento auditado: `docs/v2/15-capa-g.md`
+ `test/fixtures/g/*.edn`. Autoridad superior: `14`, `02`, `05`, `README.md`.

## Hallazgos

1. **bloqueante — `:status` nulo contradice el enum de `14 §5`, y `15` afirma que no lo revisa.**
   - 15 §3: «la salida de un motor es siempre `:curation :proposed` y `:status` nulo» y «hasta
     que se responda, el default de `15` no revisa el 14 —`proposed` vive en `:curation`—»;
     fixtures `facts.edn` `edge/p2`, `edge/p2b`, `edge/p3` llevan `:status nil`.
   - 14 §5: «**Afirmación** (quién dijo qué): evidencia de la arista `projection` … con
     `:status ∈ #{observed confirmed rejected retracted}` y exactamente un origen»; 05 decisión
     24: «afirmación = `:status` en evidencia de arista `projection`». `14 §5` no admite `nil`.
   - Corrección mínima: o se declara explícitamente la revisión de `14 §5` (añadir `nil`/`:proposed`
     al enum) o se adopta Q1 (b); no puede sostenerse «no revisa el 14» con `:status nil` en los fixtures.

2. **bloqueante — la arista de ruteo `:semantic` omite la evidencia obligatoria y cambia de capa sin declararlo.**
   - 15 §2: «Forma de la arista de ruteo: … `{:type :semantic :evidence {:relation :equivalent|:projection
     :context <W_i> :context-target <W_j> :phi "<str>" :ref-hash <:to>}}`»; no figura origen
     (`:actor` o `:engine`+`:version`) ni los extremos `:from`/`:to`.
   - 02 §3.2: «`semantic` (M↔M) | `:ref-hash`, origen, `:context`»; 02 §3: «Toda arista que cruza capas
     … lleva evidencia: (referente, hash, provenance)» (inv. 6). 15 §11 solo dice que `semantic`
     «gana las claves `:relation`/`:context-target`/`:phi`» (añade, no quita origen ni cambia M↔M).
   - Corrección mínima: fijar en la forma el origen obligatorio y declarar los extremos (nodos `W_i`/`W_j`
     o hechos) y la revisión de la capa M↔M.

3. **bloqueante — el caché de conflictos no es reconstruible desde log+pool (inv. 10).**
   - 15 §5: «su `:phi` no vive en aristas del pool … así que no basta log + pool (inv. 10 de `02 §10`)».
   - 02 §10 inv. 10: «Lo derivado se reconstruye desde log + pool; no tiene autoridad»; 14 §8: índice
     derivado «y reconstruible».
   - Corrección mínima: declarar la excepción en §11 (inv. 10 revisada) o persistir el `:phi` de los
     candidatos como evidencia de las aristas de ruteo.

4. **grave — §11 se contradice: «No toca `02 §5`» y a la vez añade validaciones de plan.**
   - 15 §11 «No toca: … `02 §5` (semántica del plan)»; 15 §11 «Revisa: … `kimun.kernel.plan`
     (…` :plan/rejected` de un `:add-node :fact/:triple` sin su `:projection` en el mismo plan …),
     `kimun.kernel.revision/apply-plan` (el `:plan/rejected` de un `:remove-edge` de la última
     `projection` activa…)».
   - 02 §5.1 fija los 7 chequeos de validación del plan (esas reglas nuevas son del mismo contrato).
   - Corrección mínima: mover el chequeo del hecho sin `projection` a «Revisa `02 §5.1`».

5. **grave — §11 omite en su revisión de `02 §6.2` la resolución de símbolos por taxonomía.**
   - 15 §6: «un símbolo resuelve si ocupa una posición en un árbol `:context` … o en una taxonomía»;
     15 §11: «endurecer **solo `:symbol/*`** a “pertenece a un árbol `:context`”».
   - 02 §6.2 (hito 7): «endurecerla a “pertenece a algún árbol `:context`”»; 12 §1 todavía lista
     «Concesiones de M/G: `:symbol` y `:fact` resuelven por existir en el CAS».
   - Corrección mínima: alinear §11 y `12 §1` con §6 (símbolos: `:context` o taxonomía).

6. **menor — Q9 fija un número que `14 §8` deja a la etapa A.**
   - 15 §8: «parámetro `:graph/dense-terms` de `store.edn` (default **propuesto 4096**…)»; §12 Q9
     default (a) 4096.
   - 14 §8: «El motor definitivo se confirma en la etapa A con el índice real» (16 §8 delega el tope).
   - Corrección mínima: default Q9 (b) «medir en la etapa A» mientras no haya medida.

7. **menor — Q2 ya está resuelta por la decisión 33.**
   - 15 §12 Q2: «cuándo se consolida el log de hechos (decisión 33, Q1=b)».
   - 14 §12 decisión 33 / 05 decisión 33: «una revisión por acción (build, assert, decisión) **más**
     un log de hechos append-only en flujo que se consolida en revisiones» (la consolidación por acción
     es el default (b) que Q2 propone).
   - Corrección mínima: marcar Q2 como resuelta por decisión 33 o acotarla al caso «al abrir».

8. **menor — el `:fact/entry` del fixture no coincide campo a campo con `10 §11`.**
   - 15 §4: «la forma del `:fact/entry` es la de `10 §11`»; `decision.edn` `:fact/entry` no lleva
     `:timestamp` y anida `:leaves` dentro de `:fact/entry`.
   - 10 §11: la línea del flujo lleva `:actor … :timestamp …`; «El documento de decisión `:action
     :assert` … embebe el mismo `:fact/entry` … **y añade `:leaves`**» (a nivel de documento).
   - Corrección mínima: añadir `:timestamp` al `:fact/entry` del fixture y colocar `:leaves` donde
     `10 §11` lo declara (o ajustar `10 §11`).

9. **menor — la promesa de rebuild sobre «el mismo `W_i`» choca con la no idempotencia del árbol.**
   - 15 §4: «re-crear un árbol de contexto **no es idempotente**» (ULID nuevo, inv. 13);
     15 §10: «un `rebuild` … produce el mismo `W_i` y los mismos hechos» (`g/rebuild-reproduces-contexts-and-facts`).
   - 02 §3.1: id de árbol nominal (ULID), descriptor en el CAS.
   - Corrección mínima: decir que se reproduce el nodo `:fact/:context` y los hechos, no el id del árbol.

10. **menor — el fixture `span2` trunca el último grafema y su comentario no se cumple.**
    - 15 §13: «una proyección por hecho con su span propio»; `facts.edn` `:sign/span2`
      `{:leaf :leaf/text2 :range [0 21]}` sobre «comparte es transitiva» (n=22) → «comparte es transitiv»;
      el comentario afirma que «cubre sujeto y predicado», pero el predicado del hecho es `property`,
      que no aparece en el texto.
    - 15 §6/§13: el span es válido (semiabierto, 0≤0≤21≤22) pero no cubre el objeto completo.
    - Corrección mínima: usar `[0 22]` o corregir el comentario del fixture.

## Ambigüedades

- **`projection` con dos sentidos.** 15 §2: «La relación de ruteo es un valor de `:relation`
  (`:equivalent|:projection`) … no … ni el tipo de arista `projection` M→G de §3» — reconoce el choque,
  pero la misma palabra designa tipo de arista y valor de ruteo; lecturas: (a) dos namespaces distintos,
  (b) un solo concepto reutilizado.
- **`:status` nulo.** 15 §3 «no nulo en cuanto hay decisión» vs 14 §5 «`:status ∈ {…}»: (a) `nil`
  permitido sin decisión, (b) `:status` siempre presente en el enum y `proposed` en `:curation`.
- **`:candidates` ausente en `:kind :contradiction`.** 15 §5 «la clave está **ausente** (no `[]`)» vs
  14 §6 «informe `{:context :facts [...] :kind :contradiction|:ambiguous :candidates [...]}`»:
  (a) `14` solo muestra forma general, (b) contradicción.
- **Término-hecho.** 15 §1 «un término suyo **designa** el nodo `:fact/:triple`»: (a) convención de
  nombre, (b) hace falta una arista/hecho que fije la designación (no definido).
- **`:granularity :transversal`.** 15 §2 usa `:granularity :transversal` y `:of {:transversal "data-class"}`:
  (a) `:transversal` es valor del enum y el nombre va en `:of`, (b) dos ejes distintos.
- **Número de preguntas.** §12 titula «Preguntas al dueño» y la tabla tiene 6 abiertas (Q1, Q2, Q4, Q6,
  Q8, Q9); Q3/Q5 se declaran eliminadas y Q7 remitida → no son «7».

## Veredicto

**ABIERTO.** Bloqueantes: **F1** (`:status nil` vs `14 §5`), **F2** (arista de ruteo `:semantic` sin
origen y con capa M↔M sin declarar) y **F3** (caché de conflictos no reconstruible desde log+pool,
inv. 10). El resto de formas declaradas y los fixtures son conformes campo a campo salvo F8 y F10;
los spans cumplen la convención semiabierta de §6.
