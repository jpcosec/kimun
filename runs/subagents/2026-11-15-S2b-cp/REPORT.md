# S2b-cp — mapeo de code points a grafemas NFC en la frontera

Worktree `/tmp/kimun-s2bcp-wt` (base `c8ca2f25`, sin commit).

## Archivos (solo nuevos; ningún archivo existente tocado)
- `src/kimun/surface/markdown/offsets.cljc` — ns `kimun.surface.markdown.offsets` (anillo markdown: kernel + host; `bb lint` lo acepta).
- `test/kimun/surface/markdown/offsets_test.cljc` — 6 pruebas, 193 aserciones.
- Evidencia: `lint.txt`, `directed.txt`, `mutation.txt`, `oracle.txt` en este directorio.

## API (pura, recibe `host` como el resto de `kimun.surface.markdown.*`; reutiliza `ports/graphemes` y `ports/nfc`, sin segunda definición de grafema)
- `(cp-range->grapheme-range host text [start end])` -> `{:range [gs ge] :text <NFC de text>}`.
- `(grapheme-range->cp-range host text [start end])` -> `{:range [s e]}` en code points del texto **tal como se dio** (no el NFC).
- Error tipado, sin lanzar: `{:error kind :why "..." :range .. :offset ..}` con `kind` en `:invalid-range`, `:out-of-range`, `:splits-grapheme`, `:splits-nfc-grapheme`, `:segmentation-diverges`. Forma elegida por analogía con `inline` (devuelve `:unparsed` en vez de lanzar).
- Método: se segmenta el texto original en clusters, se pasa a NFC cada cluster, y se construyen dos tablas de fronteras (offset en code points; offset en grafemas NFC). Si NFC de los clusters no concuerda con NFC del texto entero (texto y segmentación), devuelve `:segmentation-diverges` en vez de adivinar.

## Bordes (todos probados)
| borde | resultado |
|---|---|
| ASCII | identidad |
| par sustituto / astral | 1 code point, 1 grafema |
| `e`+U+0301 vs `é`; jamo hangul 3 cp | mismo rango NFC (`[1 2]`); NFC cambia el conteo de cp |
| corte entre base y combinante, dentro de ZWJ, dentro de CRLF | `:splits-grapheme` (rechazo declarado) |
| rango vacío `[k k]` en frontera | `[g g]` conservado; vacío dentro de un cluster = `:splits-grapheme` |
| fin == largo del texto | frontera válida |
| fuera del texto | `:out-of-range` |
| `[3 2]`, negativo, no entero | `:invalid-range` |
| fin de línea | CRLF es un grafema; `\n` solo, normal |

## Pruebas y discriminación
`directed.txt`: 6 tests, 193 aserciones, 0 fallos. `mutation.txt`: M0 (sin el ns) = error de carga; M1 (cps contados como grafemas) = fallan `nfc-changes-code-point-count` y `cuts-through-a-cluster…`; M2 (ajuste silencioso en vez de rechazo) = fallan las mismas. Ns restaurado idéntico. La propiedad de ida y vuelta (`round-trip-on-grapheme-boundaries`) recorre todos los pares de fronteras de un texto mixto (combinante, ZWJ, CRLF, bandera, hangul, `é`).
`bb lint`: `rings and docstrings ok: 111 files`. `bb oracle`: `9 rows, 0 mismatches` (no depende de este código). No se corrió `bb test` completo.

## BreakIterator en bb
Medido: ZWJ familia (8 unidades UTF-16) = 1 grafema; `e`+U+0301 = 1; bandera ES + FR = 2; CRLF = 1; pulgar + modificador de piel = 1; jamo L+V+T = 1. Sin discrepancias en los casos probados.

## Preguntas / decisiones no cubiertas por el spec (no resueltas en silencio)
1. **Cortar un grafema**: el plan del corte dice «rechaza declarado» y `14 §7` dice «nunca se descarta ni se sobreescribe en silencio»; el spec de `14 l. 356/373` y `02 §4.1` no dice qué hacer con un rango de `wittgenstein` que corta un cluster (¿rechazar, expandir al cluster, o truncar?). Implementé rechazo; la alternativa «expandir» necesita decisión del dueño.
2. **Texto sobre el que se miden los offsets** (`12 l. 209–212`: «code points sobre el documento entero vs grafemas por hoja»): la función mide sobre el texto que se le pasa; el mapeo documento->hoja y qué texto cuenta `wittgenstein` quedan al adaptador (fuera de este corte, supuesto [inf] del plan).
3. **Rango vacío**: el spec no habla de rangos vacíos (en `02 §4.1` el rango es `[inicio fin)` sin decir si `inicio=fin` es válido, y `inline/valid-marks?` exige `s < e` para marcas). Esta función los acepta y los mapea; S2b-r decide si los rechaza como marca.
4. **Offsets en unidades UTF-16**: si el productor cuenta unidades UTF-16 y no code points, la función no lo detecta (un offset dentro de un par sustituto no es expresable en code points). No se cubre.
5. `:splits-nfc-grapheme` (inversa, cota entre dos grafemas NFC nacidos de un mismo cluster) está implementado pero no se encontró texto que lo produzca; no tiene prueba que lo ejercite (lo digo con honestidad: es código defensivo no ejercitado).
6. `:segmentation-diverges` tampoco tiene prueba: no encontré un texto que lo provoque con BreakIterator de bb.
7. Q9 no se resuelve aquí (solo función pura).

## Línea propuesta para la fila `S2` de `12 §1`
Añadir al final de la celda de estado: «**Avance (S2b-cp, 2026-11-15):** `kimun.surface.markdown.offsets` (función pura, sin cablear a ningún comando): rango en code points <-> rango en grafemas NFC sobre el texto dado; un corte dentro de un grafema (combinante, ZWJ, CRLF) o fuera del texto se devuelve como error tipado, no se ajusta; 6 pruebas, `runs/subagents/2026-11-15-S2b-cp/`. Falta el adaptador de `wittgenstein` y el mapeo documento->hoja.»
