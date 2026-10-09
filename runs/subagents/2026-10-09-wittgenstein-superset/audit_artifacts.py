"""Snapshot the audit's source scope and verify local Markdown references."""
import hashlib
import json
import re
import subprocess
from pathlib import Path

repo = Path(__file__).resolve().parents[3]
run = Path(__file__).resolve().parent
bundle = repo / 'docs/reviews/wittgenstein-superset'
wroot = Path('/home/jp/proyectos/wittgenstein')


def git_state(path):
    def invoke(*args):
        result = subprocess.run(['git', '-C', str(path), *args], text=True,
                                capture_output=True, check=False)
        return {'exit': result.returncode, 'output': result.stdout.strip(),
                'error': result.stderr.strip()}
    return {'head': invoke('rev-parse', 'HEAD'),
            'status': invoke('status', '--porcelain'),
            'root': invoke('rev-parse', '--show-toplevel')}


def sources(root, subdirs, suffixes):
    found = set()
    for relative in subdirs:
        path = root / relative
        if path.is_file():
            found.add(path)
        elif path.is_dir():
            found.update(p for p in path.rglob('*')
                         if p.is_file() and p.suffix in suffixes
                         and not set(p.parts) & {'__pycache__', '.pytest_cache', '.git', 'target'})
    return [{'path': str(p.relative_to(root)),
             'sha256': hashlib.sha256(p.read_bytes()).hexdigest(),
             'bytes': p.stat().st_size}
            for p in sorted(found)]


inventories = [
    (repo, ['AGENTS.md', 'README.md', 'docs/faq.md', 'desk/tasks/Board.md',
            'docs/v2', 'docs/archive/v2/09-evaluador-anclado.md',
            'src', 'test', 'scripts/check_rings.clj', 'bb.edn', 'release.edn'],
     {'.md', '.clj', '.cljc', '.edn'}),
    (wroot / 'matrix-shrdlu-spec', ['README.md', 'specs', 'docs', 'pipeline',
            'populator', 'sia', 'tests', 'matrix/src', 'matrix/tests',
            'matrix/prototypes/shrdlu', 'experiments'], {'.md', '.py'}),
    (wroot / 'Matrix/Matrix', ['README.md', 'docs', 'src', 'tests'], {'.md', '.py'}),
    (wroot / 'Matrix/paper_v2', ['README.md', 'sections', 'deltas'], {'.md', '.tex'}),
]
snapshot = {'date': '2026-10-09',
            'note': 'Scope inventory is not a claim of full reading of each file. '
                    'Hashes describe local content, including dirty/untracked source files.',
            'wittgenstein_root': git_state(wroot),
            'sources': [{'root': str(root), 'git': git_state(root),
                         'files': sources(root, directories, suffixes)}
                        for root, directories, suffixes in inventories]}
(run / 'source-inventory.json').write_text(
    json.dumps(snapshot, indent=2, ensure_ascii=False) + '\n')

errors = []
checked = 0
documents = sorted(bundle.rglob('*.md')) + [run / 'README.md']
for doc in documents:
    if not doc.exists():
        errors.append(f'Missing document: {doc}')
        continue
    text = doc.read_text()
    for target in re.findall(r'\[[^\]]+\]\(([^)]+)\)', text):
        if target.startswith(('http:', 'https:', '#')):
            continue
        path_text = target.split('#', 1)[0]
        path_text = re.sub(r':\d+$', '', path_text)
        path = Path(path_text)
        if not path.is_absolute():
            path = doc.parent / path
        checked += 1
        if not path.exists():
            errors.append(f'{doc.relative_to(repo)}: missing target {target}')
ids = re.findall(r'^### (F\d+) ', (bundle / 'reviews/02-findings.md').read_text(), re.M)
if len(ids) != len(set(ids)) or ids != [f'F{i:02}' for i in range(1, len(ids) + 1)]:
    errors.append('Finding IDs must be unique and sequential.')
print(json.dumps({'documents': len(documents), 'local_links': checked,
                  'findings': len(ids), 'source_files': sum(len(s['files']) for s in snapshot['sources']),
                  'errors': errors}, ensure_ascii=False))
raise SystemExit(bool(errors))
