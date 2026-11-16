#!/bin/bash
# run.sh D : copia los lectores a D/kimun, confirma y corre declare read
D=$1; R=/home/robocop/proyectos/kimun
export PATH=$HOME/.local/bin:$PATH
rm -f $D/kimun/*.clj; mkdir -p $D/kimun; cp $R/runs/subagents/2026-11-16-lectores-desk/readers/*.clj $D/kimun/
(cd $D && git add -A && git -c user.name=x -c user.email=x@x commit -qm upd --allow-empty)
cd $D && s=$(date +%s.%N)
flock /tmp/kimun-bb.lock env KIMUN_POD=$R/pods/treesitter/target/release/pod-kimun-treesitter /home/robocop/bin/memcap 3 $R/bin/kimun "${@:2}"
echo "seconds $(echo "$(date +%s.%N) - $s" | bc)" >&2
