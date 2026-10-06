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
cd "$V1"
python3 -m pytest -p record_engine -p no:cacheprovider -q --no-header tests "${@:2}" || true
echo "cases: $(find "$KIMUN_PARITY_OUT" -mindepth 1 -maxdepth 1 -type d | wc -l)"
