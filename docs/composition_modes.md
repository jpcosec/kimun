# SLDB Composition Modes

## Current Capabilities
SLDB currently supports `__compositions__` via `render-time child summarization` (transclusion of child models defined by a list of links). This needs to be expanded.

## Proposed Composition Modes

### 1. Simple Transclusion (Link Resolution)
- **Mechanism:** A field value contains a canonical link (e.g. `[Some Doc](path/to/doc.md)`). During composition, the link is followed, and the entire content (or a specific field/section) of the target document replaces the link.
- **Use Case:** Reusing an identical paragraph across multiple files.

### 2. Summary Composition (Current)
- **Mechanism:** Render-time extraction of specific fields from a collection of linked documents, formatted via a `line_template`.
- **Use Case:** Aggregating task statuses into a roadmap list.

### 3. Sectional Composition
- **Mechanism:** Embedding a specific section (`doc.MyDoc.sec.my-section`) directly into the flow of the parent document.
- **Use Case:** Building a master report out of executive summaries from individual project documents.
- **Syntax Idea:** `![[path/to/doc.md#my-section]]` or via explicit `__compositions__` rules binding a `List[str]` to a target section.

### 4. Query-Driven Semantic Composition
- **Mechanism:** Instead of hardcoded links in the source document, the template contains a query (e.g. `query: type="task" AND status="open"`). The composition engine dynamically resolves the query at render time and builds the output.
- **Use Case:** "Show me all open bugs related to X".
- **Safety:** Must be carefully isolated. The resulting text must either be strictly read-only or reversible if edited in the parent document.

## Preserving Locality and Readability
- All compositions must output valid, human-readable Markdown.
- Nested compositions should properly indent or blockquote to maintain hierarchy.
- When an edit occurs in the composed output, SLDB must trace back the edit to the *origin* document (via the Addressability Model) rather than modifying the parent document's raw template or breaking the composition rule.
