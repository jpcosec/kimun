# Capa G — dudas abiertas y correcciones del dueño (2026-10-08)

> Borrador de trabajo, no autoridad. La capa G se retoma **después** de cerrar las capas S y M y el
> store. Antes de tocarla hay que leer las fuentes: el paper del dueño sobre wittgenstein/Matrix, la
> documentación de `/home/jp/proyectos/wittgenstein` (incl. `matrix-shrdlu-spec/specs/`) y, si hace
> falta, el *Tractatus* completo. El spec `15-capa-g.md` (archivado en `runs/research/2026-10-08/spec15/`) se
> escribió **sin** esa lectura y hay que revisarlo contra ella.

## Correcciones del dueño (textuales en espíritu)

1. **No existe la categoría "propuesto".** Wittgenstein separa dos categorías distintas, y kimun
   las mapea así:
   - **Sentido:** `sinnvoll`, `sinnlos`, `unsinnig`.
   - **Verdad:** **anclado** (anchored) o **derivado**.
2. **Kimun no es un store de lo que dice un humano.** Lo que dice un humano puede estar mal. Es un
   aparato para, dado un conjunto de *sources* (libros, papers, documentos, tests sobre código,
   bases de datos…), dejar **anclaje** y **formalizar** conocimiento. Más cercano a **Lean** que a
   una base de afirmaciones.
3. **Por qué grafo.** Principio base que ni las KB en grafo ni los LLMs consideran: el conocimiento
   es un conjunto de hechos que se relacionan entre sí **bajo un contexto**. Un hecho `(R A B)` se
   transmite en lenguaje, pero entre el **símbolo** `(R A B)` y el **signo lingüístico** hay un
   *drift*: no se puede traducir directo sin el contexto completo.
   - `R`: "Juanito comparte con Pedro su almuerzo" ≠ "Juanito comparte con Pedro su gusto por el
     rock": el **álgebra** que admite cada `R` (biyección, conmutatividad…) cambia.
   - `A`, `B`: "perro" = amigo (Chile) o animal, según el uso.

## Lo que esto pone en revisión (no resuelto)

- Decisión 24 (`05`, `14 §5`): "afirmación = `:status` observed/confirmed/rejected/retracted". El
  eje "afirmación por un actor" no coincide con verdad = anclado/derivado.
- Decisión 31: "salida de prompt = `proposed`". No existe "propuesto".
- Decisión 35 ("el humano tiene la autoridad máxima") frente a la corrección 2 (lo que dice un
  humano puede estar mal): hay que precisar qué autoridad es (¿sobre qué se formaliza?, ¿sobre el
  anclaje?) y no sobre la verdad del hecho.
- Todo `15 §3–§5` (status, tiers, curación, promoción, conflicto) está escrito sobre el eje
  "afirmación" y debe rehacerse sobre sentido × verdad (anclado/derivado).
- Preguntas Q1, Q2, Q4, Q6, Q8, Q9 de `15 §12`: quedan **suspendidas** hasta la lectura; varias
  pueden no tener sentido bajo este marco.
- La diferencia entre Matrix (wittgenstein) como evaluador y otro motor (Datalog) no se puede
  decidir sin leer las fuentes de Matrix.
