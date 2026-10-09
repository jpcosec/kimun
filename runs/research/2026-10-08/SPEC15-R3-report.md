# SPEC15-R3 — Reporte

Worktree `/home/jp/proyectos/kimun-worktrees/spec15`, rama `close/spec15`.
Commit: `031653a8` — `spec15(R3): corrige los hallazgos del gate de ronda 3`.
Sin push.

Archivos tocados: `docs/v2/15-capa-g.md`, `docs/v2/10-escala.md`,
`docs/v2/12-ecosistema-y-pendientes.md`, `test/fixtures/g/context.edn`,
`test/kimun/g/fixtures_test.cljc`.

## Hallazgo → cambio

| hallazgo | cambio |
|---|---|
| A-1 / B-F1 ‑ esquema del datom contradictorio | `10 §11` queda como **único dueño**: `{:e <id> :a <id> :v <id> :prov {…}}`, `:a` id de `R`, `:v` id de `B`. `15 §8` solo cita (`15` no los redefine). |
| A-2 ‑ literales crudos en el datom | eliminados: `:v <id>` (un literal es el término de `literals`, Am1), en `10 §11` y en la cita de `15 §8`. |
| B-F2 / B-amb 1 / B-3 ‑ `p×p`, matrices y cierre | `15 §8` reescrito: (a) el veto `14 §8` es contra corpus/taxonomía entera; (b) por `W_i` y `R` la matriz es densa sobre los términos que ocurren en hechos de `R` (filas=sujetos, columnas=objetos); (c) el cierre por *squaring* solo si `R` transitiva en ese `W_i` **sí añade pares** `(A,C)`, cota = alcanzabilidad `≤ |sujetos|·|objetos|`, nunca el corpus; (d) candidato = par en hechos **o** alcanzado por el cierre. Quitada la cita «a lo sumo por `W_i`». Promesa de §10 reformulada a cota medible sobre un `W_i` (+ fila nueva para el cierre). |
| B-F4 / B-amb 2 ‑ ids de árbol | `15 §4`: nodos y aristas content-addressed; los árboles (incl. `{:kind :context}`) son **ULIDs nominales** (`02 §3.1`, inv. 13); re-crear un contexto no es idempotente y el contexto se reencuentra por el **nodo `:fact/:context`** content-addressed, no por el ULID. |
| B-F5 ‑ toda escritura de hechos deja decisión | `15 §4` + `§9`: `facts assert` y cualquier `--write` de un `:fact/entry` (`facts plan --write`) materializan el documento `:action :assert`; no hay ruta de escritura no reconstruible. |
| B-F6 ‑ pendientes al abrir | `15 §4`: el aviso lo emite la **CLI tras `open`** (lee `facts.log.edn` por el host); `open` no cambia (`10 §6`, `02 §8.1`). En `§11` se retira `Backend` de la fila de kernel. |
| B-F7 ‑ punto de extensión del plan | `15 §7`: `kimun.surface.adapters/plan-propositions` devuelve ops de `TransactionPlan` (`:fact/:triple`, `:symbol/*`, aristas `binding`/`projection`); `plan-documents` queda para perfiles de documento; el despacho por perfil en `validate` sigue. |
| B-F11 ‑ anillo del evaluador | `15 §8` + `§11`: `kimun.kernel.datalog` es **ring 0 puro** (sobre valores, alimentado por el host); `index/graph/` lo lee/escribe el host (ring 1) y lo orquesta la CLI (ring 3). |
| B-F8 ‑ `06` fuera de «Revisa» | `15 §11`: fila `06` (grupos nuevos, `--write`, warning `facts/pending-upstream`). |
| B-F9 ‑ enunciados sin promesa | `15 §10` añade filas: `:candidates` solo en `:ambiguous`; `--describe` sale 0 sin runtime; «ninguna arista entra al índice sin procedencia»; «el fallback in-process nunca desaparece»; deriva `contexts/conflict` (4). Nota de nombres conforme a `03 §5` (`*_test.cljc`). |
| B-F10 / A-6 / A-7 ‑ fixtures | `context.edn` gana los tres descriptores `{:kind :taxonomy}` (`terms`/`relations`/`literals`) y las aristas `ownership` del árbol de contexto como posiciones `{:tree :parent :to :order}`. `§5` admite `:conflict/id nil` en fixtures de aliases; `fixtures_test` comprueba taxonomías, ownership, y el tipo del conflicto. `§13` actualizado. |
| A-3 ‑ hojas de `:symbol/:proposition` | `§11` fila `02 §2.1`: hojas solo ids de término (Am1). |
| A-4 ‑ formato opaco nuevo | `§11` fila `02 §2.1`: admite `:format "edn/vocabulary"`. |
| A-5 ‑ span semiabierto | `§11` fila nueva `02 §4.1`. |
| A-8 ‑ `graph rebuild` | `§9`: añadido al grupo `graph` (con nota para `06`). |
| A-9 ‑ doble punto de entrada de conflictos | `§9`: quitado `conflicts` de `contexts`; `--all` pasa a `conflicts list`. |
| A-10 ‑ Q5 decidida, cita mala | Q5 **eliminada** de `§12` y trazada a `05` decisión 23 + `14 §4`/`§2`; Q1 reducida a la **colocación** de `proposed`. |
| A-11 ‑ P11 sin cubrir | `§11` «No toca/Tampoco fija»: P11/`index/vectors/`. |
| A-12 ‑ `member`/`L_transversal` | `§2`: traza a `05` decisión 34 y `14 §16 P13`. |
| A-14 ‑ inline forzado | `§11` fila `10`: `10 §10` revisado por el inline forzado (decisión 27). |
| B-amb 3 ‑ `:prov` obligatoria | `§6`: la obligatoria es **la clave `:prov`** (vector de spans ≥ 1) en la evidencia de `binding`. |
| B-amb 4 ‑ `:proposed` | `§11` kernel: `edge/statuses` ampliado con `:proposed` como default pendiente de Q1; `§12` lo repite. |

## Preguntas finales al dueño

Q1 (reducida: colocación de `proposed`), Q2, Q4, Q6, Q8. Q3, Q5 y Q7 quedan fuera
(decididas o remitidas a `13`), como pide el brief.

## Pruebas

```
$ memcap timeout 600 bb lint
rings and docstrings ok: 98 files

$ memcap timeout 900 bb test
...
Ran 110 namespaces in 108 s
{:test 891, :pass 9952, :fail 0, :error 0, :namespaces 110}
```

## Lo que no corregí y por qué

- **`14`**: no se toca (brief explícito). El veto `p×p` se interpreta, no se reescribe.
- **`06` (contenido)**: es el dueño del inventario; `15 §9` solo propone y `06` entra en
  «Revisa». No se editan sus tablas (habría sido fijar el inventario en el spec equivocado).
- **`02`**: los cambios de contrato se declaran en `§11` («Revisa»), no se aplican al
  documento dueño.
- **`12 §1`** (quitar el pendiente de capa G) es una edición de implementación posterior; solo
  se corrigió `12 §3` para que el esquema de datoms apunte a `10 §11`.
- **Ambigüedades B restantes** no listadas en las 12 resoluciones del brief (p. ej. `:projection`
  homónimo, `:root` nombre vs id, «peor de su cadena», `:relations` vs `predicates`,
  Datalog «in-process»): quedan como están; el brief no las incluye en la ronda.
