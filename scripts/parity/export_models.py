"""Export every StructuredNLDoc subclass of a Python package as kimun descriptors.

    python3 scripts/parity/export_models.py PACKAGE OUT_DIR

Walks the package's modules, and writes OUT_DIR/<Class>.edn for each model class
defined in it (docs/v2/07 §9: the goldens of the real corpora). Needs the
package, sldb v1 and kimun's python/ on PYTHONPATH.
"""
import importlib
import pathlib
import pkgutil
import sys

from kimun.models import descriptor, edn
from sldb import StructuredNLDoc


def main(package: str, out_dir: str) -> None:
    pkg = importlib.import_module(package)
    out = pathlib.Path(out_dir)
    out.mkdir(parents=True, exist_ok=True)
    seen = {}
    modules = [pkg] + [importlib.import_module(m.name) for m in pkgutil.walk_packages(pkg.__path__, pkg.__name__ + ".")]
    for mod in modules:
        for value in vars(mod).values():
            if isinstance(value, type) and issubclass(value, StructuredNLDoc) and value is not StructuredNLDoc \
                    and value.__module__.startswith(package):
                seen[value.__name__] = value
    for name, cls in sorted(seen.items()):
        (out / f"{name}.edn").write_text(edn(descriptor(cls)) + "\n", encoding="utf-8")
    print(f"{package}: {len(seen)} models -> {out}")


if __name__ == "__main__":
    main(sys.argv[1], sys.argv[2])
