from __future__ import annotations

from typing import Any
from sldb.cli.commands.help_texts import TOP_LEVEL_HELP, TOPIC_HELP

class HelpCLI:
    """Curated help for the redesigned CLI surface."""

    def run(self, args: Any) -> int:
        """Run the help command."""
        topic = (args.topic or "").strip().lower()
        if not topic:
            print(TOP_LEVEL_HELP)
            return 0
        print(TOPIC_HELP.get(topic, f"Unknown help topic: {args.topic}"))
        return 0 if topic in TOPIC_HELP else 1
