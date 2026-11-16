# Medición: `stores sense` y `stores health` por git frente a `--full`

Proyecto sintético en /tmp (script desechable, ya borrado): N documentos `docs/d-NNNN.md` de un modelo `TicketDoc` de dos campos, registrados con UN `include build` (N=500: 4,5 s entre generar y construir; N=2000: 15,9 s de build), repo git con un commit, puntero en HEAD (`stores seen`). Casos: «nada» (nada cambió desde el puntero) y «uno» (`d-0002.md` editado, sin commitear; el `:scope` confirmó `changed 1, opened 1`; en «nada», `opened 0`). Cada medida es el proceso completo `bb -m kimun.cli.main …` (arranque incluido) bajo `flock` y `memcap 3`; tres repeticiones, segundos de reloj, mínimo / mediana. Carga de la máquina (loadavg 1 min) al empezar cada caso: 1,3 a 2,2 (no era una máquina en reposo; otro ejecutor trabajaba). Las repeticiones de un comando son consecutivas, no intercaladas entre comandos.

| N | caso | `stores show` | `docs list` | `sense` git | `sense --full` | `health` git | `health --full` |
|---|---|---|---|---|---|---|---|
| 500 | nada | 0,31 / 0,32 | 0,61 / 0,64 | 1,14 / 1,16 | 1,92 / 1,94 | 1,12 / 1,15 | 1,95 / 1,99 |
| 500 | 1 cambiado | 0,31 / 0,32 | 0,61 / 0,61 | 1,12 / 1,13 | 1,94 / 1,95 | 1,14 / 1,14 | 1,95 / 1,97 |
| 2000 | nada | 0,32 / 0,32 | 1,55 / 1,56 | 3,50 / 3,56 | 6,95 / 7,08 | 3,54 / 3,62 | 6,75 / 6,89 |
| 2000 | 1 cambiado | 0,32 / 0,33 | 1,57 / 1,58 | 3,49 / 3,65 | 6,74 / 6,96 | 3,45 / 3,47 | 6,60 / 6,66 |

(Una primera pasada de N=500 con la máquina más cargada dio sense git 1,12–1,27 y full 1,90–1,98: misma conclusión.)

Descomposición (medianas, restando el arranque de `stores show`, 0,32 s: arranca bb, carga la CLI y lee el descriptor sin abrir árboles):
- Abrir el store y listar documentos (`docs list`, que no lee archivos de documentos): ≈ 0,3 s (N=500) y ≈ 1,25 s (N=2000).
- Diagnóstico por git, nada cambiado: `sense` git − arranque = 0,84 s (N=500) y 3,24 s (N=2000); de eso, abrir+listar es ≈ 0,3 / 1,25 s y el resto (≈ 0,5 / 2,0 s) no lo separé: es la verificación de hashes de los objetos del store (`store/verify`, que se hace igual en ambos caminos), las comprobaciones de contrato de cada documento, las señales y llamar a git. Es una resta, no una medida directa de cada parte.
- Diagnóstico completo: `sense --full` − arranque = 1,62 s (N=500) y 6,76 s (N=2000). Lo que el camino por git ahorra (full − git): ≈ 0,8 s (N=500, 41 %) y ≈ 3,5 s (N=2000, 50 %) del tiempo de pared del proceso. Es el coste de abrir, hashear y clasificar los archivos que no cambiaron.
- Un archivo cambiado no se nota frente a ninguno dentro del ruido (±0,1 s en N=500, ±0,15 s en N=2000).
- El camino por git sigue creciendo con N (1,15 s → 3,56 s de 500 a 2000): abrir el store, la verificación de objetos y el contrato por documento siguen siendo O(N). Lo que desaparece es leer y hashear cada archivo, no el recorrido del store.
