from pydantic import BaseModel, Field
from sldb.store.models.model_entry import ModelEntry
from sldb.store.models.store_entry import StoreEntry
from sldb.store.models.predicate_entry import PredicateEntry


class StoreIndex(BaseModel):
    stores: list[StoreEntry] = Field(default_factory=list)
    models: list[ModelEntry] = Field(default_factory=list)
    predicates: list[PredicateEntry] = Field(default_factory=list)
    hash_a: str = ''
