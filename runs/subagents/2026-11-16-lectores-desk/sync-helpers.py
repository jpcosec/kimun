#!/usr/bin/env python3
"""Limite de kimun: read-project solo importa RELACIONES de otro contexto (nunca defs),
asi que el helper de referencias definido en base.clj no se puede usar desde otro lector.
Este script copia el bloque canonico de base.clj (desde ';; Referencias') en cada lector,
despues de su (ns ...), de modo que todos usan texto identico."""
import glob, re, sys
d = sys.argv[1]
base = open(d + "/base.clj", encoding="utf8").read()
block = base[base.index(";; Referencias"):].rstrip() + "\n"
END = ";; --- fin de los helpers compartidos"
for p in sorted(glob.glob(d + "/*.clj")):
    if p.endswith("/base.clj"): continue
    t = open(p, encoding="utf8").read()
    t = re.sub(r"\n;; Referencias.*?" + re.escape(END) + r"\n", "", t, flags=re.S)
    i = t.index("\n") + 1  # el (ns ...) ocupa la primera linea
    t = t[:i] + "\n" + block + t[i:]
    open(p, "w", encoding="utf8").write(t)
