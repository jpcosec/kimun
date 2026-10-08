# Tester lane 1 — canon / node / pool / edge / tree / hosts (docs/v2/02 §§2–4.1, §10)

Model: claude-haiku-4-5 · Explore (read-only) · fresh context · 2026-08-29

## Promises (53) — OK=38 WEAK=13 UNTESTED=14
OK (selection): id = H(class,kind,content) frozen in nodes.edn; class/kind enter the id; canonical ordering rules (map keys, sets, vectors); floats/#inst rejected; 9 class/kind rows; edge struct and 7 types with evidence; timestamp out of edge id + idempotence; tree ULID ids; descriptor CAS object; tree object shape and merkle-root frozen in trees.edn; sibling order in hash; dirty set = path; one parent per tree; supersedes recorded; opaque replace-only; invariants 2, 4, 5, 6, 7, 12, 13, 14, 15, 16, 17.
WEAK: proposition/triple literal coverage; provenance/timestamp not in node hash (implicit only); M/G data-only (no test prevents evaluation); ownership edges in Merkle (order effect only); ref-hash mutation detection (presence only); one binding per W_i (single context tested); invariant 1 (tampering only); invariant 3 (determinism only).
UNTESTED (first slice): span materialization (§4.1); one-binding-per-W_i across contexts. UNTESTED by design (milestones 4+): stand-off layers, UAX #29 boundaries, CST/AST invariants 8–9, derived rebuild (10 beyond pool), effects (11).

## Missing negative and boundary cases
Unicode beyond é (Hangul, Arabic, emoji); empty text; nil in pool; empty actor/engine strings; evidence with extra keys; single-node tree; deep trees (100+); duplicate child; order = count boundary; empty plan; nil context; supersedes without actor; invalid status property; large objects; truncated log; span creation; reachability/garbage; object reuse across separate commits; extreme integers, special keywords.

## Top 5 by risk
1. Stand-off/span materialization untested (§4, §4.1) · 2. :span never created · 3. ref-hash mutation detection · 4. one binding per W_i · 5. CST/AST invariants (milestone 4).
