from pydantic import BaseModel, Field
from .document_context import DocumentContext
from .meaning_node import MeaningNode
from .surface_node import SurfaceNode
from .graph_view import GraphView
from .section_context_entry import SectionContextEntry

class DocumentIR(BaseModel):
    context: DocumentContext = Field(default_factory=DocumentContext)
    structure: list[MeaningNode] = Field(default_factory=list)
    nodes: list[MeaningNode] = Field(default_factory=list)
    surface: list[SurfaceNode] = Field(default_factory=list)
    graph: GraphView = Field(default_factory=GraphView)
    context_index: list[SectionContextEntry] = Field(default_factory=list)
