# D0 — contrato positivo de identidad y continuidad (derivación de Pi)

Read-only. Sin código ni `bb`. Distingo **[dueño]** (palabras textuales o premisas 1–8 del
brief), **[fuente]** (Tractatus, paper, Zettelkasten, contrato vigente) e **[inferencia]**.
Numeración de premisas: la del brief `kimun-D0-contrato.md`.

## 1. Regla de identidad, en positivo

### Etapa 1 — `anchor` (la unidad marcada en una fuente)

**Nace** en el acto declarado de marcar una unidad para su seguimiento bajo un perfil de
indexación: `docs track|ingest|create` sobre una fuente y un ámbito declarados. No nace al
leer, ni al hashear, ni al escribir bytes: leer y hashear no crean nada (premisas 1, 8;
[fuente] `14 §20`: trackeable ≠ trackeado).

**Qué la determina** (propiedades, no codificación):

1. el **acto de declaración** (quién/ cuándo/ bajo qué contrato de parse) — [inferencia] de
   premisa 8 («un símbolo nuevo nace en el contexto de su documento… es curaduría explícita»);
2. la **referencia a la fuente**, que es un atributo localizador y por tanto **mutable**
   (premisa 1: no es anclaje estructural; [fuente] Zettelkasten: la ficha se direcciona y
   participa en una red, la referencia a la fuente es otra cosa);
3. el **ámbito de la marca**: perfil/clase y extensión (documento, bloque, campo) al nivel de
   abstracción declarado (premisa 2: «con niveles de abstracción»);
4. la **huella observada** de lo marcado, como campo de la observación, no como identidad
   (premisa 1, 4; [fuente] `14 §21`: el hash es sensorización);
5. el **eslabón de linaje** hacia el anchor anterior de la misma unidad, si lo hay
   (premisa 3: etapa 1 es observación; [fuente] Zettelkasten: la ficha es direccionable y
   enlazable, no reescrita).

**Qué no la cambia nunca**: el contenido, el hash, la ruta, la posición en el árbol, el número
de ficheros de la fuente. Cambian *atributos de la observación*, no la identidad. Lo único que
cambia la identidad es una **declaración**: «esto continúa como aquello», «esto es otra cosa»,
«esto se borró» (premisa 8: ruptura de anclaje se declara; borrado y mutación son distintos).

### Etapa 2 — hecho detectado (unidad/proposición)

**Nace** cuando se declara un hecho sobre hechos: una proposición `(R A B)` en un contexto
`W_i`, con `A` y `B` identidades de unidad ya existentes (premisas 3, 5). No nace del texto que
la soporta ni de la huella: la huella solo comprueba vigencia del anchor (premisa 4).

**Qué la determina** [inferencia]: su **coordenada lógica** `(contexto, R, A, B)` sobre
identidades, no sobre cadenas: dos fuentes distintas que detectan la misma proposición producen
**la misma** unidad, con dos soportes; corregir `R`, `A` o `B` produce **otra** unidad, ligada a
la anterior por una declaración de corrección. Es la lectura que hace posible premisa 3
(realidad ≠ hecho: el hecho no está atado a su fuente) y premisa 7 (ficha direccionable dentro
de una red, no copia de la fuente). **Interpretación mía, no palabra del dueño.**

**Qué no la cambia**: el soporte (qué anchors la evidencian), el orden de lectura de las
fuentes, el formato del texto. La verdad **no** forma parte de su identidad: se evalúa contra la
realidad (premisa 4).

## 2. Regla de continuidad

Se sigue una unidad por **linaje declarado**, no por parecido:

1. El sensor, por sí solo, solo puede decir: «lo marcado bajo esta huella **cambió**»
   (`:hash-mismatch`/`mutated`), «**falta**» (`missing`), «**no verifica**» (`rebuild-mismatch`),
   o «el contrato cambió» (premisa 4; [fuente] `14 §21`). Eso es vigencia de anchor, no identidad.
2. La continuidad es una **relación explícita** entre una observación anterior y una posterior
   (`continues`), con su autor y su motivo; sin ella, dos observaciones son dos unidades.
   [inferencia] de premisas 1 (identidad no topológica ⇒ no se puede re-apuntar sola) y 8
   (ascender de contexto o declarar equivalencia es curaduría explícita).
3. **Niveles de abstracción**: el linaje se declara al nivel en que se marcó. Un cambio por
   debajo de ese nivel (reformateo, reordenación de párrafos) no abre linaje nuevo: lo detecta
   la huella. Un cambio que rompe el nivel (partir un documento, fusionar dos) sí exige
   declaración (premisa 2).
4. Los hechos no heredan el linaje de sus anchors: un anchor puede soportar varios hechos y un
   hecho puede sobrevivir a sus soportes (premisa 3). El linaje de hechos se declara entre
   coordenadas.

## 3. Tabla de casos

| caso | identidad del anchor (etapa 1) | identidad del hecho/unidad (etapa 2) | relación `(R A B)` registrada | detecta el sensor solo | exige declaración |
|---|---|---|---|---|---|
| **editar** (cambia lo trackeado) | misma, nueva observación con nueva huella | la unidad sigue; si el hecho cambia de términos, nueva coordenada | `(continues a a')` si se declara continuidad; evidencia nueva | «cambió» (`:hash-mismatch`/`mutated`) | sí: ¿continuidad o reemplazo? y qué hechos siguen vigentes |
| **reformatear** (mismo contenido normalizado) | misma observación, huella de proyección igual | igual | ninguna nueva | «reformatted» (texto cambió, estructura no) | no |
| **mover** (misma fuente a otra ruta) | misma identidad; la ruta es atributo | igual | ninguna nueva | «fuente ausente en la ruta anterior» (`source/missing`), no «movido» | sí: re-apuntar el locator (o declararlo borrado+alta) |
| **copiar** (dos fuentes con el mismo texto) | **dos** anchors (dos actos de marca) | si la proposición es la misma coordenada: **un** hecho con dos soportes | `(soporta)` ×2 | dos fuentes presentes; no puede decidir si es copia o duplicado | sí, para declarar equivalencia de unidades si se quiere compartir linaje |
| **dividir** (una unidad en dos) | dos anchors nuevos; el viejo se cierra | dos unidades (o una + otra) según qué se afirme | `(continues viejo nuevoA)`, `(continues viejo nuevoB)` | que el anchor viejo ya no cubre lo que cubría | sí: el reparto (qué parte continúa cuál) |
| **fusionar** (dos en una) | un anchor nuevo; los dos viejos se cierran | una unidad; los hechos previos siguen con sus coordenadas o se corrigen en bloque | `(continues viejoA nuevo)`, `(continues viejoB nuevo)` | que ambos anchors viejos desaparecen | sí: la fusión y el destino de cada hecho |
| **borrar** | el anchor se cierra y **se declara**; no queda anclaje falso | los hechos que dependían pueden perdurar en otra capa o revisión | `(borrado a)`; `(sin-soporte h a)` | «falta» (`missing`) | sí: declarar el borrado (premisa 8) |
| **misma proposición en dos fuentes** | dos anchors distintos | **una** coordenada: un hecho, dos soportes | `(R A B)` una vez; `(soporta a h)`, `(soporta b h)` | que hay dos fuentes; no que digan lo mismo | sí para declarar que son la misma proposición si el extractor no lo hizo |

## 4. B1–B4

- **B1 (re-apuntar vs. observaciones inmutables ligadas por declaración): resuelto por
  derivación** de premisas 1, 2, 3 y 8 más Zettelkasten. Si la identidad no es lugar ni hash,
  el anchor no puede «seguir solo» a la topología; la continuidad es una relación declarada
  entre observaciones. No queda elección para el dueño.
- **B2 (identidad positiva y codificación; coordenada vs id propio del hecho): resuelto en
  propiedades, irreductible en representación.** De premisas 1–3, 5 y 7 se sigue *qué debe
  cumplir* el nombre (estable, independiente de hash y ruta, direccionable y enlazable) y que el
  hecho se individúa por su coordenada sobre identidades, con corrección declarada, no por
  reescritura. **No se sigue** cómo se acuña o codifica ese nombre; el dueño prohibió aprobar
  CAS, UUID o lugar, y ninguna premisa elige una codificación.
- **B3 (término base de `(R A B)`; sensor y hecho en un nodo o en dos): resuelto por
  derivación.** Premisa 5 dice que `R` relaciona **hechos**; un anchor es una observación de
  vigencia, no un hecho (premisas 3–4). Luego el término base es un hecho elemental de
  observación (`(anclado-en …)`), no el anchor desnudo. Y por premisa 3 (dos etapas
  conceptualmente distintas) más premisa 6 (la unidad tiene identidad propia), anchor y hecho no
  comparten identidad: son dos unidades ligadas por soporte/evidencia. [inferencia] sostenida en
  las premisas, no palabra literal.
- **B4 (deslinde S–M vs G y ejecuciones marcadas): irreductible en parte.** Se deriva qué puede
  detectar `S–M`: vigencia del anchor, cambios topológicos de lo trackeado y, si se marcan
  ejecuciones, la **verificación 2** (premisas 2, 4, 8). No se deriva el *contrato operativo* de
  las ejecuciones ni la lógica de verdad y sentido, que son `D-G`/`G`. **Caso mínimo:** una
  ejecución devuelve 42 y su recibo dice 42 — las premisas no dicen qué hace verdadero el
  resultado (¿el contrato del verificador, la repetición, la firma?); y dos documentos que
  afirman proposiciones contradictorias no se resuelven por topología.

## 5. Ambigüedad irreductible real

Queda solo lo que las premisas no alcanzan:

1. **Representación del nombre** de anchor y de hecho: propiedades fijadas (estable,
   independiente de hash/ruta, direccionable, enlazable), codificación no elegida por el dueño.
2. **Contrato entre etapas**: cuándo una observación de vigencia se **promueve** a hecho
   detectado, y **quién** puede declararlo (sensor automático vs curaduría). Las premisas dicen
   que el sensor no fija verdad y que ascender es curaduría explícita; no fijan el disparador ni
   el umbral.
3. **Ejecuciones marcadas y frontera con `G`**: el contrato de verificación 2 y la lógica de
   verdad/sentido (`D-G`). No es identidad: es el límite operativo entre `S–M` y `G`.

Nada de esto obliga a reabrir las premisas: son decisiones de implementación y de contrato
operativo, no de identidad.
