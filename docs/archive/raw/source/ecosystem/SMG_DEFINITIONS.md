# S, M, G: La Trinidad del Compilador de Conocimiento (v0.3)

Primary axis: definition
Canonical scope: core SMG vocabulary and interrelations
Depends on: none
Should not duplicate: pipeline mechanics, repo taxonomy, rollout strategy

Este documento define el vocabulario conceptual core del ecosistema Hum, donde el conocimiento se transforma desde señales humanas hacia estructuras operables y hechos mundiales.

## 1. S (Surface Context / La Capa de Anclaje)
**El Puente y el Lente.**
* **Indexador:** Ancla el conocimiento a la realidad (Quién, Cuándo, Para qué). Es la clave de la trazabilidad y el perspectivismo.
* **Mapeador:** Traduce signos superficiales (tokens, palabras) a identidades canónicas (identificadores únicos en M).
* **Tipador:** Define la naturaleza del contenedor (Receta, Historia, Técnica). El tipo de S pre-configura el espacio lógico de M.
* **Familias:** Coordinadas de agrupación semántica en la capa de superficie. Ver `docs/concepts/FAMILIES.md`.

## 2. M (Meaning / La Capa Semántica Profunda)
**El Cerebro y el Álgebra.**
* **Forma Lógica:** Representación estructural (S-expressions) que captura la intención y la lógica pura.
* **Operabilidad:** Aplica reglas de reescritura para detectar emergencia (abstracciones de alto nivel) y realizar álgebra de mundos (unión/intersección de lógicas).
* **KG-Trees:** Forma intermedia operable entre estructura superficial y persistencia en grafo. Ver `docs/concepts/KG_TREES.md`.
* **Filtro de Sentido:** Clasifica la entrada como Sinn (Sentido), Sinnlos (Vacuidad) o Unsinnig (Sinsentido técnico). Ver `docs/concepts/HEURISTIC_OF_SANITY.md`.

## 3. G (Graph / La Capa de Proyección)
**El Mundo y la Memoria.**
* **Hechos Proyectados:** Grafo de triples (Sujeto-Predicado-Objeto) resultante de la validación en M.
* **Persistencia Indexada:** Cada hecho en G mantiene un vínculo indestructible con su origen en S.
* **Consultabilidad:** Sustrato optimizado para consultas rápidas, visualización y contexto RAG para agentes.

---

## Interrelaciones Críticas

### A. Configuración (S ➔ M)
S provee el "diccionario" y el "esquema". M no puede operar sin los mapeos y firmas definidos por el contexto de S.

### B. Consolidación (M ➔ G)
M procesa la lógica y, una vez validada, "congela" los resultados en G. G es el cementerio de las ideas que M ha demostrado que tienen sentido.

La regla que gobierna ese paso de validación está descrita canónicamente en `docs/concepts/HEURISTIC_OF_SANITY.md`.

### C. Perspectivismo (G ➔ S)
El usuario o el agente pueden mirar el grafo G a través de diferentes lentes S, obteniendo versiones distintas (pero consistentes) de la realidad.

### D. Autopoiesis (G/M ➔ S)
Cuando el sistema detecta inconsistencias en G o sinsentidos en M, tiene la capacidad de generar nuevos registros en S (preguntas, tareas, alertas) para cerrar el bucle de mantenimiento.
