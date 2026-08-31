# Zero-context audit brief — task-milestone-6-stand-off-below-the-paragraph

## Bundle (the only files a lane may read)

- Task: desk/tasks/task-milestone-6-stand-off-below-the-paragraph.md
- Pill: desk/contexts/pill-guardrail-v2-implementation-gate.md
- Atoms:
  - desk/atoms/atom-stand-off-annotation-below-the-paragraph.md
  - desk/atoms/atom-stand-off-addresses-are-virtual-until-referenced.md
  - desk/atoms/atom-anchor-state-is-derived-and-computed-per-endpoint.md
  - desk/atoms/atom-inline-marks-live-on-the-block-text-leaves-stay-plain.md
- Spec: docs/v2/02-sustrato-computacional.md sections 4, 4.1, 6.2, and section 9 row 6
- Existing code the task builds on (read-only reference):
  - src/sldb/kernel/ports.cljc (TextSegmenter protocol)
  - src/sldb/host/text.cljc (graphemes)
  - src/sldb/kernel/node.cljc (:span kind)
  - src/sldb/kernel/anchor.cljc (:span endpoint validation, §6.2)

## Snapshots
- task.txt, next.txt, git-status.txt, graph.txt frozen in this directory.

## Lanes (fresh context, read-only, cheap model, no chat context)

- Lane A — internal coherence: does the task agree with docs/v2 sections 4/4.1/6.2 and the four epoch:v2 atoms? Any contradiction between the bundle and the spec?
- Lane B — executability against the A–M zero-context checklist: could a blind executor implement this from task + pill + atoms alone, with the named files, validation (bb lint/test/oracle) and Done When, without asking a question? Final verdict must be "ready" with all sections pass, or list exact gaps.
- Lane C — contradiction sweep: do any legacy atoms/features contradict this task's claim that :span and §6.2 validation already exist and that words/sentences layers are derived-not-materialized?

## Rules for lanes
- "The code does not exist yet" is NOT a finding; this is an implementation task.
- Report findings as: Note [high|medium|low] file:lines <finding>.
- Design problems are returned to the user, not invented away.

## Stop condition
- No high/medium findings, or after 3 rounds. Remaining lows recorded as accepted debt.
