from typing import Any
from sldb.cli.store_context import get_store_context
from sldb.cli.model_utils import resolve_model_ref
from sldb.store.query import load_runtime_documents

def resolve_doc_target(target: str, store_arg: str | None, pythonpath: str | None) -> tuple[Any, str]:
    parts = _parse_target_parts(target)
    store_path, _ = get_store_context(store_arg)
    docs = load_runtime_documents(store_path, resolve_model_ref, pythonpath)
    candidates = _find_doc_candidates(docs, parts)
    return _select_doc_candidate(candidates, target)

def _parse_target_parts(target: str) -> list[str]:
    normalized = target.strip("/")
    if not normalized.startswith("docs/"): raise SystemExit("Field targets must start with 'docs/'.")
    parts = normalized.removeprefix("docs/").split("/")
    if len(parts) < 2: raise SystemExit("Expected docs/<doc>/<field>...")
    return parts

def _find_doc_candidates(docs: list[Any], parts: list[str]) -> list[tuple[Any, str]]:
    c, joined = [], "/".join(parts)
    for doc in docs:
        if joined.startswith(two := f"{doc.model_name}/{doc.name}/"):
            c.append((doc, joined[len(two):]))
        elif joined.startswith(one := f"{doc.name}/"):
            c.append((doc, joined[len(one):]))
    return c

def _select_doc_candidate(c: list[tuple[Any, str]], target: str) -> tuple[Any, str]:
    if not c: raise SystemExit(f"Unknown docs field target: {target}")
    if len(c) > 1: c = [i for i in c if i[1]] or c
    if len(c) != 1: raise SystemExit(f"Ambiguous docs field target: {target}")
    doc, field = c[0]
    if not field: raise SystemExit("Missing field path.")
    return doc, field.replace("/", ".")

def parse_model_target(target: str) -> tuple[str, str | None]:
    parts = target.removeprefix("models/").split("/", 1)
    return parts[0], parts[1].replace("/", ".") if len(parts) > 1 else None
