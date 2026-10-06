#!/usr/bin/env bash
# Kernel benchmark against an existing store project directory (the dir that holds .kimun/).
# Usage: scripts/bench/run_bench.sh <store-dir> [doc [field [value]]]
# Each command runs twice; the second (warm) wall time is reported. Output: "name seconds".
set -u
ROOT="$(cd "$(dirname "$0")/../.." && pwd)"
KIMUN="$ROOT/bin/kimun"
DIR="${1:?usage: run_bench.sh <store-dir> [doc [field [value]]]}"
cd "$DIR" || exit 2
LIM="${BENCH_TIMEOUT:-900}"   # per-command cap, seconds

now() { date +%s.%N; }
# timed NAME CMD...  : run twice, print second wall time
timed() {
  local name="$1"; shift
  timeout "$LIM" "$@" >/dev/null 2>&1
  local t0 t1; t0=$(now)
  timeout "$LIM" "$@" >/dev/null 2>/tmp/kimun-bench-err.$$; local rc=$?
  t1=$(now)
  printf '%s %.3f' "$name" "$(echo "$t1 - $t0" | bc -l)"
  [ "$rc" -ne 0 ] && printf ' (exit %s)' "$rc"
  printf '\n'
}

DOC="${2:-}"
if [ -z "$DOC" ]; then
  # names of documents (after the "data" key), pick the middle one
  DOC=$(timeout "$LIM" "$KIMUN" docs list 2>/dev/null | awk '/"data"/{d=1} d && /^  *"name" *:/{print}' \
        | sed 's/.*"name" *: *"\([^"]*\)".*/\1/' | awk '{a[NR]=$0} END{print a[int((NR+1)/2)]}')
fi
# a writer the store grants: BENCH_ACTOR, else the first non-kimun/ actor of store.edn :capabilities
ACTOR="${BENCH_ACTOR:-$(bb -e '(->> (slurp ".kimun/store.edn") clojure.edn/read-string :capabilities keys (remove #(clojure.string/starts-with? % "kimun/")) sort first print)')}"
FIELD="${3:-owner}"
VALUE="${4:-benchvalue}"
echo "# store $(pwd) doc=$DOC field=$FIELD actor=$ACTOR"
timed docs_list "$KIMUN" docs list
timed docs_show "$KIMUN" docs show "$DOC"
timed fields_set "$KIMUN" fields set "$DOC" "$FIELD" "$VALUE" --actor "$ACTOR"
timed find "$KIMUN" find "${BENCH_TERM:-replay}"
timed stores_check "$KIMUN" stores check
if "$KIMUN" --help 2>&1 | grep -q 'stores verify'; then
  timed stores_verify "$KIMUN" stores verify
else
  echo "stores_verify skipped"
fi
echo "bytes_kimun $(du -sb .kimun | cut -f1)"
echo "objects $(find .kimun/objects -type f | wc -l)"
echo "log_lines $(wc -l < .kimun/log.edn)"
rm -f /tmp/kimun-bench-err.$$
