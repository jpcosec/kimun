---
id: task-ejecutar-refactor-base-del-núcleo-sldb
status: draft
summary: ''
tags:
- workspace:desk
- artifact:task
routine: routine-task-ejecutar-refactor-base-del-núcleo-sldb
current_node: checklist-task-ejecutar-refactor-base-del-núcleo-sldb-execution-ready
history: []
references: []
depends_on:
- task-disenar-estrategia-de-extraccion-y-modularizacion
pills: []
files: []
checklists:
- checklist-task-ejecutar-refactor-base-del-núcleo-sldb-execution-ready
- checklist-task-ejecutar-refactor-base-del-núcleo-sldb-testing-ready
- checklist-task-ejecutar-refactor-base-del-núcleo-sldb-closeout-ready
---

# Ejecutar refactor base del núcleo SLDB

## Rationale

_Explain why this task exists or the business driver behind it._

Una vez fijada la dirección necesitamos empezar a mover el sistema hacia fronteras sanas y componentes pequeños.

## Goal

_Describe the concrete result this task must produce._

Implementar los primeros cortes estructurales: romper ciclos, extraer servicios de dominio y adelgazar el CLI.

## Scope

_State what is in scope and what is out of scope._

Romper ciclos de import, sacar lógica de dominio fuera de cli, reorganizar módulos y preparar separación de subsistemas. Sin reintroducir legacy en runtime.

## Implementation Path

_Outline the expected implementation route or affected surface._

Aplicar el plan de modularización en slices pequeños con validación continua y preservando el historial en git.

## Validation

_List the checks required before this task can close._

- pytest

## Done When

_Name the observable condition that makes the task complete._
