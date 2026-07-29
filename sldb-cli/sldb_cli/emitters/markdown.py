import json

def emit_markdown(ast_dict: dict, output_path: str):
    """
    Takes an SLDB document AST and writes it as raw markdown.
    """
    raw_text = ast_dict.get("raw_text", "")
    with open(output_path, 'w', encoding='utf-8') as f:
        f.write(raw_text)

def emit_prosemirror(ast_dict: dict, output_path: str):
    """
    Translates an SLDB AST to ProseMirror JSON representation and writes to file.
    """
    ast_nodes = ast_dict.get("ast", [])
    
    # Simple mapping from markdown-it tokens to ProseMirror nodes
    doc = {
        "type": "doc",
        "content": []
    }
    
    current_block = None
    
    for token in ast_nodes:
        if token["type"] == "paragraph_open":
            current_block = {"type": "paragraph", "content": []}
        elif token["type"] == "heading_open":
            current_block = {
                "type": "heading",
                "attrs": {"level": int(token["tag"].replace("h", ""))},
                "content": []
            }
        elif token["type"] == "inline":
            if current_block is not None:
                current_block["content"].append({
                    "type": "text",
                    "text": token["content"]
                })
        elif token["type"] in ["paragraph_close", "heading_close"]:
            if current_block is not None:
                doc["content"].append(current_block)
                current_block = None
                
    with open(output_path, 'w', encoding='utf-8') as f:
        json.dump(doc, f, indent=2)
