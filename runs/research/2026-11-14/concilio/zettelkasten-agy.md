# Zettelkasten y la frontera S–M en `kimun` (Agy)

## 1. Artículo y fuentes primarias enlazadas
Wikipedia ([Zettelkasten](https://es.wikipedia.org/wiki/Zettelkasten)) describe una caja de fichas breves con numeración jerárquica (para insertar notas en posición), etiquetas con índice temático y referencias cruzadas que producen conexiones emergentes análogas a una red neuronal con la que «conversar», destacando las ~90.000 fichas de Niklas Luhmann digitalizadas en 2019. **Límite:** Wikipedia no define contratos de identidad temporal ante mutaciones de archivos, ASTs o hashes.

Las fuentes primarias citadas —Luhmann (1992 [1981], *Kommunikation mit Zettelkästen*) y Schmidt (2016, *Niklas Luhmann's Card Index: Thinking Tool, Communication Partner, Publication Machine*)— precisan el diseño: Luhmann descarta clasificar por taxonomía temática fija y usa una *feste Stellordnung* (orden posicional fijo) con numeración ramificada (*Folgezettel*, ej. `21/3d26g10`). Ese código no significa un tema: otorga una dirección inmutable para enlazar fichas mediante referencias explícitas (*Verweise*), notas-hub de estructura y un índice selectivo de palabras clave (*Schlagwortregister*), operando como «generador de sorpresas» (*Überraschungsgenerator*).

## 2. Contraste con S–M y autopoiesis (Interpretación propia)

- **Identidad direccionable:** Luhmann demuestra por qué la identidad de una unidad **no puede ser una taxonomía temática ni su contenido**: si cambiar el sentido o texto alterara su dirección, las referencias cruzadas se romperían. En el fichero físico el papel (S) no se mueve, por lo que ubicación y dirección coinciden. En `kimun`, donde el medio S (archivos y código en git) se edita y desplaza topológicamente (`runs/research/2026-11-14/concilio/agy.md` §3), debemos separar tres cosas: **identidad direccionable estable** de la unidad marcada, **localizador topológico mutable** en S y **huella Merkle** del estado (Verdad 1).
- **Vínculos y contextos:** Como en `docs/v2/00-nucleo.md` (1.1, 3.2), una ficha no significa aislada ni por su carpeta, sino por sus enlaces (*Verweise*). Una unidad marcada en S–M participa en múltiples contextos y modelos sin duplicarse.
- **Emergencia y sistema nervioso autopoiético:** Luhmann (quien aplicó la autopoiesis de Maturana y Varela a sistemas comunicativos) trata el fichero como un sistema con clausura operativa acoplado al investigador (`docs/research/autopoiesis/sources/maturana-1975-reprint-1999.txt` pp. 154–157; `varela-intencionalidad-traduccion-2003.txt` §2.2). En `kimun`, el árbol Merkle añade la sensorización que el papel no tenía: detecta mutaciones en S (Verdad 1) o en ejecuciones marcadas (Verdad 2) para regular los vínculos dependientes en M.
- **Niveles de abstracción:** El archivo de Luhmann articula niveles distintos sin confundirlos: fichas bibliográficas (fuentes, L1), fichas atómicas de argumento (sub-topologías marcadas, L2), fichas colectoras de estructura (modelos/subgrafos en S–M, L3) e índice de entradas.

## 3. Qué cambia en nuestra propuesta (`agy.md` / `sintesis.md`)
1. **Dirección estable (*addressability*) además de continuidad:** La unidad marcada necesita un identificador direccionable estable como extremo de aristas/referencias mientras su localizador en S y su huella evolucionan.
2. **Ramificación explícita (`Folgezettel`) para `split`:** Al dividir o especializar una unidad marcada, el linaje debe distinguir la relación tronco→rama (`split-from` / ramificación) sin colapsar la rama nueva con la unidad original ni perder sus enlaces previos.
3. **Registro ligero sin copia del ambiente:** Como el *Register* de Luhmann, el store estable guarda direcciones de unidades, contratos, localizadores, vínculos y huellas, nunca copias durables del texto fuente.

## Fuentes
- Wikipedia (es): [Zettelkasten](https://es.wikipedia.org/wiki/Zettelkasten).
- Luhmann, N. (1992 [1981]). *Kommunikation mit Zettelkästen. Ein Erfahrungsbericht*. Haux, Bielefeld, pp. 53–61.
- Schmidt, J. F. K. (2016). *Niklas Luhmann's Card Index: Thinking Tool, Communication Partner, Publication Machine*, en *Forgetting Machines*, Brill, pp. 289–311 ([Niklas Luhmann-Archiv](https://niklas-luhmann-archiv.de)).
- Internas: `docs/research/autopoiesis/README.md`, `docs/v2/00-nucleo.md`, `docs/v2/14-juego-del-lenguaje.md`, `runs/research/2026-11-14/concilio/{agy,sintesis}.md`.
