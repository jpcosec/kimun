#!/bin/bash
# usage: validate.sh <worktree> <evidence-dir> <ns for directed...>
cd "$1" || exit 9; shift
export KIMUN_POD=/home/robocop/proyectos/kimun/pods/treesitter/target/release/pod-kimun-treesitter
E=$1; shift; mkdir -p $E
L="flock /tmp/kimun-bb.lock"
$L /home/robocop/bin/memcap 3 bb lint > $E/lint.txt 2>&1 || { echo LINT FAIL; tail $E/lint.txt; exit 1; }
$L /home/robocop/bin/memcap 3 bb -e "(require 'clojure.test) (def nss '[$*]) (apply require nss) (let [r (apply clojure.test/run-tests nss)] (System/exit (if (zero? (+ (:fail r) (:error r))) 0 1)))" > $E/directed.txt 2>&1; D=$?; echo "directed $D"; tail -3 $E/directed.txt; [ $D -eq 0 ] || { grep -n "FAIL\|ERROR" -A8 $E/directed.txt | cut -c1-300 | head -50; exit 2; }
$L /home/robocop/bin/memcap 3 bb test > $E/full-test.txt 2>&1; echo "full exit $?" | tee $E/full-exit.txt; sed -n 2p $E/full-test.txt; tail -1 $E/full-test.txt; grep -n "^FAIL\|^ERROR" -A6 $E/full-test.txt | cut -c1-300 | head -30
$L /home/robocop/bin/memcap 3 bb oracle > $E/oracle.txt 2>&1; echo "oracle $?"; tail -1 $E/oracle.txt
$L bin/kimun include check > $E/include-check.txt 2>&1; echo "include $?"
