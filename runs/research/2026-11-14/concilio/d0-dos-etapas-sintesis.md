# Síntesis D0: dos etapas

El brief conserva las palabras textuales del dueño y las fuentes. Claude,
Agy y Pi coinciden en que la separación `realidad → anchoring → hecho detectado`
resuelve el error conceptual que mezclaba soporte, contenido y hecho en un ID.
La huella del anchor acredita vigencia de lo marcado; la verdad de una
proposición se contrasta con la realidad. Una mutación del soporte pide revisión;
por sí sola no convierte un hecho en falso.

**Veredicto: D0 parcialmente desbloqueada.** Quedan habilitados el diseño y la
implementación de sensorización/observación de la primera etapa que preserven
los IDs actuales como detalle transitorio (K2a, K6, K8 como localizador, S1).
Para reemplazar la identidad del kernel (K1 y dependientes) falta definir en
positivo la identidad y continuidad de los nodos, y cómo se registran los
vínculos entre ambas etapas sin inferir verdad desde la huella.

**Cuidado con las propuestas de los informes:** las dos etapas no fijan por sí
solas que siempre existan exactamente dos nodos persistidos. Agy propone una
tupla de ruta/selector/contrato/huella como identidad operativa del anchor;
esa tupla puede describir una observación, pero incluye localizador y huella,
que el dueño excluyó como identidad del nodo. Claude propone identidad lógica
del hecho por contexto y `(R A B)`; tampoco se considera decidida la
individuación de dos detecciones iguales en fuentes distintas. Pi deja ambas
identidades abiertas. La arquitectura concreta debe resolver esos casos.

Próximo caso discriminante: dos fuentes independientes sostienen la misma
proposición; una cambia y la otra no. El sistema debe mostrar dos soportes,
una o varias unidades detectadas según regla explícita, vigencia por soporte,
y el efecto de la mutación sin cambiar automáticamente el valor de verdad.

No hubo cambios de código ni tests en esta revisión.
