from __future__ import annotations

from typing import Literal

from pydantic import BaseModel, Field

from sldb import StructuredNLDoc


class DeskTaskMeta(BaseModel):
    id: str = Field(description="Short task identifier used on the board and in dependencies.")
    domain: str = Field(description="Functional area of the repo that owns the task.")
    status: Literal["open", "blocked", "in_progress", "done"] = Field(
        description="Execution state of the task."
    )
    priority: Literal["p0", "p1", "p2", "p3"] = Field(
        description="Priority level used to schedule the task."
    )
    depends_on: list[str] = Field(
        default_factory=list,
        description="Other task identifiers that must be completed first.",
    )
    created: str = Field(description="Creation date for the task document.")


class DeskTaskDoc(StructuredNLDoc):
    __semantics__ = {
        "type": ["desk", "task"],
        "workspace": ["desk", "tasks"],
    }
    __template__ = """---
⸢rev,dict•meta⸥
---

# ⸢rev•title⸥

## Objective

⸢rev,markdown•objective⸥

## Reference

⸢rev,markdown•reference⸥

## What To Fix

⸢rev,markdown•what_to_fix⸥

## How To Do It

⸢rev,markdown•how_to_do_it⸥

## Validation

⸢rev,markdown•validation⸥

## Done When

⸢rev,markdown•done_when⸥
"""

    meta: DeskTaskMeta = Field(
        description="Typed task frontmatter for identity, scheduling, and dependencies."
    )
    title: str = Field(description="Task title shown as the H1 heading.")
    objective: str = Field(description="Markdown section describing the task objective.")
    reference: str = Field(
        default="",
        description="Markdown section listing relevant files, docs, or commands.",
    )
    what_to_fix: str = Field(
        default="",
        description="Markdown section describing the intended end state.",
    )
    how_to_do_it: str = Field(
        default="",
        description="Markdown section describing the execution approach.",
    )
    validation: str = Field(
        default="",
        description="Markdown section describing how the task is verified.",
    )
    done_when: str = Field(
        default="",
        description="Markdown section describing the concrete completion gate.",
    )


class DeskPillDoc(StructuredNLDoc):
    __semantics__ = {
        "type": ["desk", "pill"],
        "workspace": ["desk", "pills"],
    }
    __template__ = """---
pill_type: ⸢rev•pill_type⸥
scope: ⸢rev•scope⸥
nature: ⸢rev•nature⸥
bound_to: ⸢rev•bound_to⸥
created: ⸢rev•created⸥
lifecycle: ⸢rev•lifecycle⸥
---

# ⸢rev•title⸥

⸢rev,markdown•body⸥
"""

    pill_type: Literal["guardrail", "decision", "pattern", "model", "warning"] = Field(
        description="Type that determines how the pill should be interpreted."
    )
    scope: Literal["global", "domain"] = Field(
        description="Whether the pill applies globally or only to this repo domain."
    )
    nature: Literal["context", "implementation"] = Field(
        description="Whether the pill is contextual guidance or implementation detail."
    )
    bound_to: str = Field(
        description="What code, workflow, or repo surface the pill is bound to."
    )
    created: str = Field(description="Creation date for the pill.")
    lifecycle: Literal["current", "deprecated"] = Field(
        description="Whether the pill is still active guidance."
    )
    title: str = Field(description="Pill title shown as the H1 heading.")
    body: str = Field(description="Markdown body containing the pill guidance.")


class InboxNoteDoc(StructuredNLDoc):
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


class DeskBoardDoc(StructuredNLDoc):
    __semantics__ = {
        "type": ["desk", "board"],
        "workspace": ["desk", "tasks"],
    }
    __template__ = """# ⸢rev•title⸥

## Current State Summary

⸢rev,markdown•current_state_summary⸥

## Active

⸢rev,markdown•active⸥

## Recently Closed

⸢rev,markdown•recently_closed⸥

## Working Rules

⸢rev,markdown•working_rules⸥
"""

    title: str = Field(description="Board title shown as the H1 heading.")
    current_state_summary: str = Field(
        description="Markdown summary of current desk state and active delivery focus."
    )
    active: str = Field(
        description="Markdown section containing active task rows or a no-active-work statement."
    )
    recently_closed: str = Field(
        description="Markdown section listing recently completed task outcomes."
    )
    working_rules: str = Field(
        description="Markdown section containing execution rules for future tasks."
    )


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
