# P4 — Grafo contextual y frontera con motores

Estado: **propuesta**. Atiende F03–F06/F13–F17/F19/F21/F22. Hay decisiones abiertas del dueño sobre verdad/sentido; esta propuesta ofrece separación operacional para discutirlas, sin imponer los estados suspendidos de spec15.

## Modelo mínimo

1. **Source:** qué se observó, locator, versión, hash y contrato de parse.
2. **Contexto:** dominio local y relación con otros contextos; vocabulario/firmas y reglas versionados.
3. **Símbolo local:** signo usado en ese contexto y sus bindings/ocurrencias.
4. **Proposición:** forma lógica recursiva, contexto de evaluación y argumentos tipados.
5. **Soporte:** anchor(s) o inferencia con premisas, reglas y transports.
6. **Evaluación:** valor de verdad y categoría de sentido calculados bajo un contrato.
7. **Correspondencia:** vínculo contextual entre símbolos/juegos con dirección, soporte y acción registrada.

### Ejes separados

| eje | pregunta | ejemplo |
|---|---|---|
| Soporte | ¿De dónde sale? | Anclado al source / derivado por regla |
| Verdad operacional | ¿Qué vale en este mundo bajo sus supuestos? | TRUE / FALSE / UNKNOWN |
| Sentido | ¿Puede expresarse/evaluarse aquí y discrimina? | sinnvoll / sinnlos / unsinnig |
| Revisión/política | ¿Quién puede admitir una fuente/correspondencia/contrato? | Evento de decisión y versión de política |
| Calidad del extractor | ¿Qué evidencia hay de fidelidad de extracción? | Método, versión, validación, confidence |

La etiqueta «anclado» prueba correspondencia a una fuente, no automáticamente verdad sobre la realidad. «Derivado» describe el soporte formal, no un grado de aprobación. Si el dueño quiere reemplazar el eje TRUE/FALSE/UNKNOWN, esa decisión debe especificar cómo se contestan falso explícito y desconocimiento conservando C06/C17.

## IR lógico y reglas

Datos recursivos para átomos, `and/or/not/if`, referencias a reglas y variables con scope. Validar operador, aridad, tipo, contexto y coste antes de compilar. No todo EDN canónico es fórmula admisible.

Datalog puede consultar y generar candidatos. La ausencia de un triple no representa FALSE; negación por fallo no se adopta sin contrato de mundo cerrado. El motor booleano trata admisibilidad y verdad; los dos motores deben coincidir en los casos compartidos o reportar límites de expresividad.

Reglas con variables tienen universo finito y scope contextual explícitos. Grounding perezoso por bindings candidatos antes que producto cartesiano global; presupuesto y salida `incomplete` al agotarse. Esquemas se versionan separados de sus instancias compiladas.

## Contrato de motor — versión nueva, no sobrecargar adapters 1

Los adapters actuales emiten hechos estructurales de código/CLI. El productor de declaraciones y el evaluador tienen funciones distintas. Proponer protocolo semántico con `describe`, `extract`, `converge`, `evaluate` según capability declarada.

Cada petición contiene request-id, source hash/contract, contextos, símbolos locales y hashes de inputs. Cada respuesta contiene la misma referencia de inputs, resultados normalizados, soportes, diagnósticos, coste e indicador de completitud. Stats decimales deben serializarse con regla compatible con canonical EDN (sin floats internos no admitidos); fijar precisión, escala y versión, no redondear silenciosamente φ antes de umbral.

El proceso no escribe el store. Kimun valida que no inventó signos fuera de scope, que los spans se verifican, que los contextos/IDs existen y que las capacidades/políticas autorizan la acción. Solo entonces prepara un plan atómico.

Timeout, output malformado, versión incompatible, inputs stale o presupuesto agotado no pueden confirmar conocimiento ni dejar media transacción aplicada. Una respuesta con request-id correcto pero input hashes diferentes se rechaza. Reintentar es idempotente por acción y contrato, no por timestamp.

Conservar NumPy/SciPy/SIA primero. Portar álgebra a Clojure solo si simplifica frontera o mejora mediciones; no escoger Datalevin como sustituto de todas las semánticas de Matrix por compartir consultas de triples.

## Convergencia y equivalencia

Conservar tres usos de CHIC: taxonomía de contextos, convergencia de símbolos y mantenimiento de mundos. La salida estadística es soporte para revisar una correspondencia, nunca demostración lógica de verdad factual. Persistir parámetros, soporte informativo, contextos/fuentes independientes y su versión.

Regla de convergencia y decisión humana son políticas de admisión de correspondencias, con poder para resolver identidad operativa bajo un contexto; no prueban identidad universal. Conservar negativas/rechazos para evitar repetición solo si sus inputs/contrato siguen vigentes.

Bootstrap puede normalizar co-participante como evidencia superficial; las siguientes pasadas usan correspondencias versionadas. Registrar cuándo cambia esa base y exigir convergencia/idempotencia de particiones, no IDs nominales de grupos.

## Consulta, deducción y generación

Una respuesta incluye mundo evaluado, revisión de conocimiento, contratos, truth, sense, prueba y citations. Cada salto registra una correspondencia autorizada y el término transportado. El término medio de W2 queda explícito; componer dos relaciones no crea automáticamente una tercera relación afirmada.

Usar el mismo gate de sentido en búsqueda, deducción, transporte y generación. Una celda TRUE con sentido inadmisible no contribuye a respuesta. Diferenciar consulta sin resultado, falso explícito, ambigüedad, inadmisibilidad, inputs stale y presupuesto agotado.

Prueba como DAG con AND para premisas conjuntas y OR para demostraciones alternativas. Retractar/invalidadar un soporte conserva las demás pruebas válidas; recalcular dependencias hasta punto fijo dentro del componente afectado. Ciclos sin raíz válida no sostienen verdad.

Export durable conserva contexto, axiomas, FALSE/UNKNOWN explícitos, reglas, soportes, decisiones, correspondencias y versiones. Generación de prosa/S-expr visible es una proyección diferente, que puede omitir elementos por propósito; no se la llama backup completo.

## Aceptación

Mismos fixtures de W1/W2 para máscara, FALSE/UNKNOWN, equivalencia dirigida, conjunctive rule, schema, middle term, reflexión y orden. Negativos para LLM con cita inventada, símbolo fuera de contexto, relación comodín, input hash viejo, inferencia cíclica sin soporte y prueba retractada con alternativa vigente. Para conflictos, nunca resolver por confidence/recencia sin política explícita y evento trazable.
