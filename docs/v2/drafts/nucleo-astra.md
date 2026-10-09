# Núcleo de kimun

Borrador independiente de Astra · ronda 1 · 2026-10-09

**1** Kimun es un motor AST-first para tratar texto, código y otros archivos como conocimiento.

**1.1** Su objeto es el lenguaje estructurado a cualquier escala; el documento es una de sus formas de organización.

**1.2** El significado se ancla en la estructura encontrada. Reconocer conocimiento no exige someter las fuentes a una plantilla previa.

**1.3** Clojure es su lenguaje constitutivo, por decisión del dueño.

**2** El conocimiento es una colección de proposiciones `(R A B)` en contexto. Todo significado depende de un contexto.

**2.1** Relaciones y términos tienen un lugar en jerarquías; las propiedades de una relación dependen de su uso contextual.

**2.11** Un signo adquiere significado por su uso. Igualar cadenas no basta para identificar símbolos; cambiar de contexto exige una correspondencia justificada.

**2.2** Un símbolo nace en el contexto de su documento; extender su alcance o declarar equivalencias es curaduría explícita.

**2.3** **D0 — abierta:** cómo se adopta la identidad por lugar en el conjunto de kimun y qué relación conserva con la identidad por contenido.

**3** El conocimiento debe poder evaluarse y responder por su justificación dentro del mundo al que pertenece.

**3.1** Las fuentes y las instrucciones fijan los principios de ese mundo. Una respuesta debe mostrar qué la sostiene y cómo se sigue de ello.

**3.11** Registrar una afirmación humana o producirla mediante un modelo no basta para establecerla. La semejanza y la coocurrencia tampoco sustituyen una proposición justificada.

**3.2** Sentido y verdad son cuestiones distintas. **D-G — abierta:** si «anclado/derivado» constituye el eje de verdad o expresa soporte junto a un valor operacional de verdad.

**4** Un modelo reconoce una forma estable de conocimiento sobre la estructura.

**4.1** Todo documento es instancia de una clase. Las diferencias se expresan mediante especialización; cambiar una clase no redefine silenciosamente sus instancias anteriores.

**4.2** Reconocer la estructura y seleccionar lo que alimenta el conocimiento son actos distintos. No todo lo expresado necesita convertirse en conocimiento registrado.

**4.3** Leer y generar tienen modelos propios: una misma comprensión admite distintas expresiones, sin prometer recuperar una expresión única.

**5** La autoridad de cada cosa corresponde a su origen.

**5.1** El archivo fuente responde por lo que contiene; el conocimiento que existe por sí mismo en el grafo responde desde allí. Una expresión generada depende de sus datos y de su modelo de generación.

**5.2** Una representación reconstruible no adquiere autoridad independiente. La trazabilidad debe conservar la diferencia entre fuente, interpretación y consecuencia.

**6** Kimun mantiene explícita la relación entre el conocimiento y el ambiente que lo sostiene.

**6.1** Un cambio o una desaparición del soporte exige revisar lo que depende de él y declarar la pérdida de sustento; no autoriza a conservarlo como si nada hubiera ocurrido.

**6.2** Una contradicción dentro de un contexto exige desambiguación o corrección explícita. Ningún conflicto se resuelve en silencio.
