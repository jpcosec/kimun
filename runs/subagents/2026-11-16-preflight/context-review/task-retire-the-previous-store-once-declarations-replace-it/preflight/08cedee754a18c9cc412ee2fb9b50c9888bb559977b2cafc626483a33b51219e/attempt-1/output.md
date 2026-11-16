## REFORMULACION

**Propósito:** retirar el almacén anterior una vez que las declaraciones lo sustituyan, según las etapas 1-2 del plan aprobado.

**Resultado:** eliminar el árbol de rutas, la huella por archivo, las revisiones, el log, los planes, el replay y el registro de identidad de include, junto con sus tests; también las cachés explícitas y comprimidas; y declarar `add` mediante rewrite-clj.

**Repo:** `/home/jp/proyectos/kimun`, workspace `desk`.

**Límites:** no eliminar nada antes de que su reemplazo funcione; después, retirarlo por completo. Mantener la suite completa y “pod” en verde tras cada eliminación.

## AMBIGUEDADES

1. “only when its replacement runs” / “Nothing is deleted before its replacement runs”: no se define qué evidencia acredita que el reemplazo “runs”. Obligaría a inventar el criterio, el comando y quién lo certifica.
2. “Also the explicit and compressed caches”: no se identifican rutas, nombres ni si deben borrarse o desactivarse. Obligaría a inventar el inventario de cachés.
3. “declare add through rewrite-clj”: no queda claro si es un cambio de implementación, una dependencia, una API o una anotación. Obligaría a inventar el alcance técnico.
4. “include's identity-registry”: la expresión es ambigua; no se sabe si “include” es un componente, una función o un archivo. Obligaría a inventar el objeto exacto.
5. “then it goes whole” frente a “after each deletion”: no se define si la retirada es atómica o incremental. Obligaría a inventar orden y granularidad.
6. “Full suite with pod green”: “pod” no está definido ni se indica el comando de suite. Obligaría a inventar la validación.

## DRY-RUN

**Lecturas:** TaskDoc, `docs/v2/drafts/plan-producto.md` (etapas 1-2), código y tests de los subsistemas enumerados, configuración de cachés y build/test.

**Cambios previstos:** borrar los subsistemas y tests indicados; retirar cachés; introducir la declaración de `add` con rewrite-clj.

**Dependencias:** reemplazo operativo y verificado; rewrite-clj disponible; autorización de retirada.

**Validaciones futuras:** suite completa y “pod” verdes tras cada borrado; ausencia de referencias residuales.

## FUTURO

Otro nodo o un humano debe aportar evidencia de que el reemplazo funciona, definir “pod” y el comando de suite, y autorizar el inicio de las eliminaciones.
