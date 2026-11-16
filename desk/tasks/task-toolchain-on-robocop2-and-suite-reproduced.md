---
sldb:
  by: agent
  at: '2026-11-16T19:08:03Z'
  authority: generated
  via: save_document_payload
id: task-toolchain-on-robocop2-and-suite-reproduced
status: draft
summary: ''
tags:
- workspace:desk
- artifact:task
current_node: ritual-execution
history: []
references:
- release.edn
- bb.edn
- runs/subagents/2026-11-16-find-genericos/coordinator/full-test.txt
- docs/v2/drafts/traspaso-2026-11-17.md
- AGENTS.md
depends_on: []
pills: []
files: []
task_type: task.external
inherits_from: []
inherit_acceptance_context: false
atoms: []
---

# Toolchain on robocop2 and suite reproduced

## Rationale

_Explain why this task exists or the business driver behind it._

On 2026-11-16 the owner moved the work on kimun to the machine robocop2. Checked by ssh that day: it has no bb (Babashka), no cargo and no ~/bin/memcap, so no test can run there. kimun is Clojure (.cljc) run by Babashka; its Markdown parser has a native Rust helper ('the pod', pods/treesitter) built with cargo; memcap is a three-line shell wrapper that caps RAM.

## Goal

_Describe the concrete result this task must produce._

On the machine robocop2 (ssh host 'robocop2', user robocop), in the clone ~/proyectos/kimun of https://github.com/jpcosec/kimun (branch main), 'bb test' runs with the tree-sitter pod and has no failures, with at least the 1081 tests of the baseline (runs/subagents/2026-11-16-find-genericos/coordinator/full-test.txt, commit e84869af).

## Scope

_State what is in scope and what is out of scope._

In scope, all on robocop2 as user robocop, no sudo needed: (1) Babashka 1.13.219 (the value of :bb in release.edn) as ~/.local/bin/bb, from the static linux-amd64 archive of the babashka GitHub release v1.13.219; (2) Rust stable through rustup, so cargo is on PATH; (3) ~/bin/memcap, executable, with exactly: '#!/usr/bin/env bash' / 'gb=3; [[ "$1" =~ ^[0-9]+$ ]] && { gb=$1; shift; }' / 'exec systemd-run --user --scope -q -p MemoryMax=${gb}G -p MemorySwapMax=0 "$@"'; (4) the pod binary pods/treesitter/target/release/pod-kimun-treesitter. Out of scope: any change to the repository's code; the paths /home/jp/... inside docs/coordinacion/validate.sh (they are adjusted by the next task that uses it).

## Implementation Path

_Outline the expected implementation route or affected surface._

cd ~/proyectos/kimun && git pull --ff-only (install the bb version that release.edn names AFTER the pull; main only moves forward, so more tests than the baseline is expected and fine). Install bb, rustup and memcap as Scope describes; the rustup installer itself appends the line that sources ~/.cargo/env to ~/.profile and ~/.bashrc (check with a new login shell that cargo --version answers; add the line . \"\$HOME\/.cargo\/env\" by hand only if it does not). If ~/proyectos/kimun is missing, clone it from the URL in Goal; if it has local changes, stop and report. Run 'bb pod' (it runs 'cargo build --release' in pods/treesitter; the first build downloads crates and takes minutes). Then four commands, each output to its own file: 'bb lint > lint.txt', '~/bin/memcap 3 bb test > full-test.txt' (about 5 minutes), 'bb oracle > oracle.txt' (needs python3, already there), 'bin/kimun include check > include-check.txt'. Put the four files in runs/subagents/YYYY-MM-DD-toolchain-robocop2/ (the day of the run) in the repo and commit them with the message 'chore(evidence): suite reproduced on robocop2' and the trailer 'Co-Authored-By: Claude Opus 5.5 <noreply@anthropic.com>'; push to origin main.

## Validation

_List the checks required before this task can close._

- bb lint exits 0
- Line 2 of full-test.txt is exactly: tree-sitter pod: available
- The last line of full-test.txt has :fail 0 and :error 0 and :test at least 1081 (baseline last line: {:test 1081, :pass 10286, :fail 0, :error 0, :namespaces 132})
- bb oracle exits 0
- bin/kimun include check prints "ok" : true with every finding at 0

## Done When

_Name the observable condition that makes the task complete._

The four output files are committed under runs/subagents/YYYY-MM-DD-toolchain-robocop2/ on main and pushed, and the five validation checks hold on them. A green suite whose line 2 says 'tree-sitter pod: NOT available' does not count: without the pod its tests are skipped silently.
