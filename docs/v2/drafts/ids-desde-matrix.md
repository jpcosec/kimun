# IDs en kimun a partir de Matrix/wittgenstein (borrador, 2026-10-09)

> Síntesis de lectura para la discusión de `14 §21`. No es autoridad.
> Leído: `Matrix/paper_v2/sections/{02,03}`, `Matrix/Matrix/docs/{concepts,kernel_symbol_policy,
> context_and_wigame,canonical_forms_and_ingestion,semantic_fields}.md`, código
> `matrix-shrdlu-spec/matrix/src/operational_model/{core,routing}`, átomos
> `TractatusKnowledgeMachine/atoms/{Filosofia/Tractatus_y_Ontologia,Computacion/Estructuras_Matriciales}`.

## Lo que dice Matrix

1. Las cosas no significan; significan por sus relaciones (paper §2: "things in the abstract mean
   nothing; they only acquire meaning through their relationships").
2. Símbolo = posición en un espacio lógico, en un contexto (paper §2; átomo Signo_vs_Simbolo).
3. Contextos = árbol de enrutamiento; lo que un símbolo denota es "the path/leaf where its occurrences
   converge" (`context_and_wigame`).
4. Desambiguar = crear jerarquía (`leaf` → `(leaf) rough|smooth`, paper §3).
5. Mismo signo en dos mundos = dos símbolos: etiquetas `"<wigame_id>::<symbol>"` (`semantic_fields` §5);
   `she@marie_curie`.
6. Mismo `(R a b)` en dos fuentes = dos hechos en dos contextos (`context_and_wigame`).
7. Identidad entre contextos = curaduría (`equivalent`), nunca string (`context_and_wigame`);
   meta-relaciones del kernel: `instance`, `equivalent` (`kernel_symbol_policy`).
8. Relaciones y contextos reificados con namespaces reversibles `rel:`, `ctx:` (`semantic_fields` §8).
9. El código va atrasado: `symbol_id` libre; proposiciones/hechos con ids aleatorios (`new_id`).

## Propuesta para kimun (dueño, 2026-10-09: "IDs = taxonomía estructural para lo que ancla,
## taxonomía semántica para M/G; el hash solo verifica")

| nodo | ID | ejemplo | hash |
|---|---|---|---|
| S (anclaje) | lugar estructural en el ambiente + proyección | `src:desk/atoms/x.md › section:Answer › para:2` | detecta mutación |
| M símbolo | ruta de contextos + signo | `ctx:kb › ctx:cocina › hoja` (`› hoja.rugosa` al desambiguar) | — |
| M relación | reificada en su contexto | `rel:comparte@ctx:cocina` | — |
| G hecho | proposición en su contexto | `ctx:cocina › (comparte agua bencina)` | — |
| contexto | ruta en el árbol | `ctx:kb › ctx:cocina` | Merkle del subárbol |

Consecuencias: párrafos idénticos = dos nodos S con el mismo hash; la igualdad de significado se ve
en M (anclan el mismo símbolo). Enlaces por ID, no por hash. Merkle de reconciliación sobre el árbol
estructural (S) y el árbol de contextos (M/G).

## Preguntas abiertas
1. Si el lugar estructural cambia (sección renombrada, archivo movido), ¿anchor roto a reanclar
   (el hash ayuda a ubicarlo) o el ID estructural debe sobrevivir?
2. ¿Un símbolo nuevo nace en el contexto del documento y subirlo/declararlo `equivalent` es curaduría?
