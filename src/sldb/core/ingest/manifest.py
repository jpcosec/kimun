"""
Manages the raw source manifest system for tracking file provenance.
"""
from __future__ import annotations
import csv
import hashlib
from datetime import datetime
from pathlib import Path
from wiki_compiler.contracts import RawSourceEntry

def compute_content_hash(path: Path) -> str:
    sha256 = hashlib.sha256()
    with open(path, "rb") as f:
        while chunk := f.read(8192):
            sha256.update(chunk)
    return sha256.hexdigest()

def load_manifest(manifest_path: Path) -> list[RawSourceEntry]:
    if not manifest_path.exists(): return []
    with open(manifest_path, "r", encoding="utf-8", newline="") as f:
        return [RawSourceEntry.model_validate(row) for row in csv.DictReader(f)]

def save_manifest(manifest_path: Path, entries: list[RawSourceEntry]) -> None:
    manifest_path.parent.mkdir(parents=True, exist_ok=True)
    fields = ["filename", "path", "file_kind", "content_hash", "status", "created", "notes"]
    with open(manifest_path, "w", encoding="utf-8", newline="") as f:
        writer = csv.DictWriter(f, fieldnames=fields)
        writer.writeheader()
        writer.writerows(entry.model_dump() for entry in entries)

def add_to_manifest(
    project_root: Path, manifest_path: Path, source_path: Path, notes: str = ""
) -> RawSourceEntry:
    if not source_path.exists(): raise FileNotFoundError(f"Not found: {source_path}")
    rel_path = source_path.relative_to(project_root).as_posix()
    content_hash = compute_content_hash(source_path)
    entries = load_manifest(manifest_path)
    return _update_or_add(manifest_path, entries, source_path, rel_path, content_hash, notes)

def _update_or_add(
    m_path: Path, entries: list[RawSourceEntry], s_path: Path,
    rel_path: str, c_hash: str, notes: str
) -> RawSourceEntry:
    for entry in entries:
        if entry.path == rel_path:
            return _update_existing(m_path, entries, entry, c_hash, notes)
    return _add_new(m_path, entries, s_path, rel_path, c_hash, notes)

def _update_existing(
    m_path: Path, entries: list[RawSourceEntry], entry: RawSourceEntry, c_hash: str, notes: str
) -> RawSourceEntry:
    if entry.content_hash != c_hash:
        entry.content_hash = c_hash
        entry.created = datetime.now().isoformat()
        entry.notes = notes or entry.notes
    save_manifest(m_path, entries)
    return entry

def _add_new(
    m_path: Path, entries: list[RawSourceEntry], s_path: Path,
    rel_path: str, c_hash: str, notes: str
) -> RawSourceEntry:
    new_entry = RawSourceEntry(
        filename=s_path.name, path=rel_path,
        file_kind=s_path.suffix.lstrip(".").lower() or "unknown",
        content_hash=c_hash, status="new",
        created=datetime.now().isoformat(), notes=notes
    )
    entries.append(new_entry)
    save_manifest(m_path, entries)
    return new_entry
