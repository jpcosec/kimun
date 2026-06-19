from __future__ import annotations

from typing import Literal

from pydantic import Field

from sldb import StructuredNLDoc


class DeskStandardsDoc(StructuredNLDoc):
    __semantics__ = {
        "type": ["desk", "standards"],
        "workspace": ["desk", "governance"],
    }
    __template__ = """# ⸢rev•title⸥

## Purpose

⸢rev,markdown•purpose⸥

## Task Rules

⸢rev,markdown•task_rules⸥

## Current Priority

⸢rev,markdown•current_priority⸥
"""

    title: str = Field(description="Standards title shown as the H1 heading.")
    purpose: str = Field(
        description="Markdown section describing what the local desk exists for."
    )
    task_rules: str = Field(
        description="Markdown section listing local task or execution rules."
    )
    current_priority: str = Field(
        default="",
        description="Markdown section describing the current focus or priority for the desk.",
    )


class DeskSpecDoc(StructuredNLDoc):
    __semantics__ = {
        "type": ["desk", "spec"],
        "workspace": ["desk", "governance"],
    }
    __template__ = """# ⸢rev•title⸥

## Product Goal

⸢rev,markdown•product_goal⸥

## Real Use Case

⸢rev,markdown•real_use_case⸥

## Minimal Feature Slice To Deliver

⸢rev,markdown•minimal_feature_slice⸥

## What Is Currently Missing

⸢rev,markdown•current_missing⸥

## Non-Goals For This Delivery

⸢rev,markdown•non_goals⸥

## Acceptance Criteria

⸢rev,markdown•acceptance_criteria⸥
"""

    title: str = Field(description="Spec title shown as the H1 heading.")
    product_goal: str = Field(
        description="Markdown section stating the product or delivery goal."
    )
    real_use_case: str = Field(
        description="Markdown section describing the concrete use case."
    )
    minimal_feature_slice: str = Field(
        description="Markdown section defining the first credible delivery slice."
    )
    current_missing: str = Field(
        description="Markdown section listing what is still missing or blocking delivery."
    )
    non_goals: str = Field(description="Markdown section listing what is out of scope.")
    acceptance_criteria: str = Field(
        description="Markdown section listing what must be true for the slice to count as delivered."
    )


class InboxNoteDoc(StructuredNLDoc):
    """Compatibility shim for tests and older local callers.

    Canonical inbox workflow ownership now lives in `deskops.models:InboxNoteDoc`.
    This local mirror preserves the historical `author` field so existing SLDB
    tests and callers that resolve `desk.models:InboxNoteDoc` keep working while
    the store consumes the canonical deskops model.
    """

    __semantics__ = {
        "type": ["desk", "inbox-note"],
        "workspace": ["desk", "inbox"],
    }
    __template__ = """---
kind: ⸢rev•kind⸥
author: ⸢rev•author⸥
created_at: ⸢rev•created_at⸥
status: ⸢rev•status⸥
---

# ⸢rev•title⸥

⸢rev,markdown•body⸥
"""

    kind: Literal["unclear", "suggestion"] = Field(
        description="Inbox note type indicating unresolved confusion or an improvement proposal."
    )
    author: str = Field(description="Source label for who or what created the inbox note.")
    created_at: str = Field(description="Timestamp for when the inbox note was created.")
    status: Literal["open", "closed"] = Field(
        description="Whether the inbox note is still open or already handled."
    )
    title: str = Field(description="Inbox note title shown as the H1 heading.")
    body: str = Field(description="Markdown body describing the question or suggestion in detail.")
