from __future__ import annotations

import json
from typing import Any
import yaml

from sldb.cli.store_context import get_store_context
from sldb.store.io import load_documents_index, load_models_index, load_store_index

class ModelsListCLI:
    def list(self, args: Any) -> int:
        sp, root = get_store_context(args.store, mode="readonly")
        idx = load_store_index(sp)
        models = self._build_model_list(idx, root)
        return self._output_models_list(sp, models, args.format)

    def _build_model_list(self, idx: Any, root: Any) -> list[dict[str, Any]]:
        models = []
        for entry in sorted(idx.models, key=lambda item: item.name):
            m_idx = load_models_index(root / entry.models_index)
            d_idx = load_documents_index(root / m_idx.documents_index)
            models.append(self._build_model_dict(entry, m_idx, d_idx))
        return models

    def _build_model_dict(self, entry: Any, m_idx: Any, d_idx: Any) -> dict[str, Any]:
        return {
            "name": entry.name,
            "model_ref": entry.model_ref,
            "path": entry.path,
            "version": m_idx.version,
            "canonical": getattr(m_idx, "canonical", False),
            "family": getattr(m_idx, "family", None),
            "semantics": list(getattr(m_idx, "semantics", [])),
            "documents": len(d_idx.documents),
        }

    def _output_models_list(self, sp: Any, models: list[dict[str, Any]], fmt: str) -> int:
        payload = {"store": str(sp), "models": models}
        if fmt == "json":
            print(json.dumps(payload, indent=2))
            return 0
        if fmt == "yaml":
            print(yaml.safe_dump(payload, sort_keys=False, allow_unicode=True))
            return 0
        return self._print_models_text(sp, models)

    def _print_models_text(self, sp: Any, models: list[dict[str, Any]]) -> int:
        if not models:
            print(f"No models registered in {sp}")
            return 0
        print(f"Models in {sp}:")
        for item in models:
            extra = [f"{item['documents']} docs", f"v{item['version']}"]
            if item["canonical"]:
                extra.append("canonical")
            print(f"- {item['name']} | {item['model_ref']} | {', '.join(extra)}")
        return 0
