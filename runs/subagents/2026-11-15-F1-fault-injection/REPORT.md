# F1 — fallos entre commit y escritura del archivo (2026-11-15)

Archivo nuevo: `test/kimun/cli/fault_injection_test.cljc` (5 pruebas, 39 aserciones, 0 fallos; `directed.txt`).
`src/` intacto (`git status src` limpio tras la mutación). `bb lint` ok (`lint.txt`). Fallo inyectado: `with-redefs` de `clojure.core/spit` que lanza para una ruta (no depende del uid).
Mutación (`mutation.txt`): `write-file!` de `docs.cljc` con `catch → []` (sin warning) → falla `docs-update-whose-file-write-fails…`; restaurado con `git checkout`.

## Pruebas
1. `docs update --set` con fallo de escritura: ok + warning `docs/file-not-written`; revisión nueva existe y es la cabeza; archivo con contenido anterior; `docs show` con el nuevo; `stores health` = `needs-human`, finding `:edited` (doc `a`, `docs/a.md`), acción `kimun docs update a`, `:next "inspect: docs/a.md"`; `stores update` omite el doc, no escribe nada.
2. `fields set` con el mismo fallo: mismas señales.
3. `docs update --set` repetido con el archivo ya escribible converge: sin warnings, `:unchanged? true`, sin revisión nueva, archivo escrito, health `ok` sin findings.
4. Lectores (`stores health`, `docs show`, `stores verify`) con `cache/` vaciado y toda escritura a `cache/` fallando: ok, sin revisión nueva.
5. Tras dos fallos de archivo (docs update y fields set): ningún nombre `.tmp` bajo `.kimun/`, `journal verify` ok. (Limitación: la inyección no toca escrituras de `objects/`, así que esta prueba solo muestra que estos fallos no dejan temporales.)

## Tabla
| Fallo inyectado | Qué promete el spec | Qué pasa hoy | ¿Señal? | Reparación |
|---|---|---|---|---|
| `docs update --set` / `fields set`: falla `spit` del archivo tras commit | `10 §11` l. 397 «Ningún cambio silencioso», reparación `docs update NAME`/`stores update`; `06` l. 516 `needs-human` con `:next` de inspección | commit hecho, archivo viejo, warning en el envelope (exit ok) | sí: warning + `stores health` `needs-human` `:edited` | `stores update` NO lo repara (lo omite: necesita decisión). Reintentar `docs update NAME --set …` con archivo escribible converge (prueba 3). El mensaje del finding ofrece las dos salidas (declarar la edición o restaurar el archivo desde `docs show`) |
| Escritura a `cache/` falla durante lectores | `10` l. 758 «Un lector no escribe ni falla por la caché» | lectores ok, store intacto | n/a | n/a |
| Escritura a `cache/` falla durante una mutación | **el spec no lo dice** | `docs update --set` devuelve `ok false`, `cli/unexpected` mensaje «inj»; no hay commit y el archivo queda igual (comprobado a mano, no en la suite) | sí, error ruidoso pero genérico | repetir cuando la caché sea escribible |

## Hallazgos (no están en la suite verde)
- **H1 `journal verify` falla si se borra `cache/`.** Reproducción: proyecto con modelo, `docs track`, `docs update a --set status=done`, `rm -r .kimun/cache`, `journal verify` → `ok false`, `store/corrupt` («store: missing resolved plan for a log line», `:replay-failed`). `stores verify` sigue ok. Contradice `10 §11` l. 387 («borrable: todo se recalcula») y el exit 5 para un store sano. Es la causa de que mi borrador de la prueba 4 fallara en `journal verify` (lo retiré de esa prueba; la prueba 5 lo corre solo con caché tibia).
- **H2 `fields set` repetido no converge.** Por lectura de código (`fields.cljc`, rama `:unchanged?` antes de `write-file!`), no ejecutado: con el store ya con el valor, `fields set` devuelve `unchanged? true` sin escribir el archivo; el health sigue `needs-human`. `docs update --set` sí reescribe (prueba 3).
- **H3 Dirección engañosa.** La categoría es `:edited` («edited outside the store») aunque el archivo no fue editado: es el store el que va por delante. Para un operador, `docs update a` sin payload leería el archivo viejo y revertiría el cambio con una revisión nueva (visible en el journal; no es silencio, pero es una pérdida si se elige a ciegas).

## Ambigüedades del spec
- Fallo de caché en una mutación: `10 §11` solo promete lectores (l. 758); no dice si una escritura debe tolerar caché no escribible. Hoy aborta antes de commit.
- No hay protocolo de reparación general entre commit y archivo (`parity-v1.md`: «no hay outbox»); el corte solo describe.
- No cubierto: fallo de escritura de `objects/`, `heads.edn` ni `cache/` por permisos reales (solo `spit`; `Files/write` del store no se inyectó).
