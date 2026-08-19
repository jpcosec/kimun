import re
from pathlib import Path

path = Path("src/sldb/store/migration.py")
content = path.read_text()

new_code = """
def _migrate_model(m_entry, p_root) -> bool:
    c_m_rel = m_entry.models_index
    c_m_path = p_root / c_m_rel
    m_idx = load_models_index(c_m_path)
    n_m_rel, n_d_rel, n_s_rel = models_index_relpath(m_entry.name), documents_index_relpath(m_entry.name), sections_index_relpath(m_entry.name)
    save_documents_index(p_root / n_d_rel, load_documents_index(p_root / m_idx.documents_index))
    if m_idx.sections_index:
        save_sections_index(p_root / n_s_rel, load_sections_index(p_root / m_idx.sections_index))
        m_idx.sections_index = n_s_rel
    changed = m_idx.documents_index != n_d_rel or c_m_rel != n_m_rel
    m_idx.documents_index = n_d_rel
    save_models_index(p_root / n_m_rel, m_idx)
    m_entry.models_index = n_m_rel
    return changed

def migrate_store_layout(store_path: Path, project_root: Path) -> bool:
    store_index = load_store_index(store_path)
    changed = not store_index_path(store_path).exists()
    for m_entry in store_index.models:
        if _migrate_model(m_entry, project_root): changed = True
    save_semantic_dag(store_path, load_semantic_dag(store_path))
    save_semantic_index(store_path, load_semantic_index(store_path))
    if changed: save_store_index(store_path, store_index)
    return changed
"""

content = re.sub(r'def migrate_store_layout\(store_path: Path, project_root: Path\) -> bool:.*    return changed\n', new_code.strip() + '\n', content, flags=re.DOTALL)
path.write_text(content)
