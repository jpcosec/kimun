from typing import Any

from sldb.cli.commands.inbox_list import list_notes
from sldb.cli.commands.inbox_show import show_note
from sldb.cli.commands.inbox_create import create_note

class InboxCLI:
    """Log unclear points or suggestions into desk/inbox/."""

    def run(self, args: Any) -> int:
        if args.list:
            return list_notes(args)
        if args.show:
            return show_note(args)
        if not args.message:
            print("Provide a message or use --list/--show.")
            return 1
        return create_note(args)
