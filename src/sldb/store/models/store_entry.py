from pydantic import BaseModel, Field


class StoreEntry(BaseModel):
    name: str
    path: str
