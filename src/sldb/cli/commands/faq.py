from __future__ import annotations

import json
from dataclasses import dataclass
from pathlib import Path
from typing import Any

import yaml


@dataclass
class FAQEntry:
    index: int
    title: str
    slug: str
    body: str


class FAQCLI:
    """Question-oriented FAQ browser."""

    def run(self, args: Any) -> int:
        entries = self._load_entries(Path(args.faq_path))
        question = (args.question or "").strip()
        if not question:
            payload = [
                {"index": entry.index, "slug": entry.slug, "title": entry.title}
                for entry in entries
            ]
            if args.format == "text":
                print("SLDB FAQ questions:")
                for entry in payload:
                    print(f"{entry['index']}. {entry['title']} [{entry['slug']}]")
                return 0
            self._print({"questions": payload}, args.format)
            return 0

        entry = self._select_entry(entries, question)
        if entry is None:
            print(f"Unknown FAQ question: {args.question}")
            return 1

        payload = {
            "index": entry.index,
            "slug": entry.slug,
            "title": entry.title,
            "body": entry.body,
        }
        if args.format == "text":
            print(f"## {entry.title}\n")
            print(entry.body)
            return 0
        self._print(payload, args.format)
        return 0

    def _load_entries(self, path: Path) -> list[FAQEntry]:
        text = path.read_text(encoding="utf-8")
        entries: list[FAQEntry] = []
        parts = text.split("\n## ")
        for index, chunk in enumerate(parts[1:], start=1):
            title, _, body = chunk.partition("\n")
            entries.append(
                FAQEntry(
                    index=index,
                    title=title.strip(),
                    slug=self._slug(title),
                    body=body.strip(),
                )
            )
        return entries

    def _select_entry(self, entries: list[FAQEntry], question: str) -> FAQEntry | None:
        if question.isdigit():
            index = int(question)
            return next((entry for entry in entries if entry.index == index), None)
        lowered = question.lower()
        return next(
            (
                entry
                for entry in entries
                if lowered == entry.slug or lowered in entry.slug or lowered in entry.title.lower()
            ),
            None,
        )

    def _print(self, payload: dict[str, Any], fmt: str) -> None:
        if fmt == "yaml":
            print(yaml.safe_dump(payload, sort_keys=False, allow_unicode=True))
            return
        print(json.dumps(payload, indent=2))

    def _slug(self, text: str) -> str:
        cleaned = "".join(ch.lower() if ch.isalnum() else "-" for ch in text)
        while "--" in cleaned:
            cleaned = cleaned.replace("--", "-")
        return cleaned.strip("-") or "question"
