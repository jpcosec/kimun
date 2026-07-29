import json
from markdown_it import MarkdownIt

def import_markdown(file_path: str) -> dict:
    """
    Parses a markdown document and translates it into an SLDB document AST representation.
    """
    with open(file_path, 'r', encoding='utf-8') as f:
        content = f.read()
    
    md = MarkdownIt("commonmark")
    tokens = md.parse(content)
    
    # Translate tokens to a simplified dict AST
    ast_nodes = []
    for token in tokens:
        node = {
            "type": token.type,
            "tag": token.tag,
            "content": token.content,
            "level": token.level,
            "map": token.map
        }
        ast_nodes.append(node)
    
    return {
        "source": file_path,
        "ast": ast_nodes,
        "raw_text": content
    }
