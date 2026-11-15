# M1d — modo `:strict` del selector

## Archivos tocados
- `src/kimun/surface/models/selector.cljc`: `extract-selector` gana la aridad `[steps text mode]`. `mode` es `:legacy-v1-template` (por defecto; la aridad de 2 argumentos sigue igual) o `:strict`, con los nombres de `:compat` del spec (l. 185-188). Un modo desconocido lanza `:models/invalid` con el modo en el mensaje y en `ex-data` (`:mode`). Nuevo `selector/modes`. `regular` anota los anclajes que acertaron en `:hits`, una clave que legacy nunca lee.
- `src/kimun/surface/models/extract.cljc`: `extract-raw` y `extract` ganan un `mode` opcional que pasan tal cual. Sin él, legacy.
- `test/kimun/surface/models/selector_test.cljc`: 4 pruebas nuevas.
- No se tocó nada más (`docs`, `subgraph`, `compile`, `descriptor`, `store`, `cli/` y el kernel quedan igual).

## Qué hace strict, con cita
1. Spec 16 l. 185-188 (regla) y punto 1 (l. 177-180): un campo `:capture :section-body` cuyo encabezado no está queda ausente del resultado en bruto. `finalize` lo convierte en `:validate/missing` si es requerido. Legacy lo captura igual.
   - Ver «Aviso 4» sobre cómo se decide que la sección está presente.
2. Spec 16 l. 185-188: una sección presente y vacía da `""`. Strict lo da incluso en un campo opcional (`optrev`), donde legacy omite la clave.
   - Es lectura literal de la regla (l. 185-188), que no distingue requerido de opcional.
   - Para un campo requerido legacy ya daba `""` (ver «Aviso 1»).

## Números
- `directed.txt`: 17 namespaces, 129 pruebas, 1878 aserciones, 0 fallos. Incluye `parity_test` (2384 pass, 0 fail), `corpus_test` (cli e index), `typed_docs_test`, `docs_parity_test`, `blocks_parity_test` (735/735), `golden_test` y todo `test/kimun/surface/models/`. Ninguno se modificó.
- `lint.txt`: ok (108 archivos). `oracle.txt`: 9 filas, 0 diferencias.
- `selector_test`: 9 pruebas, 1210 aserciones, 0 fallos.

## Qué prueba cada test nuevo
- `strict-reports-an-absent-section-as-missing`: en tres cuerpos de `AtomDoc` sin `## Answer`, legacy captura el cuerpo, strict omite `answer`, `extract … :strict` lanza `:validate/missing` y `extract` sin modo sigue en legacy.
- `strict-gives-a-present-empty-section-the-empty-string`: con una plantilla ad hoc (`## Notes` con `optrev•notes`) y `## Notes` vacío, legacy omite `notes` y strict da `""`.
- `strict-equals-legacy-on-a-document-without-quirks`: un `AtomDoc` con `## Answer`, y el texto de plantilla de cada uno de los 29 descriptores golden, dan lo mismo en ambos modos.
- `an-unknown-mode-fails-naming-it`: `:lenient` falla con `:models/invalid` y el nombre del modo.
- Las 4 usan el modo nuevo, así que sin mi cambio fallan por aridad. Por eso la mutación es la prueba que discrimina de verdad.

## Mutación (`mutation.txt`)
| Mutación | Resultado |
|---|---|
| M1: strict no corrige nada | 7 fallos |
| M2: strict sin el `""` | 1 fallo (la de `""`) |
| M3: strict sin el `:missing` | 6 fallos (la de ausente) |
| M4: modo desconocido aceptado | fallo de la prueba 4 |
| M5: strict descarta secciones ancladas aunque existan | 31 fallos (la de equivalencia) |

Cada una rompe la prueba que corresponde. El archivo se restauró y coincide con la versión final.

## Avisos, ambigüedades y lo que NO se implementó
1. **Quirk de `TaskDoc` (spec l. 181-184): no verificable.**
   - Con la plantilla de `TaskDoc` y `## Done When` vacío (con o sin salto de línea, con `_…_`, o seguido de otro `##`), legacy ya da `""`. Está en `/tmp/m1d-exp.clj`, que no se guarda.
   - No hay nada que corregir ni una prueba honesta del "heading incluido". Strict no añade lógica para ese caso.
   - Lo único que hace strict con «presente y vacío» es darlo también en campos opcionales (punto 2), que es otro comportamiento.
   - Texto del spec, l. 181-184: «v1 devolvía `"## Done When\n\n_…_"`». No reproducido; los dos documentos reales no están en el repo.
2. **Línea 178 del spec («capturaba desde el principio del documento»).** Lo medido en M1c es otra cosa: captura desde donde terminó el último bloque alineado. La corrección que propongo para el texto es: «capturaba el cuerpo que seguía al último bloque alineado, aunque faltara el encabezado `Answer`».
3. **El ancla es por tipo y nivel, no por texto.** `match-at` de un paso `:anchor` solo mira `type` y `tag`, no el regex. Un `## Otra cosa` cuenta como `## Answer` presente.
   - Strict considera la sección ausente solo cuando no hay ningún encabezado de ese nivel en su sitio. Es el comportamiento del motor, y lo documento porque «sin su sección» del spec no dice si se compara el texto.
   - Por la misma razón no probé el caso «optativa ausente» con otro `##`: lo capturaría ella.
4. **Qué ancla decide la presencia.** Bajo un encabezado la plantilla pone un párrafo-placeholder (`_…_`) que también es ancla, y un documento puede quitarlo. Decido con el encabezado más cercano de la cadena de anclas previa; si no hay ninguno, con el ancla inmediata. Es decisión mía: el spec no la define.
5. **Strict sin ancla previa.** Un `:section-body` sin ancla previa (la sección no se puede detectar) se comporta como legacy en strict. No inventé qué sería «ausente» ahí.
6. No se elige modo por modelo ni se cambia el default de los descriptores v1 (es M2).

## Propuestas para el dueño (no toqué `12` ni `16`)
- Fila M1 de `docs/v2/12`: «M1d hecha: `selector/extract-selector … :strict` corrige el campo requerido sin su sección (ausente → `:validate/missing`) y da `""` a la sección presente vacía (también en opcionales); el default sigue en `:legacy-v1-template`; el quirk de `TaskDoc` (16 §3.5, punto 2) no se reproduce, legacy ya da `""`.»
- Spec 16 l. 178: sustituir «capturaba desde el principio del documento» por «capturaba el cuerpo que seguía al último bloque alineado, aunque faltara el encabezado `Answer`».
- Spec 16 l. 181-184: el dueño debe decidir. O se retira el punto 2 de §3.5 por no reproducible con las plantillas del repo, o se aportan los dos documentos (`task-specify-diagram-traceability-contract`, `task-specify-specyaml-schema-contract`) para fijar el quirk con una prueba.
- Spec 16 §3.5: aclarar que el ancla se compara por tipo y nivel, no por texto de encabezado (aviso 3).
