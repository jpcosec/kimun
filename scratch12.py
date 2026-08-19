import re
from pathlib import Path

path = Path("src/sldb/store/semantic.py")
content = path.read_text()

new_code = """
def _process_doc_sections(doc, d_path, report):
    report.docs_processed += 1
    if not (secs := _extract_sections(d_path.read_text(encoding="utf-8"))): report.docs_empty_sections += 1
    tags, records, stack = list(doc.semantic_tags or []), [], []
    for s in secs:
        while stack and stack[-1][0] >= s["level"]: stack.pop()
        b_crumbs = list(stack[-1][1]) if stack else []
        b_crumbs.append(s["title"]); stack.append((s["level"], b_crumbs))
        if s.get("line_start") is None: report.headings_no_map += 1
        records.append(SectionContextRecord(path=s["path"], title=s["title"], breadcrumbs=b_crumbs, about=_about_terms(b_crumbs, tags), semantic_tags=tags, slug=s["slug"], level=s["level"], line_start=s.get("line_start"), line_end=s.get("line_end")))
    return DocSections(doc_name=doc.name, sections=records)
"""

content = re.sub(r'def _process_doc_sections\(.*?return DocSections\(doc_name=doc\.name, sections=records\)\n', new_code.strip() + '\n', content, flags=re.DOTALL)
path.write_text(content)
