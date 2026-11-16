REFORMULACION

Propósito: ejecutar la etapa 3 del plan de producto: permitir varias tags por nodo en varios ejes, incorporar nodos de código y hacer que kimun construya el grafo de su propio código, sustituyendo `scripts/check_rings.clj`. Resultado: kimun responde consultas de ciclos y capas sobre su propio código. Repo: no se declara explícitamente; la ruta y el nombre sugieren el repositorio kimun, pero el input no fija ruta ni rama. Límites: los nodos de código quedan bloqueados hasta que el owner responda sobre la gramática Clojure para el pod; no se detallan ejes, formato ni criterios de paridad. Es una tarea ejecutable, no un agregado.

AMBIGUEDADES

1. "Several tags per node on several axes": no se enumeran ejes, cardinalidades ni semántica; obligaría a inventar taxonomía y modelo de datos.
2. "code nodes": no se define qué es un nodo de código ni su esquema; obligaría a inventar representación y extracción.
3. "Code nodes wait for the owner's answer on the Clojure grammar for the pod.": no se sabe qué respuesta falta ni su efecto; obligaría a inventar criterio de desbloqueo.
4. "kimun's own code graph comes from kimun and replaces scripts/check_rings.clj.": no se especifican paridad, alcance ni retirada del script; obligaría a inventar criterios de sustitución.
5. Validación vacía ("-") y "Done When" cualitativo; obligaría a inventar pruebas y umbrales.

DRY-RUN

Lecturas: TaskDoc, `plan-producto.md` etapa 3, `scripts/check_rings.clj` y modelo actual de nodos/tags. Cambios previstos: modelo de tags multieje, soporte de nodos de código, indexación del propio repo y retirada de `check_rings.clj`. Dependencias: respuesta del owner sobre gramática Clojure; etapa 3 del plan. Validaciones futuras: ejecutar kimun sobre su código y comprobar ciclos/capas, tests de tags y paridad con el script. Sin ejecutar nada.

FUTURO

El owner debe responder la gramática Clojure del pod; otro nodo debe aportar `plan-producto.md` etapa 3 y, si aplica, el contrato de paridad con `check_rings.clj`. Un humano decide el criterio de aceptación final.
