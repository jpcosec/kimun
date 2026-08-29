# SLDB v2 — Estándares mínimos de código

> Reglas que `bb lint` hace cumplir antes de cada `bb test`. Lo que no se puede
> comprobar mecánicamente se lista como norma de revisión. Autoridad: por debajo de
> `01` y `02`, por encima de cualquier atom de práctica.

## 1. Anillos de dependencia

```
ring 0  sldb.kernel.*   ← puro: datos + funciones; requiere SOLO clojure.* y sldb.kernel.*
ring 1  sldb.host.*     ← adaptadores: implementan los puertos del kernel; único lugar con
                          reader conditionals (#?), imports de plataforma e I/O
ring 2  sldb.surface.*  ← superficies (CLI, importers/emitters, adaptadores externos):
                          requieren kernel y host; nunca al revés
ring 3  tests           ← requieren cualquier cosa
```

- **El kernel no importa nada de fuera**: ni `sldb.host.*`, ni `babashka.*`, ni `java.*`
  ni `js/*`. Lo que necesita de la plataforma (hash, normalización de texto, ids,
  reloj, ficheros) lo define como **puerto** (un `defprotocol`) en `sldb.kernel.ports` y
  lo recibe como argumento (`host`). Ningún `def` global mutable, ningún dynamic var
  para inyectar el host.
- **Un puerto por capacidad** (`Hasher`, `TextNormalizer`, `IdMinter`, `Backend`);
  `sldb.host.default` compone la implementación estándar del host Babashka/JVM.
- `sldb.kernel.store` define el puerto `Backend`; `sldb.host.fs-store` lo implementa.
- Dirección de las flechas: `ring n` → `ring < n` siempre; nunca lateral hacia otro
  adaptador ni hacia fuera. El script `scripts/check_rings.clj` falla ante cualquier
  `require` que rompa esto.

## 2. Compartimentación

- Un namespace = una responsabilidad nombrable en una frase (la primera línea del
  docstring del `ns`). Si hace falta un "y", son dos namespaces.
- Tamaño orientativo: ≤ 300 líneas por namespace del kernel; ≤ 40 líneas por función.
- El kernel expone funciones sobre **valores**: reciben el store/árbol/nodo y devuelven
  uno nuevo. Las únicas funciones con efectos viven en `sldb.host.*` y terminan en `!`.
- Los errores son `ex-info` con `:type` namespaced (`:canon/invalid-value`,
  `:node/invalid`, `:edge/invalid`, `:tree/invalid`, `:plan/rejected`, `:plan/conflict`,
  `:store/error`, `:store/stale`) y datos suficientes para reproducir el fallo. Nunca
  `throw` de strings ni excepciones de plataforma desde el kernel.

## 3. Docstrings

- Todo `ns`, `defn`, `defmacro`, `defprotocol` (y cada método), `def` público y
  `defrecord` público lleva docstring. `defn-` puede omitirla si el nombre basta.
- El docstring dice **qué** garantiza y cita la sección de `docs/v2/02` que implementa
  (`"(§3.1)"`), no cómo está escrito.
- `bb lint` falla si un var público del kernel o de host no tiene docstring.

## 4. Nombres y forma

- Kebab-case; predicados terminan en `?`; efectos en `!`; conversiones `a->b`.
- Datos antes que macros; `defrecord` solo para implementar protocolos.
- Nada de `declare` cruzado entre namespaces; los ciclos se resuelven con puertos.
- Reader conditionals solo en `sldb.host.*`; `#?(:clj …)` sin rama `:cljs` es una deuda
  registrada en el drawer de paridad Node, no un permiso.

## 5. Tests

- Un `*_test.cljc` por namespace, mismo nombre.
- Para cada promesa normativa de `docs/v2/02` (regla, fila de tabla, invariante) existe
  un test que **fallaría si la promesa se rompiera**; la tabla
  `docs/v2/tests/promises.md` lo registra. Un test que compara la función consigo misma
  no cuenta.
- Tres capas por hito: fixture dorado congelado (`test/fixtures/*.edn`), propiedades
  test.check con generadores compartidos (`test/sldb/kernel/generators.cljc`) y casos
  negativos (cada `reject`/`fail` del código tiene al menos un test que lo dispara).
- Los fixtures se generan **una vez** con la implementación, se revisan a mano y se
  congelan; regenerarlos exige una nota en el propio fichero con el motivo.
- Cada corrida de cierre queda en `runs/subagents/<run>/validation.log`.

## 6. Aplicación

- `bb lint` (anillos + docstrings) corre antes de `bb test`; ambos deben estar en
  verde para cerrar una task.
- Las revisiones de código de tasks futuras citan este documento; una desviación se
  registra como deuda en `desk/inbox` con `--kind suggestion`, nunca en un comentario.
