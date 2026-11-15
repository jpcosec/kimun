# ¿Está kimun listo para reemplazar el núcleo de sldb? (2026-11-15)

> Evidencia no autoritativa. La pidió el dueño el 2026-11-15, desde una sesión de revisión de
> rendimiento de sldb (`/home/jp/proyectos/SLDB_1`). El reemplazo está decidido (decisión 47 de
> `docs/v2/05-estado.md`); aquí solo se responde si se puede hacer hoy y qué falta.
>
> **Cómo se hizo:** leyendo archivos, `grep` y `git log/status/tag` en kimun (`main`,
> `6cf18730`) y en el checkout local de sldb (`641ecdfa`). No se ejecutó, compiló ni instaló
> nada, y no se corrió la suite de kimun. **[V]** = comprobado en el archivo citado.
> **[I]** = inferencia de quien escribe.

## 1. Veredicto: no está listo hoy

A lo más admite una prueba acotada: importar en un solo sentido un `.sldb` a kimun y usarlo por
CLI o por `serve`, al lado de sldb y sin reemplazarlo.

Lo que decide el veredicto:

1. **No hay API de Python.** [V] `python/README.md` §"Client library" y `docs/v2/06-superficies-y-cli.md §6`:
   el único camino desde Python es `subprocess` más el envelope JSON. Los consumidores importan
   sldb dentro del proceso (detalle en `superficie-consumidores.md`).
2. **Los consumidores siguen en v1 por decisión vigente.** [V] decisión 18
   (`docs/v2/05-estado.md:108`): «kimun avanza sin migrarlos … kimun no está desplegado». [V]
   `docs/v2/12-ecosistema-y-pendientes.md §2`: «kimun no tiene consumidores en código».
3. **El manifiesto de capacidades no tiene ninguna completa.** [V] `docs/v2/05-estado.md:73-76`:
   0 `implemented`, 12 `partial`, 11 `absent` (C01–C28).
4. **El motor de modelos está a medias.** [V] fila `M1` de `12 §1`: pendientes motor de
   selector, proyecciones, store e ids de modelo. [V] `kimun.include.edn`: `:models []` y
   `:documents []`, o sea que el desk de kimun se construye vacío.
5. **La identidad cubre solo el nivel de documento.** [V] fila `K1` de `12 §1`: las unidades de
   bloque y sección, los campos anidados y copiar/dividir/fusionar están pendientes. `D-G` sigue abierta.
6. **El formato en disco sigue moviéndose y el actual no se midió en un store real.** [V] fila
   `K2a`: «sin medición en un store real»; el esquema de rutas 2 entró el 2026-11-15 (`b4931b7c`)
   y un binario anterior lo informa como `store/corrupt-head`. Las medidas de `10-escala.md` sobre
   el store real de 1.551 documentos son de formatos anteriores.
7. **La paridad es contra un sldb viejo.** [V] `docs/v2/tests/parity-v1.md`: 590 funciones de la
   suite v1 de `hum-ecosystem/tools/sldb/tests` (520 portadas, 14 oráculo, 55 n/a, 0 brechas).
   [V] sldb lleva 527 commits desde el 2026-10-06 (`git log --since=2026-10-06` en `641ecdfa`).
8. **Versión.** [V] `release.edn`: `2.0.0-alpha.1`; los únicos tags son
   `pre-v2-planning-freeze` y `v-serve-codec-2026.08.29`.

Lo que sí hay: [V] última corrida completa registrada `{:test 1196, :pass 14201, :fail 0,
:error 0, :namespaces 147}` (`runs/subagents/2026-11-15-integracion-F/EVIDENCE.md`). `main` va 72
commits por delante de `origin`, así que el CI no corrió sobre ellos [I].

## 2. Superficie de reemplazo

| Lo que expone sldb | En kimun | Evidencia |
|---|---|---|
| API Python en proceso (`sldb.api`) | ausente | `python/README.md`; `06 §6` |
| Imports internos que usan los consumidores (`runtime.validation`, `store.io`, `store.layout`, `store.hashing`, `cli.*`) | ausente | no hay superficie Python |
| `stores update` | funciona | `src/kimun/cli/commands/stores.cljc`; `06 §3` |
| `stores check` | funciona (más `verify`, `health`, `sense`) | `06 §3` |
| `stores build` / `build --check` | parcial | `include build [--rebuild]` reconstruye desde `kimun.include.edn`; `include check` compara declaración, git y store (`13-include.md`). No es «reconstruir y comparar» [I] |
| Layout `.sldb/` | ausente como formato vivo | el formato es `.kimun/` (`06 §2`); solo hay importación en un sentido (§3) |
| Modelos tipados (pydantic) | parcial | descriptores EDN (`07`); puente `models define --from-python`; motor spec 16 sin terminar |
| Generadores (PythonSymbolDoc, CliCommandDoc, SurfaceDoc) | funciona para el conjunto v1 | `src/kimun/surface/adapters.cljc:13-17`; `python/kimun/adapters/`; protocolo en `11` |
| Documentos índice | ausente como documentos | índice derivado en caché |
| Análisis de grafo | funciona | `src/kimun/graph/` (6 archivos) y el grupo `graph` |
| Markdown | funciona dentro del perfil SLDB-MD | tablas, footnotes, imágenes y `_` como énfasis quedan fuera (`12 §1`, deuda de diseño) |
| Documentos YAML | no encontrado [I] | solo `--format yaml` de salida y el lector de la migración |
| `serve` HTTP | funciona | `06 §4`; mismo puerto y casi las mismas rutas (`12 §2`) |
| `mcp` | ausente | 0 coincidencias en `src` |
| `views`, `docs select/replace`, `stores scaffold`, `graph project` | ausente [I] | entraron a sldb después de la línea base de paridad |

## 3. Camino de swap

- **Capa de compatibilidad o binding:** no existe.
- **Importador:** `kimun migrate --from-v1 [PATH] [--pythonpath P] [--dry-run] [--only MODEL]
  [--limit N] [--force]` (`06 §3`). [V] Lee `core/store_index.yaml` y `core/documents`
  (`src/kimun/surface/migrate.cljc:12-13`) e importa las clases de modelo por Python (fallo
  `:model-not-importable`, `migrate.cljc:17`).
- **Última corrida:** [V] `runs/parity/migrate-2026-10-06-escala.md`, 1.551 documentos. Pasada
  estricta: 1.504 migrados y 47 fallidos por `:no-round-trip`, que entraron con `--force`.
  Contra v1: 1.544 payloads iguales y 7 distintos; 2 entradas de v1 ya estaban desfasadas.
- **Sin probar:** esa corrida no se repitió contra el `.sldb` de hoy (shards por documento,
  `store.yaml` declarativo, 43 modelos y unos 3.092 documentos en el store de sldb).
- **Consumidores sobre kimun:** ninguno.
- **Conformidad contra el sldb actual:** no existe. Hay corpus diferenciales
  (`test/fixtures/parity`, `test/kimun/parity`, `scripts/parity/`), todos contra la línea base v1.

## 4. Bloqueadores

**Falta escribir**

1. El motor de modelos capaz de alojar los modelos actuales de sldb (`M1`, luego `M2`).
2. La identidad bajo el nivel de documento (`K1e`, `S2b`), con `D-G` abierta para la capa G.
3. Una suite de conformidad contra el sldb de hoy.
4. Equivalentes de `stores build --check`, documentos índice, documentos YAML, `mcp` y `views`,
   o la decisión explícita de no portarlos.
5. Borrado de objetos inalcanzables: [V] `10-escala.md:598`, «no existe todavía un comando que borre».
6. Lock en `include build --rebuild`: [V] `13-include.md:70`, dos concurrentes «no están coordinados».
7. `fsync`: [V] `10-escala.md:192`, cubre la muerte del proceso y no un corte de energía.
8. El contrato estable de superficie y, después, el wrapper Python (decisión 47; filas `RS2` y `C2`).

**Existe pero no está probado**

- El formato 4 con esquema de rutas 2 sobre un store real.
- `migrate --from-v1` contra el `.sldb` actual.
- Lecturas con la caché borrada: `journal verify` falla con `store/corrupt` y `journal show`
  pierde texto (`12 §1`, avance C1 fase 1).
- Fallo entre el commit y la escritura del archivo: `stores update` no lo repara (`12 §1`, avance F1).

**Señales de estabilidad**

- El formato en disco pasó por las versiones 1 a 4 entre fines de agosto y octubre.
- Los commits llegan por ráfagas: 419 en total, los últimos 72 del 14 y 15 de noviembre.
- Un solo autor humano.

## 5. Los seis problemas de rendimiento de sldb

Detalle del lado de sldb en `sldb-rendimiento.md`.

| Problema en sldb | Estado en kimun hoy |
|---|---|
| Reprocesa todo en cada operación | **A medias.** `open` no repite el log y hay caché por documento (`10 §11`). Pero `stores check/health/sense/update` vuelven a hashear cada objeto alcanzable y cada archivo trackeado en cada corrida, sin atajo por mtime. [V] `stores check` midió 18,7 s en 1.551 documentos tras `stores upgrade` (`10-escala.md:232`). El árbol Merkle por ruta existe, pero el sensor no baja por él todavía («coste incremental» sigue abierto en `12 §1`) |
| Parseo y E/S lentos | **A medias.** El store no usa YAML (objetos EDN canónicos). Sigue habiendo un archivo por objeto salvo `stores pack` explícito. El parser Markdown por defecto es el nativo interpretado. No hay comparación medida contra sldb |
| Arranque del CLI | **Resuelto.** [V] `bin/kimun --version` en 235 ms (`runs/bench/after-2026-10-06.txt:71`); la promesa es menos de 1,5 s (`tests/promises.md:207`) |
| Store derivado en git, desfasado y con conflictos | **Resuelto para git.** El store va con `:git :ignore` y se construye al vuelo, así que no hay nada que fusionar. El costo es un rebuild por checkout: [V] 13 s para 539 documentos (`10-escala.md:316`) |
| Sin mantenimiento en segundo plano ni concurrencia | **A medias.** Lock de commit entre procesos con `store/busy`, `store/stale` y `--wait`, probado con escritores concurrentes (`10-escala.md:167-181`). `stores heartbeat` solo observa y cada latido recorre todo. El daemon de recálculo está diseñado, no escrito |
| Consumidores por subprocess | **No lo aborda hoy.** Es lo que cubren `RS2` y `C2` |

**Consecuencia:** el problema que más pesa en sldb hoy, volver a procesar lo que no cambió,
tampoco está resuelto en kimun en caliente. El reemplazo no sustituye el trabajo de velocidad
en sldb, y ese trabajo define qué tiene que igualar kimun para considerarse probado.
