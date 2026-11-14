# Zettelkasten y kimun — nota breve para el concilio

## Qué aporta el artículo (≤200 palabras)

La Wikipedia describe el Zettelkasten como una caja de notas individuales, numeradas de forma
jerárquica para poder insertar nuevas notas, con metadatos, etiquetas y referencias cruzadas. La
numeración, el formato y la estructura varían según el método. El artículo atribuye al método una
función emergente: las referencias cruzadas permiten percibir conexiones entre piezas que aisladas no
se verían; menciona a Luhmann, su archivo de ~90.000 fichas y su ensayo *Kommunikation mit
Zettelkästen*. Es una descripción de método, no un contrato formal de identidad.

## Por qué es relevante

- **Identidad direccionable.** Un Zettel necesita una dirección única para poder ser referido. Eso
  coincide con la intuición del dueño: la identidad debe permitir *referir* una unidad marcada, no
  derivarse del hash ni de su posición actual. Pero el artículo **no** define qué ocurre con esa
  dirección al editar, mover, copiar o borrar; no le atribuyo continuidad temporal. Nuestra propuesta
  de linaje sigue siendo nuestra, no de Wikipedia.
- **Vínculos y contextos.** El valor no está en coleccionar notas, sino en relacionarlas; Luhmann
  insiste en registrar el contexto del enlace y los backlinks. Es la misma dirección que kimun:
  proposiciones en contexto, aristas con evidencia y procedencia. Zettelkasten añade una exigencia
  concreta: el enlace debe decir *por qué* existe, no solo que existe.
- **Emergencia.** Las conexiones producen patrones que no están en una nota aislada. En kimun eso
  respalda que el grafo y sus vistas sean emergentes y derivados, no una taxonomía impuesta. Pero
  emergencia no es autoridad: kimun sigue exigiendo justificación y no acepta coocurrencia como
  proposición.
- **Niveles de abstracción.** Hay Zettel, secuencias, registro de entrada y red completa. Es un buen
  recordatorio de que “unidad” depende del contrato: una nota atómica, una sección, un símbolo o una
  región de código no son la misma unidad. El principio de atomicidad es una guía de granularidad,
  no una ley universal.

## Qué cambia en nuestra propuesta

1. **Refuerza la dirección estable como requisito de enlace**, separada del localizador mutable. Un
   id de unidad debe poder citarse sin depender de la ruta actual.
2. **Hace del contexto del vínculo un dato de primera clase**: además de `from/to`, evidencia y
   contexto, conviene registrar la razón del enlace (el “link context”).
3. **Confirma que la unidad no es el nodo de contenido**: el mismo texto puede ser otra unidad en
   otro lugar; la igualdad de bytes no basta.
4. **Añade la inserción local**: la numeración de Luhmann permite intercalar sin renumerar. Nuestro
   localizador/selector debe permitir lo mismo sin convertir la ruta en identidad.
5. **No cambia** la decisión de fondo: el linaje, la membrana y la continuidad siguen siendo
   propuesta abierta. Zettelkasten inspira la direccionalidad y la emergencia, no resuelve los casos
   mover/editar/copiar/dividir/fusionar/borrar.

**Interpretación propia:** el Zettelkasten es útil como crítica al “store de contenido” y como
defensa de la referenciabilidad y del enlace contextual; no como modelo de identidad temporal ni como
sustituto de la sensorización Merkle.

## Fuentes

- Wikipedia, *Zettelkasten*: https://es.wikipedia.org/wiki/Zettelkasten
- Luhmann, *Communicating with Slip Boxes*: https://zettelkasten.de/communications-with-zettelkastens/
- Luhmann, *Kommunikation mit Zettelkästen. Ein Erfahrungsbericht* (1992).
- Schmidt, *Niklas Luhmann's Card Index* (2016): https://pub.uni-bielefeld.de/download/2942475/2942530/jschmidt_2016_niklas%20luhmanns%20card%20index.pdf
- Introducción metodológica: https://zettelkasten.de/introduction/
- Archivo Luhmann: https://niklas-luhmann-archiv.de/

Sin tests, sin cambios de producto, sin diseño de G.
