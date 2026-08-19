from __future__ import annotations

from importlib.resources import files
from pathlib import Path
from typing import Any

class InitCLI:
    """Handles project initialization and example generation."""

    def init(self, args: Any) -> int:
        """Initialize the project with the sldb skill file."""
        target_dir = Path(args.path)
        skills_file = target_dir / ".skills" / "sldb" / "SKILL.md"
        
        self._check_exists(skills_file, args.force)
        self._write_skill_file(skills_file)
        
        return 0

    def example(self, args: Any) -> int:
        """Create an example project bundle."""
        target_root = Path(args.path) / "sldb_example"
        target_root.mkdir(parents=True, exist_ok=True)

        self._copy_example_bundle(target_root)
        return 0

    def _check_exists(self, path: Path, force: bool) -> None:
        """Check if file exists and raise error if not forced."""
        if path.exists() and not force:
            from sldb.core.exceptions import SLDBError
            raise SLDBError(f"File exists: {path}. Use --force to replace.")

    def _write_skill_file(self, path: Path) -> None:
        """Write the skill file to the given path."""
        path.parent.mkdir(parents=True, exist_ok=True)
        content = files("sldb.assets.skills").joinpath("sldb.md").read_text(encoding="utf-8")
        path.write_text(content, encoding="utf-8")
        print(f"Wrote {path}")

    def _copy_example_bundle(self, target: Path) -> None:
        """Copy the example bundle to the target directory."""
        bundle_path = files("sldb.examples.reference_bundle")
        for item in bundle_path.iterdir():
            if item.is_file() and item.name != "__init__.py" and "__pycache__" not in str(item):
                dest = target / item.name
                dest.write_text(item.read_text(encoding="utf-8"), encoding="utf-8")
                print(f"Wrote {dest}")
