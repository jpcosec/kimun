---
id: field-descriptions-are-required-contract
title: Field descriptions are required contract
five_wh_one_plus: what
tags:
- system:sldb
- layer:document-model
- topic:fields
provenance: /home/jp/proyectos/hum-ecosystem/tools/sldb/README.md
---

# Field descriptions are required contract

## Answer

Every `StructuredNLDoc` field must have a non-empty Pydantic `description`, and that description is part of the public model contract.

## Supporting points

- The docs treat descriptions as contract, not decoration.
- Descriptions support both human understanding and model-guided tooling.
- This requirement is part of the expected behavior of v1 models.
## Related atoms

### Depends on

- [depends_on:: [[schema-binding]]]

### Supports

- [supports:: [[structurednldoc-contract]]]
- [supports:: [[type-contract]]]

### 5WH1+ neighborhood

- This atom is typed by its `five_wh_one_plus` field and should be queried together with nearby `what`/`how`/`when`/`where` atoms rather than as an isolated note.
