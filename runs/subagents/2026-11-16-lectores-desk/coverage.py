#!/usr/bin/env python3
"""coverage.py DESK_COPY_DIR READ_EDN > coverage.txt
Cobertura: cada desk/**/*.md, con frontmatter o sin él, y si alguna proposición
de read.edn lo tiene como :source :path. Sin dependencias (frontmatter = '---' en la línea 1)."""
import os, re, sys, collections
root, edn = sys.argv[1], sys.argv[2]
txt = open(edn, encoding="utf8").read()
srcs = set(re.findall(r':source\s*\{[^}]*?:path\s+"([^"]+)"', txt))
tot = collections.defaultdict(lambda: [0, 0, 0, 0])  # files, con front, sin front, con front y sin prop
miss, nofm = [], collections.defaultdict(int)
for r, _, fs in os.walk(os.path.join(root, "desk")):
    for f in sorted(fs):
        if not f.endswith(".md"): continue
        p = os.path.relpath(os.path.join(r, f), root)
        parts = p.split("/")
        top = "/".join(parts[1:3]) if parts[1] == "drawer" and len(parts) > 3 else (parts[1] if len(parts) > 2 else "(raiz)")
        t = open(os.path.join(root, p), encoding="utf8").read()
        has = bool(re.match(r"---\r?\n.*?\r?\n---", t, re.S))
        row = tot[top]; row[0] += 1
        if has:
            row[1] += 1
            if p not in srcs: row[3] += 1; miss.append(p)
        else: row[2] += 1
print("carpeta | archivos | con frontmatter | sin frontmatter | con frontmatter y sin proposicion")
for k in sorted(tot): print(k, *tot[k], sep=" | ")
print("TOTAL", *[sum(v[i] for v in tot.values()) for i in range(4)], sep=" | ")
print("fuentes distintas en read.edn:", len(srcs))
print("\nArchivos con frontmatter y sin proposicion: %d" % len(miss))
for m in miss: print(" ", m)
