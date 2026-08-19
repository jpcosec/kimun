from typing import Any
from pathlib import Path
from sldb.cli.commands.inbox_core import get_desk_root, print_payload
from sldb.cli.commands.inbox_parse import iter_notes, parse_note, note_summary


def list_notes(args: Any) -> int:
    notes = iter_notes(get_desk_root(args))[: args.limit]
    if args.format == "text":
        return print_notes_text(notes)
    print_payload({"notes": [note_summary(path) for path in notes]}, args.format)
    return 0


def print_notes_text(notes: list[Path]) -> int:
    if not notes:
        print("No inbox notes.")
        return 0
    for path in notes:
        frontmatter, title, _body = parse_note(path)
        print(f"{path.stem} | {frontmatter.get('kind', '')} | {frontmatter.get('status', '')} | {title}")
    return 0
