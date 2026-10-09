# GATE15 — Carril A (coherencia), ronda 3

Auditor sin contexto, solo lectura. Documento auditado: `docs/v2/15-capa-g.md` + `test/fixtures/g/*.edn`.
Fuentes citadas: `14`, `02`, `05`, `10 §11`, `06 §3`, y el test de fixtures.

## Hallazgos

1. **grave** — El esquema del índice G se contradice entre `15` y su dueño de formato.
   - 15 §8: «Fija B-A: el `:a` de un datom es el **id del término predicado `R`** (no una keyword de atributo)».
   - 10 §11: «Esquema de datoms: `{:e <id> :a <keyword> :v <id|literal> :prov {…}}` (`15 §8`)»; ese mismo §11 se declara «el **único dueño del formato en disco**».
   - Corrección mínima: alinear `10 §11` a `:a <id>` (15 es coherente con §1: `R` es un id de término), en un solo sitio.

2. **menor** — El datom admite literales crudos, contra Am1.
   - 15 §8: `:v <id|literal>`.
   - 15 §1: «Fija Am1: un literal es un término también dentro de `:form` (no hay literales crudos en las hojas de una proposición)».
   - Corrección mínima: escribir `:v <id>` en §8 (el literal es el término de `literals`).

3. **menor** — §11 no declara el cambio de hojas de `:symbol/:proposition` (Am1).
   - 15 §1: «no hay literales crudos en las hojas de una proposición».
   - 02 §2.1: «`:symbol/:proposition` … args son ids de símbolos **o literales**».
   - 15 §11 (fila `02 §2.1`) solo registra «`:symbol/:proposition` admite formas anidadas», no la restricción de hojas.
   - Corrección mínima: añadir a §11: «`:symbol/:proposition :form`: hojas solo ids de término (Am1)».

4. **menor** — Se usa un formato opaco nuevo sin declararlo en «qué revisa».
   - 15 §1: «un signo `:sign/:opaque {:format "edn/vocabulary" :blob "<canonical-bytes>"}`».
   - 02 §2.1: la fila `:sign/:opaque` fija `{:format "html" :blob …}`; 15 §11 no lista `02 §2.1` por este punto.
   - Corrección mínima: registrar `:format "edn/vocabulary"` en §11 (o marcarlo como propuesta en §1).

5. **menor** — Se fija la convención semiabierta del span sin declararlo como revisión de `02 §4.1`.
   - 15 §6: «Un `[inicio fin]` … es **semiabierto**: `inicio ≤ i < fin`, con `fin` exclusivo».
   - 02 §4.1: «Una dirección es `[leaf-id inicio fin]` con offsets en grafemas» (no fija semiabierto).
   - 15 §11 no lista `02 §4.1`.
   - Corrección mínima: añadir `02 §4.1` a §11 (o «revisa» en §6).

6. **menor** — El fixture de conflicto no satisface el tipo que declara §5.
   - 15 §5: `:conflict/id "<H(canonical-bytes del informe sin :conflict/id)>"`.
   - 15 §13: «`:conflict/id nil` como placeholder explícito»; `fixtures_test` afirma `(nil? (:conflict/id cf))`.
   - Corrección mínima: admitir `nil` en la forma de §5 para el fixture, o poner un hash real.

7. **menor** — Los fixtures no ejercen las formas de taxonomía ni los hijos ownership del árbol de contexto.
   - 15 §13: los cinco fixtures «cumplen campo a campo la forma que este documento declara».
   - 15 §1: «los términos y relaciones admisibles son **sus hijos**» y taxonomías `{:kind :taxonomy :name "terms"}`; 02 §3.1 descriptor `{:tree :kind :name :root}`.
   - En `test/fixtures/g/` solo hay un descriptor `{:kind :context}`; no hay ningún `{:kind :taxonomy}` ni aristas `ownership`, así que `g/vocabulary-axis-respects-taxonomies` (§10) no es verificable.
   - Corrección mínima: añadir esos descriptores/aristas o declarar explícitamente que quedan fuera del alcance del anexo.

8. **menor** — `15 §8` invoca `graph rebuild`, que no aparece en su propia CLI (§9) ni en `06`.
   - 15 §8: «`graph rebuild` lo reconstruye; leer nunca escribe».
   - 15 §9: la fila `graph` solo ofrece `graph query` y `graph rule`; 06 §3 documenta `rebuild-indexes`.
   - Corrección mínima: añadir `graph rebuild` a §9 o usar el nombre existente `rebuild-indexes`.

9. **menor** — Duplicación de punto de entrada de conflictos en §9.
   - 15 §9: la fila `contexts` incluye `conflicts [--all]` y además existe el grupo `conflicts` (`list`/`show`/`resolve`).
   - Corrección mínima: dejar un solo punto de entrada (o justificar el solapamiento).

10. **menor** — Q5 ya está decidida por `14`/`05`; su premisa cita mal la fuente.
    - 15 §12 Q5 (jerarquía de `R/A/B`) con default (a).
    - 14 §4: «en Clojure esto es un `hierarchy` (…), que es un valor EDN y por tanto serializable y content-addressed»; 05 decisión 23: «`R`, `A`, `B` son posiciones en taxonomías con herencia»; 14 §2: «canónico ≠ persistido».
    - Corrección mínima: eliminar Q5 (ya resuelta por decisión 23 + 14 §2) o reformularla como «¿se persiste la vista `hierarchy`?».

11. **menor** — Q1 está decidida a medias.
    - 15 §12 Q1: (a) `:proposed` dentro de `:status` vs (b) campo `:curation` aparte.
    - 14 §17 Q8 («salida de prompt = siempre `proposed`») y 05 decisión 31 ya fijan que la salida de motor es `proposed`; solo queda abierto **dónde** se guarda.
    - Corrección mínima: acotar Q1 a la colocación del campo, no a la semántica.

12. **menor** — P11 (`14 §1`) no queda cubierto ni declarado fuera de alcance.
    - 14 §1 P11: «Los embeddings sobre anchors dan una ubicación pseudo-estable de co-ocurrencia, útil a escala; **co-ocurrencia ≠ `(R A B)`**»; 14 §10 fija `index/vectors/`.
    - 15 §11: «Tampoco fija … ni P14 … ni P15» (no menciona P11 ni `index/vectors/`).
    - Corrección mínima: añadir P11/`index/vectors/` a la lista de lo no fijado.

13. **menor** — La relación `member` y `L_transversal` son norma nueva solo débilmente trazada.
    - 15 §2: «Fija A-H5: … `L_transversal` … **declara la relación `member`**».
    - 14 §16 P13 solo dice: «Pendiente: qué transversales son de primera clase y quién afirma la pertenencia (selector o actor)»; 05 decisión 34 nombra el transversal, no el predicado.
    - Corrección mínima: escalar `member`/`L_transversal` en §12 o citar de qué decisión se derivan.

14. **menor** — §11 no registra la revisión de `10 §10`/decisión 27 por el inline forzado.
    - 15 §6: «las hojas `:sign/:text` … se persisten **con su texto** (inline obligatorio para ellas) aunque el store sea `:content :by-reference`» (cita `10 §10`).
    - 05 decisión 27 / 14 §10: «contenido por referencia con `inline` **por caso**»; §11 solo asigna a `10` la sección de disco, no esta política.
    - Corrección mínima: añadir `10 §10` a §11 como revisado por B-5.

## Ambigüedades

- **`:status` de motor.** 15 §3/§7 usan `:status :proposed` por defecto; 14 §5 enumera `:status ∈ {observed confirmed rejected retracted}`. Lecturas: (a) `proposed` se añade a `:status`; (b) `:status` de decisión + `:curation` de propuesta (es Q1, sin resolver).
- **`:projection` homónimo.** 15 §2: «la relación de ruteo es un valor de `:relation` (`:equivalent|:projection`) dentro de la evidencia de una arista `:semantic`». Lecturas: (i) valor de evidencia; (ii) el tipo de arista `:projection` M→G de §3. 15 reconoce el choque (Am2/B-C) pero no renombra.
- **A-H4 `:root`.** 15 §1: `:root` de la taxonomía de vocabulario es **nombre**; `:root` del descriptor `02 §3.1` es **id**. Lecturas de «taxonomía `{:kind :taxonomy :name "terms"}`»: descriptor de árbol o entrada de vocabulario.
- **«peor de su cadena de anclaje».** 15 §6: el estado del hecho es el peor de las aristas de su cadena. Lecturas: solo aristas del camino hecho→hoja (projection+binding) o toda arista que toque sus nodos.
- **`:relations` vs `predicates`.** 15 §1: «no hay tabla aparte». 06 §3: existe el grupo `predicates` (`predicates add NAME --axis A …`) que persiste predicado+eje. Lecturas: `L_i :relations` lo sustituye, o son capas distintas.
- **Datalog «in-process».** 15 §8: «evaluador Datalog semi-naive in-process … es la única vía offline». 14 §8: «el único Datalog que carga es el pod de Datalevin». Lecturas: evaluador propio vs pod; 15 no aclara qué cuenta como «offline».
- **Número de preguntas.** El brief dice 7; 15 §12 tabula 6 (Q1, Q2, Q4, Q5, Q6, Q8); Q3 eliminada y Q7 remitida a `13`.

## Veredicto

`CERRADO CON MENORES` — sin bloqueantes en `15` frente a sus autoridades citadas. Los hallazgos 1–5 y 14 son de alineación de declaraciones/revisiones; resolver el hallazgo 1 con `10 §11` es requisito previo de cualquier implementación del índice G (Carril B).
