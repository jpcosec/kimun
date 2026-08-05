#!/usr/bin/env bash
set -euo pipefail

usage() {
  cat <<'EOF'
uso:
  bootstrap_diagram_ui_from_git.sh <target-root>

qué hace:
  - clona deskops, sldb(refactor-target), spec2viz en <target-root>/.diagram-ui/vendor/
  - copia el bundle de la UI de diagramas al proyecto destino
  - instala un annotate_server.py genérico
  - crea .diagram-ui/annotations.json

notas:
  - usa git+ssh por puerto 443 para GitHub
  - fija commits exactos para reproducibilidad
EOF
}

if [[ ${1:-} == "-h" || ${1:-} == "--help" ]]; then
  usage
  exit 0
fi

TARGET_ROOT="${1:-}"
if [[ -z "$TARGET_ROOT" ]]; then
  usage >&2
  exit 1
fi

TARGET_ROOT="$(python3 - <<'PY' "$TARGET_ROOT"
import os, sys
print(os.path.abspath(os.path.expanduser(sys.argv[1])))
PY
)"

VENDOR_ROOT="$TARGET_ROOT/.diagram-ui/vendor"
UI_DOCS_ROOT="$TARGET_ROOT/docs/architecture"
SCRIPTS_ROOT="$TARGET_ROOT/scripts"
ANNO_PATH_REL=".diagram-ui/annotations.json"
ANNO_PATH="$TARGET_ROOT/$ANNO_PATH_REL"

SSH_GITHUB_CMD="ssh -o Hostname=ssh.github.com -p 443 -o StrictHostKeyChecking=accept-new"

DESKOPS_REPO="git@github.com:jpcosec/opsys.git"
DESKOPS_REF="main"
DESKOPS_SHA="593b87a"

SLDB_UI_REPO="git@github.com:jpcosec/sldb.git"
SLDB_UI_REF="refactor-target"
SLDB_UI_SHA=""

SPEC2VIZ_REPO="git@github.com:jpcosec/spec2viz.git"
SPEC2VIZ_REF="master"
SPEC2VIZ_SHA="a8e5c24"

clone_fixed() {
  local name="$1" repo="$2" ref="$3" sha="$4" dest="$5"
  rm -rf "$dest"
  GIT_SSH_COMMAND="$SSH_GITHUB_CMD" git clone --branch "$ref" --single-branch "$repo" "$dest" >/dev/null
  if [[ -n "$sha" ]]; then
    git -C "$dest" checkout "$sha" >/dev/null
    echo "clonado $name @ $sha"
  else
    echo "clonado $name @ $(git -C "$dest" rev-parse --short HEAD)"
  fi
}

copy_tree() {
  local src="$1" dest="$2"
  rm -rf "$dest"
  mkdir -p "$(dirname "$dest")"
  cp -R "$src" "$dest"
}

mkdir -p "$VENDOR_ROOT" "$UI_DOCS_ROOT" "$SCRIPTS_ROOT" "$(dirname "$ANNO_PATH")"
printf '[]\n' > "$ANNO_PATH"

clone_fixed deskops "$DESKOPS_REPO" "$DESKOPS_REF" "$DESKOPS_SHA" "$VENDOR_ROOT/deskops"
clone_fixed sldb-ui-source "$SLDB_UI_REPO" "$SLDB_UI_REF" "$SLDB_UI_SHA" "$VENDOR_ROOT/sldb-ui-source"
clone_fixed spec2viz "$SPEC2VIZ_REPO" "$SPEC2VIZ_REF" "$SPEC2VIZ_SHA" "$VENDOR_ROOT/spec2viz"

copy_tree "$VENDOR_ROOT/sldb-ui-source/docs/architecture/spec2viz" "$UI_DOCS_ROOT/spec2viz"
copy_tree "$VENDOR_ROOT/sldb-ui-source/docs/architecture/vistas" "$UI_DOCS_ROOT/vistas"
cp "$VENDOR_ROOT/sldb-ui-source/docs/architecture/sldb_Kernel_Vistas_UML.html" "$UI_DOCS_ROOT/sldb_Kernel_Vistas_UML.html"
cp "$VENDOR_ROOT/sldb-ui-source/scripts/generate_vistas_html.py" "$SCRIPTS_ROOT/generate_vistas_html.py"

cat > "$SCRIPTS_ROOT/annotate_server.py" <<'PY'
#!/usr/bin/env python3
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
    print(f"→ http://127.0.0.1:{PORT}/docs/architecture/sldb_Kernel_Vistas_UML.html")
    print(f"→ annotations: {ANNOTATIONS}")
    ThreadingHTTPServer(("127.0.0.1", PORT), Handler).serve_forever()
PY
chmod +x "$SCRIPTS_ROOT/annotate_server.py"

cat > "$SCRIPTS_ROOT/serve_diagram_ui.sh" <<'SH'
#!/usr/bin/env bash
set -euo pipefail
ROOT="$(cd "$(dirname "$0")/.." && pwd)"
cd "$ROOT"
python3 scripts/annotate_server.py
SH
chmod +x "$SCRIPTS_ROOT/serve_diagram_ui.sh"

cat > "$TARGET_ROOT/.diagram-ui/bootstrap-lock.txt" <<EOF
deskops_repo=$DESKOPS_REPO
ssh_github_cmd=$SSH_GITHUB_CMD
deskops_ref=$DESKOPS_REF
deskops_sha=$DESKOPS_SHA
sldb_ui_repo=$SLDB_UI_REPO
sldb_ui_ref=$SLDB_UI_REF
sldb_ui_sha=$SLDB_UI_SHA
spec2viz_repo=$SPEC2VIZ_REPO
spec2viz_ref=$SPEC2VIZ_REF
spec2viz_sha=$SPEC2VIZ_SHA
EOF

cat <<EOF
ok
- target: $TARGET_ROOT
- html: $UI_DOCS_ROOT/sldb_Kernel_Vistas_UML.html
- server: $SCRIPTS_ROOT/annotate_server.py
- serve: $SCRIPTS_ROOT/serve_diagram_ui.sh
- annotations: $ANNO_PATH
EOF
