"""Export a sldb v1 `StructuredNLDoc` class as a kimun model descriptor (docs/v2/07 §2).

`python -m kimun.models export mod:Class` prints the descriptor as EDN. It is the only
bb→Python point of the product (docs/v2/06 §9); the parity recorder reuses `descriptor`.
"""
from __future__ import annotations

import importlib
import json
import sys
import types
import typing
from typing import Any


def _field_type(ann: Any) -> tuple[Any, bool]:
    """(kimun type, optional?) for a Python annotation; unknown shapes become `:yaml`."""
    origin = typing.get_origin(ann)
    args = typing.get_args(ann)
    if origin in (typing.Union, getattr(types, "UnionType", None)):
        rest = [a for a in args if a is not type(None)]
        if len(rest) == 1:
            return _field_type(rest[0])[0], True
        return Kw("yaml"), type(None) in args
    if origin is typing.Literal:
        return [Kw("enum"), *[str(a) for a in args]], False
    if ann is str:
        return Kw("string"), False
    if ann is bool:
        return Kw("bool"), False
    if ann is int:
        return Kw("int"), False
    if origin in (list, typing.List):
        inner = _field_type(args[0])[0] if args else Kw("yaml")
        return [Kw("list"), inner], False
    if origin in (dict, typing.Dict):
        inner = _field_type(args[1])[0] if len(args) == 2 else Kw("yaml")
        return [Kw("map"), inner], False
    return Kw("yaml"), False


class Kw(str):
    """An EDN keyword."""


def edn(v: Any) -> str:
    """Serialize `v` (dict/list/str/Kw/int/bool/None) as EDN text."""
    if isinstance(v, Kw):
        return ":" + v
    if v is None:
        return "nil"
    if isinstance(v, bool):
        return "true" if v else "false"
    if isinstance(v, int):
        return str(v)
    if isinstance(v, float):
        return json.dumps(str(v))
    if isinstance(v, str):
        return json.dumps(v, ensure_ascii=False)
    if isinstance(v, dict):
        return "{" + " ".join(f"{edn(k)} {edn(x)}" for k, x in v.items()) + "}"
    if isinstance(v, (list, tuple)):
        return "[" + " ".join(edn(x) for x in v) + "]"
    return json.dumps(str(v), ensure_ascii=False)


def _jsonable(v: Any) -> Any:
    try:
        json.dumps(v)
        return v
    except TypeError:
        return str(v)


def descriptor(cls: type) -> dict:
    """The kimun descriptor of v1 model class `cls`, as a dict with `Kw` keys."""
    from pydantic_core import PydanticUndefined

    fields = []
    for name, info in cls.model_fields.items():
        ftype, optional = _field_type(info.annotation)
        f = {Kw("name"): name, Kw("type"): ftype,
             Kw("required"): bool(info.is_required()) and not optional,
             Kw("description"): info.description or "",
             Kw("py/annotation"): str(info.annotation)}
        if info.exclude:
            f[Kw("exclude")] = True
        if info.default is not PydanticUndefined and info.default is not None:
            f[Kw("default")] = _jsonable(info.default)
        elif info.default_factory is not None:
            try:
                f[Kw("default")] = _jsonable(info.default_factory())
            except Exception:
                pass
        fields.append(f)
    bases = [b.__name__ for b in cls.__mro__[1:]
             if hasattr(b, "model_fields") and b.__name__ not in ("StructuredNLDoc", "BaseModel")]
    d = {Kw("model/name"): cls.__name__, Kw("model/version"): 1, Kw("base"): bases,
         Kw("fields"): fields, Kw("template"): getattr(cls, "__template__", "") or ""}
    for attr, key in (("__references__", "references"), ("__containment__", "containment"),
                      ("__compositions__", "compositions")):
        val = getattr(cls, attr, None)
        if val:
            d[Kw("py/" + key)] = _jsonable(val)
    return d


def export(ref: str) -> str:
    """EDN descriptor of `module:Class`."""
    mod, _, name = ref.partition(":")
    return edn(descriptor(getattr(importlib.import_module(mod), name)))


if __name__ == "__main__":
    if len(sys.argv) == 3 and sys.argv[1] == "export":
        print(export(sys.argv[2]))
    else:
        sys.exit("usage: python -m kimun.models export module:Class")
