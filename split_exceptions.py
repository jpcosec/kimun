import os

content = """
class SLDBError(Exception):
    \"\"\"Base exception for all SLDB errors.\"\"\"
    pass

class SLDBModelError(SLDBError):
    \"\"\"Raised when there is an issue with a StructuredNLDoc model definition.\"\"\"
    pass

class SLDBModelDraftError(SLDBModelError):
    \"\"\"Raised when there is an issue with a temporary draft of a model contract.\"\"\"
    pass

class SLDBModelEditError(SLDBModelError):
    \"\"\"Raised when a CLI model edit cannot be applied safely.\"\"\"
    pass

class SLDBValidationError(SLDBError):
    \"\"\"Raised when document validation or idempotency checks fail.\"\"\"
    def __init__(self, message, details=None):
        super().__init__(message)
        self.details = details or {}

class SLDBStoreError(SLDBError):
    \"\"\"Raised when there is an issue with the SLDB store or indexes.\"\"\"
    pass

class SLDBASTError(SLDBError):
    \"\"\"Raised when there is an issue parsing or processing the AST.\"\"\"
    pass

class SLDBLinkError(SLDBError):
    \"\"\"Raised when links cannot be recovered or composed.\"\"\"
    pass
"""

def camel_to_snake(name):
    import re
    s1 = re.sub('(.)([A-Z][a-z]+)', r'\1_\2', name)
    return re.sub('([a-z0-9])([A-Z])', r'\1_\2', s1).lower()

classes = [
    "SLDBError", "SLDBModelError", "SLDBModelDraftError", 
    "SLDBModelEditError", "SLDBValidationError", 
    "SLDBStoreError", "SLDBASTError", "SLDBLinkError"
]

os.makedirs("src/sldb/core/exceptions", exist_ok=True)

for c in classes:
    snake = camel_to_snake(c)
    with open(f"src/sldb/core/exceptions/{snake}.py", "w") as f:
        f.write(f"from sldb.core.exceptions.sldb_error import SLDBError\n\n" if c != "SLDBError" else "")
        # Wait, some inherit from SLDBModelError, let's just make it simple.
