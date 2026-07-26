from __future__ import annotations

import json
import re
from datetime import datetime
from pathlib import Path
from typing import Any

import yaml

from sldb.cli.utils import get_store_context, registered_model, resolve_model_ref
from sldb.core.exceptions import SLDBStoreError, SLDBValidationError
from sldb.runtime.validation import validate_model_input_roundtrip
from sldb.store.layout import project_root
from sldb.store.ops import track_document
from sldb.store.resolver import find_local_store


class InboxCLI:
    """Log unclear points or suggestions into desk/inbox/."""

    _MIN_DETAIL_CHARS = 24

    def run(self, args: Any) -> int:
        if args.list:
            return self._list_notes(args)
        if args.show:
            return self._show_note(args)
        if not args.message:
            print("Provide a message or use --list/--show.")
            return 1

        desk_root = self._desk_root(args)
        inbox_dir = desk_root / "inbox"
        inbox_dir.mkdir(parents=True, exist_ok=True)

        created_at = datetime.now()
        title = args.title.strip() if args.title else self._derive_title(args.message)
        self._validate_message(args.message, title)
        slug = self._slug(title)
        path = inbox_dir / f"{created_at.strftime('%Y%m%d-%H%M%S')}-{args.kind}-{slug}.md"
        path.write_text(
            self._render_note(args.kind, title, args.message, args.author, created_at),
            encoding="utf-8",
        )
        tracked_name = self._auto_track_note(args, path)
        print(f"Wrote {path}")
        if tracked_name:
            print(f"Tracked '{tracked_name}'")
        return 0

    def _validate_message(self, message: str, title: str) -> None:
        normalized = " ".join(message.strip().split())
        if len(normalized) >= self._MIN_DETAIL_CHARS:
            return
        if "\n" in message.strip():
            return
        if normalized.lower() == title.strip().lower():
            raise SLDBStoreError(
                "Inbox note needs more detail. Provide at least one short explanatory sentence "
                f"or a multi-line note; title-only placeholders under {self._MIN_DETAIL_CHARS} "
                "characters are rejected."
            )

    def _desk_root(self, args: Any) -> Path:
        if args.desk_root:
            return Path(args.desk_root).resolve()
        if args.store:
            return (project_root(Path(args.store).resolve()) / "desk").resolve()
        local_store = find_local_store()
        if local_store is not None:
            return (project_root(local_store) / "desk").resolve()
        return (Path.cwd() / "desk").resolve()

    def _list_notes(self, args: Any) -> int:
        notes = self._iter_notes(self._desk_root(args))[: args.limit]
        if args.format == "text":
            if not notes:
                print("No inbox notes.")
                return 0
            for path in notes:
                frontmatter, title, _body = self._parse_note(path)
                print(
                    f"{path.stem} | {frontmatter.get('kind', '')} | "
                    f"{frontmatter.get('status', '')} | {title}"
                )
            return 0
        payload = {"notes": [self._note_summary(path) for path in notes]}
        self._print(payload, args.format)
        return 0

    def _show_note(self, args: Any) -> int:
        note = self._resolve_note(self._desk_root(args), args.show)
        if note is None:
            print(f"Unknown inbox note: {args.show}")
            return 1
        detail = self._note_detail(note)
        if args.format == "text":
            print(f"# {detail['title']}\n")
            for meta in ("kind", "author", "created_at", "status", "path"):
                if meta in detail:
                    print(f"{meta}: {detail[meta]}")
            print(f"\n{detail.get('body', '')}")
            return 0
        self._print(detail, args.format)
        return 0

    def _iter_notes(self, desk_root: Path) -> list[Path]:
        inbox_dir = desk_root / "inbox"
        if not inbox_dir.exists():
            return []
        return sorted(inbox_dir.glob("*.md"), reverse=True)

    def _resolve_note(self, desk_root: Path, raw: str) -> Path | None:
        notes = self._iter_notes(desk_root)
        exact = next((path for path in notes if raw in {path.name, path.stem}), None)
        if exact is not None:
            return exact
        lowered = raw.lower()
        return next((path for path in notes if lowered in path.stem.lower()), None)

    def _parse_note(self, path: Path) -> tuple[dict[str, str], str, str]:
        text = path.read_text(encoding="utf-8")
        frontmatter: dict[str, str] = {}
        body = text
        if text.startswith("---\n"):
            _, rest = text.split("---\n", 1)
            fm_block, body = rest.split("\n---\n", 1)
            frontmatter = yaml.safe_load(fm_block) or {}
        lines = body.strip().splitlines()
        title = lines[0].lstrip("# ").strip() if lines and lines[0].startswith("# ") else path.stem
        content = "\n".join(lines[1:]).strip() if lines and lines[0].startswith("# ") else body.strip()
        return frontmatter, title, content

    def _note_summary(self, path: Path) -> dict[str, str]:
        frontmatter, title, _ = self._parse_note(path)
        return {
            "id": path.stem,
            "path": str(path),
            "kind": frontmatter.get("kind", ""),
            "status": frontmatter.get("status", ""),
            "created_at": frontmatter.get("created_at", ""),
            "title": title,
        }

    def _note_detail(self, path: Path) -> dict[str, str]:
        frontmatter, title, body = self._parse_note(path)
        return {
            "id": path.stem,
            "path": str(path),
            "title": title,
            "body": body,
            **frontmatter,
        }

    def _render_note(
        self, kind: str, title: str, message: str, author: str, created_at: datetime
    ) -> str:
        return "\n".join(
            [
                "---",
                f"kind: {kind}",
                f"author: {author}",
                f"created_at: {created_at.isoformat(timespec='seconds')}",
                "status: open",
                "---",
                "",
                f"# {title}",
                "",
                message.strip(),
                "",
            ]
        )

    def _auto_track_note(self, args: Any, path: Path) -> str | None:
        context = self._store_context(args)
        if context is None:
            return None
        store_path, root = context
        try:
            model_type, entry, idx = registered_model(
                store_path, "InboxNoteDoc", args.pythonpath
            )
        except SLDBStoreError:
            return None
        valid, details = validate_model_input_roundtrip(
            model_type, path.read_text(encoding="utf-8")
        )
        if not valid:
            raise SLDBValidationError("Inbox note does not validate.", details)
        doc_name = path.stem.split("-", 2)[-1]
        track_document(
            store_path,
            root,
            idx,
            model_type,
            entry,
            path,
            doc_name,
            resolve_model_ref,
            args.pythonpath,
        )
        return doc_name

    def _store_context(self, args: Any) -> tuple[Path, Path] | None:
        if args.desk_root and not args.store:
            return None
        if args.store:
            return get_store_context(args.store)
        local_store = find_local_store()
        if local_store is None:
            return None
        return local_store, project_root(local_store)

    def _derive_title(self, message: str) -> str:
        first = message.strip().splitlines()[0] if message.strip() else "Inbox note"
        return first[:72]

    def _slug(self, text: str) -> str:
        slug = re.sub(r"[^a-z0-9]+", "-", text.lower()).strip("-")
        return slug or "note"

    def _print(self, payload: Any, fmt: str) -> None:
        if fmt == "yaml":
            print(yaml.safe_dump(payload, sort_keys=False, allow_unicode=True))
            return
        print(json.dumps(payload, indent=2, default=str))
