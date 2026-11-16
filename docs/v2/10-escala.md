# kimun (SLDB v2) — Escala y formatos del store (2026-10-09)

> Por qué y cómo cambió la forma en disco de las revisiones (formato 2), qué cuesta abrir,
> leer y escribir un store, qué comprueba cada verificación y cómo se pasa un store de
> formato 1 al 2; qué cambia el **formato 3** (§10, docs/v2/14 §10) y qué cambia el
**formato 4** (§11, docs/v2/14 §18–§19: registro por documento y árbol como caché). Este documento es el
> **único dueño del formato en disco**; `02` fija el
> contrato de la revisión y enlaza aquí para la forma en disco. Autoridad: `02` (qué
> construye) > este documento. Números en
> `runs/bench/baseline-2026-10-06.txt` (antes) y `runs/bench/after-2026-10-06.txt` (después)
> para el formato 2, en `runs/bench/format3-store-size.txt` (S1) y `runs/research/2026-10-08/S1b-report.md`
> para el 3, y en `runs/subagents/S1c-2026-10-09/store-size-*.txt` para el 4.
>
> **Unidades:** 1 MB = 1.024.000 B en las tablas de tamaño del store (la convención de
> `bb store-size`, que imprime KB de 1024); las cifras de `du -sb` se citan en bytes.

## 1. Antes

El store real de sldb v1 migrado a kimun (1.551 documentos, 1.586 transacciones) con el
kernel de formato 1, antes del trabajo de escala:

| medida | valor |
|---|---|
| `docs show` de un documento | 187,9 s |
| `docs list` | 180,3 s |
| `stores check` / `stores verify` | 173,2 s / 228,4 s |
| `.kimun/` (aparente, `du -sb`) | 606 MB, 36.718 objetos |
| migración desde v1 (pasada estricta + `--force`) | 318 s + 204 s |
| sintético, ms por commit | 33 (100 docs) → 56 (200) → 95 (400), lineal |

Un paliativo, el snapshot EDN de apertura (`.kimun/index/open-snapshot.edn`, 721 MB), lo
bajaba a ~25 s: 7x, no la solución. Se retiró (commit `c53a477`).

## 2. Perfil

- **`open` replayaba el log entero**: 205,7 s de los ~200 s de cualquier comando. Leer el
  log costaba 0,5 s y leer los 36.718 objetos 3,1 s: el tiempo estaba en volver a aplicar
  las 1.586 transacciones.
- **Cada revisión guardaba el estado completo**: `:roots {tree-id merkle-root}` de todos los
  árboles vivos, el objeto *tree-set* (un par por árbol) y el objeto *edge-set* (todas las
  aristas activas). Bytes y tiempo por commit eran O(árboles + aristas), y la historia
  O(revisiones × (árboles + aristas)): de ahí los 606 MB.
- `commit!` listaba todas las claves de objetos para saber cuáles escribir, y el valor en
  memoria retenía todos los objetos de toda la historia.
- Medido después, ya en formato 2: los lectores de la superficie que pedían vistas enteras
  (`(:trees store)` cargaba cada árbol con todas sus posiciones para leer su nombre;
  `docs list` leía 16.491 objetos en un store de 800 documentos, 9.190 de ellos objetos
  de árbol); `heads.edn` con una entrada por árbol, reescrito entero en cada commit
  (314 KB y 11,3 ms de `pr-str` a 3.200 documentos, casi todo el crecimiento del costo por
  commit que quedaba); y el nodo modelo, destino de una arista por documento, cuya
  entrada de incidencia (~1.500 ids en una hoja) se reescribía en cada commit (108 KB por
  commit a 800 documentos).

## 3. Diseño

1. **Mapas Merkle independientes de la historia** (`kimun.kernel.mmap`). Un hash trie
   persistente cuyos nodos son objetos EDN del CAS como cualquier otro. Hoja
   `{:mmap :leaf :entries [[k v] …]}` (ordenadas por camino, hasta `leaf-capacity` = 16);
   rama `{:mmap :branch :count n :children {slice id}}` (fan-out 16: un carácter hex del
   camino por nivel). El camino de una clave que ya es un id hex de 64 caracteres es ella
   misma; el de cualquier otra (ULID de árbol) es `canon/digest` de la clave. **La forma
   es función solo del contenido**: un subárbol con ≤ 16 entradas es una hoja, con más es
   una rama, y quitar colapsa. Por eso la raíz de un mismo conjunto es la misma sea cual
   sea el orden de inserción y borrado (propiedad probada en `mmap_test`). Un cambio de
   k claves reescribe O(k · log₁₆ n) nodos, cada uno bajo un bloque de 4 KB.
2. **Revisión de formato 2**: las tres colecciones por revisión pasan a ser raíces de
   mmaps: `:trees` (tree-id → `[descriptor-id merkle-root]`), `:edges` (edge-id → true,
   las aristas activas que no son de propiedad) e `:incidence` (node-id → las aristas
   activas que lo tienen como `:from` o `:to`; un vector ordenado hasta 16 ids, y
   `{:set <raíz de un mmap edge-id → true>}` por encima, de modo que un nodo concentrador
   cuesta unos pocos nodos por arista y no su conjunto entero). Desaparece `:roots`. Siguen
   siendo ocho campos y `revision-id = H(los ocho)`. `:incidence` es derivada de `:edges`
   y determinista; hace que el re-anclaje de §6.1 cueste las aristas que tocan el nodo
   reemplazado, no un recorrido de todas.
3. **`apply-plan` proporcional al plan**: actualiza los mmaps solo en las claves que la
   transacción tocó y devuelve explícitamente `:new-objects`, lo único que `commit!`
   escribe. Diff, `tree-at` y la detección de conflictos descienden los mmaps solo donde
   difieren.
4. **Cabezas por árbol en el CAS**: `heads.edn` es `{:head rev-id :heads-root id}`, y la
   raíz nombra un mmap tree-id → rev-id. Tamaño fijo (154 bytes); el commit escribe unos
   pocos nodos del mmap en vez de reescribir un mapa de todos los árboles.
5. **Valor de store perezoso**. El valor sigue teniendo las claves que lee la superficie
   (`:objects :trees :edges :revisions :heads :head …`), pero un host con el puerto
   opcional `LazyMaps` (`kimun.host.lazy`, un `proxy` de `APersistentMap` en Babashka) las
   calcula al usarlas y nunca cuando nadie pregunta; sin el puerto el kernel cae a mapas
   ansiosos (los hosts de prueba puros siguen funcionando). El adaptador de archivos
   guarda cada objeto ya parseado (puerto opcional `ObjectCache`, sin límite, por
   instancia de backend: un proceso de la CLI abre una vez y muere). Un hash nunca se
   comprueba contra la caché: `verify` y `open` hashean los bytes del disco.
6. **Accesos baratos del kernel** para que la superficie no fuerce vistas enteras:
   `edge-active?`, `edges-touching`, `tree-ids`, `tree-entry`, `tree-descriptor`,
   `tree-descriptors` (descriptores sin objetos de árbol), `trees-named` (índice
   nombre → árboles, construido una vez por linaje de vistas y heredado por `apply-plan`
   en O(plan)).

## 4. Alternativas descartadas

| alternativa | por qué no |
|---|---|
| snapshot EDN del store abierto | 721 MB, 34,6 s en escribirse y 33,9 s en leerse: 7x, sigue O(store) por comando |
| conservar los conjuntos completos por revisión y comprimirlos o guardar deltas | la identidad del conjunto dependería de la historia (los deltas) o seguiría costando O(n) por commit para hashearlo; el trie da raíz canónica y costo O(log n) |
| fan-out 256 | cada commit reescribe una rama raíz de ~18 KB por mapa (medido en `mmap-measure`) |
| `leaf-capacity` 8, 32 o 64 | con fan-out 16, 16 escribe menos bytes canónicos por commit, no más ficheros que 32, y todo nodo cabe en un bloque de 4 KB |
| incidencia siempre como vector | la entrada del nodo modelo crecía con el store (50,7 KB por commit a 200 documentos frente a 40,0 KB con el conjunto anidado) |
| `heads.edn` como mapa de todos los árboles | 314 KB reescritos por commit a 3.200 árboles, ~12 ms; ~25 % del commit |
| store en SQLite | cambia el backend sin cambiar el problema (revisiones con estado completo); queda abierto como backend, no como solución de escala |
| caché de objetos acotada | `stores check` y `stores verify` recorren todo lo alcanzable, y el mapa perezoso de `:objects` ya memoriza los mismos valores: acotar solo añadiría relecturas |
| índice persistente nombre → árboles en la revisión | no hecho: el índice se construye en memoria leyendo un descriptor por árbol (1.552 en el store real, ~0,1 s); sería otro cambio de formato para ahorrar eso |
| escritor perdedor: dejar su línea en el log y que `open` la ignore | toda lectura del log tendría que saltar líneas ajenas; se eligió que el log y las cabezas cambien juntos bajo un lock (§7) |

## 5. Formato 2 en disco

```
<dir>/store.edn      {:format-version 2 :hash-alg :sha-256 :capabilities {...} ...}
<dir>/objects/<id>   texto EDN canónico de la forma hasheada del objeto; H(fichero) == id
<dir>/log.edn        una Transaction {:id :plan :revision} por línea, solo se agrega
<dir>/heads.edn      {:head rev-id :heads-root mmap-root-id}  (antes del primer commit: ausente)
<dir>/lock           vacío; el lock exclusivo de commit-tx! se toma sobre él

Revision (formato 2) {:trees     <raíz mmap tree-id -> [descriptor-id merkle-root]>
                      :edges     <raíz mmap edge-id -> true>
                      :incidence <raíz mmap node-id -> [edge-id …] | {:set <raíz mmap edge-id -> true>}>
                      :parents [rev-id] :tx tx-id :actor :engines :timestamp}
nodo mmap            {:mmap :leaf :entries [[k v] …]} | {:mmap :branch :count n :children {slice id}}
```

Los ids de nodos, aristas, objetos de árbol y descriptores no cambian con el formato
(`bb oracle`). Cambian los ids de revisión (sus campos) y de transacción (el plan
resuelto incluye `:base`, un id de revisión). Un store de formato 1 (`:format-version 1`,
o sin la clave) sigue abriendo y escribiendo con el motor conservado en
`kimun.kernel.legacy.revision` (abre por replay; `heads.edn` = `{:head :heads}`).

Objetos sin referencia que pueden quedar en `objects/` (todos de escritura única e
inofensivos): los de un escritor que perdió la carrera (§7) y los nodos del mmap de
cabezas que un commit posterior reemplazó (nada guarda una raíz vieja de cabezas).
Los informa `stores unreachable` (`§11.3`, solo lectura); recogerlos es el GC del hito 8, que no existe todavía. `stores check` cuenta
en `:objects` todo lo que hay en `objects/`, ellos incluidos.

## 6. Qué confía `open` y qué comprueba cada verificación

`open` (formato 2, host con `LazyMaps`) **no replaya ni lee el log**. Comprueba: que
`store.edn` existe y su `:hash-alg` es el del host; que `heads.edn` tiene la forma
`{:head id :heads-root id}` (`:store/corrupt-heads`); que el objeto `:head` existe, hashea
a su id y es una Revision, y que `:heads-root` existe, hashea a su id y es un nodo mmap
(`:store/corrupt-head`, `:reason :missing | :hash-mismatch | :not-a-revision |
:not-an-mmap-node`). Lee exactamente esos dos objetos, sean cuantos sean los árboles y
las revisiones. **Confía** en que `heads.edn` nombra las cabezas a las que lleva el log
(es el punto de commit), en todo otro objeto (se lee bajo demanda sin rehashear), en las
cabezas por árbol bajo `:heads-root` y en `:incidence` y demás estructuras derivadas
dentro de las Revisiones. Una cola no confirmada del log no se busca.

| comando | función | detecta |
|---|---|---|
| cualquier comando | `store/open` | `store.edn`/hash-alg, forma de `heads.edn`, Revision cabeza y raíz de cabezas ausentes, alteradas o de otra forma |
| `stores check` | `open` + `store/verify` | además: el hash de **todo objeto alcanzable** desde las cabezas (nodos, aristas, objetos de árbol, descriptores, nodos mmap, Revisiones, cabezas de cada árbol) y el **final** del log frente a las cabezas (`:log-heads-mismatch`). No una línea intermedia alterada o cortada. Fallo `store/invalid`, exit 5 |
| `stores verify` | `store/verify-full` | replay completo del log por `apply-plan` (cada id de transacción y revisión, `:incidence` y las cabezas recalculadas, las cabezas finales contra `heads.edn`) y luego `verify` sobre el store replayado. Cualquier línea alterada, cortada o faltante y cabezas que no coinciden con el log. Fallo `store/corrupt`, exit 5, `:bad [{:reason :replay-failed}]` |
| `journal verify` | `replay-open` + DAG de revisiones + `verify` | lo de `stores verify` más el DAG |

Unas cabezas exactamente una transacción por detrás del log son indistinguibles de un
escritor muerto (§7): se informan como `:uncommitted-tail`, no como error.

## 7. Escritor perdedor y ventana de caída

`commit!` escribe primero los objetos nuevos (escritura única: un fichero existente no
se reescribe) y después llama a la única operación de mutación del puerto `Backend`,
`commit-tx! [b expected tx new]`. El adaptador de archivos la ejecuta bajo un lock
exclusivo (`FileChannel.lock` sobre `<dir>/lock` entre procesos, más un monitor de la JVM
entre hilos del mismo proceso, que el lock de fichero solo rechazaría): compara las
cabezas actuales con las que el escritor cargó; si difieren no escribe nada y devuelve
false (`:store/stale`, exit 4); si coinciden repara la cola del log (ver abajo), agrega la
línea y reemplaza `heads.edn` (fichero temporal + `ATOMIC_MOVE`). **El log y las cabezas
cambian juntos**: el perdedor no deja ninguna línea, el store sigue abriendo y pasando la
verificación completa, y un reintento sobre un store recién abierto funciona. Sus objetos
quedan sin referencia (§5). El lock se mantiene milisegundos: lo que dura comparar,
agregar una línea y reemplazar un fichero de 154 bytes. Si **otro proceso** tiene el lock
en ese momento, el commit falla enseguida con `store/busy` (exit 4, sin escribir nada más
que objetos de escritura única) o, con la opción global `--wait` de la CLI
(`fs-store/backend` con `{:wait? true}`), espera a que se libere y sigue (el contrato de
v1: fallar rápido o esperar). Los hilos de un mismo proceso siempre esperan (monitor).
Pruebas: `test/kimun/parity/closed_gaps_test.cljc` (la carrera determinista, cuatro
escritores concurrentes, otro proceso con el lock sin y con `--wait`).

**Ventana de caída**: entre agregar la línea al log y reemplazar `heads.edn`. Un proceso
que muere ahí deja una última línea (entera o cortada) que las cabezas no registran. La
regla pura `store/log-tail` la clasifica: `:clean` (el log termina en la cabeza),
`:uncommitted` (la última línea no está registrada pero la anterior es la cabeza, o es
la única y no hay cabezas: nunca fue un commit) o `:inconsistent` (cualquier otra cosa).
`open` y `replay-open` la ignoran (abren en las cabezas registradas); `stores verify`,
`stores check` y `journal verify` la informan como `:uncommitted-tail` sin fallar; el
siguiente commit la descarta (trunca) bajo el lock antes de agregar. Un log
`:inconsistent` hace que el siguiente commit falle con `store/error` sin escribir nada.
No hay `fsync`: está cubierta la muerte del proceso, no un corte de energía.

## 8. Ruta de upgrade

`kimun stores upgrade` (sobre `kimun.kernel.upgrade/upgrade!`, que lee un backend y escribe
otro vacío):

1. Construye el store nuevo en el directorio hermano `<store>.upgrade-<stamp>`, con el
   descriptor del original (todas sus claves) en `:format-version 2`.
2. Vuelve a confirmar, en orden de log y con el motor de formato 2, cada transacción
   confirmada del original; el plan resuelto queda igual salvo `:base`, reescrito al id
   nuevo de la misma revisión. Actor, timestamp, engines y ops son los del plan; la cadena
   de padres sigue el orden del log; nodos, aristas y árboles salen con los mismos ids.
3. Comprueba que ninguna transacción se rebasó y que cada plan quedó igual; que las
   cabezas nuevas (la del store y la de cada árbol) son las originales mapeadas; que los
   árboles (descriptor y raíz Merkle) y las aristas activas de la cabeza coinciden con los
   de la Revision cabeza original; y `verify-full` del store nuevo.
4. Solo entonces renombra el original a `<store>.format-1-<stamp>` (respaldo que el
   usuario puede borrar) y pone el nuevo en su lugar. Si `heads.edn` del original cambió
   mientras tanto, abandona con `store/stale` (exit 4) y borra el directorio de trabajo.
5. Escribe `upgrade-format-1.edn` en el store nuevo (ids viejos → nuevos de revisiones y
   transacciones). Copia los demás ficheros del directorio salvo `index/`, derivado y
   indexado por ids de revisión que ya no existen (`rebuild-indexes` lo regenera).

Una cola no confirmada del log original no se lleva (nunca fue un commit) y se informa en
`:dropped-tail`. Sobre un store de formato 2 el comando no hace nada (`:upgraded false`).
Pruebas: `test/kimun/kernel/upgrade_test.cljc` (historia con todas las ops, propiedad
sobre historias aleatorias, rechazos) y `test/kimun/cli/upgrade_test.cljc` (`docs list`,
`docs show`, `fields get`, `journal show` y `stores verify` responden igual antes y
después, leyendo los ids por el mapa).

## 9. Después

Store real (copia de 1.551 documentos, mejorada con `stores upgrade` en 77,6 s):

| medida | antes | después | objetivo |
|---|---|---|---|
| `docs show` | 187,9 s | 0,37 s | ≤ 2 s |
| `docs list` | 180,3 s | 0,77 s | ≤ 3 s |
| `fields set` | (open solo: 205,7 s) | 0,38 s | ≤ 3 s |
| `stores check` / `stores verify` | 173,2 s / 228,4 s | 18,7 s / 32,0 s | — |
| `.kimun/` (aparente, `du -sb`) | 606 MB, 36.718 objetos | 97,7 MB, 82.755 objetos | ≤ 150 MB (aparente) |
| `.kimun/` (disco real, bloques) | — | 364 MB | 150 MB: **no cumplida** |
| migración desde v1 (estricta + `--force`) | 522 s | 71 s | ≤ 5 min |

El store formato 2 ocupa 364 MB **en disco real** (no aparentes) porque 80.435 de sus 82.787
objetos pesan menos de 4 KB y cada uno ocupa un bloque: la meta de 150 MB no se cumple en disco.
El arreglo es empaquetar los objetos pequeños (packfiles al estilo git, `12 §1`).

`docs show` lee 1.923 de 82.755 objetos (2,3 %): 1.552 descriptores (el índice de
nombres), 276 nodos mmap, 53 objetos de árbol, 38 nodos y 4 aristas.

Escala (sintético, un documento por commit):

| revisiones | open | commit | bytes por commit |
|---|---|---|---|
| 201 | 0,3 ms | 42,0 ms | 48,6 KB |
| 801 | 0,2 ms | 36,4 ms | 58,3 KB |
| 1.601 | 0,3 ms | 38,6 ms | 58,8 KB |
| 3.201 | 0,3 ms | 38,4 ms | 62,4 KB |

Abrir no depende de la historia ni del número de árboles; el commit tampoco (la
pequeña subida de bytes por commit es la profundidad log₁₆ de los mapas). Lo que sigue
siendo O(store) por naturaleza: `find`, `stores check` (rehashea todo lo alcanzable) y
`stores verify` (replaya el log). Lo que sigue siendo O(árboles) sin serlo por
naturaleza: el índice de nombres de `docs show`/`fields set` (§4).

## 10. Formato 3 (docs/v2/14 §10)

El formato 3 **no cambia la estructura de la revisión** (los ocho campos de §5, los mmaps
`:trees`/`:edges`/`:incidence`, y los ids de nodo, arista, árbol y descriptor). Los ids de
transacción y revisión **sí cambian** para el mismo plan de entrada, porque hashean el plan
resuelto, cuya forma fija el formato (S1b: referencia los nodos por id). Cambia qué se escribe
y de qué vive un documento.

```
<dir>/store.edn      {:format-version 3 … :content :inline}   ; :content ausente = by-reference
<dir>/objects/<id>   igual que el formato 2
<dir>/log.edn        una Transaction {:id tx-id :revision rev-id} por línea
<dir>/heads.edn      igual que el formato 2
```

Qué cambia:

1. **Sin copia verbatim del texto.** El signo `markdown/source` y su arista
   `derived root→source` desaparecen del plan por defecto. El `:contract` (id del
   descriptor con que el documento se extrajo, `05` decisión 40) vive en la evidencia de la
   arista `reference root→external` y, para documentos sin archivo, en la `reference
   root→model`. La evidencia del anchor registra además `:content-hash` y `:field-hashes`
   del payload escrito, que es como `diagnostics` distingue un reformateo de una edición sin
   guardar el texto (con el archivo editado, el árbol da el texto rastreado).
2. **El texto se lee del archivo.** `docs` reconstruye `:source` así: el signo verbatim si
   lo hay (`:inline` o store formato 2), si no el archivo **mientras su fingerprint coincide
   con el registrado** (el archivo ES el texto rastreado), si no el render canónico del
   árbol (lo rastreado cuando el archivo se editó por fuera). El kernel no hace I/O: el
   host inyecta `:read-file` en el valor del store.
3. **`inline` por caso** (`:content :inline` en `store.edn`, `stores init --content inline`,
   `:store {:content :inline}` en `kimun.include.edn`, o `docs create|track --inline`):
   entonces sí se escribe el signo verbatim y su arista, y el documento no depende del
   archivo. Por defecto `:by-reference`.
4. **El plan no se guarda dos veces.** La línea del log es `{:id tx-id :revision rev-id}`;
   el plan resuelto sigue en el CAS bajo `tx-id` (invariante 17) y `replay-open`/`verify-full`
   lo leen de ahí. Las líneas de formato 2 (con `:plan`) se siguen leyendo. Además, el plan
   resuelto **referencia los nodos por id**: un `:add-node` con alias se resuelve a
   `{:op :add-node :id <node-id>}` y el contenido del nodo vive una sola vez, como objeto del
   CAS; `replay-open`/`verify-full` reconstruyen el nodo desde el CAS cuando falta `:node`. El
   plan de ENTRADA no cambia (sigue aceptando `:node` inline) y un plan resuelto almacenado sin
   `:as` (formato 2 o un formato 3 anterior) se lee igual y reproduce su propio `tx-id`. Del
   mismo plan de entrada cambian `tx-id` y `revision-id`; los ids de nodo, arista, árbol,
   descriptor y mmap no.
5. **Una revisión por `include build`** (§11): todos los modelos y documentos declarados
   componen UN `TransactionPlan` (`kimun.kernel.plan/compose-plans`) y un solo `commit!`.
   `stores update` también compone en **UNA revisión** los `update-plan` de todos los documentos
   que repara (el journal de esa revisión lleva `:documents`, con los campos que cambió cada uno).

Ruta de upgrade: `stores upgrade` sobre un store formato 2 lo convierte **en el sitio**
(reescribe las líneas del log sin `:plan` y el descriptor a `:format-version 3`, y verifica;
restaura si falla). Para un store `:git :ignore` el camino fuerte es `include build --rebuild`,
que reconstruye el store desde los archivos y descarta las copias verbatim.

Medido en este repo (desk de 433 KB, 539 documentos; antes = formato 2 del commit
`b4e02790`, después = formato 3 de S1b): aparente **28.349.458 B → 8.066.006 B** (~7,88 MB)
(`log.edn` 5.891.029 B → 151 B; índice mmap 12.338.663 B → 708.116 B; plan 5.676.855 B →
4.031.322 B, que ya no embebe el contenido de ningún nodo); en disco 119 MB → 55,2 MB;
`include build` 38 s → 13 s, con **1 revisión** en vez de 556. La corrida S1 (el primer
"después") midió plan 4.712.584 B y aparente 8.754.014 B en `runs/bench/format3-store-size.txt`;
las cifras de arriba son de S1b, base del formato 4. `bb store-size [DIR]` reproduce la tabla.

Lo que el formato 3 todavía **no** hace (ver `12` §1): el árbol de un documento con anchor
sigue persistiéndose entero (nodos `block`/`text`/`opaque` y tree objects), y el plan
resuelto de un build sigue en el CAS, así que el store no baja de los 433 KB del desk que
indexa. Convertir el árbol en caché verificable y no persistir el plan de un store
`:git :ignore` es el resto del §10 del 14 (decisión 27).

## 11. Formato 4 (docs/v2/14 §18–§19)

El formato 4 implementa el registro por documento de `14 §18.6` y la separación caché /
store estable de `14 §19`. No cambia la estructura de la revisión (los ocho campos de §5, los
mmaps `:trees`/`:edges`/`:incidence`, ids de nodo, arista, árbol y descriptor): cambia **qué
se persiste de un documento con archivo**. Los `tx-id`/`revision-id` del mismo plan de entrada
cambian otra vez (el plan resuelto tiene otra forma).

```
<dir>/store.edn          {:format-version 4 :hash-alg :sha-256 :capabilities {…} :name :links}
<dir>/objects/<id>       igual que el formato 2/3 (registro y estructura referenciada)
<dir>/cache/objects/<id> objetos derivados por hash (tree objects, nodos block/text/opaque)
<dir>/cache/docs/<key>.edn  índice por documento: {fingerprint, class, parser, root-hash, tree}
<dir>/log.edn            una Transaction {:id tx-id :revision rev-id} por línea
<dir>/heads.edn          igual que el formato 2
```

**Registro por documento.** Un documento trackeado con archivo se registra con:

- su **anchor externo** (`sign/external`: ruta + `:fingerprint`), como en el formato 3;
- su **clase**: el id del descriptor del modelo del que es instancia, o `generic/<ext>` para
  un documento sin modelo (`14 §18.1`); vive en `:class` del descriptor del árbol;
- el **hash raíz** de su estructura indexada (el `merkle-root` del árbol), que ya registra el
  mmap `:trees` de la revisión (`tree-id → [descriptor-id merkle-root]`);
  el descriptor puede llevar además `:units {"<campo>" "<id>"}`, las unidades de campo del
  documento tipado (K1c), y `:marks {"<ulid>" {:kind :locator :hash :label}}`, las unidades
  estructurales declaradas (K1e): **son store, no caché**, y **no entran** en `merkle-root`,
  `projection-hash` ni `content-hash`;
- el **hash de contenido** del payload extraído, `:content-hash` del descriptor del árbol.

La evidencia de las aristas deja de llevar `:field-hashes` (se recomputan del payload cuando
un lector los necesita) y, para estos documentos, tampoco `:content-hash`: viven en el
registro. El `:contract` (decisión 40) sigue en la evidencia de la arista `reference
root→external`, así que un documento sigue leyéndose bajo la clase con que se escribió aunque
el descriptor del modelo se suceda (`14 §18.3`).

**Nada de árbol para un documento con archivo.** El plan de `docs track`/`docs update`/
`include build` ya no emite `:ownership`, ni `:add-node` de bloques/texto, ni tree objects:
emite un `:register-tree` (op nuevo del kernel: descriptor + `merkle-root`, sin posiciones),
el nodo raíz del documento y el anchor externo, más las aristas de modelo/anchor. Los tree
objects y los nodos del documento se escriben en `cache/objects/`, no en `objects/`. Los
documentos sin archivo (`docs create` sin ruta) y los `:inline` (S1) **conservan su árbol
como hoy**: son la excepción en que el store ES la fuente del texto.

**Caché por hash.** `cache/objects/<id>` es content-addressed (el mismo id que tendría en el
CAS); `cache/docs/<key>.edn` es un índice con `key = sha256(fingerprint, clase, versión del
parser)`. El backend de ficheros lee `objects/` y cae al caché si falta, así que un lector no
cambia. **La lectura de caché se verifica contra su id**: si los bytes de
`cache/objects/<id>` no hashean a `<id>`, es un fallo de caché y `read-object` devuelve nil
(C1); la lectura **no** borra, renombra ni sobrescribe el objeto dañado. Un
`kimun.surface.docs/doc-tree` (el resolver de superficie) devuelve el árbol de un documento:
del store si está persistido, del caché si es derivado, y **recalculado del archivo** (y
reescrito al caché) si el caché no está. Un acierto de caché exige **todos** los objetos del
árbol —los tree objects y los nodos que nombran sus posiciones—, no solo la raíz: si falta o
no verifica cualquiera, es caché fría y el árbol se reconstruye del archivo (C2), y el árbol
reconstruido se entrega a los lectores de la misma operación. Comprobar que el árbol está
completo lee todos sus objetos en cada resolución, también cuando el lector iba a usar solo
una parte. Verifica
`merkle-root == hash raíz registrado`; si no coincide, el documento está desactualizado
(fingerprint != registrado). El caché es gitignored (`include gitignore` lo añade) y
borrable: todo se recalcula.

La matriz de comandos de lectura con caché fría o con un objeto corrupto es `test/kimun/cli/cold_cache_test.cljc`; sus brechas conocidas (`known-cold-cache-gaps`) son la deuda de esta promesa (evidencia: `runs/subagents/2026-11-15-cold-cache/REPORT.md`).

Coste medido (store de banco de 600 documentos, `docs render`/`docs show` de un documento,
3 corridas): 0,50–0,54 s antes y 0,50–0,52 s después del SHA-256 por acierto de caché; la
salida es idéntica. **Límite:** un objeto de caché dañado hace lento ese documento hasta que
alguien borre `cache/` o reconstruya el store — cada lectura lo rechaza y reconstruye en
memoria, y este corte no añade señal ni comando de reparación.

**Detección y reconciliación.** `fingerprint(archivo) != registrado` ⇒ `:edited`/`:reformatted`
según el contenido (`stores check|health`); archivo ausente ⇒ `:missing`; clase sucedida ⇒
`:contract-moved` (el documento sigue siendo instancia de su clase original y se lee bajo
ella). La acción de reparación sigue siendo `docs update NAME` / `stores update`, que
re-registran fingerprint, clase y hash raíz. Ningún cambio silencioso.

### 11.1 Contrato de parse registrado con digest (K6, primer corte)

El `:contract` solo decía la clase. Si la misma fuente se leía con otro parser, nada lo registraba
y un diagnóstico podía confundirlo con una edición externa (F08 de
`docs/reviews/wittgenstein-superset/reviews/02-findings.md`). Desde K6 la arista de anclaje lleva
además `:parse-contract`, el contrato completo con su digest (`kimun.surface.parse-contract`):

```clojure
{:contract/version 1
 :parser ["kimun.surface.markdown/native" "1"]   ; parser en vigor y store-cache/parser-version
 :grammar "builtin"                              ; o "<pod>/<versión> tree-sitter/<versión>"
 :normalization "nfc"
 :extractor ["kimun.surface.models" "1"]
 :class "<id del descriptor>"                    ; campos y plantilla: selector y clase
 :digest "sha-256:<hex>"}                        ; de los seis componentes, en orden fijo
```

- El parser registrado es el **efectivo** (`kimun.surface.markdown.ast/effective-parser`):
  `KIMUN_PARSER=treesitter` solo cuenta si el pod está disponible.
- Los componentes viajan con el digest para poder decir **cuál** cambió. Al leer, el digest se
  recalcula de los componentes; si no coincide, la evidencia no se usa.
- El entorno que lee (parser, gramática) se resuelve **una vez por diagnóstico**, no por documento.

Qué distingue la sensorización (`stores check|health|sense|heartbeat`):

| situación | categoría | señal | `content-ok` | `contract-ok` | reparación |
|---|---|---|---|---|---|
| cambió la fuente | `:edited` · `:reformatted` · `:missing` | `source/*` | false | según el contrato | como antes |
| cambió el contrato (parser, gramática, extractor o clase), fuente intacta | `:contract-moved` | `contract/changed` | true | false | segura: `docs update` / `stores update`; no reescribe el archivo si la clase no cambió |
| el parser registrado no se puede usar aquí, o el pod no responde | `:parser-unavailable` | `contract/parser-unavailable` | true | false | decide una persona |
| el digest no corresponde a sus componentes | `:contract-inconsistent` | `contract/inconsistent` | true | false | decide una persona |
| fuente y contrato iguales, pero reconstruir en memoria da una raíz (o hash de contenido) distinta | `:rebuild-mismatch` | `contract/rebuild-mismatch` | true | true | decide una persona (`docs update`) |

`:contract {:status :changed}` del finding (y del `:detail` de la señal) nombra los componentes que
difieren; `:parser` aparece solo si cambió el parser, nunca por un cambio de extractor o de clase.
Si cambian fuente y contrato a la vez, la categoría es la de la fuente y `contract-ok` es false.
El recálculo de observación para detectar `:rebuild-mismatch` (`kimun.surface.docs/rebuild-tree` y
`rebuilt-root`) corre **solo en memoria** y nunca escribe en `<store>/cache/`; `doc-tree` solo
repone la caché cuando la raíz recomputada coincide con la registrada (nunca sobrescribe caché ante
archivo desactualizado o `:rebuild-mismatch`), y `docs update` re-registra el árbol cuando detecta
divergencia de raíz o hash de contenido incluso si huella y contrato no cambiaron.

**Compatibilidad y migración.** No cambia la versión de formato: es una clave de evidencia adicional
(`02 §3.2`). Un documento escrito antes de K6 no tiene `:parse-contract`: se lee igual, su estado es
`:unrecorded` y **nunca** se informa como cambio de contrato por eso. Adquiere el contrato la próxima
vez que se escribe (`docs update` con cambios, `stores update`, `include build --rebuild`); no hay
migración forzada. Un kimun anterior ignora la clave.

**Fuera de este corte.**

- Gramática y perfil de documentos de código, y selector EDN sobre tree-sitter: dependen del spec 16
  (M1), que no está escrito como implementación.
- Descriptores de los modelos base: el descriptor los nombra, no los resuelve.
- Versión por componente del parser nativo: gramática y normalización son código y comparten
  `store-cache/parser-version`, que se sube a mano.

**Verificación.** `reachable`/`verify` no exige en el CAS los tree objects de un árbol
marcado `:cached?`: son derivados y su integridad la comprueba el resolver (raíz registrada
vs recomputada). El resto de objetos (nodos raíz, anchors, aristas, descriptores, mmaps,
revisiones) se verifica como siempre.

**Ruta de upgrade.** `include build --rebuild` produce el store formato 4 (reconstruye desde
los archivos; es el camino fuerte para un store `:git :ignore`). `stores upgrade` sobre un
store formato 2 lo pasa a 3 en el sitio; sobre un store formato 3 reporta `:needs-rebuild` y
nombra `include build --rebuild`, porque registrar como caché exige los archivos y no es una
reescritura en el sitio.

**Medido en este repo** (desk de 433 KB, 539 documentos; antes = formato 3 de S1b, después =
formato 4; `include build --rebuild` en ambos):

| medida | formato 3 | formato 4 |
|---|---:|---:|
| plan resuelto (1 objeto) | 4.031.322 B | 907.465 B |
| tree-object | 1.650.467 B (5.478) | 3.877 B (18) |
| sign/sign/block | 80.062 B (691) | 62.067 B (540) |
| sign/sign/text | 168.272 B (1.278) | — (0) |
| sign/sign/opaque (17) | 303.151 B (865) | 39.952 B (17) |
| descriptor | 94.859 B (540) | 185.950 B (540) |
| edge/reference | 701.496 B (1.078) | 331.485 B (1.078) |
| edge/derived | 157.927 B (539) | 157.927 B (539) |
| sign/sign/external | 171.995 B (539) | 171.995 B (539) |
| mmap (leaf+branch) | 708.116 B (1.486) | 708.426 B (1.508) |
| revision | 488 B (1) | 488 B (1) |
| **objetos** | **8.065.855 B (12.494)** | **2.569.632 B (4.781)** |
| **aparente** | **8.066.006 B (7,88 MB)** | **2.569.783 B (2,51 MB)** |
| **caché (derivado)** | — | 2.315.429 B (8.815 ficheros) |

El registro (anchor externo 172 KB + descriptor 186 KB + nodo raíz 62 KB + aristas 489 KB) y
el plan (907 KB) fijan el tamaño; el árbol ya no está en el store (tree-object 1,65 MB → 3,9 KB;
texto 168 KB → 0). `bb store-size [DIR]` reproduce la tabla y mide el caché aparte; detalle por
clase en `runs/research/2026-10-08/S1c-report.md §2` (medición `store-size-after.txt`).

### 11.2 El mapa de rutas: de mapa plano a árbol Merkle por ruta (K2a, corte C5)

El registro por ruta de lo trackeado (`kimun.kernel.route`, contrato de la superficie en
`06 §3` «Rutas de lo trackeado») tiene dos esquemas. El formato del store sigue siendo 4 y
`heads.edn` conserva sus claves (`:routes-root`, `:routes-object`); lo que cambia es el objeto
que `:routes-object` nombra y cómo se calcula `:routes-root`.

| | esquema 1 (`kimun.route/1`) | esquema 2 (`kimun.route/2`) |
|---|---|---|
| objeto | **un** mapa `ruta → entrada` con todas las rutas | un nodo por **directorio** (`kimun.kernel.route-tree`) |
| `:routes-root` | digest de la lista ordenada de entradas (5 campos) | hash de ruta del nodo raíz |
| `:routes-object` | digest del mapa completo, con `:tree-id` | id CAS del nodo raíz |
| escribir una ruta | reescribe el mapa entero | reescribe **enteros** los nodos de su camino: tantos nodos como segmentos, y un trabajo igual a la suma de los hijos directos de esos directorios |
| abrir | lee y rehashea el mapa entero | lee y comprueba **solo el nodo raíz** (sus hijos directos) |
| buscar una ruta | en memoria, tras leer todo | un nodo por segmento, cada uno leído y rehasheado entero |
| comparar dos raíces | diff de las dos listas completas | baja solo por donde los hashes difieren |
| caché `cache/routes/` | espejo derivado del mapa | no se usa: los nodos son objetos del CAS |

**Nodo del árbol.**

```clojure
{:route/version "kimun.route/2"
 :children {"segmento" {:entry {:fingerprint … :class … :projection-hash … :content-hash … :tree-id …}
                        :dir   {:hash "<hash de ruta>" :object "<id CAS>" :count n}}}}
```

Un hijo tiene `:entry` (una ruta trackeada termina ahí), `:dir` (hay rutas más abajo) o ambos
(el esquema 1 admite `a` y `a/b.md` a la vez; el árbol no rechaza lo que aquel aceptaba). Un
nodo que no es la raíz nunca queda vacío: el árbol es función de sus entradas y no del orden
de las escrituras que lo construyeron.

**Dos hashes sobre un mismo árbol.** El **hash de ruta** de un nodo cubre los segmentos y los
cuatro campos sensorizados de cada entrada, y deja fuera `:tree-id`, ids de objeto, revisión y
tiempo: registrar los mismos archivos con ULID nuevos da la misma raíz (invariante de C2). El
**id CAS** del nodo cubre todo, incluidos los localizadores. Al bajar, cada nodo se comprueba
contra lo que su padre registró (hash de ruta y cuenta); si no coincide es `route/corrupt`
(exit 5) **en esa rama**, y las demás siguen respondiendo. `stores verify` rehashea todos los
nodos alcanzables como cualquier otro objeto.

**Compatibilidad: ningún store cambia sin que se pida.**

- `store.edn` de un store **nuevo** lleva `:route-schema 2`. Un `store.edn` sin esa clave —todo
  store creado antes de este corte— es esquema 1.
- Un store de esquema 1 **abre, responde y escribe como antes**: mapa plano, la misma raíz, su
  caché derivado. Una escritura no lo convierte y `store.edn` no se toca.
- Mientras el store no tiene rutas decide `store.edn`; cuando las tiene, decide el objeto que
  nombran las cabeceras (su `:route/version`).
- `stores check` informa `:routes-schema` (1 o 2).
- El **replay** (`replay-open`, `verify-full`) aplica cada escritura con el esquema que declara
  `store.edn`, y compara las cabeceras resultantes con `heads.edn`.

**Pasar de 1 a 2 es explícito**, por una de dos vías:

1. `kimun stores upgrade` sobre un store formato 4 de esquema 1
   (`kimun.kernel.store/upgrade-routes!`): escribe los nodos del árbol con **las mismas
   entradas, `:tree-id` incluidos**, reemplaza las cabeceras bajo el lock de escritura (falla
   con `store/stale` si otro escritor las movió) y graba `:route-schema 2`. No agrega
   transacción ni revisión; el log no se mueve; documentos, árboles e historial quedan igual.
   Después rehashea todo lo alcanzable y, si eso falla, repone las cabeceras y `store.edn`
   anteriores. Interrumpido entre cabeceras y `store.edn`, el store abre y escribe como
   esquema 2 y una segunda llamada lo completa.
2. `kimun include build --rebuild`, que crea un store nuevo (esquema 2 desde el inicio).

**La raíz cambia al pasar de esquema.** Es otro cálculo sobre las mismas entradas, y el comando
devuelve las dos (`:routes-root-before`, `:routes-root`). Dos raíces de esquemas distintos no
se comparan entre sí. Un store actualizado y uno nacido en esquema 2 con los mismos archivos
tienen la misma raíz, porque el árbol no depende de la historia.

**Un mapa plano que no se puede leer no se actualiza.** Si la raíz de un store de esquema 1 no
corresponde a su mapa, `upgrade-routes!` falla con `store/error` y no escribe nada: se
reconstruye primero. Nada se inventa.

**Un binario anterior a este corte** no conoce el esquema 2: sobre un store de esquema 2 falla
al abrir con `store/corrupt-head` (`:not-a-route-map`). Falla cerrado, pero el mensaje habla
de corrupción y no de versión. No se subió la versión de formato para no forzar la migración
de todos los stores formato 4.

**Límites.**

- **No hay fan-out dentro de un directorio, y el coste no es independiente del número de
  rutas.** Lo que el árbol acota es la **cantidad de nodos** leídos y escritos (los del camino).
  Cada uno de esos nodos se lee, se rehashea y se reescribe **entero**, de modo que el trabajo y
  los bytes escritos son proporcionales a los hijos directos de los directorios del camino. Un
  directorio ancho cuesta en proporción a su ancho, y un store con todos sus archivos en **un**
  directorio (o en la raíz) paga por todas las rutas en cada escritura, igual que el esquema 1.
  Lo medido: 2000 rutas en 20 directorios de 7 subdirectorios tocan 3 nodos por escritura, y
  eso prueba **nodos tocados**, no bytes; con las mismas 2000 rutas en un solo directorio se
  tocan 2 nodos y uno lleva las 2000 entradas (`kimun.kernel.route-tree-test`,
  `a-wide-directory-is-rewritten-whole`). No está medido en un store real.
- `14 §20` pide que el cambio suba a la raíz y se pueda bajar solo por la rama que cambió; eso
  está. **No** está un coste sublineal dentro de un directorio plano. Si K2a lo exige, es un
  pendiente: un índice con fan-out por directorio sería otra forma de nodo, es decir otro
  esquema de rutas con su propio paso explícito.
- Los nodos reemplazados quedan en `objects/` sin referencia, como los demás objetos
  superados; recolectarlos es K5.
- Enumerar todas las rutas (`stores check`, la cobertura) sigue leyendo el árbol entero.
- Las hojas son entradas por documento; los **campos** trackeados como hojas (`14 §20`) no
  están: dependen de K1.
- La ruta sigue siendo localizador, no identidad; nada aquí decide qué es un archivo movido.

### 11.3 Inventario de objetos no alcanzables (K5, primer corte)

`§5` y `§7` dicen que en `objects/` quedan objetos sin referencia y que ningún comando los
informa. Desde este corte los informa `kimun stores unreachable`
(`kimun.kernel.store/unreachable`). **Es solo un inventario: no escribe, no borra, y no existe
todavía un comando que borre.**

**Alcance exacto.**

- **Guardado:** todo id bajo `objects/`. El caché derivado (`cache/`) no se mira.
- **Alcanzado:** `store/reachable`, el mismo recorrido que rehashea `stores verify`: la revisión
  de cabecera y **toda su historia** (`:parents`), las cabeceras por árbol y el mapa de rutas que
  nombran las cabeceras. No hay política de retención: nada de la historia se considera
  prescindible.
- **No alcanzable** = guardado y no alcanzado. Cada uno se lee del store mismo (nunca del caché),
  se rehashea y cae en una de tres listas:

| lista | qué es | qué se puede decir |
|---|---|---|
| `:held` | un plan resuelto (`:plan`) cuyo id nombra una línea **confirmada** del log (formatos 1–3 guardan los planes en el CAS) | el recorrido no lo sigue, pero sin él el log no se puede reproducir (`verify-full` responde `:replay-unavailable`). **No es candidato** |
| `:damaged` | no hashea a su nombre, o no se puede leer | su clase no es confiable: solo se informa |
| `:candidates` | el resto, con su clase por forma y `:root?` | nada conocido lo necesita **en el momento del inventario** |

**Clases** (por forma, `store/object-class`): `revision`, `mmap-leaf`, `mmap-branch`, `route-map`
(esquema 1), `route-node` (esquema 2), `plan`, `descriptor`, `node`, `edge`, `tree-object`,
`unknown`. La clase dice qué es el objeto, no por qué quedó ahí.

**Qué se puede leer de los candidatos sin adivinar.**

- `:root?` — ningún otro objeto no alcanzable menciona su id. Son las raíces del mapa de
  cabeceras o del mapa de rutas que un commit posterior reemplazó o que escribió un escritor
  perdedor.
- `:lost-revisions` — candidatos de clase `revision`: commits que no están en la historia de la
  cabecera. Es la revisión de un escritor que perdió la carrera (`§7`) o la de la cola no
  confirmada del log (`:uncommitted-tail`, que el inventario nombra). Una revisión perdida no es
  raíz: la menciona el mapa de cabeceras que se escribió con ella.
- Un store formato 4 con un solo escritor deja solo `mmap-*` y `route-*` (raíces reemplazadas).
  Un store de esquema de rutas 1 deja un `route-map` por escritura que tocó rutas.

**Lo que este corte no decide.**

- **Un candidato no es basura comprobada.** Un escritor guarda sus objetos **antes** de mover
  las cabeceras (`§7`); un inventario tomado durante un commit lista objetos que están por ser
  alcanzados. Borrar exige el lock de escritura y volver a mirar; nada de eso está hecho.
  `:stable?` dice si las cabeceras se movieron entre abrir y terminar el inventario; no
  protege contra un escritor que todavía no las movió.
- **Retención e historia:** qué revisiones antiguas se pueden soltar (pins, poda) es la
  política del hito 8 de `02`; aquí toda la historia cuenta como alcanzada.
- **El índice de nombres** de K5 no está en este corte.
- **«Menciona»** es toda cadena con forma de id dentro del objeto: un superconjunto de sus
  referencias. Puede marcar como no-raíz un objeto que nadie referencia de verdad; nunca al
  revés.
- No se miran referencias desde fuera del store (otro store enlazado, el índice derivado,
  `upgrade-format-1.edn`). No se conoce ninguna que apunte a objetos de `objects/`; no está
  demostrado que no exista.

**Comprobación de solidez** (`kimun.kernel.unreachable-test`): sobre una **copia** de un store,
borrar todos los candidatos deja un store que abre en la misma cabecera, con las mismas rutas,
que pasa `verify` y `verify-full` (el log sigue reproduciéndose) y acepta una escritura nueva.
Borrar un plan `:held` deja `verify` en verde y el replay no disponible: por eso no es
candidato.

### 11.4 Packs de objetos pequeños (K4, primer corte)

Un objeto suelto de menos de 4 KB ocupa un bloque entero: en un store real la
mayoría son así y de ahí los 551 MB en bloques de §9. `stores pack` mueve los
objetos pequeños a un pack; `stores unpack` los devuelve.

```
<dir>/packs/<name>.pack   "KMNPACK1"                                    8 B
                          registro*: longitud (4, big-endian) + contenido UTF-8,
                                     en orden estricto de id
                          pie: "KMNPIDX1" (8) + número de registros (8)
                               + SHA-256 del índice canónico (32)        48 B
<dir>/packs/<name>.idx    índice canónico, 44 B por registro:
                          id (32 binarios) + offset (8) + longitud (4)
```

- **El pack es store** (única copia de lo que contiene); el `.idx` es
  **derivado** y el **pie es store**. El pack no escribe el id por registro: el
  id es el SHA-256 del contenido.
- **Empaquetar es explícito** y corre bajo el lock de escritura; nunca es parte
  de un commit. Solo objetos sueltos con tamaño `< --max-bytes` (por defecto
  4096, tope 16 MiB). Un suelto cuyo contenido no hashea a su nombre **no** se
  empaqueta: queda suelto y se informa (`:skipped-damaged`).
- **Carga**: se usa el `.idx` cuando su tamaño y su SHA-256 coinciden con el
  pie (sin rehashear contenidos); si no, se reconstruye **una vez por instancia
  de backend, en memoria, y no se escribe nada**: un lector no modifica el store
  (`stores sense` y `stores heartbeat` prometen no escribir). Si el índice
  reconstruido tampoco coincide con el pie, el pack o el pie están dañados:
  `:store/corrupt-pack`; la recuperación es `stores unpack`, que lee solo el pack
  e ignora el índice y el pie. Mientras nadie repare el `.idx`, **cada comando
  vuelve a recorrer el pack**: 2,66 s para un pack de 14,8 MB en el store de
  banco. La reparación es explícita: `stores pack` reescribe el `.idx` que falte
  o no coincida (`:indexes-repaired`), aunque no haya nada que empaquetar.
- **Lectura**: búsqueda binaria sobre los bytes del índice (los ids están
  ordenados); el rango se valida contra el cuerpo del pack antes de reservar
  memoria; **no hay hash por lectura** y un fallo no se reintenta. Un registro
  dañado dentro del pack se lee tal cual y `verify` lo informa como
  `:hash-mismatch`, igual que un suelto dañado.
- **Frescura para un lector vivo**: un backend recuerda la fecha de modificación
  de `packs/` y relista solo si cambió. Como esa fecha puede tener granularidad
  de un segundo, un sello igual solo se cree cuando tiene más de 2 s: durante
  los 2 s siguientes a un `pack`/`unpack` el directorio se lista en cada lectura.
  Un pack que desaparece entre la búsqueda y la lectura (otro proceso hizo
  `unpack`) se resuelve leyendo el suelto.
- **Vuelta atrás**: `stores unpack` escribe cada objeto que falte, reemplaza un
  suelto dañado aunque tenga el mismo tamaño, comprueba que todos están y solo
  entonces borra `.pack` y `.idx`.
- Formato del store: sigue en **4** (empaquetar no cambia ids, revisiones ni
  cabeceras). Un binario anterior a K4 **no lee** un store empaquetado: no
  encuentra los objetos que están en el pack y falla al abrir
  (`store/corrupt-head`, `:missing`). `stores unpack` con un binario que conozca
  los packs lo deja legible otra vez.
- **Durabilidad**: se fuerza el fichero del pack (`force`) y, tras el `move`, el
  directorio `packs/` **antes** de borrar los sueltos (mejor esfuerzo: algunas
  plataformas no lo admiten). No se fuerza `objects/` al escribir un objeto, ni
  el directorio del store: la garantía es «el pack y su entrada existen antes de
  perder la otra copia», no un `fsync` completo del sistema de ficheros.
- **Recuperación**: `unpack` lee solo el pack, pero necesita su **armazón**
  intacto (magia `KMNPACK1`, longitud de cada registro, magia y cuenta del pie).
  Un pack truncado a mitad de registro no se recupera por partes.

Medido en un store sintético de 600 documentos (`runs/subagents/2026-11-15-K4/measurement.md`):
`.kimun` en bloques **204,6 → 145,6 MB (−28,8 %)**; `objects/` en bloques
151,7 → 77,1 MB; `docs list` 0,72–0,73 → 0,84–0,86 s (**×1,16**); `stores verify`
75,7 → 77,5 s (+2,4 %); `stores pack` 4,54 s, `stores unpack` 7,5 s; `objects/`
byte-idéntico tras `unpack`.

El ×1,16 de `docs list` (unos 0,12 s) corresponde a sus 4.198 lecturas de pack
(de 4.202 lecturas de objeto), unos 29 µs por lectura. De eso, abrir, leer y
cerrar el `.pack` son unos 4 µs medidos aparte —no se cachea el canal: un
backend por latido de `stores heartbeat` filtraría un descriptor por pack—; el
resto (el `stat` de frescura del directorio y la búsqueda binaria, que corre
interpretada) **no está desglosado**. El sobrecoste
grande que hubo primero (×1,55) **no** era eso: era la conversión del id en cada
búsqueda, un bucle interpretado de 32 pasos (~52 µs por id, 217 ms por comando),
que `java.util.HexFormat` deja en ~1 µs; lo mismo bajó `stores pack` de 12,3 s a
4,54 s. La lectura empaquetada **no** cuesta lo mismo que la suelta, y el factor
está medido en un solo store de 600 documentos: no hay medición a otra escala.

**Límites de este corte.** `cache/` **no se empaqueta** y es la mayor parte de
los ficheros pequeños del formato 4 (aquí 12.344 ficheros, **36 % de los bloques
del store empaquetado**), así que el objetivo de bloques de §9 no se alcanza con
packs solo. No hay reempaquetado ni GC: un objeto empaquetado que
deja de ser alcanzable sigue en el pack y solo `unpack` + otro `pack` lo
reordena. La durabilidad es la del punto de arriba y no más: no hay `fsync` de
`objects/` ni del directorio del store.
### 11.5 Pack de la caché (K4b)

`cache/objects` es la mayor parte de los ficheros pequeños del formato 4; K4
empaquetó el store y dejó la caché aparte. `stores pack --cache` empaqueta la
caché con el **mismo formato** que K4 (magia, registros, pie con cuenta y
digest), pero bajo `cache/packs/`: `rm -rf cache/` lo borra todo.

**Qué es store y qué es caché.** El pack de `packs/` es store (única copia,
`verify` lo rehashea). El de `cache/packs` es **caché**: derivado, borrable y
recalculable desde el archivo. `object-ids`, `read-stored-object`, `verify`,
`verify-full`, `unreachable`, `stored-size`, `pack-stats` y `unpack!` **no
conocen** el pack de caché.

**Orden de lectura** de un objeto (`FsBackend/read-object`): suelto de store →
packs de store → suelto de caché (verificado) → **pack de caché (verificado)** →
plan. **Toda lectura del pack de caché se verifica por hash**: si el SHA-256 de
los bytes no es el id pedido, es fallo de caché (`nil`), aunque el pie y el
`.idx` sean válidos; eso cubre bytes corruptos con índice bueno.

**Un lector no escribe ni falla por la caché.** Un pack de caché cuyo `.idx`
falte o no coincida con el pie, o cuyos registros no deriven el pie, se **ignora
en memoria** para esa instancia de backend: no se recorre el pack para
reconstruir el índice, no se renombra ni se borra, y no hay error. Es caché fría
y `doc-tree` reconstruye del archivo (C2). `stores check` lo nombra con el
warning `cache/corrupt-pack` (nunca exit 5). El estado de los packs cargados
vive en el `FsBackend` (átomos propios, sello del directorio con asentamiento de
2 s); `kimun.host.store-cache` y `kimun.surface.docs` no cambian.

**El comando.** `stores pack --cache [--max-bytes N]`, bajo el lock del store:
descarta los packs de caché que no cargan (`:dropped-packs`), reescribe el
`.idx` de los sanos que lo tienen mal (`:indexes-repaired`), **borra** el suelto
de caché que no hashea a su nombre (`:dropped-damaged`), borra el suelto que ya
está en un pack (`:deduplicated`) y empaqueta el resto en streaming, un objeto
en memoria a la vez. No hay `unpack --cache`: la caché se recupera borrándola.

Medido sobre una copia del store de banco de 600 documentos con el store ya
empaquetado (`runs/subagents/2026-11-15-K4b/measurement.md`): `.kimun` en
bloques **143,7 → 101,8 MB (−29,2 %)**; ficheros del store **12.953 → 1.839**;
`cache/objects` baja de 12.344 ficheros a 0; `stores pack --cache` tarda 1,09 s
(11.116 objetos, pack 3,1 MB + índice 0,5 MB); `docs render`/`docs show` quedan
en 0,54–0,58 s y `docs list` en 1,01 s, igual que con la caché suelta.

**Límites.** Un pack de caché sin `.idx` es caché fría hasta el siguiente
`stores pack --cache` (un lector no lo reconstruye). No hay reempaquetado ni GC:
un objeto de caché que deja de usarse sigue en el pack hasta que se borre
`cache/`. Un duplicado suelto convive sin daño hasta la siguiente corrida del
comando.

