import re
from pathlib import Path

path = Path("src/sldb/store/semantic.py")
content = path.read_text()

new_code = """
def _save_indexes(s_path, p_by_n, equiv, t_to_d, docs):
    nodes = [SemanticNode(id=n_id, parents=sorted(p)) for n_id, p in sorted(p_by_n.items())]
    save_semantic_dag(s_path, SemanticDAG(nodes=nodes, equivalences=equiv))
    tags = {t: sorted(d) for t, d in sorted(t_to_d.items())}
    save_semantic_index(s_path, SemanticIndex(tags=tags, documents=docs))
"""

content = re.sub(r'def _save_indexes\(.*?def _about_terms', new_code.strip() + '\n\n\ndef _about_terms', content, flags=re.DOTALL)
path.write_text(content)
