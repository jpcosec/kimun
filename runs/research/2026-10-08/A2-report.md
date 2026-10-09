# A2 — field-add neutral para campos opcionales; `:description` obligatoria por campo

Worktree `/home/jp/proyectos/kimun-worktrees/A2`, rama `close/A2`. Reanudado desde el
trabajo parcial existente (diff sin commitear); se continuó sin descartar nada.

## Qué cambió y por qué

### (a) `models field-add` de un campo opcional ya no reasigna el último hueco

Decisión: se arregla en **el extractor**, no recolocando el slot en `field-add`.

- `src/kimun/surface/models/extract.cljc`: nuevo `section-boundary`. Al calcular el final
  del cuerpo de una sección ya no cuenta como frontera una receta posterior que captura
  cuerpo de sección si su heading/ancla no aparece en el documento. Un `optrev` cuyo
  heading está ausente queda **ausente** y deja de capturar el bloque de la sección
  anterior (docs/v2/07 §4). Antes, `## summary_en` ausente re-bindaba el último bloque y
  `body` quedaba vacío con el texto en `summary_en`.
- Por qué el extractor y no `field-add`: recolocar el slot solo arregla documentos nuevos;
  los documentos ya renderizados bajo el contrato viejo seguirían mal-extraídos. Además
  haría irrepresentable un template cuya última sección es opcional. El extractor ataca la
  causa: la semántica de "ausente" de `optrev`.
- `test/kimun/parity/known_gaps_test.cljc`: se eliminó la brecha fijada
  `field-add-of-an-optional-field-rebinds-the-last-slot-of-existing-documents` (y el
  `def ticket` muerto que la acompañaba).
- `test/kimun/parity/closed_gaps_test.cljc`: reescrita como promesa, dos casos:
  `field-add-of-an-optional-field-is-neutral-for-existing-documents` (opcional: payload
  intacto; un doc que sí usa el campo lo conserva) y
  `field-add-of-a-required-field-with-default-is-neutral-for-existing-fields` (requerido
  con default: el default entra en el campo nuevo, los viejos no se tocan).

### (b) `:description` no vacía obligatoria por campo

- `src/kimun/surface/models/descriptor.cljc`: `validate!` exige `:description` string no
  vacía en cada campo; lanza `:models/invalid` con mensaje
  `"fields must define a non-empty description: <todos los campos, en orden de declaración>"`.
- `src/kimun/cli/commands/models.cljc`: `models field-add` exige `--description TEXT`
  (falta ⇒ `:cli/usage`, exit 6) y la guarda tal cual en el campo.
- `src/kimun/surface/http/routes.cljc`: `POST /models/fields-add` requiere `description`.
- Fixtures y descriptores de test actualizados (no se debilitó la regla): ~23 archivos de
  test, más los descriptores inline; los ficheros `.edn` de fixture ya tenían descripción.
- `test/kimun/parity/known_gaps_test.cljc`: eliminada la brecha
  `descriptor-accepts-a-field-without-description`.
- `test/kimun/parity/closed_gaps_test.cljc`: reescrita como promesa
  `descriptor-requires-a-non-empty-description-per-field` (sin descripción, vacía, en
  blanco, lista de todos los campos) más `field-add-requires-a-description` (CLI, exit 6 y
  sin definir versión).
- `docs/v2/tests/parity-gaps.edn`: eliminada la entrada
  `test_standalone.py::test_structured_nl_doc_requires_field_descriptions`.
- Docs: `07` (descripción obligatoria, nueva semántica de `optrev`, synopsis de
  `field-add`), `06` (opción `--description`), `12` (quitados los pendientes 2 y 4),
  `parity-v1.md` (brechas 3→2, texto), `05-estado.md` (516 portadas, 2 brechas, suite).

## Pruebas (evidencia en `runs/subagents/A2/`)

`bb lint`:

```
rings and docstrings ok: 96 files
exit 0
```

`bb test` (con `memcap`):

```
Ran 107 namespaces in 181 s
{:test 877, :pass 9458, :fail 0, :error 0, :namespaces 107}
exit 0
```

Tests añadidos: 3 deftests en `test/kimun/parity/closed_gaps_test.cljc`
(neutralidad opcional, neutralidad requerido-con-default, CLI exige `--description`) y 3
aserciones de descripción en `test/kimun/surface/models/store_test.cljc`.

`bb oracle`: no ejecutado; no se tocó `src/kimun/kernel`.

## Paridad (coverage.py)

Comando pedido, salida en `runs/subagents/A2/coverage.txt`:

```
TOTAL  590  516  1  <oráculo>  55  2  0  0  16
strict: FAIL - 14 blocking, 14 errors, 2 unclassified under allowed prefixes, 2 gaps allowed
exit 1
```

- **Una brecha menos: confirmado** (3 → 2; portadas 515 → 516). Es lo que pide el brief.
- **El exit NO es 0**, pero es ajeno a A2: los 14 `errors` son entradas de
  `parity-oracle.edn` que coverage.py no puede casar porque `oracle_names` sigue leyendo
  `test/fixtures/parity/{engine,segments}/*/meta.edn`, y esos `meta.edn` los eliminó el
  empaquetado del corpus en `engine.cases.jsonl.gz` / `segments.cases.jsonl.gz`
  (commit `a283949f`, ya en HEAD). También baja `oráculo` de 14 a 0 y sube `abiertas`/`open`
  a 16. Reproducido idéntico sobre un export limpio de `HEAD` (mismos 14 errores, exit 1,
  solo cambiaba `gaps 3 → 2`). No lo toqué: actualizar `oracle_names` al corpus empaquetado
  cambiaría el marcador de paridad y las cifras que poseen `05-estado.md` /
  `tests/parity-v1.md`, fuera del alcance de A2 y potencialmente en conflicto con otra lane.

## Notas

- Se respetó la regla de memoria: todo `bb`/script vía `memcap timeout 900 …`; el corpus y
  los escaneos de fixtures procesados por archivo, sin acumular árboles.
- Durante la sesión hubo dos fallos de `bb test` por `/tmp` lleno (`No space left on
  device`, y luego `FileSystemException`/`NoSuchFileException` en `cli/adapters_test`) por
  corridas concurrentes de otras lanes. Se liberó espacio propio y el rerun dio 0 fallos.
- Commit único en `close/A2` con el trailer `Co-Authored-By: Claude Opus 5.5
  <noreply@anthropic.com>`; sin push.
