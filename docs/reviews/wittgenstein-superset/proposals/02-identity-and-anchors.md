# P2 — Identidad, ocurrencia y verificación

Estado: **propuesta**, especialmente el alcance de §21/decisión 46 para todo el kernel (D0) todavía pendiente. Atiende F02/F07/F12/F22/F23. Se incorpora lo ya respondido en `14 §21`: ID estructural roto = borrado declarado o mutación detectada por hash; símbolos nacen en el contexto del documento y su ascenso/equivalencia es curaduría. No se reabren esas respuestas.

## Cuatro cosas distintas

| entidad | identidad propuesta | hash / dirección | cambio |
|---|---|---|---|
| Objeto de contenido | Digest canónico interno | CAS deduplica bytes/estructura | Otro contenido produce otro objeto |
| Ocurrencia S | Source + lugar estructural versionado | Payload hash verifica lo anclado | Si el lugar desaparece, se declara borrado; no mantener nodo sin referente |
| Símbolo M | Contexto de declaración + clave local | Estado/definición tiene digest | Equivalencia no elimina la identidad local |
| Hecho G | Proposición normalizada en contexto | Soporte/contrato/prueba versionados por separado | Una nueva fuente agrega soporte, no reescribe el origen |

Los hashes de contenido pueden seguir direccionando objetos; enlaces de dominio apuntan a entidades/contextos/ocurrencias y registran el hash esperado. Esto satisface «hash verifica» sin perder la compartición útil del kernel actual. Es una alternativa a evaluar junto con IDs taxonómicos puros, no una decisión del dueño ya tomada.

## Forma ilustrativa de anchor

```clojure
{:source "src:repo-a/docs/curie.md"
 :source-revision "git:<commit-o-version-de-ambiente>"
 :locator {:section ["Biography"] :block 2}
 :selection [[0 14]]
 :unit :grapheme-nfc
 :expected-hash "sha256:…"
 :parse-contract "contract:…"}
```

El ejemplo no fija la sintaxis final de IDs (pendiente tras D0). Una dirección por ordinal es barata pero cambia al insertar bloques. Ninguna heurística decide identidad silenciosamente. Una referencia a contenido idéntico y otra a una ocurrencia concreta deben ser distinguibles en el contrato.

## Reglas

1. Dos párrafos iguales en A/B pueden compartir blob y tener anchors distintos.
2. Reemplazar A no registra sucesión de B ni redirige sus enlaces.
3. `she` en Curie/Franklin son dos símbolos aun compartiendo signo/idioma.
4. Una equivalencia es contextual, dirigida/bidireccional según su contrato, tiene soporte y revisión. No significa borrar el nodo local.
5. Renombrar un documento puede borrar un lugar estructural y crear otro; se declara la pérdida de soporte y se registra la correspondencia si se acepta. El conocimiento que sobrevive pertenece a su capa semántica, no a un nodo S sin referente.
6. Store y contexto se cualifican antes de resolver nombres. Encontrar por string solo produce candidatos.

## Correspondencia de offsets

El productor entrega raw source hash y spans originales. La superficie construye un mapa `raw bytes/codepoints ↔ CST ↔ hojas NFC ↔ grafemas`. Cada tramo anclado conserva su correspondencia; una cita puede mapear a varios anchors. Los offsets no representables exactamente se reportan, no se redondean.

Cambiar solo markup puede mantener payload hash pero cambiar locator/source hash; cambiar norma de Unicode o parser cambia contrato aunque los bytes no cambien. Estos estados necesitan nombres distintos.

## Casos de aceptación

- Párrafo duplicado en dos árboles y dos ocurrencias del mismo árbol: editar una preserva las demás referencias.
- Mismo signo/idioma, dos contextos: sin correspondencia, ningún transporte; con correspondencia revisada, solo dirección autorizada.
- Move, rename de heading, inserción de párrafo, borrado: borrados estructurales explícitos y reanclaje aceptado; no conservar un S que no apunta a nada.
- Dos `README.md` en directorios o stores distintos: IDs distintos, alias corto ambiguo.
- CRLF, NFC/NFD, emoji compuesto, inline markup, spans multihoja: fragmento original verificable.
- Equivalencia retractada: cachés y respuestas dependientes invalidan aunque source siga intacto.

## Migración

Agregar capa de ocurrencias/IDs y conservar digest de blobs primero. Rebuild de stores regenerables para el slice piloto; importación guarda equivalencias entre IDs viejos y nuevos si son necesarias para leer decisiones existentes. No actualizar hashes dorados ni reescribir datos v1 sin una desviación y su evidencia.
