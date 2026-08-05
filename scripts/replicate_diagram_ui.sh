#!/usr/bin/env bash
set -euo pipefail

SRC_HTML_DEFAULT="docs/architecture/sldb_Kernel_Vistas_UML.html"
TARGET_ROOT="${1:-}"
SRC_HTML="${2:-$SRC_HTML_DEFAULT}"
TARGET_HTML_REL="${3:-docs/architecture/sldb_Kernel_Vistas_UML.html}"
TARGET_SERVER_REL="${4:-scripts/annotate_server.py}"
TARGET_ANNOTATIONS_REL="${5:-.diagram-ui/annotations.json}"

if [[ -z "$TARGET_ROOT" ]]; then
  echo "uso: $0 <target-root> [src-html] [target-html-rel] [target-server-rel] [target-annotations-rel]" >&2
  exit 1
fi

if [[ ! -f "$SRC_HTML" ]]; then
  echo "html fuente no existe: $SRC_HTML" >&2
  exit 1
fi

TARGET_ROOT="$(python3 - <<'PY' "$TARGET_ROOT"
import os, sys
print(os.path.abspath(os.path.expanduser(sys.argv[1])))
PY
)"

TARGET_HTML="$TARGET_ROOT/$TARGET_HTML_REL"
TARGET_SERVER="$TARGET_ROOT/$TARGET_SERVER_REL"
TARGET_ANNOTATIONS="$TARGET_ROOT/$TARGET_ANNOTATIONS_REL"

mkdir -p "$(dirname "$TARGET_HTML")" "$(dirname "$TARGET_SERVER")" "$(dirname "$TARGET_ANNOTATIONS")"
cp "$SRC_HTML" "$TARGET_HTML"
: > "$TARGET_ANNOTATIONS"
printf '[]\n' > "$TARGET_ANNOTATIONS"

cat > "$TARGET_SERVER" <<'PY'
#!/usr/bin/env python3
"""Servidor local de anotaciones para una UI HTML de diagramas.

Uso:
    python3 scripts/annotate_server.py

Opcionales por env:
    DIAGRAM_UI_PORT=8765
    DIAGRAM_UI_ROOT=/ruta/al/proyecto
    DIAGRAM_ANNOTATIONS_PATH=.diagram-ui/annotations.json
"""
import json
import os
import time
from http.server import ThreadingHTTPServer, SimpleHTTPRequestHandler
from pathlib import Path

ROOT = Path(os.environ.get("DIAGRAM_UI_ROOT") or Path(__file__).resolve().parent.parent).resolve()
PORT = int(os.environ.get("DIAGRAM_UI_PORT", "8765"))
ANNOTATIONS = ROOT / os.environ.get("DIAGRAM_ANNOTATIONS_PATH", ".diagram-ui/annotations.json")


class Handler(SimpleHTTPRequestHandler):
    def __init__(self, *args, **kwargs):
        super().__init__(*args, directory=str(ROOT), **kwargs)

    def _send_json(self, data, status=200):
        body = json.dumps(data, ensure_ascii=False).encode()
        self.send_response(status)
        self.send_header("Content-Type", "application/json; charset=utf-8")
        self.send_header("Content-Length", str(len(body)))
        self.end_headers()
        self.wfile.write(body)

    def _read_annotations(self):
        if ANNOTATIONS.exists() and ANNOTATIONS.stat().st_size:
            return json.loads(ANNOTATIONS.read_text())
        return []

    def _write_annotations(self, data):
        ANNOTATIONS.parent.mkdir(parents=True, exist_ok=True)
        ANNOTATIONS.write_text(json.dumps(data, ensure_ascii=False, indent=2) + "\n")

    def do_GET(self):
        if self.path == "/annotations":
            self._send_json(self._read_annotations())
        else:
            super().do_GET()

    def do_POST(self):
        if self.path != "/annotate":
            self._send_json({"error": "not found"}, 404)
            return
        length = int(self.headers.get("Content-Length", 0))
        payload = json.loads(self.rfile.read(length) or b"{}")
        if not {"vista", "label", "comentario"} <= payload.keys():
            self._send_json({"error": "faltan campos: vista, label, comentario"}, 400)
            return
        data = self._read_annotations()
        data.append({
            "vista": payload["vista"],
            "label": payload["label"],
            "comentario": payload["comentario"],
            "ts": time.strftime("%Y-%m-%dT%H:%M:%S%z"),
        })
        self._write_annotations(data)
        self._send_json({"ok": True, "total": len(data)})

    def do_DELETE(self):
        if self.path != "/annotate":
            self._send_json({"error": "not found"}, 404)
            return
        length = int(self.headers.get("Content-Length", 0))
        payload = json.loads(self.rfile.read(length) or b"{}")
        data = [a for a in self._read_annotations()
                if not (a.get("vista") == payload.get("vista")
                        and a.get("label") == payload.get("label"))]
        self._write_annotations(data)
        self._send_json({"ok": True, "total": len(data)})

    def log_message(self, fmt, *args):
        pass


if __name__ == "__main__":
    print(f"→ root: {ROOT}")
    print(f"→ port: {PORT}")
    print(f"→ annotations: {ANNOTATIONS.relative_to(ROOT) if ANNOTATIONS.is_relative_to(ROOT) else ANNOTATIONS}")
    ThreadingHTTPServer(("127.0.0.1", PORT), Handler).serve_forever()
PY

chmod +x "$TARGET_SERVER"

echo "copiado: $TARGET_HTML"
echo "servidor: $TARGET_SERVER"
echo "anotaciones: $TARGET_ANNOTATIONS"
echo "arranque: cd '$TARGET_ROOT' && python3 '$TARGET_SERVER_REL'"
