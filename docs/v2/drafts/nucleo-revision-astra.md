# Revisión de la fusión — Astra, ronda 3

**Veredicto: requiere ajustes puntuales antes de aprobar.** D0 y D-G conservan correctamente su alcance abierto. No propongo añadir mecanismos ni nuevas decisiones. Los cambios siguientes corrigen alcance, una omisión y la dependencia entre proposiciones.

## 1. Una mutación no implica necesariamente pérdida de soporte

**Actual, 2.4:**

> Si el soporte muta o desaparece, se revisa lo que depende de él y se declara la pérdida; ninguna pérdida de soporte se oculta.

**Propuesta:**

> Si el soporte muta o desaparece, se revisa lo que depende de él; si se pierde soporte, se declara. Ninguna pérdida de soporte se oculta.

**Por qué:** la frase actual declara una pérdida también ante toda mutación. El dueño distingue mutación detectada y borrado declarado; además, un cambio en lo trackeable puede dejar intacto lo trackeado. La revisión determina las consecuencias; no debe anticiparlas como pérdida. Fuentes: `docs/v2/14-juego-del-lenguaje.md`, §20–§21; `docs/v2/drafts/fuentes-identidad.md`, §4.3.

## 2. Recuperar el modelo como forma estable y dar un padre a las clases

**Actual, 5:**

> Anclar y generar son relaciones distintas entre estructura y conocimiento.

**Propuesta:**

> Los modelos expresan formas estables de estructura y conocimiento; el anclaje y la generación tienen modelos distintos.

**Por qué:** P10 fija el modelo como tipo de subgrafo estable, no solo como selector o generador. Ese rasgo falta en la fusión. Además, con el padre actual, 5.1 y 5.2 introducen clases e inmutabilidad sin explicar su relación con la distinción anclar/generar. El padre propuesto permite que 5.1–5.2 desplieguen las formas estables y que 5.3–5.4 desplieguen sus dos direcciones. Fuentes: `docs/v2/14-juego-del-lenguaje.md`, P10 y §18.1–§18.3, §18.7.

**Actual, 5.2:**

> Las clases no cambian: una clase nueva no redefine sus instancias anteriores; reclasificar es explícito.

**Propuesta:**

> Una clase cambiada es otra clase; sus instancias siguen perteneciendo a la clase original hasta su reclasificación explícita.

**Por qué:** «sus instancias anteriores» tiene como antecedente inmediato una clase nueva, que aún no las tenía. La propuesta conserva exactamente la garantía del dueño: permanece la pertenencia a la clase original. Fuente: `docs/v2/14-juego-del-lenguaje.md`, §18.3.

## 3. Quitar una repetición y explicitar de dónde llegan los principios

**Actual, 2.2:**

> El conocimiento se forma desde fuentes estructuradas y desde principios explícitos; toda respuesta expone lo que la sostiene.

**Propuesta:**

> El conocimiento se forma desde fuentes estructuradas y principios explícitos, recibidos por instrucción o desde las fuentes.

**Por qué:** la segunda mitad repite la exigencia que ya gobierna 4 y 4.3. Suprimirla deja 2.2 centrada en los insumos de la formalización y permite nombrar las dos vías que el dueño fijó para los principios. No convierte toda instrucción en evidencia ni en una afirmación verdadera. Fuente: `docs/v2/14-juego-del-lenguaje.md`, P1.

## 4. Poner la reconciliación bajo la revisión del soporte

**Actual, 6.4:**

> Un cambio en el ambiente se localiza en lo que depende de él y se reconcilia ahí, con todas sus consecuencias y sin recalcular lo independiente. El registro propio no se reescribe: cada cambio es una acción registrada.

**Propuesta: sustituir ese párrafo por estos dos, en sus lugares respectivos:**

> **2.41** La revisión reconcilia todas las dependencias afectadas, sin recalcular lo independiente.

> **6.4** Los cambios del registro propio se registran sin reescribir su historia.

**Por qué:** la primera oración actual desarrolla la revisión del soporte de 2.4, más directamente que la autoridad por origen de 6. Su nuevo lugar hace explícita esa dependencia decimal y evita repetir «cambio en el ambiente». La segunda conserva bajo 6 la continuidad de procedencia, aclarando que lo que no se reescribe es la historia; no prohíbe reemplazar una representación reconstruible. Fuentes: `docs/v2/14-juego-del-lenguaje.md`, §19–§20; `docs/v2/01-orden-filosofico.md`, §4.2.

Tras estos cambios no encuentro otra omisión constitutiva ni otra afirmación que exceda las fuentes. Las decisiones de representación, motores y almacenamiento siguen correspondiendo al spec: no necesitan enumerarse en esta página para conservar su vigencia.

APROBADO (ronda 3b)
