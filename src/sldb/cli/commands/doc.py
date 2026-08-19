"""Doc CLI commands module."""
from __future__ import annotations

from pathlib import Path
from typing import Any
import yaml

from sldb.cli.utils import get_store_context, registered_model, resolve_model_ref
from sldb.runtime.validation import render_model_markdown, validate_model_input_roundtrip
from sldb.store.io import load_store_index, load_models_index, load_documents_index, save_documents_index, save_models_index, store_lock
from sldb.store.hashing import hash_text, hash_fields, hash_documents_index
from sldb.store.semantic import rebuild_semantic_indexes, rebuild_sections_indexes
from sldb.store.ops import cascade_hash_a, track_document
from sldb.core.exceptions import SLDBValidationError, SLDBASTError, SLDBError

class DocCLI:
    """Handles document management: add, track, update."""

    def run(self, args: Any) -> int:
        """Dispatch doc subcommands.
        Args: args (Any): CLI args.
        Returns: int: Exit code.
        """
        cmd_map = {"add": self.add, "track": self.track, "update": self.update, "untrack": self.untrack}
        if args.doc_command not in cmd_map: raise SLDBError(f"Unknown doc command: {args.doc_command}")
        return cmd_map[args.doc_command](args)

    def _parse_payload(self, payload_arg: str) -> dict[str, Any]:
        p = Path(payload_arg)
        try:
            if p.exists(): return yaml.safe_load(p.read_text(encoding="utf-8"))
        except OSError: pass
        try:
            if not isinstance(data := yaml.safe_load(payload_arg), dict): raise SLDBASTError("Payload must be object.")
            return data
        except yaml.YAMLError as e: raise SLDBASTError(f"Parse error: {e}")

    def add(self, args: Any) -> int:
        """Add a doc.
        Args: args (Any): CLI args.
        Returns: int: Exit code.
        """
        sp, root = get_store_context(args.store)
        model_type, entry, idx = registered_model(sp, args.model, args.pythonpath)
        rendered = render_model_markdown(model_type, self._parse_payload(args.payload))
        if not validate_model_input_roundtrip(model_type, rendered)[0]: raise SLDBValidationError("Idempotency fail", validate_model_input_roundtrip(model_type, rendered)[1])
        out = self._resolve_doc_path(args.output, root)
        out.parent.mkdir(parents=True, exist_ok=True)
        out.write_text(rendered + "\n", encoding="utf-8")
        track_document(sp, root, idx, model_type, entry, out, args.name or out.stem, resolve_model_ref, args.pythonpath)
        print(f"Created and tracked '{args.name or out.stem}'")
        return 0

    def track(self, args: Any) -> int:
        """Track a doc.
        Args: args (Any): CLI args.
        Returns: int: Exit code.
        """
        sp, root = get_store_context(args.store)
        model_type, entry, idx = registered_model(sp, args.model, args.pythonpath)
        path = self._resolve_doc_path(args.path, root)
        if not args.force and not validate_model_input_roundtrip(model_type, path.read_text(encoding="utf-8"))[0]:
            raise SLDBValidationError("Idempotency fail", validate_model_input_roundtrip(model_type, path.read_text(encoding="utf-8"))[1])
        track_document(sp, root, idx, model_type, entry, path, args.name or path.stem, resolve_model_ref, args.pythonpath)
        print(f"Tracked '{args.name or path.stem}'")
        return 0

    def _find_doc(self, root: Path, idx: Any, doc_ref: str) -> tuple[Any, Any, Any, Any]:
        for m_entry in idx.models:
            m_idx, d_idx = load_models_index(root / m_entry.models_index), load_documents_index(root / load_models_index(root / m_entry.models_index).documents_index)
            if doc := next((d for d in d_idx.documents if d.name == doc_ref or d.path == doc_ref), None): return m_entry, m_idx, d_idx, doc
        raise SLDBError(f"Doc '{doc_ref}' not found.")

    def update(self, args: Any) -> int:
        """Update a doc.
        Args: args (Any): CLI args.
        Returns: int: Exit code.
        """
        sp, root = get_store_context(args.store)
        idx = load_store_index(sp)
        m_entry, m_idx, d_idx, doc = self._find_doc(root, idx, args.doc)
        model_type = resolve_model_ref(m_entry.model_ref, args.pythonpath)
        rendered = render_model_markdown(model_type, self._parse_payload(args.payload))
        if not validate_model_input_roundtrip(model_type, rendered)[0]: raise SLDBValidationError("Update fail", validate_model_input_roundtrip(model_type, rendered)[1])
        (root / doc.path).write_text(rendered + "\n", encoding="utf-8")
        doc.hash_c, doc.hash_d = hash_text(rendered + "\n"), hash_fields(model_type, rendered + "\n")
        self._save_updated(sp, root, idx, m_entry, m_idx, d_idx, args)
        print(f"Updated '{doc.name}'")
        return 0

    def _save_updated(self, sp: Any, root: Path, idx: Any, m_entry: Any, m_idx: Any, d_idx: Any, args: Any) -> None:
        with store_lock(sp):
            save_documents_index(root / m_idx.documents_index, d_idx)
            m_idx.hash_b = ""
            save_models_index(root / m_entry.models_index, m_idx)
            rebuild_semantic_indexes(sp, root, resolve_model_ref, args.pythonpath)
            cascade_hash_a(sp, root, idx)

    def untrack(self, args: Any) -> int:
        """Untrack a doc.
        Args: args (Any): CLI args.
        Returns: int: Exit code.
        """
        sp, root = get_store_context(args.store)
        idx = load_store_index(sp)
        m_entry, m_idx, d_idx, doc = self._find_doc(root, idx, args.doc)
        d_idx.documents = [e for e in d_idx.documents if e.name != doc.name]
        with store_lock(sp):
            save_documents_index(root / m_idx.documents_index, d_idx)
            m_idx.hash_b = hash_documents_index(d_idx)
            save_models_index(root / m_entry.models_index, m_idx)
            rebuild_semantic_indexes(sp, root, resolve_model_ref, args.pythonpath)
            rebuild_sections_indexes(sp, root, resolve_model_ref, args.pythonpath)
            cascade_hash_a(sp, root, idx)
        print(f"Untracked '{doc.name}'")
        return 0

    def _resolve_doc_path(self, raw_path: str, root: Path) -> Path:
        return Path(raw_path).resolve() if Path(raw_path).is_absolute() else (root / Path(raw_path)).resolve()
