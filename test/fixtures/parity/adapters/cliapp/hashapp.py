"""Stand-alone argparse factories of v1's contract-hash tests (tests/selfdoc/test_parser.py); no v1 import."""
import argparse


def _counted(default):
    parser = argparse.ArgumentParser(prog="app")
    parser.add_subparsers().add_parser("run").add_argument("--count", default=default)
    return parser


def build_one():
    return _counted(1)


def build_two():
    return _counted(2)


def build_wide():
    parser = argparse.ArgumentParser(prog="app")
    child = parser.add_subparsers().add_parser("run")
    child.add_argument("--a-long-option-name", choices=["first", "second"])
    child.add_argument("--another-long-option-name", required=True)
    return parser
