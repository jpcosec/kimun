import yaml
import sys

def convert():
    with open("sldb.spec.yml", "r", encoding="utf-8") as f:
        ast_data = yaml.safe_load(f)

    elements = ast_data["semantics"]["elements"]
    relations = ast_data["semantics"]["relations"]

    # Build spec2viz component diagram
    def sanitize(node_id):
        return node_id.replace(".", "_").replace("-", "_")

    nodes = {}
    edges = []

    for el in elements:
        safe_id = sanitize(el["id"])
        nodes[safe_id] = {
            "label": el["label"],
            "type": el["kind"]
        }

    for rel in relations:
        edges.append({
            "from": sanitize(rel["source"]),
            "to": sanitize(rel["target"]),
            "label": rel["kind"]
        })

    # Auto-register missing nodes
    for edge in edges:
        for endp in ("from", "to"):
            n = edge[endp]
            if n not in nodes:
                nodes[n] = {
                    "label": n.split("_")[-1],
                    "type": "external" if "unknown" in n or "module" in n else "unknown"
                }

    spec2viz_data = {
        "id": "sldb_ast_detailed",
        "title": "SLDB Deep AST Architecture",
        "type": "component",
        "version": "1.0",
        "data": {
            "nodes": nodes,
            "edges": edges
        }
    }

    with open("docs/sldb_ast_architecture.spec.yaml", "w", encoding="utf-8") as f:
        yaml.dump(spec2viz_data, f, sort_keys=False, allow_unicode=True)

if __name__ == "__main__":
    convert()
    print("Generated docs/sldb_ast_architecture.spec.yaml")
