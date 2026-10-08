# SLDB v1 external-behavior atom map

## Scope

Requested sources reviewed:
- `README.md`
- `docs/faq.md`
- CLI/behavior context implied by current docs, especially `docs/architecture/current-cli-tree.md`, `docs/README.md`, and `docs/workspaces.md`

This map is intentionally limited to **observable user-facing behavior** and **stable product contracts** that should survive a refactor. It excludes engine/internal architecture atoms unless the user-visible contract depends on them.

## High-value findings

1. **info — `README.md:24-59`, `docs/faq.md:5-16`, `docs/architecture/current-cli-tree.md:6-16`**  
   The public invocation contract is explicit: run the CLI as `sldb ...` or `python -m sldb ...`, not `bash sldb ...`.

2. **info — `README.md:43-58`, `docs/faq.md:24-31`, `docs/faq.md:182-195`**  
   The docs distinguish two durable workflow modes: direct model operations (`extract`, `render`, `validate`) that do not require a store, and store-backed project workflows that do.

3. **info — `README.md:57-59`, `docs/faq.md:63-76`**  
   A core user contract is that models are referenced as Python imports in `module:ClassName` form, with `--pythonpath` used when the module is outside the default import path.

4. **info — `README.md:69-87`, `docs/faq.md:133-145`, `README.md:125-138`**  
   The docs present durable distinctions among document lifecycle behaviors: create/track/update versus recover/compose, plus plural-first command surfaces and deprecation of singular aliases.

5. **info — `docs/faq.md:114-131`, `docs/atoms/tracked-document.atom.md:7-23`, `README.md:125-130`**  
   Tracked documents have a stable user-facing identity model: logical store name plus physical file path, and commands may accept tracked name, qualified name, or path.

6. **info — `docs/faq.md:46-61`, `README.md:89-123`, `docs/README.md:27-39`**  
   Search and navigation behavior is intentionally split between semantic and physical retrieval, with deeper navigation coming from `sections`, `fields`, and `ast` once a store exists.

7. **info — `README.md:85-98`, `docs/faq.md:212-233`, `docs/faq.md:284-292`**  
   `stores semantic-export` is documented as a product boundary: SLDB emits its own semantic document truth for graph consumers, but not workflow-specific or source-code dependency edges.

8. **warning — `README.md:55`, `docs/architecture/current-cli-tree.md:15-16`, `docs/faq.md:133-145`**  
   The docs still reference both old top-level `recover`/`compose` and newer `docs recover`/`docs compose` surfaces. The durable atom should capture the behavior distinction, not overfit to a specific command nesting while the CLI settles.

9. **warning — `README.md:85-87`, `README.md:261-362`, `docs/faq.md:33-45`**  
   Low-level store implementation details vary across docs (`.sldb/core/` + `.sldb/runtime/` vs older YAML cascade examples). External atoms should preserve store purpose and visible outcomes, not freeze internal file layout.

10. **warning — `README.md:50-53`, `docs/faq.md:325-344`, `docs/workspaces.md:7-16`**  
   `inbox` is user-facing today, but it is partially tied to the repo's `desk/` workspace pattern. It is less durable as a cross-project SLDB core atom than store/model/doc/query behaviors.

## External behavior atoms: create now

| Domain | Proposed atom title | Concise answer / durable contract | Durable enough now? | Key evidence |
|---|---|---|---|---|
| Onboarding & invocation | **CLI invocation contract** | Run SLDB as `sldb ...` or `python -m sldb ...`; `bash sldb ...` is incorrect because `sldb` is an entrypoint, not a shell script. | **Yes** | `README.md:24-40`; `docs/faq.md:5-16` |
| Onboarding & workflow choice | **Direct mode vs store-backed mode** | You can use `extract`, `render`, and `validate` without a store; use a store when you need registrations, tracked docs, project queries, semantic/section indexes, or integrity checks. | **Yes** | `README.md:61-67`; `docs/faq.md:24-31`; `docs/faq.md:182-195` |
| Store & identity | **What a store is** | A store is SLDB's metadata workspace for models, tracked docs, integrity, and indexes; it does not replace Markdown files or become the text source of truth. | **Yes** | `docs/faq.md:18-45`; `README.md:261-263` |
| Store & identity | **Local vs global store precedence** | A project-local `.sldb/` is the normal workflow location; a global `~/.sldb/` may also exist, and the local store wins when both are present. | **Yes** | `docs/faq.md:24-31`; `README.md:263`; `docs/atoms/store.atom.md:19-23` |
| Models | **Model reference format** | Model identifiers are Python import refs in `module:ClassName` form, not raw file paths; pass `--pythonpath` when needed to make the module importable. | **Yes** | `README.md:57-58`; `docs/faq.md:63-76` |
| Models | **StructuredNLDoc contract** | A `StructuredNLDoc` defines the Markdown template, typed Pydantic fields, field descriptions, and optional semantics that make Markdown ↔ payload workflows possible. | **Yes** | `docs/faq.md:147-160`; `README.md:59`; `README.md:152-178` |
| Models | **Field descriptions are required contract** | Every `StructuredNLDoc` field must have a non-empty Pydantic `description`; docs treat those descriptions as part of the public model contract. | **Yes** | `README.md:59`; `README.md:226`; `docs/faq.md:300-301` |
| Rendering & data | **Payload input forms** | Create/update/render workflows accept payload data either inline or from a JSON/YAML file path; the payload must conform to the target model schema. | **Yes** | `docs/faq.md:162-180`; `README.md:80`; `README.md:174-178` |
| Documents | **Tracked document identity** | A tracked doc has both a logical store name and a physical file path; commands may accept tracked name, qualified `Model/name`, or path depending on the command. | **Yes** | `docs/faq.md:114-131`; `README.md:125-130` |
| Documents | **Create vs track vs update** | `docs create` renders a new document from payload and tracks it; `docs track` validates and registers an existing Markdown file; `docs update` re-renders a tracked doc from new payload data. | **Yes** | `docs/faq.md:133-145`; `README.md:69-83`; `README.md:287-315` |
| Links & composition | **Recover vs compose** | `recover` answers what links/transclusions resolve to; `compose` produces the document view after transclusions are expanded. These are link-oriented behaviors, not payload extraction. | **Yes** | `docs/faq.md:133-145`; `docs/faq.md:253-264`; `README.md:125-130` |
| Query & retrieval | **Semantic vs physical search** | Physical search matches names/paths/structure; semantic search matches meaning-like tags and section meaning. Users choose based on whether they know a path-like token or a concept. | **Yes** | `docs/faq.md:46-61`; `README.md:89-106` |
| Query & retrieval | **How to get data out of SLDB** | Different outputs exist for different levels: `extract` for one file payload, `docs show` for tracked docs, `fields` for field values, `find` for retrieval, `ast show` for normalized inspection, and `stores semantic-export` for bulk graph handoff. | **Yes** | `docs/faq.md:212-233`; `README.md:89-123` |
| Query & retrieval | **Field and section navigation** | Once a store exists, `sections`, `fields`, and `find` are the supported user-facing ways to navigate document structure more deeply than shallow doc models allow. | **Yes** | `README.md:100-123`; `docs/README.md:27-39`; `docs/README.md:81-87` |
| Debugging & inspection | **AST as the debugging surface** | `ast show` is the primary user-facing debug surface when query, section, or ownership results do not match expectations. | **Yes** | `docs/faq.md:196-210`; `README.md:91-95` |
| Integrity & maintenance | **Store integrity checks** | `stores check` reports drift or breakage in tracked state without modifying content; `stores update` rebuilds indexes after bulk changes. Preserve the user-visible purpose, not the internal hash implementation. | **Yes** | `docs/faq.md:182-195`; `README.md:277-285`; `README.md:352-362`; `docs/workspaces.md:38-46` |
| Authoring patterns | **Shallow `title + body` is the default for heterogeneous docs** | For large READMEs, FAQs, architecture docs, and Pandoc-style narrative docs, the recommended default is a shallow `title + body` model unless typed structure is truly needed. | **Yes** | `docs/faq.md:91-112`; `docs/faq.md:274-282`; `docs/README.md:23-39`; `docs/README.md:81-87` |
| Authoring patterns | **Lead paragraph anchor rule** | When using generic `title + body`, keep a short paragraph after the H1 before dense fenced blocks or subsections so extraction stays stable. | **Yes** | `docs/README.md:35-39`; `docs/faq.md:99-106` |
| Export boundary | **Semantic export boundary** | `stores semantic-export` emits SLDB-owned semantic document truth for graph consumers, but downstream systems add workflow-specific and code-dependency edges. | **Yes** | `README.md:95-98`; `docs/faq.md:222-233`; `docs/faq.md:284-292`; `docs/workspaces.md:7-16` |
| CLI evolution | **Plural-first CLI surface and legacy alias status** | The primary public workflow is organized around `stores`, `models`, `docs`, `fields`, `sections`, `find`, and `ast`; older singular/raw surfaces are legacy/deprecated. | **Yes** | `README.md:43-55`; `docs/architecture/current-cli-tree.md:21-42` |
| Model editing | **Draft-first model edits** | Template and field edits are draft-first; the active model contract does not change until validation/promotion succeeds. | **Yes** | `README.md:75-87` |

## External behavior atoms: probably defer or phrase carefully

| Domain | Proposed atom title | Why defer or narrow it | Durable enough now? | Key evidence |
|---|---|---|---|---|
| Onboarding | **FAQ browser behavior** | `sldb faq` is clearly user-facing, but it is more of a discoverability surface than a core product contract. Keep as supporting UX unless the refactor is explicitly preserving CLI onboarding helpers. | **Maybe later** | `docs/faq.md:307-323`; `README.md:48-50`; `docs/architecture/current-cli-tree.md:12` |
| Onboarding | **Explore command behavior** | `explore` appears in docs as a written-guidance search tool, but its long-term role is less emphasized than `faq`/`help`/`find`. Capture later if the CLI surface remains. | **Maybe later** | `README.md:40`; `docs/faq.md:305`; `docs/architecture/current-cli-tree.md:14` |
| Repo-integrated feedback | **Inbox note capture** | This is observable today, but it depends on `desk/` workspace conventions and `InboxNoteDoc`, which are less clearly core SLDB than store/model/doc behaviors. | **No, unless refactor explicitly keeps it** | `README.md:50-53`; `docs/faq.md:325-344`; `docs/workspaces.md:7-16` |
| Store internals | **Store file layout and hash cascade** | The docs disagree on the exact layout (`.sldb/core`/`.sldb/runtime` vs older YAML cascade). Preserve only the external promise of metadata, precedence, integrity checks, and rebuildability. | **No as an external atom** | `docs/faq.md:33-45`; `README.md:267-285` |
| Debugging internals | **Normalized AST schema shape** | `ast show` is durable as a debugging surface, but the precise normalized payload shape is more implementation-sensitive than a stable user contract. | **No as a fine-grained atom** | `docs/faq.md:196-210`; `docs/atoms/ast-view.atom.md:7-23` |
| Federation | **Linked store namespace behavior** | Federation is documented, but store-linking and cross-store model resolution feel less proven as a v1 durable contract than local store basics. Keep if the refactor scope includes federation. | **Maybe later** | `README.md:317-342` |

## Recommended domain grouping for the atom set

1. **Onboarding & invocation**
   - CLI invocation contract
   - Direct mode vs store-backed mode
   - Plural-first CLI surface and legacy alias status

2. **Store & identity**
   - What a store is
   - Local vs global store precedence
   - Tracked document identity
   - Store integrity checks

3. **Models & payloads**
   - Model reference format
   - StructuredNLDoc contract
   - Field descriptions are required contract
   - Payload input forms
   - Draft-first model edits

4. **Document lifecycle & composition**
   - Create vs track vs update
   - Recover vs compose
   - Shallow `title + body` default
   - Lead paragraph anchor rule

5. **Query, retrieval & export**
   - Semantic vs physical search
   - How to get data out of SLDB
   - Field and section navigation
   - AST as the debugging surface
   - Semantic export boundary

6. **Optional / supporting UX atoms**
   - FAQ browser behavior
   - Explore command behavior
   - Inbox note capture

## Distinction from internal/engine atoms

These topics appear in the docs but should **not** be treated as primary external-behavior atoms unless the refactor needs them explicitly:
- exact `.sldb/` file layout
- exact Merkle/hash layer names (`hash_a`/`hash_b`/`hash_c`/`hash_d`)
- AST normalization schema details
- specific runtime artifact directories
- downstream workflow-domain semantics owned by `opsys`/`deskops`

The stable external contract is the observable behavior: what the CLI accepts, what it produces, when a store is required, how docs are identified, what commands mean, and what kinds of queries/exports users can rely on.

## Suggested “create now” atom slate

If the refactor wants a compact durable atom set immediately, create these first:
1. CLI invocation contract
2. Direct mode vs store-backed mode
3. What a store is
4. Local vs global store precedence
5. Model reference format
6. StructuredNLDoc contract
7. Field descriptions are required contract
8. Payload input forms
9. Tracked document identity
10. Create vs track vs update
11. Recover vs compose
12. Semantic vs physical search
13. Store integrity checks
14. Shallow `title + body` is the default for heterogeneous docs
15. Semantic export boundary

Those fifteen cover the clearest v1 user-facing contracts that recur across both `README.md` and `docs/faq.md` and are least coupled to current implementation details.

```acceptance-report
{
  "criteriaSatisfied": [
    {
      "id": "criterion-1",
      "status": "satisfied",
      "evidence": "Concrete findings are listed under High-value findings with file paths, line ranges, and info/warning severity; the atom map cites the same source files for each durable behavior contract."
    }
  ],
  "changedFiles": [
    "subagent/behavior-atom-map.md"
  ],
  "testsAddedOrUpdated": [],
  "commandsRun": [
    {
      "command": "ls . && find . -path '**/README.md' && find . -path '**/docs/faq.md'",
      "result": "passed",
      "summary": "Located requested docs and confirmed docs/faq.md exists in the current repo."
    },
    {
      "command": "read README.md; read docs/faq.md; read docs/README.md; read docs/workspaces.md; read docs/architecture/current-cli-tree.md",
      "result": "passed",
      "summary": "Reviewed the primary contract docs plus adjacent CLI/workspace context."
    },
    {
      "command": "nl -ba README.md | sed -n '1,420p'; nl -ba docs/faq.md | sed -n '1,360p'; nl -ba docs/README.md | sed -n '1,220p'; nl -ba docs/workspaces.md | sed -n '1,220p'; nl -ba docs/architecture/current-cli-tree.md | sed -n '1,220p'",
      "result": "passed",
      "summary": "Captured line-numbered evidence for the final atom map and findings."
    }
  ],
  "validationOutput": [
    "Artifact written to /home/jp/proyectos/hum-ecosystem/tools/sldb-refactor-worktree/subagent/behavior-atom-map.md",
    "Findings grounded in README.md, docs/faq.md, docs/README.md, docs/workspaces.md, and docs/architecture/current-cli-tree.md"
  ],
  "residualRisks": [
    "Current docs mix newer plural command groups with older top-level recover/compose references, so atoms should preserve behavior meaning rather than lock to one nesting form.",
    "Store layout details differ across docs, so only high-level store behavior should be treated as durable without code-level confirmation.",
    "Inbox and some onboarding helpers may be less stable across refactors because they are partially tied to repo-specific desk workflows."
  ],
  "noStagedFiles": true,
  "diffSummary": "Added a behavior atom map artifact summarizing durable user-facing SLDB v1 contracts from current documentation.",
  "reviewFindings": [
    "warning: README.md:55 and docs/architecture/current-cli-tree.md:15-16 - command-surface docs still show both deprecated top-level and newer nested recover/compose variants.",
    "warning: README.md:267-285 and docs/faq.md:33-45 - store internals are described inconsistently enough that only high-level store behavior should be frozen as an external atom.",
    "no blockers: the requested external-behavior atom candidates are well-supported by the reviewed docs."
  ],
  "manualNotes": "The map intentionally excludes engine/internal atoms and identifies which UX/helper surfaces are probably too unstable to enshrine yet."
}
```