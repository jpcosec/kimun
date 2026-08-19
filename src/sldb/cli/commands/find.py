from __future__ import annotations
from typing import Any
from sldb.cli.graph import iter_search_records, search_records, SearchRecord
from sldb.cli.commands.find_filters import FindFilters
from sldb.cli.commands.find_format import FindFormatter

class FindCLI:
    """Unified semantic + physical retrieval."""

    def run(self, args: Any) -> int:
        """Run the find command."""
        matched = self._get_matches(args)
        if args.where:
            matched = self._apply_filters(matched, args)
        return FindFormatter().format_output(matched, args)

    def _get_matches(self, args: Any) -> list[SearchRecord]:
        records = iter_search_records(
            args.store, args.pythonpath,
            include_linked=args.global_scope,
            rebuild=getattr(args, "rebuild", False),
        )
        kinds = {args.type} if args.type != "all" else None
        return search_records(
            records, args.term,
            search_in=args.search_in, regex=args.regex,
            fuzzy=args.fuzzy, kinds=kinds,
        )

    def _apply_filters(self, matched: list[SearchRecord], args: Any) -> list[SearchRecord]:
        filters = FindFilters(args.pythonpath)
        return [r for r in matched if filters.matches(r, args.where)]
