from __future__ import annotations

import ast
from pathlib import Path
from typing import Any

from sldb.cli.utils import read_text
from sldb.core.exceptions import SLDBModelDraftError, SLDBModelEditError
from sldb.cli.commands.models_utils import registered_model_source, draft_path as get_draft_path, find_class_node, replace_rhs_expression

class ModelsTemplateCLI:
    def template(self, args: Any) -> int:
        if args.template_command == "show":
            return self._show_template(args)
        if args.template_command == "edit":
            return self.edit_template(args)
        raise SystemExit(f"Unknown models template command: {args.template_command}")

    def _show_template(self, args: Any) -> int:
        path, _mod, attr = registered_model_source(args)
        src_path = get_draft_path(path) if args.draft else path
        if args.draft and not src_path.exists():
            raise SLDBModelDraftError(f"No draft template for '{args.model}'.")
        template = self._read_template_literal(src_path, attr.split(".")[-1])
        print(template)
        return 0

    def edit_template(self, args: Any) -> int:
        path, _, attr = registered_model_source(args)
        template = read_text(args.input).rstrip("\n")
        d_path = get_draft_path(path)
        s_path = d_path if d_path.exists() else path
        updated = self._replace_template_literal(s_path, attr.split(".")[-1], template)
        d_path.write_text(updated, encoding="utf-8")
        print(f"Wrote draft template for '{args.model}' to {d_path}")
        return 0

    def _replace_template_literal(self, path: Path, class_name: str, template: str) -> str:
        source = path.read_text(encoding="utf-8")
        tree = ast.parse(source)
        class_node = find_class_node(tree, class_name, path)
        assign_node = self._get_template_assign_node(class_node, class_name)
        return replace_rhs_expression(source, assign_node.value, self._template_literal(template))

    def _get_template_assign_node(self, class_node: ast.ClassDef, class_name: str) -> ast.Assign:
        for node in class_node.body:
            if isinstance(node, ast.Assign) and any(isinstance(t, ast.Name) and t.id == "__template__" for t in node.targets):
                return node
        raise SLDBModelEditError(f"Class '{class_name}' has no __template__ assignment.")

    def _template_literal(self, template: str) -> str:
        escaped = template.replace('"""', '\"\"\"')
        return f'"""{escaped}""".strip()'

    def _read_template_literal(self, path: Path, class_name: str) -> str:
        source = path.read_text(encoding="utf-8")
        tree = ast.parse(source)
        class_node = find_class_node(tree, class_name, path)
        assign_node = self._get_template_assign_node(class_node, class_name)
        value_node = assign_node.value
        if isinstance(value_node, ast.Call):
            return ast.literal_eval(value_node.func.value)
        return ast.literal_eval(value_node)
