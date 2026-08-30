from __future__ import annotations

from http.server import BaseHTTPRequestHandler
from pathlib import Path
from typing import Any

from sldb.cli.store_context import get_store_context
from sldb.cli.serve.responses import send_json
from sldb.cli.serve.routes import dispatch, handle_options


def build_handler(
    store_arg: str | None,
    pythonpath: str | None,
    cors: bool,
) -> type[BaseHTTPRequestHandler]:
    store_path, project_root = get_store_context(store_arg)
    pythonpath = pythonpath or str(project_root)
    return type("SLDBServeHandler", (SLDBHTTPRequestHandler,), _handler_attrs(store_path, project_root, pythonpath, cors))


class SLDBHTTPRequestHandler(BaseHTTPRequestHandler):
    store_path = Path(".")
    project_root = Path(".")
    pythonpath = "."
    cors = False

    def do_GET(self) -> None:
        _handle(self, "GET")

    def do_POST(self) -> None:
        _handle(self, "POST")

    def do_OPTIONS(self) -> None:
        handle_options(self, self.cors)

    def log_message(self, format: str, *args: Any) -> None:
        return


def _handler_attrs(
    store_path: Path,
    project_root: Path,
    pythonpath: str,
    cors: bool,
) -> dict[str, Any]:
    return {"store_path": store_path, "project_root": project_root, "pythonpath": pythonpath, "cors": cors}


def _handle(handler: SLDBHTTPRequestHandler, method: str) -> None:
    try:
        payload, status = dispatch(handler, method, handler.store_path, handler.project_root, handler.pythonpath)
    except SystemExit as exc:
        send_json(handler, 400, {"ok": False, "error": str(exc)}, handler.cors)
        return
    except Exception as exc:  # pragma: no cover
        send_json(handler, 500, {"ok": False, "error": str(exc)}, handler.cors)
        return
    send_json(handler, status, payload, handler.cors)
