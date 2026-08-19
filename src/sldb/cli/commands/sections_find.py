from __future__ import annotations
import json
from sldb.cli.commands.find_filter import where_matches
from typing import Any
import yaml
from sldb.cli.graph_ops import iter_search_records, search_records

class FindSectionsCLI:
    def run(self, args: Any) -> int:
        matched = self._get_find_matches(args)
        payload = [r.as_dict() for r in matched]
        return self._format_find_payload(payload, args.format)

    def _get_find_matches(self, args: Any) -> list:
        records = iter_search_records(
            args.store, args.pythonpath,
            include_linked=getattr(args, "global_scope", False), rebuild=getattr(args, "rebuild", False),
        )
        matched = search_records(
            records, args.term, search_in=args.search_in,
            regex=args.regex, fuzzy=args.fuzzy, kinds={"section"},
        )
        return self._filter_find_where(matched, args)

    def _filter_find_where(self, matched: list, args: Any) -> list:
        if not hasattr(args, "where") or not args.where:
            return matched
        from sldb.cli.commands.find import FindCLI

        return [r for r in matched if where_matches(r, args.where, args.pythonpath)]

    def _format_find_payload(self, payload: list[dict], fmt: str) -> int:
        if fmt == "text":
            self._print_find_text(payload)
        elif fmt == "yaml":
            print(yaml.safe_dump({"results": payload}, sort_keys=False, allow_unicode=True))
        else:
            print(json.dumps({"results": payload}, indent=2))
        return 0 if payload else 1

    def _print_find_text(self, payload: list[dict]) -> None:
        for item in payload:
            parts = [item.get("title", ""), f"[{item.get('path', '')}]"]
            if item.get("about"):
                parts.append(f"about={', '.join(item['about'][:3])}")
            print(" | ".join(p.strip() for p in parts if p.strip()))
