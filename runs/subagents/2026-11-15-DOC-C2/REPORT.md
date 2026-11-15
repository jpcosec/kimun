# DOC-C2 — retiro de C2 y limpieza documental (2026-11-15)

Solo documentos; `src/` y `test/` intactos. `bb lint` ok (106 archivos, `lint.txt`). Dirigidas
(`directed.txt`): doc14-contract, wittgenstein-manifest, spec16-models-contract, lint-test: 17 pruebas,
1237 aserciones, 0 fallos. **No ajusté ninguna prueba** (ninguna fijaba los textos cambiados).

**Decisión del dueño:** C2 se retira. Todo lo demás (redacción, motivo citado, qué otros textos corregir) es mío.

## Cambios
- `docs/v2/12-ecosistema-y-pendientes.md`
  - fila `C2` (§1, antes «cliente Python y rutas HTTP de graph_ui…», deps `K2a, P1`): ahora «retirada (2026-11-15, decisión del dueño)», conserva el texto original y el motivo (rutas retiradas según `parity-v1.md` y `06 §5`; cliente no existe ni está especificado, `06 §6`; kimun sin consumidores en código; `graph_ui` queda como consumidor de v1); deps `—`; mismas 4 columnas.
  - frente «Operación y vista»: quitado `C2/API` de las tareas, nota de que se retiró.
  - mismo frente: avances de la matriz de caché fría (47 de 49 lectores iguales con `cache/` borrada; difieren `journal verify` (falla `store/corrupt`, falta plan resuelto en `cache/plans`) y `journal show`; sin arreglos) y de F1 (warning `docs/file-not-written`, `stores health` `needs-human`, `stores update` no repara, reintentar `docs update NAME --set` converge; hallazgos H1–H3 siguen abiertos). Resumido de los REPORT de `2026-11-15-cold-cache` y `2026-11-15-F1-fault-injection`.
  - fila `M1`: añadido «M1b (2026-11-15): `kimun.surface.models.compile/template->spec16` compila el `:template` v1 a selector + proyección `:markdown/v1` como vista derivada (`runs/subagents/2026-11-15-M1b/`)»; el «Pendiente» pasa de «compilar el template, motor de selector…» a «motor de selector…».
- `python/README.md` (§Client library): quitada la referencia a `from kimun import Client` «en 06 §6»; ahora: no hay cliente ni está previsto (C2 retirada), no hay API especificada, usar CLI con `subprocess` + envelope JSON.
- `docs/v2/05-estado.md` §2: marcador de paridad 518/1/14/55/0/1 corpus/1 abierta (2026-10-08) -> 520 portadas, 1 mapeada, 14 oráculo, 55 n/a, 0 brechas, 0 mencionadas, 0 corpus, 0 abiertas (cerrado 2026-11-14), citando `tests/parity-v1.md`. Y §«No implementado»: nota de que el cliente Python se retiró.
- `docs/v2/06-superficies-y-cli.md §6`: la viñeta «Cliente Python y wheels» indica C2 retirada (el resto igual). §5 no afirma nada pendiente.
- `docs/v2/14-juego-del-lenguaje.md` (~l.735, plan «Etapa C»): C2 anotada como retirada.
- `docs/v2/08-distribucion.md` (4 sitios: tabla l.11, l.16, l.46, bloque «Pendiente» l.151): decían «cliente Python previsto/pendiente»; ahora «no hay cliente (C2 retirada)» y el bloque pasa a «Retirado». Corrección evidente por contradicción directa.

## Hallazgos sin corregir / a decisión
- `docs/archive/v2/tests/parity-serve-checklist.md` (archivo, no tocado): su cabecera dice «Pendientes reales: `/models/promote` y `/kgdb/snapshot`». Contradice el retiro; es de archivo.
- `/kgdb/snapshot` no se nombra como retirada en `parity-v1.md` (solo `/models/promote`, l.75); `06 §5` retira «la materialización de kgdb (`project`)». Mi motivo «rutas retiradas» para `/kgdb/snapshot` es inferencia de esos dos textos; `routes.cljc` no tiene ninguna de las dos (según `frentes-plan.md` §1.5). Convendría nombrarla en `parity-v1.md` o `06 §5` (no lo hice: fuera de alcance).
- `docs/reviews/wittgenstein-superset/reviews/02-findings.md:165` dice «cliente Python pendiente» (revisión histórica, no tocada).
- Otras «C2» en `06`, `10-escala.md`, `16` son el corte C2 de K2a / capacidad C2: no relacionadas, no tocadas.
- `desk/tasks/Board.md` no menciona C2. No revisé `docs/v2/12` más allá de las menciones de C2 (grep).
