from __future__ import annotations

import ast
from pathlib import Path
from typing import Any

from sldb.cli.utils import parse_data_value
from sldb.core.exceptions import SLDBModelEditError
from sldb.cli.commands.models_utils import registered_model_source, draft_path as get_draft_path, find_class_node, remove_node_block

class ModelsFieldsCLI:
    def fields(self, args: Any) -> int:
        if args.fields_command == "add":
            return self.add_field(args)
        if args.fields_command == "remove":
            return self.remove_field(args)
        raise SystemExit(f"Unknown models fields command: {args.fields_command}")

    def add_field(self, args: Any) -> int:
        path, _module_name, attr_path = registered_model_source(args)
        class_name = attr_path.split(".")[-1]
        draft_path = get_draft_path(path)
        source_path = draft_path if draft_path.exists() else path
        field_block = self._get_new_field_block(args)
        updated = self._insert_field_block(source_path, class_name, args.field, field_block)
        draft_path.write_text(updated, encoding="utf-8")
        print(f"Added field draft '{args.field}' for '{args.model}' in {draft_path}")
        return 0

    def _get_new_field_block(self, args: Any) -> str:
        d_supplied = args.default is not None
        d_val = parse_data_value(args.default) if d_supplied else None
        return self._field_block(args.field, args.field_type, args.description, d_supplied, d_val)

    def _field_block(self, field_name: str, field_type: str, description: str, default_supplied: bool, default_value: Any) -> str:
        if default_supplied:
            return f"    {field_name}: {field_type} = Field(default={default_value!r}, description={description!r})\n"
        return f"    {field_name}: {field_type} = Field(description={description!r})\n"

    def _insert_field_block(self, path: Path, cls_name: str, f_name: str, f_block: str) -> str:
        source = path.read_text(encoding="utf-8")
        tree = ast.parse(source)
        class_node = find_class_node(tree, cls_name, path)
        self._check_field_not_exists(class_node, cls_name, f_name)
        lines = source.splitlines(keepends=True)
        lines.insert(class_node.body[-1].end_lineno, f_block)
        return "".join(lines)

    def _check_field_not_exists(self, class_node: ast.ClassDef, cls_name: str, f_name: str) -> None:
        for node in class_node.body:
            if isinstance(node, ast.AnnAssign) and isinstance(node.target, ast.Name) and node.target.id == f_name:
                raise SLDBModelEditError(f"Field '{f_name}' already exists in '{cls_name}'.")

    def remove_field(self, args: Any) -> int:
        path, _module_name, attr_path = registered_model_source(args)
        class_name = attr_path.split(".")[-1]
        draft_path = get_draft_path(path)
        source_path = draft_path if draft_path.exists() else path
        updated = self._remove_field_block(source_path, class_name, args.field)
        draft_path.write_text(updated, encoding="utf-8")
        print(f"Removed field draft '{args.field}' for '{args.model}' in {draft_path}")
        return 0

    def _remove_field_block(self, path: Path, class_name: str, field_name: str) -> str:
        source = path.read_text(encoding="utf-8")
        tree = ast.parse(source)
        class_node = find_class_node(tree, class_name, path)
        field_node = self._get_field_node(class_node, class_name, field_name)
        return remove_node_block(source, field_node)

    def _get_field_node(self, class_node: ast.ClassDef, class_name: str, field_name: str) -> ast.AST:
        for node in class_node.body:
            if isinstance(node, ast.AnnAssign) and isinstance(node.target, ast.Name) and node.target.id == field_name:
                return node
        raise SLDBModelEditError(f"Field '{field_name}' does not exist in '{class_name}'.")
