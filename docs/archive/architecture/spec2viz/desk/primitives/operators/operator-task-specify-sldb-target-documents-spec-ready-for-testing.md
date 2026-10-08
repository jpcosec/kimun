> **Archivado 2026-10-08.** Artefacto del sub-desk pre-v2 de spec2viz (deskops). Superado por `AGENTS.md` y por el desk de kimun (`.kimun`).

---
# operator-xxx
id: operator-task-specify-sldb-target-documents-spec-ready-for-testing
# active | archived
status: active
# Atomic runtime action, e.g., set_field, append_list
action: set_field
# Payload path modified by the operator
target: status
# Value used by the operator action
value: ready_for_testing
# e.g., system:deskops
tags:
- primitive:operator
---

# Mark ready for testing

## Summary

_Summarize the state transition this operator performs._

Moves the task into the testing gate.
