from __future__ import annotations

from http.server import BaseHTTPRequestHandler
from pathlib import Path
from typing import Any
from urllib.parse import urlparse

from sldb.cli.commands.fields_save import save_payload
from sldb.cli.model_utils import resolve_model_ref
from sldb.cli.serve.responses import read_json_body, send_json, send_common_headers
from sldb.cli.serve.schema import schema_models
from sldb.store.export import export_kgdb_semantic_payload
from sldb.store.query import load_runtime_documents


def dispatch(
    handler: BaseHTTPRequestHandler,
    method: str,
    store_path: Path,
    project_root: Path,
    pythonpath: str,
) -> tuple[dict[str, Any], int]:
    route = urlparse(handler.path).path
    if method == "GET":
        return dispatch_get(route, store_path, project_root, pythonpath)
    if method == "POST":
        return dispatch_post(handler, route, store_path, pythonpath)
    return {"ok": False, "error": "Method not allowed"}, 405


def dispatch_get(
    route: str,
    store_path: Path,
    project_root: Path,
    pythonpath: str,
) -> tuple[dict[str, Any], int]:
    handlers = {"/health": health_payload, "/schema": lambda *_: {"models": schema_models(store_path, pythonpath)}, "/graph": lambda *_: {"documents": graph_documents(store_path, pythonpath)}, "/kgdb/snapshot": lambda *_: export_kgdb_semantic_payload(store_path, project_root, resolve_model_ref, pythonpath)}
    if route in handlers:
        return handlers[route](route), 200
    return {"ok": False, "error": f"Unknown route: {route}"}, 404


def health_payload(_: str) -> dict[str, Any]:
    return {"status": "ok"}


def graph_documents(store_path: Path, pythonpath: str) -> list[dict[str, Any]]:
    docs = load_runtime_documents(store_path, resolve_model_ref, pythonpath)
    return [serialize_document(doc) for doc in docs]


def serialize_document(doc: Any) -> dict[str, Any]:
    return {"id": doc.name, "model_name": doc.model_name, "path": str(doc.path), "payload": doc.payload, "semantic_tags": doc.semantic_tags}


def dispatch_post(
    handler: BaseHTTPRequestHandler,
    route: str,
    store_path: Path,
    pythonpath: str,
) -> tuple[dict[str, Any], int]:
    if route != "/save":
        return {"ok": False, "error": f"Unknown route: {route}"}, 404
    request = read_json_body(handler)
    return save_document(request, store_path, pythonpath)


def save_document(
    request: dict[str, Any],
    store_path: Path,
    pythonpath: str,
) -> tuple[dict[str, Any], int]:
    doc_name = request.get("doc")
    payload = request.get("payload")
    ensure_save_request(doc_name, payload)
    runtime_doc = find_runtime_document(store_path, pythonpath, doc_name)
    if runtime_doc is None:
        return {"ok": False, "error": f"Unknown doc: {doc_name}"}, 404
    save_payload(runtime_doc, payload, str(store_path), pythonpath)
    return {"ok": True, "doc": doc_name}, 200


def ensure_save_request(doc_name: Any, payload: Any) -> None:
    if not isinstance(doc_name, str):
        raise ValueError("Expected 'doc' to be a string")
    if not isinstance(payload, dict):
        raise ValueError("Expected 'payload' to be an object")


def find_runtime_document(store_path: Path, pythonpath: str, doc_name: str) -> Any:
    docs = load_runtime_documents(store_path, resolve_model_ref, pythonpath)
    return next((doc for doc in docs if doc.name == doc_name), None)


def handle_options(handler: BaseHTTPRequestHandler, cors: bool) -> None:
    if not cors:
        send_json(handler, 404, {"ok": False, "error": "CORS disabled"}, cors)
        return
    handler.send_response(204)
    send_common_headers(handler, 0, cors)
    handler.end_headers()
