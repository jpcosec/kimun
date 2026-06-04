from __future__ import annotations

from datetime import datetime, timezone
from importlib.metadata import PackageNotFoundError, version
from pathlib import Path
from typing import Any, Callable

from sldb.store.io import (
    load_documents_index,
    load_models_index,
    load_sections_index,
    load_semantic_dag,
    load_store_index,
    store_lock,
)
from sldb.store.layout import semantic_dag_path, semantic_index_path, store_index_path
from sldb.store.semantic import rebuild_sections_indexes, rebuild_semantic_indexes


def export_kgdb_semantic_payload(
    store_path: Path,
    project_root: Path,
    resolve_model_ref: Callable[..., Any] | None = None,
    pythonpath: str | None = None,
    *,
    rebuild: bool = False,
    command: list[str] | None = None,
) -> dict[str, Any]:
    """Build the KGDB semantic export payload from persisted SLDB indexes."""

    if rebuild:
        if resolve_model_ref is None:
            raise ValueError("resolve_model_ref is required when rebuild=True")
        with store_lock(store_path):
            rebuild_semantic_indexes(
                store_path, project_root, resolve_model_ref, pythonpath
            )
            rebuild_sections_indexes(
                store_path, project_root, resolve_model_ref, pythonpath
            )

    store_index = load_store_index(store_path)
    dag = load_semantic_dag(store_path)
    models: list[dict[str, Any]] = []
    documents: list[dict[str, Any]] = []
    sections: list[dict[str, Any]] = []

    for model_entry in sorted(store_index.models, key=lambda item: item.name):
        models_idx = load_models_index(project_root / model_entry.models_index)
        docs_idx = load_documents_index(project_root / models_idx.documents_index)
        sections_idx = (
            load_sections_index(project_root / models_idx.sections_index)
            if models_idx.sections_index
            else None
        )

        models.append(
            {
                "name": models_idx.name,
                "model_ref": models_idx.model_ref,
                "path": models_idx.path,
                "models_index": model_entry.models_index,
                "documents_index": models_idx.documents_index,
                "sections_index": models_idx.sections_index,
                "version": models_idx.version,
                "canonical": models_idx.canonical,
                "family": models_idx.family,
                "semantics": sorted(set(models_idx.semantics)),
                "base_models": sorted(set(models_idx.base_models)),
                "hash_b": models_idx.hash_b,
            }
        )

        for doc in sorted(docs_idx.documents, key=lambda item: item.name):
            doc_id = f"{models_idx.name}:{doc.name}"
            documents.append(
                {
                    "id": doc_id,
                    "name": doc.name,
                    "model": models_idx.name,
                    "path": doc.path,
                    "hash_c": doc.hash_c,
                    "hash_d": doc.hash_d,
                    "semantic_tags": sorted(set(doc.semantic_tags)),
                }
            )

        section_documents = sections_idx.documents if sections_idx is not None else []
        for doc_sections in sorted(section_documents, key=lambda item: item.doc_name):
            document_id = f"{models_idx.name}:{doc_sections.doc_name}"
            for section in doc_sections.sections:
                item = {
                    "id": f"{document_id}#{section.path}",
                    "document_id": document_id,
                    "path": section.path,
                    "title": section.title,
                    "breadcrumbs": section.breadcrumbs,
                    "about": section.about,
                    "semantic_tags": section.semantic_tags,
                    "slug": section.slug,
                    "level": section.level,
                    "line_start": section.line_start,
                    "line_end": section.line_end,
                }
                sections.append(item)

    return {
        "contract": {
            "name": "sldb_kgdb_semantic_export",
            "version": 1,
            "generated_at": _utc_now(),
        },
        "producer": {
            "name": "sldb",
            "version": _producer_version(),
            "command": command or [],
        },
        "store": {
            "root": str(project_root),
            "store_path": str(store_path),
            "hash_a": store_index.hash_a,
            "runtime_sources": {
                "store_index": _display_path(store_index_path(store_path), project_root),
                "semantic_index": _display_path(
                    semantic_index_path(store_path), project_root
                ),
                "semantic_dag": _display_path(semantic_dag_path(store_path), project_root),
                "sections_indexes": [
                    model["sections_index"]
                    for model in models
                    if model.get("sections_index")
                ],
            },
        },
        "models": models,
        "documents": documents,
        "sections": sections,
        "semantic_dag": {
            "nodes": [
                {"id": node.id, "parents": sorted(set(node.parents))}
                for node in sorted(dag.nodes, key=lambda item: item.id)
            ],
            "equivalences": {
                key: sorted(set(values))
                for key, values in sorted(dag.equivalences.items())
            },
        },
    }


def _display_path(path: Path, project_root: Path) -> str:
    try:
        return str(path.relative_to(project_root))
    except ValueError:
        return str(path)


def _producer_version() -> str:
    try:
        return version("sldb")
    except PackageNotFoundError:
        return "0.1.0"


def _utc_now() -> str:
    return datetime.now(timezone.utc).replace(microsecond=0).isoformat().replace(
        "+00:00", "Z"
    )
