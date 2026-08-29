import os
import re

atoms_dir = 'desk/atoms'
files = [f for f in os.listdir(atoms_dir) if f.endswith('.md')]

categories = {
    'shell': ['-command-group.md', 'cli-', 'python-', 'git-orchestration', 'direct-mode.md', 'store-backed-mode.md', 'plural-first-cli-surface.md', 'visual-ux-surface.md', 'legacy-cli-aliases.md'],
    'core': ['rust-', 'canonical-ast.md', 'rowan', 'blake3', 'importer-', 'emitter-', 'markdown-importer.md', 'markdown-emitter.md', 'ast-anchor.md', 'ast-locator.md', 'relation-ast', 'treesitter', 'pulldown', 'hashing', 'node-hash.md', 'rayon', 'document-materializer.md', 'prosemirror-adapter.md', 'matrix-adapter.md', 'tree-sitter-adapter.md', 'semantic-exporter.md'],
    'store': ['graph-store.md', 'rusqlite', 'database', 'derived-index.md', 'field-index.md', 'section-index.md', 'snapshots.md', 'append-only-event-log.md', 'store-infrastructure.md', 'store-integrity-checks.md', 'merkle-index.md', 'dependency-index.md', 'what-a-store-is.md', 'immutable-append-only-database.md', 'local-vs-global-store-precedence.md'],
    'shared': ['addressability-layer.md', 'canonical-address.md', 'canonical-identity.md', 'canonical-existence.md', 'document-path.md', 'stable-selector.md', 'source-document-hash.md', 'tracked-document-identity.md', 'semantic-role.md', 'semantic-tag.md', 'semantic-reference.md', 'fragment-id.md', 'node.md', 'document.md', 'field-path.md', 'provenance-record.md', 'identity-layer.md', 'hash-field.md', 'stable-selector.md', 'locator-strategy.md', 'decision-links-and-anchors-are-canonical.md']
}

keywords = {
    'shell': ['cli', 'python', 'git', 'orchestration', 'surface', 'workflow', 'ux'],
    'core': ['rust', 'ast', 'parsing', 'importer', 'emitter', 'canonical', 'ffi', 'hashing', 'logic', 'adapter'],
    'store': ['store', 'database', 'persist', 'index', 'sql', 'sqlite', 'log', 'snapshot'],
    'shared': ['identity', 'address', 'path', 'selector', 'contract', 'concept', 'binding', 'format', 'schema', 'type', 'semantic']
}

def get_layer(filename, content):
    filename = filename.lower()
    content = content.lower()
    for layer, patterns in categories.items():
        if any(p in filename for p in patterns): return layer
    for layer, keys in keywords.items():
        if any(k in filename for k in keys): return layer
    for layer, keys in keywords.items():
        if any(k in content for k in keys): return layer
    return 'shared'

for filename in files:
    path = os.path.join(atoms_dir, filename)
    with open(path, 'r') as f:
        content = f.read()
    
    layer = get_layer(filename, content)
    
    # Remove existing layer: tag if any (clean up previous run)
    content = re.sub(r'layer: \w+\n?', '', content)
    # Fix the ---layer: mess
    content = content.replace('---layer:', '---\nlayer:')
    
    # Proper insertion
    if content.startswith('---'):
        first_line_end = content.find('\n')
        if first_line_end != -1:
            content = content[:first_line_end+1] + f'layer: {layer}\n' + content[first_line_end+1:]
    
    with open(path, 'w') as f:
        f.write(content)
