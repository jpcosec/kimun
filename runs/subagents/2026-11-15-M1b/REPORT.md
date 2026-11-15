# M1b — compilar el `:template` v1 a selector (vista derivada)

## Archivos (solo nuevos, sin commit)
- `src/kimun/surface/models/compile.cljc` — `template->spec16` (pura, 1 función pública).
- `test/kimun/surface/models/compile_test.cljc` — 6 pruebas, 81 aserciones.
- Evidencia: `runs/subagents/2026-11-15-M1b/{lint,directed,mutation}.txt`.
No se tocó nada más (ni `descriptor`, `store`, `extract`, `subgraph`, `docs`, `cli/`, kernel, `docs/v2/12|16`).

## Qué hace
`(template->spec16 d)` valida `d` con `descriptor/validate!` (así `py•` sigue rechazado) y devuelve un descriptor spec 16 completo:
`:model/name`, `:model/version` (1 si falta), `:base`, `:document-role :source`,
`:substrate {:parser :sldb-md/blocks :compat :legacy-v1-template}`,
`:anchoring {:fields <los de v1> :selector (vec (recipe/recipes template))}`,
`:generation {:projections {:markdown/v1 {:format :markdown :template <texto>}}}`.
No incluye `:template` ni se mezcla con `d`. No ejecuta el selector.

## Resultados
- directed (compile-test + subgraph-test): 12 tests, 104 aserciones, 0 fallos (`directed.txt`).
- `bb lint`: ok, 106 archivos (`lint.txt`).
- Comparación con R3 (`R3-selectors/edn/{ChecklistDoc,AtomDoc,TaskDoc}.edn`): el selector compilado es **igual paso a paso** (8, 4 y 20 pasos) y `:base` coincide. **Cero diferencias que documentar.** (Los templates salen de `test/fixtures/models/deskops/*.edn`, congelados; los modelos del desk se retiraron el 2026-10-09.)

## Pruebas (las 6 del plan)
1. `output-validates-as-spec16`: la salida pasa `subgraph/validate!` para los 3 modelos; compat `:legacy-v1-template`, parser `:sldb-md/blocks`, existe `:markdown/v1`.
2. `selector-equals-the-r3-files`: igualdad por paso con los 3 archivos R3.
3. `the-stored-descriptor-is-not-touched`: `store/serialize` de `d` idéntico antes/después; la vista no contiene `:template`; su serialización difiere de la de `d`; `d` no gana `:anchoring`/`:generation`.
4. `py-markers-are-still-rejected`: `⸢py•x⸥` -> `:models/invalid` con "py•".
5. `compilation-is-deterministic`: dos compilaciones `=`; la vista sobrevive `pr-str`/`edn/read-string`.
6. `render-marker-lives-in-the-projection`: `⸢render•details⸥` está en el texto de la proyección y en ningún marcador del selector (solo queda `title`).

## Mutación (`mutation.txt`, base: 0 fallos)
- M1 selector sin su primer paso: 33 fallos (pruebas 2 y 6).
- M2 vista mezclada con el descriptor (`merge d …`, conserva `:template`): 3 fallos (prueba 3).
- M3 proyección sin el texto del template: 1 error (prueba 6).
- M4 sin validación v1: 2 fallos + 1 error (prueba 4).
- M5 compat `:strict`: 3 fallos (prueba 1).
No mutado: la prueba 5 (determinismo) no tiene mutación propia, y la prueba 3 no detecta mutar `d` in situ (imposible: valores inmutables); detecta mezclar la vista en la salida.

## Ambigüedades / decisiones mías (spec 16)
- Forma de `:markdown/v1`: `16:119` solo dice «una proyección de salida `:generation/:projections :markdown/v1`» sin su contenido. Elegí `{:format :markdown :template <texto>}` (sin `:mode`/`:fields`); M1e (`project`) deberá fijarla. No fijada por el spec.
- Qué cuenta como "marker render queda en la proyección" (`16:116-117` lista `⸢render•…⸥` entre los marcadores que compilan "a" selector y proyección, sin decir cómo): lo interpreté como lo indica el plan (§3.2): fuera del selector, dentro del texto de la proyección. Hoy `recipe` ya no lo emite como campo.
- `:model/version` ausente en v1: pongo 1 (v1 lo hace opcional, spec 16 lo exige, `subgraph.cljc` check). Decisión mía.
- `:contract/digest`: no se emite (lo calcula K6, M1h).

## Límites
- Solo lleva `:fields`, `:base`, `:name/version`, template. Se pierden (no pasan a la vista) `:state` y claves `:py/*` del descriptor v1 (`:py/references`, `:py/containment`).
- Los campos v1 sin `:type` fallarían `subgraph/validate!` (exige tipo válido); los 3 fixtures lo traen. No verificado con los otros 20 descriptores migrados.
- El selector contiene `:col-markers` como `sorted-map` (de `recipe`); es `=` tras round-trip EDN, pero no se probó una tabla (los 3 modelos y el de render no la usan).

## Propuesta para la fila M1 de `docs/v2/12`
«M1b implementada: `kimun.surface.models.compile/template->spec16` compila el `:template` v1 a `:anchoring :selector` + proyección `:markdown/v1` (vista derivada, `:compat :legacy-v1-template`); el selector de AtomDoc/ChecklistDoc/TaskDoc es igual a R3; pendiente M1c–M1l.»
