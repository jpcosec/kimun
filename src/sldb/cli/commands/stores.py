from __future__ import annotations

import json
from typing import Any

import yaml

from sldb.cli.commands.store import StoreCLI
from sldb.cli.utils import get_store_context
from sldb.store.io import load_store_index


class StoresCLI:
    """Plural store surface for the redesigned CLI."""

    def __init__(self) -> None:
        self._store = StoreCLI()

    def run(self, args: Any) -> int:
        if args.stores_command == "list":
            return self.list(args)
        args.store_command = args.stores_command
        return self._store.run(args)

    def list(self, args: Any) -> int:
        sp, root = get_store_context(args.store, mode="readonly")
        idx = load_store_index(sp)
        stores = [
            {"name": entry.name, "path": entry.path}
            for entry in sorted(idx.stores, key=lambda item: item.name)
        ]
        payload = {"store": str(sp), "stores": stores}
        if args.format == "json":
            print(json.dumps(payload, indent=2))
            return 0
        if args.format == "yaml":
            print(yaml.safe_dump(payload, sort_keys=False, allow_unicode=True))
            return 0
        if not stores:
            print(f"No federated stores linked in {sp}")
            return 0
        print(f"Federated stores in {sp}:")
        for item in stores:
            print(f"- {item['name']} -> {item['path']}")
        return 0
