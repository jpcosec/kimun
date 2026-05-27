from __future__ import annotations

import json
from typing import Any
import yaml

from sldb.cli.utils import get_store_context, resolve_model_ref
from sldb.store.query import (
    find_semantic,
    find_structural,
    get_global_semantic,
    get_semantic,
    get_structural,
    glob_semantic,
    glob_structural,
    list_global_semantic,
    list_semantic,
    list_structural,
)
from sldb.core.exceptions import SLDBStoreError


class QueryCLI:
    """Handles store-based queries and exploration."""

    def _resolve_store(self, args: Any) -> Any:
        return get_store_context(args.store)[0]

    def ls(self, args: Any) -> int:
        store = self._resolve_store(args)
        if args.address.startswith("st"):
            res = list_structural(
                store, args.address, resolve_model_ref, args.pythonpath
            )
        elif args.address.startswith("se"):
            res = list_semantic(store, args.address, resolve_model_ref, args.pythonpath)
        elif args.address.startswith("gse"):
            res = list_global_semantic(
                store, args.address, resolve_model_ref, args.pythonpath
            )
        else:
            raise SLDBStoreError(f"Bad root: {args.address}")
        for item in res:
            print(item)
        return 0

    def get(self, args: Any) -> int:
        store = self._resolve_store(args)
        if args.address.startswith("st"):
            res = get_structural(
                store, args.address, resolve_model_ref, args.pythonpath
            )
        elif args.address.startswith("se"):
            res = get_semantic(store, args.address, resolve_model_ref, args.pythonpath)
        elif args.address.startswith("gse"):
            res = get_global_semantic(
                store, args.address, resolve_model_ref, args.pythonpath
            )
        else:
            raise SLDBStoreError(f"Bad root: {args.address}")

        if args.format == "text":
            print(res)
        else:
            payload = {"result": res}
            print(
                yaml.safe_dump(payload, sort_keys=False)
                if args.format == "yaml"
                else json.dumps(payload, indent=2)
            )
        return 0 if res is not None else 1

    def glob(self, args: Any) -> int:
        store = self._resolve_store(args)
        if args.address.startswith("st"):
            res = glob_structural(
                store, args.address, resolve_model_ref, args.pythonpath
            )
        elif args.address.startswith("se"):
            res = glob_semantic(store, args.address, resolve_model_ref, args.pythonpath)
        else:
            raise SLDBStoreError(f"Bad root: {args.address}")
        for item in res:
            print(item)
        return 0

    def find(self, args: Any) -> int:
        store = self._resolve_store(args)
        if args.address.startswith("st"):
            res = find_structural(
                store, args.address, args.where, resolve_model_ref, args.pythonpath
            )
        elif args.address.startswith("se"):
            res = find_semantic(
                store, args.address, args.where, resolve_model_ref, args.pythonpath
            )
        else:
            raise SLDBStoreError(f"Bad root: {args.address}")
        for item in res:
            print(item)
        return 0
