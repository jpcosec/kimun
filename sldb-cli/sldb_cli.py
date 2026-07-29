import argparse
import sys
from sldb_cli.store import Store
from sldb_cli.importers.markdown import import_markdown
from sldb_cli.emitters.markdown import emit_markdown, emit_prosemirror

def init_command(args):
    """
    Initializes a new SLDB database at the specified path.
    (This fulfills the onboarding workflow).
    """
    print(f"Initializing SLDB store at {args.path}...")
    store = Store(args.path)
    print("✨ SLDB store successfully initialized!")

def import_command(args):
    """
    Imports a markdown file into the SLDB ecosystem.
    """
    print(f"Importing {args.file}...")
    ast_dict = import_markdown(args.file)
    print("✅ File successfully imported into AST!")
    print(f"Document root elements: {len(ast_dict['ast'])}")

def emit_command(args):
    """
    Emits an AST back to markdown or ProseMirror JSON.
    """
    # For scaffold purposes, we just run the importer to get an AST, then emit it.
    ast_dict = import_markdown(args.file)
    if args.format == "prosemirror":
        emit_prosemirror(ast_dict, args.output)
        print(f"🌟 ProseMirror JSON emitted to {args.output}")
    else:
        emit_markdown(ast_dict, args.output)
        print(f"📝 Markdown emitted to {args.output}")

def search_command(args):
    """
    Searches the SLDB store for a specific payload using semantic or direct search.
    """
    store = Store(args.store)
    results = store.search_nodes_by_payload(args.query)
    print(f"🔎 Search results for '{args.query}':")
    for r in results:
        print(f" - [{r.hash[:8]}] {r.node_type}: {r.payload[:50]}...")
        
def main():
    parser = argparse.ArgumentParser(description="SLDB Command Line Interface")
    subparsers = parser.add_subparsers(dest="command")
    
    # Init command
    init_parser = subparsers.add_parser("init", help="Initialize a new SLDB store")
    init_parser.add_argument("path", help="Path to the SQLite store")
    
    # Import command
    import_parser = subparsers.add_parser("import", help="Import a Markdown file")
    import_parser.add_argument("file", help="Path to the markdown file")
    
    # Emit command
    emit_parser = subparsers.add_parser("emit", help="Emit a document")
    emit_parser.add_argument("file", help="Source file")
    emit_parser.add_argument("output", help="Output path")
    emit_parser.add_argument("--format", choices=["markdown", "prosemirror"], default="markdown")

    # Search command
    search_parser = subparsers.add_parser("search", help="Search the store")
    search_parser.add_argument("store", help="Path to the SQLite store")
    search_parser.add_argument("query", help="Text to search")

    args = parser.parse_args()
    
    if args.command == "init":
        init_command(args)
    elif args.command == "import":
        import_command(args)
    elif args.command == "emit":
        emit_command(args)
    elif args.command == "search":
        search_command(args)
    else:
        parser.print_help()

if __name__ == "__main__":
    main()
