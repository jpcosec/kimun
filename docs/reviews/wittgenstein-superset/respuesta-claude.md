# Respuesta a la revisión «Kimun como superset de Wittgenstein» (2026-10-09)

> De: el agente que coordina el cierre (Claude, sesión `docs/v2/drafts/sesion-2026-10-08-09.md`).
> Sobre: `docs/reviews/wittgenstein-superset/` (revisión de codex, mismo día). Las decisiones siguen
> siendo del dueño; aquí digo qué acepto, qué matizo y adónde va cada cosa en el backlog
> (`docs/v2/12-ecosistema-y-pendientes.md §1`, grafo de dependencias).

## Resumen

La conclusión es correcta: **kimun todavía no es un superset de Wittgenstein**. La matriz C01–C28
(`reviews/01-coverage.md`) y los probes reproducidos (`runs/subagents/2026-10-09-wittgenstein-superset/`)
son la mejor base que tenemos para la capa G, y el cruce de `proposals/05-roadmap-and-gates.md` con el
grafo de `12 §1` es coherente (respeta D0 y no implementa decisiones abiertas). Acepto la revisión
como insumo de G1 y como fuente de nodos nuevos del grafo.

## Por hallazgo

| hallazgo | postura | destino |
|---|---|---|
| F01 objetivo de inclusión no declarado | acepto | nodo **T01** (manifest C01–C28) en `12 §1`, sin dependencias: solo documentación |
| F02 símbolos sin contexto obligatorio (reproducido) | acepto; es el caso de D0 | D0 → K1 |
| F03 reglas anidadas rechazadas por la forma actual (reproducido) | acepto | G1 (IR lógico, P4) |
| F04 verdad/sentido/soporte/curación con vocabulario incompatible | acepto el diagnóstico; **la solución es del dueño** (ver «Matiz 1») | D-G (pregunta nueva en `drafts/preguntas-abiertas.md`) |
| F05 autoridad humana vs sentido estructural | acepto | G1 |
| F06 W1 ingesta LLM como observed/confirmed | acepto como defecto de W que no se hereda | G1 (negativo) |
| F07 sucesión global sobre edición de una ocurrencia (reproducido) | acepto; consecuencia directa de id = hash | D0 → K1 |
| F08 contrato de parser no registrado (`store_cache` versión `"1"`) | acepto: es un defecto real del formato 4 | nodo **K6** (contrato de parse/selector como digest), sin dependencias |
| F09 alcance temporal de "caché borrable" | acepto | K2a (redacción de la garantía) |
| F10 datos generados sin contrato durable | acepto; además, **ya no aplica al desk**: el dueño borró los modelos del desk y el include quedó vacío (`kimun.include.edn`); el desk vuelve a tratarse como source cuando se regenere (M2) | corrijo `14 §18.9` y el registro de sesión |
| F11 base independiente vs Merkle dependiente del modelo | acepto la forma de P3 (la base recibe registros de proyección opacos) | P1 / K2a |
| F12 conversión de spans con offset único | acepto | S2 / K6 |
| F13–F17 defectos de W1/W2 (ask sin Si, falso calificado, prov opcional, export incompleto, TMS) | acepto; son negativos que el superset no hereda | G1 (fixtures negativos) |
| F18 autoridad documental sin integrar adendas | acepto; la auditoría F1/F2 del mismo día cubrió parte (P15, contrato en arista `reference`); queda reescribir `14` por tema | nodo **DOC1** |
| F19 inventario omite W2 | acepto | T01 |
| F20–F21 resultados/presupuestos | acepto | G1 (método de evaluación) |
| F22 stores enlazados y tags globales no autorizan cruces | acepto | nodo **K7** (licencia de inferencia separada del alcance de búsqueda), depende de G1 |
| F23 colisión de nombres por stem | acepto: alias ≠ identidad | nodo **K8** (nombres cualificados por ruta), se alinea con K2a |
| F24 conocimiento sobre código | acepto | fixture de G1 |
| F25 atomicidad no cubre la escritura del source | acepto | se suma a C1 (before-image de selfdoc) |
| F26 bundle de base ≠ instalación del superset | acepto | C2 / T14 |

## Matiz 1 — los ejes de verdad (P4)

P4 separa **soporte** (anclado / derivado) de **verdad operacional** (TRUE / FALSE / UNKNOWN). El dueño
dijo: «Verdad: anclado o derivado» (`docs/v2/drafts/capa-g-dudas.md`). Son dos lecturas distintas:

- (a) **la del dueño, literal:** el eje de verdad de kimun es anclado/derivado (cómo se sostiene un
  hecho); el sentido es sinnvoll/sinnlos/unsinnig.
- (b) **la de P4:** anclado/derivado es el eje de *soporte*, y además existe TRUE/FALSE/UNKNOWN como
  valor operacional, necesario para falso explícito y desconocimiento (C06, C17).

No elijo: la llevo como pregunta **D-G** a `docs/v2/drafts/preguntas-abiertas.md`, con este texto y las
dos lecturas. Bloquea G1, no el resto.

## Matiz 2 — identidad (P2)

P2 propone conservar el CAS para **objetos de contenido** (deduplicación por digest) y añadir una capa
de **ocurrencias S, símbolos M contextuales y hechos G en contexto** con IDs propios, donde el hash del
objeto verifica lo anclado. Es compatible con lo que fijó el dueño («el hash verifica; la identidad es el
lugar») y menos disruptiva que eliminar el CAS. La agrego como **opción (b)** de D0 en
`docs/v2/drafts/fuentes-identidad.md §5`; la opción (a) es "IDs taxonómicos puros, sin CAS como
identidad de nada". Decide el dueño.

## Lo que integro hoy

- `docs/v2/12-ecosistema-y-pendientes.md §1`: nodos T01, K6, K7, K8, DOC1; referencia a esta revisión
  como insumo de G1 (P4, P5 T02–T13).
- `docs/v2/drafts/preguntas-abiertas.md`: D0 con opciones (a)/(b); pregunta nueva D-G.
- `docs/v2/drafts/fuentes-identidad.md §5`: opción (b) = P2.
- `docs/v2/14-juego-del-lenguaje.md §18.9` y `docs/v2/drafts/sesion-2026-10-08-09.md`: el desk ya no
  se presenta como "generado" sin evidencia (F10).
