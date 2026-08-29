# Implementation Ambiguities

1. **Clojure/Python ownership map:** Which first-slice components are implemented in Clojure vs Python orchestration? The atoms assign the canonical core to Clojure and the user shell to Python, but do not explicitly place `markdown-importer`, `markdown-emitter`, `document-tracker`, `document-materializer`, `query-engine`, `store-infrastructure`, `store-integrity-checks`, or `semantic-indexing`. (`clojure-core.md`, `python-cli-orchestration-layer.md`, related component atoms)
2. **Python↔Clojure boundary contract:** What is the concrete integration contract between the Python CLI layer and the Clojure core—API shape, serialization format, error taxonomy, and versioning of AST/projection payloads? Without this, orchestration can easily re-own core logic or tests can lock onto the wrong boundary. (`python-patterns.md`, `clojure-patterns.md`, `ast-command-group.md`)
3. **First migration slice:** What exact workflow is the first safe vertical slice: direct-mode `extract/render/validate`, Markdown import→emit round-trip, or a store-backed `docs` workflow? The atoms point to importer/emitter as the first round-trip slice, but the CLI continuity atoms do not name the first command family to move. (`markdown-importer.md`, `markdown-emitter.md`, `python-cli-orchestration-layer.md`, `direct-mode-vs-store-backed-mode.md`)
4. **Model source of truth during migration:** Are Python `StructuredNLDoc`/Pydantic models still the runtime source of truth, or are they compiled/transcribed into a Clojure-side schema/type contract? This is especially unclear because model references remain Python import refs while canonical structure and invariants move to Clojure. (`structurednldoc-contract.md`, `model-reference-format.md`, `type-contract.md`, `clojure-core.md`)
5. **Direct-mode sharing vs duplication:** In direct mode, do `extract`, `render`, and `validate` execute the same Clojure-backed canonical pipeline used by store-backed workflows, just without persistence, or is a separate lightweight path expected? The current atoms describe two modes but not whether implementation should share one pipeline. (`direct-mode-vs-store-backed-mode.md`, `canonical-ast.md`, `projection.md`)
6. **Tracked-doc authority after import:** After `docs track` imports an existing Markdown file, what becomes authoritative for later operations: the imported canonical AST, the Markdown artifact, or both under drift detection rules? This needs a precise answer to avoid incompatible behavior across `docs track`, `docs update`, `stores check`, and `stores update`. (`create-vs-track-vs-update.md`, `document-tracker.md`, `document-materializer.md`, `store-integrity-checks.md`)
7. **Accepted identifiers per command:** Which commands must accept tracked name, qualified `Model/name`, raw path, or model import ref? The atoms say accepted identifiers vary by command, but there is no command-by-command matrix, which makes compatibility testing underspecified. (`tracked-document-identity.md`, `docs-command-group.md`, `models-command-group.md`)
8. **AST output stability for tests/tools:** What minimum `ast` output contract is intentionally stable across the refactor? The atoms preserve `ast` as the debugging surface but explicitly leave schema evolution open, which makes regression-test scope and downstream tooling brittle unless a stable subset is declared. (`ast-command-group.md`, `ast-as-the-debugging-surface.md`)
9. **Search MVP boundary:** What is in scope for the first implementation of `find` semantic behavior—explicit tags/links only, selector-aware structure, optional embeddings, or all of them? The atoms allow a wide range of semantic indexing depth, but they do not define the minimum contract that Python tests and Clojure components should target first. (`find-command-group.md`, `semantic-vs-physical-search.md`, `semantic-indexing.md`, `embeddings.md`)
10. **Store resolution rules at the CLI boundary:** How should store-backed commands resolve context when local and global stores both exist, and when the invocation also supplies paths or model refs? Local precedence is stated, but the exact lookup/fallback/error behavior for mixed inputs is not. (`local-vs-global-store-precedence.md`, `what-a-store-is.md`, `repository-registry.md`)
11. **Legacy alias migration policy:** Are deprecated singular/raw aliases required to remain functional during the refactor, or is preserving only the plural-first surface acceptable? The atoms preserve public direction but not the operational deprecation plan, which directly affects routing code and CLI compatibility tests. (`plural-first-cli-surface-and-legacy-alias-status.md`, `help-command-group.md`, `cli-workflow-surface.md`)
12. **Test ownership at the seam:** Which behaviors require end-to-end Python CLI tests across the Python↔Clojure seam, and which can be proven only with Clojure structural tests? The atoms split testing by layer, but they do not define the mandatory integration-test matrix for round-trips, store maintenance, query behavior, and command compatibility. (`testing.md`, `clojure-testing.md`, `python-testing.md`)

```acceptance-report
{
  "criteriaSatisfied": [
    {
      "id": "criterion-1",
      "status": "satisfied",
      "evidence": "Produced only a concise ambiguity list derived from desk/atoms CLI and component-related atoms; no source implementation changes were made."
    },
    {
      "id": "criterion-2",
      "status": "satisfied",
      "evidence": "Included the exact output file path, a focused list of implementation-facing questions, and an acceptance report with reviewed-tool evidence and residual risks."
    }
  ],
  "changedFiles": [
    "/home/jp/proyectos/hum-ecosystem/tools/sldb-refactor-worktree/subagent/questions-implementation-ambiguities.md"
  ],
  "testsAddedOrUpdated": [],
  "commandsRun": [
    {
      "command": "functions.ls desk/atoms",
      "result": "passed",
      "summary": "Enumerated atom files to constrain review scope."
    },
    {
      "command": "functions.read on CLI, command-group, component, testing, and Clojure/Python split atoms under desk/atoms",
      "result": "passed",
      "summary": "Reviewed 62 relevant atom files without reading outside desk/atoms."
    },
    {
      "command": "functions.grep for migration and Clojure/Python references under desk/atoms",
      "result": "passed",
      "summary": "Confirmed where migration and language-boundary guidance is explicit vs missing."
    }
  ],
  "validationOutput": [
    "Scope stayed within /desk/atoms.",
    "Findings are implementation-facing questions only, focused on boundaries, migration, testing scope, and the Clojure/Python split.",
    "No repo source files were modified; only this findings file was written."
  ],
  "residualRisks": [
    "The atom set leaves several decisions intentionally open; answers will need supervisor/reviewer direction before implementation starts.",
    "Git staged-file state was not directly inspectable with the available tools."
  ],
  "noStagedFiles": true,
  "diffSummary": "Added one findings document containing a concise ambiguity list and acceptance evidence; no product/source files changed.",
  "reviewFindings": [
    "no blockers in scope; the output is a question list intended to surface design decisions before implementation."
  ],
  "manualNotes": "The review intentionally emphasized CLI/component atoms and avoided widening into non-atom design docs."
}
```