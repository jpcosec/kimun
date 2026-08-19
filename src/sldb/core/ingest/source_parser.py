from __future__ import annotations
import re
from pathlib import Path

def decompose_source(p: Path) -> list[tuple[str, str, str]]:
    try: text = p.read_text(encoding="utf-8")
    except UnicodeDecodeError: return [(p.stem, f"Binary source `{p.name}`.", "concept")]
    return _parse_sections(text, p.stem)

def _parse_sections(text: str, stem: str) -> list[tuple[str, str, str]]:
    sections = re.split(r"\n(?=## )", "\n" + text)
    return [_parse_section(s.strip(), stem) for s in sections if s.strip()]

def _parse_section(section: str, stem: str) -> tuple[str, str, str]:
    lines = section.splitlines()
    if lines[0].startswith("## "):
        title = lines[0].lstrip("# ").strip()
        n_type = "doc_standard" if "rule" in title.lower() or "standard" in title.lower() else "concept"
        return title, "\n".join(lines[1:]).strip(), n_type
    return _parse_top_section(lines, section, stem)

def _parse_top_section(lines: list[str], section: str, stem: str) -> tuple[str, str, str]:
    if lines[0].startswith("# "):
        return lines[0].lstrip("# ").strip(), "\n".join(lines[1:]).strip(), "concept"
    return stem.replace("_", " ").title(), section, "concept"

def summarize_source(p: Path) -> tuple[str, str]:
    try: text = p.read_text(encoding="utf-8")
    except UnicodeDecodeError:
        return _clean_stem(p), f"Binary or non-UTF-8 source captured from `{p.name}`."
    lines = [line.strip() for line in text.splitlines() if line.strip()]
    if lines and lines[0].startswith("#"): lines = lines[1:]
    return _clean_stem(p), (lines[0] if lines else f"Draft generated from `{p.name}`.")

def _clean_stem(p: Path) -> str:
    return p.stem.replace("_", " ").replace("-", " ").title()
