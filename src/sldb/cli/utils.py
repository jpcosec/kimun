from __future__ import annotations
import sys
import json
import yaml
from pathlib import Path
from typing import Any

def read_text(path: str) -> str:
    """Read text from a file or stdin."""
    if path == "-":
        return sys.stdin.read()
    return Path(path).read_text(encoding="utf-8")

def write_text(path: str, content: str) -> None:
    """Write text to a file or stdout."""
    if path == "-":
        sys.stdout.write(content)
        return
    Path(path).write_text(content, encoding="utf-8")

def parse_data_value(raw: str) -> Any:
    """Parse JSON/YAML scalars or objects from a CLI string."""
    try:
        return json.loads(raw)
    except json.JSONDecodeError:
        return yaml.safe_load(raw)
