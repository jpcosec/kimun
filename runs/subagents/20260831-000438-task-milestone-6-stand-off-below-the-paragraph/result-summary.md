# Result summary — milestone 6 stand-off below the paragraph

## Final validation status
- `bb lint`   → `rings and docstrings ok: 25 files`
- `bb test`   → `Ran 127 tests containing 551 assertions. 0 failures, 0 errors.`
- `bb oracle` → `9 rows, 0 mismatches`

Full combined output in `validation.log`.

## Files changed (only the task `files:` list)

| file | change | what it does |
|---|---|---|
| `src/sldb/kernel/ports.cljc` | modified | Extends the `TextSegmenter` protocol with `words` and `sentences`, each returning `[start end]` grapheme-offset ranges (UAX #29), beside the existing `graphemes`. |
| `src/sldb/host/text.cljc` | modified | Implements `words`/`sentences` for JVM (`java.text.BreakIterator` word/sentence instances) and cljs (`Intl.Segmenter`), converting every code-unit boundary to a grapheme offset via a private `grapheme-offset` map built from `graphemes`. `Segmenter` record now satisfies all three protocol methods. |
| `src/sldb/kernel/standoff.cljc` | new | Kernel namespace for stand-off below the paragraph. `layers` derives the graphemes/words/sentences of a `:sign/:text` leaf on demand and memoizes by leaf id; `grapheme-count`; `resolve-address [leaf-id start end]` returns the addressed substring virtually (no node created); `cached?` exposes memo state for tests. Depends only on `kernel/ports`, `kernel/revision`, `kernel/err`. |
| `test/sldb/kernel/standoff_test.cljc` | new | Proves (a) word/sentence offsets are grapheme-based and deterministic, with multi-UTF-16 graphemes (emoji) so grapheme≠code-unit; (b) layers memoized by leaf id (identical? cached value; `cached?` flips after first derivation); (c) a virtual address resolves to the correct substring with the store object count unchanged; (d) out-of-range, unresolved-leaf, non-text-leaf and malformed-range each return a typed `err`. |

## Scope adherence
- Only the four files in `files:` were touched.
- `:span` node kind (`node.cljc`) and its `resolves?` validation (`anchor.cljc`) left unchanged.
- No change to canonical bytes, revision, or store — oracle frozen ids still match.

## Notes
- Cache key is the leaf node id (= content hash), per the task clarification.
- BreakIterator word/sentence boundaries always fall on grapheme boundaries, so the
  code-unit→grapheme conversion is exact for every reported segment endpoint.
