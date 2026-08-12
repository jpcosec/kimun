---
kind: suggestion
sender_project: deskops
created_at: 2026-06-25T12:00:00
status: open
---

# Redesign primitives via AST, nested templates, and hooks

_Describe the incoming message with enough evidence to triage._

The current `deskops` workflow relies on an excessive number of external primitive files (`ConditionDoc`, `ChecklistDoc`, `EdgeDoc`) to define state transitions for a single task. This breaks the locality of behavior, making tasks fragmented and difficult to read as unified documents.

We need to evolve SLDB's templating and AST capabilities so these primitives can be collapsed into a single, cohesive Task Markdown file without losing programmatic type safety.

The proposed architectural shift entails four key improvements:

1. **Separation of AST and Template**: The framework must explicitly define a document as `(AST + Template) = Rendered Document`. This clean separation allows the system to semantically understand the Markdown structure (e.g., native task lists) rather than relying solely on flat regex string replacement.
2. **Executable Hooks in Markdown**: Specifically for `deskops` operators and conditions, we need the ability to embed executable hooks directly within the Markdown (e.g., via Markdown comments or codeblocks). These hooks must be capable of dynamically pointing to and invoking Python classes, external scripts, or modules that return a verifiable value (like the exit code of `pytest`).
3. **Decoupled AST and Field Instances**: To make markers more meaningful, we need the ability to instantiate AST representations and Pydantic fields as separate, interoperable instances. This allows SLDB to map specific AST blocks (like a Markdown checklist) to specific field structures (`list[ChecklistItem]`).
4. **Rich Compositional Types**: Leverage SLDB's `__compositions__` engine to parse these new, meaningful markers (e.g., `⸢rev,task_list•execution_checklist⸥`). This will allow us to define primitives as nested SLDB Pydantic classes rather than requiring separate Markdown files for every condition or edge.