---
id: issue-sldb-table-marker
status: closed
tags:
- system:sldb
- topic:templates
- topic:feature
---

# SLDB: add `⸢rev,table•⸥` marker for structured table fields

## Problem

SLDB templates soportan `⸢rev•field⸥` (scalar) y `⸢rev,list•field⸥` (lista), pero no hay un marcador para **tablas**. Campos `list[dict]` o `dict[str, str]` no tienen representación directa en el template, lo que fuerza a aplanarlos en texto o usar composiciones para algo que debería ser directo.

## What's needed

Un marcador `⸢rev,table•fieldname⸥` o `⸢rev,table[col1,col2]•fieldname⸥` que:

- **Renderee** un `list[dict]` como tabla markdown:
  ```markdown
  | col1   | col2   |
  |--------|--------|
  | val1   | val2   |
  | val3   | val4   |
  ```

- **Extraiga** una tabla markdown de vuelta a `list[dict]`, con las cabeceras como keys

- **Haga roundtrip** fiel (render → extraer → mismo valor)

## Files to read first

- `src/sldb/core/template_extractor.py` — cómo se detectan los marcadores `⸢rev•⸥` / `⸢rev,list•⸥` en el template
- `src/sldb/core/data_extractor.py` — cómo se extraen valores del documento contra los recipes
- `src/sldb/core/renderer_engine/yaml.py` — cómo se renderiza frontmatter (referencia de patrón)
- `src/sldb/runtime/validation.py` — orquestación de extract/render
- Tests existentes para `⸢rev,list•⸥` como referencia de patrón de testing

## Design considerations

- **Formato de tabla**: pipes estándar markdown (GFM) con cabecera separada por `|---|`
- **Columna explícita vs implícita**: ¿las columnas se definen en el template (`table[col1,col2]`) o se infieren del primer item?
- **Celdas vacías**: soportar `|  | valor |`
- **Multilínea**: ¿cómo se comporta si un valor contiene `\n`? (no aplica en GFM tables)
- **Anidación**: no soportar por ahora — el valor de cada celda es string plano
- **Alineación**: `:---`, `:---:`, `---:` — ¿se preservan?

## Suggested syntax

```python
__template__ = """
⸢rev,table[name,status,goal]•tasks⸥
"""
```

Donde `name,status,goal` son las keys del dict que se renderizan como columnas. Si se omite la lista de columnas, se infieren del primer item.

## Task list

- [ ] Revisar `template_extractor.py` y entender el registro de nuevos tipos de marcador
- [ ] Revisar `data_extractor.py` para entender parseo inverso
- [ ] Implementar handler de tabla en SLDB (render + extract)
- [ ] Agregar tests de roundtrip
- [ ] Agregar tests de edge cases (celdas vacías, tablas sin datos)

## Pills

- `desk/contexts/pill-sldb-template-markers.md` — cómo funcionan los marcadores `⸢rev•⸥` en SLDB

## Resolution

Implemented as `⸢rev,table[...]•field⸥` and `⸢rev,table•field⸥`, with Markdown table render/extract roundtrip coverage for explicit columns, inferred columns, and empty cells.
