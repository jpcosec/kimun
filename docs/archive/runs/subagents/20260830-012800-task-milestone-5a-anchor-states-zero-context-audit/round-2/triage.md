# Triage — round 2

| # | finding | lane | resolution |
|---|---|---|---|
| 1 | `:add-edge` supersedes trigger not coded; `anchor.cljc` absent; `markdown->update-plan` absent | A | Not spec defects: they are the deliverable of 5a and 5b. Recorded as acceptance conditions. |
| 2 | fixture/generator emit a bare hex fingerprint | A | Scoped work, already in the task's files and implementation path. |
| 3 | a `:span` over an orphan leaf only implicitly orphan | A | **§6.2** states it. |
| 4 | default `:base` undefined when a revision has not exactly one parent | A | **§6.5**: `:base` becomes obligatory there and its absence raises `:reconcile/base-required`. |
| 5 | empty chain / first-step multiplicity of `:successors`, `:latest`, `:ambiguous?` | A | **§6.2**: the three cases spelled out. |
| 6 | a cycle "cuts the chain" without saying what the fields become | B | **§6.2**: a cycle behaves as the end of the chain; `:latest` is the last node reached, `:ambiguous?` stays false, never an error. |
| 7 | `anchor/diff` `:changed` — keywords or full maps, and names colliding with the endpoints | B | **§6.3**: renamed to `:before`/`:after` and typed as the edge state keywords. |
| 8 | `promises.md` missing from the task's files | B | Not true of the live task: the lane read a `task.txt` snapshotted before round 1's corrections. Snapshot refreshed. |
| 9 | how `node/validate` gets the algorithm; where the check lives; which error | B | Already settled in §6.4; repeated in the task's implementation path so the task alone suffices. |
| 10 | positions order; `derived` with a superseded endpoint | B | Already settled in §6.3 and §6.2. |
| 11 | 5a alone does not close the autopoietic loop | A | Accepted: the roadmap split says so. |

Round 3 re-runs A and B fresh on the corrected bundle and on the refreshed snapshot.
