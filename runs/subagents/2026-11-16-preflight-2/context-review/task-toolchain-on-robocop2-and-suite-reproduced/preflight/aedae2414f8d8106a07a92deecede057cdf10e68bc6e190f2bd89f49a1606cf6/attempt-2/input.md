FILE:desk/tasks/task-toolchain-on-robocop2-and-suite-reproduced.md
---
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

On 2026-11-16 the owner moved the work on kimun to the machine robocop2. Checked by ssh that day: it has no bb (Babashka), no cargo and no ~/bin/memcap, so no test can run there. kimun is Clojure (.cljc) run by Babashka; its Markdown parser has a native Rust helper ('the pod', pods/treesitter) built with cargo; memcap is a 5-line wrapper that caps RAM.

## Goal

_Describe the concrete result this task must produce._

On the machine robocop2 (ssh host 'robocop2', user robocop), in the clone ~/proyectos/kimun of https://github.com/jpcosec/kimun (branch main), 'bb test' runs with the tree-sitter pod and has no failures, with at least the 1081 tests of the baseline (runs/subagents/2026-11-16-find-genericos/coordinator/full-test.txt, commit e84869af).

## Scope

_State what is in scope and what is out of scope._

In scope, all on robocop2 as user robocop, no sudo needed: (1) Babashka 1.13.219 (the value of :bb in release.edn) as ~/.local/bin/bb, from the static linux-amd64 archive of the babashka GitHub release v1.13.219; (2) Rust stable through rustup, so cargo is on PATH; (3) ~/bin/memcap, executable, with exactly: '#!/usr/bin/env bash' / 'gb=3; [[ "$1" =~ ^[0-9]+$ ]] && { gb=$1; shift; }' / 'exec systemd-run --user --scope -q -p MemoryMax=${gb}G -p MemorySwapMax=0 "$@"'; (4) the pod binary pods/treesitter/target/release/pod-kimun-treesitter. Out of scope: any change to the repository's code; the paths /home/jp/... inside docs/coordinacion/validate.sh (they are adjusted by the next task that uses it).

## Implementation Path

_Outline the expected implementation route or affected surface._

cd ~/proyectos/kimun && git pull --ff-only. Install the three tools. Run 'bb pod' (it runs 'cargo build --release' in pods/treesitter; first build downloads crates and takes minutes). Run '~/bin/memcap 3 bb test > /tmp/full-test.txt 2>&1' (about 5 minutes; it runs 'bb lint' first). Run 'bb oracle' (needs python3, already there) and 'bin/kimun include check'. Copy the four outputs to runs/subagents/<today>-toolchain-robocop2/ in the repo and commit them with the message 'chore(evidence): suite reproduced on robocop2' and the trailer 'Co-Authored-By: Claude Opus 5.5 <noreply@anthropic.com>'; push to origin main.

## Validation

_List the checks required before this task can close._

- Line 2 of the bb test output is exactly: tree-sitter pod: available
- The last line of the bb test output has :fail 0 and :error 0 and :test at least 1081 (baseline last line: {:test 1081, :pass 10286, :fail 0, :error 0, :namespaces 132})
- bb oracle exits 0
- bin/kimun include check prints "ok" : true with every finding at 0

## Done When

_Name the observable condition that makes the task complete._

The four validation outputs are committed under runs/subagents/<date>-toolchain-robocop2/ on main and pushed. A green suite whose line 2 says 'tree-sitter pod: NOT available' does not count: without the pod its tests are skipped silently.

FILE:instruccion
Preflight solo-TaskDoc de la tarea task-toolchain-on-robocop2-and-suite-reproduced: reformula el encargo tal como lo leería un ejecutor sin ningún otro contexto, lista ambigüedades con cita textual del input y da el DRY-RUN sin ejecutar nada. No ejecutes nada.

