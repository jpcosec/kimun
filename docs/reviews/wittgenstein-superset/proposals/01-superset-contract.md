# P1 — Contrato explícito de inclusión

Estado: **propuesta**. Responde a F01/F19 y a la solicitud del dueño. No sustituye `docs/v2` hasta su integración deliberada.

## Contrato propuesto

Kimun comprende una base documental, un paquete de conocimiento contextual y motores sustituibles. El producto completo cubre las capacidades seleccionadas de W1 y W2, preservando sus garantías útiles y ampliándolas con revisión inmutable, anclaje estructural y generación de documentos.

La base sola puede instalarse y operar sin G. «Superset» describe el producto completo con sus paquetes soportados, no obliga a que todos los kernels dependan del motor estadístico ni a incluir una UI en el primer slice.

Para una capacidad C:

1. Se define el input canónico y sus precondiciones.
2. Se compara resultado, verdad, sentido, contexto, ruta, justificación y efectos.
3. Se documenta cualquier diferencia intencional frente a W.
4. Se ejecuta el mismo fixture con la implementación de referencia y el camino de Kimun, o un oráculo independiente si la referencia tiene un defecto conocido.
5. Se mantiene negativo contra el defecto que no se hereda.

No se exige igualdad de IDs aleatorios, timestamps, layout de archivos o prosa del REPL. Se exige igualdad del significado observable y preservación de procedencia.

## Ejemplo de inclusión observable

Entrada: dos sources sobre Curie/Franklin con `she` y una correspondencia contextual revisada. Pregunta: `born_in(she@Curie, Warsaw)`.

Salida válida: resultado local o ruteado con mundo de evaluación, soporte anclado, hash de source, ruta autorizada, versión del contrato y prueba. `she@Franklin` no alimenta la respuesta. Cambiar el párrafo citado vuelve stale su soporte; no cambia la evidencia de Franklin ni confirma por sí mismo otra respuesta.

Una API que devuelve el párrafo con Warsaw pero no evalúa el símbolo/contexto no cumple ese caso.

## Exclusiones deliberadas iniciales

- No copiar todo legacy, kernels abandonados ni modelos globales solo por estar en el repo.
- Causalidad permanece experimental: co-ocurrencia no prueba intervención.
- JAX/GPU y bloque global son optimizaciones opt-in, sujetas a presupuesto.
- OWL/UNL requieren elegir fixtures y consumidores; quedan inventariados, sin promesa completa de interoperabilidad.
- Compatibilidad de REPL se mide por aclaración/foco/why y no por equivalencia textual de prompts.
- Mantener v1 como regresión documental; no usarla para bloquear la separación anclaje/generación.

## Integración documental propuesta

| documento | cambio concreto |
|---|---|
| README | Añadir objetivo de conocimiento contextual y estado de inclusión |
| FAQ | Separar «fuera del slice actual» de «fuera del producto» |
| `05` | Estado por C01–C28, sin elevar «dirección» a «implementado» |
| `12 §3` | Inventario de ambas líneas con capacidades y excepciones |
| `14` | Integrar adendas por tema, señalar ejes suspendidos y mantener §21 pendiente |
| specs 15/16 | Derivar contratos de casos de inclusión; no partir de curación como verdad |
| `tests/promises` | Añadir promesas semánticas solo al existir evidencia de conformidad |

## Aceptación

Un manifest enumera C01–C28 con `required/deferred/excluded`, razón, fixture, resultado esperado, implementación, evidencia y desviación. No se afirma superset antes de cerrar todos los `required`. Toda capacidad diferida conserva su lugar visible en el objetivo. La inclusión de futuro/legacy requiere decisión explícita de alcance, no una importación masiva.
