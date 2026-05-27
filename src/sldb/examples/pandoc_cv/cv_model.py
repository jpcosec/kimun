from pydantic import Field

from sldb import StructuredNLDoc


class PandocCVDoc(StructuredNLDoc):
    __template__ = """
# ⸢rev•title⸥

⸢rev•body⸥
""".strip()

    title: str = Field(description="CV title shown in the H1 heading.")
    body: str = Field(
        description="Body after the H1, including lead paragraph, Pandoc fenced div blocks, lists, and subsections."
    )
