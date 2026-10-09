# GATE15 — Carril A (coherencia) — informe

Auditor sin contexto. Solo lectura. Documento auditado: `docs/v2/15-capa-g.md` (rama `close/spec15`) + `test/fixtures/g/*.edn`. Autoridad: `14` > `02` > `05`.

## Hallazgos

1. **bloqueante** — propiedades de `R`: forma vs regla.
   Cita 15 §1: «`{:name "comparte" :axis ["thing" "thing"] :properties #{:transitive} ; álgebra de R en ESTE contexto}`» … «Las propiedades algebraicas de `R` son **hechos** … El evaluador booleano (§8) las lee de ahí; no hay tabla aparte.»
   Choca con 15 §1 misma sección (la forma `:relations[…].:properties` es una tabla aparte) y con el anexo `context.edn` («`:properties [:fact/property-comparte-transitive]`»).
   Corrección mínima: quitar `:properties` de `:relations` y declarar solo los hechos `(property R p) @ W_i` como fuente.

2. **grave** — resolución (c) de conflicto contra la forma de hecho.
   Cita 15 §5: «marcar una fuente equivocada en ese contexto — un hecho sobre el anchor del documento (`:sign/:external`), no sobre el hecho.»
   Choca con 15 §1: «`R`, `A` y `B` son **ids de nodos `:symbol/:term`**» y con `02 §2.1` `:fact/:triple {:subject <id> :predicate <id> :object <id|literal> :context …}`.
   Corrección mínima: fijar que el anchor se nombra por un término (`:symbol/:term`) que designa el `:sign/:external`, o declarar la excepción en «qué revisa».

3. **grave** — arista de ruteo `:semantic` G↔G sin declarar.
   Cita 15 §2: «`15` propone `{:type :semantic :evidence {:relation :equivalent|:projection :context <W_i> :context-target <W_j> :phi "<str>" :ref-hash <nodo destino>}}`».
   Choca con `02 §3.1`/`§3.2`: `semantic` es M↔M (símbolos), y un `W_i` es `:fact/:context` (capa G); la clave `:context-target` no está en `02 §3.2`. No aparece en 15 §11 «Qué revisa».
   Corrección mínima: listar la nueva forma de arista (o su cambio de `02 §3.2`) en §11 «Revisa».

4. **grave** — Q3 ya decidida por `14`.
   Cita 15 §12 Q3: «formato de los documentos de decisión (decisión 27/Q6) | (a) EDN siempre …».
   Fuente: `14 §12` decisión 27 «… materializadas como documentos por la composición grafo→documento, **formato según el caso**» y `14 §17` «Q6 … formato según el caso». La misma 15 §4 lo dice: «El formato (EDN o Markdown según el caso) es la pregunta §12 Q3.»
   Corrección mínima: eliminar Q3 y fijar el default ya decidido («el formato del caso»).

5. **grave** — `source_hash`/`source-hash` con dos formatos.
   Cita 15 §3: «`:prov     [{:path "…" :source-hash "sha-256:…" …}]`» y 15 §7: «`"source_hash": "sha256:…"` … `source_hash` es `sha256:<hex>`».
   Choca con `02 §6.4` («`alg` es el nombre del algoritmo del store (`sha-256` hoy)») y decisión 11 («`<alg>:<hex>`»); el fixture usa `"sha-256:…"`.
   Corrección mínima: unificar clave y etiqueta (`:source-hash` / `sha-256:<hex>`) entre §3, §7 y los fixtures.

6. **menor** — enum de `:granularity` inconsistente.
   Cita 15 §1: «`:context {:name "<NFC>" :granularity :document|:section|:paragraph|:corpus …}`» vs 15 §2: «`:granularity :document|:section|:paragraph|:corpus|:transversal`».
   Corrección mínima: incluir `:transversal` (o excluirlo) en los dos sitios.

7. **menor** — taxonomía `literals` declarada y ausente.
   Cita 15 §1: «`{:kind :taxonomy :name "literals"}`»; la forma-ejemplo del propio §1 y `vocabulary.edn` solo listan `terms`/`relations`.
   Corrección mínima: añadir `literals` a la forma-ejemplo y al fixture, o quitar la declaración de primera clase.

8. **menor** — `self` excede decisión 34.
   Cita 15 §2: «`self` (el store y sus modelos: `store`, `model`, `context`, `fact`, `edge`, `sign`, `tree`)».
   Fuente: decisión 34 «la autodescripción de kimun (su store y sus modelos)».
   Corrección mínima: citar la ampliación o ceñir la lista a «store y modelos».

9. **menor** — P8/P14/P15 sin regla ni declaración de no-alcance.
   Cita 15 §6: «P5, P8 y decisión 32» (solo cubre spans). `14 §7` define además anchors «de evaluación `:opaque {:format "edn/value"}` con aristas `derived`»; decisiones 36 (P14) y 38 (P15) no tienen regla en 15 ni salen en §11 «No toca».
   Corrección mínima: añadir la regla de anchors de evaluación o declarar P14/P15 fuera de alcance en §11.

10. **menor** — nombres de taxonomía sin traza.
    Cita 15 §1: «Taxonomías de primera clase que este documento fija, con nombre y forma: `terms`, `relations`, `literals`».
    No se remonta a `14`/`05` (decisión 23 solo dice «posiciones en taxonomías»).
    Corrección mínima: citar la decisión que fija los nombres o marcarlos como revisión de `02 §2.1`.

### Fixtures (`test/fixtures/g/`)

11. **grave** — `source.md` inexistente.
    Cita `context.edn`: «`:of {:path "test/fixtures/g/source.md"}`» y `facts.edn`: «`:path "test/fixtures/g/source.md"`»; el directorio solo contiene `conflict/context/decision/facts/vocabulary.edn`.
    Choca con 15 §2: «un `rebuild` con los mismos archivos produce el mismo contexto y los mismos hechos (decisión 27)».
    Corrección mínima: añadir `test/fixtures/g/source.md` o cambiar las rutas a un archivo existente.

12. **bloqueante** — árbol de contexto fuera de la forma declarada + hecho de propiedad colgante.
    Cita `context.edn`: «`:tree {:kind :context :name "…" :children [… :term/transitive] :properties [:fact/property-comparte-transitive]}`».
    Choca con `02 §3.1` (descriptor `{:tree <id> :kind … :name "…" :root <node-id>}`: faltan `:tree`/`:root`, sobran `:children`/`:properties`) y con `facts.edn`, donde no existe `:fact/property-comparte-transitive`.
    Corrección mínima: dar `:tree`/`:root`, eliminar `:properties` (va como hecho) y definir el hecho de propiedad en `facts.edn`.

13. **grave** — aliases colgantes en `conflict.edn`.
    Cita `conflict.edn`: «`:facts [:fact/f2 :fact/f2b] :edges [:edge/p2 :edge/p2b]`».
    `facts.edn` solo define `:fact/f1..f3` y un único alias de arista (`:edge/p3`); `f2b`/`p2`/`p2b` no existen.
    Corrección mínima: definir el segundo hecho y las aristas referidas (con aliases) o corregir las referencias.

14. **menor** — alias `:ctx/doc` nunca declarado.
    Aparece en `context.edn` (implícito), `facts.edn`, `vocabulary.edn`, `conflict.edn` y `decision.edn`; ningún fixture lo liga al nodo de contexto.
    Corrección mínima: declarar `:ctx/doc` → nodo `:fact/:context` en `context.edn`.

15. **grave** — eje del vocabulario violado por el hecho.
    Cita `vocabulary.edn`: «`{:name "es" :axis ["thing" "kind"] :properties #{} :tiers #{:observed :confirmed}}`» vs `facts.edn`: «`:fact/f3 {… :subject :term/agua :predicate :term/es :object :term/liquidez …}`».
    `liquidez` está en `terms` (raíz `thing`), no en `kind`; y `kind`/`relation-property` no existen en ninguna taxonomía declarada.
    Corrección mínima: corregir el eje (`thing`×`thing`) y declarar los términos/paths usados en los ejes.

16. **menor** — spans idénticos y no cubren el predicado.
    Cita `facts.edn`: las tres `projection` usan «`:span {:leaf :leaf/text1 :range [3 32]}`»; el texto es «el agua comparte liquidez con la bencina», que no contiene `property`/`transitive` ni `es`.
    Choca con 15 §6: «Anclar un hecho = su cadena de aristas hasta las hojas».
    Corrección mínima: dar a cada proyección su span propio (o documentar que la prov es informativa).

17. **menor** — `:conflict/id` no es id ni alias.
    Cita `conflict.edn`: «`:conflict/id "<H(canonical-bytes de este mapa sin :conflict/id)>"`».
    Choca con 15 §13: «Los ids se muestran como aliases … el id real … es `H(canonical-bytes …)`».
    Corrección mínima: marcarlo como placeholder explícito (`:conflict/id nil` o alias) como en el resto de fixtures.

## Ambigüedades

- **`:axis`**: 15 §1 «`:axis ["thing" "thing"] ; A × B admisibles, por taxonomía`» admite (i) nombres de taxonomía y (ii) posiciones/raíces dentro de una taxonomía. En `vocabulary.edn` conviven `"thing"`/`"relation"` (raíces) con `"kind"`/`"relation-property"` (no declarados).
- **`:range [a b]`**: 15 §6 dice «rango de grafemas NFC» y `02 §4.1` «`[leaf-id inicio fin]`»; no se fija si `fin` es inclusivo o exclusivo (`02 §6.2` solo exige `b ≤ número de grafemas`).
- **unidad del `span` de `prov`**: 15 §6 «El motor emite spans en `code points` … los mapea … a `(hoja :sign/:text, rango de grafemas NFC)`» vs 15 §7 «`"span": {"start": 412, "end": 468, "unit": "codepoints"}`»: dos lecturas de si el `span` del protocolo va en code points o en grafemas de hoja.
- **`:status` vs `:tier` y `:tiers` por relación**: 15 §3 fija el orden global `observed > confirmed > inferred > statistical`, mientras `vocabulary.edn` restringe `:tiers` por relación (p. ej. `es` solo `#{:observed :confirmed}`): admite que el vocabulario limite o que el orden global gane.
- **`equivalent` bidireccional**: 15 §2 «`equivalent` (bidireccional, `φ ≥ 0,95` en ambos sentidos)» vs 15 §1 `:promotions` con `:to :confirmed` (¿la promoción es la arista de ruteo o solo la identidad de contextos?).

## Veredicto

**ABIERTO** — bloqueantes: **1** (las propiedades de `R` son hechos y a la vez un campo `:properties`), **12** (el árbol de contexto del fixture no cumple `02 §3.1` y referencia un hecho de propiedad inexistente).
