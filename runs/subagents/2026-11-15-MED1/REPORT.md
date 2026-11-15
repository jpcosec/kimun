# REPORT MED1 — coste del árbol de rutas (K2a) y del latido

Estado: **medición parcial, recortada por contención del candado de bb** (varios ejecutores y `bb test` del
coordinador compartiendo `/tmp/kimun-bb.lock`; la generación de un store de N=2000 tarda 9–13 min bajo candado).
`src/` y `test/` sin tocar; no se commiteó. Stores sintéticos en `/tmp/med1-s/`, borrados.

## Archivos
- Modificado: `scripts/bench/gen_store.clj` (argumentos opcionales `layout` = flat|dirs20|nested y `esquema` = 1|2; reabre el store cada 400 documentos; compatible con el uso anterior).
- Nuevos: `scripts/bench/route_cost.sh` (una medición), `scripts/bench/route_matrix.sh` (matriz; `ONLY=regex`).
- Evidencia: `runs/subagents/2026-11-15-MED1/{measurement.md,REPORT.md,raw/}` (tablas completas, comandos para reproducir, crudos).

## Qué se midió y qué no
- Medido, 3 repeticiones (min y mediana en `measurement.md`): N=500 × {plano, 20 dirs, anidado} × {esquema 1, 2} (6 de 6); N=2000 × plano × {esquema 1, 2} (2 de 6).
- **No medido**: N=2000 con 20 dirs y anidado (4 configuraciones), y N=10000 entero. Motivos: N=2000 tardó > 3 min por configuración (generar 549 s y 765 s, más ≈ 4–5 min de medición), y el coordinador pidió parar; N=10000 además no cabe en disco (/tmp tenía 3–4 GB libres y 2000 plano ya ocupa 0,9–1,7 GB).
- Distribución de rutas: sintética (no la del desk real): `flat` todo en la raíz; `dirs20` 20 directorios round-robin; `nested` tres niveles de 10 directorios (1000 hojas). Documentos `BenchNote` de ≈ 2 KB. Máquina cargada: carga media ≈ 4 durante todo el trabajo (otras sesiones); el candado serializa `bb` pero no el resto de la CPU. Un tiempo (500, 20 dirs, esquema 1, latido caliente: 15,5 / 28,4 / 31,4 s) es claramente ruido de carga.

## Resultados (resumen; tabla completa en `measurement.md`)
Bytes del nodo de ruta que reescribe un `docs update`:
- N=500: plano 196 KB (esquema 2) / 203 KB (esquema 1); 20 dirs esquema 2: raíz 3,6 KB + nodo de directorio 9,8 KB (13,4 KB; esquema 1 209 KB); anidado esquema 2: 4 nodos, 4,9 KB en total, raíz 1,8 KB (esquema 1 212 KB).
- N=2000 plano: 784 KB (esquema 2) / 810 KB (esquema 1). El nodo plano crece exactamente ×4,00 con N ×4 (≈ 392 B por ruta en esquema 2, 405 en esquema 1): el esquema 2 plano **cuesta lo mismo que el 1**, como declara `10 §11.2`.
- Un `docs update` a N=500 plano escribe ≈ 214 KB de los cuales 91 % es el nodo de ruta; el log crece 151 B; 18–31 objetos nuevos (1 a 4 son nodos de ruta).

Tiempo de `docs update` (mediana; incluye ≈ 0,7 s fijos de arranque de bb): N=500: 1,08 / 1,40 s plano (s1 / s2), 1,34 / 1,03 s 20 dirs, 1,41 / 0,72 s anidado. N=2000 plano: 2,73 / 2,30 s. El anidado esquema 2 es el suelo (0,71 s).

Latido `stores sense --once`: N=500 ≈ 9–15 s; N=2000 plano 37,7–40,2 s (esquema 1 y 2 iguales). **Frío (sin `cache/`) y caliente no se distinguen**, y esquema, forma y N=500 20 dirs/anidado cambian poco: el latido crece ≈ ×4 con N ×4 y no depende de cómo se guardan las rutas (lo medido apunta a coste por documento, no a las rutas; no se perfiló la causa).

## Crecimiento
Solo hay dos tamaños completos (500 y 2000, y solo plano): no hay ley. Observado: bytes del nodo plano ×4,00; latido ×3,6–4,2; `docs update` ×1,6 (s2) y ×2,5 (s1) (con suelo fijo). Con dos puntos no se distingue lineal de otra forma salvo para los bytes del nodo, que son proporcionales al número de hijos directos por construcción.

## Conclusión prudente
Medido: (1) en un directorio plano, cada `docs update` reescribe un nodo de ≈ 0,4 KB por ruta (0,8 MB a 2000 rutas) y eso es ≈ 90 % de los bytes escritos; (2) repartir las rutas en directorios baja ese nodo en dos órdenes de magnitud a N=500 (13 KB con 20 dirs; 4,9 KB anidado); (3) el tiempo absoluto de un `docs update` a 2000 rutas planas fue 2,3 s, con ≈ 0,7 s de arranque, es decir, no es prohibitivo; (4) el latido costó ≈ 38 s a 2000 documentos y no depende del esquema.
Inferido (no medido): que un directorio con ≈ 10⁴ hijos directos escribiría ≈ 4 MB por cambio (extrapolación lineal de dos puntos); que el fan-out no hace falta mientras los directorios reales tengan cientos de hijos; que el sobrecoste dominante del latido son los documentos y no las rutas. Para estos tamaños (≤ 2000 rutas, directorios ≤ 2000 hijos) el fan-out dentro de un directorio **no parece necesario por tiempo** (segundos), pero sí por bytes escritos si se espera historia larga de actualizaciones en un directorio plano (0,8 MB por edición): no se midió ni espacio acumulado ni N=10000 ni un store real, así que no se descarta.

## Texto propuesto (no se tocó `docs/v2/*`)
- `12 §1`, fila `K2a`, en «Pendiente»: «medido (MED1) sobre stores sintéticos: un directorio plano reescribe ≈ 0,4 KB por ruta en cada escritura (0,8 MB a 2000 rutas, ≈ 2,3 s por `docs update`), repartido en 20 directorios 13 KB a 500 rutas; el fan-out dentro de un directorio no hizo falta a estos tamaños, y sigue sin medirse N=10000 ni un store real».
- `10 §11.2`, tras el límite declarado: «Medición (2026-11-15, stores sintéticos, `runs/subagents/2026-11-15-MED1/`): el nodo plano pesa ≈ 392 B por ruta (esquema 2) y 405 B (esquema 1), proporcional al número de hijos directos; a 2000 rutas en un directorio, una escritura reescribe 784 KB. Con rutas repartidas (20 directorios, o tres niveles) los nodos del camino suman 13 KB y 5 KB a 500 rutas».

## Ambigüedades y desvíos
- «Frío» se definió como `cache/` apartado; no se pudo vaciar la caché de disco del SO. No hubo diferencia observable (el sensor quizá no usa `cache/`; no se verificó en el código).
- El plan decía regresión simple: con dos puntos no se hizo; se reportan cocientes.
- N=500 y 2000-plano-s1 se midieron con un candado por medición entera (≈ 4–5 min, más que lo que luego pidió el coordinador); `2000-plano-s2` con un candado por llamada y reloj dentro del candado. Los tiempos no incluyen espera de candado.
- Una pasada de generación a 2000 se perdió (`StackOverflowError`, `raw/failed/`) y otra se mató al ajustar el plan; el generador ya reabre el store cada 400 documentos.
- `/tmp` quedó con < 5 GB libres durante el trabajo; stores borrados.
