from __future__ import annotations

import json
import threading
from http.server import ThreadingHTTPServer
from pathlib import Path
from urllib.error import HTTPError
from urllib.request import Request, urlopen

from sldb.cli.serve.http_server import build_handler
from tests.store.test_cli_store import _PY_ARGS, _doc_track, _init, _model_add


JSON_HEADERS = {"Content-Type": "application/json"}


def test_serve_endpoints_use_real_store(tmp_path: Path) -> None:
    store = _build_store(tmp_path)
    pythonpath = _PY_ARGS[1]
    handler = build_handler(str(store), pythonpath, cors=False)
    server = ThreadingHTTPServer(("127.0.0.1", 0), handler)
    thread = threading.Thread(target=server.serve_forever, daemon=True)
    thread.start()
    base_url = f"http://127.0.0.1:{server.server_address[1]}"

    try:
        health_status, health = _request_json(f"{base_url}/health")
        assert health_status == 200
        assert health == {"status": "ok"}

        schema_status, schema = _request_json(f"{base_url}/schema")
        assert schema_status == 200
        assert schema["models"] == [
            {
                "id": "SimpleBook",
                "model_ref": "tests.store.test_cli_store:SimpleBook",
                "fields": [
                    {"name": "title", "kind": "string", "required": True}
                ],
            }
        ]

        graph_status, graph = _request_json(f"{base_url}/graph")
        assert graph_status == 200
        assert graph["documents"] == [
            {
                "id": "book",
                "model_name": "SimpleBook",
                "path": "book.md",
                "payload": {"title": "My Book"},
                "semantic_tags": [],
            }
        ]

        save_status, save = _request_json(
            f"{base_url}/save",
            method="POST",
            payload={"doc": "book", "payload": {"title": "Updated Title"}},
        )
        assert save_status == 200
        assert save == {"ok": True, "doc": "book"}

        graph_after_status, graph_after = _request_json(f"{base_url}/graph")
        assert graph_after_status == 200
        assert graph_after["documents"][0]["payload"] == {"title": "Updated Title"}
        assert (tmp_path / "book.md").read_text(encoding="utf-8") == "# Updated Title\n"

        missing_status, missing = _request_json(
            f"{base_url}/save",
            method="POST",
            payload={"doc": "missing", "payload": {"title": "Nope"}},
            expected_error=404,
        )
        assert missing_status == 404
        assert missing == {"ok": False, "error": "Unknown doc: missing"}
    finally:
        server.shutdown()
        server.server_close()
        thread.join(timeout=5)


def _build_store(tmp_path: Path) -> Path:
    _init(tmp_path)
    _model_add(tmp_path)
    doc = tmp_path / "book.md"
    doc.write_text("# My Book\n", encoding="utf-8")
    _doc_track(tmp_path, doc)
    return tmp_path / ".sldb"


def _request_json(
    url: str,
    *,
    method: str = "GET",
    payload: dict | None = None,
    expected_error: int | None = None,
) -> tuple[int, dict]:
    request = Request(url, method=method)
    data = None
    if payload is not None:
        data = json.dumps(payload).encode("utf-8")
        for name, value in JSON_HEADERS.items():
            request.add_header(name, value)
    try:
        with urlopen(request, data=data, timeout=5) as response:
            return response.status, json.loads(response.read().decode("utf-8"))
    except HTTPError as exc:
        if expected_error != exc.code:
            raise
        return exc.code, json.loads(exc.read().decode("utf-8"))
