# Análisis — el grafo como juego del lenguaje (borrador, 2026-10-08; archivado 2026-10-09)

> Material histórico. Movido desde `docs/v2/drafts/` (auditoría L2, 2026-10-09): está superado por
> `docs/v2/14 §1–§8` (y ya no describe `11`).
>
> Borrador fuera de la tabla de autoridad de `docs/v2/README.md`. Insumo para el spec 14
> (store + juego del lenguaje). Fuentes: dossiers `runs/research/2026-10-08/store-dossier-L{1,2,3}-report.md`
> (citas con `ruta:línea`), `wittgenstein/matrix-shrdlu-spec/specs/{matrix,grounding,provenance,SPEC}.md`.

## 0. El modelo del dueño (resumen fiel)

1. El grafo es un juego del lenguaje: una estructura simbólica evaluable sobre un mundo de conocimiento.
1.1 Evalúa su realidad contra principios rectores, que llegan por instrucción al grafo o por ingesta de documentos marcados como `source`.
1.1.1 Sin contradicciones ni errores lógicos internos (categorías de sentido del Tractatus). `source 1: perro=animal` vs `source 2: perro=no-animal` ⇒ error lógico marcado ⇒ pedir desambiguación (contextos distintos, o una fuente equivocada).
1.2 Los punteros del grafo a la base documental son *anchors*; cada uno con hash del source evaluado; cambio ⇒ recalcular la KB.
1.3 El documento se indexa completo estructuralmente (tree-sitter); anchors sobre anchors ⇒ Merkle y estructura de nodos.
1.3.1 Sobre eso, evaluaciones semánticas (contexto, tipado de documento/párrafo, `(R A B)`) que *emergen* del texto. El LLM no inventa, archiva.
1.4 Evaluar proposiciones contra la KB: recorrido del grafo (tools) y evaluación booleana vectorial (Matrix): dos etapas; y trazar la justificación hacia atrás.
1.4.1 Anchors textuales, pero también evaluaciones "en código" o "en prompt" sobre el texto; tipado flexible para encontrar secciones en documentos de largos distintos; la estructura de interés es una abstracción sobre el AST.
1.5 Pipelines: ingesta/descomposición/recomposición; proyección a grafo (programa nuevo, visualización); composición a texto natural.
1.6 No todo es indexable igual; lo menos estructurado cuesta más.
1.7 El "modelo" de SLDB deja de ser plantilla dura: pasa a ser *tipo de subgrafo estructuralmente estable* en el AST.
1.8 La estabilidad no es arbitraria: Matrix y clustering de grafos.
1.9 Embeddings sobre anchorings = co-ocurrencia pseudo-estable; co-ocurrencia ≠ `(R A B)`.
Clojure: declarar estructuras y escribir el AST es nativo; inmutabilidad, herencia (`derive`/`isa?`), homoiconicidad para instrucciones declarativas sobre el grafo.

## 1. Veredicto

Compatible con `01`/`02` (hecho en `W_i`, S/M/G sobre un pool, `W_i` nodo + árbol) y con wittgenstein (verdad, sentido, procedencia, curado). No choca con las cinco decisiones protegidas del gate (`atom-decision-first-slice-kernel-semantics-confirmed`). Revisa explícitamente: `01 §4.1` (autoridad única del pool), `07` (modelo = template), `11` (tree-sitter fuera de proceso). Llena el hueco de G (`12:37`: nada produce `:fact/:triple`).

## 2. Mapa punto → spec existente → estado

| tuyo | ya existe | estado |
|---|---|---|
| 1 | `01 §2`; `atom-w-i-context-node-and-index`; Matrix `WiGame(ctx,R)` `V_i/S_i/I` | kimun tiene el hueco, Matrix el evaluador; sin unión |
| 1.1 | `atom-canonical-projected-or-asserted` (canónico/proyectado/afirmado); `09` assert/ingest; tiers provenance.md | falta rol "source" |
| 1.1.1 | Matrix `TRUE/FALSE/UNKNOWN`, `sinnvoll/sinnlos/unsinnig`, `ambiguous` con candidatos; grounding: nada pasa a TRUE sin decisión; kimun `ConflictSet` (estructural), propuestas `drifted` | triple sin verdad ni sentido (`12`); sin "conflicto lógico" |
| 1.2 | `:sign/:external`; v1 `source_hash`; `09 §4` invalidación por `anchor/states`, `derive run --stale --propagate` | diseñado, `09` no implementado |
| 1.3 | árboles de posiciones, tree objects, `04`, stand-off; pod R2 | existe; `11` obsoleto respecto a tree-sitter |
| 1.3.1 | proyección por (hash, motor, versión); grounding "LLM descompositor de superficie"; `11` hechos con `source_hash` | `11` solo símbolos de código → documentos |
| 1.4 | `09` evaluador; R1 Datalog; Matrix check/assert; `Prov`, `derived_from`; evidencia en aristas | dos etapas sin contrato |
| 1.4.1 | `09 §4` `derive define --expr --engine sci\|shell:CMD`; `parity-v1:132` | sin lenguaje de selección; sin motor `llm:` |
| 1.5 | `01` ciclo D; `09` EffectPlan; `04` render; `07` templates | motores, fuera del store |
| 1.6 | clases structural/opaque/external; informe de cobertura | existe |
| 1.7 | `07` descriptor opaco + template | reescribir `07`; herencia = `derive`/`isa?` serializable |
| 1.8 | convergencia CHIC/SIA, `D_i`, `gate` | motor; requiere AST como triples |
| 1.9 | `01 §4.5`; proyección por (hash, motor, versión) | unidad de anclaje por decidir |
| Clojure | decisión 8; `:symbol/:proposition {:form}`; s-expr = EDN | `:proposition` sin formas anidadas (`12`) |

## 3. Hoyos conceptuales (orden de bloqueo)

1. **Verdad y sentido sin lugar.** `V`/`S` como índice derivado del juego; afirmación (observado/confirmado/retractado) como `:status` en evidencia de arista `projection`.
2. **Contradicción solo dentro de un contexto.** Entre fuentes aparece al unir contextos (`equivalent`/`projection`); la pregunta al usuario ocurre en convergencia, no en ingesta. Definir contexto por defecto de un documento.
3. **"Source" y vocabulario de relaciones (`L_i`) como rol/documento.** Declararlos en EDN como documentos source.
4. **Granularidad de "recalcular".** Hoja igual ⇒ `intact`; hoja cambiada ⇒ `orphan` ⇒ `drifted` ⇒ actor. Falta para G, hechos multi-hoja y tipado de documento.
5. **Decisiones humanas vs store desechable.** Afirmaciones humanas materializadas como documentos source ⇒ store 100 % derivado ⇒ `:git :ignore` sin pérdida (autopoiesis G/M→S).
6. **Lenguaje de selección sobre el AST.** Modelo = consulta; anchor en código = derivado con consulta como input. Si Datalog (R1): AST como triples, modelo = query. Decisión con spike.
7. **Contrato de las dos etapas.** EDN → resolver (`09`) → recorrer (Datalog) → evaluar en `W_i` (Matrix externo) → `{:answer :truth :sense :justification :citations}`. Nada persiste salvo `assert`.
8. **Proyecciones por prompt**: siempre `proposed`; versión = modelo + hash del prompt.
9. **Unidad de anclaje**: hoja + grafemas (kimun) sobre chunk + code points (wittgenstein).
10. **Protocolo de adaptadores** generalizado a proposiciones con procedencia, cualquier motor.

## 4. El store bajo esta luz

- Autoridad partida por clase: bytes → archivo (+git); estructura/identidad → pool (hashes); hechos y decisiones → pool, decisiones también como documentos. Revisa `01 §4.1`.
- Canónico ≠ persistido: el parse determinista es canónico y reconstruible; se verifica (root hash), no se guarda. Persistir solo lo referenciado (regla de spans).
- Por documento queda: anchor externo (~150 B), anchors referenciados por hechos, hechos. Resto = caché.
- Hoy: 27 MB por 0,44 MB; grueso = historia (`log.edn` 5,8 MB + snapshot mmap por ~540 tx) + copias (blob verbatim 763 KB, hojas). Trampa: índice completo de hashes en EDN pesa más que el texto ⇒ el árbol es caché.
- `source-node` (copia verbatim) se elimina en cualquier escenario.

## 5. Decisiones pedidas al dueño

a. Contexto por defecto = cada documento source es su `W_i`; unión por convergencia o instrucción. (rec: sí)
b. `V`/`S` como índice derivado; afirmación como `:status` en evidencia. (rec: sí)
c. Contenido por referencia por defecto; `inline` opcional por store. (rec: sí)
d. Decisiones humanas como documentos source en el repo. (rec: sí)
e. Matrix como motor externo por protocolo; sin portar ahora. (rec: sí)
