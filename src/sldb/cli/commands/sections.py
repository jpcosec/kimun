from __future__ import annotations

from typing import Any

from sldb.cli.commands.sections_show import ShowSectionsCLI
from sldb.cli.commands.sections_find import FindSectionsCLI
from sldb.cli.commands.sections_fields import FieldsSectionsCLI


class SectionsCLI:
    """Noun-first section inspection surface."""

    def run(self, args: Any) -> int:
        if args.sections_command == "show":
            return ShowSectionsCLI().run(args)
        if args.sections_command == "find":
            return FindSectionsCLI().run(args)
        if args.sections_command == "fields":
            return FieldsSectionsCLI().run(args)
        raise SystemExit(f"Unknown sections command: {args.sections_command}")
