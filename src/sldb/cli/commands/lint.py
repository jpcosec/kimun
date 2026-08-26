"""Canonical path lint for repo-root knowledge references."""

from __future__ import annotations
import re
from pathlib import Path
from typing import Any, Optional

ALLOWED_PREFIXES = ("source_docs/", "projects/", "software/", "docs/", "desk/", "src/", "tests/", "tools/", "scripts/", "build/")
LEGACY_PREFIXES = ("Lab_Chile-Teva/", "otros_proyectos/", "archived/", "backup/", "_old/")
RELATIVE_TRAVERSAL = re.compile(r"\.\./")

def lint_path(path_str: str, repo_root: Optional[Path] = None) -> list[str]:
    errors: list[str] = []
    _check_relative_traversal(path_str, errors)
    _check_legacy_prefix(path_str, errors)
    _check_canonical_prefix(path_str, errors)
    _check_path_exists(path_str, repo_root, errors)
    return errors

def _check_relative_traversal(path_str: str, errors: list[str]) -> None:
    if RELATIVE_TRAVERSAL.search(path_str):
        errors.append(f"Relative traversal '..' not allowed: '{path_str}'")

def _check_legacy_prefix(path_str: str, errors: list[str]) -> None:
    for legacy in LEGACY_PREFIXES:
        if path_str.startswith(legacy):
            errors.append(f"Legacy prefix '{legacy}' not allowed: '{path_str}'")

def _check_canonical_prefix(path_str: str, errors: list[str]) -> None:
    if not any(path_str.startswith(p) for p in ALLOWED_PREFIXES) and not path_str.startswith("/"):
        errors.append(f"Path must start with a canonical prefix ({', '.join(ALLOWED_PREFIXES)}): '{path_str}'")

def _check_path_exists(path_str: str, repo_root: Optional[Path], errors: list[str]) -> None:
    if repo_root and not errors:
        full = repo_root / path_str
        if not full.exists():
            errors.append(f"Referenced path does not exist: '{path_str}'")

def _build_path_pattern() -> re.Pattern:
    a = "|".join(re.escape(p) for p in ALLOWED_PREFIXES)
    l = "|".join(re.escape(p) for p in LEGACY_PREFIXES)
    return re.compile(rf"(?<![a-zA-Z0-9/])((?:{a})[a-zA-Z0-9_./-]+|(?:{l})[a-zA-Z0-9_./-]+|\.\.(?:/\.\.)*/[a-zA-Z0-9_./-]+)")

def lint_text(text: str, repo_root: Optional[Path] = None, label: str = "text") -> list[dict]:
    results: list[dict] = []
    pat = _build_path_pattern()
    for line_idx, line in enumerate(text.splitlines(), 1):
        for m in pat.finditer(line):
            errs = lint_path(m.group(0).rstrip("/."), repo_root)
            if errs:
                results.append({"path": m.group(0).rstrip("/."), "errors": errs, "line": line_idx, "source": label})
    return results

def _lint_dir(dir_path: Path, repo_root: Path) -> list[dict]:
    results: list[dict] = []
    for ext in ("*.md", "*.yml", "*.yaml", "*.py"):
        for f in sorted(dir_path.rglob(ext)):
            try:
                results.extend(lint_text(f.read_text(encoding="utf-8"), repo_root, label=str(f)))
            except Exception:
                continue
    return results

def lint_cli(args: Any) -> int:
    repo_root = Path(args.repo_root).resolve() if args.repo_root else Path.cwd()
    if not args.target:
        return _report(_lint_dir(repo_root, repo_root), args.format)
    target = Path(args.target)
    if not target.exists():
        print(f"Target not found: {args.target}"); return 1
    if target.is_file():
        return _report(lint_text(target.read_text("utf-8"), repo_root, str(target)), args.format)
    return _report(_lint_dir(target, repo_root), args.format)

def _report(results: list[dict], fmt: str) -> int:
    if fmt == "json":
        import json; print(json.dumps(results, indent=2)); return 1 if results else 0
    if fmt == "yaml":
        import yaml; print(yaml.dump(results, default_flow_style=False)); return 1 if results else 0
    if not results:
        print("No path issues found."); return 0
    for r in results:
        for err in r["errors"]:
            print(f"{r['source']}:{r['line']}: {err}")
    return 1