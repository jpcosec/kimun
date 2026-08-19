from typing import Any

def _split_path(path: str) -> list[str]:
    parts = [part for part in path.split(".") if part]
    if not parts:
        raise KeyError(path)
    return parts

def _get_part(value: Any, part: str, path: str) -> Any:
    if isinstance(value, dict):
        if part not in value:
            raise KeyError(path)
        return value[part]
    if isinstance(value, list):
        return value[int(part)]
    raise KeyError(path)

def deep_get(payload: Any, path: str) -> Any:
    value = payload
    for part in _split_path(path):
        value = _get_part(value, part, path)
    return value

def _deep_set_dict(target: dict, part: str, create: bool, path: str) -> Any:
    if part not in target:
        if not create:
            raise KeyError(path)
        target[part] = {}
    return target[part]

def _deep_set_traverse(target: Any, parts: list[str], create: bool, path: str) -> Any:
    for part in parts[:-1]:
        if isinstance(target, dict):
            target = _deep_set_dict(target, part, create, path)
        elif isinstance(target, list):
            target = target[int(part)]
        else:
            raise KeyError(path)
    return target

def _deep_set_leaf(target: Any, leaf: str, new_value: Any, create: bool, path: str) -> None:
    if isinstance(target, dict):
        if not create and leaf not in target:
            raise KeyError(path)
        target[leaf] = new_value
        return
    if isinstance(target, list):
        target[int(leaf)] = new_value
        return
    raise KeyError(path)

def deep_set(payload: Any, path: str, new_value: Any, create: bool = False) -> Any:
    parts = _split_path(path)
    target = _deep_set_traverse(payload, parts, create, path)
    _deep_set_leaf(target, parts[-1], new_value, create, path)
    return payload

def _delete_leaf(target: Any, leaf: str, path: str) -> None:
    if isinstance(target, dict):
        target.pop(leaf, None)
    elif isinstance(target, list):
        target.pop(int(leaf))
    else:
        raise KeyError(path)

def deep_delete(payload: Any, path: str) -> Any:
    parts = _split_path(path)
    target = payload
    for part in parts[:-1]:
        target = target[part] if isinstance(target, dict) else target[int(part)]
    _delete_leaf(target, parts[-1], path)
    return payload

def ensure_list(payload: Any, path: str) -> list[Any]:
    value = deep_get(payload, path)
    if not isinstance(value, list):
        raise TypeError(f"Target '{path}' is not a list field.")
    return value
