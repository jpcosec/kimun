import os
import ast
import sys
from pathlib import Path

# We will read scanner.py and write out its pieces.

scanner_src = Path("src/sldb/core/ingest/scanner.py").read_text()

# I will write the code to create the new files directly from here in my python script
