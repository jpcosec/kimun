from __future__ import annotations

import re
from pathlib import Path
from typing import Any

from sldb.store.layout import project_root
from sldb.store.io import load_semantic_index
from sldb.store.query import load_runtime_documents
from sldb.store.query_engine.models import RuntimeDocument


def _match_semantic_pattern(tag: str, pattern: str) -> bool:
    """Matches a semantic tag against a glob-like pattern (** or *)."""
    return SemanticUtils.match_semantic_pattern(tag, pattern)


def _semantic_children(tags: list[str], prefix: str) -> list[str]:
    """Retrieves child semantic nodes for a given prefix."""
    return SemanticUtils.semantic_children(tags, prefix)


def _local_semantic_docs(
    store_path: Path, resolve_model_ref, pythonpath: str | None = None
) -> tuple[list[RuntimeDocument], Any]:
    """Loads docs and the semantic index, rebuilding if necessary."""
    return SemanticUtils.local_semantic_docs(store_path, resolve_model_ref, pythonpath)


class SemanticUtils:
    """Utilities for semantic query processing."""

    @classmethod
    def match_semantic_pattern(cls, tag: str, pattern: str) -> bool:
        """Matches a semantic tag against a glob-like pattern (** or *)."""
        escaped = re.escape(pattern)
        escaped = escaped.replace(re.escape("**"), ".*")
        escaped = escaped.replace(re.escape("*"), "[^.]+")
        return re.fullmatch(escaped, tag) is not None

    @classmethod
    def semantic_children(cls, tags: list[str], prefix: str) -> list[str]:
        """Retrieves child semantic nodes for a given prefix."""
        children = set()
        prefix_dot = f"{prefix}." if prefix else ""
        for tag in tags:
            cls._add_child(children, tag, prefix_dot)
        return sorted(children)

    @classmethod
    def _add_child(cls, children: set[str], tag: str, prefix_dot: str) -> None:
        if not tag.startswith(prefix_dot):
            return
        remainder = tag[len(prefix_dot) :]
        if remainder and "." in remainder:
            children.add(remainder.split(".", 1)[0])

    @classmethod
    def local_semantic_docs(
        cls, store_path: Path, resolve_model_ref, pythonpath: str | None = None
    ) -> tuple[list[RuntimeDocument], Any]:
        """Loads docs and the semantic index, rebuilding if necessary."""
        docs = load_runtime_documents(store_path, resolve_model_ref, pythonpath)
        semantic_index = load_semantic_index(store_path)
        if not semantic_index.documents:
            semantic_index = cls._rebuild_index(store_path, resolve_model_ref, pythonpath)
        return docs, semantic_index

    @classmethod
    def _rebuild_index(cls, store_path: Path, resolve_model_ref, pythonpath: str | None = None) -> Any:
        from sldb.store.semantic import rebuild_semantic_indexes

        rebuild_semantic_indexes(
            store_path,
            project_root(store_path),
            resolve_model_ref,
            pythonpath,
        )
        return load_semantic_index(store_path)
