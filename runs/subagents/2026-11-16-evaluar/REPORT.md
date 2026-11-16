# REPORT evaluar (corte 1: Leer) — 2026-11-16

## Archivos
Nuevos: `src/kimun/surface/declare.cljc` (evaluador puro), `src/kimun/cli/commands/declare.cljc` (grupo `declare`),
`test/kimun/surface/declare_test.cljc` (8 pruebas), `test/kimun/cli/declare_test.cljc` (2 pruebas).
Tocados (una línea/bloque mínimo): `src/kimun/cli/main.cljc` (require, grupo, `declare/spec` en el merge, una línea de ayuda),
`src/kimun/cli/errors.cljc` (`"declare" 3`), `docs/v2/06-superficies-y-cli.md` (sección `### declare` con la fila, y `declare` en la fila de exit 3).
Evidencia: `lint.txt`, `directed.txt` (60 pruebas, 586 aserciones, 0 fallos), `mutation.txt`, `oracle.txt` (9 filas, 0 desajustes).
`sci.core` está disponible en el bb de la máquina (`(require 'sci.core)` ok); no se añadió ninguna dependencia.

## ANILLOS: estorbo (hay que decidir)
`scripts/check_rings.clj` no admite ningún prefijo `sci.` en ningún anillo (el catch-all `kimun.` admite `clojure. kimun.kernel. kimun.host.
kimun.surface.markdown. kimun. babashka. cheshire. clj-yaml. org.httpkit.`). Un `(:require [sci.core])` en `kimun.surface.declare` falla el lint.
No relajé el anillo: resolví `sci.core` tarde con `requiring-resolve` (función `sci-fn`), que el lint no ve. Es un rodeo, no una solución:
la corrección limpia es una línea en `rings` (`"sci."` en el anillo `kimun.`), o decidir otro anillo. Dejado a quien coordina.

## Regla exacta: símbolo desnudo frente a ligado (docstring del namespace)
Argumento de una relación que es un símbolo: es un TÉRMINO (el símbolo, con puntos, nunca resuelto como clase) salvo que esté ligado,
y entonces vale su VALOR. Ligado = (1) local del código que lo rodea (`let`, `doseq`, `for`, parámetros de `fn`/`defn`; se ve por `&env`
en la macro) o (2) var que el propio archivo definió con `def`/`defn` (`resolve` en el ns `user` de sci, no macro). `clojure.core` y el
vocabulario NO ligan: `name`, `type`, `first` desnudos son términos. Valor símbolo → término; cadena/número/keyword → literal; proposición
→ anidada por `:id`. Lista: se evalúa y debe dar proposición, `at`, `files` o datos planos (nil, funciones, etc. → `:declare/bad-node`).
Probado en los dos sentidos (`bare-symbol-is-a-term-and-bound-symbol-is-its-value`, `declarations-can-be-generated-with-code`).

## Forma de las proposiciones (salida real de `declare read`, edn)
Entrada (`proyecto.decisiones`, el ejemplo de §1): la anidada sale así:
```
{:r supports :a {:proposition "1a1b014e035ecfe49fe77f174127d1ad91a3113d5df974bd06fa830ee46b8bcc"} :b owner-2026-11-15
 :context proyecto.decisiones :at {:file "f.clj" :line 9}
 :id "407525834ba9e92b4c940d12c2923bfef257612e8d1357b45c467d654cf30a31"}
```
y la interior (línea 8, escrita otra vez dentro de la anidada en la 9) sale una vez: `{:r summarizes :a cache-is-not-a-source :b cache-decision ...
:at {:file "f.clj" :line 8} :id "1a1b01…" :also-at [{:file "f.clj" :line 9}]}`. `(isa (files "src/ui/**/*.py") UI.component)` →
`:a {:files "src/ui/**/*.py"} :b UI.component`; `(at "docs/spec.md" ["Caché"] {…})` → `{:anchor {:path "docs/spec.md" :locator [["Caché"] {:type :paragraph :n 1}]}}`.
Resultado por archivo: `{:context :relations {sym {:doc}} :propositions [...]}`; el comando añade `:file` y `:summary {:files :relations :propositions}`.
`:id` = `canon/digest` (host por defecto, SHA-256) de `{:r :a :b :context}` con los nodos ya normalizados (términos = símbolos, anidada = `{:proposition id}`).

## Qué cubren las pruebas
Ejemplo completo de §1 con proposiciones escritas a mano e id calculado independientemente + un id congelado; símbolo desnudo/ligado
(let, def, parámetro, nombres de core); generación con `doseq`, `defn` y `for`; duplicado → una con `:also-at`; `:id` estable, independiente de `:at`,
distinto con otro contexto; errores declarados con archivo y línea (relación sin definir, aridad por defecto y por exceso, docstring ausente/vacío,
sin `ns`, `ns` no primero, cláusulas de `ns`, sintaxis, nodo no válido, flotante); aislamiento (`slurp`, `spit`, `System/exit`, `java.io.File`, shell,
`require`, `load-file` rechazados, el proceso sigue vivo y no se creó el archivo); `at`/`files` sin abrir nada; CLI con dos archivos (envelope, sin store,
el directorio idéntico antes y después), fallo con exit 3 y `:file`/`:line`, archivo ausente (1), uso (6).
Mutaciones (en `mutation.txt`, con copias en /tmp, sin stash; restauradas y comprobadas con `diff`): siempre-citar, nunca-citar, id sin contexto, id con `:at`,
sin dedupe, sin aridad, sin docstring, ns-no-primero (sobrevivió hasta añadir una aserción), sandbox abierto (el `System/exit` del test mata el proceso): todas detectadas.
No se tocó ninguna prueba existente.

## Límites de sci para esto
- Sin límite de tiempo ni de memoria: un `(loop [] (recur))` o una `repeat` infinita cuelga el proceso; sci no lo acota (no se probó; es propiedad conocida).
- Los errores de sci llegan como `ex-info` envueltos; hay que recorrer `ex-cause` para hallar el declarado. La línea de un error de evaluación es la de la forma
  de nivel superior si sci no da otra.
- `relation` es macro: el nombre debe ser un símbolo literal; no se puede generar el NOMBRE de una relación con código (`doseq` sobre nombres), sí sus usos.
- Un `:deny` explícito de `slurp spit load-file load-string require use import` más lo que sci ya no trae; el aislamiento es "sci no expone nada" (no hay `:classes`), no un sandbox del SO.
- `resolve` y `&env` funcionan en las macros de sci como en Clojure; sin ellos la regla desnudo/ligado no sería posible.

## Ambigüedades / incomodidades / errores del diseño
1. **Anillos**: ver arriba; el diseño §4 pide sci y el lint no lo admite.
2. **JSON pierde la diferencia símbolo/cadena**: `:b UI.component` y `:a "a"` salen ambos como cadena en `--format json` (cheshire). El término frente al literal solo se ve en `--format edn`. El diseño §3 muestra símbolos como datos; el envelope por defecto es JSON. Decisión pendiente (¿marcar los términos en la salida, p. ej. `{:term "x"}`?). No lo cambié.
3. **§3 no dice cómo sale una proposición anidada**: elegí `{:proposition id}` (brief: "por su `:id`"). El diseño no fija la clave.
4. **«Lo que va entre paréntesis debe dar un nodo» (§2) frente a «o un valor literal» (brief)**: implementé datos planos (incluidos vectores y mapas) como válidos; un mapa de usuario `{:files "x"}` es indistinguible de `(files "x")`. Quizá deba marcarse el nodo.
5. **Símbolo ligado por `def` incómodo**: un archivo que haga `(def cache-decision …)` convierte ese símbolo en valor en todo el resto; un término homónimo desaparece sin aviso. Es consecuencia de la regla pedida; conviene decidir si un `def` debería tener que ser explícito.
6. **`ns`**: el diseño dice «el contexto es el `ns`»; implementé: `ns` primero y único, y solo nombre+docstring+mapa (cualquier `:require` es error, pues el archivo no puede requerir nada). Un `ns` no primero o repetido es `declare/no-context`. Decisión mía, no del diseño.
7. **Redefinir una relación** (mismo nombre, otro docstring) en el mismo archivo: la última gana en silencio. §6 deja abierto «dos contextos definen la misma relación»; dentro de un archivo tampoco está dicho. Las relaciones son por archivo (el resultado `:relations` es de ese archivo; no hay noción de relación compartida entre archivos).
8. **Orden de dedupe**: una proposición anidada se evalúa antes que la exterior y se registra primero con su propia `:at`; si luego se escribe aparte, esa segunda va a `:also-at`. Con la anidada que aparece antes que su «propia» línea, la `:at` canónica es la de la anidada, no la de la declaración independiente. Consecuencia del «primera `:at`», pero puede sorprender.
9. **Floats**: `canon` no admite flotantes ni racionales; `(r a 1.5)` falla con `declare/bad-node` (el diseño no lo menciona).
10. **Error de «símbolo sin resolver»** cubre también `slurp`, `System/exit` y clases: tipo `declare/unresolved` para todo lo que el sandbox no tiene. El mensaje lo dice; no los distingo de una relación mal escrita.
11. **Versionado de las relaciones en el `:id`**: el `:id` incluye solo el símbolo `:r`, no su docstring ni su contexto de definición; cambiar el sentido de una relación no cambia los ids (relacionado con §6, último punto).

# Correcciones del coordinador (segunda ronda)
1. Anillos: `"sci."` añadido solo al anillo `kimun.` de `scripts/check_rings.clj`; `declare.cljc` usa `(:require [sci.core :as sci])` (sin `requiring-resolve`); `docs/v2/03 §1` lo menciona en una frase. `lint_test` no fija la tabla: sin cambios.
2. Nodos etiquetados, iguales en EDN y JSON: `{:term "x"}`, `{:proposition id}`, `{:anchor …}`, `{:files …}`, o literal escalar (cadena, número, keyword, booleano). `:r`, `:context` y las claves de `:relations` son cadenas. Vectores/mapas como argumento ⇒ `declare/bad-node` (valen dentro de `at`). `at`/`files` marcan su nodo con metadatos internos que no salen. `:id` sobre la forma etiquetada; id congelado nuevo `3ac18f3e…672a`. Fila de `06` actualizada. Esto cierra los puntos 2 y 4 de ambigüedades.
3. `declare/relation-redefined` (archivo y línea) con otro docstring; con el mismo, nada. Cierra el punto 7.
Mutaciones nuevas (en `mutation.txt`): término sin etiquetar (4 fallos), mapa de usuario aceptado (1), redefinición silenciosa (1): detectadas. Lint, oracle y dirigidas (60 pruebas) pasan.

# Tercera ronda: evaluación fijada a su propio namespace
Causa: `sci/eval-form` evalúa en el namespace actual del proceso; el vocabulario puesto en `'user` no se veía desde otro namespace (la suite).
Arreglo (`src/kimun/surface/declare.cljc`): una primera pasada con un contexto sci desechable lee las formas (sintaxis, `ns` primero y único) y fija el contexto;
luego un contexto con el vocabulario en el namespace **del contexto del archivo** evalúa el texto entero con `sci/eval-string+ … {:ns (sci/create-ns contexto nil)}`
(líneas reales de sci, sin relleno). `arg-form` compara el `:ns` de la var con ese namespace; el `defmacro` de las relaciones cae en él. No se toca el namespace del proceso.
Prueba nueva `evaluation-is-pinned-to-its-own-namespace-whatever-the-caller` (llama con `*ns*` ligado a `otro.ns`; comprueba que el namespace del llamador no cambia y que nada se filtra): falla con la versión anterior (error `Unable to resolve symbol: relation`).
Mutación `p1` (vocabulario otra vez en `'user`): 9 fallos y 9 errores. Corrida conjunta: `joint.txt` (mis 2 namespaces + `main-test` + 20 de `test/kimun/cli/`): 158 pruebas, 2109 aserciones, 0 fallos. Lint, oracle y dirigidas pasan.
Nota: un `(ns kimun.declare.impl)` o `(ns clojure.core)` como contexto no se rechaza; sería un contexto raro, no se trató.
