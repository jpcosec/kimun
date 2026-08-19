import re
from pathlib import Path

path = Path("src/sldb/store/semantic_tags.py")
content = path.read_text()

new_code = """
def flatten_model_semantics(model_type: type) -> list[str]:
    tags = []
    for k, v in (getattr(model_type, "__semantics__", {}) or {}).items():
        if isinstance(v, str): tags.append(f"{k}.{v}")
        elif isinstance(v, (list, tuple)): tags.extend([".".join([k, *[str(p) for p in v]])] if v else [])
        elif isinstance(v, dict): tags.extend([".".join([k, ck, *([str(p) for p in cv] if isinstance(cv, (list, tuple)) else [str(cv)])]) for ck, cv in v.items()])
    return sorted(set(t for t in tags if t))
"""

content = re.sub(r'def flatten_model_semantics\(model_type: type\) -> list\[str\]:.*?return sorted\(set\(tag for tag in tags if tag\)\)\n', new_code.strip() + '\n', content, flags=re.DOTALL)
path.write_text(content)
