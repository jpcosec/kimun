EXPLORE_HELP = """sldb explore

Deep search over markdown docs and Python docstrings.

Use this when curated help is not enough and you want to inspect the written knowledge in
`docs/`, `README.md`, `.sldb/README.md`, or Python docstrings across `src/`.

Examples:
  sldb explore store
  sldb explore StructuredNLDoc --source docstrings
  sldb explore compose --source docs
  sldb explore 'semantic.*physical' --regex --source all

Flags:
  --source all|docs|docstrings  Restrict where SLDB searches
  --regex                       Treat the term as a regex
  --docs-root PATH              Docs directory to scan, default `docs`
  --code-root PATH              Python source directory to scan, default `src`
  --max-results N               Limit result count, default 20
"""

LEGACY_HELP = """sldb legacy

Compatibility surface for the raw query/link commands.

Examples:
  sldb legacy ls st
  sldb legacy get 'st.{Book}.my-book.title'
  sldb legacy find 'st.{Book}' --where 'status = "accepted"'
  sldb legacy recover my-book
"""
