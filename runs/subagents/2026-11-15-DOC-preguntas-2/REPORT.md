# REPORT DOC-preguntas-2

Archivo tocado: `docs/v2/drafts/preguntas-abiertas.md` (+24 líneas, ninguna borrada). Evidencia: `lint.txt` (`rings and docstrings ok: 112 files`), `directed.txt` (`kimun.doc14-contract-test`, `kimun.lint-test`, `kimun.spec16-models-contract-test`: 14 tests, 498 aserciones, 0 fallos). Ninguna prueba lee `preguntas-abiertas.md` (grep en `test/`).

Tabla activa: 12 filas de datos a 24 (cabecera y separador incluidos, 12 a 25 filas), 4 columnas en todas, antes y despues (script de conteo con `\|` escapado).

## Filas nuevas (13, ninguna amplia una existente: no habia solape)
- De plan-2 Q1 a Q9: `P1-linea`, `LINAJE`, `RENOMBRAR`, `K2b`, `K2a-fanout`, `K5-nombres`, `LATIDO`, `M2-modo`, `S2b-anclas`.
- `K1f-campo` (K1f REPORT, «Interpretaciones»).
- `M2-derivar` (M2a a) y `M2-desk` (M2a b), con la evidencia de M2a declarada sin integrar y sin rutas.
- `RS1`: solo remite a la fila `RS1` de `12 §1` l. 94, que dice «Criterio propuesto por el equipo, pendiente del dueño». No copia su contenido.
- Nota fechada «2026-11-15, segunda tanda» añadida antes del bloque «Decidido por el dueño».
- `K2b` lleva los hechos de K2b-0 (los cuatro mmap, solo la arista al archivo con informacion unica, aristas no cambian formato y un mmap si, sin dependencia tecnica con K1 hallada); `K2a-fanout` y `LATIDO` los numeros de MED1 con lo no medido; `S2b-anclas` suma lo de S2b-cp (rechazo vs expandir, texto de los offsets, rango vacio).
- Nada esta respondido ni recomendado; las «lecturas del plan» se marcan como tales.

## Citas que no se sostuvieron y como las deje
- plan-2 «`fields.cljc` l. 170-172» (rechazo de `fields set` sobre spec 16): en `src/kimun/surface/fields.cljc` esas lineas son otra cosa. Cite el sitio real: `src/kimun/cli/commands/fields.cljc` l. 170-173 y `src/kimun/cli/commands/docs.cljc` l. 263-270 y 323.
- plan-2 «`02 l. 844`» (hito 8 de retencion): esa linea esta vacia. Cite `docs/v2/02-sustrato-computacional.md` l. 186-192 y `10-escala.md` l. 654.
- plan-2 «`14 l. 736`» («K1e les dara identidad»): la frase esta en l. 745 (l. 736 es el encabezado de S2 fase 3); cite l. 740-745. «`14 l. 455-459`» (regla 4): empieza en l. 456; cite 456-459. «`14 cabecera l. 31`»: cite l. 30-31.
- plan-2 «`16 l. 76-88` (autoridad)» y «sin inversion linea a linea de `16 §2.2`»: la expresion literal no existe; el texto real es l. 70-72 («sin exigir que ninguna proyeccion sea inversible linea a linea contra el `source` original»). Lo cite asi, no como cita textual.
- Cualquier cita de plan-2 no listada aqui (`10` l. 108, 584-601, 655; `12` l. 43, 74, 80, 81, 84, 89, 94; `14` l. 202, 243, 344-345; `16` l. 113-119, 204-207, 229-236, 267-268, 284; `06` l. 99-160; `check_rings.clj` l. 14-17; `docs.cljc` l. 54-59; K1c brief l. 37; sintesis D0 l. 110 y 127) la abri y se sostiene.

## No verificables
- La evidencia de M2a (cifras 35/178, 105 fallos, 38 con otro payload, 3 y 10 modelos): se registra tal cual lo dio el encargo; no esta en el repo y no la pude abrir.
- `kimun.include.edn` existe en el repo; no ejecute `include check` para comprobar la deriva (accion que el encargo excluye).
- La afirmacion de K2b-0 de que no hay dependencia tecnica con K1 es inferencia del propio informe; la marque asi.
