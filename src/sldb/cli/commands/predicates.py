from __future__ import annotations

import json
from typing import Any

import yaml

from sldb.cli.utils import get_store_context
from sldb.core.exceptions import SLDBStoreError
from sldb.store.io import load_store_index, save_store_index
from sldb.store.models import PredicateEntry
from sldb.store.predicates import validate_predicate, validate_predicates


class PredicatesCLI:
    """Manage store-backed predicate definitions used by document links."""

    def run(self, args: Any) -> int:
        handlers = {
            "add": self.add,
            "list": self.list,
            "show": self.show,
            "validate": self.validate,
            "remove": self.remove,
        }
        handler = handlers.get(args.predicates_command)
        if not handler:
            raise SLDBStoreError(
                f"Unknown predicates command: {args.predicates_command}"
            )
        return handler(args)

    def add(self, args: Any) -> int:
        sp, _root = get_store_context(args.store)
        idx = load_store_index(sp)
        if any(entry.name == args.name for entry in idx.predicates):
            raise SLDBStoreError(f"Predicate '{args.name}' already exists.")
        entry = PredicateEntry(
            name=args.name,
            axis=args.axis.upper(),
            description=args.description or "",
        )
        errors = validate_predicate(entry)
        if errors:
            raise SLDBStoreError(" ".join(errors))
        idx.predicates.append(entry)
        save_store_index(sp, idx)
        print(f"Registered predicate '{entry.name}'")
        return 0

    def list(self, args: Any) -> int:
        sp, _root = get_store_context(args.store, mode="readonly")
        idx = load_store_index(sp)
        entries = [
            entry.model_dump()
            for entry in sorted(idx.predicates, key=lambda item: item.name)
        ]
        payload = {"store": str(sp), "predicates": entries}
        if args.format == "json":
            print(json.dumps(payload, indent=2))
        elif args.format == "yaml":
            print(yaml.safe_dump(payload, sort_keys=False, allow_unicode=True))
        elif not entries:
            print(f"No predicates registered in {sp}")
        else:
            print(f"Predicates in {sp}:")
            for entry in entries:
                print(f"- {entry['name']} | {entry['axis']} | {entry['description']}")
        return 0

    def show(self, args: Any) -> int:
        sp, _root = get_store_context(args.store, mode="readonly")
        idx = load_store_index(sp)
        entry = next((item for item in idx.predicates if item.name == args.name), None)
        if entry is None:
            raise SLDBStoreError(f"Predicate '{args.name}' is not registered.")
        payload = entry.model_dump()
        if args.format == "json":
            print(json.dumps(payload, indent=2))
        elif args.format == "yaml":
            print(yaml.safe_dump(payload, sort_keys=False, allow_unicode=True))
        else:
            print(f"{entry.name} | {entry.axis} | {entry.description}")
        return 0

    def validate(self, args: Any) -> int:
        sp, _root = get_store_context(args.store, mode="readonly")
        idx = load_store_index(sp)
        if args.name:
            entries = [item for item in idx.predicates if item.name == args.name]
            if not entries:
                raise SLDBStoreError(f"Predicate '{args.name}' is not registered.")
        else:
            entries = idx.predicates
        errors = validate_predicates(entries)
        payload = {"valid": not errors, "errors": errors}
        if args.format == "json":
            print(json.dumps(payload, indent=2))
        elif args.format == "yaml":
            print(yaml.safe_dump(payload, sort_keys=False, allow_unicode=True))
        else:
            print(
                "PASS: predicates valid" if not errors else "FAIL: predicates invalid"
            )
            for error in errors:
                print(f"- {error}")
        return 0 if not errors else 1

    def remove(self, args: Any) -> int:
        sp, _root = get_store_context(args.store)
        idx = load_store_index(sp)
        remaining = [item for item in idx.predicates if item.name != args.name]
        if len(remaining) == len(idx.predicates):
            raise SLDBStoreError(f"Predicate '{args.name}' is not registered.")
        idx.predicates = remaining
        save_store_index(sp, idx)
        print(f"Removed predicate '{args.name}'")
        return 0
