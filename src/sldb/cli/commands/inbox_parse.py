from pathlib import Path
import yaml


def iter_notes(desk_root: Path) -> list[Path]:
    inbox_dir = desk_root / "inbox"
    if not inbox_dir.exists():
        return []
    return sorted(inbox_dir.glob("*.md"), reverse=True)


def resolve_note(desk_root: Path, raw: str) -> Path | None:
    notes = iter_notes(desk_root)
    exact = next((path for path in notes if raw in {path.name, path.stem}), None)
    if exact is not None:
        return exact
    lowered = raw.lower()
    return next((path for path in notes if lowered in path.stem.lower()), None)


def parse_note(path: Path) -> tuple[dict[str, str], str, str]:
    text = path.read_text(encoding="utf-8")
    frontmatter, body = split_frontmatter(text)
    lines = body.strip().splitlines()
    has_title = lines and lines[0].startswith("# ")
    title = lines[0].lstrip("# ").strip() if has_title else path.stem
    content = "\n".join(lines[1:]).strip() if has_title else body.strip()
    return frontmatter, title, content


def split_frontmatter(text: str) -> tuple[dict[str, str], str]:
    if text.startswith("---\n"):
        _, rest = text.split("---\n", 1)
        fm_block, body = rest.split("\n---\n", 1)
        return yaml.safe_load(fm_block) or {}, body
    return {}, text


def note_summary(path: Path) -> dict[str, str]:
    frontmatter, title, _ = parse_note(path)
    return {
        "id": path.stem,
        "path": str(path),
        "kind": frontmatter.get("kind", ""),
        "status": frontmatter.get("status", ""),
        "created_at": frontmatter.get("created_at", ""),
        "title": title,
    }


def note_detail(path: Path) -> dict[str, str]:
    frontmatter, title, body = parse_note(path)
    return {
        "id": path.stem,
        "path": str(path),
        "title": title,
        "body": body,
        **frontmatter,
    }
