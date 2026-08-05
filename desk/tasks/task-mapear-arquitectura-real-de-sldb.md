---
id: task-mapear-arquitectura-real-de-sldb
status: draft
summary: ''
tags:
- workspace:desk
- artifact:task
routine: routine-task-mapear-arquitectura-real-de-sldb
current_node: checklist-task-mapear-arquitectura-real-de-sldb-execution-ready
history: []
references: []
depends_on: []
pills: []
files: []
checklists:
- checklist-task-mapear-arquitectura-real-de-sldb-execution-ready
- checklist-task-mapear-arquitectura-real-de-sldb-testing-ready
- checklist-task-mapear-arquitectura-real-de-sldb-closeout-ready
---

# Mapear arquitectura real de SLDB

## Rationale

_Explain why this task exists or the business driver behind it._

SLDB creció de forma orgánica y hoy no tenemos una visión operativa confiable de sus módulos, acoplamientos y ciclos.

## Goal

_Describe the concrete result this task must produce._

Producir un relevamiento estructural del sistema actual usando AST, dependencias, hubs y flujos reales.

## Scope

_State what is in scope and what is out of scope._

Inventario modular de src/, ciclos de import, hotspots, superficies CLI, subsistemas reales y mapa inicial para spec2viz. Sin refactor todavía.

## Implementation Path

_Outline the expected implementation route or affected surface._

Analizar src/ con AST y dependencias; volcar hallazgos en drawer docs y superficies de contexto; preparar specs de visualización del estado actual.

## Validation

_List the checks required before this task can close._

- python -m compileall src

## Done When

_Name the observable condition that makes the task complete._
