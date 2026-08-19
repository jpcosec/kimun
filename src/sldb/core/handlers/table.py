from __future__ import annotations
import re
from typing import Any
from sldb.core.handlers.base import BaseNodeHandler
from sldb.core.handlers.utils import parse_marker
from sldb.core.node import SLDBNode

class TableNodeHandler(BaseNodeHandler):
    def compile_recipe(self, node: SLDBNode) -> list[dict[str, Any]]:
        rows = self._get_table_rows(node)
        if len(rows) < 2 or not (col_markers := self._extract_col_markers(rows[-1])):
            return []
        first_marker_name = list(col_markers.values())[0]["marker"].name
        return [{
            "name": first_marker_name,
            "handler": "table",
            "col_markers": col_markers,
            "props": [m["marker"].name for m in col_markers.values()],
        }]

    def _get_table_rows(self, node: SLDBNode) -> list[SLDBNode]:
        rows = []
        for child in node.children:
            if child.type in ["thead", "tbody"]:
                rows.extend([tr for tr in child.children if tr.type == "tr"])
            elif child.type == "tr":
                rows.append(child)
        return rows

    def _extract_col_markers(self, template_row: SLDBNode) -> dict[int, Any]:
        col_markers = {}
        for col_idx, cell in enumerate(template_row.children):
            text = self.get_text(cell).strip()
            if match := re.search(r"⸢([^⸥]+)⸥", text):
                self._process_table_cell(match, text, col_idx, col_markers)
        return col_markers

    def _process_table_cell(self, match: re.Match, text: str, col_idx: int, col_markers: dict[int, Any]) -> None:
        marker = parse_marker(match.group(1))
        if marker.is_reversible or marker.is_optional:
            col_markers[col_idx] = {"marker": marker, "cell_template": text}

    def extract_data(self, node: SLDBNode, recipe: dict[str, Any]) -> Any:
        rows = self._get_table_rows(node)
        if "marker" in recipe and self._is_table_marker(recipe["marker"]):
            return self._extract_marker_table(rows, recipe)
        data_rows = rows[1:] if len(rows) > 1 else []
        cm = recipe["col_markers"]
        results = self._extract_row_records(data_rows, cm, self._build_col_patterns(cm))
        return {recipe["name"]: results} if results else None

    def _build_col_patterns(self, col_markers: dict[int, Any]) -> dict[int, str]:
        from sldb.core.handlers.text import build_text_pattern
        return {idx: build_text_pattern(m["cell_template"])["regex"] for idx, m in col_markers.items()}

    def _extract_row_records(self, data_rows: list[SLDBNode], col_markers: dict[int, Any], patterns: dict[int, str]) -> list[dict[str, str]]:
        results = []
        for row in data_rows:
            if row_record := self._extract_single_row(row, col_markers, patterns):
                results.append(row_record)
        return results

    def _extract_single_row(self, row: SLDBNode, col_markers: dict[int, Any], patterns: dict[int, str]) -> dict[str, str]:
        row_record, has_data = {}, False
        for col_idx, meta in col_markers.items():
            if col_idx < len(row.children):
                if match := re.fullmatch(patterns[col_idx], self.get_text(row.children[col_idx]).strip()):
                    row_record[meta["marker"].name] = match.group(1)
                    has_data = True
        return row_record if has_data else {}

    def _is_table_marker(self, marker: Any) -> bool:
        return any(trait == "table" or trait.startswith("table[") for trait in marker.traits)

    def _extract_marker_table(self, rows: list[SLDBNode], recipe: dict[str, Any]) -> dict[str, list[dict[str, str]]]:
        if not rows:
            return {recipe["name"]: []}
        headers = [self.get_text(cell).strip() for cell in rows[0].children]
        cols = recipe.get("table_columns") or headers
        hl = {header: idx for idx, header in enumerate(headers)}
        return {recipe["name"]: self._build_marker_records(rows[1:], cols, hl)}

    def _build_marker_records(self, data_rows: list[SLDBNode], columns: list[str], hl: dict[str, int]) -> list[dict[str, str]]:
        records = []
        for row in data_rows:
            record = {}
            for col in columns:
                idx = hl.get(col)
                record[col] = self.get_text(row.children[idx]).strip() if idx is not None and idx < len(row.children) else ""
            records.append(record)
        return records
