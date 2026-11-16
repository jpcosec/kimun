# Medición: `stores sense` frente a `stores changed --since`

Proyecto sintético en /tmp (generado con un script desechable en /tmp, ya borrado: `scripts/bench/gen_store.clj` está roto en este árbol, `tdocs/create-plan` ya no existe, así que no se pudo usar). N documentos de un modelo de dos campos, registrados en UN `include build` (N=500: 6,4 s; N=2000: 28,8 s) y commiteados en un repo git. Un documento (`d-0001`) con una marca de sección «Body». Casos: nada cambió; se edita `d-0002` (sin marca); se edita `d-0001` (con marca). `--since` = HEAD, cambios sin commitear. Cada medida es el proceso completo `bin/kimun …` (arranque de bb incluido) bajo `flock` y `memcap 3`; tres repeticiones, segundos de reloj.

Arranque: `bin/kimun stores show` (arranca bb, carga la CLI, lee el descriptor, no toca árboles) = 0,47 / 0,51 / 0,52 s (mínimo 0,47, mediana 0,51), medido sobre el proyecto de 500. (`bb -e nil` solo mide 0,03 s: no carga la CLI, no sirve de base.)

| N | caso | `stores sense` min / mediana | `stores changed --since` min / mediana |
|---|---|---|---|
| 500 | nada cambió | 3,21 / 3,24 | 0,92 / 0,94 |
| 500 | 1 archivo sin marca | 3,21 / 3,30 | 0,89 / 0,92 |
| 500 | 1 archivo con marca | 3,43 / 3,47 | 0,94 / 1,02 |
| 2000 | nada cambió | 11,67 / 12,91 | 2,27 / 2,31 |
| 2000 | 1 archivo sin marca | 11,57 / 12,56 | 2,22 / 2,28 |
| 2000 | 1 archivo con marca | 11,52 / 11,55 | 2,17 / 2,21 |

Trabajo aparte del arranque (resta de la mediana de arranque 0,51 s): sense ≈ 2,7 s (N=500) y ≈ 11–12,4 s (N=2000); changed ≈ 0,4–0,5 s (N=500) y ≈ 1,7–1,8 s (N=2000).

Lo que se ve y lo que no:
- `changed` no crece con los archivos cambiados (0 o 1 con marca da lo mismo dentro del ruido), pero SÍ crece con N (0,9 s a 2,3 s): abrir el store y listar los documentos del store (`tdocs/documents`, `generic-documents`, para saber cuáles tienen marcas) cuesta por documento. No lo medí por separado; es una inferencia, no una medida.
- Estos 12 s de sense con 2000 documentos no coinciden con los ≈38 s de `runs/subagents/2026-11-15-MED1/`: otro proyecto sintético, otra carga; no los reconcilio.
- Máquina: otros ejecutores usaban la CPU. Carga (uptime) 1,5–1,6 al empezar el N=500 y 2,6 al acabar; 3,1 al empezar el N=2000. No estaba en reposo; las medidas entre sense y changed se intercalaron repetición a repetición, así que comparten carga.
- Solo se midió el caso «un archivo, con marca de sección»; no se midieron muchos archivos cambiados ni marcas de otros tipos.
