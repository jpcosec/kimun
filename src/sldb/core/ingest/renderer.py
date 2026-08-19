from __future__ import annotations
import re
from pathlib import Path
from datetime import datetime

def render_draft(
    rel_src: str, n_id: str, title: str, content: str, n_type: str = "concept", s_hash: str = ""
) -> str:
    return _build_draft_content(rel_src, n_id, title, content, n_type, s_hash, datetime.now().isoformat())

def _build_draft_content(rel_src: str, n_id: str, title: str, content: str, n_type: str, s_hash: str, compiled_at: str) -> str:
    abstract = _extract_abstract(content)
    frontmatter = _build_frontmatter(rel_src, n_id, n_type, s_hash, compiled_at)
    return f"{frontmatter}\n\n{abstract}\n\n## Details\n\n{content}\n\nGenerated from `{rel_src}`."

def _build_frontmatter(rel_src: str, n_id: str, n_type: str, s_hash: str, compiled_at: str) -> str:
    lines = [
        "---", "identity:", f'  node_id: "{n_id}"', f'  node_type: "{n_type}"',
        "edges:", f'  - {{target_id: "raw:{rel_src}", relation_type: "documents"}}',
        "compliance:", '  status: "planned"', "  failing_standards: []",
        "source:", f'  source_path: "{rel_src}"', f'  source_hash: "{s_hash}"',
        f'  compiled_at: "{compiled_at}"', '  compiled_from: "wiki-compiler"', "---"
    ]
    return "\n".join(lines)

def _extract_abstract(content: str) -> str:
    lines = [line.strip() for line in content.splitlines() if line.strip()]
    return lines[0] if lines else "No abstract provided."

def render_index(subdir: str, entries: list[dict]) -> str:
    label = subdir or "drafts"
    lines = [f"# Index: {label}", ""]
    lines.extend(f"- **{e['node_id']}** — {e['title']}: {e['abstract']}" for e in entries)
    lines.append("")
    return "\n".join(lines)

def slugify(value: str) -> str:
    slug = re.sub(r"[^a-zA-Z0-9]+", "_", value).strip("_").lower()
    return (slug or "draft_node")[:80].rstrip("_")

def extract_abstract(content: str) -> str:
    return _extract_abstract(content)
