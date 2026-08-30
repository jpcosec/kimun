# Task: generalize the store to Document/Codec + own exceptions (store NOT moved)

## Environment
- You are in an isolated git worktree: `/home/jp/proyectos/hum-ecosystem/tools/iso-lab/worktrees/sldb`, branch `iso-lab/sldb`. Edit ONLY here.
- Validation runs in a sealed Docker image via `tools/iso-lab/bin/lab-test.sh` from the host. Baseline that MUST be preserved: **sldb 411, kgdb 23, deskops 140**.
- deskops imports sldb at runtime (`from sldb import StructuredNLDoc`, `sldb.store.io`, `sldb.runtime.validation`), so deskops tests are the real integration proof.

## Non-negotiable constraints
- Do NOT move or physically separate the store directory. This task is ONLY: remove the store's remaining hard imports of `StructuredNLDoc`, `extract_model_data`, and `sldb.core.exceptions`, by inverting them to injection / local ownership.
- NO mocks, stubs, fake data, placeholders, TODO shortcuts. If genuinely blocked, STOP and report.
- Behavior must stay identical. The public API of `sldb` (what deskops/kgdb import) must keep working unchanged.
- Keep changes minimal and readable.

## Current couplings to remove (verified)
1. `StructuredNLDoc` (in `store/diagnostics.py:6`, `store/hashing.py:5`).
2. `extract_model_data` (in `store/semantic.py:9`, `store/hashing.py:6`, `store/query.py:5`).
3. `sldb.core.exceptions.SLDB(Store)Error` (in `store/facade.py`, `store/ops.py`, and all of `store/io/*.py` — 10 sites).

## Design (follow exactly)

### Part A — Codec abstraction (covers couplings 1 and 2)
Insight: `StructuredNLDoc` + `extract_model_data` are always used together as
`(model_type, markdown) -> payload dict`. That pair IS a codec.

1. Create `src/sldb/store/codec.py` defining a minimal Protocol:
   ```python
   from __future__ import annotations
   from typing import Any, Protocol, runtime_checkable

   @runtime_checkable
   class StoreCodec(Protocol):
       """Turns a document's raw text into a field payload for a given model type.
       The store treats model_type as opaque; the codec knows how to decode it."""
       def extract(self, model_type: Any, markdown_text: str) -> dict[str, Any]: ...
   ```
2. Provide the real default codec that wraps the existing runtime, in the SAME file:
   ```python
   class RuntimeCodec:
       def extract(self, model_type: Any, markdown_text: str) -> dict[str, Any]:
           from sldb.runtime.validation import extract_model_data
           return extract_model_data(model_type, markdown_text)

   default_codec: StoreCodec = RuntimeCodec()
   ```
   The `from sldb.runtime.validation import ...` is a LOCAL (function-body) import inside RuntimeCodec.extract, so the store module graph has no top-level dependency on runtime. This is injection with a real default — NOT a mock.
3. In `store/hashing.py`, `store/query.py`, `store/semantic.py`:
   - Remove top-level `from sldb.runtime.validation import extract_model_data` and `from sldb.models.structured_doc import StructuredNLDoc`.
   - Add a `codec: StoreCodec = default_codec` parameter (keyword, defaulted) to the functions that need extraction: `hash_fields`, `_load_doc`/`load_runtime_documents` (thread it), and the semantic function in `semantic.py`. Call `codec.extract(model_type, text)` instead of `extract_model_data(model_type, text)`.
   - Replace `Type[StructuredNLDoc]` annotations with `Any` (import `Any` from typing). `model_type` is opaque to the store now.
   - Because parameters are defaulted to `default_codec`, ALL existing callers keep working with zero changes. That is how behavior stays identical.

### Part B — diagnostics StructuredNLDoc annotation
`store/diagnostics.py` only uses `StructuredNLDoc` as a type annotation
(`Optional[Type[StructuredNLDoc]]`). Replace those annotations with `Any` and
drop the import. No behavior change (annotations only).

### Part C — store owns its exceptions WITHOUT breaking CLI `except` clauses
The CLI catches `SLDBStoreError` (imported from `sldb.core.exceptions`) in ~10
places. Exception IDENTITY must be preserved so those `except` keep matching.
The goal is to remove the store's TOP-LEVEL module import of
`sldb.core.exceptions` (the load-time coupling), NOT to change what is raised.

Do exactly this:

1. Create `src/sldb/store/exceptions.py`:
   ```python
   class StoreError(Exception):
       """Base error owned by the store layer."""
   ```
   (Owned by the store. Available for future use; raising it is optional here.)

2. Make `sldb.core.exceptions.SLDBStoreError` ALSO subclass the store's
   `StoreError`, so it keeps its current identity AND becomes a `StoreError`.
   Edit `src/sldb/core/exceptions/sldb_store_error.py`:
   ```python
   from .sldb_error import SLDBError
   from sldb.store.exceptions import StoreError

   class SLDBStoreError(SLDBError, StoreError):
       """Raised when there is an issue with the SLDB store or indexes."""
       pass
   ```
   This is core importing store (allowed: core/exceptions is not the store), giving
   `isinstance(SLDBStoreError(), StoreError) == True` for future code.

3. In every store file that has a TOP-LEVEL
   `from sldb.core.exceptions import SLDBStoreError` (store/ops.py and all
   store/io/*.py), REMOVE that top-level import. At each `raise SLDBStoreError(...)`
   site, add a LOCAL (function-body) import right before the raise:
   `from sldb.core.exceptions import SLDBStoreError`.
   - Result: identical exception object raised (CLI `except SLDBStoreError` still
     matches), but the store module graph no longer imports core.exceptions at load
     time. That is the real decoupling.
   - Do NOT change any CLI file. Do NOT widen exception scope.
   - `store/facade.py` imports `SLDBError` (not Store): apply the same local-import
     treatment there.

## Validation (run from host, capture output)
```
cd /home/jp/proyectos/hum-ecosystem/tools/iso-lab
bin/lab-test.sh          # MUST show sldb 411, kgdb 23, deskops 140
```
Decoupling proof (top-level imports gone from the store graph):
```
cd worktrees/sldb
rg -n "^from sldb.runtime|^from sldb.models.structured_doc|^from sldb.core.exceptions" src/sldb/store
# expected: EMPTY (only local/in-function imports remain, which is fine)
rg -n "sldb.runtime.validation|structured_doc|core.exceptions" src/sldb/store
# any remaining hits must be INSIDE function bodies (local imports) or the codec default, never module top-level
```

## Done when
- sldb 411, kgdb 23, deskops 140 — all unchanged, verified via lab-test.sh.
- No TOP-LEVEL imports of runtime.validation / structured_doc / core.exceptions remain in src/sldb/store.
- Store has codec.py (Codec protocol + real default) and exceptions.py (StoreError).
- Behavior identical; no CLI files changed; no mocks.

## Commit
When green, commit ON THE iso-lab/sldb BRANCH inside the worktree:
`git add -A && git commit -m "refactor(store): Codec protocol + own StoreError, drop top-level runtime/core coupling"`
Do NOT touch primary main. Do NOT promote. Report results to the supervisor.
