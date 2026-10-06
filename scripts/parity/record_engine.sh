#!/usr/bin/env bash
# Records the engine parity corpus from the sldb v1 test suite (docs/v2/tests/parity-v1.md).
#   scripts/parity/record_engine.sh [V1_REPO]
# Writes test/fixtures/parity/engine/<hash>/…; nothing is written into the v1 repo.
set -euo pipefail
ROOT="$(cd "$(dirname "$0")/../.." && pwd)"
V1="${1:-$HOME/proyectos/hum-ecosystem/tools/sldb}"
export KIMUN_PARITY_OUT="$ROOT/test/fixtures/parity/engine"
export PYTHONPATH="$V1/src:$ROOT/python:$ROOT/scripts/parity"
export PYTHONDONTWRITEBYTECODE=1
# Some cases carry absolute tmp paths (composition refs) in their payload, so the case hash
# depends on pytest's basetemp. It is pinned to the one the corpus was first recorded with:
# re-recording is then idempotent (same hashes) instead of adding a new copy of those cases.
# Still clock-dependent: tests/test_cli_v2.py::test_inbox_auto_tracks_when_model_registered stamps
# `created_at` with the wall clock, so each run adds 3 InboxNoteDoc cases that only differ from
# the recorded ones in that timestamp — delete the new copies (`git status` shows them untracked).
BASETEMP="${KIMUN_PARITY_BASETEMP:-/tmp/pytest-of-jp/pytest-8}"
mkdir -p "$(dirname "$BASETEMP")"
cd "$V1"
python3 -m pytest -p record_engine -p no:cacheprovider -q --no-header --basetemp="$BASETEMP" tests "${@:2}" || true
echo "cases: $(find "$KIMUN_PARITY_OUT" -mindepth 1 -maxdepth 1 -type d | wc -l)"
