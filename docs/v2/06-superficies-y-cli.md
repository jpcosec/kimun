# kimun — Superficies y CLI

> Inventario real de la CLI de `kimun`: grupos, subcomandos, opciones, forma de `:data` y
> códigos de salida. Cada afirmación de aquí corresponde a `src/kimun/cli/**`
> (`main.cljc`, `args.cljc`, `out.cljc`, `errors.cljc`, `store.cljc`, `commands/*`).
> Autoridad según `docs/v2/README.md`. El kernel es `docs/v2/02`; los modelos, `07`; la
> distribución, `08`; los formatos del store y la escala, `10`; la inclusión en
> git, `13`. Los anillos y reglas de dependencia son `03`.

## 1. Envelope, opciones globales y códigos de salida

Todo comando escribe **un** valor en stdout, construido por `kimun.cli.out`:

```clojure
{:ok true  :command "stores check" :store {:name "kimun" :path ".kimun"}
 :revision "<rev-id>|nil" :data {…} :warnings [{:type … :message …}]}
{:ok false :error {:type "store/corrupt" :message "…" :data {…}} :exit 5}
```

- `:type` es el keyword del error (`03 §2`) impreso como `"ns/name"`; el stacktrace solo con `--debug` (a stderr).
- Un comando con `:warnings` sigue en `:ok true`.
- Un exit distinto de 0 siempre lleva `:ok false` y `:exit` repetido en el envelope.
- **Excepción explícita: `stores heartbeat`.** Es el único comando que escribe una secuencia en stdout (NDJSON, un registro JSON compacto por línea) en vez de un envelope; su contrato está en §3, «El latido». Un fallo anterior al primer registro (opción inválida, sin store) sí es el envelope de fallo habitual.

Opciones globales (`kimun.cli.args`):

| opción | significado |
|---|---|
| `--store PATH` | directorio del store (`.kimun` o su proyecto); si falta, `$KIMUN_STORE`; si falta, walk-up |
| `--format FMT` | `json` (defecto) · `edn` · `text` · `md` · `yaml` · `ast` |
| `--actor NAME` | actor de las transacciones (defecto `human/$USER`) |
| `--debug` | imprime el stacktrace de un error inesperado en stderr |
| `--wait` | si otro proceso escribe, espera en vez de fallar con `store/busy` |
| `--help`, `--version` | uso y versión |

Un primer token que no sea grupo conocido devuelve `eval/not-available` (exit 6) nombrando los grupos; el evaluador anclado no existe todavía (ver §6).

Códigos de salida (`kimun.cli.errors`): 0 ok, 1 `missing`/`empty`, 2 `ambiguous`, 3 rechazo de entrada, 4 `conflict`/`stale`, 5 store/serve (runtime), 6 uso, 70 inesperado. La tabla explícita:

| exit | cuándo |
|---|---|
| 0 | ok |
| 1 | `*/missing` o `*/empty` por nombre (`find/empty`, `graph/empty`, `links/missing`, `docs/missing`, `models/no-file`, `eval/missing`) |
| 2 | `*/ambiguous` |
| 3 | namespaces `query` `models` `validate` `docs` `fields` `sections` `find` `graph` `edges` `semantic` `include` `links` `declare`; `:plan/rejected` salvo chequeo de capability |
| 4 | `*/conflict`, `*/stale`, `plan/conflict`, `store/stale`, `store/busy` |
| 5 | namespaces `store` `route` `markdown` `index` `serve`; `cli/no-store`, `cli/store-exists` |
| 6 | `cli/usage`, `org.babashka/cli`, `eval/not-available` |
| 7 | `plan/rejected` con `{:check :capability}` |
| 70 | cualquier otra cosa |

## 2. El store `.kimun/` (formato 4; 2/3 legados)

Descubrimiento (`kimun.cli.store/resolve-store`): `--store` > `$KIMUN_STORE` > walk-up buscando `.kimun/store.edn` desde el cwd. Un valor de `--store` que no es ruta existente y parece nombre de enlace se busca como enlace del store actual o de un ancestro; si tampoco, `cli/no-store` (exit 5). Sin store, todo comando que lo exige falla con `cli/no-store` (exit 5).

Layout de un store formato 4 (`kimun.kernel.store`; detalle en `10`):

```
.kimun/
  store.edn      ; {:format-version 4 :hash-alg :sha-256 :capabilities {…} :name :links}
  objects/<id>   ; CAS: EDN canónico del objeto; H(fichero) == id (registro y estructura referenciada)
  cache/objects/<id>  ; derivado (tree objects, nodos block/text/opaque de docs con archivo), gitignored
  cache/docs/<key>.edn ; índice por documento: {fingerprint, class, root-hash, tree}
  log.edn        ; una Transaction por línea, append-only
  heads.edn      ; {:head rev-id :heads-root <id-mmap>}  (commit point)
  lock           ; fichero vacío; lock exclusivo de commit
  index/<head>.edn   ; índice derivado por cabecera (`rebuild-indexes`), gitignored
```

- Un store de formato anterior al 4 o sin esquema de rutas 2 no se abre: cualquier comando que lo abra falla con `store/unsupported-format` (exit 5) y pide reconstruir con `include build --rebuild` (`10 §8`). No hay `stores upgrade`.
- Los enlaces (`:links`) son `{:name :path}` con `:path` **relativo al store**; un enlace absoluto o duplicado es `store/invalid-links` (exit 5). `stores link NAME PATH` enlaza un `.kimun` o su proyecto; `stores links` informa `ok | missing | not-a-store`.
- Capacidades por defecto al crear: `{actor :all "kimun/derive" :all "kimun/materialize" :all}`.
- `index/` es derivado: leer nunca escribe; `rebuild-indexes` lo reconstruye bajo orden.

## 3. Grupos de la CLI

### stores

| subcomando | opciones | `:data` y notas |
|---|---|---|
| `stores init` | `--name NAME`, `--content by-reference\|inline` | `{:path :name :actor :capabilities}`; `--content inline` escribe `:content :inline` en `store.edn` (docs/v2/10 §10); falla `cli/store-exists` (5) si ya hay store |
| `stores check` | — | `{:name :path :hash-alg :revisions :trees :objects :head :links}` + `:routes-root :route-count :routes-complete? :routes-missing :routes-shared :routes-outside` si hay documentos registrados con archivo (abajo, «Rutas») + `:documents` si hay findings; abre por replay; store inválido ⇒ `store/invalid` (5) |
| `stores verify` | — | `{:checked :bad}`; verify-full (replay + hash); corrupción ⇒ `store/corrupt` (5) |
| `stores show` | — | `{:name :path :hash-alg :links :head :capabilities}`; sin replay |
| `stores health` | `[--full]` | `{:status :summary :findings :actions :next :scope}`; `ok \| degraded \| needs-human`. Igual que `sense`, abre solo los documentos que git reporta cambiados desde el puntero (ver `stores sense`); `--full` abre todos |
| `stores sense` | `[--once] [--full]` | una observación read-only: `{:mode :once :observed-at :tracked :valid :signals :counts :summary :scope}`; con repositorio git con commits **y** puntero (`stores seen`) pregunta a git qué rutas cambiaron entre el puntero y el árbol de trabajo (lo no commiteado y lo no seguido incluidos; un renombre cuenta en la ruta vieja y en la nueva) y **solo abre y hashea esos documentos**: uno cuya ruta git no reporta cuenta como válido a nivel de archivo, y solo se le hacen las comprobaciones que salen del store (huérfano, contrato movido/inconsistente, parser no disponible); `unextractable`, `rebuild-mismatch` y `noise` necesitan el texto y solo se detectan en los documentos que se abren. Sin git, sin commits o sin puntero, o con `--full`, abre todos como antes; un puntero dañado es `git/bad-pointer`. `:scope` `{:mode :git\|:full :since :head :changed :opened}` dice qué camino se usó (`:opened` = archivos leídos). **Nadie mueve el puntero aquí**: si alguien lo movió con el store desfasado, el camino por git no ve ese desfase (`--full` sí); y un documento en un archivo ignorado por git no se ve cambiar. La verificación de hashes de los objetos del store (`verify`) se hace igual en ambos caminos; reusa `kimun.surface.diagnostics`, no escribe ni en el store ni en `<store>/cache/` y no inicia daemon. `:observed-at` es la hora de esta respuesta, no un evento persistido; las señales cubren las categorías del diagnóstico, no todo cambio topológico. Distingue fuente, contrato y recálculo (`10 §11.1`, K6): `source/*` frente a `contract/changed`, `contract/parser-unavailable`, `contract/inconsistent` y `contract/rebuild-mismatch`; el `:detail` lleva `content-ok`, `contract-ok`, `contract {:status :changed}` y `:registered-root`/`:rebuilt-root` |
| `stores changed` | `[--since COMMIT]` | solo lectura: pregunta a git qué archivos cambiaron entre COMMIT y el árbol de trabajo (incluye lo sin commitear y lo nuevo sin seguimiento no ignorado; sin hashear nada por cuenta propia) y, solo de los cambiados con alguna marca (`docs mark`), el estado de cada marca (`anchored \| mutated \| ambiguous \| missing`, o `unknown` con `:reason` si COMMIT no dice qué había) comparando el texto de COMMIT (`git show`) con el de ahora. `{:since :head :dirty :changed :marks :summary}`. Sin `--since` usa el puntero de `stores seen` (si no hay, error de uso que dice cómo crearlo); `:data` añade `:pointer`. No mueve el puntero ni toca el store; fuera de un repositorio git: `git/not-a-repository`. Al lado de `stores sense`, que no cambia |
| `stores seen` | `[--set COMMIT]` | el puntero «miré hasta este commit», en `<store>/seen.edn` (`{:commit sha}`, escritura atómica, fuera del log). Sin `--set` solo lee: `{:commit :head :behind}`; con `--set` (COMMIT; `HEAD` es uno) lo mueve —lo único que escribe— y responde `{:previous :commit :head :behind}`. `include build` lo deja en HEAD si el proyecto es un repositorio git con commits. Ilegible o con un sha desconocido: `git/bad-pointer` |
| `stores heartbeat` | `[--interval SECONDS] [--beats N] [--full]` | daemon: repite la observación de `stores sense` (cada latido pregunta a git; `--full`: abre todos los documentos; la `observation` lleva `:scope`) cada intervalo y la escribe como NDJSON (`started`, `beat`/`error`, `stopped`); no escribe en el store. Contrato abajo, «El latido» |
| `stores update` | — | `{:updated [name] :skipped [{:document :category :action}] :status}`; todos los reparados van en UNA revisión |
| `stores reconcile` | `[ROOT] [--apply]` | `{:root :discovered :missing :stale :applied}`; sin `--apply` no escribe |
| `stores link` | `NAME PATH` | `{:link {:name :path} :resolved}` |
| `stores unlink` | `NAME` | `{:unlinked {:name :path}}` |
| `stores links` | — | `{:links [{:name :path :resolved :status}]}`; enlace roto es warning |

#### El latido (`stores heartbeat`)

Contrato del daemon de heartbeat que el dueño propuso el 2026-11-14 (cabecera de `14`): su **única
función es sensar**. Código: `kimun.surface.heartbeat` (el bucle, sin proceso ni reloj propios) y
`kimun.cli.commands.stores/heartbeat`.

**Qué hace y qué no.** Cada latido abre el store desde cero y recalcula la misma observación que
`stores sense` (mismo código): las señales de `kimun.surface.signals` sobre el diagnóstico existente.
No despacha acciones, no llama LLMs, no calcula embeddings, no repara, y no escribe en el store, el
log, las cabeceras, la caché ni ningún otro archivo. No cambia la identidad ni amplía qué topologías
se observan: cubre las categorías del diagnóstico, igual que `sense`. No guarda nada entre latidos
salvo, en memoria, la huella de la última observación exitosa (para `changed`).

**Salida.** NDJSON en stdout, con flush por línea; `--format` no aplica al flujo. Registros, en orden:

| `kind` | cuándo | campos |
|---|---|---|
| `started` | una vez, al inicio | `schema` (`"kimun.heartbeat/1"`), `at`, `store {name path}`, `interval-ms`, `beats` (`null` = sin límite) |
| `beat` | observación lograda | `seq`, `store`, `revision` (cabecera observada), `changed`, `elapsed-ms`, `observation` = el `:data` de `stores sense` sin `:mode` (`observed-at tracked valid signals counts summary scope`) |
| `error` | observación fallida | `seq`, `at`, `error {type message data}` (la forma del envelope de fallo), `consecutive-errors`, `elapsed-ms` |
| `stopped` | una vez, al terminar en orden | `at`, `reason` (`beats` · `cancelled` · `fatal`), `beats` (intentos), `errors` (fallidos); siempre esos cuatro. Con `fatal` se añade `error {type message}` |

**Semántica de la observación.**

- Un `beat` es el **estado completo** recalculado, no un delta: quien llega tarde o pierde una línea
  no pierde información; el siguiente latido la repite. Dos latidos sin cambios dan las mismas señales.
- `changed` compara cabecera, `tracked`, `valid` y señales con la última observación lograda **de este
  proceso** (el primer latido es `true`; la hora no cuenta). Es una ayuda para el consumidor, no un
  evento: no se persiste y no sobrevive al proceso.
- Una señal no es una acción ni un hecho registrado: quién responde, y cómo, queda fuera del daemon.
- `seq` cuenta intentos desde 1, logrados o no.

**Intervalo.** `--interval SECONDS`, con decimales; por defecto 30, mínimo 0.1, máximo 86400. Es un
retardo fijo contado **desde el final** de una observación: las observaciones nunca se solapan ni se
acumulan si una tarda más que el intervalo. `--beats N` termina tras N intentos (para scripts y pruebas).

**Error transitorio.** Toda observación fallida se trata como transitoria: se emite `error`, el bucle
sigue y el latido siguiente vuelve a abrir el store desde cero (por ejemplo, un store que desaparece
y vuelve: `cli/no-store` y luego `beat`). No hay reintento inmediato ni espera creciente, y el daemon
no decide rendirse: `consecutive-errors` deja esa decisión al consumidor.

**Cancelación.** SIGINT o SIGTERM se atienden entre observaciones y durante la espera, nunca a mitad
de una: se emite `stopped` con `reason: "cancelled"` y el proceso termina con el estado de la señal
(130 o 143). El proceso espera ese registro hasta 2 s; si la observación en curso tarda más, termina
sin `stopped`. Con `--beats`, `reason: "beats"` y exit 0.

**Consumidor ausente.** Si el consumidor cierra stdout, el bucle termina sin escribir más (exit 0).
No hay registro `stopped` para ese caso: `stdout-closed` es un resultado interno del bucle
(`kimun.surface.heartbeat/outcomes`), no un `reason`. Un fin de flujo sin `stopped` debe leerse como
final abrupto. El cierre se detecta sobre el stdout del proceso (`System/out`): es el único destino
soportado; si se invoca el comando en proceso con `*out*` reasignado, las líneas van a ese writer y
el cierre del consumidor no se detecta.

**Fallo fatal.** Si falla el propio bucle (no una observación), el flujo termina con `stopped`,
`reason: "fatal"`, los mismos `beats`/`errors` y `error {type message}` (tipo `heartbeat/fatal`), y el
proceso sale con 70. Ese registro se escribe si stdout aún lo admite.

**Límites conocidos.** Cada latido incluye la verificación de hashes del store (`stores check`), así
que su costo crece con el store; el intervalo lo elige quien lo lanza (bajo `memcap` si el store es
grande, `AGENTS.md`). No hay activación por eventos: el daemon solo da el pulso.

### docs

#### Nombres de documento (K8)

Un documento tipado tiene tres formas de nombrarse; ninguna de las dos primeras es su identidad.

| forma | qué es | estabilidad |
|---|---|---|
| nombre corto (`:name`) | un **alias**: por defecto el nombre del archivo sin extensión | varios documentos pueden compartirlo |
| nombre cualificado (`:qualified`) | la **ruta** del archivo, relativa al proyecto y normalizada (`/`, sin `./`, NFC); es el localizador del anclaje | sigue al archivo: si la ruta cambia, cambia |
| id de árbol (`:tree`) | la referencia estable del documento en el store | no cambia |

- `a/README.md` y `b/README.md` son dos documentos con alias `README`. Antes de K8 el segundo se
  rechazaba con `docs/exists`.
- Todo comando que recibe un documento (`docs show|update|untrack|delete|recover|compose`, `fields`,
  `sections`) acepta cualquiera de las tres formas. Orden: id de árbol, nombre corto, nombre cualificado.
- **Un alias que nombra a más de un documento no se resuelve nunca**: falla con `docs/ambiguous`
  (exit 2) y `:candidates [{:name :qualified :tree :path :model}]`. No se elige por orden, por hash
  ni por última escritura.
- `docs/exists` (3) queda para lo que nada distingue: el mismo nombre sobre el mismo archivo, o el
  mismo nombre cuando alguno de los dos documentos no tiene archivo.
- El diagnóstico (`stores check|health|sense|heartbeat`) sigue dando `:document` = nombre corto, con
  `:path`, y añade `:qualified` y `:tree` al finding. Si el alias es compartido, `:action` y el mensaje
  nombran el documento por su nombre cualificado. `stores update` repara por id de árbol.
- La ruta **no es identidad**: un archivo movido es una fuente ausente (`source/missing`), no se
  reapunta solo. Cuando su archivo se mueve, la continuidad se **declara** (contrato D0, `14 §21`):
  `docs reanchor NAME FILE --reason TEXT` conserva la unidad; sin declaración sigue ausente.

Fuera de este corte:

- **`include`** sigue nombrando por el nombre del archivo sin extensión y rechaza dos declarados
  con el mismo (`include/conflict`, `13`). Compara lo declarado con el store por nombre; pasarlo a
  ruta va con el registro de rutas de K2a (corte C2 de
  `runs/research/2026-11-14/concilio/k2a-implementacion.md`), que aún no existe.
- Los documentos **no tipados** (`docs ingest`) siguen con nombre único.
- Enlaces: `docs recover|compose ALIAS` con un alias compartido falla con `docs/ambiguous`. Dentro de
  un documento, `[[alias]]` compartido **no** se liga a ningún documento del store: se resuelve solo
  si nombra un archivo (junto al documento o bajo la raíz) y, si no, queda en `:unresolved`
  (`links/missing`). `[[a/README.md]]` se resuelve por ruta, como antes.
- Buscar por nombre cualificado lee todos los documentos (no hay índice por ruta hasta C2); por
  nombre corto e id de árbol cuesta lo de antes.

#### Rutas de lo trackeado (K2a, corte C2b)

Cada documento registrado con archivo —tipado o de clase genérica— lleva una **ruta** en el mapa
de rutas del store (`kimun.kernel.route`, `10 §11`), cuya raíz es el Merkle de `14 §20`. La
superficie `docs` la mantiene en cada escritura; la entrada es:

| campo | qué cubre | tipado | `generic/md` |
|---|---|---|---|
| `:path` | localizador: la ruta cualificada | igual | igual |
| `:fingerprint` | los bytes de la fuente | hash del texto | igual |
| `:class` | qué clase selecciona | id del descriptor del modelo | `"generic/md"` |
| `:projection-hash` | la estructura leída: digest canónico del AST neutro, **antes** de ponerle el id del documento | igual | igual |
| `:content-hash` | lo que la clase selecciona de esa estructura | hash del payload extraído | el mismo que `:projection-hash`: la clase selecciona la estructura entera |

- **La raíz no depende de ids nominales.** Registrar los mismos archivos de nuevo, con ULID nuevos,
  da la misma raíz. El `tree-id` va en la entrada como localizador, fuera de la raíz.
- **Qué mueve la raíz:** una edición (fingerprint, proyección y, en tipados, contenido), un
  reformateo (solo el fingerprint; la proyección no cambia), un cambio de clase y una ruta que entra
  o sale. **No** la mueve un cambio de contrato de parse que deja la misma estructura: eso lo
  sensoriza K6 (`contract/changed`).
- **Mover** un documento a otra ruta (`docs update OTRO.md --name N` en un genérico; `:path` en
  `update-plan` para un tipado) retira la ruta anterior y registra la nueva **en el mismo commit**.
- **`docs untrack|delete`** retira la ruta junto con el anclaje; la ruta queda libre. Son un
  **cierre declarado**: `--reason TEXT` es opcional; `:closure {:file :reason}` de la respuesta lleva
  la razón y el estado del archivo antes de actuar (`present | missing | none`).
- **`docs mark|unmark|marks`** son las **unidades estructurales** (K1e): una sección o un
  bloque se marca por **declaración** (nada se marca solo), con id propio acuñado al marcarla
  y localizador + contrato como anclaje. `docs marks` calcula un estado contra el texto leído
  ahora —`anchored`, `mutated` (el lugar resuelve y lo registrado no está en otra parte),
  `ambiguous` (el lugar resuelve a otra cosa y lo registrado aparece en otro sitio) o
  `missing`— y, si hay evidencia, **candidatos**; kimun **nunca decide** que una unidad se
  movió, se partió o es otra: lista todos los candidatos y el actor declara (`docs mark --unit
  … --reason …`). Una escritura normal registra la huella nueva de una marca `mutated` y no
  toca las demás; una marca no se quita en una escritura (`docs unmark` lo declara). Los
  ordinales del localizador se desplazan al insertar: eso es exactamente lo que `ambiguous`
  señala sin resolver.
- **`docs anchors NAME`** devuelve las **anclas** de un documento como evidencia: secciones
  (encabezado y lo que sigue hasta el siguiente de nivel menor o igual, anidadas) y bloques
  (hijos directos del documento), cada uno con un **localizador** —camino de títulos con ordinal
  entre hermanas, o `{:section :type :n}`—, la huella de su sub-AST y, cuando la capa de bloques
  concuerda 1:1 con el AST neutro, línea y bytes. Un localizador **no es identidad**: insertar un
  bloque desplaza los ordinales de los siguientes del mismo tipo en esa sección (lo que justifica
  que `K1e` acuñe identidad al marcar). Es solo lectura y no baja dentro de un bloque; los rangos
  en grafemas son de `S2b`.
- **`docs reanchor NAME FILE --reason TEXT`** declara que el documento está anclado ahora en FILE:
  conserva id de documento e id de árbol, libera la ruta vieja y toma la nueva en una sola
  revisión. La ausencia no termina una unidad y el sensor no decide continuidad: eso lo declara
  este comando. kimun **no mueve ni borra** el archivo; si el viejo sigue ahí, warning
  `docs/previous-file-present`. Límites: no hay detección de movimientos, `--reason` es
  opcional solo en el cierre, no existen copiar/dividir/fusionar con linaje declarado, y un
  `kimun.include.edn` que declara la ruta vieja hay que cambiarlo a mano.
- **Una ruta, un documento.** Registrar un documento nuevo —tipado o genérico— sobre un archivo cuya
  ruta tiene otro es `docs/exists` (3) y nombra al que la tiene.
- **Archivo fuera del proyecto.** La API admite `--store` desde otro directorio y guarda una ruta
  relativa con `..`. El mapa de rutas rechaza una ruta así (`kimun.kernel.route/valid-path?`), de
  modo que ese documento sigue funcionando **sin ruta** y `stores check` lo declara
  (`:routes-outside`, warning `routes/outside-project`). Admitir `..` en el mapa cambiaría una
  guarda del kernel; queda como decisión aparte.
- La ruta es **localizador, no identidad**: nada aquí decide qué pasa con un documento cuyo archivo
  se movió por fuera (sigue siendo `source/missing`).

**Raíz parcial (stores anteriores a las rutas).** Un documento registrado antes de C2 no tiene
ruta, y la raíz cubre solo lo escrito después. Eso **se declara, nunca se informa como completo**:
`stores check` da `:routes-complete? false`, `:routes-missing [rutas]` y el warning `routes/partial`
con cuántos documentos cubre la raíz; el store sigue válido. `:routes-complete?` también es false
mientras haya documentos en `:routes-shared` o `:routes-outside`: la raíz no los cubre. Cada documento gana su ruta al
volver a registrarse, aunque su archivo no haya cambiado: `docs update NAME` (tipado) o
`docs update FILE --name NAME` (genérico). `include build --rebuild` la completa de una vez.

**Pendiente, tarea C4:** no hay todavía un comando que registre de una vez las rutas que faltan en
un store existente sin reconstruirlo (por ejemplo dentro de `stores update`), y `stores sense` /
`heartbeat` no informan la cobertura: solo `stores check`.

**Esquema del mapa (K2a, corte C5; `10 §11.2`).** Un store nuevo guarda el mapa como **árbol
Merkle por ruta** (esquema 2): una escritura reescribe solo los nodos del camino que tocó (cada uno
entero: un directorio ancho cuesta en proporción a sus hijos, `10 §11.2`), abrir lee solo el nodo
raíz y dos raíces se comparan bajando por donde difieren. `stores check`
informa `:route-count` y `:routes-root`. Un nodo dañado del árbol es `route/corrupt` (5) al leer esa rama.

El objeto del mapa de rutas (en esquema 2, su nodo raíz) es, junto con la revisión de cabecera y la raíz de cabeceras por árbol,
uno de los tres que la apertura misma verifica: dañado o ausente, todo comando falla con
`store/corrupt-head` (5).

Límites: los documentos `:inline`, los sin archivo y los no tipados con árbol persistido no tienen
ruta ni cuentan para la cobertura.

#### Documentos sin modelo: clase genérica (K3)

`14 §18.1`: no hay documentos sin clase. Un archivo ingerido sin modelo se registra como un
documento tipado con archivo (`10 §11`): entra al store su **anclaje externo** (ruta relativa y
fingerprint, con el contrato de parse de K6), la **clase** `generic/md` en el descriptor del árbol y
el **hash raíz**; el árbol y sus bloques son **caché**, recalculable desde el archivo. Antes de K3 se
persistía el árbol entero y el archivo no quedaba anclado.

- Vale para stores por referencia (el defecto) con el archivo dentro del proyecto. En un store
  `:inline`, o con un archivo fuera del proyecto, el árbol se persiste como antes.
- El documento **queda sensorizado**: `stores check|health|sense|heartbeat` lo cuentan en `:tracked` y
  dan `source/missing`, `source/mutated` (cambió la estructura), `source/reformatted` (cambió el texto,
  no la estructura) y las señales de contrato de K6. El finding lleva `:generic true` y `:model nil`.
  `stores update` vuelve a registrar lo que no requiere decisión, sin reescribir el archivo.
- **Una ruta, un documento**: ingerir un archivo que ya ancla otro documento (tipado o genérico), o
  reanclar sobre él, es `docs/exists` (3). Es unicidad del localizador, no identidad; coincide con el
  rechazo de rutas duplicadas del registro de rutas de K2a.
- `docs list` lo muestra entre los no tipados con `:class "generic/md"`. No es un documento tipado:
  `docs show NAME` (sin `--ast`), `fields`, `sections` y `find` siguen sin verlo, igual que antes.
- El nombre sigue siendo único entre documentos sin modelo.
- `docs untrack NAME` y `docs delete NAME --yes` valen también para estos documentos (por nombre,
  ruta o id de árbol): se retira el anclaje, el archivo deja de sensorizarse y su ruta y su nombre
  quedan libres; el árbol queda en la historia, oculto.
- Tipados y genéricos se resuelven **juntos** (regla de K8): un alias que comparten un documento
  tipado y uno genérico es `docs/ambiguous` (exit 2) con ambos candidatos (el genérico lleva `:class`
  en vez de `:model`), en `docs show|render|update|untrack|delete`. El nombre cualificado o el id de
  árbol los distingue; el diagnóstico nombra al genérico por su id de árbol si el alias es compartido.

Compatibilidad: un documento sin modelo ingerido antes de K3 conserva su árbol persistido y se lee
y actualiza como antes; no hay migración automática (se vuelve a ingerir con otro nombre o tras
`docs untrack`). No cambia la versión de formato.

Fuera de este corte: el **perfil de indexación por tipo de archivo** y su granularidad por store
(`14 §18.5`) no existen todavía: `generic/md` es el árbol SLDB-MD completo, y otros tipos de
archivo esperan al spec 16 (M1). `include` sigue sin admitir documentos sin modelo (`13`).

| subcomando | opciones | `:data` y notas |
|---|---|---|
| `docs ingest FILE` | `--name N` | `{:name :tree :path :blocks}` más `:class "generic/md" :anchor <ruta relativa>` cuando se registra bajo la clase genérica (K3, abajo); `docs/exists` (3) si el nombre ya existe o el archivo ya está trackeado |
| `docs update FILE --name N` | — | reingesta de un documento sin modelo (edición externa); si está registrado bajo la clase genérica vuelve a registrar el archivo y añade `:class :anchor :unchanged?` |
| `docs update NAME [FILE]` | `--data F`, `--set k=v` | reingesta/edición parcial de un documento tipado; `:unchanged?` |
| `docs render NAME\|TREE` | — | Markdown canónico como string de `:data` (`--format text` lo imprime crudo) |
| `docs list` | `--model M` | untyped `{:name :tree :root}`; typed añade `:model :model-version :state :path` |
| `docs create MODEL NAME` | `--data F` \| `--set k=v…`, `--path P`, `--inline` | mapa del documento (con payload, hashes, path); `--inline` guarda el texto en el store |
| `docs track MODEL FILE` | `--name N`, `--force`, `--inline` | mapa del documento; `--force` para un fichero que no ida-vuelta; `--inline` guarda el texto en el store Con una clase spec 16 (M1g, sin `:template`): extrae con el selector compuesto de la clase (propio + heredado por `:base`); no hay ida-vuelta ni ruido que comprobar (no hay renderizador, M2); `docs create` y `docs update --data/--set` se rechazan por lo mismo; las mutaciones de `fields` y `sections fields` también se rechazan; `stores health/sense/check` y `find` sí leen el documento |
| `docs show NAME` | `--format md`, `--ast` (equivale a `--format ast`) | mapa con `:markdown` y, en un documento tipado, `:units [{:field :id :status}]` (`anchored \| missing`, K1c); `--ast` ⇒ `{:name :tree :ast}`. Un documento de clase spec 16 añade `:class` (dirección del descriptor con el que se ancló) y `:ancestors` (direcciones de los ancestros con los que se compuso su selector); **[inferencia del equipo]** la cadena se fija por revisión exacta: si se redefine la clase o un ancestro, el documento sigue leyéndose bajo la cadena registrada y pasa a `:superseded` hasta `docs update`. Un documento v1 no lleva esas claves |
| `docs anchors NAME` | `--sections` | anclas estructurales del documento como **evidencia, sin identidad** (S2a): `{:name :tree :path :parse-contract :sections [{:locator :level :hash :lines :bytes :blocks}] :blocks [{:locator :hash :lines :bytes}]}`; `--sections` omite `:blocks`. Solo lectura: no escribe store ni caché y no acuña ids. Tipado o genérico |
| `docs mark NAME` | `--locator L` o `--field F` con `--index N` / `--path a.b`, `--label TEXT`, `--unit ID`, `--reason TEXT` | **declara** una unidad estructural (sección o bloque) de un documento por referencia: sin `--unit` acuña su id; con `--unit` re-ancla la **misma** unidad (el `--reason` es entonces obligatorio). `L` es el localizador que imprime `docs anchors` (EDN o JSON). Con `--field F --index N` (ítem de un campo lista) o `--field F --path a.b` (clave anidada de un campo estructurado, claves separadas por `.`; una clave con punto se da con `--locator '{"field":F,"path":[…]}'`) marca un valor **de un documento tipado** (K1h; localizador `{:field :index}` / `{:field :path}`, `:kind :item` / `:nested`); un documento genérico no tiene campos (`docs/not-anchored`). Con `--locator B --range S:E` (B = localizador de un **párrafo o encabezado**) marca un **rango de grafemas `[S E)` del texto plano NFC** de ese bloque (S2b-r; localizador `{:block B :range [S E]}`, `:kind :span`, huella del subtexto); un rango vacío o mal formado es `cli/usage`, y uno que pasa del texto o un bloque sin texto plano (código, lista, opaco) es `docs/anchor-unresolved`. Data: el documento más `:marked {:kind :locator :hash :label}` y `:id`. Rechazos sin revisión: `docs/anchor-unresolved`, `docs/exists` (localizador ya marcado), `docs/missing` (unidad desconocida), `docs/not-anchored`, `cli/usage` |
| `docs unmark NAME ID` | `--reason TEXT` | **declara** el cierre de la unidad `ID`: la marca sale del descriptor. Con la id de una **unidad de campo** (K1f) el `--reason` es obligatorio (`cli/usage`), la id sale de `:units` y no se reutiliza; un campo aún presente recibe una id nueva en la escritura siguiente. Data: el documento más `:unmarked` |
| `docs mark OTHER --unit ID --from NAME` | `--locator L` o `--field F`, `--reason TEXT` | **declara el traslado** (K1f) de la unidad `ID` de NAME a OTHER con la misma id, en una sola revisión. `--locator` traslada una marca (documentos tipados o genéricos); `--field` una unidad de campo (dos tipados; `F` es clave del payload de OTHER y no tiene unidad). Rechazos sin revisión: `cli/usage` (sin `--reason`, ni uno ni ambos de `--locator`/`--field`, mismo documento), `docs/exists` (localizador o campo ya con unidad: no se fusiona), `docs/missing`, `docs/anchor-unresolved`, `docs/not-anchored`. Data: el documento de OTHER más `:id` y `:moved` |
| `docs marks NAME` | — | estado derivado de las marcas: `[{:id :kind :label :locator :status :candidates}]` por id y `:parse-contract`. Estados `anchored \| mutated \| ambiguous \| missing`; los candidatos son **propuestas** (`:locator`, `:reason :locator\|:hash`), nunca una decisión. Solo lectura |
| `docs reanchor NAME FILE` | `--reason TEXT` (obligatorio) | declara el ancla nueva conservando ids: `:reanchored {:from :to :previous-file :reason}` + warning si el archivo viejo sigue |
| `docs untrack NAME` | `--reason TEXT` | desrastrea; `:closure {:file :reason}` |
| `docs delete NAME` | `--yes`, `--reason TEXT` | desrastrea y borra el fichero; sin `--yes` ⇒ `docs/confirmation-required` (3) |
| `docs recover DOC` | `--depth N`, `--links-only`, `--include-transclusions` (aceptada e ignorada) | `{:root :path :links [...] :unresolved [...]}`; enlaces sin resolver ⇒ `links/missing` (1) |
| `docs compose DOC` | `-o FILE\|-` | Markdown con transclusiones expandidas; sin resolver ⇒ `links/missing` (1) |

**Contrato de lectura (A1, 2026-10-08).** Un documento tipado recuerda el descriptor con el
que se escribió (el `:contract` de la evidencia de su arista de fuente); `docs show|list`,
`fields`, `sections`, `find`, `edges` y `stores check|health` lo leen bajo ESE descriptor
hasta que se actualiza, así que redefinir un modelo no cambia su payload en silencio.
`docs update NAME` (sin FILE ni `--data`/`--set`) y `stores update` mueven el documento al
contrato actual re-renderizando su payload registrado (y reescriben su fichero);
`models define|field-add … --backfill` hace lo mismo en masa. Un documento sin `:contract`
(anterior a A1 o migrado de v1) cae al contrato actual del modelo.

### include

Declaración en `kimun.include.edn`; detalle en `13`.

| subcomando | opciones | notas |
|---|---|---|
| `include check` | `--staged` | deriva entre declaración, git y store; `include/drift` (3) |
| `include gitignore` | `--write` | sin `--write` imprime el bloque gestionado en vez de escribirlo |
| `include hook` | `--install` \| `--uninstall` | hook pre-commit; encadena uno ajeno |
| `include build` | `--rebuild` | construye el store; `--rebuild` mueve el existente a `<store>.bak-<marca>-<ulid>` (nombre único; nunca pisa un respaldo anterior) y **arrastra la identidad de los documentos** (id de documento e id de árbol, leídos del store viejo por ruta cualificada antes de moverlo): :identities {:source :available :carried :minted :units-carried :models {:tree-carried :kept :advanced :new} :models-not-carried :not-carried :ambiguous :without-file}` (`:source` es el `.bak` del que se leyeron; `:without-file` cuenta los documentos sin archivo, que no tienen ruta por la que seguirlos). Lo que no se puede seguir se declara: warning `include/identities-not-carried` (rutas que ya no están, documentos que la declaración no construye —también los de clase genérica ingeridos a mano— o duplicadas) y `include/identities-unavailable` si el store viejo no se pudo abrir (el rebuild sigue con ids nuevos). Los modelos son **clases**: el rebuild conserva el id del árbol `models` y la versión y el contrato de cada modelo (`:kept` misma versión, `:advanced` contrato distinto ⇒ versión + 1, `:new` nombre nuevo); un modelo que la declaración ya no define se nombra en `:models-not-carried` y en el warning `include/identities-not-carried`. La dirección del descriptor (su hash) es la de un valor, no la identidad de una unidad: si un modelo pasa a ser unidad del grafo lo decide `M1`. Límite: el **historial** de versiones (`models history`) no se arrastra, ni el historial de documentos (`14 §21`, K1a, K1d) |
| `include status` | — | recuentos de la declaración |

### models

Validación del descriptor (tipos, bases, `:state`, template, `py•` rechazado) en `07`.

| subcomando | opciones | notas |
|---|---|---|
| `models define` | `-f X.edn`, `--dry-run`, `--backfill` | nombre nuevo ⇒ `:add-node`; existente ⇒ versión+1 y `supersedes`; el descriptor es v1 (`:template`) o spec 16 (`:anchoring`), nunca ambos; `:document-role :generated` se rechaza (F10) |
| `models field-add` | `NAME --field F --type T --description TEXT [--default D] [--required]` | nueva versión del descriptor |
| `models field-remove` | `NAME --field F` | nueva versión del descriptor |
| `models template` | `NAME [-f FILE]` | sin `-f` muestra el template; con `-f` lo reemplaza (nueva versión) |
| `models list` | `[--isa BASE]` | `[{:name :version :id :fields :documents :not-intact}]`; `--isa BASE` filtra a BASE y sus subclases transitivas por `:base` (spec 16 §4.1) |
| `models show` | `NAME [--format md]` | descriptor, bases, template, historial, documentos que lo referencian |
| `models history` | `NAME` | versiones (cadena `supersedes`) |
| `models check` | `[NAME] [-f DRAFT.edn]` | valida descriptores y documentos; `models/*` (3) |

### extract · render · validate (modo directo, sin store)

El modelo es `--model X.edn | NAME del store | module:Class`, o el primer token posicional.

| comando | opciones | `:data` |
|---|---|---|
| `extract [MODEL]` | `--input FILE`, `-o FILE` | `{:model :payload}` (+`:output` si se escribió) |
| `render [MODEL]` | `--data FILE`, `-o FILE` | Markdown (o `{:model :output}`) |
| `validate [MODEL]` | `--input FILE` \| `--data FILE`, `--mode render-validity\|strict-roundtrip` | `{:ok true :model :mode :errors []}`; no valida ⇒ `validate/invalid` (3) |

Un campo `rev` ausente es `validate/missing` (3), nunca vacío silencioso.

### find

`find TERM` | `--in physical|semantic|all` (v1 `both` se acepta) | `--type KIND[,KIND]` (`store model doc section field tag`) | `--global` | `--regex` | `--fuzzy` (Dice ≥ 0.7) | `--where EXPR` | `--select K,…` | `--limit N`.

`:data` es `[{:store :tree :name :model :path :kind :matches …}]`, ordenado por store y tree-id. Sin resultados ⇒ `find/empty` (1); `--where` inválido ⇒ `query/bad-where` (3); `--regex` y `--fuzzy` se excluyen.

Formas de `--where`: `[:has f]`, `[:in x f]`, `[:re f pat]`, `[:model<= Base]`, `[:cmp op f v]` con `op ∈ = != >= <=`, combinadas con `:and`/`:or`/`:not`. `--global` recorre los `:links` y cada resultado lleva su `(store, tree-id)`.

Direcciones de `TERM`: `st.{Store}.{Model}.doc.field`, `st.{Base+}`, `se.<tag>`, `gse.<tag>`.

### corpus

| subcomando | opciones | `:data` |
|---|---|---|
| `corpus rank QUERY…` | `--k N` (5), `--threshold X`, `--among ID,…`, `--fields`, `--refresh` | `{:query :fields :hits [{:id :model :name :score :payload :tags …}]}` |
| `corpus refresh` | — | `{:stores [{:store :head :documents :fields}]}` |

### rebuild-indexes

Sin opciones ni subcomandos. Escribe el índice derivado de la cabecera bajo `.kimun/index/<head>.edn`. `:data` incluye `bytes cache complete documents head models pruned skipped tags revision`.

### edges

El grafo es la vista derivada del store en su cabecera; `init` y `rebuild` solo informan. Sin `--local`, `show`, `node` y `nodes` incluyen los stores enlazados alcanzables, sus documentos y secciones calificados por el nombre de enlace (`sldb://document/annex:Table:t99`, `semantics.store`); `--local` los deja fuera.

| subcomando | opciones | `:data` |
|---|---|---|
| `edges show` | `[ID \| --from ID \| --to ID] [--relation R] [--no-links] [--exclude-tag TAG] [--local]` | `{:from :to :relation :links :count :edges …}` |
| `edges check` | `[--no-links] [--exclude-tag TAG]` | validación del grafo local (no federa) |
| `edges node ID` | `[--local]` | `{:id :type :semantics :facets :edges}` |
| `edges nodes` | `--type T [--local]` | `{:type :count :nodes [{:id :type}]}` |
| `edges types` | — | `{:count :types …}` |
| `edges relate` | `FROM TO --relation R` | `{:changed :relation :from :to :anchor}` |
| `edges unrelate` | `FROM TO --relation R` | igual |
| `edges type-define NAME` | `--direction --cardinality --source-types --target-types --condition --description` | `{:changed :type}` |
| `edges init` \| `edges rebuild` | — | no-op informado: `{:changed false :derived true :message … :nodes :edges …}` |

### predicates

| subcomando | opciones | `:data` |
|---|---|---|
| `predicates add NAME` | `--axis A [--description D]` | `{:changed :predicate {:name :axis :description}}` |
| `predicates list` | — | `{:count :predicates […]}` |
| `predicates show NAME` | — | la definición |
| `predicates validate [NAME]` | — | validación |
| `predicates remove NAME` | — | `{:changed …}` |

### graph

Lecturas puras sobre el grafo (`--snapshot FILE` lee un snapshot en vez del store). Salvo `--snapshot`, `--local` limita la lectura al store actual; sin él, los stores enlazados alcanzables entran calificados por nombre de enlace (v1). Nada se escribe salvo el fichero de `snapshot save`, `export -o` e `ingest-sldb -o`; las rutas de salida no son absolutas.

| subcomando | opciones | `:data` |
|---|---|---|
| `graph get ID` | `--snapshot F` | `{:id :type :semantics :facets :edges}`; desconocido ⇒ `graph/missing` (1) |
| `graph list` | `--type T` | `{:count :nodes [id]}` |
| `graph query` | `-f FILE.json` | `{:count :nodes […]}`; consulta malformada ⇒ `graph/bad-query` (3) |
| `graph neighborhood ID` | `--depth N` (1), `--direction both\|incoming\|outgoing`, `--relation R` | `{:node :depth :direction :nodes [id]}` |
| `graph path FROM TO` | `--directed`, `--relation R \| --all-relations` | `{:from :to :length :path [id]}`; sin ruta ⇒ `graph/empty` (1) |
| `graph cycles` | `--limit N` (10), `--relation R` | `{:count :cycles [[id]]}` |
| `graph order` | `--relation R` | `{:order [id] :layers [[id]]}`; ciclo ⇒ `graph/cycle` (3) |
| `graph components` | `--type T`, `--relation R` | `{:count :components [[id]]}` |
| `graph central` | `--metric pagerank\|betweenness\|degree\|in_degree\|out_degree` (pagerank), `--limit N` (20), `--kind` (nombre v1 de `--metric`), `--type T` | `{:metric :nodes [{:id :score}]}` |
| `graph isolated` | `--type T`, `--relation R` | `{:count :nodes [id]}` |
| `graph similar ID` | `--limit N` (10), `--type T`, `--relation R` (por defecto `tagged_as`) | `{:node :nodes [{:id :shared}]}` |
| `graph snapshot save FILE` | `[--local]` | `{:path :nodes :edges :types :relations}` |
| `graph snapshot load FILE` | — | igual (no abre store) |
| `graph export` | `-o FILE [--local]` | node-link (`{"directed" "multigraph" "graph" "nodes" "links"}`) o resumen |
| `graph ingest-sldb FILE` | `-o OUT` | convierte un export semántico (contrato `sldb_kgdb_semantic_export` v2) |

### fields

Campos de documentos tipados. `DOC` es nombre, tree-id o `Model:doc`; un campo de modelo se direcciona `models/Model[/field]`.

| subcomando | opciones | notas |
|---|---|---|
| `fields get DOC PATH` | — | valor con su sección (alias `show`) |
| `fields schema MODEL [FIELD]` | — | esquema del campo |
| `fields query FIELD` | `--where EXPR`, `--model M` | un campo a través de los documentos |
| `fields set DOC PATH VALUE` | — | crea si falta (alias `create`; `update` exige existir) |
| `fields unset DOC PATH` | — | elimina campo o elemento de lista (alias `remove`) |
| `fields append DOC PATH VALUE` | — | añade a una lista |
| `fields clean DOC PATH` | `--dedupe`, `--drop-empty` | limpia una lista |
| `fields clear DOC PATH` | — | vacía una lista |

### sections

| subcomando | opciones | `:data` |
|---|---|---|
| `sections list DOC` (alias `show`) | — | `{:document :model :path :sections [{:path :title …}]}` |
| `sections find [TERM]` | `--in physical\|semantic\|both` (both), `--regex`, `--fuzzy`, `--where EXPR` | `{:term :results [{:kind :store :name …}]}` |
| `sections fields DOC SECTION` | — | `{:document :section :fields [{:field_path :value …}]}` |

### semantic

| subcomando | opciones | `:data` |
|---|---|---|
| `semantic show [TAG]` | — | un tag (`{:tag :parents :children :below :above :equivalents :documents}`) o el DAG (`{:roots :nodes :equivalences}`) |
| `semantic tags [PREFIX]` | — | `{:prefix :tags :documents}` |
| `semantic parent-add CHILD PARENT` | — | `{:changed …}` |
| `semantic parent-remove CHILD PARENT` | — | `{:changed …}` |
| `semantic equivalent-add A B` | — | `{:changed …}` |
| `semantic export` | `-o FILE` | payload (productor `kimun`, rutas relativas) |

### declare

| subcomando | opciones | `:data` |
|---|---|---|
| `declare read` | `[FILE…]` (rutas relativas al cwd; sin ellas, los `*.clj` bajo `kimun/` del cwd, recursivo y por ruta; sin carpeta o vacía, respuesta vacía y `:ok true`; sin store) | `{:files [{:file :context :relations :propositions}] :summary {:files :relations :propositions}}`. Evalúa los archivos como **un conjunto** (Clojure aislado en `sci`, `docs/v2/drafts/diseno-evaluar.md`): un contexto puede traer relaciones de otro del mismo conjunto con `(ns b (:require [a :refer [isa]] [c :as c]))`, evaluados en orden de dependencias; `:r` es `"contexto-que-la-define/nombre"` y muestra las proposiciones `{:r :a :b :context :at {:file :line} :id}` (`:r` y `:context` cadenas; `:a` y `:b` nodos etiquetados, iguales en JSON y EDN: `{:term "x"}`, `{:proposition id}`, `{:anchor …}`, `{:files …}` o un literal escalar; un vector o mapa como argumento es `declare/bad-node`); no escribe nada. Archivo ausente ⇒ `declare/missing` (1); un archivo que no evalúa ⇒ `declare/*` (3, p. ej. `declare/relation-redefined`, y del conjunto `declare/unknown-context`, `declare/duplicate-context`, `declare/require-cycle`, `declare/unknown-relation`) con `:file` y `:line` |
| `declare anchors` | `[FILE…]` (como `declare read`: sin archivos, el proyecto `kimun/`), `--since COMMIT`; sin store | `{:files :anchors [{:proposition :role :r :other :anchor {:path :locator} :kind :declared-at {:file :line} :status :candidates :opened :hash? :before? :reason? :why?}] :summary {:files :anchors :opened :by-status} :since :pointer :head}`. Evalúa los archivos y, para cada `(at …)` usado como `:a` o `:b`, lo resuelve contra los archivos del proyecto (el cwd) con los localizadores de `kimun.surface.markdown.anchors` (formas en `kimun.surface.declared-anchors`: `(at "r")` archivo entero, `[títulos]` sección, `{:type :n}` bloque, `[inicio fin]` rango; `:n` y los ordinales de título son 1-based). Sin comparación (`:since` nil): `:resolved \| :unresolved \| :file-missing`. Con `--since`, o con el puntero válido de `.kimun/seen.edn` en el cwd (`:pointer`), git dice qué archivos cambiaron y solo esos se abren: `:anchored \| :mutated \| :ambiguous \| :missing` (con `:candidates`, sin elegir) o `:unknown` con `:reason`; un archivo no cambiado sale `:anchored` con `:opened false`. Un localizador interno sobre un archivo que no es `.md` es `:unsupported`. Localizador mal formado ⇒ `declare/bad-locator` (3) con `:file` y `:line`; errores `git/*` como `stores changed`; no escribe nada |
| `declare add` | `--to ARCHIVO`, `--context NOMBRE`, `'FORMA'` | agrega el texto de FORMA (una forma, en una línea nueva al final) a un archivo de declaración; con `--context` y sin el archivo, lo crea con su `(ns NOMBRE)`; sin archivo ni `--context`, `cli/usage`. Antes de escribir evalúa en memoria el conjunto con la forma (el proyecto `kimun/` si ARCHIVO cuelga de él, si no ese archivo solo): si no evalúa devuelve el error `declare/*` y no escribe; si la forma no aporta proposición ni relación nuevas, `:added false` con `:reason` y no escribe. Escritura atómica (temporal + renombrado) y solo de ese archivo: es la única escritura del grupo. Data `{:file :added :propositions [las nuevas] :relations [nombres nuevos]}` |

### serve

`serve [start] [--port N] [--host H] [--token T] [--cors]`. Bloquea hasta interrupción; `--token` o `$KIMUN_SERVE_TOKEN` exige `Authorization: Bearer TOKEN`; def. `127.0.0.1:8787`.

### Recorrido observable de una mutación

`bash scripts/tour_mutation.sh` demuestra con la CLI, en un proyecto temporal,
que kimun ve una mutación y la lleva hasta quedar reconciliada: reposo →
archivo editado (`source/mutated`, health `needs-human` con `:next` de
inspección) → consulta de lo que cambió y **decisión explícita que el guion simula**
(ninguna persona revisa el cambio; ejecuta una de las `:actions`) → `ok` → la misma fuente bajo otro parser (`contract/changed`, health
`degraded` con `:next "run: kimun stores update"`: reparación sin decisión) →
`stores update` → `ok` → archivo movido (`source/missing`; la ausencia no termina
la unidad) → `docs reanchor` (mismo `:tree`) → dos
latidos. Comprueba con sha256 del árbol del store que `stores sense`,
`stores health` y `stores heartbeat` no escriben. Requiere el pod tree-sitter para
el tramo K6 (sin él lo omite y lo dice). No promete la vista del circuito más
allá del nivel documento, ni unidades debajo del documento, ni que kimun detecte
movimientos: el sensor informa ausencia y el actor declara. Evidencia:
`runs/subagents/2026-11-15-TV1/` (transcript y prueba `tour-test`).

## 4. Rutas de `serve`

Tabla de rutas como datos en `src/kimun/surface/http/routes.cljc`; cada ruta delega en un comando de §3. Familias:

- `GET /health /schema /stores /stores/check /stores/links /stores/sense /stores/health /docs /document /document/ir /document/segments /ast /lint /fields /sections /find /extract /models /models/detail /render /graph /edges /edges/node /edges/nodes /corpus/rank`.
- `/stores/sense` y `/stores/health` son lecturas puras: devuelven el envelope de `stores sense` / `stores health` sin transformarlo (no escriben en el store ni en `cache/`). `stores heartbeat` no tiene ruta: es un flujo NDJSON, no un envelope.
- `GET /graph/{cycles,components,central,isolated,neighborhood,path}` y `GET /similar`.
- `POST /save /docs /docs/create /docs/ingest /docs/track /docs/untrack /extract/segments /models/define /models/fields-add /models/fields-remove /models/template-edit /models/validate`.

## 5. Mapeo sldb v1 → kimun

| v1 (sldb) | kimun | nota |
|---|---|---|
| `stores init` | `stores init [--name]` | `init` es explícito y falla si ya existe (`cli/store-exists`, 5) |
| `stores add`, `store update` | `stores link NAME PATH` | enlaces federados con ruta relativa al store |
| `stores list`, `store show` | `stores show` | descriptor y cabeceras sin replay |
| `stores check` | `stores check` | abre por replay; findings como warnings |
| — | `stores verify` | nuevo: recomputa hashes y verifica el replay |
| — | `stores health`, `stores update`, `stores reconcile [--apply]` | nuevos: diagnóstico, reparación sin decisión, federación |
| `stores semantic-map` | `rebuild-indexes` | solo reconstruye bajo orden; leer nunca escribe |
| `stores semantic-export`, `query` | `graph export\|snapshot\|query`, `semantic export` | export con rutas relativas, `producer kimun`, contrato v2 |
| `models add\|update` | `models define` | modelo = datos en el árbol, no clase Python |
| `models list\|show\|validate\|template\|schema\|fields` | `models list\|show\|check\|history\|template\|field-add\|field-remove` | |
| `extract`, `render`, `validate` | mismos | CST/AST, sin store en modo directo |
| `docs create\|track\|update\|untrack\|show\|list\|recover\|compose` | mismos | más `docs ingest\|render\|delete\|status` |
| `fields show\|query\|clean`, `doc … fields` | `fields get\|schema\|query\|set\|append\|unset\|clean\|clear` con alias `show\|create\|update\|remove` | un solo parser de `--where` |
| `sections show\|find\|fields` | `sections list\|find\|fields` | las secciones salen del Markdown, sin índice persistido |
| `predicates add\|list\|show\|validate\|remove` | `predicates` (mismos) | predicados como nodos del árbol `relations` |
| `edges …` | `edges …` | grafo como vista derivada |
| `graph …` | `graph …` | |
| `find`, `serve` | `find`, `serve` | |

Retirados, no portados: `explore`, `faq`, `inbox`, `ast show|schema` (→ `docs show --ast`), `lint`, `legacy ls|get|glob|raw-find`, textos de `help` hardcodeados (→ `--help` genérico), `project init|example`, la materialización de kgdb (`project`), y las banderas `--kgdb-output`/`--kgdb-command`.

## 6. No implementado

- **Evaluador anclado** (S4/S5 de la planificación retirada): un primer token desconocido devuelve `eval/not-available` (6). No existen `eval`, `next`, `check`, `rel`, `anchors`, `anchor add`, ni el grupo `derive`; tampoco hay `.kimun/session.edn` ni outbox. Diseño en `docs/archive/v2/09-evaluador-anclado.md`.
- **Cliente Python y wheels** (tarea `C2` diferida el 2026-11-15, decisión 47 del dueño: el wrapper Python se agrega después de `RS1` y `RS2`, `12 §1`): no existe código Python en el repositorio (`python/` se borró el 2026-11-15) ni la task `bb wheel`.
- **Motores de derivados**: las capacidades `kimun/derive` y `kimun/materialize` se registran al crear el store, pero ningún comando las ejercita todavía.
