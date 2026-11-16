# REPORT proyecto-declaraciones (corte 3a) — 2026-11-16

Worktree `/tmp/kimun-proj-wt` (sin commit). Evidencia: `lint.txt` (107 archivos ok), `oracle.txt` (9 filas, 0 desajustes),
`directed.txt` (54 pruebas, 367 aserciones, 0 fallos: mis 2 namespaces, declare-test, declared-anchors-test, cli declare-test,
declare-anchors-test, main-test, golden-test, lint-test), `joint.txt` (mis 2 + 20 de `test/kimun/cli/` en una invocación: 184 pruebas,
2196 aserciones, 0 fallos), `mutation.txt`.

## Archivos
- `src/kimun/surface/declare.cljc`: `read-project` (nueva, pura) y `read-declarations` (ahora un caso de `read-project`); `ns-info`/`libspec`/`scan`/`order`/`evaluate-file`/`prelude`.
- `src/kimun/cli/commands/declare.cljc`: `project-dir` (única definición de `"kimun"`), `project-files`, `read-set`, `add-form`, `write-atomic!`; `read-files` y `anchors-of` pasan por `read-set` (se borra `read-file`, ya sin uso); `spec` gana `:context`; el mapa `commands` y el mensaje de subcomando desconocido ganan `add`. Bloques nuevos, nada reordenado.
- `src/kimun/cli/main.cljc`: solo las 3 líneas de ayuda de `declare`. `docs/v2/06`: filas `declare read`, `anchors`, nueva `add`. `docs/v2/drafts/diseno-evaluar.md`: §10 «Decidido al implementar el corte 3a» (no se tocó §9).
- Pruebas nuevas: `test/kimun/surface/declare_project_test.cljc` (7), `test/kimun/cli/declare_project_test.cljc` (8). Ajustadas: `surface/declare_test` (`:r` calificado, id congelado de `isa` ahora `44d0eb63…bbce`, `ns-clauses` con `:import`/`:use`, y `:require [clojure.string]` ahora es `unknown-context`), `cli/declare_test` y `cli/declare_anchors_test` (sin FILE ya no es error de uso: es el proyecto; la anchors pasa a probar `--since` sin valor). `declared_anchors.cljc` solo propaga `:r`: sin cambio.

## Cómo quedó la evaluación de varios contextos con sci
Cada archivo, un contexto sci propio, fijado a su namespace. Para cada contexto que requiere, antes de evaluar el texto se evalúa en el namespace sci de ese
contexto un `defmacro` por cada relación suya (`(defmacro isa [& args] (kimun.declare.impl/expand-as 'a/isa &form &env args resolve))`), que emite con `:r "a/isa"` y
las reglas de término/ligado del archivo que usa. Luego se evalúa el texto entero, `(ns b (:require …))` incluido: el `ns` de sci resuelve `:refer` y `:as` solo, porque los
namespaces ya existen (sin `load-fn`). Los `def` de `a` nunca entran al sci de `b`. Orden: DFS por requires, empates por entrada, cada contexto una vez; resultados en orden de entrada.
Comprobaciones previas a evaluar: sintaxis, `duplicate-context`, `unknown-context`, `require-cycle`; `unknown-relation` al llegar al archivo que la pide.

## Salida real (`declare read` sobre un proyecto temporal; a.clj: `(ns a)(relation isa "d")(isa x Y)`; sub/b.clj: `(ns b (:require [a :refer [isa]]))(isa z Y)`)
`:files` = `[{:file "kimun/a.clj" :context "a" … :propositions [{:r "a/isa" :a {:term "x"} :b {:term "Y"} :context "a" :at {:file "kimun/a.clj" :line 4} :id …}]}
{:file "kimun/sub/b.clj" :context "b" … :propositions [{:r "a/isa" :a {:term "z"} :b {:term "Y"} :context "b" :at {:file "kimun/sub/b.clj" :line 2} :id …}]}]`, `:summary {:files 2 :relations 2 :propositions 2}`.
Errores reales: `b.clj:3: context `a` does not define a relation `nope`` (`declare/unknown-relation`, `:line 3`); `a.clj:1: contexts require each other: a -> b -> a` (`:cycle ["a" "b" "a"]`).
`declare add` sobre b.clj con `(isa new Thing)`: `{:file "kimun/sub/b.clj" :added true :propositions [{:r "a/isa" … :at {… :line 3}}] :relations []}`.

## Qué cubren las pruebas
Puras: `:refer` y `:as`; `:r` calificado por el contexto que define y `:context` el que escribe; `:relations` solo las propias; homónimas `isa` en `a` y `c` dan `:r` e `:id` distintos;
orden de entrada irrelevante (3 permutaciones, resultados iguales); un contexto requerido por dos se evalúa una vez (contador sobre `evaluate-file`); `def` de otro contexto no visible
(ni por `:refer` ni por alias); un solo requerido por relación importada (no re-exportada); cada error con archivo y línea (línea del libspec, ciclo, duplicado en el segundo archivo,
9 formas de `ns-clauses`); evaluación idéntica llamando con otro `*ns*`. CLI en directorios temporales: proyecto con `kimun/a.clj` y `kimun/sub/b.clj` (y un `.txt` y un `.clj` fuera, ignorados), sin carpeta y carpeta vacía (`:ok true`, vacío; read y anchors),
archivos explícitos (solo esos), `anchors` sin archivos, `add`: agrega (una forma en línea nueva, ningún otro archivo ni temporal), relación nueva cuenta como nueva, no evalúa (3 tipos de error, archivo byte a byte igual; también
una forma que rompe por el conjunto), ya estaba (`:added false` con `:reason`, igual), `--context` crea (con carpeta intermedia), sin archivo ni `--context` es uso (6).
Mutaciones (`mutation.txt`): 13, 12 detectadas; sobrevive «evaluación no fijada al namespace», equivalente: como el primer form es `(ns ctx)`, sci cambia solo al namespace correcto, así que el `:ns` fijado quedó defensivo.

## Límites
- La línea de un error de `:require` se busca como ficha entera del nombre desde la línea del `ns` (el lector no da línea a símbolos/vectores); un mismo contexto en dos libspecs da la primera aparición.
- `declare add` no comprueba que el texto sea una sola forma; no valida que `--context` coincida con el `ns` de un archivo existente (se ignora en ese caso).
- Una relación propia con el nombre de una importada por `:refer` falla como `declare/eval` (sci: «already refers»), sin tipo propio.
- Sin tope de tiempo en evaluar (heredado); `add` evalúa el proyecto entero dos veces (antes y después) para saber qué es nuevo.
- `--to` es la opción compartida de `edges` (su descripción en la ayuda de opciones habla de ID de aristas).
- No se verificó el comportamiento con enlaces simbólicos ni rutas absolutas fuera del cwd (se aceptan tal cual como archivo explícito).

## Ambigüedades, incomodidades, posibles errores del diseño §9
1. «`:r` con el contexto que define»: se calificó también el uso dentro del propio contexto (`a/isa` en `a`). Cambia todos los `:id`; el id congelado cambió.
2. `:context` (quien escribe) y la parte de contexto de `:r` (quien define) ahora pueden diferir; la prosa de §9 no dice si `:context` debía seguir siendo el que escribe. Elegí que sí.
3. Un `(:require a)` sin `:as`/`:refer` es una dependencia válida pero inerte (solo `a/rel` calificado); no está en el brief, lo dejé aceptado.
4. Sin archivos, si hay conjunto parcial (explícitos) un archivo que requiere otro no nombrado falla: coherente con «solo esos forman el conjunto», pero incómodo para `declare read kimun/b.clj` a mano.
5. «Nueva» en `add`: la proposición repetida con otra forma de escribirla es la misma (mismo id) y cuenta como ya estaba; una relación con el mismo nombre/docstring también.
6. Cuesta señalar la línea de un libspec con el lector de sci (ver Límites); una tarea de lector propio lo arreglaría.
7. Compartir también `def`/`defn` con sci habría sido factible (definirlos en el namespace sci del contexto y dejar que el `ns` los resuelva), pero no se hizo, como se pidió: reporto que no cuesta más que lo hecho para las relaciones.
