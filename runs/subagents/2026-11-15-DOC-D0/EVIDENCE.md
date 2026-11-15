# DOC-D0 — D0 stops being a question for the owner (docs only)

Base: `main` at `c0fe5aff`. No file under `src/` changes. K1 is not implemented here.

## Why

The owner corrected the council (relayed by the coordinator, 2026-11-15): the premises of node
identity were already fixed — identity is neither the hash nor the structural anchoring;
reality and fact are different; two stages, anchoring then the detected fact; follow the marked
topologies in time; the relational fact `(R A B)`; Zettelkasten as the source — and must not
be asked for again. The documents still said «D0 ABIERTO», «decisión del dueño», «pendiente de
confirmar», and listed K1, K2b, S2 and M1 as waiting for the owner.

## The contract

`runs/research/2026-11-15/concilio/d0-contrato-sintesis.md` (v5), committed here with what it
was built from: two independent derivations (`d0-contrato-claude.md`, `d0-contrato-pi.md`), a
second reading by Pi (`d0-segunda-lectura-pi.md`) and five rounds of review by the coordinator.

Its rules I1–I13 are **inferences of the council** from the owner's premises, and are labelled
so; none is the owner's text. In short: the unit is the detected fact, with an identity of its
own minted when it is marked; the anchoring is its current reference to reality, with no node
identity; a new observation is not a new unit; absence does not end a unit (the sensor reports
`missing`; closing or re-anchoring is declared); copy, split and merge create new units with
declared lineage; the sensor proposes and never asserts continuity.

Left out on purpose, as future design of G and not a blocker of K1: the base of the recursion
of `(R A B)` and the logic of truth (annex A of the synthesis, no rule adopted).

## What changed

- `docs/v2/drafts/preguntas-abiertas.md`: a dated note; the D0 row kept as history, struck.
- `docs/v2/14-juego-del-lenguaje.md`: a dated note after the owner's three; §21 retitled, its
  status rewritten, the contract summarised, and «qué queda abierto» replaced by what is
  pending after D0 (implementation, technical decisions, design of G); decision 46 in §12;
  the entries of §13 that said «bloqueado por D0».
- `docs/v2/05-estado.md`: decision 46; the sentence that listed D0 as open for the owner.
- `docs/v2/12-ecosistema-y-pendientes.md §1`: header note; rows D0, K1, K2b, S2, M1 and G1; the
  closing paragraph. K1 is now «implementación pendiente del contrato D0», with the id format
  as a technical decision of the team.
- `AGENTS.md`: the reading order named D0 as a decision only the owner can make; it now names
  D-G.
- `test/kimun/doc14_contract_test.cljc`: it pinned «D0 ABIERTO» and «bloqueada por `K1` /
  `D0`». It now pins the contract markers of §21, that the rules are council inferences, and
  that the obsolete block is gone.

The owner's three notes of 2026-11-14 at the head of `14` are untouched.

## Validation (through `~/bin/memcap`)

- `lint.txt`: rings and docstrings ok, 104 files.
- `targeted-tests.txt`: `kimun.doc14-contract-test`, `kimun.spec16-models-contract-test`,
  `kimun.wittgenstein-manifest-test`, `kimun.lint-test` — 17 tests, 1235 assertions, 0 failures, exit 0.
- `include-check.json`: ok.
- The full suite was not run: no file under `src/` changed, and the tests that read the edited
  documents are the first two above.

## Not changed, and still saying the old thing

- `docs/v2/00-nucleo.md §3.3` («D0, abierta») and the line of `docs/v2/README.md` that says the
  nucleus names D0 as open.
- `docs/v2/16-modelos-como-subgrafos.md` and its fixture
  (`test/fixtures/models/spec16_examples.edn`, `:blocked-by` with `:D0`), pinned by
  `kimun.spec16-models-contract-test`.
- About thirty mentions of «`K1`/`D0`» across `14`: the new note at its head says how to read
  them (they wait for K1).

## Limits

- The contract is the council's reading; the owner has not read its text.
- Agy did not take part. Pi reviewed v4; its six objections are in v5, which Pi has not seen.
- The external Zettelkasten sources were not reread in the original.
