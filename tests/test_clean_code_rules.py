import ast
import os
import pytest
from pathlib import Path

SRC_DIR = Path(__file__).parent.parent / "src"

def get_python_files():
    for root, _, files in os.walk(SRC_DIR):
        for file in files:
            if file.endswith(".py"):
                yield Path(root) / file

@pytest.mark.parametrize("filepath", get_python_files(), ids=lambda p: p.relative_to(SRC_DIR).as_posix())
def test_clean_code_rules(filepath: Path):
    with open(filepath, "r", encoding="utf-8") as f:
        source = f.read()
    
    raw_lines = source.splitlines()
    tree = ast.parse(source, filename=str(filepath))
    
    classes = [node for node in tree.body if isinstance(node, ast.ClassDef)]
    
    # Rule 1: One class per file (max)
    assert len(classes) <= 1, f"File {filepath.name} has {len(classes)} classes. Must have max 1."
    
    # Rule 2: Functions <= 10 lines of logic
    for node in ast.walk(tree):
        if isinstance(node, (ast.FunctionDef, ast.AsyncFunctionDef)):
            body = node.body
            if body and isinstance(body[0], ast.Expr) and isinstance(body[0].value, ast.Constant) and isinstance(body[0].value.value, str):
                body = body[1:] # exclude docstring
                
            if not body:
                continue
                
            start_line = body[0].lineno
            end_line = body[-1].end_lineno
            logic_lines = end_line - start_line + 1
            
            assert logic_lines <= 10, f"Function '{node.name}' in {filepath.name} has {logic_lines} lines of logic (max 10)."
            
    # Rule 3: File < 80 lines
    non_empty_lines = [l for l in raw_lines if l.strip() and not l.strip().startswith("#")]
    imports = [n for n in tree.body if isinstance(n, (ast.Import, ast.ImportFrom))]
    import_lines = sum(getattr(n, 'end_lineno', n.lineno) - n.lineno + 1 for n in imports)
    
    effective_lines = len(non_empty_lines) - import_lines
    assert effective_lines <= 80, f"File {filepath.name} has {effective_lines} effective lines (max 80)."
