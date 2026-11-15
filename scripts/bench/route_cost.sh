#!/usr/bin/env bash
# Route-tree cost (K2a) on an existing gen_store project: what one `docs update` writes and how long
# `stores sense` takes. Usage: scripts/bench/route_cost.sh <store-dir> <n-docs> [reps]
# Every kimun call takes /tmp/kimun-bb.lock on its own (flock per call, memcap 3); lock wait time is therefore inside the timings only if another bb holds it: run when the lock is free or see lock_wait in the notes.
# Output lines: "update <rep> secs=.. new_objects=.. new_bytes=.. route_nodes=.. route_bytes=.. root_bytes=.. node_bytes=a,b,.. log_growth=.. cache_new=.."
#               "sense_cold <rep> secs=.." (cache/ moved away first, restored after) and "sense_warm <rep> secs=..".
set -u
ROOT="$(cd "$(dirname "$0")/../.." && pwd)"
DIR="${1:?store dir}"; N="${2:?n docs}"; REPS="${3:-3}"
POD=/home/jp/proyectos/kimun/pods/treesitter/target/release/pod-kimun-treesitter
K() { flock /tmp/kimun-bb.lock env KIMUN_POD=$POD /home/jp/bin/memcap 3 "$ROOT/bin/kimun" "$@"; }
# KT: like K but the clock runs inside the lock, so lock wait is excluded; elapsed seconds -> $EL
KT() {
  EL=$(flock /tmp/kimun-bb.lock bash -c 't0=$(date +%s.%N); env KIMUN_POD='"$POD"' /home/jp/bin/memcap 3 "$0" "$@" >/dev/null 2>&1; rc=$?; t1=$(date +%s.%N); echo "$(echo "$t1 - $t0" | bc -l) $rc"' "$ROOT/bin/kimun" "$@")
  RC=${EL#* }; EL=${EL% *}
}
cd "$DIR" || exit 2
S=.kimun
DOC=$(printf 'note-%05d' $((N / 2)))
for r in $(seq 1 "$REPS"); do
  find $S/objects -type f | sort > /tmp/med1-before.$$
  find $S/cache -type f 2>/dev/null | sort > /tmp/med1-cbefore.$$
  l0=$(stat -c %s $S/log.edn)
  KT docs update "$DOC" --set owner="bench$r"; rc=$RC
  find $S/objects -type f | sort > /tmp/med1-after.$$
  find $S/cache -type f 2>/dev/null | sort > /tmp/med1-cafter.$$
  new=$(comm -13 /tmp/med1-before.$$ /tmp/med1-after.$$)
  nb=0; rn=0; rb=0; list=""
  for f in $new; do
    sz=$(stat -c %s "$f"); nb=$((nb + sz))
    if grep -q 'kimun\.route/[12]' "$f"; then rn=$((rn + 1)); rb=$((rb + sz)); list="$list$sz,"; fi
  done
  rootid=$(grep -o ':routes-object "[0-9a-f]*"' $S/heads.edn | sed 's/.*"\(.*\)"/\1/')
  rootb=$(for f in $new; do case "$f" in *"$rootid"*) stat -c %s "$f";; esac; done)
  printf 'update %s secs=%.3f rc=%s new_objects=%s new_bytes=%s route_nodes=%s route_bytes=%s root_bytes=%s node_bytes=%s log_growth=%s cache_new=%s\n' \
    "$r" "$EL" "$rc" "$(echo "$new" | grep -c .)" "$nb" "$rn" "$rb" "${rootb:-?}" "${list%,}" \
    "$(( $(stat -c %s $S/log.edn) - l0 ))" "$(comm -13 /tmp/med1-cbefore.$$ /tmp/med1-cafter.$$ | grep -c .)"
done
for r in $(seq 1 "$REPS"); do
  mv $S/cache /tmp/med1-cache-aside.$$ 2>/dev/null
  KT stores sense --once
  rm -rf $S/cache; mv /tmp/med1-cache-aside.$$ $S/cache 2>/dev/null
  printf 'sense_cold %s secs=%.3f\n' "$r" "$EL"
done
K stores sense --once >/dev/null 2>&1
for r in $(seq 1 "$REPS"); do
  KT stores sense --once
  printf 'sense_warm %s secs=%.3f\n' "$r" "$EL"
done
echo "route_schema $(grep -o ':route-schema [0-9]' $S/store.edn || echo 1) objects $(find $S/objects -type f | wc -l) bytes_kimun $(du -sb $S | cut -f1)"
rm -f /tmp/med1-*.$$
