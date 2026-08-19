import json
import yaml
from typing import Any
from sldb.cli.dict_utils import deep_get
from sldb.cli.store_context import get_store_context
from sldb.cli.model_utils import registered_model
from sldb.cli.commands.fields_target import parse_model_target, resolve_doc_target
from sldb.cli.graph_ops import query_field_records

def show_field(args: Any) -> int:
    target = args.target.strip("/")
    if target.startswith("models/"): return _show_model(target, args)
    return _show_doc(target, args)

def _show_model(target: str, args: Any) -> int:
    model_name, field_path = parse_model_target(target)
    model_type, _, _ = registered_model(get_store_context(args.store)[0], model_name, args.pythonpath)
    payload = _build_model_payload(model_name, model_type, field_path)
    print(yaml.safe_dump(payload, sort_keys=False, allow_unicode=True))
    return 0

def _build_model_payload(model_name: str, model_type: Any, field_path: str | None) -> dict[str, Any]:
    if field_path is None: return _build_model_payload_all(model_name, model_type)
    return _build_model_payload_field(model_name, model_type, field_path)

def _build_model_payload_all(model_name: str, model_type: Any) -> dict[str, Any]:
    fields = []
    for name, field in model_type.model_fields.items():
        fields.append({
            "name": name,
            "description": field.description or "",
            "annotation": getattr(field.annotation, "__name__", repr(field.annotation)),
        })
    return {"model": model_name, "fields": fields}

def _build_model_payload_field(model_name: str, model_type: Any, field_path: str) -> dict[str, Any]:
    field = model_type.model_fields[field_path]
    return {
        "model": model_name,
        "field": field_path,
        "description": field.description or "",
        "annotation": getattr(field.annotation, "__name__", repr(field.annotation)),
    }

def _show_doc(target: str, args: Any) -> int:
    runtime_doc, field_path = resolve_doc_target(target, args.store, args.pythonpath)
    value = deep_get(runtime_doc.payload, field_path)
    _print_result({"value": value}, args.format)
    return 0

def _print_result(data: dict[str, Any], fmt: str) -> None:
    if fmt == "yaml": print(yaml.safe_dump(data, sort_keys=False, allow_unicode=True))
    else: print(json.dumps(data, indent=2))

def query_field(args: Any) -> int:
    rows = query_field_records(args.store, args.pythonpath, args.field, include_linked=args.global_scope)
    _print_result({"results": rows}, args.format)
    return 0 if rows else 1
