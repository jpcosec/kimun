from dataclasses import dataclass

@dataclass
class FAQEntry:
    index: int
    title: str
    slug: str
    body: str
