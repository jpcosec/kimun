import os
import re
from pathlib import Path
import ast

src_dir = Path("src/sldb/store")
diag_models_py = src_dir / "diagnostics_models.py"

content = diag_models_py.read_text()

models_dir = src_dir / "diagnostics_models"
models_dir.mkdir(exist_ok=True)

tree = ast.parse(content)
classes_nodes = [node for node in tree.body if isinstance(node, ast.ClassDef)]

init_lines = []

for cls in classes_nodes:
    cls_name = cls.name
    snake_name = re.sub(r'(?<!^)(?=[A-Z])', '_', cls_name).lower()
    
    file_path = models_dir / f"{snake_name}.py"
    cls_source = ast.unparse(cls)
    
    deps = [n.id for n in ast.walk(cls) if isinstance(n, ast.Name) and n.id in [c.name for c in classes_nodes]]
    deps = list(set(deps))
    
    file_content = "from __future__ import annotations\nfrom dataclasses import dataclass, field\nfrom enum import Enum\n"
    for dep in deps:
        if dep != cls_name:
            dep_snake = re.sub(r'(?<!^)(?=[A-Z])', '_', dep).lower()
            file_content += f"from sldb.store.diagnostics_models.{dep_snake} import {dep}\n"
    
    file_content += "\n\n" + cls_source + "\n"
    file_path.write_text(file_content)
    
    init_lines.append(f"from sldb.store.diagnostics_models.{snake_name} import {cls_name}")

(models_dir / "__init__.py").write_text("\n".join(init_lines) + "\n")
os.remove(diag_models_py)

