# Lane A — internal coherence (round 1)

## Reading Summary

**Files audited:**
- `desk/tasks/task-milestone-6-stand-off-below-the-paragraph.md` (task spec)
- `desk/contexts/pill-guardrail-v2-implementation-gate.md` (v2 execution guardrail)
- Four epoch:v2 atoms (stand-off, virtual addresses, anchor state, inline marks)
- `docs/v2/02-sustrato-computacional.md` sections 4, 4.1, 6.2, and row 6 of roadmap (§9)

---

## Findings

### Note [high] task-milestone-6-stand-off-below-the-paragraph.md:32–42 (Scope statement)

**Finding:** The task states "The span node kind and its per-endpoint anchor-state validation (section 6.2) already exist and stay as-is." However, §6.2 does not describe span nodes or their validation. §6.2 (`Anchor state is derived and computed per endpoint`, lines 575–635) covers endpoint state computation via `resolves?(R, X)` and the state logic, but makes no reference to span-specific validation in `node.cljc` or `anchor.cljc`. The implication that span validation for anchors is a separate concern in existing code is not supported by the referenced section.

**Impact:** Unclear whether task boundary is correctly drawn. The task assumes span validation (including range checks against grapheme count in §6.2:599–601) is already present and working; if not, this is out-of-scope work that the task does not claim to do.

---

### Note [medium] task-milestone-6-stand-off-below-the-paragraph.md:27–30 (Goal statement)

**Finding:** The goal says "Add deterministic UAX 29 word and sentence segmentation to the TextSegmenter port and its host adapter, derived on demand and cached by leaf id, not materialized as graph nodes." This aligns with §4 and §4.1 of `02-sustrato-computacional.md`. However, the task does not explicitly state whether the grapheme layer (which is prerequisite to word/sentence offsets being deterministic and correct) must also be added or if it is already available via `ports/graphemes`. The task's Implementation Path says "Start from the existing span node validation in src/sldb/kernel/anchor.cljc which already counts leaf graphemes via ports/graphemes," implying `ports/graphemes` is already present, but this is not verified in scope.

**Impact:** Medium risk. The task assumes a pre-existing grapheme-counting capability. If `ports/graphemes` does not exist or does not count in grapheme clusters (not UTF-16 code units), the word and sentence layers will be built on a broken foundation.

---

### Note [medium] atom-stand-off-annotation-below-the-paragraph.md (entire; no line needed)

**Finding:** The atom states that "deterministic, derived on demand and cached by leaf hash" are stored as "offset arrays cached by leaf hash." However, `desk/tasks/task-milestone-6-stand-off-below-the-paragraph.md:38` says offset arrays are "memoized per leaf id" and §4 of `02-sustrato-computacional.md` (line 547) says they are "cacheados por hash de hoja." The task file uses "leaf id" and "leaf-id" throughout, while the atom and sustrato use "leaf hash." These are not the same: an id is content-addressed (the hash of the node), while "hash of the leaf" could mean the hash stored as `:leaf` inside the span node's `{:leaf :range}` map. The task implementation should clarify whether the cache key is the node's id (a hash) or a separate hash value.

**Impact:** Medium risk. Ambiguity in cache key identity may lead to incorrect memoization logic or confusion during implementation or testing.

---

### Note [low] task-milestone-6-stand-off-below-the-paragraph.md:43–52 (Validation and Done When)

**Finding:** The task requires "bb lint, bb test and bb oracle are all green" but section §9 row 6 of `02-sustrato-computacional.md` (the roadmap milestone 6) states the exit criterion as "dirección hasta el grafema sin nodos materializados" (address down to the grapheme with no materialized nodes). The task's "Done When" does not explicitly invoke the exit criterion from the spec; instead it relies on test evidence. This is not a contradiction (tests are the proof), but it is a misalignment of language. The task's Done When should mention the spec exit criterion explicitly.

**Impact:** Low. Test evidence is sufficient proof; the omission is a documentation/alignment issue, not a functional gap.

---

## Summary

**Internal coherence status:** Two medium-risk ambiguities; one high-risk scope boundary issue.

- **High:** Span validation is assumed to exist; verify it is already in `src/sldb/kernel/anchor.cljc` or decide if it is in scope.
- **Medium:** Cache key identity (`leaf-id` vs. `leaf hash`) is ambiguous; clarify the concrete type in `src/sldb/kernel/standoff.cljc`.
- **Medium:** Prerequisite `ports/graphemes` capability is assumed to exist and work correctly; verify it is present and grapheme-cluster–aware.
- **Low:** Task's Done When should cite the roadmap exit criterion (§9 row 6) for clarity.

---

## No Contradictions

The task makes no claims that contradict §4, §4.1, §6.2, or the four atoms. The virtual address resolver, memoization by leaf, non-materialization of deterministic layers, and re-use of the span kind with its anchor-state validation are all aligned with the spec.

