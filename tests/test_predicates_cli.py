import json

import yaml

from sldb.cli import main as cli_main
from sldb.store.io import load_store_index


def test_store_init_seeds_editable_predicates(tmp_path, capsys):
    assert cli_main(["stores", "init", "--path", str(tmp_path)]) == 0
    capsys.readouterr()

    store = tmp_path / ".sldb"
    index = load_store_index(store)
    assert {item.name for item in index.predicates} >= {"implements", "grounded_by"}

    assert (
        cli_main(
            [
                "predicates",
                "show",
                "implements",
                "--store",
                str(store),
                "--format",
                "json",
            ]
        )
        == 0
    )
    payload = json.loads(capsys.readouterr().out)
    assert payload["name"] == "implements"
    assert payload["axis"] == "HOW"


def test_predicates_add_list_validate_and_remove(tmp_path, capsys):
    assert cli_main(["stores", "init", "--path", str(tmp_path)]) == 0
    store = tmp_path / ".sldb"
    capsys.readouterr()

    assert (
        cli_main(
            [
                "predicates",
                "add",
                "depends_on",
                "--axis",
                "dependency",
                "--description",
                "Declares a dependency.",
                "--store",
                str(store),
            ]
        )
        == 0
    )
    capsys.readouterr()

    assert (
        cli_main(["predicates", "list", "--store", str(store), "--format", "json"]) == 0
    )
    listed = json.loads(capsys.readouterr().out)
    custom = next(item for item in listed["predicates"] if item["name"] == "depends_on")
    assert custom == {
        "name": "depends_on",
        "axis": "DEPENDENCY",
        "description": "Declares a dependency.",
    }

    assert cli_main(["predicates", "validate", "--store", str(store)]) == 0
    assert "PASS" in capsys.readouterr().out

    assert cli_main(["predicates", "remove", "depends_on", "--store", str(store)]) == 0
    capsys.readouterr()
    assert all(item.name != "depends_on" for item in load_store_index(store).predicates)


def test_predicates_validate_reports_duplicate_yaml_entries(tmp_path, capsys):
    assert cli_main(["stores", "init", "--path", str(tmp_path)]) == 0
    store = tmp_path / ".sldb"
    index_file = store / "core" / "store_index.yaml"
    payload = yaml.safe_load(index_file.read_text(encoding="utf-8"))
    payload["predicates"].append(dict(payload["predicates"][0]))
    index_file.write_text(yaml.safe_dump(payload, sort_keys=False), encoding="utf-8")
    capsys.readouterr()

    assert (
        cli_main(["predicates", "validate", "--store", str(store), "--format", "json"])
        == 1
    )
    result = json.loads(capsys.readouterr().out)
    assert result["valid"] is False
    assert any("Duplicate predicate" in error for error in result["errors"])


def test_docs_recover_uses_custom_store_predicate(tmp_path, capsys):
    assert cli_main(["stores", "init", "--path", str(tmp_path)]) == 0
    store = tmp_path / ".sldb"
    assert (
        cli_main(
            [
                "predicates",
                "add",
                "depends_on",
                "--axis",
                "DEPENDENCY",
                "--store",
                str(store),
            ]
        )
        == 0
    )
    target = tmp_path / "target.md"
    target.write_text("# Target\n", encoding="utf-8")
    source = tmp_path / "source.md"
    source.write_text("[depends_on:: [[target.md]]]", encoding="utf-8")
    capsys.readouterr()

    assert (
        cli_main(
            ["docs", "recover", str(source), "--store", str(store), "--format", "json"]
        )
        == 0
    )
    result = json.loads(capsys.readouterr().out)
    assert result["links"][0]["predicate"] == "depends_on"
    assert result["links"][0]["w5h1_type"] == "DEPENDENCY"
