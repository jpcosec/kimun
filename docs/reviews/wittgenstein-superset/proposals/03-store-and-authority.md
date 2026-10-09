# P3 — Autoridad durable y store reconstruible

Estado: **propuesta**. Atiende F08–F11 y la separación de `14 §18–§20`.

## Matriz de autoridad

| clase | fuente durable | base estable | caché | grafo |
|---|---|---|---|---|
| Source externo | Archivo/git, URL archivada o artefacto por política explícita | Locator, fingerprint, contrato, digest de proyección | Árbol/hojas/extracciones | Interpretaciones y soportes anclados |
| Documento generado | Instancia durable + modelo de generación | Anchor del output si se observa | Árbol de salida/render | Valores de instancia, relaciones, contrato de generación |
| Regla/clase/contexto | Documento source o registro durable de conocimiento | Referencia de versión si aplica | Compilación/matrices | Definición y relaciones |
| Decisión de reconciliación/equivalencia | Evento durable o documento de decisión versionado | Nuevo registro de anchors según acción | Índices | Correspondencia y justificación |
| Resultado de motor | Inputs durables + motor/version/config; output conservado si no reproducible | Digest del input observado | Output determinista reproducible | Prueba/soporte aceptado, nunca verdad externa por el nombre del motor |

«Base no contiene texto» no implica «producto no conserva conocimiento». Los datos que solo existen en G deben tener persistencia y backup propios. Si `.kimun/` se ignora y los datos de instancia viven solo allí, reconstrucción no está garantizada.

## Frontera de base y grafo

La base recibe registros de proyección ya producidos, no importa selectores ni modelos:

```clojure
{:source-id "src:…"
 :locator {:path "docs/x.md"}
 :fingerprint "sha256:…"
 :projection-hash "sha256:…"
 :producer-contract "contract:…"
 :tracked-anchors [{:locator … :expected-hash "sha256:…"}]}
```

El grafo posee selección/clasificación y declara sus dependencias. El lint verifica que base no requiere `kimun.graph.*`; los entrypoints de producto pueden componer ambos paquetes. La dirección de dependencia se comprueba por namespace/paquete, no se deduce solo de los anillos numéricos existentes.

La base debe detectar cambios del ambiente aunque el grafo no esté cargado. Recomputar proyección requiere el productor; sin él puede reportar source cambiado, pero no asegurar que cambió lo trackeado.

## Contratos y claves de caché

Registrar digest de parser+gramática+perfil+normalización+selector+clase, incluyendo versiones de bases de herencia. Para G: agregar hashes de contexto, reglas, equivalencias/rutas, políticas y configuración de motor. Dos evaluaciones con mismo texto y reglas distintas no comparten resultado válido.

La clase genérica `generic/<ext>` es un nombre de familia, no una versión inmutable del contrato. Toda instancia necesita referencia a una definición concreta. Un campo ausente por nuevo parser no es automáticamente un cambio del source.

## Estados observables

| situación | conducta propuesta |
|---|---|
| Source/contrato sin cambios, caché borrado | Recalcular y verificar igualdad de raíz |
| Fingerprint distinto, proyección igual | Cambio no semántico para esa proyección; actualizar observación de source según política |
| Proyección distinta | Invalidar dependencias afectadas; propuesta de reconciliación |
| Regla/contexto/ruta distintos, S igual | Invalidar evaluación G dependiente |
| Parser/contrato original no disponible | `contract-unavailable`; no afirmar edición externa |
| Archivo borrado, revisión histórica solicitada | Leer artefacto histórico declarado o `historical-source-unavailable` |
| Output generado borrado | Regenerar de instancia durable |
| Output generado editado | Informe con alternativas: descartar/regenerar, importar como source o modificar instancia mediante acción aceptada |

## Merkle por ruta y temporalidad

### Escritura sobre archivos

La acción de producto debe capturar el before-image del source y verificarlo antes de reemplazarlo. Commit del store y rename de archivo no son una única operación atómica del filesystem: proponer un intent durable y protocolo de recuperación idempotente, con estados explícitos `prepared/knowledge-committed/projection-pending/complete` o equivalentes. El detalle requiere diseño y pruebas de fallo antes de afirmar all-or-nothing sobre ambos.

Si la autoridad es source, fallar la escritura no puede dejar una cabecera aparentemente vigente frente a bytes que no se publicaron. Si la autoridad es instancia generada, el commit puede ser válido con output pendiente, pero la respuesta y el diagnóstico deben decirlo. No sobreescribir edits externos sin comparar before-image ni confiar en el CAS de heads para detectar cambios fuera del store.

Merkle de la base sobre registros trackeados por source/ruta para localizar cambios. Merkle de G sobre conocimiento/reglas/contextos por separado. Sus raíces son dependencias distintas; comparar solo una no garantiza vigencia total.

No meter copias enteras de planes en el CAS estable solo para reproducir índice reconstruible. Antes de retirarlas, definir qué revisiones y acciones se conservan durables y cómo auditarlas. Log de cache no puede ser el único lugar de una decisión o valor de instancia.

No se promete recuperar texto histórico únicamente con hash+archivo actual. La garantía de caché borrable se formula sobre inputs durables disponibles. Para formatos viejos/versionados, decidir retención e importación antes de GC.

## Aceptación

Fixture source y fixture generado pasan: build → consulta → borrar caché → misma respuesta/prueba; borrar output generado → regeneración; cambiar source → stale local; cambiar regla → stale G; contrato faltante → error distinto; rebuild desde archivos/decisiones/instancias durables → mismo conocimiento normalizado. Medir tamaño de **todo lo durable**, además de caché y archivos fuente, para no esconder coste trasladándolo de carpeta.

Añadir fallos antes/después de commit, fallo de escritura, crash y edición concurrente del archivo; reintento recupera sin doble decisión ni clobber.
