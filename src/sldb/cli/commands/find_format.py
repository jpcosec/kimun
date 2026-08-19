from __future__ import annotations
import json
import yaml
from typing import Any
from sldb.cli.graph import SearchRecord

class FindFormatter:
    """Formats search results."""

    def format_output(self, records: list[SearchRecord], args: Any) -> int:
        """Format and print the matching records."""
        payload = [self._serialize(record) for record in records]
        payload = self._apply_select(payload, args.select) if args.select else payload

        if args.format == "text": self._print_text(payload)
        elif args.format == "yaml": self._print_yaml(payload)
        else: self._print_json(payload)
            
        return 0 if payload else 1

    def _serialize(self, record: SearchRecord) -> dict[str, Any]:
        data = record.as_dict()
        if record.kind == "doc":
            data["semantic_tags"] = list(record.semantic)
        return data

    def _apply_select(self, payload: list[dict[str, Any]], select: str) -> list[dict[str, Any]]:
        fields = [part.strip() for part in select.split(",") if part.strip()]
        return [{key: item.get(key) for key in fields} for item in payload]

    def _print_text(self, payload: list[dict[str, Any]]) -> None:
        for item in payload:
            print(self._format_line(item))

    def _print_yaml(self, payload: list[dict[str, Any]]) -> None:
        out = yaml.safe_dump({"results": payload}, sort_keys=False, allow_unicode=True)
        print(out)

    def _print_json(self, payload: list[dict[str, Any]]) -> None:
        print(json.dumps({"results": payload}, indent=2))

    def _format_line(self, item: dict[str, Any]) -> str:
        parts = [item.get("kind", "?")]
        for key in ("store", "model", "doc", "field", "path", "name"):
            value = item.get(key)
            if value:
                parts.append(str(value))
        if "value" in item and item.get("value") not in (None, ""):
            parts.append(f"value={item['value']}")
        return " | ".join(parts)
