from typing import Any
from sldb.cli.commands.fields_show import show_field, query_field
from sldb.cli.commands.fields_mutate import mutate_field

class FieldsCLI:
    """Field CRUD and query surface."""

    def run(self, args: Any) -> int:
        cmd = args.fields_command
        if cmd == "show": return show_field(args)
        if cmd == "query": return query_field(args)
        if cmd in {"create", "update", "remove", "append", "clean"}: return mutate_field(args, cmd)
        raise SystemExit(f"Unknown fields command: {cmd}")

    def show(self, args: Any) -> int: return show_field(args)
    def query(self, args: Any) -> int: return query_field(args)
    def create(self, args: Any) -> int: return mutate_field(args, "create")
    def update(self, args: Any) -> int: return mutate_field(args, "update")
    def remove(self, args: Any) -> int: return mutate_field(args, "remove")
    def append(self, args: Any) -> int: return mutate_field(args, "append")
    def clean(self, args: Any) -> int: return mutate_field(args, "clean")
