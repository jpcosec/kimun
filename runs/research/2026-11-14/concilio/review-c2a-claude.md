# Revisión de C2a (registro de rutas) y propuesta para C2b — Claude

Solo lectura sobre el worktree de Pi `/tmp/kimun-c2a-wt` (base `f837069d`, sin commitear) y
`src/kimun/kernel/route.cljc` de `main`. No edité nada suyo ni corrí su suite. Corrí dos scripts
pequeños bajo `memcap` para medir hashes; sus resultados están en §4.

> **Estado (actualización posterior):** las secciones 1–3 describen el **WIP anterior** de C2a
> (caché de rutas con digest en `heads.edn`). Pi lo reemplazó por un mapa de rutas como objeto
> durable del CAS. El diagnóstico vigente está en **§6**; la decisión sobre `:content-hash`, en **§7**.
> El §4 (hash de proyección) sigue vigente.

## 1. Resumen (WIP anterior)

C2a es coherente en lo que hace: la raíz de rutas no incluye el `tree-id`, la caché se valida al
abrir y una ruta con dos dueños se rechaza. Encontré **un riesgo alto** (la caché de rutas no es
reconstruible si también falta la caché de planes), **dos medios** y tres cosas que C2b debe
resolver en la superficie. Ninguno bloquea K3, ya integrado en `076be35b`.

## 2. Hallazgos (WIP anterior; estado actual en §6)

### Alta — borrar la caché de rutas puede bloquear las escrituras sin salida

> **Resuelto** en el diseño posterior (mapa de rutas como objeto durable del CAS): ver §6.

- Dónde: `revision.cljc` (`register-tree-op`, `remove-route-op` rechazan con
  `:route/rebuild-required` si `:routes-available?` es falso) y `store.cljc` (`RouteCache`: «may be
  rebuilt by replay from the `:route` values in the log»).
- El log formato 3–4 guarda `{:id :revision}`, sin plan. Desde `f837069d` el plan resuelto vive en
  la caché de planes. Si se borra `cache/` entero, que `docs/v2/14-juego-del-lenguaje.md §19`
  declara borrable sin perder nada, no queda de dónde repetir los `:route`.
- Efecto: el store abre, pero todo `docs track|update|ingest` y `stores update` falla hasta un
  `include build --rebuild`, que hoy no cubre documentos fuera del include.
- **Pido a Pi una de estas dos soluciones concretas:**
  1. *(la que recomiendo)* llevar la entrada de ruta en el **descriptor del árbol registrado**
     (`:route {:path :fingerprint :projection-hash}` junto a `:class` y `:content-hash`, que ya
     están). El mapa de rutas se recalcula plegando `tree-descriptors` de la cabecera, dentro del
     kernel y sin replay. La caché pasa a ser solo aceleración.
  2. si se mantiene el diseño actual: que la falta de caché **no rechace escrituras**. Se
     reconstruye desde lo que la superficie ya registra (ancla externa y descriptor) o, si no se
     puede, la raíz se marca `route-unbuilt` y se sigue escribiendo.

### Media — `:routes-cache-digest` en `heads.edn` hace durable un dato nominal

> **Ya no aplica:** ese digest se reemplazó por `:routes-object`, el id del objeto del CAS (§6).

- Dónde: `store.cljc` (`heads-record`, `well-formed-heads?`), `route.cljc` (`entries-digest`).
- El digest incluye `tree-id`. Resuelve bien la tensión «la raíz excluye el `tree-id` pero la caché
  lleva el localizador»: la caché alterada con otros `tree-id` se detecta. El costo es que
  `heads.edn`, el punto de commit, queda con tres formas válidas y con un valor que cambia en cada
  reconstrucción aunque la raíz no cambie.
- Con la solución 1 de arriba sobra: la caché se verifica recalculando desde los descriptores.
- Si se conserva: documentar en `docs/v2/10-escala.md` que no es parte de la raíz estable y probar
  que un `heads.edn` de dos claves (store anterior) sigue abriendo.

### Media — un store con documentos anteriores a C2 tendría una raíz parcial

- Los árboles registrados antes de C2 no tienen `:route`. La primera escritura posterior produce una
  `:routes-root` que cubre solo los documentos nuevos y parece completa.
- Pido: una raíz solo se informa como tal si todos los árboles `:cached?` de la cabecera tienen
  ruta; si no, `route-partial` con el conteo. Con la solución 1 esto se calcula solo.

### Baja — códigos de salida de los rechazos nuevos

- `:route/ambiguous` cae en exit 2 por nombre. `:route/valid` y `:route/rebuild-required` llegan
  como rechazo de plan; conviene un test de CLI que fije su exit (3, no 70).

## 3. Lo que C2b debe hacer en la superficie (vigente, salvo el punto 4)

1. **Cambio de ruta.** Hoy un árbol puede quedar con dos rutas: `register-tree` con otra `:route`
   añade la nueva y deja la vieja. `docs update NAME --path`, y en K3
   `kimun.surface.docs/generic-update-plan` cuando reancla, deben emitir `:remove-route` de la
   ruta anterior en el mismo plan.
2. **Untrack.** `untrack-plan` debe emitir `:remove-route`; si no, la ruta queda tomada por un
   documento oculto y volver a trackear el archivo da `:route/ambiguous`.
3. **Colisión de ruta entre tipado y genérico.** K3 ya rechaza en la superficie ingerir un archivo
   que otro documento ancla (`refuse-anchored!`, `docs/exists`). `docs track` de un archivo que ya
   ancla otro documento con otro nombre no se rechaza en la superficie: con C2 lo rechazará el
   kernel. Conviene que la superficie lo traduzca a `docs/exists` con el nombre del dueño.
4. **Documentos genéricos y `:content-hash`** (*propuesta retirada; la vigente está en §7*). `route/entry` exige `:content-hash` como string. Un
   documento de clase `generic/md` no tiene payload ni `:content-hash`. Propongo hacerlo opcional en
   `route` antes que duplicar el hash de proyección en ese campo.

## 4. Hash de proyección estable (lo que pidió el coordinador)

**El problema es real.** `registry` y `generic-ops` hacen `(assoc-in ast [:attrs :doc] ULID)`
antes de `md/ast->tree`. Medido: el `:merkle-root` cambia con el ULID y no cambia con `tree-id`,
nombre ni clase. Usarlo como `:projection-hash` haría inestable la raíz de rutas tras un rebuild.

Medí dos candidatos sobre el mismo texto, con dos ULID distintos y sin ULID:

| candidato | igual entre ULID | igual tras reformatear | cambia al editar |
|---|---|---|---|
| A. digest canónico del AST neutro de `ast/parse`, antes del `assoc-in` | sí | sí | sí |
| B. digest de los hashes de los hijos de la raíz del árbol ya construido | sí | sí | sí |

Sobre la idea de usar el AST neutro (A):

- **Es canónico:** `canon/valid?` lo admite; sus claves son `:type :attrs :children :text :marks
  :level :ordered :lang :format :blob`, sin spans ni offsets. Un texto en NFD y el mismo en NFC dan
  el mismo digest. Debe tomarse con `canon/digest`, no con `pr-str`.
- **Es suficiente** para distinguir reformateo (cambia `:fingerprint`, no la proyección) de edición
  (cambian ambos).
- B da lo mismo sin un segundo recorrido, pero ata el hash a la codificación del árbol. Prefiero A
  por ser independiente de esa codificación; el costo es un digest más por documento escrito.

**El contrato de parse** (*el campo `:contract` que sigue lo retiré en §7*). No lo metería dentro de `:projection-hash`: un cambio de parser que
produce la misma estructura aparecería como «proyección cambiada». Propongo un campo aparte en la
entrada, `:contract` = digest de `kimun.surface.parse-contract` (K6), que ya cubre parser, gramática,
normalización, extractor y clase. Como ningún store tiene todavía una raíz registrada, subir el
esquema a `kimun.route/2` cuesta solo regenerar el golden de `test/fixtures/kernel/route-entries.edn`.
Si Pi prefiere no tocar el esquema, la alternativa es
`:projection-hash = digest {:structure A, :contract digest}`, documentando que mezcla las dos causas.

Pruebas que pediría a C2b:

- misma raíz en dos rebuilds de los mismos archivos con ULID nuevos;
- raíz distinta tras editar un archivo, y `diff` nombrando solo esa ruta;
- raíz igual en `:projection-hash` y distinta en `:fingerprint` tras un reformateo;
- raíz distinta al cambiar el contrato (por ejemplo `store-cache/parser-version`) con la fuente intacta.

## 5. Límites de esta revisión

- Leí el diff de `src/` de C2a; no leí su test `route_store_test.cljc` ni su evidencia.
- No verifiqué el comportamiento de C2a en ejecución: los hallazgos salen de leer el código.
- Las mediciones del §4 son sobre un documento pequeño, con el parser in-process.

## 6. Revisión posterior al cambio de Pi (mapa de rutas como objeto del CAS)

Leí el diff de `src/` del worktree `/tmp/kimun-c2a-rb` (base `076be35b`, sin commitear). No corrí
sus tests ni leí su evidencia (`runs/subagents/2026-11-14-k2a-route-cas/`).

**Lo que verifiqué leyendo el código:**

- El mapa completo (`route/map-object`: `{:route/version :entries}` con `:tree-id`) entra como
  objeto nuevo en el mismo commit; `heads.edn` lleva `:routes-root` y `:routes-object`.
- Al abrir se lee ese objeto del CAS y se comprueba que es un mapa de rutas, que su digest es
  `:routes-object` y que su raíz es `:routes-root`. La caché de `cache/routes` queda como aceleración.
- El objeto entra al conjunto alcanzable que recorre `verify`.
- La raíz sigue sin incluir el `tree-id`; el id del objeto sí. La tensión raíz/localizador queda
  resuelta sin poner un dato de caché en el punto de commit.

**El hallazgo alto queda resuelto por diseño:** borrar `cache/` ya no deja al store sin rutas,
porque el mapa está en `objects/`. `:route/rebuild-required` solo puede ocurrir si falta un objeto
del CAS, que es corrupción y ya tiene su diagnóstico.

**Riesgos que quedan:**

1. **Media — crecimiento del store.** El mapa es un objeto plano que se reescribe entero en cada
   commit que toca una ruta, y los anteriores quedan en el CAS. Con N documentos, cada `docs update`
   suelto añade un objeto de N entradas. Un `include build` o un `stores update` componen una sola
   revisión y no sufren; mil ediciones sueltas sobre 1.500 documentos sí. Pido una medición con
   `bb store-size` antes y después de, por ejemplo, 50 `docs update` sobre el desk, y decidir si el
   mapa pasa al mmap con estructura compartida que ya usan `:trees` y `:edges`.
2. **Media — raíz parcial.** Sigue igual que en §2: un store con documentos registrados antes de C2
   tendría una raíz que cubre solo los nuevos. Falta `route-partial` o exigir rebuild para informarla.
3. **Baja — texto del rechazo.** `:route/rebuild-required` aún dice «route cache is absent; replay or
   rebuild»; con el mapa en el CAS ya no describe la causa.
4. **Baja — formas de `heads.edn`.** Se aceptan tres: sin rutas, con `:routes-root` sola y con
   `:routes-root` + `:routes-object`. Si la segunda ya no se escribe nunca, conviene retirarla o
   decir para qué queda.
5. **Sin cambio — lo de la superficie (§3).** Cambio de ruta y untrack siguen necesitando
   `:remove-route` en el mismo plan. Desde `43d9db6d` un documento `generic/md` ya se puede
   desanclar; ese plan todavía no retira ninguna ruta porque C2a no está en `main`.

## 7. `:content-hash` para `generic/md`: mantener el esquema 1

Retiro mi propuesta del §3.4 de hacerlo opcional. Recomiendo **mantener `kimun.route/1`** con esta
definición, que hace que la coincidencia en el genérico sea una consecuencia y no un accidente:

| campo | qué cubre | tipado | `generic/md` |
|---|---|---|---|
| `:fingerprint` | los bytes de la fuente | hash del texto | igual |
| `:class` | qué clase selecciona | id del descriptor del modelo | `"generic/md"` |
| `:projection-hash` | la **estructura** leída | digest canónico del AST neutro (§4, opción A) | igual |
| `:content-hash` | lo que la **clase selecciona** de esa estructura | hash del payload extraído (el que ya existe) | digest canónico del AST neutro |

- En un documento tipado los dos hashes difieren con sentido: cambiar azúcar que el modelo no
  selecciona mueve `:projection-hash` y no `:content-hash`.
- En `generic/md` la clase selecciona la estructura entera (`docs/v2/14-juego-del-lenguaje.md §18.1`:
  «la estructura… cortada a una granularidad declarada», y hoy no hay corte). Los dos hashes
  coinciden **por definición de la clase**. Cuando exista un perfil que corte la granularidad
  (spec 16), `:content-hash` del genérico se separará solo, sin cambiar el esquema.
- `§20` solo exige fingerprint, clase y proyección; el esquema 1 ya los cubre y el golden de C1 no
  se toca.

**El contrato de parse no entra a la raíz.** También retiro el campo `:contract` que propuse en §4.
La raíz de rutas dice qué contenido está trackeado; cómo se lee lo sensoriza K6 desde la arista de
anclaje (`contract/changed`). Un cambio de parser que deja la misma estructura no mueve la raíz; uno
que la cambia mueve `:projection-hash`. Si el dueño quiere que la raíz se mueva ante cualquier
cambio de contrato, eso sí pide un campo explícito y `kimun.route/2`.

Pruebas que pediría a C2b para este contrato:

- genérico: `:content-hash = :projection-hash`, con un test que diga que es intencional;
- tipado: editar texto que el modelo no selecciona cambia `:projection-hash` y no `:content-hash`;
- misma raíz en dos rebuilds con ULID nuevos, para un store con un tipado y un genérico;
- raíz distinta tras editar; raíz igual con `:fingerprint` distinto tras un reformateo.

