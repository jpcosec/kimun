# Modelo de sistema viable (VSM) y kimun — Claude (2026-11-14)

Investigación; sin código ni cambios de producto; no diseña G.

## La fuente (Wikipedia, en)

El VSM de Stafford Beer (*Brain of the Firm*, 1972) modela la organización de un sistema autónomo; según el artículo,
corresponde a la autopoiesis en el nivel biológico y deriva del sistema nervioso. Cinco sistemas:
1, actividades primarias, cada una viable; 2, canales y coordinación entre ellas; 3, reglas y recursos, con auditoría
(3*); 4, mira al entorno y a la adaptación; 5, política. Es **recursivo**: todo sistema viable contiene sistemas viables
y está contenido en uno; por eso las unidades del sistema 1 tienen autonomía. **Variedad** (Ashby) es el número de
estados posibles. Los **canales** necesitan más capacidad que quien genera la información, y al cruzar una frontera hay
**transducción**, cuya variedad no puede ser menor que la del canal. Las **alertas algedónicas** escalan por los niveles
cuando el desempeño real se desvía de la capacidad.

No menciona a Maturana, memoria ni sensores.

## Inferencias (mías)

**Delimitación.** El VSM describe una organización que actúa. Kimun no realiza actividades primarias ni decide
política: no es ninguno de los sistemas 1–5. Sirve a una organización viable (dueño, agentes, repos) como soporte
sensorial y memoria. En el VSM la analogía nerviosa abarca toda la gestión, decisión incluida; la del dueño es más
estrecha. Los sistemas 1–5 no son S/M/G, y un nodo no es un sistema viable: los niveles de kimun son de observación.

**Regulación.** Kimun la permite; no la ejerce ("señal ≠ acción", `pi.md §3.4`).

**Autonomía y entorno.** Las fuentes cambian sin pedir permiso: por eso la continuidad se registra, no se supone.
Vocabulario: el "ambiente" de `docs/v2/14-juego-del-lenguaje.md §19` está, para el VSM, dentro de la organización.

**Canales y variedad.** El contrato de observación es un transductor: debe distinguir al menos lo que la
organización necesita regular (una huella por archivo no ve cambios por función). Trackeado frente a trackeable
(`14 §20`) reduce variedad a propósito.

**Alertas.** No toda mutación es alarma; la pérdida de soporte de conocimiento sí (`docs/v2/00-nucleo.md` 2.4).

**Otras fuentes.** Zettelkasten es memoria direccionable; EPL2R, una actividad humana de elaboración; autopoiesis,
la identidad de la unidad. El VSM agrega a quién sirven las señales.

## Qué cambia en la propuesta

1. El contrato de identidad no cambia.
2. Criterio nuevo para el contrato de observación: variedad suficiente para lo que se quiere regular.
3. Distinguir señal informativa de alerta que escala, y decir quién la recibe; kimun no la resuelve.

Nada es arquitectura aprobada.

## Fuentes

- Wikipedia, «Viable system model»: https://en.wikipedia.org/wiki/Viable_system_model (consultado 2026-11-14).
- Beer, *Brain of the Firm* (1972): citado por el artículo; no leído.
- Internas: `runs/research/2026-11-14/concilio/{brief,pi,zettelkasten-claude,epl2r-claude}.md`.
