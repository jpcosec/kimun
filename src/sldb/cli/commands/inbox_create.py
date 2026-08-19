import re
from datetime import datetime
from pathlib import Path
from typing import Any

from sldb.core.exceptions import SLDBStoreError, SLDBValidationError
from sldb.cli.model_utils import registered_model, resolve_model_ref
from sldb.runtime.validation import validate_model_input_roundtrip
from sldb.store.ops import track_document

from sldb.cli.commands.inbox_core import get_desk_root, get_store_context_safe

MIN_DETAIL_CHARS = 24


def create_note(args: Any) -> int:
    inbox_dir = prepare_inbox_dir(args)
    created_at = datetime.now()
    title = args.title.strip() if args.title else derive_title(args.message)
    validate_message(args.message, title)
    
    path = write_note_file(args, inbox_dir, title, created_at)
    return track_and_print_note(args, path)


def prepare_inbox_dir(args: Any) -> Path:
    inbox_dir = get_desk_root(args) / "inbox"
    inbox_dir.mkdir(parents=True, exist_ok=True)
    return inbox_dir


def write_note_file(args: Any, inbox_dir: Path, title: str, created_at: datetime) -> Path:
    s = slug(title)
    path = inbox_dir / f"{created_at.strftime('%Y%m%d-%H%M%S')}-{args.kind}-{s}.md"
    content = render_note(args.kind, title, args.message, args.author, created_at)
    path.write_text(content, encoding="utf-8")
    return path


def track_and_print_note(args: Any, path: Path) -> int:
    tracked_name = auto_track_note(args, path)
    print(f"Wrote {path}")
    if tracked_name:
        print(f"Tracked '{tracked_name}'")
    return 0


def validate_message(message: str, title: str) -> None:
    normalized = " ".join(message.strip().split())
    if len(normalized) >= MIN_DETAIL_CHARS or "\n" in message.strip():
        return
    if normalized.lower() == title.strip().lower():
        raise_validation_error()


def raise_validation_error() -> None:
    raise SLDBStoreError(
        f"Inbox note needs more detail. Provide at least one short explanatory sentence "
        f"or a multi-line note; title-only placeholders under {MIN_DETAIL_CHARS} "
        "characters are rejected."
    )


def render_note(kind: str, title: str, message: str, author: str, created_at: datetime) -> str:
    lines = [
        "---", f"kind: {kind}", f"author: {author}",
        f"created_at: {created_at.isoformat(timespec='seconds')}",
        "status: open", "---", "", f"# {title}", "", message.strip(), ""
    ]
    return "\n".join(lines)


def derive_title(message: str) -> str:
    first = message.strip().splitlines()[0] if message.strip() else "Inbox note"
    return first[:72]


def slug(text: str) -> str:
    s = re.sub(r"[^a-z0-9]+", "-", text.lower()).strip("-")
    return s or "note"


def auto_track_note(args: Any, path: Path) -> str | None:
    context = get_store_context_safe(args)
    if context is None:
        return None
    store_path, root = context
    try:
        model_type, entry, idx = registered_model(store_path, "InboxNoteDoc", args.pythonpath)
        return do_track(args, path, store_path, root, model_type, entry, idx)
    except SLDBStoreError:
        return None


def do_track(args: Any, path: Path, store_path: Path, root: Path, model_type: Any, entry: Any, idx: Any) -> str:
    text = path.read_text(encoding="utf-8")
    valid, details = validate_model_input_roundtrip(model_type, text)
    if not valid:
        raise SLDBValidationError("Inbox note does not validate.", details)
    doc_name = path.stem.split("-", 2)[-1]
    track_document(store_path, root, idx, model_type, entry, path, doc_name, resolve_model_ref, args.pythonpath)
    return doc_name
