from __future__ import annotations

import re
from collections.abc import Iterable

from sldb.store.models import PredicateEntry


_PREDICATE_NAME = re.compile(r"^[A-Za-z0-9_-]+$")
_AXIS_NAME = re.compile(r"^[A-Za-z0-9_-]+$")

_DEFAULT_PREDICATES = (
    ("is_solved_by", "HOW", "Identifies how the source is solved."),
    ("implements", "HOW", "Identifies what the source implements."),
    ("mathematically_proves", "WHY", "Provides a mathematical proof."),
    ("explains_failure_of", "WHY", "Explains why the target fails."),
    ("grounded_by", "PROVENANCE", "Identifies supporting provenance."),
    ("restricts", "WHEN_WHERE", "Restricts the target context."),
    ("defines", "WHAT", "Defines the target concept."),
)


def default_predicates() -> list[PredicateEntry]:
    """Return fresh predicate definitions for a newly initialized store."""
    return [
        PredicateEntry(name=name, axis=axis, description=description)
        for name, axis, description in _DEFAULT_PREDICATES
    ]


def validate_predicate(entry: PredicateEntry) -> list[str]:
    errors: list[str] = []
    if not _PREDICATE_NAME.fullmatch(entry.name):
        errors.append(
            f"Predicate name '{entry.name}' must contain only letters, numbers, '_' or '-'."
        )
    if not _AXIS_NAME.fullmatch(entry.axis):
        errors.append(
            f"Predicate axis '{entry.axis}' must contain only letters, numbers, '_' or '-'."
        )
    return errors


def validate_predicates(entries: Iterable[PredicateEntry]) -> list[str]:
    errors: list[str] = []
    seen: set[str] = set()
    for entry in entries:
        errors.extend(validate_predicate(entry))
        if entry.name in seen:
            errors.append(f"Duplicate predicate '{entry.name}'.")
        seen.add(entry.name)
    return errors


def predicate_axes(entries: Iterable[PredicateEntry]) -> dict[str, str]:
    """Build the parser lookup from persisted predicate definitions."""
    return {entry.name: entry.axis for entry in entries}
