#!/usr/bin/env bash
# Matrix driver for the K2a route-tree measurement (MED1): for each N, layout and route schema it
# generates a synthetic store under $WORK (default /tmp/med1-s), measures it with route_cost.sh, and
# deletes it before the next one. Usage: scripts/bench/route_matrix.sh <out-dir> <reps> <N>...
# ONLY=regex restricts the tags (nN-layout-sS) that run. Each generation and each measurement takes /tmp/kimun-bb.lock on its own (a few minutes at most).
set -u
ROOT="$(cd "$(dirname "$0")/../.." && pwd)"
OUT="${1:?out dir}"; REPS="${2:?reps}"; shift 2
WORK="${WORK:-/tmp/med1-s}"
POD=/home/jp/proyectos/kimun/pods/treesitter/target/release/pod-kimun-treesitter
mkdir -p "$OUT"
for N in "$@"; do
  for layout in flat dirs20 nested; do
    for schema in 1 2; do
      tag="n$N-$layout-s$schema"; d="$WORK/$tag"
      [[ -n "${ONLY:-}" && ! "$tag" =~ $ONLY ]] && continue
      rm -rf "$d"; mkdir -p "$d"
      t0=$(date +%s)
      ( cd "$ROOT" && flock /tmp/kimun-bb.lock env KIMUN_POD=$POD /home/jp/bin/memcap 3 bb scripts/bench/gen_store.clj "$d" "$N" "$layout" "$schema" ) > "$OUT/gen-$tag.txt" 2>&1
      echo "gen $tag exit=$? wall_incl_lock_wait=$(( $(date +%s) - t0 ))s" | tee -a "$OUT/progress.txt"
      flock /tmp/kimun-bb.lock "$ROOT/scripts/bench/route_cost.sh" "$d" "$N" "$REPS" > "$OUT/cost-$tag.txt" 2>&1
      echo "cost $tag exit=$?" | tee -a "$OUT/progress.txt"
      rm -rf "$d"
    done
  done
done
