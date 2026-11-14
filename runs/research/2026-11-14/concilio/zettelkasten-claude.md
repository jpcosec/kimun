# Zettelkasten y kimun — Claude (2026-11-14)

Investigación; sin cambios de producto ni de `docs/`. No diseña G. Complementa `runs/research/2026-11-14/concilio/claude.md`.

## Qué dice el artículo (Wikipedia, es)

Método de gestión de conocimiento con muchas notas individuales. Se numeran jerárquicamente para poder insertar notas
nuevas en su lugar, llevan metadatos para vincularlas, etiquetas y referencias cruzadas. Las referencias muestran
conexiones que las notas aisladas no dejan ver, "como una red neuronal" con la que se conversa. Luhmann usó unas
90.000 fichas y describió el método en *Kommunikation mit Zettelkästen*, en el marco de la teoría de sistemas.

**El artículo no establece ningún contrato de identidad en el tiempo**: no dice qué pasa con una nota que se edita,
mueve, divide o borra.

## Qué agrega Luhmann (fuente primaria, traducciones)

- **Lugar fijo**: cada ficha recibe un número que "nunca cambia"; no hay orden temático.
- **Ramificación**: se puede continuar desde cualquier punto (57/12a, 57/12a1) sin renumerar.
- Porque el número es fijo, admite cuantas referencias se quiera; hace falta un **registro de palabras clave** para entrar.
- La nota vale por su red de referencias; "no hay lugares privilegiados"; una nota sin conexión "se pierde".
- Con masa crítica la caja sorprende: contiene más información que la anotada.
- Caja aparte para la bibliografía leída.

## Relevancia para kimun (interpretación mía)

**Identidad direccionable.** El número de Luhmann no es huella ni tema: es una dirección asignada una vez,
independiente del contenido. Coincide con la mitad negativa de la corrección del dueño (ni hash ni significado).
Pero es un lugar, y funciona porque la caja es **memoria propia**: nadie mueve ni copia fichas desde fuera. Kimun
sensa un ambiente que muta por fuera. Lección: la dirección fija sirve para el registro propio de kimun; en el
ambiente el lugar sigue siendo localizador.

**Vínculos y contextos.** Las referencias se acumulan sobre direcciones estables; el registro de palabras clave es
puerta de entrada, no identidad (compatible con K8 de `docs/v2/12-ecosistema-y-pendientes.md §1`: el nombre corto es
alias). "La nota vale por su red" equivale a `docs/v2/00-nucleo.md` 1.1.

**Emergencia.** La sorpresa de Luhmann es la del sistema nervioso: estados que generan estados. Refuerza que kimun
registra lo distinguido y sus vínculos, no una copia del mundo (`00-nucleo` 6.2). Contraste: Luhmann acepta la pérdida
silenciosa; kimun la prohíbe (`00-nucleo` 2.4).

**Niveles de abstracción.** Nota, secuencia ramificada, registro y caja bibliográfica son niveles distintos. La caja
bibliográfica separada es el análogo más cercano: referencias al ambiente por un lado, conocimiento propio por otro
(`docs/v2/14-juego-del-lenguaje.md §19`).

## Qué cambia en la propuesta

1. **Se agrega un requisito al identificador abierto**: asignado una vez, nunca cambiado, independiente de contenido,
   tema y ruta, para que las referencias se acumulen. No elige codificación.
2. **Dos direcciones, no una**: dirección en el registro propio (estable) y localizador en el ambiente (mutable).
3. **Ramificación como procedencia**: dividir o derivar puede registrarse como "continúa desde", sin renumerar al
   origen. Es una sugerencia para los casos dividir/copiar, no una regla.
4. **No cambia la continuidad**: el Zettelkasten no dice nada de mover, editar o fusionar. Sigue haciendo falta
   linaje registrado; un número fijo no lo explica.
5. "La nota no conectada se pierde" coincide con trackeable frente a trackeado (`14 §20`), sin cambio.

## Fuentes

- Wikipedia, «Zettelkasten»: https://es.wikipedia.org/wiki/Zettelkasten (consultado 2026-11-14).
- N. Luhmann, *Kommunikation mit Zettelkästen. Ein Erfahrungsbericht*. Traducciones: M. Kuehn,
  https://luhmann.surge.sh/communicating-with-slip-boxes ; revisada, https://zettelkasten.de/communications-with-zettelkastens/
  (esta fecha el original en 1981; Wikipedia cita la edición de 1992). Leídas por extracto, no el alemán.
- J. Schmidt (2016), *Niklas Luhmann's Card Index*, citado por Wikipedia: **no se pudo leer** (acceso bloqueado).
- Internas: `docs/research/autopoiesis/README.md`, `runs/research/2026-11-14/concilio/{brief,claude,pi}.md`.
