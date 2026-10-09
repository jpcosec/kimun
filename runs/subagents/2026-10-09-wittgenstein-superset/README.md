# Evidencia — revisión Kimun/Wittgenstein

Fecha: 2026-10-09. Entregable: [reviews y proposals](../../../docs/reviews/wittgenstein-superset/README.md). No se modificó implementación ni fuentes de Wittgenstein; se agregaron documentos y scripts de diagnóstico reproducibles a este run.

## Baseline

| validación | resultado | evidencia |
|---|---|---|
| `bb test` (incluye lint) | 889 tests, 9.580 aserciones, 109 namespaces, 0 fail/error; 152 s | [kimun-test.log](kimun-test.log) |
| `bb oracle` | Canonical bytes independientes: 9/9 | [kimun-oracle.log](kimun-oracle.log) |
| W1 pipeline `pytest tests` | 132 passed, 1 skipped; 24,33 s | [wittgenstein-pipeline-test.log](wittgenstein-pipeline-test.log) |
| W1 engine `pytest tests` desde `matrix/` | 179 passed; 1,74 s | [wittgenstein-engine-test.log](wittgenstein-engine-test.log) |
| W2 ocho módulos seleccionados | 64 passed; 0,27 s | [wittgenstein-secondary-test.log](wittgenstein-secondary-test.log) |
| Probes Kimun | 3 casos emitidos, exit 0 | [script](probe_kimun.clj), [salida](probe-kimun.log) |
| Probes W1 | 4 casos emitidos, exit 0 | [script](probe_wittgenstein.py), [salida](probe-wittgenstein.log) |
| Artefactos | Referencias locales, IDs de hallazgos y snapshots de fuentes | [script](audit_artifacts.py), [salida](artifact-validation.log), [inventario](source-inventory.json) |
| Include tras cambios concurrentes | 7 tests / 40 aserciones; include check limpio | [tests](kimun-include-final-test.log), [check](kimun-include-check.log) |

Resumen de closeout: [validation.log](validation.log). Snapshot final de lectura antes del commit: Kimun `3d090411`; inicio de lectura: `89b0300d`. La base `src` no cambió entre ambos, y se verificó el único namespace de tests modificado (include). El nuevo AGENTS exige memcap; las comprobaciones finales se ejecutaron con ese wrapper.

El test skipped de W1 no se considera validación aprobada de su capacidad: la invocación `-q` no imprimió el motivo. No se repitieron suites solo para obtener ese detalle. El script de auditoría guarda hashes del árbol local: las capacidades extra de W2 no deben atribuirse automáticamente al commit HEAD si los archivos estaban modificados/no trackeados.

## Reproducción

Desde Kimun:

```bash
/home/jp/bin/memcap 3 bb test
/home/jp/bin/memcap 3 bb oracle
/home/jp/bin/memcap 3 bb runs/subagents/2026-10-09-wittgenstein-superset/probe_kimun.clj
PYTHONDONTWRITEBYTECODE=1 /home/jp/bin/memcap 3 python3 runs/subagents/2026-10-09-wittgenstein-superset/probe_wittgenstein.py
/home/jp/bin/memcap 3 python3 runs/subagents/2026-10-09-wittgenstein-superset/audit_artifacts.py
```

W1 pipeline, desde `/home/jp/proyectos/wittgenstein/matrix-shrdlu-spec`:

```bash
PYTHONDONTWRITEBYTECODE=1 OPENBLAS_NUM_THREADS=1 PYTHONPATH=/home/jp/proyectos/wittgenstein/matrix-shrdlu-spec/matrix bash scripts/capped.sh 4 -- python3 -m pytest -q -p no:cacheprovider tests
```

W1 engine, desde su subdirectorio `matrix/`:

```bash
PYTHONDONTWRITEBYTECODE=1 OPENBLAS_NUM_THREADS=1 bash ../scripts/capped.sh 4 -- python3 -m pytest -q -p no:cacheprovider tests
```

W2, desde `/home/jp/proyectos/wittgenstein/Matrix/Matrix`:

```bash
PYTHONDONTWRITEBYTECODE=1 OPENBLAS_NUM_THREADS=1 bash /home/jp/proyectos/wittgenstein/matrix-shrdlu-spec/scripts/capped.sh 4 -- python3 -m pytest -q -p no:cacheprovider tests/test_middle_term.py tests/test_reflection.py tests/test_relation_algebra.py tests/test_shape_guard.py tests/test_rule_schema.py tests/test_partition.py tests/test_hypertensor.py tests/test_projection_emit.py
```

Python local: 3.13; pytest 8.3.4; NumPy 2.1.3; SciPy 1.15.3. No pip install, LLM, red ni bytecode/cache de pytest en fuentes externas. Los probes son demostraciones de comportamiento actual, no tests de aceptación del diseño propuesto.

## Intento fallido conservado

[wittgenstein-test.log](wittgenstein-test.log) es la primera invocación conjunta de W1 `tests matrix/tests`: exit 2, 38 errores de collection por colisión de paquetes llamados `tests`. Se corrigió la invocación siguiendo el README (suites separadas). No se cuenta como defecto funcional ni como validación de W1. La primera construcción del probe Python omitió tres argumentos de `Declaration`; se corrigió antes de guardar la salida final, sin cambiar código fuente externo.

## Closeout

El commit de esta tarea agrupa únicamente la carpeta de revisión y este run. Las validaciones respaldan el diagnóstico y la base actual; no acreditan implementación de las propuestas ni cierre del objetivo futuro de inclusión funcional.
