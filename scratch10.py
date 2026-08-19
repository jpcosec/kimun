import re
from pathlib import Path

path = Path("src/sldb/store/semantic.py")
content = path.read_text()

new_code = """
def _about_terms(breadcrumbs: list[str], semantic_tags: list[str]) -> list[str]:
    seen, terms = set(), []
    for b in breadcrumbs:
        if (r := b.strip()) and r not in seen: seen.add(r); terms.append(r)
        if (n := _slugify(r).replace("-", " ").strip()) and n not in seen: seen.add(n); terms.append(n)
    for tag in semantic_tags:
        if tag not in seen: seen.add(tag); terms.append(tag)
    return terms
"""

content = re.sub(r'def _about_terms\(.*?return terms\n', new_code.strip() + '\n', content, flags=re.DOTALL)
path.write_text(content)
