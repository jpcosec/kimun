from __future__ import annotations

from pathlib import Path
import html
import yaml

ROOT = Path(__file__).resolve().parents[1]
DOCS = ROOT / "docs"


def load_text(path: Path) -> str:
    return path.read_text(encoding="utf-8").strip()


def build_nav(vistas: list[dict]) -> str:
    groups: dict[str, list[dict]] = {}
    for vista in vistas:
        groups.setdefault(vista.get("category", "Other"), []).append(vista)
    parts: list[str] = []
    for category, items in groups.items():
        links = "".join(
            f'<a href="#${item["id"]}">{html.escape(item["id"])}</a>'.replace("#$", "#")
            for item in items
        )
        parts.append(f'<div class="nav-category"><span>{html.escape(category)}</span>{links}</div>')
    return "\n".join(parts)


def build_sections(vistas: list[dict]) -> str:
    parts: list[str] = []
    current_category: str | None = None
    for vista in vistas:
        category = vista.get("category", "Other")
        if category != current_category:
            parts.append(f'<div class="macro-separator"><h2>— {html.escape(category)} —</h2></div>')
            current_category = category
        src = (DOCS / vista["src"]).resolve()
        mermaid = html.escape(load_text(src))
        title = html.escape(vista["title"])
        desc = html.escape(vista.get("desc", ""))
        spec = html.escape((vista.get("specs") or [""])[0])
        parts.append(
            f'''<section id="{html.escape(vista["id"])}" data-spec="{spec}">\n'''
            f'''  <div class="lbl">{html.escape(vista.get("kind", ""))}</div>\n'''
            f'''  <h2>{title}</h2>\n'''
            f'''  <p class="desc">{desc}</p>\n'''
            f'''  <div class="board">\n'''
            f'''    <div class="mermaid">\n{mermaid}\n    </div>\n'''
            f'''  </div>\n'''
            f'''</section>'''
        )
    return "\n".join(parts)


def main() -> None:
    vistas_cfg = yaml.safe_load(load_text(DOCS / "vistas.yml"))
    template = load_text(DOCS / vistas_cfg["template"])
    rendered = template.replace("{{NAV}}", build_nav(vistas_cfg["vistas"])).replace(
        "{{SECTIONS}}", build_sections(vistas_cfg["vistas"])
    )
    (DOCS / "architecture.html").write_text(rendered + "\n", encoding="utf-8")
    print("Generated docs/architecture.html")


if __name__ == "__main__":
    main()
