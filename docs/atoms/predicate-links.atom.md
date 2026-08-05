# Predicate Links

Links con semántica relacional explícita usando `[predicate:: [[target]]]`.

## Sintaxis

```markdown
[implements:: [[spec-001]]]
[grounded_by:: [[research-paper]]]
[depends_on:: [[service-api]]]
```

- `predicate` identifica la relación.
- `target` identifica el documento relacionado.
- Cada store mantiene su propio vocabulario de predicates.

## Registro del store

Las definiciones viven en `.sldb/core/store_index.yaml`:

```yaml
predicates:
  - name: implements
    axis: HOW
    description: Identifies what the source implements.
```

Los predicates iniciales son editables y removibles. Los predicates no registrados se recuperan con eje `CUSTOM`.

## CLI

```bash
sldb predicates add depends_on --axis DEPENDENCY --description "Declares a dependency." --store .sldb
sldb predicates list --store .sldb
sldb predicates show depends_on --store .sldb
sldb predicates validate --store .sldb
sldb predicates remove depends_on --store .sldb
```

`docs recover` consulta el registro del store:

```bash
sldb docs recover doc.md --store .sldb --format json
```

Salida relevante:

```json
{
  "target": "service-api",
  "kind": "predicate_link",
  "predicate": "depends_on",
  "w5h1_type": "DEPENDENCY"
}
```

## Predicates iniciales

| Predicate | Eje |
|---|---|
| `is_solved_by` | `HOW` |
| `implements` | `HOW` |
| `mathematically_proves` | `WHY` |
| `explains_failure_of` | `WHY` |
| `grounded_by` | `PROVENANCE` |
| `restricts` | `WHEN_WHERE` |
| `defines` | `WHAT` |
