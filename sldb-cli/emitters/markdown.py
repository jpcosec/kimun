def emit_markdown(document_ast: dict, output_path: str):
    """
    Emits a markdown document from an SLDB document AST representation.
    """
    content = document_ast.get("raw_text", "")
    with open(output_path, 'w', encoding='utf-8') as f:
        f.write(content)
