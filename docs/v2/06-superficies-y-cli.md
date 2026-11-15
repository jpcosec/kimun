# kimun — Superficies y CLI

> Inventario real de la CLI de `kimun`: grupos, subcomandos, opciones, forma de `:data` y
> códigos de salida. Cada afirmación de aquí corresponde a `src/kimun/cli/**`
> (`main.cljc`, `args.cljc`, `out.cljc`, `errors.cljc`, `store.cljc`, `commands/*`).
> Autoridad según `docs/v2/README.md`. El kernel es `docs/v2/02`; los modelos, `07`; la
> distribución, `08`; los formatos del store y la escala, `10`; los adaptadores, `11`; la inclusión en
> git, `13`. Los anillos y reglas de dependencia son `03`. La paridad medida con sldb v1 es
> `docs/v2/tests/parity-v1.md`.

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

Códigos de salida (`kimun.cli.errors`): 0 ok, 1 `missing`/`empty`, 2 `ambiguous`, 3 rechazo de entrada, 4 `conflict`/`stale`, 5 store/journal/migrate/serve/adapters (runtime), 6 uso, 70 inesperado. La tabla explícita:

| exit | cuándo |
|---|---|
| 0 | ok |
| 1 | `*/missing` o `*/empty` por nombre (`find/empty`, `graph/empty`, `links/missing`, `docs/missing`, `models/no-file`, `eval/missing`) |
| 2 | `*/ambiguous` |
| 3 | namespaces `query` `models` `validate` `docs` `fields` `sections` `find` `graph` `edges` `semantic` `include` `links`; `:plan/rejected` salvo chequeo de capability |
| 4 | `*/conflict`, `*/stale`, `plan/conflict`, `store/stale`, `store/busy` |
| 5 | namespaces `store` `route` `markdown` `index` `journal` `migrate` `serve` `adapters`; `cli/no-store`, `cli/store-exists` |
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

- `stores upgrade` reescribe un store formato 1 como formato 2 (backup `<store>.format-1-<marca>`, mapa `upgrade-format-1.edn`); un store formato 2 pasa a 3 en el sitio; un store formato 3 reporta `:needs-rebuild` y nombra `include build --rebuild` (formato 4). Un store formato 4 conserva su formato; si su mapa de rutas es de esquema 1 pasa a esquema 2, el árbol Merkle por ruta (`10 §11.2`), y si ya lo es no hace nada.
- Los enlaces (`:links`) son `{:name :path}` con `:path` **relativo al store**; un enlace absoluto o duplicado es `store/invalid-links` (exit 5). `stores link NAME PATH` enlaza un `.kimun` o su proyecto; `stores links` informa `ok | missing | not-a-store`.
- Capacidades por defecto al crear: `{actor :all "kimun/derive" :all "kimun/materialize" :all}`.
- `index/` es derivado: leer nunca escribe; `rebuild-indexes` lo reconstruye bajo orden.

## 3. Grupos de la CLI

### stores

| subcomando | opciones | `:data` y notas |
|---|---|---|
| `stores init` | `--name NAME`, `--content by-reference\|inline` | `{:path :name :actor :capabilities}`; `--content inline` escribe `:content :inline` en `store.edn` (docs/v2/10 §10); falla `cli/store-exists` (5) si ya hay store |
| `stores check` | — | `{:name :path :hash-alg :revisions :trees :objects :head :links}` + `:routes-root :route-count :routes-complete? :routes-missing :routes-shared :routes-outside` si hay documentos registrados con archivo (abajo, «Rutas») + `:documents` si hay findings; abre por replay; store inválido ⇒ `store/invalid` (5) | Warning `cache/corrupt-pack` cuando un pack de `cache/packs` no se puede cargar (se ignora; nunca exit 5).
| `stores verify` | — | `{:checked :bad}`; verify-full (replay + hash); corrupción ⇒ `store/corrupt` (5) |
| `stores unreachable` | `[--ids]` | inventario **read-only** de los objetos de `objects/` que las cabeceras no alcanzan (`10 §11.3`): `{:note :stored :reached :unreachable :candidates {:count :bytes :by-class} :roots {…} :lost-revisions [id] :held {…} :damaged [{:id :reason}] :stable?}` + `:uncommitted-tail` si la hay; `--ids` agrega `:ids {:candidates [{:id :class :root? :bytes}] :held […]}`. No escribe ni borra nada; no existe comando que borre. Un candidato **no** es basura segura: `:note` lo dice en cada respuesta. Warning `unreachable/unstable` si las cabeceras se movieron durante el inventario |
| `stores pack` | `[--max-bytes N] [--cache]` | mueve los objetos sueltos menores que N (defecto 4096, máximo 16 MiB) a `packs/<name>.pack` y borra los sueltos (`10 §11.4`): `{:path :packed :pack :bytes :kept-loose :skipped-damaged :packs :checked :indexes-repaired}`; `:indexes-repaired` cuenta los `.idx` que reescribió (lectores no escriben: un `.idx` ausente o raro se repara aquí, y puede ser 1 con `:packed` 0). Verifica después; un fallo es `store/corrupt` (5) y recuerda que los sueltos ya no están (`stores unpack`). `--cache` empaqueta la **caché derivada** (`cache/objects` → `cache/packs`; verifica por lectura, ignora en memoria un pack malo, borra sueltos dañados y duplicados) y nunca toca el store; `:scope` dice cuál. Solo formato 4; otro formato es `store/error` (5); `--max-bytes` inválido es `cli/usage` (6) |
| `stores unpack` | — | escribe de vuelta cada objeto empaquetado y borra el pack: `{:path :packs :unpacked :checked}`; verifica después (`store/corrupt` 5 si falla). Solo formato 4 |
| `stores upgrade` | — | `{:upgraded :format-version :path …}`; 1→2 reconstruye en un directorio hermano (`:transactions :backup :map`), 2→3 convierte en el sitio (líneas `{:id :revision}`, `:dropped-plans`), 3→4 reporta `:needs-rebuild true :action "kimun include build --rebuild"` (el registro como caché exige los archivos); formato 4 con rutas de esquema 1 pasa a esquema 2 en el sitio (`:from-route-schema 1 :route-schema 2 :routes :routes-root-before :routes-root :route-nodes :checked`), y con esquema 2 responde `:upgraded false :route-schema 2`; carrera de escritura ⇒ `store/stale` (4) |
| `stores show` | — | `{:name :path :hash-alg :links :head :capabilities}`; sin replay |
| `stores health` | — | `{:status :summary :findings :actions :next}`; `ok | degraded | needs-human` |
| `stores sense` | `[--once]` | una observación read-only: `{:mode :once :observed-at :tracked :valid :signals :counts :summary}`; reusa `kimun.surface.diagnostics`, no escribe ni en el store ni en `<store>/cache/` y no inicia daemon. `:observed-at` es la hora de esta respuesta, no un evento persistido; las señales cubren las categorías del diagnóstico, no todo cambio topológico. Distingue fuente, contrato y recálculo (`10 §11.1`, K6): `source/*` frente a `contract/changed`, `contract/parser-unavailable`, `contract/inconsistent` y `contract/rebuild-mismatch`; el `:detail` lleva `content-ok`, `contract-ok`, `contract {:status :changed}` y `:registered-root`/`:rebuilt-root` |
| `stores heartbeat` | `[--interval SECONDS] [--beats N]` | daemon: repite la observación de `stores sense` cada intervalo y la escribe como NDJSON (`started`, `beat`/`error`, `stopped`); no escribe en el store. Contrato abajo, «El latido» |
| `stores update` | — | `{:updated [name] :skipped [{:document :category :action}] :status}`; todos los reparados van en UNA revisión, cuyo journal lleva `:documents` ({:address :fields}) |
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
| `beat` | observación lograda | `seq`, `store`, `revision` (cabecera observada), `changed`, `elapsed-ms`, `observation` = el `:data` de `stores sense` sin `:mode` (`observed-at tracked valid signals counts summary`) |
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
  **cierre declarado**: `--reason TEXT` es opcional y la declaración
  (`:declared {:kind "closure" :file :reason :file-deleted?}`) queda en el journal; `:file` es el
  estado del archivo antes de actuar (`present | missing | none`).
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
  `docs/previous-file-present`. La declaración queda en el journal
  (`:declared {:kind "reanchor" …}`). Límites: no hay detección de movimientos, `--reason` es
  opcional solo en el cierre, no existen copiar/dividir/fusionar con linaje declarado, y un
  `kimun.include.edn` que declara la ruta vieja hay que cambiarlo a mano.
- **Una ruta, un documento.** Registrar un documento nuevo —tipado o genérico— sobre un archivo cuya
  ruta tiene otro es `docs/exists` (3) y nombra al que la tiene.
- **Duplicados heredados.** Un store de v1 puede traer dos documentos sobre el mismo archivo. Entran
  solo por el modo explícito que usa `migrate --from-v1` (`track-plan` con `:shared-path :allow`): el
  segundo queda trackeado y sensorizado pero **sin ruta**, la ruta sigue con el primero, y
  `stores check` lo declara (`:routes-shared`, warning `routes/shared-path`).
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
raíz y dos raíces se comparan bajando por donde difieren. Un store creado antes
es esquema 1 (un mapa plano) y **sigue así, sin cambios, hasta que se pide**: `stores upgrade` lo
pasa a esquema 2 con las mismas rutas, documentos e historial, y devuelve la raíz anterior y la
nueva (la raíz cambia al cambiar de esquema); `include build --rebuild` también. `stores check`
informa `:routes-schema`. Un nodo dañado del árbol es `route/corrupt` (5) al leer esa rama.

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
  `:inline` o formato 1, o con un archivo fuera del proyecto, el árbol se persiste como antes.
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
| `docs track MODEL FILE` | `--name N`, `--force`, `--inline` | mapa del documento; `--force` para un fichero que no ida-vuelta; `--inline` guarda el texto en el store |
| `docs show NAME` | `--format md`, `--ast` (equivale a `--format ast`) | mapa con `:markdown` y, en un documento tipado, `:units [{:field :id :status}]` (`anchored \| missing`, K1c); `--ast` ⇒ `{:name :tree :ast}` |
| `docs status` | — | `[{:name :path :status}]` con `status` `ok \| modified \| missing` |
| `docs anchors NAME` | `--sections` | anclas estructurales del documento como **evidencia, sin identidad** (S2a): `{:name :tree :path :parse-contract :sections [{:locator :level :hash :lines :bytes :blocks}] :blocks [{:locator :hash :lines :bytes}]}`; `--sections` omite `:blocks`. Solo lectura: no escribe store ni caché y no acuña ids. Tipado o genérico |
| `docs reanchor NAME FILE` | `--reason TEXT` (obligatorio) | declara el ancla nueva conservando ids: `:reanchored {:from :to :previous-file :reason}` + warning si el archivo viejo sigue |
| `docs untrack NAME` | `--reason TEXT` | desrastrea; `:closure {:file :reason}` y `:declared` en el journal |
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
| `models define` | `-f X.edn` \| `--from-python MOD:CLASS` `[--pythonpath P]`, `--dry-run`, `--backfill` | nombre nuevo ⇒ `:add-node`; existente ⇒ versión+1 y `supersedes`; el descriptor es v1 (`:template`) o spec 16 (`:anchoring`), nunca ambos; `:document-role :generated` se rechaza (F10) |
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
| `extract [MODEL]` | `--input FILE`, `-o FILE`, `--allow-empty` | `{:model :payload}` (+`:output` si se escribió) |
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

### adaptadores

Diseño, protocolo JSON y adaptadores incluidos en `11`.

| subcomando | opciones | notas |
|---|---|---|
| `adapters list` | — | `[{:name :file :available? :profile :languages :options :missing}]` |
| `adapters scan` | `ADAPTER PATH --system S [--factory M:A] [--language L] [--output DIR] [--architecture-spec FILE] [-- opciones]` | planes, sin escribir ni store |
| `adapters sync` | idem | define modelos ausentes y aplica create/update/untrack |
| `adapters check` | idem | como `sync` sin escribir; deriva ⇒ `adapters/missing` (1) |
| `adapters python-scan\|python-sync\|python-check` | `--source-root DIR` (`src`), `--package P`, `--system sldb` | alias v1; adaptador `python-symbols` |
| `adapters selfdoc-scan\|selfdoc-sync\|selfdoc-check` | `--factory M:A` (obligatoria), `--pythonpath P`, `--include-hidden` | alias v1; adaptador `argparse-cli` sobre la raíz |

Errores: `adapters/missing` (1, con `:reason "drift"` para `check`), `adapters/conflict` (4), `adapters/runtime|failed|invalid-output` (5), `cli/usage` (6). Las opciones del adaptador no declaradas van tras `--`.

### migrate

`migrate --from-v1 [PATH] [--pythonpath P] [--dry-run] [--only MODEL] [--limit N] [--force]`. Trae un store sldb v1 (`.sldb`) al store kimun en un proceso y un open; idempotente, cada ítem en un bucket. `PATH` por defecto es el `.sldb` junto al store. Fallos: `migrate/not-v1-store` (5), `cli/usage` (6), `migrate/nothing-migrated` (5).

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

### journal

| subcomando | opciones | `:data` |
|---|---|---|
| `journal show` | `--limit N`, `--by ACTOR`, `--tree NAME\|ID` | entradas, la más nueva primero; una entrada puede llevar `:operation`/`:address`/`:fields` y, si la revisión reparó varios documentos (`stores update`), `:documents` |
| `journal verify` | — | `{:revisions :checked :objects :bad}`; problema ⇒ `store/corrupt` (5) |

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
la unidad) → `docs reanchor` (mismo `:tree`, `:declared` en el journal) → dos
latidos. Comprueba con sha256 del árbol del store que `stores sense`,
`stores health` y `stores heartbeat` no escriben. Requiere el pod tree-sitter para
el tramo K6 (sin él lo omite y lo dice). No promete la vista del circuito más
allá del nivel documento, ni unidades debajo del documento, ni que kimun detecte
movimientos: el sensor informa ausencia y el actor declara. Evidencia:
`runs/subagents/2026-11-15-TV1/` (transcript y prueba `tour-test`).

## 4. Rutas de `serve`

Tabla de rutas como datos en `src/kimun/surface/http/routes.cljc`; cada ruta delega en un comando de §3. Familias:

- `GET /health /schema /stores /stores/check /stores/links /stores/sense /stores/health /docs /document /document/ir /document/segments /ast /lint /fields /sections /find /extract /models /models/detail /render /graph /edges /edges/node /edges/nodes /journal /journal/verify /corpus/rank`.
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
| — | `stores health`, `stores update`, `stores reconcile [--apply]`, `stores upgrade` | nuevos: diagnóstico, reparación sin decisión, federación, formato del store (1→2→3→4) |
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
| `journal`/log | `journal show\|verify` | |
| `selfdoc scan\|sync\|check`, `selfdoc python-*` | `adapters scan\|sync\|check`, `adapters python-*` y alias `selfdoc-*` | adaptador fuera de proceso, protocolo 1 |
| `find`, `serve` | `find`, `serve` | |

Retirados, no portados: `explore`, `faq`, `inbox`, `ast show|schema` (→ `docs show --ast`), `lint`, `legacy ls|get|glob|raw-find`, textos de `help` hardcodeados (→ `--help` genérico), `project init|example`, la materialización de kgdb (`project`), y las banderas `--kgdb-output`/`--kgdb-command`.

## 6. No implementado

- **Evaluador anclado** (S4/S5 de la planificación retirada): un primer token desconocido devuelve `eval/not-available` (6). No existen `eval`, `next`, `check`, `rel`, `anchors`, `anchor add`, ni el grupo `derive`; tampoco hay `.kimun/session.edn` ni outbox. Diseño en `docs/archive/v2/09-evaluador-anclado.md`.
- **Cliente Python y wheels** (tarea `C2` diferida el 2026-11-15, decisión 47 del dueño: el wrapper Python se agrega después de `RS1` y `RS2`, `12 §1`): no existen `python/kimun/client.py`, `python/kimun/errors.py` ni la task `bb wheel`. El único puente Python es `python -m kimun.models export mod:Class` para `models define --from-python`.
- **Motores de derivados**: las capacidades `kimun/derive` y `kimun/materialize` se registran al crear el store, pero ningún comando las ejercita todavía.
