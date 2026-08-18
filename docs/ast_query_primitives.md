# SLDB AST Query Primitives

## Context
SLDB serves as the physical storage and runtime structure representation of documents. While `KGDB` (Knowledge Graph Database) operates on high-level semantics and inference, SLDB must provide lower-level **structural** query primitives based on the Markdown AST.

## Structural Query Primitives

### 1. Block Addressing (`get_block(address)`)
- **Purpose:** Retrieve a specific AST node or text block using its physical address.
- **Example Use:** Fetching `doc.RoadmapDoc.plan.field.tasks[2]` directly from the AST.
- **Scope:** Document-local. It relies entirely on the parsed representation of the file.

### 2. Section Lookup (`get_section(doc, heading_slug)`)
- **Purpose:** Locate a specific section by its canonical heading slug.
- **Behavior:** Returns a list of all AST nodes contained under that heading, until a heading of equal or higher precedence is encountered.
- **Scope:** Document-local.

### 3. Field Ownership (`get_owner_section(field_address)`)
- **Purpose:** Determine which section structurally encloses a given field.
- **Behavior:** Walks up the AST tree from a field node to the nearest parent heading node.
- **Scope:** Document-local structural introspection.

### 4. Document-Local Structural Search (`find_blocks(doc, query)`)
- **Purpose:** Find AST blocks within a document matching specific structural patterns (e.g., "All list items that contain a checkbox").
- **Scope:** Operates purely on the Markdown tokens and their properties, not on the semantic meaning.

## Distinction from KGDB
- **SLDB Primitives:** Focus strictly on *how* the document is laid out (Headings, Lists, Tables, Reversible markers). They do not understand the domain logic or cross-document semantic properties.
- **KGDB Primitives:** Focus on *what* the document means (Entities, Relationships, Inference). KGDB queries might translate down to multiple SLDB AST lookups under the hood, but SLDB remains ignorant of the broader graph.
