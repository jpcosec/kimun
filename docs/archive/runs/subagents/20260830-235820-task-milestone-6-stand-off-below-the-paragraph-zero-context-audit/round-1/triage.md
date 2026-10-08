# Triage — round 1

## Lane B: FINAL VERDICT ready (A–M pass). No action.
## Lane C: no contradiction; claims (1) :span+§6.2 validation exist and (2) layers are derived-not-nodes both VERIFIED against src/. No action.

## Lane A findings

- [high] "span validation assumed to exist / §6.2 does not describe span validation."
  Disposition: RESOLVED BY EVIDENCE (Lane C). anchor.cljc:68–79 `resolves?` implements
  per-endpoint :span validation and its docstring cites §6.2; node.cljc:19–23 defines the
  :span kind. The claim is accurate. Correction applied anyway: task scope now points to the
  concrete files (node.cljc / anchor.cljc) instead of leaning on the "§6.2" label, to remove
  the ambiguity the lane flagged. -> task edit.

- [medium] cache-key identity: "leaf id" vs "leaf hash".
  Disposition: REAL AMBIGUITY -> CORRECTION. In this kernel a node id IS its content hash,
  so leaf-id and leaf-hash denote the same value. Clarified in the task scope so the executor
  keys the memo cache on the leaf node id (content hash). -> task edit.

- [medium] grapheme-layer prerequisite (ports/graphemes present + grapheme-cluster aware).
  Disposition: RESOLVED BY EVIDENCE (Lane C). ports.cljc:22–25 + host/text.cljc:24–41 provide
  graphemes counting UAX #29 clusters (not UTF-16). No bundle change needed.

- [low] Done When should cite the §9 row 6 exit criterion.
  Disposition: CORRECTION (cheap). Added the exit-criterion phrase to Done When. -> task edit.

## Outcome
No design problem returned to user. One real ambiguity fixed (cache key), one wording
tightened (scope), one low fixed (exit criterion). Re-run Lane A on the corrected bundle.
