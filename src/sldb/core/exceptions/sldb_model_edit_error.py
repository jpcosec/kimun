from .sldb_model_error import SLDBModelError

class SLDBModelEditError(SLDBModelError):
    """Raised when a CLI model edit cannot be applied safely."""
    pass
