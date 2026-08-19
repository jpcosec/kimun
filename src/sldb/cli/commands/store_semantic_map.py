from __future__ import annotations
from typing import Any
from sldb.cli.store_context import get_store_context
from sldb.store.io import load_semantic_dag, save_semantic_dag
from sldb.store.models import SemanticDAG

def semantic_map_store(args: Any) -> int:
    sp, _root = get_store_context(args.store)
    dag = load_semantic_dag(sp)
    _add_equivalence(dag, args.concept_a, args.concept_b)
    _add_equivalence(dag, args.concept_b, args.concept_a)
    save_semantic_dag(sp, dag)
    print(f"Mapped {args.concept_a} <-> {args.concept_b}")
    return 0

def _add_equivalence(dag: SemanticDAG, c1: str, c2: str) -> None:
    if c1 not in dag.equivalences:
        dag.equivalences[c1] = []
    if c2 not in dag.equivalences[c1]:
        dag.equivalences[c1].append(c2)
