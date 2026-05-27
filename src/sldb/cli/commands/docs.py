from __future__ import annotations

import json
from typing import Any

import yaml

from sldb.cli.commands.doc import DocCLI
from sldb.cli.commands.explore import ExploreCLI
from sldb.cli.commands.links import LinkCLI
from sldb.cli.graph import ast_for_target
from sldb.cli.utils import get_store_context
from sldb.store.io import load_documents_index, load_models_index, load_store_index


class DocsCLI:
    """Plural docs surface for the redesigned CLI."""

    def __init__(self) -> None:
        self._doc = DocCLI()
        self._explore = ExploreCLI()
        self._links = LinkCLI()

    def run(self, args: Any) -> int:
        command = args.docs_command
        if command == "list":
            return self.list(args)
        if command in {"create", "track", "update", "untrack"}:
            args.doc_command = {
                "create": "add",
                "track": "track",
                "update": "update",
                "untrack": "untrack",
            }[command]
            return self._doc.run(args)
        if command == "show":
            payload = ast_for_target(args.store, args.pythonpath, f"docs/{args.doc}")
            if args.format == "yaml":
                print(yaml.safe_dump(payload, sort_keys=False, allow_unicode=True))
            else:
                print(json.dumps(payload, indent=2))
            return 0
        if command == "recover":
            return self._links.recover(args)
        if command == "compose":
            return self._links.compose(args)
        if command == "explore":
            return self._explore.run(args)
        raise SystemExit(f"Unknown docs command: {command}")

    def list(self, args: Any) -> int:
        sp, root = get_store_context(args.store, mode="readonly")
        idx = load_store_index(sp)
        docs = []
        for entry in sorted(idx.models, key=lambda item: item.name):
            model_index = load_models_index(root / entry.models_index)
            docs_index = load_documents_index(root / model_index.documents_index)
            for doc in docs_index.documents:
                docs.append(
                    {
                        "name": doc.name,
                        "model": entry.name,
                        "path": doc.path,
                    }
                )
        payload = {"store": str(sp), "documents": docs}
        if args.format == "json":
            print(json.dumps(payload, indent=2))
            return 0
        if args.format == "yaml":
            print(yaml.safe_dump(payload, sort_keys=False, allow_unicode=True))
            return 0
        if not docs:
            print(f"No tracked documents in {sp}")
            return 0
        print(f"Tracked documents in {sp}:")
        for item in docs:
            print(f"- {item['name']} ({item['model']}) -> {item['path']}")
        return 0
