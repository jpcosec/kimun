#!/usr/bin/env python3
"""Fake kimun surface adapter for tests (protocol 1).

Usage: fake_adapter.py ROOT [--system NAME] [--facts FILE] [--mode MODE] [--factory M:A]
       fake_adapter.py --describe

Prints the JSON document stored in FILE (``--facts`` or env ``KIMUN_FAKE_FACTS``)
verbatim, so a test decides the facts. ``--mode`` (or env ``KIMUN_FAKE_MODE``)
simulates the failures of the contract: ``fail`` (exit 1, ``error: ...``),
``runtime`` (exit 3, ``error: runtime missing: ...``), ``garbage`` (exit 0,
stdout is not JSON). It never writes anywhere, and never reads ROOT.
"""
import json
import os
import sys


def main(argv):
    if "--describe" in argv:
        mode = os.environ.get("KIMUN_FAKE_MODE", "ok")
        print(json.dumps({"adapter": "fake", "protocol": 1, "profile": "symbols", "languages": ["python"],
                          "options": [{"name": "--facts", "help": "JSON file printed as the output"},
                                      {"name": "--mode", "help": "ok | fail | runtime | garbage"}],
                          "runtime": {"available": mode != "runtime",
                                      "missing": ["fake_runtime"] if mode == "runtime" else []}}, sort_keys=True))
        return 0
    facts = os.environ.get("KIMUN_FAKE_FACTS")
    mode = os.environ.get("KIMUN_FAKE_MODE", "ok")
    rest = argv[1:]
    if not argv or argv[0].startswith("--"):
        sys.stderr.write("usage: fake_adapter.py ROOT [--system NAME] [--facts FILE] [--mode MODE]\n")
        return 2
    i = 0
    while i < len(rest):
        if rest[i] == "--facts" and i + 1 < len(rest):
            facts = rest[i + 1]
            i += 1
        elif rest[i] == "--mode" and i + 1 < len(rest):
            mode = rest[i + 1]
            i += 1
        elif rest[i] in ("--system", "--factory") and i + 1 < len(rest):
            i += 1
        i += 1
    if mode == "fail":
        sys.stderr.write("warning: about to fail\nerror: fake scan failed\n")
        return 1
    if mode == "runtime":
        sys.stderr.write("error: runtime missing: fake_runtime\n")
        return 3
    if mode == "garbage":
        sys.stdout.write("this is <not> json\n")
        return 0
    if not facts:
        sys.stderr.write("error: no facts file (--facts FILE or KIMUN_FAKE_FACTS)\n")
        return 1
    with open(facts, "rb") as handle:
        sys.stdout.write(handle.read().decode("utf-8"))
    return 0


if __name__ == "__main__":
    sys.exit(main(sys.argv[1:]))
