# Medición (carga de la máquina: load average 90-108 durante las corridas, ruido alto)

Stores temporales en /tmp/kfind: `p` = 200 documentos genéricos pequeños (3 secciones c/u), `t` = 200 tipados (modelo Note, 2 campos).
Guion: /tmp/kfind/bench.clj. "antes" = `git archive HEAD` del main del worktree sin parche; "después" = el worktree con parche.
Tres repeticiones en ms (1ª fría). `rebuild` = `cache/rebuild!`; `find-sin-caché` = `find uniqueword7` sin `index/` (construye el índice en memoria);
`find` y `find-todo` con el índice en caché recién escrito.

| store | caso | antes | después |
|---|---|---|---|
| genéricos (p) | rebuild-indexes | 185 / 3.0 / 3.0 | 856 / 346 / 345 |
| genéricos (p) | find sin caché | 61 / 47 / 52 (0 resultados) | 533 / 575 / 651 (11 resultados) |
| genéricos (p) | find término (caché) | 1.5 / 0.9 / 0.9 | 27.5 / 16.3 / 15.0 |
| genéricos (p) | find "" (caché) | 0.8 / 0.8 / 0.8 | 14.8 / 18.9 / 14.6 |
| tipados (t), 2ª tanda | rebuild-indexes | 426 / 158 / 168 | 378 / 213 / 231 |
| tipados (t), 2ª tanda | find sin caché | 332 / 351 / 320 | 310 / 297 / 294 |
| tipados (t), 2ª tanda | find término (caché) | 78 / 67 / 68 | 71 / 74 / 85 |
| tipados (t), 2ª tanda | find "" (caché) | 74 / 101 / 71 | 80 / 177 / 93 |

Mínimo / mediana tipados: rebuild 158/168 -> 213/231 (ruido: 1ª tanda dio 183/193 -> 275/299 y 192/207 antes; hay outliers de 37 s y 79 s en la 1ª tanda de ambos lados por la carga),
find sin caché 320/332 -> 294/297, find término 67/71 -> 71/74.
Lectura: en tipados no hay diferencia fuera del ruido (el trabajo añadido es una lista vacía de `generic-documents`).
En genéricos el costo existe porque ahora se leen: ~1.7 ms/documento al reconstruir y construir en memoria (lee el árbol por la caché de árboles); con el índice en disco `find` cuesta 15-27 ms vs 1 ms antes (antes no veía nada).
Crudo: /tmp/kfind/before.txt y after.txt.
