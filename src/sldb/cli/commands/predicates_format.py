import json
from typing import Any
import yaml

def print_list_text(entries: list, sp: str) -> None:
    if not entries:
        print(f"No predicates registered in {sp}")
        return
    print(f"Predicates in {sp}:")
    for entry in entries:
        print(f"- {entry['name']} | {entry['axis']} | {entry['description']}")

def print_list_payload(fmt: str, payload: dict, entries: list, sp: str) -> None:
    if fmt == "json":
        print(json.dumps(payload, indent=2))
    elif fmt == "yaml":
        print(yaml.safe_dump(payload, sort_keys=False, allow_unicode=True))
    else:
        print_list_text(entries, sp)

def print_show_payload(fmt: str, entry: Any) -> None:
    payload = entry.model_dump()
    if fmt == "json":
        print(json.dumps(payload, indent=2))
    elif fmt == "yaml":
        print(yaml.safe_dump(payload, sort_keys=False, allow_unicode=True))
    else:
        print(f"{entry.name} | {entry.axis} | {entry.description}")

def print_validate_text(errors: list) -> None:
    print("PASS: predicates valid" if not errors else "FAIL: predicates invalid")
    for error in errors:
        print(f"- {error}")

def print_validate_payload(fmt: str, errors: list) -> None:
    payload = {"valid": not errors, "errors": errors}
    if fmt == "json":
        print(json.dumps(payload, indent=2))
    elif fmt == "yaml":
        print(yaml.safe_dump(payload, sort_keys=False, allow_unicode=True))
    else:
        print_validate_text(errors)
