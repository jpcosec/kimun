import re
from pathlib import Path

path = Path("src/sldb/store/semantic.py")
content = path.read_text()

new_code = """
def _process_model_semantics(m_entry, root, resolver, py_path, report, docs_dict, t_to_d, p_by_n):
    m_idx = load_models_index(root / m_entry.models_index)
    d_idx = load_documents_index(root / m_idx.documents_index)
    for doc in d_idx.documents:
        d_path = root / doc.path
        if not d_path.exists():
            report.docs_skipped_missing += 1; continue
        docs_dict[doc.name] = _process_doc(doc, d_path, resolver(m_entry.model_ref, py_path), m_entry.name, report)
        for tag in doc.semantic_tags:
            t_to_d[tag].append(doc.name)
            for p, c in _prefix_edges(tag): p_by_n[c].add(p); p_by_n.setdefault(p, set())
    save_documents_index(root / m_idx.documents_index, d_idx)
"""

content = re.sub(r'def _process_model_semantics\(.*?\n    save_documents_index\(root / m_idx\.documents_index, d_idx\)\n', new_code.strip() + '\n', content, flags=re.DOTALL)
path.write_text(content)
