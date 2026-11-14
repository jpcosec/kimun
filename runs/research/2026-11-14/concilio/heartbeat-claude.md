# Heartbeat, sistema y unidad — Claude (2026-11-14)

Consecuencias y fronteras de la corrección del dueño. Sin implementación ni cambios de producto; no diseña G.

## Mandato del dueño

1. Un daemon cuya **única** función es el heartbeat: recalcular señales, como momento de autopercepción. El sistema
   es asíncrono: mutaciones, señales externas y respuestas a acciones internas pueden activar respuestas.
2. Autopoiesis y VSM aplican **al sistema**, no a la unidad. La unidad permite la recursión.
3. Es un sistema para LLMs. Cada unidad tiene sentido relacional **discreto**, semántico y estructural, y además
   embeddings **continuos**.
4. Unidad = nodo con identidad propia, espacio mínimo de convergencia. Su valor está en relaciones declarables:
   jerárquicas, horizontales, métricas y/o recursivas (herencia, temporales, secuenciales).
5. Se retira la definición obligatoria unidad = elaboración/pregunta/práctica con ciclo propio.
6. Embedding no es identidad; similitud no es relación validada.

## Qué retiro de mis informes

- `unidad-claude.md`: la definición por pregunta, respuesta y ciclo de revisión, y la continuidad atada a ese ciclo.
  Una elaboración revisada pasa a ser una clase posible de unidad, no la definición.
- `unidad-claude.md`: la separación tajante "nodo sensor (S) frente a unidad (M)". El mandato dice que la unidad **es**
  el nodo, donde convergen lo estructural y lo semántico.
- `autopoiesis-claude.md`, crítica 3: pedir "la organización de cada unidad" aplicaba al individuo lo que es del sistema.
- `epl2r-claude.md`: los cinco pasos quedan como un recorrido posible, no como contrato.

Se mantiene: clase distinta de individuo; identidad que no es hash, lugar, tema ni identificador (y ahora tampoco
embedding); kimun no elabora ni decide.

## Consecuencias (lectura mía)

- **El heartbeat es percepción, no acción.** Recalcula lo que ya es derivado: el estado de un anclaje no se persiste,
  se recomputa (`docs/v2/02-sustrato-computacional.md §10`, invariante 18). Cubre lo que ningún evento avisa: una
  edición hecha fuera de kimun no deja transacción.
- **Tres entradas, una salida.** Mutaciones, señales externas y respuestas a acciones internas entran; salen señales.
  Quién responde queda fuera del daemon.
- **Recursión por relaciones.** Una unidad puede relacionarse con unidades que la componen o la heredan. Las propiedades
  de sistema (regulación, regeneración) surgen del conjunto; ninguna unidad necesita ser viable.
- **Dos planos por unidad.** Lo discreto se declara y se puede verificar; lo continuo ubica vecindad. Coincide con
  `docs/v2/14-juego-del-lenguaje.md §21` (embedding = ubicación) y P11 (co-ocurrencia ≠ `(R A B)`).

## Fronteras

- El heartbeat no ejecuta LLMs, no muta el grafo, no reconcilia, no escala alertas. Da el reloj; la organización actúa
  (`02 §10`, invariante 11: un efecto externo nunca muta el grafo directamente).
- La similitud puede **proponer** una relación; solo una declaración la establece (`docs/v2/00-nucleo.md` 4.3).
- Declarar una relación no es evaluarla como hecho: eso es G.

## Propuesta técnica (no mandato)

- Latido idempotente: dos latidos sin cambios dan las mismas señales.
- Bajar solo por las ramas del Merkle que cambiaron (`14 §20`), bajo `memcap` (`AGENTS.md`).
- Qué recuerda el daemon entre latidos queda abierto; no propongo esquema persistente.

## Abierto

- Qué es "identidad propia" en positivo: el mandato dice dónde converge, no cómo se nombra.
- "Relaciones métricas": las leo como relaciones declaradas con magnitud, distintas de la distancia entre embeddings.
- El backlog pone embeddings después de G (`docs/v2/12-ecosistema-y-pendientes.md §1`, G3); el mandato los pone en cada unidad.
