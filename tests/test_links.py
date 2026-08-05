import json

from pathlib import Path

from sldb.cli import main as cli_main


def _write_models(base: Path) -> str:
    module = base / "link_models.py"
    module.write_text(
        '''from pydantic import Field
from sldb import StructuredNLDoc


class NoteDoc(StructuredNLDoc):
    __template__ = """# ⸢rev•title⸥

⸢rev•body⸥""".strip()
    title: str = Field(description="Document title.")
    body: str = Field(description="Document body.")


class RootDoc(StructuredNLDoc):
    __template__ = """# ⸢rev•title⸥

See [[⸢rev•linked_doc⸥]] for background.

![[⸢rev•transclusion_doc⸥]]""".strip()
    title: str = Field(description="Document title.")
    linked_doc: str = Field(description="Tracked linked document name.")
    transclusion_doc: str = Field(description="Tracked transcluded document name.")
''',
        encoding="utf-8",
    )
    return str(module.parent)


def test_recover_and_compose_links(tmp_path, capsys):
    pythonpath = _write_models(tmp_path)
    store = tmp_path / ".sldb"

    assert cli_main(["stores", "init", "--path", str(tmp_path)]) == 0
    assert (
        cli_main(
            [
                "models",
                "add",
                "link_models:NoteDoc",
                "--store",
                str(store),
                "--pythonpath",
                pythonpath,
            ]
        )
        == 0
    )
    assert (
        cli_main(
            [
                "models",
                "add",
                "link_models:RootDoc",
                "--store",
                str(store),
                "--pythonpath",
                pythonpath,
            ]
        )
        == 0
    )

    concept = tmp_path / "concept.md"
    concept.write_text("# Concept\n\nBackground info.\n", encoding="utf-8")
    constraints = tmp_path / "constraints.md"
    constraints.write_text(
        "# Constraints\n\nAlways validate first.\n", encoding="utf-8"
    )
    root = tmp_path / "root.md"
    root.write_text(
        "# Root\n\nSee [[concept]] for background.\n\n![[constraints]]\n",
        encoding="utf-8",
    )

    assert (
        cli_main(
            [
                "docs",
                "track",
                str(concept),
                "--model",
                "NoteDoc",
                "--store",
                str(store),
                "--pythonpath",
                pythonpath,
            ]
        )
        == 0
    )
    assert (
        cli_main(
            [
                "docs",
                "track",
                str(constraints),
                "--model",
                "NoteDoc",
                "--store",
                str(store),
                "--pythonpath",
                pythonpath,
            ]
        )
        == 0
    )
    assert (
        cli_main(
            [
                "docs",
                "track",
                str(root),
                "--model",
                "RootDoc",
                "--store",
                str(store),
                "--pythonpath",
                pythonpath,
            ]
        )
        == 0
    )

    capsys.readouterr()
    rc = cli_main(
        [
            "docs",
            "recover",
            str(root),
            "--store",
            str(store),
            "--format",
            "json",
            "--include-transclusions",
        ]
    )
    recovered = json.loads(capsys.readouterr().out)
    assert rc == 0
    assert recovered["root"] == "root"
    assert recovered["links"][0]["target"] == "concept"
    assert recovered["links"][0]["kind"] == "link"
    assert recovered["links"][0]["resolved"] is True
    assert recovered["links"][1]["target"] == "constraints"
    assert recovered["links"][1]["kind"] == "transclusion"
    assert recovered["unresolved"] == []

    capsys.readouterr()
    rc = cli_main(["docs", "compose", str(root), "--store", str(store)])
    composed = capsys.readouterr().out
    assert rc == 0
    assert "See [[concept]] for background." in composed
    assert "# Constraints" in composed
    assert "Always validate first." in composed


def test_docs_recover_and_compose_accept_tracked_doc_names_and_yaml(tmp_path, capsys):
    pythonpath = _write_models(tmp_path)
    store = tmp_path / ".sldb"

    assert cli_main(["stores", "init", "--path", str(tmp_path)]) == 0
    assert (
        cli_main(
            [
                "models",
                "add",
                "link_models:NoteDoc",
                "--store",
                str(store),
                "--pythonpath",
                pythonpath,
            ]
        )
        == 0
    )
    assert (
        cli_main(
            [
                "models",
                "add",
                "link_models:RootDoc",
                "--store",
                str(store),
                "--pythonpath",
                pythonpath,
            ]
        )
        == 0
    )

    concept = tmp_path / "concept.md"
    concept.write_text("# Concept\n\nBackground info.\n", encoding="utf-8")
    constraints = tmp_path / "constraints.md"
    constraints.write_text(
        "# Constraints\n\nAlways validate first.\n", encoding="utf-8"
    )
    root = tmp_path / "root.md"
    root.write_text(
        "# Root\n\nSee [[concept]] for background.\n\n![[constraints]]\n",
        encoding="utf-8",
    )

    assert (
        cli_main(
            [
                "docs",
                "track",
                str(concept),
                "--model",
                "NoteDoc",
                "--store",
                str(store),
                "--pythonpath",
                pythonpath,
            ]
        )
        == 0
    )
    assert (
        cli_main(
            [
                "docs",
                "track",
                str(constraints),
                "--model",
                "NoteDoc",
                "--store",
                str(store),
                "--pythonpath",
                pythonpath,
            ]
        )
        == 0
    )
    assert (
        cli_main(
            [
                "docs",
                "track",
                str(root),
                "--model",
                "RootDoc",
                "--name",
                "root-doc",
                "--store",
                str(store),
                "--pythonpath",
                pythonpath,
            ]
        )
        == 0
    )

    capsys.readouterr()
    rc = cli_main(
        [
            "docs",
            "recover",
            "root-doc",
            "--store",
            str(store),
            "--format",
            "json",
            "--include-transclusions",
        ]
    )
    recovered = json.loads(capsys.readouterr().out)
    assert rc == 0
    assert {link["target"] for link in recovered["links"]} == {"concept", "constraints"}

    capsys.readouterr()
    rc = cli_main(
        [
            "docs",
            "compose",
            "root-doc",
            "--store",
            str(store),
            "--format",
            "yaml",
            "-o",
            "-",
        ]
    )
    out = capsys.readouterr().out
    assert rc == 0
    assert out.lstrip().startswith("root: root")
    assert "transclusions:" in out


# =============================================================================
# Tests for predicate links (5W1H+)
# =============================================================================


def test_parse_predicate_links():
    """Test parsing of [predicate:: [[target]]] syntax."""
    from sldb.links import parse_links

    markdown = """
    This task [implements:: [[spec-001]]].
    Another [is_solved_by:: [[solution-002]]].
    [custom_predicate:: [[some-doc]]].
    """
    links = parse_links(
        markdown,
        {"implements": "HOW", "is_solved_by": "HOW"},
    )

    assert len(links) == 3

    # First predicate link
    assert links[0].target == "spec-001"
    assert links[0].predicate == "implements"
    assert links[0].w5h1_type == "HOW"
    assert links[0].kind == "predicate_link"

    # Second predicate link with different predicate
    assert links[1].target == "solution-002"
    assert links[1].predicate == "is_solved_by"
    assert links[1].w5h1_type == "HOW"

    # Custom predicate gets CUSTOM type
    assert links[2].target == "some-doc"
    assert links[2].predicate == "custom_predicate"
    assert links[2].w5h1_type == "CUSTOM"


def test_parse_links_uses_injected_predicate_axes():
    """Predicate classification comes from the caller, not hardcoded data."""
    from sldb.links import parse_links

    links = parse_links(
        "[depends_on:: [[target]]] [unknown:: [[other]]]",
        {"depends_on": "DEPENDENCY"},
    )

    assert links[0].predicate == "depends_on"
    assert links[0].w5h1_type == "DEPENDENCY"
    assert links[1].predicate == "unknown"
    assert links[1].w5h1_type == "CUSTOM"


def test_parse_mixed_links_with_predicates():
    """Test parsing of documents with both regular links and predicate links."""
    from sldb.links import parse_links

    markdown = """
    See [[background]] for context.

    This solution [implements:: [[spec-v2]]] and
    [grounded_by:: [[research-paper]]].

    Also check [[related-work]].
    """
    links = parse_links(
        markdown,
        {"implements": "HOW", "grounded_by": "PROVENANCE"},
    )

    # 2 predicate links + 2 regular links = 4 total
    assert len(links) == 4

    # Check kinds
    kinds = {link.kind for link in links}
    assert "link" in kinds
    assert "predicate_link" in kinds

    # Check specific predicates
    predicate_links = [link for link in links if link.kind == "predicate_link"]
    assert len(predicate_links) == 2
    assert predicate_links[0].predicate == "implements"
    assert predicate_links[1].predicate == "grounded_by"


def test_predicate_links_with_transclusion_bang():
    """Test that [predicate:: ![[target]]] is supported."""
    from sldb.links import parse_links

    markdown = "[implements:: ![[embedded-spec]]]"
    links = parse_links(markdown, {"implements": "HOW"})

    assert len(links) == 1
    assert links[0].target == "embedded-spec"
    assert links[0].predicate == "implements"
    # Bang inside predicate link means transclusion kind
    assert links[0].kind == "transclusion"


def test_resolve_predicate_link(tmp_path):
    """Test resolving a predicate link via relative path."""
    from sldb.links import resolve_link_target

    # Create a document alongside current doc
    docs = tmp_path / "docs"
    docs.mkdir()
    spec_doc = docs / "spec-001.md"
    spec_doc.write_text("# Spec 001\n", encoding="utf-8")

    resolved = resolve_link_target(
        "docs/spec-001.md",
        tmp_path / "current.md",
        None,  # No store, use relative path
        predicate="implements",
        w5h1_type="HOW",
    )

    assert resolved.resolved is True
    assert resolved.kind == "predicate_link"
    assert resolved.predicate == "implements"
    assert resolved.w5h1_type == "HOW"
    assert resolved.source == "path"


def test_recover_predicate_links_cli(tmp_path, capsys):
    """Test that CLI recover command shows predicate info in JSON format."""
    pythonpath = _write_models(tmp_path)
    store = tmp_path / ".sldb"

    assert cli_main(["stores", "init", "--path", str(tmp_path)]) == 0
    assert (
        cli_main(
            [
                "models",
                "add",
                "link_models:NoteDoc",
                "--store",
                str(store),
                "--pythonpath",
                pythonpath,
            ]
        )
        == 0
    )

    spec = tmp_path / "spec.md"
    spec.write_text("# Spec\n", encoding="utf-8")
    research = tmp_path / "research.md"
    research.write_text("# Research\n", encoding="utf-8")
    task = tmp_path / "task.md"
    # Use resolved links to different documents
    task.write_text(
        "# Task\n\n" + "[implements:: [[spec]]]\n" + "[grounded_by:: [[research]]]",
        encoding="utf-8",
    )

    assert (
        cli_main(
            [
                "docs",
                "track",
                str(spec),
                "--model",
                "NoteDoc",
                "--store",
                str(store),
                "--pythonpath",
                pythonpath,
            ]
        )
        == 0
    )
    assert (
        cli_main(
            [
                "docs",
                "track",
                str(research),
                "--model",
                "NoteDoc",
                "--store",
                str(store),
                "--pythonpath",
                pythonpath,
            ]
        )
        == 0
    )
    assert (
        cli_main(
            [
                "docs",
                "track",
                str(task),
                "--model",
                "NoteDoc",
                "--store",
                str(store),
                "--pythonpath",
                pythonpath,
            ]
        )
        == 0
    )

    capsys.readouterr()
    rc = cli_main(
        [
            "docs",
            "recover",
            str(task),
            "--store",
            str(store),
            "--format",
            "json",
        ]
    )
    recovered = json.loads(capsys.readouterr().out)

    assert rc == 0
    predicate_links = [
        link for link in recovered["links"] if link["kind"] == "predicate_link"
    ]
    assert len(predicate_links) == 2

    predicates = {link["predicate"] for link in predicate_links}
    assert predicates == {"implements", "grounded_by"}

    # Check 5W1H types
    implements_link = next(
        link for link in predicate_links if link["predicate"] == "implements"
    )
    assert implements_link["w5h1_type"] == "HOW"

    grounded_link = next(
        link for link in predicate_links if link["predicate"] == "grounded_by"
    )
    assert grounded_link["w5h1_type"] == "PROVENANCE"
