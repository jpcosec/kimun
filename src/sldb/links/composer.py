from __future__ import annotations
import re
from pathlib import Path
from .parser import LINK_PATTERN
from .resolver import resolve_link_target

def compose_document(dp: Path, sp: Path | None, s: set[Path] | None = None) -> dict:
    dp, s = dp.resolve(), s or set()
    if dp in s: return {"markdown": dp.read_text("utf-8"), "transclusions": [], "unresolved": []}
    s.add(dp); tr, un = [], []
    def _r(m: re.Match[str]) -> str:
        res = resolve_link_target(m.group(2).strip(), dp, sp) if m.group(1) else None
        if not res or not res.path: un.append(m.group(2).strip()) if res else None; return m.group(0)
        tr.append(m.group(2).strip()); n = compose_document(Path(res.path), sp, s)
        un.extend(n["unresolved"]); tr.extend(n["transclusions"]); return n["markdown"].rstrip()
    return {"root": dp.stem, "path": str(dp), "markdown": LINK_PATTERN.sub(_r, dp.read_text("utf-8")), "transclusions": sorted(dict.fromkeys(tr)), "unresolved": sorted(dict.fromkeys(un))}
