from pydantic import BaseModel, Field


class PredicateEntry(BaseModel):
    name: str
    axis: str
    description: str = ''
