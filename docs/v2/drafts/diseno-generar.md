# Generar: materializar desde las proposiciones

Borrador de Claude, 2026-11-16. Sigue a `diseno-evaluar.md` y abre la etapa 5 de `plan-producto.md`.
Entre «» va el dueño (`sesion-2026-11-15.md`); con número, `00-nucleo.md`. Lo demás son elecciones
mías. Nada está probado.

## 1. La idea

«Para poder "ver el conocimiento" hay que materializarlo en otro lado.» «TODOS los documentos que
genera SLDB […] pasarían a ser MATERIALIZACIONES DESDE MODELOS.»

Un documento generado no es una fuente: es una vista de las proposiciones, escrita en un archivo.
Se puede borrar y volver a generar. Si alguien lo edita a mano, kimun lo dice; no lo absorbe.

## 2. Una receta es una función

Las declaraciones son Clojure que corre (**7.1**). Entonces la receta de un documento es una función
de Clojure, escrita en el mismo archivo de declaración, que pregunta y devuelve texto:

```clojure
(ns proyecto.vistas (:require [proyecto.base :refer [isa]]))

(defn decisiones [ask]
  (str "# Decisiones\n\n"
       (apply str (for [p ((:about ask) 'decision)]
                    (str "- " (:term (:other p)) "\n")))))

(isa (generated "docs/decisiones.md" decisiones) doc.indice)
```

- **`(generated ruta receta)`** es vocabulario, como `at` y `files`. Da un nodo `{:generated {:path
  ruta}}` que entra en proposiciones como cualquier otro (el archivo generado se puede etiquetar,
  relacionar, anclar) y deja registrada la receta de esa ruta. Una ruta, una receta.
- **La receta recibe `ask`**: un mapa de funciones para preguntar a las proposiciones del proyecto
  (`:about`, `:about-path`, `:members`, `:terms`, las de `diseno-evaluar.md §9`) y `:text`, que da el
  texto actual de un ancla `(at …)`. Es lo único que la receta puede ver: sigue aislada del disco.
- **Devuelve una cadena.** No hay lenguaje de plantillas aparte: `str`, `for`, funciones. Las
  plantillas con marcadores de SLDB se pueden ofrecer después como una función más.
- **Muchos documentos de una clase** se declaran con código: un `doseq` que emite un `(generated …)`
  por miembro. La clase comprime también lo que se genera.

Evaluar las declaraciones no corre ninguna receta: solo las registra.

## 3. Comandos

- `declare generate` — corre las recetas y escribe cada archivo (escritura atómica). Solo escribe
  rutas declaradas con `generated`, dentro del proyecto. Dice qué creó, qué cambió y qué quedó igual.
- `declare generate --check` — no escribe: dice qué archivos generados difieren de lo que su receta
  daría hoy (cambiaron las fuentes, o alguien los editó).
- Dos corridas seguidas dan los mismos bytes.

## 4. Lo que no está pensado

- El docstring tipado como materialización de una clase.
- Generar hacia algo que no es un archivo (una respuesta de la API, una vista).
- Qué hacer con un archivo generado cuya declaración desapareció (hoy: nada; nada se borra solo).
- El porqué, quién y cuándo, igual que en `diseno-evaluar.md §6`.
