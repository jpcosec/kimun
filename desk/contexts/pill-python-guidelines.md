---
id: pill-python-guidelines
tags:
- workspace:desk
---

# Pill: Python CLI Guidelines

This pill ensures the Python Orchestration layer (`sldb-cli`) respects the architectural boundaries:
- **No Heavy Lifting**: Python must not perform heavy parsing or graph traversal logic. It delegates to the Rust Core via `pyo3`.
- **Git as Transport**: Python is solely responsible for Git integration (commit, push, tracking). Rust does not know about Git.
- **Direct vs Store-Backed**: Abstract away whether a command is operating directly on a file or on the store. The boundary should be seamless.
