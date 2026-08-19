from __future__ import annotations
import fnmatch
from dataclasses import dataclass

@dataclass(frozen=True)
class IgnoreRule:
    pattern: str
    reason: str | None

    def matches(self, path: str) -> bool:
        if fnmatch.fnmatch(path, self.pattern) or fnmatch.fnmatch(path, f"{self.pattern}/**"): return True
        if self.pattern.startswith("**/"):
            prefix = self.pattern[3:].rstrip("/")
            if f"/{prefix}/" in f"/{path}/" or f"/{path}".endswith(f"/{prefix}"): return True
        if self.pattern.endswith("/"):
            prefix = self.pattern
            return path.startswith(prefix) or path == prefix.rstrip("/")
        return False
