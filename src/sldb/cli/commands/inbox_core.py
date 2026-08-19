import json
from pathlib import Path
from typing import Any

import yaml

from sldb.cli.store_context import get_store_context
from sldb.store.layout import project_root
from sldb.store.resolver import find_local_store


def get_desk_root(args: Any) -> Path:
    if args.desk_root:
        return Path(args.desk_root).resolve()
    if args.store:
        return (project_root(Path(args.store).resolve()) / "desk").resolve()
    return default_desk_root()


def default_desk_root() -> Path:
    local_store = find_local_store()
    if local_store is not None:
        return (project_root(local_store) / "desk").resolve()
    return (Path.cwd() / "desk").resolve()


def get_store_context_safe(args: Any) -> tuple[Path, Path] | None:
    if args.desk_root and not args.store:
        return None
    if args.store:
        return get_store_context(args.store)
    local_store = find_local_store()
    if local_store is None:
        return None
    return local_store, project_root(local_store)


def print_payload(payload: Any, fmt: str) -> None:
    if fmt == "yaml":
        print(yaml.safe_dump(payload, sort_keys=False, allow_unicode=True))
        return
    print(json.dumps(payload, indent=2, default=str))
