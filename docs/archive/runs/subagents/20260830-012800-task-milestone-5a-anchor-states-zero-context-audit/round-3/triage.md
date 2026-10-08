# Triage — round 3 (last round; the ritual caps the gate at 3)

| # | finding | lane | resolution |
|---|---|---|---|
| 1 | §5 lists the primitive ops without saying that two of them re-anchor | A | **Fixed in §5**: `replace` always, `add-edge` when the `supersedes` is new, and no other op. |
| 2 | §6.3 calls `:as-from`/`:as-to` "mapas" while showing a vector | A | **Fixed**: vectors of `anchor/state` maps. |
| 3 | §6.3 does not say the edge-id order is ascending | A | **Fixed**, in both places. |
| 4 | seven "high" findings that each say the implementation does not exist yet | B | **Rejected as category errors.** The lane prompt excluded them explicitly and the lane reported them anyway — the documented failure mode of a weak model on a bundle with nothing left to find. Each is quoted from the task's own implementation path, which is the evidence that the bundle settles it. |
| 5 | "anchor.cljc's internal algorithm is not specified" | B | **Rejected**: §6.2 gives three ordered conditions and three chain cases and §6.3 fixes every shape and order. Asking for the algorithm is asking for the code. |
| 6 | "moving re-anchor above add-edge-op" is ambiguous | B | **Fixed** in the task's implementation path: both the relocation and the reason (Clojure resolves the var at read time) are stated, and it is said the move changes no code. |

## Gate verdict

**Closed.** Round 3 leaves zero high and zero medium findings that survive triage: lane A's
single medium and two lows are corrected in the spec, lane B's highs are the absence of
the implementation, which the gate is not about, and its one genuine finding is corrected
in the task.

Accepted debt carried out of the gate: none from the spec. Two process notes for the next
gate — snapshot `task.txt` *after* the round's corrections, not before (it cost lane B a
false high in round 2), and state the "the code does not exist yet is not a finding" rule
in the lane prompt from round 1 (stating it in round 3 did not stop the lane, so a weak
model needs the rule *and* a triage that rejects it).
