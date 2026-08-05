---
id: task-diseñar-estrategia-de-extracción-y-modularización
status: draft
summary: ''
tags:
- workspace:desk
- artifact:task
routine: routine-task-diseñar-estrategia-de-extracción-y-modularización
current_node: checklist-task-diseñar-estrategia-de-extracción-y-modularización-execution-ready
history: []
references: []
depends_on:
- task-definir-arquitectura-objetivo-modular-de-sldb
pills: []
files: []
checklists:
- checklist-task-diseñar-estrategia-de-extracción-y-modularización-execution-ready
- checklist-task-diseñar-estrategia-de-extracción-y-modularización-testing-ready
- checklist-task-diseñar-estrategia-de-extracción-y-modularización-closeout-ready
---

# Diseñar estrategia de extracción y modularización

## Rationale

_Explain why this task exists or the business driver behind it._

La refactorización profunda requiere cortes seguros, especialmente en store y otras zonas que ya parecen subsistemas.

## Goal

_Describe the concrete result this task must produce._

Definir slices de migración, paquetes candidatos y estrategia para sacar legacy del árbol activo y dejarlo sólo en git.

## Scope

_State what is in scope and what is out of scope._

Plan de migración por slices, aislamiento de compatibilidad temporal, extracción potencial de store, y criterios para dividir query/semantic y CLI.

## Implementation Path

_Outline the expected implementation route or affected surface._

Usar la arquitectura objetivo para proponer un plan incremental con dependencias, riesgos y orden de ejecución.

## Validation

_List the checks required before this task can close._

- python -m compileall src

## Done When

_Name the observable condition that makes the task complete._
