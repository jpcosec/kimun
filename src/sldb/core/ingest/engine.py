"""Handles ingestion of raw sources into knowledge graph draft nodes."""
from __future__ import annotations
from pathlib import Path
from .scanner import load_wikiignore_rules, match_ignore_reason
from .manifest import compute_content_hash, add_to_manifest
from .source_parser import decompose_source, summarize_source
from .renderer import render_draft, slugify, render_index, extract_abstract

def ingest_raw_sources(
    src_dir: Path, dst_dir: Path, root: Path | None = None,
    overwrite: bool = False, m_path: Path | None = None,
) -> list[Path]:
    root = root or src_dir.parent
    rules = load_wikiignore_rules(root / ".wikiignore")
    written: list[Path] = []
    subdirs: dict[str, list[dict]] = {}
    for p in sorted(p for p in src_dir.rglob("*.md") if p.is_file()):
        _process_path(p, src_dir, dst_dir, root, rules, overwrite, m_path, written, subdirs)
    _write_indices(dst_dir, subdirs)
    return written

def _process_path(
    p: Path, src_dir: Path, dst_dir: Path, root: Path, rules: list,
    overwrite: bool, m_path: Path | None, written: list[Path], subdirs: dict
) -> None:
    rel_to_src = p.relative_to(src_dir)
    if any(part.startswith(".") for part in rel_to_src.parts): return
    rel_src = p.relative_to(root).as_posix()
    if match_ignore_reason(rel_src, rules): return
    subdir = _source_subdir(rel_to_src)
    s_hash = compute_content_hash(p)
    if m_path: add_to_manifest(root, m_path, p)
    _write_nodes(p, dst_dir, subdir, rel_src, s_hash, overwrite, written, subdirs)

def _write_nodes(
    p: Path, dst_dir: Path, subdir: str, rel_src: str, s_hash: str,
    overwrite: bool, written: list[Path], subdirs: dict
) -> None:
    for title, content, n_type in decompose_source(p):
        d_slug = slugify(title)
        d_path = (dst_dir / subdir if subdir else dst_dir) / f"{d_slug}.md"
        if d_path.exists() and not overwrite: continue
        d_path.parent.mkdir(parents=True, exist_ok=True)
        n_id = _compute_node_id(dst_dir, subdir, d_slug)
        d_path.write_text(render_draft(rel_src, n_id, title, content, n_type, s_hash), encoding="utf-8")
        written.append(d_path)
        subdirs.setdefault(subdir, []).append({"node_id": n_id, "title": title, "abstract": extract_abstract(content)})

def _write_indices(dst_dir: Path, subdirs: dict) -> None:
    for subdir, entries in subdirs.items():
        idx_path = (dst_dir / subdir / "INDEX.md") if subdir else (dst_dir / "INDEX.md")
        idx_path.parent.mkdir(parents=True, exist_ok=True)
        idx_path.write_text(render_index(subdir, entries), encoding="utf-8")

def _source_subdir(rel_to_src: Path) -> str:
    parts = rel_to_src.parts
    return parts[0] if len(parts) > 1 else ""

def _compute_node_id(dst_dir: Path, subdir: str, d_slug: str) -> str:
    base = _get_base_path(dst_dir)
    return f"doc:{(base / subdir / d_slug).as_posix()}.md" if subdir else f"doc:{(base / d_slug).as_posix()}.md"

def _get_base_path(dst_dir: Path) -> Path:
    if not dst_dir.is_absolute(): return dst_dir
    p_name = dst_dir.parent.name
    if p_name == "wiki": return Path(p_name) / dst_dir.name
    if p_name == "desk": return Path("desk") / dst_dir.name
    return Path(dst_dir.name)

def draft_node_path(dst_dir: Path, stem: str) -> str:
    return (_get_base_path(dst_dir) / f"{slugify(stem)}.md").as_posix()
