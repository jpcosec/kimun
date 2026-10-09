"""Read-only probes of the Matrix-SHRDLU engine; no network or source writes."""
import json
import sys
from pathlib import Path

sys.dont_write_bytecode = True
root = Path('/home/jp/proyectos/wittgenstein/matrix-shrdlu-spec')
sys.path[:0] = [str(root), str(root / 'matrix')]
from src.operational_model import (
    Fact, LiSpace, LogicalSystem, Name, Proposition, Relation, SenseValue,
    Symbol, Thing, TruthValue, WiGame,
)
from pipeline.query import ask
from pipeline.ingest import _evidence
from pipeline.decompose import Declaration

system = LogicalSystem()
system.register_relation(Relation('r', 'r'))
for sid in ('a@ctx', 'b@ctx'):
    system.register_thing(Thing(Symbol(sid), Name(sid)))
world = WiGame(wigame_id='wigame:ctx:r', li=LiSpace(
    li_id='li:ctx:r', axis_a=['a@ctx'], axis_b=['b@ctx'], relation_id='r'))
system.register_wigame(world)
fact = Fact(Proposition('r', 'a@ctx', 'b@ctx', world.wigame_id), TruthValue.TRUE)
system.add_fact(fact)
print(json.dumps({'probe': 'fact_without_citation', 'accepted': True,
                  'evidence': fact.evidence}, ensure_ascii=False))
world.set_sense('a@ctx', 'b@ctx', SenseValue.UNSINNIG)
answer = ask(system, 'r', 'a@ctx')
print(json.dumps({'probe': 'ask_after_unsinnig', 'sense': world.Si.get('a@ctx', 'b@ctx'),
                  'answer': answer.as_dict(), 'truth': answer.truth_for(None)}, ensure_ascii=False))
world.set_sense('a@ctx', 'b@ctx', SenseValue.SINNVOLL)
world.Vi.set('a@ctx', 'b@ctx', TruthValue.FALSE.value)
fact.truth = TruthValue.FALSE
answer = ask(system, 'r', 'a@ctx', 'b')
print(json.dumps({'probe': 'ask_explicit_false', 'cell': world.Vi.get('a@ctx', 'b@ctx'),
                  'truth': answer.truth_for('b')}, ensure_ascii=False))
print(json.dumps({'probe': 'llm_ingest_evidence', 'evidence': _evidence(
    Declaration(ctx_id='ctx', relation='r', a='a@ctx', b='b@ctx', span=(0, 1), source='llm',
                a_sign='a', b_sign='b', sentence_index=0),
    'source.md')}, ensure_ascii=False))
