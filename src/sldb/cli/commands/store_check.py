from __future__ import annotations
import json
from typing import Any
from sldb.cli.store_context import get_store_context

def check_store(args: Any) -> int:
    from sldb.store.diagnostics import diagnose_store
    sp, root = get_store_context(args.store, mode="readonly")
    res = diagnose_store(sp, root, pythonpath=args.pythonpath)
    return _handle_check_res(res, args.format)

def _handle_check_res(res: Any, fmt: str) -> int:
    if fmt in ("json", "yaml"):
        _print_formatted_diag(res, fmt)
        if not res.is_valid:
            raise SystemExit(1)
        return 0
    print(f"{'PASS' if res.is_valid else 'FAIL'}: store integrity")
    return 0 if res.is_valid else 1

def _print_formatted_diag(res: Any, fmt: str) -> None:
    payload = _build_diag_payload(res)
    if fmt == "json":
        print(json.dumps(payload))
    else:
        import yaml
        print(yaml.dump(payload, allow_unicode=True, sort_keys=False))

def _build_diag_payload(res: Any) -> dict[str, Any]:
    return {
        "valid": res.is_valid,
        "hash_a_ok": res.hash_a_ok,
        "models": [_build_model_payload(m) for m in res.models],
    }

def _build_model_payload(model: Any) -> dict[str, Any]:
    return {
        "name": model.name,
        "hash_b_ok": model.hash_b_ok,
        "documents": [_build_doc_payload(d) for d in model.documents],
    }

def _build_doc_payload(doc: Any) -> dict[str, Any]:
    return {
        "name": doc.name,
        "path": doc.path,
        "hash_c_ok": doc.hash_c_ok,
        "hash_d_ok": doc.hash_d_ok,
        "path_exists": doc.path_exists,
        "note": doc.note.value,
    }
