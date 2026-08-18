import ast
import os
from pathlib import Path
from collections import defaultdict
import json

def get_imports(filepath):
    try:
        with open(filepath, "r", encoding="utf-8") as f:
            content = f.read()
        tree = ast.parse(content, filename=str(filepath))
    except Exception:
        return []
    
    imports = []
    for node in ast.walk(tree):
        if isinstance(node, ast.Import):
            for alias in node.names:
                imports.append(alias.name)
        elif isinstance(node, ast.ImportFrom):
            module = node.module or ""
            for alias in node.names:
                imports.append(f"{module}.{alias.name}" if module else alias.name)
    return imports

def main():
    src_dir = Path("src")
    modules = {}
    
    for py_file in src_dir.rglob("*.py"):
        if py_file.name == "__init__.py":
            continue
            
        module_path = py_file.relative_to(src_dir).with_suffix("")
        module_name = str(module_path).replace(os.sep, ".")
        imports = get_imports(py_file)
        
        # Filter only internal sldb imports
        internal_imports = []
        for imp in imports:
            if imp.startswith("sldb") or imp.startswith("core") or imp.startswith("cli") or imp.startswith("store"):
                internal_imports.append(imp)
                
        modules[module_name] = {
            "path": str(py_file),
            "imports": internal_imports
        }
    
    print("# SLDB Architecture Map")
    print("\n## Modules")
    for mod, data in sorted(modules.items()):
        print(f"\n### `{mod}`")
        if data["imports"]:
            print("Dependencies:")
            for imp in data["imports"]:
                print(f"- {imp}")
        else:
            print("No internal dependencies.")

if __name__ == "__main__":
    main()
