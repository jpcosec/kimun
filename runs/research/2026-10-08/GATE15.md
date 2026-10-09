# GATE15 — Gate de cero contexto sobre `docs/v2/15-capa-g.md`

Eres un auditor **sin contexto previo**: no has participado en el diseño. Solo lectura; no edites
nada. Si corres algo, con `memcap` (lee `/home/jp/.cache/coord-briefs/close-common.md`).
Repo: `/home/jp/proyectos/kimun-worktrees/spec15` (rama `close/spec15`). El documento auditado:
`docs/v2/15-capa-g.md` + `test/fixtures/g/*.edn`. Su autoridad superior: `docs/v2/14-juego-del-lenguaje.md`
(gana ante contradicción), `docs/v2/02-sustrato-computacional.md`, `docs/v2/05-estado.md`
(decisiones 1–40), `docs/v2/README.md` (orden de autoridad).

Entrega un informe en el fichero que te indique tu carril, con este formato:
- **Hallazgos** numerados, cada uno con: severidad (`bloqueante` | `grave` | `menor`), cita del 15
  (`15 §x` + frase), cita de la fuente con la que choca o que no cubre (`14 §y`, `02 §z`…), y la
  corrección mínima propuesta (una frase). Sin opiniones sin cita.
- **Ambigüedades**: enunciados del 15 que admiten dos lecturas (cita + las dos lecturas).
- **Veredicto**: `CERRADO` (sin bloqueantes), `CERRADO CON MENORES`, o `ABIERTO` (lista de bloqueantes).

## Carril A — coherencia (informe: `/home/jp/.cache/coord-briefs/GATE15-A.md`)
¿El 15 contradice al 14, al 02 o a alguna decisión de `05`? ¿Usa formas de nodo/arista que `02 §2–3`
no define sin declararlo en "qué revisa"? ¿Cada principio P1–P15 del 14 que aplica a G tiene su
regla en el 15, y cada regla del 15 se remonta a un principio o decisión? ¿Los fixtures EDN
cumplen las formas que el propio 15 declara (campo a campo)? ¿Las 7 preguntas al dueño son
realmente del dueño, o alguna ya está decidida en 14/05?

## Carril B — ejecutabilidad (informe: `/home/jp/.cache/coord-briefs/GATE15-B.md`)
Con el kernel actual (`src/kimun/kernel/*`, anillo 0 puro; `src/kimun/surface/*`; `src/kimun/host/*`),
¿se puede implementar cada sección del 15 sin violar `03` (anillos, kernel sin I/O)? Señala qué
sección exige cambios al kernel y cuáles. ¿El esquema del índice `index/graph/` y el log de hechos
son reconstruibles desde el pool (02 inv. 10, 14 §2)? ¿La tabla de promesas (15 §10) tiene para
cada enunciado normativo un test que lo rompería, y es escribible con `bb test`? ¿El protocolo
`propositions` es compatible con el protocolo 1 de `11` (un adaptador existente sigue válido)?
¿Hay costes que crezcan `p×p` con los símbolos (prohibido por 14 §8)?
