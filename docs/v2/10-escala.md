# kimun (SLDB v2) — Escala: formato 2 del store (2026-10-06)

> Por qué y cómo cambió la forma en disco de las revisiones, qué cuesta ahora abrir,
> leer y escribir un store, qué comprueba cada verificación y cómo se pasa un store de
> formato 1 al 2. Este documento es el **único dueño del formato 2**; `02` fija el
> contrato de la revisión y enlaza aquí para la forma en disco. Autoridad: `02` (qué
> construye) > este documento. Números en
> `runs/bench/baseline-2026-10-06.txt` (antes) y `runs/bench/after-2026-10-06.txt` (después).

## 1. Antes

El store real de sldb v1 migrado a kimun (1.551 documentos, 1.586 transacciones) con el
kernel de formato 1, antes del trabajo de escala:

| medida | valor |
|---|---|
| `docs show` de un documento | 187,9 s |
| `docs list` | 180,3 s |
| `stores check` / `stores verify` | 173,2 s / 228,4 s |
| `.kimun/` en disco | 606 MB, 36.718 objetos |
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
Ningún comando los informa hoy; los recogerá el GC del hito 8. `stores check` cuenta
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
| `.kimun/` | 606 MB, 36.718 objetos | 97,7 MB, 82.755 objetos | ≤ 150 MB |
| migración desde v1 (estricta + `--force`) | 522 s | 71 s | ≤ 5 min |

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
