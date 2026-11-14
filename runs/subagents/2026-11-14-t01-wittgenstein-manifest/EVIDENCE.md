# T01 — manifest de inclusión Wittgenstein W1/W2

El manifiesto enumera C01–C28 con disposición, estado actual en kimun,
dependencias, fixture y control negativo. Se documentan las exclusiones y
diferimientos transversales. README, FAQ, estado y backlog enlazan el contrato
sin presentar el hash del CAS como identidad de nodo (D0 sigue abierto).

## Validación

- `light-checks.log`: `bb lint` (103 archivos), `bb oracle` (9/9),
  `bin/kimun include check` (`ok: true`) y prueba dirigida del manifiesto
  (4 tests, 742 aserciones), todos en verde.
- `full-test.txt`: `~/bin/memcap 3 bb test` sobre `main` con K3u integrado:
  119 namespaces, 971 tests, 10.888 aserciones, 0 fallos, 0 errores,
  exit 0.

El manifiesto es un contrato de alcance. No implementa el motor G ni resuelve
la identidad y continuidad positiva pendiente en D0.
