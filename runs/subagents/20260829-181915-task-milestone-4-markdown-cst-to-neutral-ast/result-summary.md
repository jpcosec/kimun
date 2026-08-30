# Result summary — executor lane

- task: task-milestone-4-markdown-cst-to-neutral-ast · run_id: 20260829-181915-task-milestone-4-markdown-cst-to-neutral-ast · session: https://claude.ai/code/session_01Fy6NkZNuvnLh1SaFeNpvFU
- gate: runs/subagents/20260829-180407-…-zero-context-audit (2 rounds; round 2: A 0 high, B ready)

## Delivered (ring 2, sldb.surface.markdown.*)
- cst — total lossless segmentation (§3, §3.1) · inline — profile inline parser with delimiter stack, escapes, grapheme offsets, canonical marks (§4, §5) · ast — CST → neutral AST with recursive containers and opaque degradation (§3.2, §4, §7) · render — canonical renderer with escapes, fence sizing, list widths, quote prefixes (§6) · plan — ast->plan / markdown->plan / store->ast / store->markdown (§8) · report — addressability report (§9)
- kernel port TextSegmenter (graphemes, UAX #29) in ports.cljc, sldb.host.text (BreakIterator), sldb.host.default
- fixtures: markdown/profile.md (canonical, byte-for-byte), profile.ast.edn, outside-profile.md, outside-profile.report.edn
- tests: cst_test, inline_test, roundtrip_test (parse-render identity + idempotence over gen-ast), plan_test (store round-trip, shared leaves, not-a-document, report)
- docs/v2/04 rewritten to closed rules through the gate; promises.md §04 rows; spec2viz v2-markdown-surface

## Design change forced by this milestone (user decision)
"One parent per node per tree" could not represent lists (all items are one node) or repeated paragraphs. Trees are now trees of POSITIONS (Git-like): tree.cljc rewritten (positions with per-position hash = dirty marker, identical subtrees share objects, detach/move by path), plan ops address ownership by path (:parent/:at/:from/:to, new :detach), conflicts per parent path; edge/revision/plan/tests/fixtures updated; 02 §3.1/§3.2/§5/§5.1/§5.2/§10 and atoms updated; atom-decision-trees-are-trees-of-positions-git-like records it. trees.edn root hash unchanged (tree objects kept their format).

## Bugs found by tests on the way
list continuation absorbed a following list of another marker family; blank lines inside list items and under quote prefixes lost their indentation; `![` formed by an escaped-less `!` before a link; code marks edged with backticks; emphasis/strong sharing a boundary; link text scanning across code spans and escapes; list indentation width vs dedent width.

## Validation
bb lint ok · bb test: 99 tests, 413 assertions, 0 failures, stable ×3 (validation.log) · bb oracle 9/9 (oracle.log) · roundtrip property additionally run 10×300 during development
