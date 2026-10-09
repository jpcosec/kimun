# S1b — Formato 3, segunda vuelta: el plan referencia nodos por id; `stores update` en una revisión

Lee primero `/home/jp/.cache/coord-briefs/close-common.md` (regla de memoria: todo `bb` con `memcap`)
y `/home/jp/.cache/coord-briefs/S1-report.md` (lo hecho y lo que quedó fuera, §4).
Worktree: `/home/jp/proyectos/kimun-worktrees/S1b`, rama `close/S1b` desde `main` (`eea1839d`, ya
incluye S1 y S2; construye el pod con `memcap 4 bb pod`).
Autoridad: `docs/v2/14 §10` (el texto vive UNA vez), `10-escala.md §10` (formato 3, actualízalo),
decisiones 27 y 33 en `05`.

## Por qué

Tras S1 el store de este repo pesa 8,75 MB aparentes por 433 KB de desk. El mayor bloque es el
**plan resuelto del `build` (4,71 MB)**: sus ops `{:op :add-node :node {…}}` llevan el contenido
de cada nodo embebido, y los mismos nodos ya están en el CAS como objetos. Es una copia del texto
que el 14 prohíbe. Segundo: `stores update` sigue haciendo una transacción por documento reparado.

## Qué hacer (cada paso con suite verde antes del siguiente)

1. **El plan resuelto referencia nodos por id.** En `kimun.kernel.revision/apply-plan`, el
   `resolved-plan` que va al CAS (y del que sale `tx-id`) lleva `{:op :add-node :id <node-id>}`
   en vez de `:node {…}`; el nodo entero va en `:new-objects` como hoy. `replay-open`,
   `verify-full`, `kimun.surface.journal/entry` y cualquier lector de planes reconstruyen el nodo
   desde el CAS por id cuando falte `:node`. El plan de ENTRADA (lo que escribe un llamante) no
   cambia: sigue aceptando `:node` inline. Planes ya almacenados con `:node` inline (formato 2 y 3
   anteriores) se siguen leyendo.
   - Consecuencia: `tx-id` cambia para el mismo plan de entrada. Antes de tocar nada, corre
     `memcap bb oracle` y mira si alguna de sus 9 filas es un id de transacción o de revisión. Si
     lo es, NO cambies el oráculo en silencio: documenta en el reporte qué fila cambia y por qué
     (el formato 3 define la forma del plan resuelto) y actualízala en un commit aparte con esa
     justificación. Los ids de nodo, arista, tree object, descriptor y mmap NO deben cambiar.
   - Mide con `bb store-size` antes y después (`include build --rebuild` en el worktree; el
     store es `:git :ignore`).
2. **`stores update` compone una sola revisión.** Reutiliza `kimun.kernel.plan/compose-plans`
   (S1): los `update-plan` de cada documento reparado se calculan en memoria sobre el estado que
   deja el anterior y se componen con `:base` = cabeza original; un solo `commit!`. Mantén el
   envelope y el journal que hoy producen (`operation`, campos cambiados por documento).
3. **Docs y promesas:** `10 §10` (forma del plan resuelto, tabla de tamaño nueva), `06` si cambia
   algo visible, `05` (cifras), `tests/promises.md` (promesa: "el plan resuelto de una revisión no
   contiene el contenido de ningún nodo" → test que la rompe).

## Límites

- No hagas el paso 2 de S1 (árbol como caché) ni toques `mmap`: es S1c, decisión aparte.
- No toques `:field-hashes`/`:content-hash` de la evidencia del anchor (S1 los puso a propósito).
- Verificación obligatoria: `bb lint`, `bb test`, `bb oracle`, `bin/kimun include check`,
  marcador `memcap python3 scripts/parity/coverage.py /home/jp/proyectos/hum-ecosystem/tools/sldb . --strict --allow-open-prefix selfdoc/` exit 0,
  y `include build --rebuild` + `bb store-size`.
- Commits por paso, trailer `Co-Authored-By: Claude Fable 5.1 <noreply@anthropic.com>`. Sin push.

## Reporte

`/home/jp/.cache/coord-briefs/S1b-report.md`: qué cambió, tabla de tamaño antes/después, qué ids
cambiaron (si alguno) y la salida de la batería.
