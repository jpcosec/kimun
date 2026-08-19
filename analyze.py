import ast
import sys

def main(filepath):
    with open(filepath, "r") as f:
        source = f.read()
    
    tree = ast.parse(source)
    for node in tree.body:
        if isinstance(node, (ast.FunctionDef, ast.AsyncFunctionDef, ast.ClassDef)):
            if isinstance(node, ast.ClassDef):
                print(f"Class: {node.name}")
                for method in node.body:
                    if isinstance(method, ast.FunctionDef):
                        body = method.body
                        if body and isinstance(body[0], ast.Expr) and isinstance(body[0].value, ast.Constant):
                            body = body[1:]
                        if body:
                            start = body[0].lineno
                            end = body[-1].end_lineno
                            print(f"  Method: {method.name} ({end - start + 1} lines)")
            else:
                body = node.body
                if body and isinstance(body[0], ast.Expr) and isinstance(body[0].value, ast.Constant):
                    body = body[1:]
                if body:
                    start = body[0].lineno
                    end = body[-1].end_lineno
                    print(f"Function: {node.name} ({end - start + 1} lines)")

main("src/sldb/cli/graph.py")
