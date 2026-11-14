# Borrador de contrato S–M en dos etapas: realidad, anchoring y hecho detectado

> **Estado:** Propuesta técnica del concilio (borrador de trabajo sobre [`brief-d0-dos-etapas.md`](file:///home/jp/proyectos/kimun/runs/research/2026-11-14/concilio/brief-d0-dos-etapas.md) y [`d0-dos-etapas-{claude,pi,agy}.md`](file:///home/jp/proyectos/kimun/runs/research/2026-11-14/concilio/d0-dos-etapas-agy.md)). **Solo investigación**: no modifica `docs/v2/` ni código, **no inventa formatos de ID** ni **diseña la capa G**.
>
> **Convención de autoridad:**
> - **`[D]`** = orientación textual dada por el dueño (`14-juego-del-lenguaje.md` cabeceras y `brief-d0-dos-etapas.md`).
> - **`[C]`** = propuesta contractual mínima de síntesis del concilio (requiere validación antes de entrar a `K1`).
> - **`[ABIERTO]`** = punto bloqueante reservado al dueño o pendiente de diseño sin resolver aquí.

---

## 1. Tres planos y dos etapas en S–M

1. **Realidad (`[D]` fuera de `kimun`):** El entorno cambiante (archivos fuente en disco, árboles sintácticos en el árbol de trabajo, ejecuciones y entorno externo; `00-nucleo.md` §6.1–6.2; *Tractatus* `2.06`). No se clona como respaldo CAS dentro de `kimun` y es el único dominio donde se evalúa la verdad de una proposición.
2. **Etapa 1 — *Anchoring* (`[D]` / `[C]` plano `S`):** Mecanismo y registro que localiza y sensoriza una porción perceptible de la realidad (**signo** bajo contrato de observación `K6` + huella Merkle; `14` cabecera, `00-nucleo.md` §2.3, §3.1). Equivalente funcional en Zettelkasten a la referencia bibliográfica/localizador de página hacia la fuente externa.
3. **Etapa 2 — Hecho detectado / unidad relacional (`[D]` / `[C]` plano `S–M` / `M`):** Proposición o unidad `(R A B)` situada en un contexto, espacio mínimo de convergencia con relaciones declarables y embeddings continuos (`14` cabecera). Sus extremos `A` y `B` permiten recursión (`[D]`: `R` relaciona hechos `A` y `B`, lo cual es formulación del dueño para `kimun` y su paper, no texto literal de *Tractatus* `2.01`/`2.02`). Equivalente en Zettelkasten a la ficha direccionable cuya significación proviene de sus vínculos.

---

## 2. Semántica mínima `anchor → hecho detectado` y cardinalidad `N:M`

### 2.1 Reglas semánticas mínimas (`[C]`)
1. **No-colapso de planos:** Ningún objeto de la realidad es un nodo del grafo. El **anchor** (Etapa 1) no es el **hecho detectado** (Etapa 2); el hash Merkle detecta mutaciones del soporte en Etapa 1 y no constituye identidad ni verdad en ninguna de las dos etapas (`14` cabecera).
2. **Vínculo explícito de soporte (`supports` / procedencia):** La transición `anchor → hecho detectado` no es derivación automática de verdad ni equivalencia de identidad, sino una relación explícita y auditable de **soporte observacional** (entre Etapa 1 y Etapa 2):
   - Un *anchor* aporta evidencia de qué fragmento o ejecución de la realidad respaldó o motivó la detección/declaración del hecho.
   - Si el hecho requiere término base en la recursión `(R A B)`, la conexión permanece mediada por observación declarada/trazada (queda `[ABIERTO]` en §5 si el anchor mismo actúa como término hoja o como hecho elemental `(anclado-en ...)`).
3. **Independencia de identidad:** La identidad del *anchor* (por definir en `D0`) y la identidad del hecho/unidad de Etapa 2 (por definir en `D0`) son independientes de la ruta cambiante y del valor del hash. Igualar huellas o similitud entre textos de Etapa 1 **no** fusiona dos hechos de Etapa 2 ni identifica dos ocurrencias en fuentes distintas (`00-nucleo.md` §1.4, §3.1).

### 2.2 Cardinalidad `N:M` (`[C]`)
La relación entre anchors de Etapa 1 (`A`) y hechos detectados de Etapa 2 (`H`) es **`N:M`** (vía aristas de soporte tipadas):
- **`1 → M` (un anchor soporta varios hechos):** Una única función marcada o sección documental puede sustentar múltiples hechos `(R A B)` distintos (p. ej., pertenencia modular, invariante declarado y vínculo con un requisito).
- **`N → 1` (varios anchors soportan un mismo hecho):** Un hecho detectado o unidad relacional puede requerir soporte simultáneo de múltiples fuentes en la realidad (p. ej., código en `.cljc` + prueba unitaria + especificación, o corroboración desde dos fuentes independientes).
- **`0 → 1` (soporte agotado / degradado):** Si todos los anchors que respaldaban a `H` desaparecen en la realidad, `H` **no se borra ni destruye sus relaciones en red**: pasa a exhibir pérdida explícita de soporte (`00-nucleo.md` §2.4).
- **`1 → 0` (anchor sin hecho promovido):** Puede existir un fragmento sensorizado/anclado en Etapa 1 que aún no sustenta hechos declarados en Etapa 2 (`trackeado` en espera de extracción/revisión).

---

## 3. Régimen de cambios, vigencia y verdad

### 3.1 Separación de verificaciones (`[D]` + `[C]`)
| Plano | Qué evalúa | ¿Tiene valor de verdad proposicional? | Fuente de autoridad |
|---|---|---|---|
| **Etapa 1 (*Anchor*)** | **Vigencia de soporte:** (1) la porción marcada en la realidad coincide con la huella Merkle bajo contrato `K6`; (2) si hay ejecución marcada bajo contrato, su recibo/resultado coincide con lo observado. | **No (`[C]`):** una referencia localizada no afirma sobre el mundo; sólo acredita que el signo/soporte no mutó o que una corrida acotada devolvió un código/salida. | Estado calculado del soporte contra la realidad actual del archivo/ejecución (`02 §6.2`, `02 §10` inv. 18). |
| **Etapa 2 (Hecho `(R A B)`)** | **Sentido y verdad (`[D]`):** primero admisibilidad contextual (sentido en `L_i`), luego **verdad evaluada en la realidad**. | **Sí (`[D]`):** verdadero o falso según su correspondencia con la realidad en su contexto. | La **realidad** (en S–M sólo es re-verificable mecánicamente lo reproducible por detector/ejecución bajo contrato; la evaluación general pertenece a `G`, fuera de este contrato). |

### 3.2 Matriz de transiciones ante cambios (`[C]`)
Ninguna transición del soporte en Etapa 1 **reemplaza silenciosamente** la historia ni **auto-decide verdad** en Etapa 2 (`00-nucleo.md` §2.4, §4.3; `02 §10` inv. 7, 11, 18):

| Evento en la realidad o contrato | Efecto observable en Etapa 1 (*Anchor*) | Efecto contractual sobre Etapa 2 (Hecho `H`) | Regla de verdad y prohibición |
|---|---|---|---|
| **Sin cambios** | Vigente / `intact` (Verificación 1 satisfecha). | Soporte vigente (`N:M` sin alteración). | No vuelve `TRUE` a `H`; sólo confirma estabilidad del soporte sígnico. |
| **Edición del fragmento** bajo mismo localizador | Huella Merkle distinta → señal `drifted` / `mutated` bajo mismo `K6`. | `H` conserva identidad y pasa a **soporte alterado / requiere revisión** respecto de ese anchor. | **Prohibido** marcar `H` como falso automáticamente o reescribir `H` en silencio. |
| **Copia idéntica en otra fuente** (mismo hash en ruta distinta) | Anchor original sigue `intact`; aparece un **segundo** anchor independiente con igual huella. | Los hechos vinculados al anchor original quedan intactos; el segundo anchor **no** hereda ni fusiona `H` automáticamente (`00-nucleo.md` §1.4). | **Prohibido** deducir continuidad o identidad por igualdad de hash. |
| **Movimiento / refactorización** (desaparece en ruta `X`, aparece en `Y`) | Anchor en `X` deja de resolver (`missing`/`orphan`); en `Y` hay observación candidata (con igual o cercana huella). | `H` conserva su identidad, señala pérdida de soporte de `X` y expone propuesta de re-anclaje hacia `Y`. | **Prohibido** re-apuntar en silencio mientras la regla de continuidad (§5.1) siga abierta. |
| **Borrado de una fuente** (siendo `N ≥ 2` vs `N = 1`) | Anchor borrado → `orphan` / `missing`; otros anchors del mismo `H` permanecen `intact`. | Si quedan otros anchors vigentes (`N ≥ 2`), `H` conserva soporte parcial con pérdida parcial declarada; si era el último (`N = 1`), queda en **sin soporte vigente**. | Nunca ocultar pérdida de soporte; jamás borrar la historia de `H`. |
| **Cambio de contrato `K6`** (cambia parser/selector sin mutar archivo) | Señal específica `contract-changed` (distinta de `source-changed`). | `H` queda en revisión por cambio de instrumento de observación, no por cambio del mundo. | Distingue mutación de la realidad de cambio en la lectura técnica. |
| **Ejecución marcada falla** (Verificación 2, cuando haya contrato) | Recibo/señal de ejecución marca fallo respecto del estado/dependencias vigentes. | Hechos cuyo soporte depende de esa ejecución reciben alerta de invalidez operacional de soporte. | Test en verde sólo acredita el predicado contratado; fallo operacional dispara revisión en realidad, no inferencia ciega en `G`. |

---

## 4. Caso trazado con fuente dual (`N:M` en S–M)

Sea un sistema donde convergen **dos fuentes reales distintas** (`Fuente A` y `Fuente B`), trazado sin inventar formato de ID (usando únicamente etiquetas descriptivas de observación `[p1]`, `[p2]`, ...):

### Estado inicial `t0`
- **Etapa 1 (2 Anchors en fuentes duales):**
  - `Anc_cod`: localizador en `src/kimun/kernel/reconcile.cljc` (función `reconcile`, huella `H_cod_1`, contrato `K6_clj`).
  - `Anc_doc`: localizador en `docs/v2/02-sustrato-computacional.md` (§6.5 «propuesta de reconciliación vive fuera del pool», huella `H_doc_1`, contrato `K6_md`).
- **Etapa 2 (2 Hechos detectados en configuración `N:M`):**
  - `Hecho_1`: `(regla reconcile no-auto-muta-pool)` — apoyado **conjuntamente** en `[Anc_cod, Anc_doc]` (**`2 → 1`**).
  - `Hecho_2`: `(pertenece reconcile modulo-kernel)` — apoyado en `[Anc_cod]` (haciendo **`1 → 2`** junto con `Hecho_1`, completando el esquema **`N:M`**).

### Evolución trazada `t1 → t4`
1. **`t1` — Edición sólo en `Fuente A` (`reconcile.cljc`):**
   - *Etapa 1:* `Anc_cod` cambia a huella `H_cod_2` → emite señal `drifted` (`source-changed`). `Anc_doc` sigue `intact` (`H_doc_1`).
   - *Etapa 2:* `Hecho_2` pierde soporte vigente total y pide revisión. `Hecho_1` conserva **soporte parcial** desde `Anc_doc` y entra en **desfase entre sus dos fuentes** (`Anc_cod` mutado vs `Anc_doc` intacto): señala que código y spec pueden haber divergido en la realidad, sin declarar falso a `Hecho_1`.
2. **`t2` — Copia idéntica del párrafo de `Fuente B` en una `Fuente C` (`notas/borrador.md`):**
   - *Etapa 1:* Nace un tercer anchor `Anc_borr` con idéntica huella `H_doc_1`.
   - *Etapa 2:* `Anc_borr` **no** se conecta automáticamente a `Hecho_1`. Si un humano/LLM lo enlaza explícitamente después, `Hecho_1` pasará a tener 3 anchors de soporte; hasta entonces son procedencias independientes (`00-nucleo.md` §1.4).
3. **`t3` — Movimiento de `reconcile` a `src/kimun/kernel/reconcile_v2.cljc` (con edición menor):**
   - *Etapa 1:* `Anc_cod` deja de resolver (`missing`/`orphan`); aparece candidato estructural `Anc_cod_v2` en la nueva ruta.
   - *Etapa 2:* `Hecho_1` y `Hecho_2` **conservan su identidad y sus vínculos relacionales**; registran que `Anc_cod` no resuelve y ofrecen `Anc_cod_v2` como candidato de continuidad para declaración explícita (sin auto-reemplazarlo).
4. **`t4` — Borrado completo de `docs/v2/02-sustrato-computacional.md`:**
   - *Etapa 1:* `Anc_doc` pasa a `missing`/`orphan`.
   - *Etapa 2:* Si `Anc_cod_v2` tampoco se ha re-anclado, `Hecho_1` alcanza `0` anchors vigentes: **conserva su nodo, su contexto y la traza histórica** de haber estado soportado por `Anc_cod` y `Anc_doc`, quedando marcado como **sin soporte vigente** (`00-nucleo.md` §2.4).

---

## 5. Preguntas realmente bloqueantes (frente a lo ya utilizable)

Lo único que **no** impide este borrador —y que ya puede observarse sin cambios de esquema— es un corte `S–M` de Etapa 1 centrado en localizador + contrato `K6` + huella (`stores sense` por unidades/selectores marcados, reportando `intact`/`mutated`/`missing`/`contract-changed` sin acuñar IDs nuevos ni emitir verdad de `G`).

En cambio, **cuatro preguntas reales siguen bloqueando el paso a `K1` (persistencia de identidad e interfaces definitivas) y a la integración completa de Etapa 2**:

1. **Bloqueo B1 — Regla de continuidad temporal de Etapa 1 y Etapa 2 ante movimiento/edición/división/fusión:**
   Cuando la realidad mueve o parte una topología marcada bajo un *anchor*, ¿el *anchor* original se **re-apunta** conservando el mismo ID bajo criterios estrictos, o los *anchors* son observaciones inmutables ligadas por una transacción/hecho explícito `(continúa A_viejo A_nuevo)` (cf. propuesta de `d0-dos-etapas-claude.md §5`)? Sin cerrar esto no puede implementarse `reconcile.cljc` ni `K1`.
2. **Bloqueo B2 — Identidad positiva e individuación de `Anchor` (Etapa 1) y `Hecho` (Etapa 2):**
   La cabecera de `14` prohíbe aún asumir `CAS`, `UUID` u otra codificación. Falta decidir: (a) cómo nace y se codifica el ID de Etapa 1 y de Etapa 2 antes de modificar `bb oracle`, y (b) si un hecho `(R A B)` de Etapa 2 se individúa por su **coordenada lógica** `(contexto, R, A, B)` (`03_proposed_representation.md`) o por un **identificador propio independiente** incluso cuando se corrige `R`/`A`/`B` o cuando dos fuentes detectan la misma proposición.
3. **Bloqueo B3 — Naturaleza del término base en `(R A B)` y topología del nodo:**
   El dueño dice que `R` relaciona **hechos** `A` y `B` en dos etapas (1- anchoring, 2- hecho detectado). Queda abierto: (a) si el *anchor* de Etapa 1 entra directamente como hoja base de `(R A B)` o mediante un **hecho elemental de observación** (p. ej. `(anclado-en ...)`, cf. `d0-dos-etapas-claude.md §3`), y (b) si en el modelo de datos de `S–M` el sensor (`S`) y el hecho/unidad (`M`) comparten un nodo con facetas o son dos nodos distintos conectados por aristas (`ambiguedades.md` `A1.1`).
4. **Bloqueo B4 — Deslinde operativo `S–M` vs `G` sobre «hechos detectados» y ejecuciones marcadas:**
   `12 §1` y `14 §19` ubicaban todos los hechos y contextos en la capa `G` (después de `S–M`), mientras el enfoque de dos etapas introduce «hecho detectado» y relaciones declarables ya en `S–M`. Falta delimitar exactamente qué hechos `(R A B)` pueden **detectarse y verificarse mecánicamente en `S–M`** (topológicos y ejecuciones bajo contrato `Verificación 2`) frente a lo que espera a la lógica de verdad y sentido de **`G`** (`D-G`, `12 §1` `G1`), sin reintroducir `G` prematuramente ni inferir verdad por soporte.
