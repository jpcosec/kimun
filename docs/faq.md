# Workspace FAQ

## What is this repository for?

Direction (`docs/v2/`), the knowledge base (`desk/atoms/`) and the implementation of
`kimun`: the SLDB v2 kernel plus the product surface (CLI, models, indexes, packaging).
Current status is in `docs/v2/05-estado.md`; pending items are in
`docs/v2/12-ecosistema-y-pendientes.md`.

## What is out of the current slice vs. excluded from the product?

See `docs/v2/tests/wittgenstein-manifest.md` for the full `C01–C28` manifest.

### Out of the current slice (deferred or blocked by open decisions)

Notice that only the **base S/M surfaces** (and, since 2026-11-16, declarations in Clojure read without a store: `declare *`, `kimun mcp`) are implemented today: the contextual `G` superset (`C01–C28`) is **not yet implemented** (`0 implemented`, `12 partial`, `5 direction`, `11 absent`).

- Contextual `G` engine, truth/sense evaluation (`V_i`/`S_i`), deduction and `ask` (`C04–C10`, `C12–C17`, `C21–C23`, `C25–C27`), which depend on the owner decision `D-G`, on `G1` and on the implementation (`K1`) of the `D0` identity contract (derived 2026-11-15, no longer an owner decision).
- The anchored evaluator (S4) and derived writes (S5), embeddings (`G3`), and full conversational REPL prose (`C18`; API session contracts stay tracked in `T13`, historic Node parity stays in the drawer). **Visualization as a whole is not out of scope:** a minimal view of the circuit and signals remains required under `Operación y vista` (`docs/v2/12-ecosistema-y-pendientes.md §1`).
- Opt-in global hypertensor/partition views (`C24`, subject to `memcap`) and OWL/UNL importers (`C28`, awaiting concrete consumer selection).

### Excluded from the product

- Legacy global uncontextualized kernels, abandoned code and experimental causality (`appendix_causality`) where co-occurrence is treated as intervention (`X01`, `X02`).
- Ad-hoc YAML layouts, random IDs as authority, and literal REPL prompt equivalence (`X03`, `X04`).
- Upstream bugs from W1/W2 (`X05`: auto-confirming LLM claims, ignoring `Si = unsinnig` in `ask`, losing explicit `FALSE` under qualification, anchorless facts, lossy snapshots, and unsafe retraction).
- v1 parity as a 1:1 port. Parity with sldb v1 is **suspended** (owner, 2026-11-15: «olvidémonos de
  la paridad con v1 hasta que terminemos esta "rearquitecturización"»), not closed; decision 18
  keeps v1 in v1 and lets the stores be regenerated (`docs/v2/05-estado.md §3`).

### Deferred by the owner (2026-11-15/16)

- Redoing git (file-level monitoring of kimun's own): kimun uses git for it
  (`docs/v2/drafts/sesion-2026-11-15.md §11`).
- The `G` layer, until owner decision `D-G`.
- Replacing sldb for its consumers (`RS1`, `RS2`, `C2`), after kimun is proven working
  (decision 47).

The working order is `docs/v2/drafts/plan-producto.md`.

## How does an agent work and close a task?

See `AGENTS.md`. Implementation progress requires test evidence under `runs/subagents/` and
a commit.

## How should "desk" and deskops be interpreted here?

- `kimun` is the data/document layer; `sldb` v1 and `kgdb` are frozen and superseded by it.
- The desk is a kimun store at `.kimun/`, built by `bin/kimun include build` (alias
  `bb desk`) from `kimun.include.edn`, which declares no models or documents today
  (`desk/models/*.edn` were retired on 2026-10-09). It is managed with kimun commands; the
  deskops CLI is not used in this repository. What SLDB used to generate becomes
  materializations from models (`docs/v2/drafts/sesion-2026-11-15.md §4`).
