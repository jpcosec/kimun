import argparse
import sys

def main():
    parser = argparse.ArgumentParser(description="SLDB - Single Source Database CLI")
    subparsers = parser.add_subparsers(dest="command", required=True, title="Command Groups")

    # 1. Docs (docs-command-group.md)
    docs_parser = subparsers.add_parser("docs", help="Manage document-level workflows")
    docs_subparsers = docs_parser.add_subparsers(dest="docs_cmd", required=True)
    docs_subparsers.add_parser("create", help="Create a new document from a model")
    docs_subparsers.add_parser("track", help="Track an untracked document")
    docs_subparsers.add_parser("update", help="Update a document into the store")
    docs_subparsers.add_parser("show", help="Show document details")
    docs_subparsers.add_parser("list", help="List documents")
    docs_subparsers.add_parser("compose", help="Compose transcluded document views")
    docs_subparsers.add_parser("recover", help="Recover history")
    docs_subparsers.add_parser("explore", help="Explore document graph")
    docs_subparsers.add_parser("untrack", help="Untrack document")

    # 2. AST (ast-command-group.md)
    ast_parser = subparsers.add_parser("ast", help="Inspect Canonical AST")
    ast_subparsers = ast_parser.add_subparsers(dest="ast_cmd", required=True)
    ast_subparsers.add_parser("show", help="Show AST for a document")
    ast_subparsers.add_parser("query", help="Query AST nodes")

    # 3. Fields (fields-command-group.md)
    fields_parser = subparsers.add_parser("fields", help="Inspect and mutate frontmatter fields")
    fields_subparsers = fields_parser.add_subparsers(dest="fields_cmd", required=True)
    fields_subparsers.add_parser("get", help="Get a field value")
    fields_subparsers.add_parser("set", help="Set a field value")
    fields_subparsers.add_parser("list", help="List all fields")

    # 4. Sections (sections-command-group.md)
    sections_parser = subparsers.add_parser("sections", help="Inspect and mutate document sections")
    sections_subparsers = sections_parser.add_subparsers(dest="sections_cmd", required=True)
    sections_subparsers.add_parser("get", help="Get section content")
    sections_subparsers.add_parser("list", help="List document sections")

    # 5. Find (find-command-group.md)
    find_parser = subparsers.add_parser("find", help="Search the knowledge graph")
    find_subparsers = find_parser.add_subparsers(dest="find_cmd", required=True)
    find_subparsers.add_parser("text", help="FTS5 text search")
    find_subparsers.add_parser("semantic", help="Semantic search (Tantivy/Vector)")
    find_subparsers.add_parser("links", help="Find link relations")

    # 6. Models (models-command-group.md)
    models_parser = subparsers.add_parser("models", help="Manage StructuredNLDoc models")
    models_subparsers = models_parser.add_subparsers(dest="models_cmd", required=True)
    models_subparsers.add_parser("list", help="List registered models")
    models_subparsers.add_parser("show", help="Show model schema")
    models_subparsers.add_parser("init", help="Initialize a new model")
    models_subparsers.add_parser("sync", help="Sync model schemas")

    # 7. Stores (stores-command-group.md)
    stores_parser = subparsers.add_parser("stores", help="Manage local SLDB stores")
    stores_subparsers = stores_parser.add_subparsers(dest="stores_cmd", required=True)
    stores_subparsers.add_parser("init", help="Initialize a new .sldb store")
    stores_subparsers.add_parser("build", help="Build graph indexes")
    stores_subparsers.add_parser("destroy", help="Destroy the local store")

    # 8. Direct Mode (extract / render / validate)
    direct_parser = subparsers.add_parser("direct", help="Direct mode operations")
    direct_subparsers = direct_parser.add_subparsers(dest="direct_cmd", required=True)
    direct_subparsers.add_parser("extract", help="Extract AST from file")
    direct_subparsers.add_parser("render", help="Render markdown from AST")
    direct_subparsers.add_parser("validate", help="Validate document against model")

    # 9. FAQ / Help
    subparsers.add_parser("faq", help="FAQ and onboarding")
    subparsers.add_parser("help", help="Help system")

    args = parser.parse_args()

    # Route execution based on command groups
    if args.command == "stores":
        if args.stores_cmd == "init":
            from sldb_cli.store import Store
            store_path = ".sldb.sqlite"
            print(f"Initializing SLDB store at {store_path}...")
            # This triggers connection and schema setup in Rust core
            Store(store_path)
            print("✨ SLDB store successfully initialized via Rust Core!")
        elif args.stores_cmd == "build":
            print("Building indexes... (delegated to Rust)")
        elif args.stores_cmd == "destroy":
            print("Destroying store...")

    elif args.command == "find":
        if args.find_cmd == "text":
            from sldb_cli.store import Store
            # Temporary positional read, should use argparse for query later
            query = input("Enter search query: ")
            store = Store(".sldb.sqlite")
            results = store.search_nodes_by_payload(query)
            print(f"🔎 Search results for '{query}':")
            for r in results:
                print(f" - [{r.hash[:8]}] {r.node_type}: {r.payload[:50]}")
        else:
            print(f"Executing find {args.find_cmd} via Rust Core...")
            
    else:
        print(f"SLDB CLI Router invoked for command group: {args.command}. Subcommand: {getattr(args, f'{args.command}_cmd', 'N/A')}")

if __name__ == '__main__':
    main()
