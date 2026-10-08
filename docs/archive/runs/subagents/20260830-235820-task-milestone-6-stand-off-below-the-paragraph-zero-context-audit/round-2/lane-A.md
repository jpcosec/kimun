# Lane A — internal coherence (round 2)

## Summary
Task bundle task-milestone-6-stand-off-below-the-paragraph passes internal coherence audit against docs/v2 sections 4/4.1/6.2 and the four epoch:v2 atoms. Code references are accurate; claims about content-addressed identity, caching by leaf id, and the exit criterion are all consistent with foundational docs.

## Detailed findings

### Section and code references
- **docs/v2/02 §4** (Stand-off below paragraph): Exists at line 225. ✓
- **docs/v2/02 §4.1** (Stand-off addresses and materialization): Exists at line 250. ✓
- **docs/v2/02 §6.2** (Anchor states): Exists at line 473. ✓
- **src/sldb/kernel/node.cljc lines 19-23** (span kind validation): Verified; shows `{:leaf <id> :range [a b]}` shape with integer bounds and `<= 0 a b`. ✓
- **src/sldb/kernel/anchor.cljc lines 68-79** (resolves? for spans): Verified; checks leaf resolution recursively, validates text kind, and bounds `end` against grapheme count. ✓

### Content-addressed identity claim
- **Task states**: "in this kernel a node id is its content hash, so leaf-id and leaf-hash denote the same value"
- **docs/v2/02 §2.1**: `id(nodo) = H(canonical-bytes({:class :kind :content}))` — identity equals hash of canonical bytes.
- **docs/v2/02 §1 table**: "identidad = hash del contenido canónico"
- **Verification**: Accurate. Node id is deterministically the hash of canonical content; leaf nodes are :sign/:text, so leaf-id == leaf-hash. ✓

### Caching strategy
- **Task states**: "memoized by the leaf node id. Note that… the cache key is the leaf node id"
- **docs/v2/02 §4**: "Las capas deterministas (UAX #29) se… cachean por hash de hoja"
- **docs/v2/02 §4.1**: "Las capas deterministas (grafemas, palabras, oraciones) son arrays de offsets cacheados por `leaf-id`, no nodos ni aristas"
- **Atom 2** (stand-off-addresses-are-virtual-until-referenced): "Deterministic layers (graphemes, words, sentences) are offset arrays cached by leaf id"
- **Verification**: Consistent. All three sources (task, docs, atom) agree cache key is leaf id. ✓

### Offset units
- **Task**: "in grapheme offsets" and "with offsets counted in UAX 29 grapheme clusters"
- **Atom 2**: "in UAX #29 grapheme offsets"
- **docs/v2/02 §4**: offsets described as "grafema"
- **Verification**: Consistent across all sources. ✓

### Four atoms alignment
- **atom-stand-off-annotation-below-the-paragraph** (epoch:v2): Describes typed span layers cached by leaf hash, no materialized nodes. ✓
- **atom-stand-off-addresses-are-virtual-until-referenced** (epoch:v2): Specifies virtual address format `[leaf-id start end]`, cached by leaf id, no implicit materialization. ✓
- **atom-anchor-state-is-derived-and-computed-per-endpoint** (epoch:v2): Covers span resolution by "checking the range against its grapheme count". ✓
- **atom-inline-marks-live-on-the-block-text-leaves-stay-plain** (epoch:v2): Establishes plain text leaves; marks live on block. ✓
- **Verification**: All four atoms tagged epoch:v2; all are cited and internally consistent with task scope. ✓

### Exit criterion
- **Task states**: "Exit criterion from 02 section 9 row 6 is an address down to the grapheme with no materialized nodes"
- **docs/v2/02 §9 row 6** (Hito 6, Stand-off): "dirección hasta el grafema sin nodos materializados"
- **Verification**: Exact match. ✓

### Files and dependencies
- **files list**: ports.cljc (extend TextSegmenter), text.cljc (implement words/sentences), standoff.cljc (new derivation namespace), standoff_test.cljc (new tests)
- **Task specifies**: standoff.cljc depends only on kernel/ports, kernel/revision, kernel/err
- **Rationale**: TextSegmenter protocol lives in ports; store/revision queries live in revision; error handling in err. No circular deps. ✓

### Span kind — existing and verified
- **Task states**: "The span node kind and its per-endpoint anchor-state validation (section 6.2) already exist and stay as-is"
- **Code evidence**: node.cljc lines 19-23 show :span kind exists with proper shape validation
- **Code evidence**: anchor.cljc lines 68-79 show span resolution exists and recurses to leaf grapheme count
- **Verification**: Both exist and are verified. Out-of-scope claim is accurate. ✓

### No implicit span creation
- **Task states**: "the kernel never creates spans implicitly during the validation"
- **Verification**: No `kind :span` creation in revision.cljc; spans only created via `:add-node` in plans. ✓

### NFC normalization
- **Task scope**: addresses are "over a leaf NFC text"
- **docs/v2/02 §2.1**: canonical strings are normalized to NFC before hashing
- **docs/v2/02 §4**: "La hoja canónica de un documento es el texto plano… con offsets de grafema"
- **Verification**: Consistent; leaf text is canonical NFC. ✓

## Conclusion
No high-severity or medium-severity contradictions found. Task bundle is internally coherent with docs/v2/02 sections 4/4.1/6.2 and the four epoch:v2 atoms. Code references are accurate, claims are well-founded, and dependencies are minimal and correct.

---

```acceptance-report
{
  "criteriaSatisfied": [
    {
      "id": "criterion-1",
      "status": "satisfied",
      "evidence": "12 verification points: accurate section/code references, consistent content-addressed identity claim, cache strategy alignment, offset unit consistency, four atoms coherent and aligned, exit criterion matches docs/v2 §9 row 6, files and dependencies correct, span kind exists and verified, no implicit span creation, NFC normalization consistent"
    }
  ],
  "changedFiles": [],
  "testsAddedOrUpdated": [],
  "commandsRun": [],
  "validationOutput": [
    "Read-only audit lane; no files modified"
  ],
  "residualRisks": [
    "none"
  ],
  "noStagedFiles": true,
  "diffSummary": "No changes; read-only audit",
  "reviewFindings": [
    "no blockers: task bundle is coherent"
  ],
  "manualNotes": "Round 2 audit confirms all corrections from round 1 are accurate. Standoff.cljc does not exist yet (expected for implementation task). All code references verified and accurate. Task is ready for execution."
}
```
