#!/usr/bin/env bash
# Records the segments parity corpus (segments + field ownership) from the sldb v1 test suite.
#   scripts/parity/record_segments.sh [V1_REPO] [extra pytest args, e.g. test files]
# Writes test/fixtures/parity/segments.cases.jsonl.gz (one gzipped JSON line per case; layout:
# docstring of record_segments.py, old per-case directories are packed by
# scripts/parity/pack_corpus.clj); nothing is written into the v1 repo and the engine
# corpus is not touched.
set -euo pipefail
ROOT="$(cd "$(dirname "$0")/../.." && pwd)"
V1="${1:-$HOME/proyectos/hum-ecosystem/tools/sldb}"
export KIMUN_PARITY_OUT="${KIMUN_PARITY_OUT:-$ROOT/test/fixtures/parity/segments}"
export PYTHONPATH="$V1/src:$ROOT/python:$ROOT/scripts/parity"
export PYTHONDONTWRITEBYTECODE=1
# Same pin as record_engine.sh: some payloads/refs carry absolute tmp paths and case ids are content
# hashes, so the basetemp the corpus was first recorded with keeps re-recording idempotent.
BASETEMP="${KIMUN_PARITY_BASETEMP:-/tmp/pytest-of-jp/pytest-8}"
mkdir -p "$(dirname "$BASETEMP")"
cd "$V1"
if [ "$#" -gt 1 ]; then TARGETS=("${@:2}"); else TARGETS=(tests); fi
python3 -m pytest -p record_segments -p no:cacheprovider -q --no-header --basetemp="$BASETEMP" "${TARGETS[@]}" || true
echo "cases: $(gzip -dc "$KIMUN_PARITY_OUT.cases.jsonl.gz" | wc -l)"
