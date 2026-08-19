from .sldb_error import SLDBError

class SLDBValidationError(SLDBError):
    """Raised when document validation or idempotency checks fail."""

    def __init__(self, message, details=None):
        super().__init__(message)
        self.details = details or {}
