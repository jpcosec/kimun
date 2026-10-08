# kimun — Adaptadores de superficie (protocolo 1)

> Un parser como tree-sitter, `ast` o `argparse` es un **adaptador de superficie**: un proceso
> aparte que lee código fuente y emite hechos estructurados como JSON. kimun convierte esos
> hechos en documentos por su camino de escritura normal (`docs create|update|untrack`, `06`).
> Este documento fija el protocolo, el planificador puro y el grupo `kimun adapters`. Los
> adaptadores incluidos viven en `python/kimun/adapters/`; el planificador, en
> `src/kimun/surface/adapters.cljc`; el grupo, en `src/kimun/cli/commands/adapters.cljc`.

## 1. Marco: S, M, G y por qué fuera de proceso

kimun guarda conocimiento direccionado por contenido en tres capas: **S** (texto de
superficie), **M** (árbol sintáctico) y **G** (significado). Un adaptador trabaja en la
frontera S→M: recibe el texto de un código y entrega hechos sobre su estructura. No toca el
store; lo que kimun hace con los hechos es asunto del planificador (§6).

Los adaptadores son **procesos separados por diseño**:

- Babashka no puede cargar tree-sitter ni importar el código que se documenta (el adaptador
  `argparse-cli` importa una fábrica de parser Python). Un proceso aparte lo resuelve sin
  ampliar el runtime de kimun. (El caso de tree-sitter lo cubren ahora los **pods**, decisión
  19/29: `kimun.host.treesitter` carga `pod-kimun-treesitter` en proceso, ver `08 §4`; este
  protocolo 1 sigue siendo para adaptadores que emiten hechos.)
- Terceros agregan adaptadores sin tocar kimun: basta un ejecutable que cumpla este contrato
  (§8).
- El contrato es solo JSON por stdout más códigos de salida; no hay API de plugins.

## 2. Invocación

```
<adaptador> ROOT [--system NAME] [opciones del adaptador]
```

- `ROOT` es el directorio a escanear. Las rutas de los hechos son relativas a `ROOT`.
- Los incluidos se lanzan como `python3 python/kimun/adapters/<nombre>.py ROOT ...`; los
  archivos son ejecutables (`#!/usr/bin/env python3`). kimun los corre con el intérprete de
  la variable `KIMUN_ADAPTER_PYTHON` (por defecto `python3`) y `PYTHONDONTWRITEBYTECODE=1`.
- Nombres (CLI / archivo): `python-symbols` / `python_symbols.py`, `argparse-cli` /
  `argparse_cli.py`, `click-cli` / `click_cli.py`, `treesitter-symbols` /
  `treesitter_symbols.py`.
- Los adaptadores nunca escriben dentro de `ROOT` (ni bytecode ni cachés).
- Los adaptadores de CLI toman `--factory modulo:attr` (`ROOT` se pone en `sys.path`).

## 3. `--describe`

`<adaptador> --describe` imprime un objeto JSON y sale con 0 **aun si falta el runtime**:

```json
{"adapter": "...", "protocol": 1, "profile": "symbols|cli", "languages": ["..."],
 "options": [{"name": "--x", "help": "..."}],
 "runtime": {"available": true, "missing": []}}
```

Los cuatro incluidos listan `--system` entre sus `options` (la aceptan aunque el escaneo no la
use); el contrato no lo exige a los adaptadores de terceros. `kimun adapters list` (§7) se apoya en esta salida.

## 4. Salida: sobre y hechos

Stdout es **un** objeto JSON UTF-8, determinista: claves ordenadas y hechos ordenados por `id`.

```json
{"protocol": 1, "adapter": "python-symbols", "profile": "symbols",
 "language": "python", "facts": [ FACT, ... ]}
```

`profile` es `symbols` o `cli`; `language` es un nombre de lenguaje o `null`.

### 4.1 Hecho (FACT)

Toda clave está siempre presente; vale `null` o `[]` cuando no aplica.

| clave | tipo | significado |
|---|---|---|
| `id` | string | identidad estable, única en la salida. symbols: ruta calificada con puntos desde la raíz (python: `<módulo>.<qualname>`; hecho módulo: `<módulo>`). cli: tokens de la ruta del comando unidos por espacio (`""` no se permite: el parser raíz es el nombre del programa); argumentos: `<id del comando>#<dest>` |
| `kind` | string | vocabulario neutral: `module`, `class`, `function`, `method`, `command`, `group`, `argument` |
| `name` | string | nombre corto |
| `qualified_name` | string | nombre calificado dentro de su unidad (python: qualname, p. ej. `Clase.metodo`; módulo: nombre del módulo; cli: ruta del comando con espacios) |
| `path` | string o null | ruta de la fuente relativa a `ROOT`, separador POSIX, nunca absoluta |
| `span` | `{"start_line","end_line"}` o null | enteros, 1-based, inclusivo |
| `source_hash` | `"sha256:<hex>"` o null | hash de los bytes del archivo fuente del que salió el hecho (excepción documentada en §9.1) |
| `signature` | string o null | firma del invocable / uso del comando |
| `synopsis` | string o null | resumen de una línea (cli: línea de ayuda del padre) |
| `doc` | string o null | docstring / descripción larga, tal como se declaró |
| `relations` | lista de `{"type","target"}` | ordenada; tipos: `contained-in`, `imports`, `references`, ... |
| `attrs` | objeto | extras del adaptador, opacos para el protocolo |

**Regla de neutralidad de lenguaje.** Nada fuera de `attrs` puede ser específico de un
lenguaje. Lo propio del lenguaje (`native_kind`, detalles de argumentos de argparse: `names`,
`required`, `default`, `choices`, `nargs`, `value_type`, `action`, `const`, `help`; de
comandos: `hidden`, `exclusive_groups`, ...) va en `attrs`, igual que todo lo que los
registros de v1 llevaban sin clave neutral.

### 4.2 Relaciones y cobertura

- symbols: todo hecho que no es módulo lleva `contained-in` hacia el id de su contenedor
  (módulo, clase o función). Un adaptador emite un hecho módulo por cada archivo que escanea.
- Import crudo: en el hecho módulo, `{"type":"imports","target":<cadena del import>}`, una por
  import declarado. `python-symbols` emite además relaciones **resueltas** (portadas de
  `python_source_graph` de v1): `imports` y `references` (de asignaciones anotadas) cuyo
  `target` es un id de hecho si `external` es `false`, o un nombre punteado externo si es
  `true`. `external` es la única clave permitida además de `type` y `target`.
  `treesitter-symbols` emite solo `contained-in` e imports crudos.
- Para los hechos módulo, el planificador toma la lista de imports de `attrs.imports` cuando es
  una lista (`python-symbols` guarda ahí la lista exacta de v1) y, si no, de las relaciones
  `imports` en el orden emitido. Las relaciones solas no son exactas a v1 para `imports` y
  `provenance` (las resueltas se mezclan con las crudas): `attrs.imports` es la fuente y las
  relaciones, el grafo.
- Ids duplicados: los dos adaptadores symbols añaden `~2`, `~3`, ... en orden de escaneo (p. ej.
  `Engine.label~2`); el `qualified_name` queda igual, así que colisionan al pasar a id de
  documento (§6.2).
- Determinismo: claves ordenadas, hechos por `id`, relaciones ordenadas; misma entrada, mismos
  bytes.

### 4.3 Validación

`kimun.surface.adapters/validate` devuelve `[]` si la salida cumple el contrato, o
`[{:code :message :fact}]` con códigos `:adapters/protocol`, `/profile`, `/facts`, `/fact`,
`/missing-key`, `/empty-id`, `/duplicate-id`, `/path`, `/span`, `/source-hash`, `/relations`,
`/attrs`. Comprueba: protocolo 1, perfil conocido, `facts` vector, claves requeridas, ruta
relativa POSIX, ids únicos y no vacíos, forma de `span`, `source_hash` y `relations`. De cada
relación exige solo `type` y `target` strings: **acepta claves extra**, así que el `external` de
`python-symbols` pasa la validación (el planificador no lo lee).

## 5. Salida de proceso y stderr

| exit | significado |
|---|---|
| 0 | ok: JSON en stdout y nada más en stdout |
| 1 | escaneo fallido (fuente ilegible o no parseable, falla al importar la fábrica); stderr termina con la línea `error: <mensaje>` |
| 2 | error de uso |
| 3 | falta el runtime (click, tree_sitter, ...); stderr: `error: runtime missing: <paquetes>` |

Con cualquier salida distinta de 0, stdout queda vacío. Los avisos van a stderr con prefijo
`warning: `.

## 6. Cómo kimun planifica documentos desde hechos

El planificador `kimun.surface.adapters` es **puro**: sin E/S, sin reloj, sin cheshire (trae
su codificador JSON compatible con Python). Todo hash se inyecta como `:sha256` en las
opciones de `plan-documents` (el CLI lo pasa; sin él, problema `:adapters/no-digest`).

### 6.1 Perfiles y modelos

| perfil | modelos | directorio de salida |
|---|---|---|
| `symbols` | `PythonSymbolDoc` | `<output>/symbols/<id>.md` |
| `cli` | `CliCommandDoc`, `SurfaceDoc` | `<output>/commands/<id>.md`, `<output>/surfaces/<id>.md` |

Los modelos son descriptores congelados en `resources/kimun/adapters/models/*.edn`, exportados
desde las clases de v1; `sync` los define si faltan (`models define`). **Decisión:** los
lenguajes no Python usan el mismo modelo `PythonSymbolDoc`, con prefijo de id
`<lenguaje>-<system>-<safe>`; el nombre del modelo es herencia de v1, no una restricción de
lenguaje.

### 6.2 Identificadores

- symbols: `<lenguaje>-<system>-<safe>`, con `safe = re.sub(r"[^a-z0-9_]+","-", f"{módulo}-{qualname}".lower()).strip("-")`. Para python es el id de v1 (`python-<system>-<safe>`).
- cli: `cmd-<system>-...` para comandos y `surface-<system>-...` para superficies.
- `system` sigue la regex de v1 `[a-z][a-z0-9_-]*`; si no, problema `:adapters/invalid-system`.
- Dos hechos que dan el mismo id de documento son una **colisión** (`:adapters/collision`,
  "collide when converted to document IDs", como v1): el planificador devuelve el problema y
  el llamador rechaza la operación.
- Los hechos módulo no generan documento (solo aportan nombre de módulo e imports; v1 solo
  emitía clases y funciones).

### 6.3 Campos del parser y campos autorados

El payload de un documento se compone de dos partes (`merge-payload`: `{**existente, **campos}`
de v1; sin existente, los defectos).

| modelo | campos del parser (se sobrescriben en cada sync) | campos autorados (defecto en documento nuevo; nunca se sobrescriben) |
|---|---|---|
| `PythonSymbolDoc` | `id system module qualname kind source_path source_span source_sha256 signature docstring imports architecture_spec provenance` | `purpose` y `architecture` = `Not documented.`; `tags` = `[]` |
| `CliCommandDoc` | `id system command_path synopsis arguments provenance` | `purpose`, `how_it_works` = `Not documented.`; `usage` = firma del hecho; `tags` = `[]` |
| `SurfaceDoc` | `id system surface commands provenance` | `purpose`, `how_it_works` = `Not documented.`; `tags` = `[]` |

Todo valor es string salvo `tags` (lista de strings). `architecture_spec` vale
`Not declared.` salvo que se pase `--architecture-spec` (§7).

### 6.4 Procedencia y regla `owned?`

`provenance` es `<prefijo>:<id de registro v1>; contract-sha256:<hex>`. El prefijo es
`python-ast` para `python-symbols`, el nombre del adaptador para otros adaptadores symbols
(p. ej. `treesitter-symbols:javascript:index:Shape; ...`) y `parser:` para el perfil cli.

`owned?(scope, tracked)` decide si un documento rastreado lo generó este adaptador y sistema.
Es verdadero cuando se cumplen **todas**:

1. el modelo pertenece al perfil;
2. la ruta empieza por `<output>/<directorio del modelo>/`;
3. el campo `system` del payload es el del scope;
4. el nombre empieza por `<lenguaje>-<system>-` (symbols; lenguaje nulo vale `python`),
   `cmd-<system>-` o `surface-<system>-`;
5. la procedencia empieza por `python-ast:`, `<adaptador>:` o `parser:` según el caso.

La fábrica (`--factory`) no forma parte del scope.

### 6.5 Plan de sincronización y deriva

`sync-plan(planned, tracked)` (con `tracked` ya filtrado por `owned?`) devuelve cuatro listas
ordenadas por nombre:

| lista | contenido |
|---|---|
| `create` | planificado sin documento rastreado, con payload completo |
| `update` | existe y algún campo del parser difiere; incluye `:changed` (campos que difieren) |
| `unchanged` | existe y coincide |
| `untrack` | documento rastreado propio cuyo nombre ya no produce ningún hecho |

`drift-report(plan)` da `{:drift? bool :documents [...]}` con estado `missing` (habría que
crear), `changed` (habría que actualizar) u `orphan` (habría que dejar de rastrear); el grupo
CLI agrega `missing-file` y `untracked` (§7).

**Regla de untrack.** Un símbolo que desaparece del código no borra su documento: `sync` hace
`docs untrack` y el archivo queda en disco. El alcance del untrack es etiqueta del adaptador +
`system` + directorio de salida: sincronizar otro subárbol bajo el mismo `system` deja de
rastrear los documentos del primero. Para el perfil cli la etiqueta es `parser:` en todo
adaptador cli, así que los documentos de `argparse-cli` y `click-cli` del mismo sistema se
consideran el mismo conjunto generado.

**Adopción.** Si un documento nuevo planificado ya tiene archivo en su ruta sin rastrear, se
lee con el descriptor del modelo (el guardado o el congelado, en memoria). Si extrae y su
`id` de payload es el id planificado, se **adopta**: `docs track` (un commit) y luego el
payload fusionado con el existente como base, con `docs update` solo si algún campo del
parser difiere. Cualquier otro caso en esa ruta es `:adapters/conflict` antes de escribir
nada (modelos incluidos). Consecuencia: si un símbolo vuelve, su archivo retenido se adopta y
sus campos autorados sobreviven. Porta la rama "untracked" de `_save_document` de v1.

**`source_sha256`.** Es el hash del archivo fuente completo y es campo del parser: editar un
docstring actualiza todos los documentos de símbolos de ese archivo (como en v1).

## 7. El grupo `kimun adapters`

La sintaxis y las opciones exactas de cada subcomando las posee `06 §3`; un ejemplo:
`kimun adapters scan python-symbols src --system sldb`.

- `scan`: muestra los documentos planificados; no escribe ni necesita store.
- `sync`: corre el adaptador, valida, planifica, compara con los documentos propios
  rastreados, hace preflight (conflicto o adopción), define los modelos ausentes y aplica create/update/untrack.
- `check`: igual que `sync` sin escribir; la deriva es un fallo.
- `list`: adaptadores incluidos y si su runtime está presente.

`ADAPTER` es un nombre incluido o una ruta a un ejecutable; `PATH` y una ruta de adaptador se
resuelven contra el directorio de trabajo. El adaptador recibe
`<adaptador> <PATH absoluto> --system S <tokens...> [--factory M:A]`, corre en el cwd del
contexto con el entorno del padre y `PYTHONDONTWRITEBYTECODE=1`.

### 7.1 Opciones

| opción | aplica a | significado |
|---|---|---|
| `--system NAME` | scan/sync/check (obligatoria, si no `cli/usage`); los alias usan `sldb` | sistema del planificador; también se pasa al adaptador |
| `--factory M:A` | adaptadores cli | se pasa al planificador y al adaptador |
| `--language L` | todos | sobrescribe el lenguaje reportado |
| `--output DIR` | todos | directorio de documentación relativo a la raíz del proyecto (padre del store), por defecto `knowledge`; absoluto o con `..` se rechaza |
| `--architecture-spec FILE` | symbols | relativo a la raíz; se registra `<rel>; sha256=<hex>`; si no es un archivo existente del proyecto, `cli/usage` |
| `--source-root DIR` | alias `python-*` | PATH = `<raíz>/<DIR>`, por defecto `src` |
| `--package NAME` | alias `python-*` | se pasa al adaptador |
| `--pythonpath P` | alias `selfdoc-*` | se pasa al adaptador |
| `--include-hidden` | alias `selfdoc-*` | se pasa al adaptador |

Las opciones del adaptador no declaradas van **después de `--`**
(`kimun adapters sync ADAPTER PATH --system s -- --opcion x`); el parseo es `:restrict true`,
así que un `--x` desconocido antes de `--` es error de uso.

### 7.2 Sobres (`:data`)

| comando | forma |
|---|---|
| `scan` | `{:adapter :profile :language :system :output :facts n :documents [{:name :model :path :fact :fields}]}` |
| `sync` | claves de alcance + `{:models {:defined :present} :counts {:created :updated :untracked :unchanged :restored :adopted} :created :updated [{:name :changed}] :untracked :unchanged :restored :adopted}` |
| `check` sin deriva | claves de alcance + `{:drift? false :documents []}` |
| `check` con deriva | fallo con datos `{:reason "drift" :drift? true :documents [{:name :path :model :status :fields}]}`; `status` ∈ `missing`, `changed`, `orphan`, `missing-file`, `untracked` (archivo existente adoptable; cuenta como deriva, exit 1) |
| `list` | `[{:name :file :available? :profile :languages :options :missing}]`; un adaptador que no responde: `{:name :file :available? false :missing [] :error}` |

`missing-file` / `restored` es adición del CLI sobre el planificador: un documento rastreado e
igual al código cuyo archivo desapareció (el `missing` de v1); `sync` reescribe el archivo con
`docs update` (misma fuente, sin commit nuevo).

### 7.3 Errores y códigos de salida

| tipo | cuándo | exit |
|---|---|---|
| `:adapters/missing` (`reason "unknown-adapter"`) | `ADAPTER` no es incluido ni archivo | 1 |
| `:adapters/missing` (`reason "drift"`) | `check` halló deriva | 1 |
| `:adapters/conflict` | en una ruta planificada hay un archivo no adoptable (no extrae bajo el modelo o su `id` no es el planificado); antes de escribir nada | 4 |
| `:adapters/runtime` | el adaptador sale con 3 o no puede arrancar | 5 |
| `:adapters/failed` | el adaptador sale con 1 u otro código; datos `{:adapter :exit :stderr}`, mensaje con la línea `error:` | 5 |
| `:adapters/invalid-output` | stdout no es un objeto JSON, `validate` o el planificador reportan problemas | 5 |
| `:cli/usage` | falta ADAPTER/PATH/`--system`/`--factory` (alias `selfdoc-*`), `--architecture-spec` inválido | 6 |

La deriva sale como `:adapters/missing` con `{:reason "drift"}`, exit 1 (el de `selfdoc check` de v1).
El namespace `adapters` mapea a exit 5 (`errors.cljc`), que es también el de `runtime`, `failed`
e `invalid-output`; por eso no se usa un tipo `:adapters/drift`.

### 7.4 Escritura

Orden de `sync`: adaptador, validación, plan, `sync-plan`, preflight, `models define` de los
modelos ausentes, `docs create`, `docs track` (adopciones), `docs update`, `docs untrack`. Una operación de journal por
documento, cada una con `:address Model:name`; un commit por documento. El commit de
`models define` es el del comando existente, que hoy no registra `:operation` ni `:address`
en el journal (solo las escrituras de documentos los llevan).

### 7.5 Alias de v1

| alias | equivale a |
|---|---|
| `python-scan`, `python-sync`, `python-check` | `scan`/`sync`/`check` con `python-symbols`, PATH = raíz/`--source-root` (por defecto `src`) |
| `selfdoc-scan`, `selfdoc-sync`, `selfdoc-check` | idem con `argparse-cli` sobre la raíz del proyecto; `--factory` obligatorio |

Los alias no toman posicionales ADAPTER/PATH; los tokens posicionales se pasan al adaptador.
No se portan `--kgdb-output` ni `--kgdb-command` (kimun no tiene KGDB).

## 8. Escribir un adaptador de terceros

Basta un ejecutable que cumpla §2 a §5. Ejemplo mínimo completo (`my-adapter`, Python,
`chmod +x`), con un hecho módulo y un hecho función:

```python
#!/usr/bin/env python3
import hashlib, json, sys

def main(argv):
    if "--describe" in argv:
        print(json.dumps({"adapter": "my-adapter", "protocol": 1, "profile": "symbols",
            "languages": ["demo"], "options": [{"name": "--system", "help": "accepted"}],
            "runtime": {"available": True, "missing": []}}, sort_keys=True))
        return 0
    root = argv[0]
    src = open(root + "/demo.txt", "rb").read()
    h = "sha256:" + hashlib.sha256(src).hexdigest()
    nul = dict(synopsis=None, doc=None, signature=None, attrs={})
    mod = dict(id="demo", kind="module", name="demo", qualified_name="demo", path="demo.txt",
        span={"start_line": 1, "end_line": 2}, source_hash=h, relations=[], **nul)
    fn = dict(id="demo.run", kind="function", name="run", qualified_name="run", path="demo.txt",
        span={"start_line": 2, "end_line": 2}, source_hash=h,
        relations=[{"type": "contained-in", "target": "demo"}], **nul)
    print(json.dumps({"protocol": 1, "adapter": "my-adapter", "profile": "symbols",
        "language": "demo", "facts": [mod, fn]}, sort_keys=True))
    return 0

sys.exit(main(sys.argv[1:]))
```

El ejemplo lee `<ROOT>/demo.txt` (su único archivo fuente); si falta, el proceso termina con
exit 1 y kimun lo reporta como `:adapters/failed` con el stderr del adaptador. Se ejecuta por ruta:

```
kimun adapters scan ./my-adapter src --system demo
kimun adapters sync ./my-adapter src --system demo
```

Con lenguaje `demo` los documentos se llaman `demo-demo-demo-run` (prefijo
`<lenguaje>-<system>-`) y `provenance` empieza por `my-adapter:`. Los hechos módulo no
generan documento, así que `scan` listará solo la función. Recordatorios: ids únicos y
ordenados; `path` relativo; no escribir en `ROOT`; errores con `error: ...` en stderr y
stdout vacío.

## 9. Adaptadores incluidos

Todos escriben `protocol 1` y declaran `--system` en `--describe` (lo aceptan; el escaneo no lo usa). Disponibilidad del
runtime: `--describe` o `kimun adapters list`.

### 9.1 `python-symbols`

`python_symbols.py`; perfil `symbols`; lenguaje `python`; runtime: solo la biblioteca estándar
(`ast`, sin importar ni ejecutar). Port del `PythonScanner` de v1. Opciones: `--package`
(prefijo antepuesto al nombre de cada módulo) y `--system` (aceptada, no usada).

- Hechos: un `module` por cada `*.py` bajo `ROOT` y un `class`/`function`/`method` por
  definición. Id: `<módulo>.<qualname>`; módulo: `<módulo>` (`__init__` colapsa al paquete; sin
  nombre, `__init__`). Duplicados: `~2`, `~3` en orden de escaneo.
- `attrs`: `native_kind` (nombre del nodo `ast`; `Module` en módulos) y `module`; el módulo añade
  `imports` (lista ordenada exacta de v1). Relaciones: §4.2.
- `source_hash`: la regla del protocolo es el sha256 de los bytes del archivo. `python-symbols`
  hashea el texto tras decodificar con saltos universales, como v1: idéntico salvo en archivos
  con CR/CRLF. Excepción documentada de fidelidad a v1.
- Errores: exit 1 con `error: <ruta>: <excepción>` ante cualquier archivo ilegible o no
  parseable (como v1); también si `ROOT` no es un directorio.
- `source_sha256` del documento es el hash del archivo entero y campo del parser: §6.5.

### 9.2 `argparse-cli`

`argparse_cli.py`; perfil `cli`; lenguaje `python`; runtime: biblioteca estándar. Importa
`--factory modulo:attr` (invocable que devuelve un `argparse.ArgumentParser`; `ROOT` en
`sys.path`) y construye el parser sin despachar handlers. Opciones: `--factory` (obligatoria),
`--pythonpath` (entrada extra de `sys.path`, relativa a `ROOT` salvo que sea absoluta),
`--include-hidden` (también comandos y argumentos con ayuda `SUPPRESS`) y `--system`.

- Hechos: uno por comando/grupo (`command`/`group`; id = ruta con espacios) y uno por argumento
  (`argument`; id `<comando>#<dest>`). `path`, `span` y `source_hash` son `null`.
- `attrs` de comando/grupo: `path` (lista de tokens), `hidden`, `exclusive_groups`, `arguments`
  (heredados + propios). De argumento: `names`, `required`, `default`, `choices`, `nargs`,
  `value_type`, `action`, `const`, `help`, `dest`.
- Errores: falta `--factory`, uso (exit 2); fábrica que falla o no devuelve un parser, exit 1
  (`error: <Tipo>: <mensaje>`). Lo que la fábrica imprima va a stderr.

### 9.3 `click-cli`

`click_cli.py`; perfil `cli`; lenguaje `python`; runtime: `click` (si falta, exit 3 con
`error: runtime missing: click`; `--describe` sale 0 con `available: false`). Importa
`--factory modulo:attr` (un `click.Group` o un invocable que devuelve uno) sin invocar handlers.
Opciones: `--factory` (obligatoria), `--pythonpath`, `--program` (nombre del programa en las
firmas de uso; por defecto `pron`, como v1) y `--system`.

- Hechos: solo comandos hoja, más un hecho por parámetro (`<comando>#<dest>`), con la misma
  forma de `attrs` que `argparse-cli`.
- Errores: igual que `argparse-cli` (uso exit 2; fábrica inválida exit 1).

### 9.4 `treesitter-symbols`

`treesitter_symbols.py`; perfil `symbols`; lenguajes `python` y `javascript`; runtime opcional:
necesita `tree_sitter` y la gramática del lenguaje elegido (`tree_sitter_python`,
`tree_sitter_javascript`); si falta, exit 3 (`error: runtime missing: <paquetes>`).
`KIMUN_ADAPTER_PYTHON` selecciona un intérprete que las tenga. Opciones: `--language
python|javascript` y `--system` (aceptada, no usada).

- Lenguaje: se infiere de los archivos; con ambos presentes, exit 2 (`error: both ... found:
  pass --language`); sin archivos, `python`.
- Tabla por lenguaje (`LANGUAGES`): extensiones, consulta tree-sitter, mapa captura-kind, tipos
  de nodo transparentes y de ancla, y lectores de nombre, doc, firma, `native_kind`, módulo e
  imports. Agregar un lenguaje es agregar una entrada.
- JavaScript (`.js .mjs .cjs .jsx`): declaraciones de función, generador y async, clases,
  métodos, y `const`/`let`/`var` de nivel módulo cuyo valor es función flecha o expresión de
  función; el bloque JSDoc es `doc`; los imports son las cadenas de origen; se omiten
  `node_modules` y `*.min.js`.
- Hechos: misma forma que `python-symbols` (módulo + símbolos, ids `<módulo>.<qualname>`, `~2`,
  `~3` en duplicados); relaciones solo `contained-in` e imports crudos; `attrs`: `native_kind`,
  `module` y, en el módulo, `imports`.
- `source_hash` es el sha256 de los bytes (sin la excepción de §9.1).
- Errores: una fuente con errores de sintaxis da `warning: <ruta>: syntax errors, facts are
  best-effort` en stderr y hechos de mejor esfuerzo (no es fallo); `OSError` o `ROOT` que no es
  directorio, exit 1.
- Acuerdo con `python-symbols`: claves neutrales, `signature` e imports crudos iguales sobre
  el propio código de v1.

## 10. Desviaciones respecto de v1

- El grupo `selfdoc` de v1 pasa a llamarse `adapters`; `selfdoc scan|sync|check` y
  `selfdoc python-*` sobreviven como alias (§7.5). El `--factory` por defecto de v1
  (`sldb.cli.parser:build_parser`) se descarta: es obligatorio. No se portan `--kgdb-output`
  ni `--kgdb-command`.
- El escaneo ya no es código en proceso: v1 importaba su scanner; kimun ejecuta un adaptador
  externo con un protocolo JSON versionado (`protocol: 1`), y un adaptador puede ser de
  terceros.
- `untrack` es una adición: v1 solo informaba `removed` (y `sync` salía con 1); aquí `check` informa
  `orphan` (exit 1) y `sync` hace `docs untrack` conservando el archivo (§6.5). La adopción
  de un archivo retenido porta la rama "untracked" de `_save_document` de v1.
- `sync` no tiene guarda de imagen previa del archivo (la de v1, "changed during planning"): `docs update`
  re-renderiza desde el payload rastreado; solo se protegen escrituras concurrentes al store.
- Los lenguajes no Python reutilizan `PythonSymbolDoc` con prefijo `<lenguaje>-<system>-` (el id
  de python no cambia).
- Los documentos de símbolos se ordenan por id de hecho (v1: orden de archivo/DFS); los
  payloads son idénticos. El perfil cli conserva el orden de v1.
- `check`: la deriva sale con 1 (como v1) pero vía `:adapters/missing`; `missing-file` es
  una adición sobre el informe de v1.
- Los símbolos duplicados desambiguados por el adaptador (p. ej. `Engine.label~2`, mismo
  `qualified_name`) colisionan en el id de documento, como en v1 (`collide`).
- Esquinas no exactas conocidas: flotantes dentro de `attrs` se imprimen con `str` de Clojure,
  no con `repr` de Python; `.lower()` de Python y `toLowerCase` de Java pueden diferir en
  mapeos de mayúsculas exóticos (todo lo que no es `[a-z0-9_]` termina en `-` de todos modos).
