import re
from pathlib import Path

path = Path("src/sldb/store/semantic.py")
content = path.read_text()

# We will move all sections related code to src/sldb/store/sections.py

sections_code = ""

sections_funcs = ["_process_doc_sections", "_process_model_sections", "rebuild_sections_indexes", "_parse_md_nodes", "_build_section", "_extract_sections"]

for func in sections_funcs:
    match = re.search(rf'def {func}\(.*?def ', content, re.DOTALL)
    if match:
        # extract it
        func_code = re.search(rf'def {func}\(.*?(?=\ndef )', content, re.DOTALL)
        if func_code:
            sections_code += func_code.group(0) + "\n\n"
            content = content.replace(func_code.group(0) + "\n\n", "")
            
# extract the last one if it is at the end
match = re.search(rf'def _extract_sections\(.*?return sections\n', content, re.DOTALL)
if match:
    sections_code += match.group(0) + "\n"
    content = content.replace(match.group(0) + "\n", "")

# Wait, `rebuild_sections_indexes` might be the last one if _extract_sections is not.

# Let's write the sections code with the correct imports
sections_file_content = """from __future__ import annotations
import re
from pathlib import Path
from sldb.store.io import load_documents_index, load_models_index, save_models_index, save_sections_index, load_store_index
from sldb.store.layout import sections_index_relpath
from sldb.store.models.doc_sections import DocSections
from sldb.store.models.section_context_record import SectionContextRecord
from sldb.store.models.sections_index import SectionsIndex
from sldb.store.semantic import RebuildReport, _about_terms, _slugify
import logging

logger = logging.getLogger(__name__)

""" + sections_code

Path("src/sldb/store/sections.py").write_text(sections_file_content)

# We also need to fix semantic.py
# Re-import rebuild_sections_indexes from sections.py in __init__.py? No, semantic.py exports it.
# Let's just add it to semantic.py
content += "\nfrom sldb.store.sections import rebuild_sections_indexes\n"

path.write_text(content)
