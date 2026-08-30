# Lane A — internal coherence, round 2 (corrected bundle)
Model: claude-haiku-4-5, fresh context, read-only.

## Findings

Note [high] docs/v2/02 §6.1:455-467 vs src/sldb/kernel/revision.cljc:112-132 — the
`:add-edge` re-anchoring trigger is specified and not implemented.
→ Not a spec defect: it is the deliverable. The lane confirms the spec now states all
  three steps (detect new, re-anchor with old = `:to` and new = `:from`, record the pair).

Note [high] docs/v2/02 §6.4:586 vs test/fixtures/nodes.edn:23 and generators.cljc:41-42 —
the frozen fixture and the generator emit a bare hex fingerprint.
→ Not a spec defect: the alignment of fixture, generator and node_test is scoped work.

Note [high] docs/v2/04 §8:279-288 — `markdown->update-plan` is specified and not coded.
→ Not a spec defect: it is milestone 5b, and the section says so.

Note [medium] docs/v2/02 §6.2:489-503 — a `:span` over an orphan leaf is only implicitly
orphan. → **Fixed**: stated explicitly.

Note [medium] docs/v2/02 §6.5:617-618 — the default `:base` is "the single parent"; a
revision with several parents leaves it undefined. → **Real gap. Fixed**: `:base` is
required when the revision does not have exactly one parent, and its absence raises.

Note [low] docs/v2/02 §6.3:559-561 — the empty and first-step-multiplicity cases of
`:successors`/`:latest`/`:ambiguous?` should be spelled out. → **Fixed**.

Note [low] docs/v2/01 §8 vs docs/v2/02 §6 — 5a alone reports orphans and does not close
the autopoietic loop; the loop closes with 5b. No contradiction. → Accepted as written;
the roadmap split already says it.

## Verdict

§6.1–§6.5 are internally coherent and coherent with 01, 03, 04, 05 and the epoch:v2
atoms: idempotent no-ops, chain following with ambiguity, worst-of-two state, and
deterministic fingerprint validation hang together, and §6.5 does not contradict §3.2 or
§6.1. The three high findings are the absence of the implementation itself. Two real gaps
remain, both narrow.
