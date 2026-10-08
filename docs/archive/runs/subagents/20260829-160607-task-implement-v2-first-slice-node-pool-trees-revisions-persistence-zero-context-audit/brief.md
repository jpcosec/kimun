# Zero-context audit gate — brief

Task: task-implement-v2-first-slice-node-pool-trees-revisions-persistence
Ritual: desk/rituals/ritual-zero-context-audit-gate.md
Pill: desk/contexts/pill-guardrail-v2-implementation-gate.md
Date: 2026-08-29
Model for lanes: claude-haiku-4-5 (deliberately weak), subagent type Explore (read-only), no chat context.

## Authority (what counts as truth)

1. docs/v2/01-orden-filosofico.md
2. docs/v2/02-sustrato-computacional.md
3. desk/atoms/atom-*.md tagged epoch:v2 (13 files)
4. everything else is lower authority and may be wrong

## Lanes (round 1)

| Lane | Reads | Answers |
|---|---|---|
| A | docs/v2/*.md, desk/atoms/atom-*.md (epoch:v2) | internal coherence: contradictions, undefined terms, unverifiable invariants |
| B | task file, its routine/checklists, bound atoms, docs/v2/02 §§2–5, 8–10, README.md, AGENTS.md, bound pill, checklist A–M | executability by a blind executor; verdict ready / not ready |
| C1 | legacy atoms desk/atoms/[a–k]*.md (no atom- prefix) vs docs/v2 | contradictions with v2: retire / rewrite / neutral |
| C2 | legacy atoms desk/atoms/[l–z]*.md (no atom- prefix) vs docs/v2 | same |
| C3 | docs/architecture/**/*.md (contracts, overview), desk/drawer/**/*.md, desk/tasks/*.md, desk/rituals/*.md, desk/contexts/*.md vs docs/v2 | same, plus tasks/tracks/features that must be retired or rewritten |

## Severity

- high: blocks execution, or two authoritative sources contradict each other
- medium: undefined term / ambiguity that would force an executor to improvise
- low: wording, style, minor inconsistency without execution impact

## Output format (mandatory, every lane)

```
## Review
- Correct: <what is well defined> (file:lines)
- Note [high|medium|low]: file:lines — <contradiction / gap / ambiguity> — <concrete question>
## Verdict
<lane-specific>
```

Lane B additionally reports the 100% subagent-ready checklist (sections A–M, pass/partial/fail) and a final `ready` / `not ready` with required fixes.
Lanes C report one table: file | classification (retire / rewrite / neutral) | quoted contradicting sentence | v2 source it contradicts.

## Checklist A–M (lane B)

Source: tools/deskops/desk/drawer/attention/20260621-164028-note-subagent-ready-task-standard-checklist.md
A identity and bounded purpose · B task-local content only · C dependency clarity · D reference (atom) sufficiency · E file sufficiency · F pill sufficiency · G validation quality · H testability and immediate test path · I fixture/evidence sufficiency · J boundary clarity · K ambiguity (what to read first, what to modify, how to test, what evidence, what done means) · L legacy/duplication · M repo-sync truthfulness.
Hard rule: if a reasonable executor would need to ask a clarifying question before starting, the task is not ready.

## Round 2 (after triage of round 1)

Same authority, severity and output format. Bundle changed: docs/v2/02 gained §2.1, 3.1, 3.2, 4.1, 5.1–5.3, 6.1, 8.1 and invariants 13–15; 22 epoch:v2 atoms; four milestone tasks (0–3) with depends_on chain; bb.edn/deps.edn exist; 16 legacy atoms, 5 architecture docs and 7 drawer items retired under raw/source/*-retired/.
Lanes: A (coherence), B0 (milestone 0 task), B1-3 (milestone 1–3 tasks), C1/C2 (legacy atoms a–k / l–z), C3 (docs/architecture, drawer, tasks, rituals, entry docs). Lanes never read runs/ or raw/source/.
