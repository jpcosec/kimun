from .sldb_error import SLDBError
from sldb.store.exceptions import StoreError

class SLDBStoreError(SLDBError, StoreError):
    """Raised when there is an issue with the SLDB store or indexes."""
    pass
