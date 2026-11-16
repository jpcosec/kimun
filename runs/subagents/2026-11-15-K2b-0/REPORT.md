# K2b-0 — medir e inventariar (sin cambiar `src/`)

Nota del coordinador: el ejecutor no pudo escribir este archivo; su informe llegó por mensaje
y se resume aquí. Los entregables completos son `readers.md`, `measurement.md`, `changes.md` y
los datos crudos de este directorio.

## Medición (store sintético, 500 documentos v1 de un modelo, un `include build`, formato 4)

- Objetos: 1.999.824 B.
- Los cuatro mmap (`:trees`, `:edges`, `:incidence`, `:heads-root`): 633.685 B (31,7 %).
- Las tres aristas por documento: 617.500 B (30,9 %) — `reference`→clase 136.000 B,
  `derived`→clase 146.500 B, `reference`→archivo 335.000 B.
- Atribuible a las tres aristas (aristas + `:edges` + `:incidence`): 1.084.862 B (54,2 %).
- Con un commit por documento (501 revisiones) los mmap de la Revisión pesan 8,65 MB frente a
  unos 0,57 MB vivos: la historia multiplica el mmap, no las aristas.
- Frente a `14 §20` (desk de 539 documentos, 13 modelos; comparación no controlada): el mmap
  sigue en ≈1,3 KB por documento; las aristas crecieron 36 % por documento; el descriptor se
  duplicó por `:units`.

## Inventario de lectores

- `derived`→clase solo la lee `describe`, para decidir `:state`.
- `reference`→clase es la única copia de «clase vigente» tras una sucesión de modelo.
- `reference`→archivo es la única copia de `:parse-contract` y `:ancestors`.
- `:incidence` tiene un lector (`edges-touching`) y se deriva de `:edges`; `:edges` y `:trees`
  son copias únicas.

## Consecuencias de cada retiro

- Retirar aristas a nivel de superficie no cambia `:format-version` ni la forma de la
  Revisión; cambia los ids de las revisiones nuevas.
- Retirar `:edges`, `:incidence` o `:trees` cambia la forma de la Revisión (formato nuevo y
  despacho por versión para reproducir logs viejos).
- `bb oracle` no se mueve en ningún caso: solo cubre ids de nodo.

## No medido

N=2000; un store real; clases spec 16, genéricos y rutas con directorios; tiempos de lectura
con y sin cada estructura. La causa del aumento de las aristas (el `:parse-contract` de K6)
es inferencia del ejecutor.

## Para el dueño (Q4, sin responder)

El spec no dice a cuál de los cuatro mmap se refiere «el índice mmap por ids internos». No se
halló en `src/` ninguna dependencia técnica de K2b con K1 (ni ids de nodo ni de unidad en
estas aristas o en los mmap); es inferencia, sin probar.
