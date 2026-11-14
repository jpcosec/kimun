# Heartbeat, sistema asíncrono y unidad como convergencia para LLMs (Agy)

## 1. Mandato explícito del dueño (corrección de dirección)
1. **Daemon de *heartbeat* (autopercepción):** Existirá un daemon cuya **única función** es ejecutar el *heartbeat* para recalcular señales (momento de autopercepción). Opera dentro de un sistema asíncrono donde mutaciones del entorno, señales externas y respuestas a acciones internas permiten activar respuestas posteriores.
2. **Escala de autopoiesis y VSM:** Autopoiesis y VSM aplican **al sistema completo**, no a la unidad aislada. El papel de la unidad es habilitar la **recursión** entre escalas.
3. **Sistema para LLMs y redefinición de unidad:**
   - Se **retira** de [unidad-agy.md](file:///home/jp/proyectos/kimun/runs/research/2026-11-14/concilio/unidad-agy.md) y [autopoiesis-agy.md](file:///home/jp/proyectos/kimun/runs/research/2026-11-14/concilio/autopoiesis-agy.md) la definición obligatoria de unidad como «elaboración/pregunta/práctica con ciclo o clausura propia».
   - **Unidad = nodo con identidad propia y espacio mínimo de convergencia**, donde se articulan sentido relacional **discreto**, **semántico** y **estructural**, además de **embeddings continuos**.
   - El valor reside en las **relaciones declarables**: jerárquicas, horizontales, métricas y/o recursivas (herencia, temporales, secuenciales).
   - **Restricciones duras:** `embedding ≠ identidad` y `similitud continua ≠ relación validada`.

## 2. Propuesta técnica: consecuencias y fronteras
*(Análisis nuestro derivado del mandato; no impone esquema persistente ni diseño de G.)*

### A. Frontera del daemon de *heartbeat* (autopercepción ≠ ejecución LLM)
- **Qué entra en el *heartbeat*:** Es el pulso propioceptivo acotado del sistema. Ante cambios en archivos, señales externas o resultados de acciones internas, recalcula determinísticamente huellas estructurales (Merkle), estados de anclaje (`fresh`, `drifted`, `orphan`, `ambiguous`), métricas relacionales/temporales y señales de desajuste, dejándolas disponibles para el sistema.
- **Qué queda fuera:** El *heartbeat* **no es un orquestador ni un ejecutor de LLMs**, no toma decisiones semánticas ni muta fuentes. Los agentes LLM, herramientas o humanos actúan como consumidores asíncronos de las señales emitidas; el efecto de sus respuestas vuelve a entrar al siguiente pulso como nueva mutación o señal, cerrando el ciclo VSM/autopoiético a escala del sistema.

### B. Frontera de la unidad como espacio mínimo de convergencia
- **Convergencia sin ciclo individual obligatorio:** Un nodo (función, sección, símbolo, nota o contenedor recursivo) es unidad por poseer **identidad direccionable propia** donde convergen cuatro planos consumibles por un LLM:
  1. **Estructural:** ubicación topológica y recursión (contención de subunidades).
  2. **Relacional discreto:** vínculos declarables jerárquicos (herencia, parte-todo), horizontales (dependencia, contraste), temporales/secuenciales (linaje, orden) y métricas discretas.
  3. **Semántico:** signo/símbolo interpretable en contexto.
  4. **Continuo:** embeddings vectoriales acoplados al nodo.
- **Separación entre geometría continua y relaciones declaradas:** El embedding sirve como señal métrica continua (búsqueda aproximada, detección de vecindad o sugerencia de candidatos durante el análisis asíncrono), pero cambiar de modelo de embeddings o de texto no altera la identidad del nodo, ni una alta similitud coseno crea o valida por sí misma una arista discreta sin declaración explícita.
