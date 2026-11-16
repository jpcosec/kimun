<!-- Informe de un agente de exploración de solo lectura (2026-11-16). Es salida de un modelo: verifica antes de apoyarte en una cifra. -->

# Informe: qué separa a kimun de un producto terminado e instalable (operaciones)

Contexto: rama `main`, 113 commits locales que no se han subido a `origin` (`jpcosec/kimun`). No hay ningún tag de release v2: los únicos tags son `pre-v2-planning-freeze` y `v-serve-codec-2026.08.29`. Tampoco existe `dist/`. Marco cada afirmación con **[L]** (verificado leyendo) o **[I]** (inferencia mía).

## 1. Construcción, instalación y release

**Tasks de `/home/jp/proyectos/kimun/bb.edn`** [L]. Exige bb ≥ 1.13.0 (l.2).

| task | qué hace |
|---|---|
| `cli` (l.8) | `bb -m kimun.cli.main …` en proceso |
| `desk` (l.11) | alias de `kimun include build` (`--rebuild` mueve el store viejo a un respaldo) |
| `pod` (l.14) | `cargo build --release` en `pods/treesitter` (build dinámico, no el estático) |
| `jar` (l.17) | escribe `resources/git-sha` y corre `bb uberjar target/kimun.jar -m kimun.cli.main` |
| `release` (l.23) | `bb scripts/release.clj …` |
| `lint` (l.26) | `scripts/check_rings.clj` |
| `oracle` (l.28) | `python3 scripts/canon_oracle.py` |
| `store-size` (l.30) | `scripts/store_size.clj` |
| `test` (l.33-71) | depende de `lint`. Avisa si el pod no está (sus tests se saltan), corre los `test/**/*_test.cljc` en un directorio temporal propio, cronometra cada namespace, muestra los 12 más lentos y sale con 1 si algo falla |

**`/home/jp/proyectos/kimun/scripts/release.clj`** [L]
- Lee `release.edn` y exige que `:version` sea igual a `resources/VERSION` (l.231-234; hoy ambos dicen `2.0.0-alpha.1`).
- Sin `--jar`, siempre reconstruye el jar (l.245-247).
- Por cada plataforma descarga el bb fijado (`:bb "1.13.219"`), lo guarda en `dist/.cache` y comprueba su sha256. Si el digest es `nil`, aborta (l.148-162).
- Normaliza el jar y el árbol para que el build sea reproducible (`SOURCE_DATE_EPOCH`, o 1980 por defecto; l.41-78).
- Si encuentra el pod, lo copia y le hace un smoke test (l.181-194). Si no lo encuentra, solo avisa y empaqueta sin pod (l.195-198).
- Genera `tar --sort=name … gzip -n` o `.zip` en Windows, más `SHA256SUMS`. `--pin` reescribe los digests de `release.edn`.

**Contenido de un bundle** [L, l.5-6 y 164-210]: `kimun-<v>-<plat>/{bin/kimun, bin/kimun.cmd, lib/bb(.exe), lib/kimun.jar, lib/pod-kimun-treesitter(.exe) solo si existe, VERSION, LICENSE}`, más el `.tar.gz` o `.zip` y `SHA256SUMS`. El launcher POSIX sigue symlinks, exporta `KIMUN_POD` y ejecuta `lib/bb --jar lib/kimun.jar -- "$@"` (l.82-96).

**Qué está fijado y qué no** (`/home/jp/proyectos/kimun/release.edn`) [L]
- Fijados: la versión de bb y los sha256 de los 5 assets de bb (l.16-26).
- Sin fijar: el pod tiene `:sha256 nil` en las 5 plataformas (l.27-47). Un digest `nil` simplemente no se comprueba (release.clj l.185).
- El toolchain de Rust es `channel = "stable"` y no una versión exacta (`pods/treesitter/rust-toolchain.toml`). Las versiones de las crates sí quedan fijas en `Cargo.lock`.

**Plataformas**
- Declaradas: linux-amd64, linux-aarch64, macos-amd64, macos-aarch64 y windows-amd64.
- Probadas en CI [L, `.github/workflows/ci.yml` l.36-40]: solo linux-amd64, y con `--local-bb`.
- `release.yml` construye todas las plataformas en `ubuntu-latest` (l.60) [L]. Ningún job compila el pod ni llena `target/pods/<plat>/` (grep de `pod` en `.github`: nada) [L].
- Por tanto, los bundles publicados por CI saldrían **sin pod** [I, muy probable].
- `release-test` cubre solo linux-amd64; el zip de Windows "sin verificar" lo admite `12 §1` l.49 [L].
- **Bug [L+I]:** `pod-source` (release.clj l.107-116) recurre al build de desarrollo `pods/treesitter/target/release/…` para *cualquier* plataforma. En local, `bb release` sin `--platforms` metería el binario Linux en los bundles de macOS y Windows (en Windows buscaría `.exe` y no lo encontraría). El smoke test pasaría igual, porque corre en el host (l.118-128).

**Workflows** [L]
- `ci.yml`: lint, test, oracle (instala Python 3.12), smoke del uberjar y del bundle local. No construye el pod, así que en CI los tests del pod se saltan [I, por bb.edn l.49-52].
- `release.yml`: con un tag `v*` corre lint y test (sin oracle), comprueba tag = VERSION, construye los bundles y publica con el gate `environment: release`, que exige un revisor humano.

**Pod tree-sitter** (`/home/jp/proyectos/kimun/pods/treesitter/README.md`) [L]
- Es Rust, con tree-sitter 0.26, md 0.5.3 y python 0.25. **No incluye la gramática de Clojure** (lo confirma `sesion-2026-11-15.md` l.136-139).
- El README dice que se empaqueta el build static-pie (l.20-22), pero `bb pod` hace el dinámico.
- Orden de búsqueda: `$KIMUN_POD`, luego `lib/` del bundle, luego el build de desarrollo. Si no hay pod, cae al parser nativo.

**Instalar hoy en una máquina limpia** [L+I]
- No hay ningún release publicado. Hay que clonar, instalar bb 1.13.x y usar `bin/kimun` (lanzador de desarrollo, `bin/kimun` l.1-5).
- Alternativa: `bb release --local-bb --platforms linux-amd64` y luego `ln -s …/bin/kimun ~/.local/bin` (`08 §7` l.142).
- Para tener el pod hace falta Rust y `bb pod`.
- Al probar kimun sobre un store existente: todo store que no sea formato 4 con esquema de rutas 2 no abre (`06` l.71). El `.kimun/` de este checkout tuvo que reconstruirse; hay un respaldo `.kimun.bak-20261115-…`.
- `/home/jp/proyectos/kimun/bin/kimun.cmd` es el launcher de bundle, no uno de desarrollo, y no exporta `KIMUN_POD`. Es incoherente con `bin/kimun` [L].

## 2. Qué prometen README y documentos de usuario

**`/home/jp/proyectos/kimun/README.md`**
- l.3-5 "store con CLI y kernel content-addressed, sucesor de sldb y kgdb": vigente.
- l.7-13 "La única decisión abierta del dueño es `D-G`": **obsoleta**. `preguntas-abiertas.md` tiene unas 19 filas activas.
- l.15-20 describe CAS, "trees of positions", supersedes y reconciliación como la implementación: es cierto del código, pero **es justo lo que se difiere**. El árbol por ruta, las revisiones, el log y el journal son "rehacer git" (`12 §1` l.76-84). El README no lo dice.
- l.27-34, Install: "un bundle no necesita nada en la máquina" es cierto del diseño, pero **no hay bundle publicado**, y el bundle de CI no llevaría pod.
- l.44, `journal show` en el tour: el comando existe, pero el dueño considera redundante el journal (`sesion` §6). **Obsoleto en dirección.**
- l.54: `bb oracle` exige Python. El README no lo menciona.
- l.61-65 y Layout: no citan `sesion-2026-11-15.md`.

**`/home/jp/proyectos/kimun/docs/faq.md`**
- l.27-29 "La paridad con sldb v1 está cerrada y medida": **obsoleto**. Está suspendida (`12 §1` l.86-91).
- l.17: la lista de lo diferido no incluye "rehacer git" ni la nueva dirección de declaraciones como formas de Clojure.
- l.37-41, desk: coherente.

**`/home/jp/proyectos/kimun/docs/v2/README.md`**
- l.3-4 nombra `11` como documento de producto: **no existe** ningún `docs/v2/11-*.md` [L]. `05` decisión 19 (l.107) y `08` l.42 lo siguen citando como `11-adaptadores.md`.
- La tabla de autoridad (l.17-29) no incluye `drafts/sesion-2026-11-15.md`. Las decisiones del 15/11 no tienen rango de autoridad salvo lo que ya se copió en `12 §1`.
- El orden de lectura (l.61-71) no la menciona.
- l.44 sobre `10` ("el vigente es el 4; los anteriores ya no se leen") está al día.

**`/home/jp/proyectos/kimun/AGENTS.md`**
- l.91 "La última sesión de diseño (2026-10-08/09) cambió la dirección": **obsoleto**. Debe apuntar a la del 2026-11-15.
- l.55 "gestiona el desk con … `journal`": obsoleto en dirección.
- l.121-123 "format 4 keeps…": correcto, pero no dice que el nivel de archivo pasa a git.
- l.113-117 depende de herdr, deepseek y `~/bin/memcap`, que son infraestructura local del dueño [L].

## 3. Documentos con autoridad (`docs/v2/*.md`)

| doc | líneas | qué especifica | frente a la dirección del 2026-11-15 |
|---|---|---|---|
| `00-nucleo.md` | 71 | núcleo en proposiciones | **vigente**. 6.4 ("cambios del registro sin reescribir historia") es compatible si la historia la lleva git [I] |
| `01-orden-filosofico.md` | 156 | capas S/M/G, principios | vigente salvo §2 y §4.3, que ya marca superados (cabecera) |
| `02-sustrato-computacional.md` | 882 | pool, árboles, revisiones, log, formatos | **difiere mucho**: §5 revisiones y transacciones, log y formatos. Aún habla de "2/3 legados" y "formato 1 (legado)" (l.199-311, 806, 820) aunque esos formatos se borraron |
| `03-estandares-de-codigo.md` | 106 | anillos, docstrings | vigente ("adaptadores" aquí son el anillo 1, no el subsistema borrado) |
| `04-superficie-markdown.md` | 407 | perfil SLDB-MD | mayormente vigente. El mapeo al pool (§8) depende del store diferido [I] |
| `05-estado.md` | 135 | estado y decisiones 1-47 | **sin el 15/11**: cabecera fechada 2026-10-09. l.13 "Kernel (formatos 2, 3 y 4)"; l.35 Journal como capacidad. No registra "rehacer git" diferido, ni paridad suspendida, ni declaraciones como formas |
| `06-superficies-y-cli.md` | 569 | CLI, envelope, `serve` | vigente como inventario. Título de §2 "(formato 4; 2/3 legados)" obsoleto; journal y planes están en dirección de retiro. `serve` se especifica en una sola línea (l.511) |
| `07-modelos-como-nodos.md` | 208 | modelos v1 implementados | ya superado por `14 §18` y `16` (cabecera) |
| `08-distribucion.md` | 156 | repos, toolchain, release | vigente en lo operativo. Arrastra `11` (l.42) y A3 sobre `python/kimun/graph/` (l.31) |
| `10-escala.md` | 738 | formatos 2-4 en disco, coste, GC | **mayormente "rehacer git"** (§5, §10, §11.2-11.5) → diferido |
| `12-ecosistema-y-pendientes.md` | 263 | backlog, consumidores, wittgenstein | parcialmente actualizado (l.76-91) |
| `13-include.md` | 96 | `kimun.include.edn` | vigente (es justamente la integración con git) |
| `14-juego-del-lenguaje.md` | 870 | principios, autoridad, §18-§21 | §18-§21 vigentes. §10 y §20 (Merkle por ruta) se difieren en lo que toca al nivel de archivo |
| `16-modelos-como-subgrafos.md` | 285 | spec M1 | vigente en clases y selectores. La parte de **leer el Markdown del desk como fuente** (M2) se reemplaza: "TODOS los documentos que genera SLDB… pasarían a ser MATERIALIZACIONES DESDE MODELOS" (`sesion` l.90-91) |
| `README.md` | 73 | autoridad e índice | ver §2 |

**Mecanismos diferidos o reemplazados**
- Formatos del store: `10`, `02 §5`.
- Journal y planes: `06` grupo journal, `10 §11.3`.
- Árbol Merkle por ruta: `10 §11.2`, `14 §20`.
- Modelos que leen el Markdown del desk: `16` M2, `07`, `13 §3`.
- Adaptadores: `05` dec.19, `08` l.42 y el `11` fantasma.

**Backlog `12 §1`** (`/home/jp/proyectos/kimun/docs/v2/12-ecosistema-y-pendientes.md`) [L]

Notas de cabecera:
- l.3-8: reemplazo de sldb, decisión 47, `RS1` y `RS2`.
- l.10-14: D0 derivado; K1 es implementación.
- l.16-21: nota histórica del 14/11.
- l.71-74: invariantes (lint, test, `include check`, memcap, oracle solo cambia en K1).
- l.76-84: "rehacer git" diferido (K2a, K2b, K4, K5, revisiones, log y journal).
- l.86-91: paridad v1 suspendida.

Tabla de frentes (l.42-49): Autoridad, Sensor, Modelos, Relaciones, Heartbeat, Ejecuciones, G, Operación y vista. En "Operación y vista": R1 reproducible hecho, pod en CI pendiente, zip sin verificar, auditoría S1 solo como evidencia.

Filas (l.95-118):

| fila | estado |
|---|---|
| D0 | contrato derivado |
| K1 | cortes a-h hechos. Pendientes: renombrar, linaje, copiar/dividir/fusionar, sensor, modelos como unidades |
| K2a | parcial → **diferido** |
| K2b | medido, sin iniciar → **diferido** |
| K3 | cerrada |
| K4 | implementado → **diferido** |
| K5 | inventario hecho → **diferido** |
| P1 | parcial; línea base/grafo abierta |
| M1 | muchos cortes; faltan M1e, M1k, M1l |
| M2 | regenerar modelos del desk → **redefinido** por el 15/11 |
| S1 | cerrada |
| S2 | S2a hecho; S2b parcial |
| G1 | espera D-G |
| G2 | espera G1 |
| G3 | espera G1 |
| C1 | cerrada y borrada |
| RS1 | sin empezar. **Su criterio (a) cita `migrate --from-v1`, que se borró** (`08` l.32) |
| RS2 | sin empezar |
| C2 | diferida. Cita `python/README.md`, que ya no existe |
| T01 | cerrada |
| K6 | implementada para Markdown |
| K7 | espera G1 |
| K8 | cerrada |
| DOC1 | cerrada |

Además, l.122 aún presenta K2a como trabajo "en paralelo hoy", lo que contradice l.76-84.

## 4. Preguntas abiertas (`/home/jp/proyectos/kimun/docs/v2/drafts/preguntas-abiertas.md` l.65-89)

Ninguna fila se ha borrado todavía, aunque la sesión dice que varias ya no tienen objeto [L].

| id | resumen | ¿la responde el 15/11? |
|---|---|---|
| D-G | eje de verdad en G | **abierta** (`sesion` l.214) |
| GC | carrera entre GC y escritor en vuelo | **respondida en forma**: «algo que nada referencia se marca como "borrado" y luego… se quita» y «eso debería poder declararse por cada proyecto y según cada tipo de dato» (l.101-105). El `stores gc` retenido no se integra (l.109-110). Además K5 queda diferido |
| PLANES | ¿los planes son store o caché? | **pierde su objeto**: «Así es.» a «el journal y los planes como registro propio dejen de hacer falta» (l.118-121; lectura l.123-124) |
| M1e | modos y azúcar de las proyecciones | **orientada, no cerrada**: «exportando un documento… (aquí aprovechamos la idea de los templates de SLDB)» (l.48-49); la sesión lo anota como "orienta la fila M1e" (l.58) |
| F1-H3 | store por delante del archivo | **abierta** (l.212) [I: con git llevando el nivel de archivo podría replantearse] |
| SERVE | seguridad de `serve` | **abierta** (l.212) |
| 16§3.5 | texto del spec sobre los quirks de v1 | **abierta** (l.213). [I: con la paridad suspendida pierde urgencia] |
| 16-det | herencia en diamante, pasos, etc. | **abierta** (l.213) |
| U1 | ¿unidad ausente es `needs-human`? | **abierta** (l.213) |
| F10 / A1.1 | dónde vive un documento generado; ¿nodo o dos? | (a) **respondida**: «Para poder "ver el conocimiento" hay que materializarlo en otro lado» (l.47-48). La sesión dice que responde F10 (l.56-57). (b) no se toca |
| P1-linea | línea entre base y grafo | **abierta** (l.213). Parcialmente orientada: las fronteras salen del grafo del código y de "clean_code" (l.73-75) |
| LINAJE | dónde vive el linaje | **respondida en dirección**: «lo importante no es el nodo mismo, son sus relaciones» (l.51-52); la sesión dice que el linaje es una relación del grafo (l.57-58). Falta el diseño |
| RENOMBRAR | ¿la unidad continúa o es nueva? | **abierta** (l.213). [I: además "dejemos de hablar de identidad" (l.181) la relativiza] |
| K1f-campo | traslado con otro nombre de campo | **abierta** (l.214) |
| K2b | qué mmap retirar | **pierde objeto por diferimiento**: «el "rehacer git" déjalo EXPLÍCITAMENTE diferido» (l.198). Aunque la sesión aún la cuenta entre las no tocadas (l.213) |
| K2a-fanout | fan-out por directorio | **pierde objeto por diferimiento** (misma cita; `12` l.81). No está en la lista de l.212-214 |
| K5-nombres | índice de nombres y retención | **pierde objeto por diferimiento** (K5 está en `12` l.82). La sesión aún la lista (l.213) |
| LATIDO | contrato del heartbeat y coste | **abierta** (l.213). [I: usar git para detectar mutaciones (l.192-193) es parte de la respuesta a "cómo detectar cambios sin releer todo"] |
| M2-modo | legacy o strict por modelo del desk | **pierde objeto**: la sesión lo dice (l.96-97), por «pasarían a ser MATERIALIZACIONES DESDE MODELOS» |
| M2-derivar | derivar modelos v1 con base | **pierde objeto** (l.96-97) |
| M2-desk | include check en deriva | **pierde objeto** (l.96-97) |
| S2b-anclas | qué significa S2b | **abierta** (l.213) |
| RS1 | criterio de "probadamente funcionando" | **abierta** (l.214), y su criterio (a) está roto porque `migrate` ya no existe |

## 5. Tests y validación

**Tamaño y duración de la suite** [L]
- 148 archivos `*_test.cljc`.
- Última evidencia, `/home/jp/proyectos/kimun/runs/subagents/2026-11-15-integracion-R/full-test.txt`: 148 namespaces en **195 s**, `{:test 1176 :pass 12521 :fail 0 :error 0}`, con el pod disponible.
- Los más lentos: `cli.release-test` 55,0 s, `cli.diagnostics-test` 19,8 s, `kernel.mmap-test` 19,4 s, `cli.cold-cache-test` 10,5 s, `cli.docs-parity-test` 7,4 s, `parity.closed-gaps-test` 6,1 s, `kernel.route-tree-test` 5,9 s.

**Qué verifica cada herramienta** [L]
- `bb lint` (`scripts/check_rings.clj`): anillos de dependencia `kimun.kernel/host/surface.markdown/graph/cli` (l.11-19), prohibido el `def` mutable en el anillo 0, docstrings obligatorios. `pending-graph-crossings` está vacío. Resultado: "rings and docstrings ok: 103 files".
- `bb oracle` (`scripts/canon_oracle.py`): reimplementa en Python los canonical-bytes EDN + NFC + SHA-256 y compara con los ids fijados en `test/fixtures/nodes.edn` (9 filas, 0 diferencias).
- `bin/kimun include check` (`13 §2`): compara `kimun.include.edn` con `git ls-files` y el store; si hay deriva sale con 3. Con `--staged` es el hook pre-commit.

**Paridad v1 (suspendida)** [L]
- `/home/jp/proyectos/kimun/scripts/parity/`: 10 scripts. Seis son Python (`coverage.py` es el marcador por `def test_*` de v1; los `record_*.py` graban v1). Quedan, por tanto, **7 archivos Python** en el repo pese a "borrar todo Python": se borró `python/`, no `scripts/`.
- `/home/jp/proyectos/kimun/test/kimun/parity/`: 20 archivos con **139 `deftest`**.
- Hay más tests de paridad fuera de esa carpeta (`*-parity-test` en `cli/`, `graph/` y `surface/models/`).

## 6. Seguridad de `serve` (`/home/jp/proyectos/kimun/runs/subagents/2026-11-15-S1-serve-security/vectors.md`) [L]

Los vectores se midieron y no se arreglaron.
- **Alta sin token:** `POST /docs/create` con `path` absoluto o con `..` escribe fuera del proyecto (2a, 2b). `/docs/track` permite leer archivos exteriores vía `/render` (2c). `/save` con `id=../…` escribe fuera (2d). `--host 0.0.0.0` arranca sin token y sin advertencia (3). Cualquier página web puede escribir cross-origin con `text/plain` (7b).
- **Media:** cuerpos de 400 KB bloquean las escrituras unos 82 s (lock sin timeout; el tope de 8 MB es el valor por defecto de http-kit) (1). `--token ""` apaga la auth incluso si hay `KIMUN_SERVE_TOKEN` (4). No se valida `Host`, lo que permite DNS rebinding (7c).
- **Baja:** cabeceras mal formadas fallan cerrado (5); el timing del token solo filtra su longitud (6); symlinks por CLI (8).
- Funciona como se especifica: el default `127.0.0.1`, el Bearer y CORS apagado.

## Lo que falta o está obsoleto para un producto terminado (operaciones)

Ordenado por cuánto bloquea una primera release usable.

1. **[L] No existe ninguna release v2.** No hay tag `v2.0.0-alpha.1`, `main` está 113 commits por delante del remoto y nunca ha corrido `release.yml`.
2. **[L/I] El pod no está en la cadena de release.** Ningún job de CI compila ni deja el pod en `target/pods/<plat>/`; su sha256 es `nil` en las 5 plataformas; Rust no está fijado (`stable`). Los bundles publicados saldrían sin pod y en CI sus tests se saltan. En local, el fallback de `pod-source` (release.clj l.107-116) metería un binario Linux en los bundles de otras plataformas.
3. **[I] La dirección del producto no está definida.** El "rehacer git" está diferido y las declaraciones pasan a ser formas de Clojure, pero ese cambio "no está diseñado" (`sesion` l.127). El CLI que se publicaría hoy expone journal, revisiones y packs, que van a cambiar. Publicar ahora congelaría una superficie que ya está desautorizada.
4. **[L] `serve` es inseguro sin token** (2a-2d, 3, 7b): lectura y escritura de archivos arbitrarios. No debería salir en una release sin confinar las rutas o sin exigir token fuera de loopback. La decisión está pendiente del dueño (fila `SERVE`).
5. **[L] Solo se prueba linux-amd64.** macOS, aarch64 y Windows nunca se ejecutan; el zip y `kimun.cmd` no están verificados; además `bin/kimun.cmd` del repo es inconsistente con `bin/kimun`.
6. **[L] No hay ruta de instalación documentada para usuarios.** El README remite a construir con `bb release`; no hay script de instalación ni paquete. Los stores que no sean formato 4 con esquema de rutas 2 no abren y la única salida es `include build --rebuild`.
7. **[L] Documentos de usuario obsoletos:** README l.7-13 ("única abierta D-G") y l.44 (journal); FAQ l.27-29 ("paridad cerrada"); AGENTS l.91 (sesión 10-08/09) y l.55; `docs/v2/README.md` cita `11`, que no existe, y no da rango a `sesion-2026-11-15.md`.
8. **[L] Documentos con autoridad sin el 15/11:**
   - `05` (cabecera de 2026-10-09; "formatos 2, 3 y 4"; Journal como capacidad; `11-adaptadores.md`).
   - `02` y `06 §2` (formatos "legados" que ya no existen).
   - `10` casi entero es lo diferido.
   - `12 §1`: K2a todavía "en paralelo hoy" (l.122); RS1(a) depende de `migrate --from-v1`, que se borró; C2 cita `python/README.md`.
9. **[L] `preguntas-abiertas.md` sin podar:** siete filas ya sin objeto o respondidas (PLANES, M2-modo, M2-derivar, M2-desk, GC, F10(a), LINAJE) y tres sin objeto por el diferimiento (K2a-fanout, K2b, K5-nombres) siguen como activas.
10. **[L] Python residual:** `bb oracle` exige Python, CI instala 3.12 y `release.yml` no corre el oráculo. `scripts/parity/*.py` sigue ahí aunque la paridad está suspendida, con 139 deftests de paridad dentro de `bb test`.
11. **[L] Coste de la suite:** 195 s. El `release-test` solo consume 55 s.
12. **[I] Dependencias del entorno del dueño en el flujo de trabajo:** memcap, herdr y deepseek (AGENTS l.113-117). No bloquean al usuario, pero sí a un colaborador externo.
