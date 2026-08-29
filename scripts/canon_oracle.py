#!/usr/bin/env python3
"""Independent oracle for canonical-bytes (docs/v2/02 §2.1).

Re-implements, in Python and without looking at the Clojure code, the canonical
printing of the EDN subset admitted in node content (strings, integers,
booleans, nil, keywords, vectors, maps, sets) plus NFC normalization and
SHA-256, and checks the frozen ids of test/fixtures/nodes.edn. Exit 1 on any
mismatch, so the conformance of sldb.kernel.canon is not circular.
"""
import hashlib
import sys
import unicodedata

# ---------------------------------------------------------------- minimal EDN reader

class Kw(str):
    """A keyword; printed with a leading colon."""

class Sym(str):
    """A symbol; printed bare."""

NIL = object()


class Reader:
    def __init__(self, text):
        self.s, self.i = text, 0

    def peek(self):
        return self.s[self.i] if self.i < len(self.s) else ""

    def skip(self):
        while self.i < len(self.s):
            c = self.s[self.i]
            if c in " \t\n\r,":
                self.i += 1
            elif c == ";":
                while self.i < len(self.s) and self.s[self.i] != "\n":
                    self.i += 1
            else:
                break

    def read(self):
        self.skip()
        c = self.peek()
        if c == "{":
            self.i += 1
            items = []
            while True:
                self.skip()
                if self.peek() == "}":
                    self.i += 1
                    break
                items.append(self.read())
            if len(items) % 2:
                raise ValueError("odd map")
            return dict(zip(items[::2], items[1::2]))
        if c == "[":
            self.i += 1
            out = []
            while True:
                self.skip()
                if self.peek() == "]":
                    self.i += 1
                    return out
                out.append(self.read())
        if c == "#" and self.s[self.i:self.i + 2] == "#{":
            self.i += 2
            out = set()
            while True:
                self.skip()
                if self.peek() == "}":
                    self.i += 1
                    return frozenset(out)
                out.add(self.read())
        if c == '"':
            self.i += 1
            out = []
            while self.peek() != '"':
                ch = self.s[self.i]
                if ch == "\\":
                    nxt = self.s[self.i + 1]
                    out.append({"n": "\n", "t": "\t", "r": "\r", '"': '"', "\\": "\\"}[nxt])
                    self.i += 2
                else:
                    out.append(ch)
                    self.i += 1
            self.i += 1
            return "".join(out)
        if c == ":":
            self.i += 1
            return Kw(self.token())
        tok = self.token()
        if tok == "nil":
            return NIL
        if tok == "true":
            return True
        if tok == "false":
            return False
        try:
            return int(tok)
        except ValueError:
            return Sym(tok)

    def token(self):
        j = self.i
        while self.i < len(self.s) and self.s[self.i] not in ' \t\n\r,{}[]()"':
            self.i += 1
        return self.s[j:self.i]


# ---------------------------------------------------------------- canonical printing

def edn_string(s):
    return '"' + s.replace("\\", "\\\\").replace('"', '\\"').replace("\n", "\\n").replace("\t", "\\t").replace("\r", "\\r") + '"'


def canon(v):
    if v is NIL:
        return "nil"
    if v is True:
        return "true"
    if v is False:
        return "false"
    if isinstance(v, Kw):
        return ":" + v
    if isinstance(v, Sym):
        return str(v)
    if isinstance(v, str):
        return edn_string(unicodedata.normalize("NFC", v))
    if isinstance(v, int):
        return str(v)
    if isinstance(v, float):
        raise ValueError("floats are not admitted")
    if isinstance(v, dict):
        entries = sorted(((canon(k), canon(x)) for k, x in v.items()), key=lambda kv: kv[0])
        return "{" + " ".join(k + " " + x for k, x in entries) + "}"
    if isinstance(v, (frozenset, set)):
        return "#{" + " ".join(sorted(canon(x) for x in v)) + "}"
    if isinstance(v, list):
        return "[" + " ".join(canon(x) for x in v) + "]"
    raise ValueError("type not admitted: %r" % (v,))


def node_id(cls, kind, content):
    form = {Kw("class"): cls, Kw("kind"): kind, Kw("content"): content}
    return hashlib.sha256(canon(form).encode("utf-8")).hexdigest()


def main(path="test/fixtures/nodes.edn"):
    with open(path, encoding="utf-8") as f:
        rows = Reader(f.read()).read()
    bad = 0
    for row in rows:
        got = node_id(row[Kw("class")], row[Kw("kind")], row[Kw("content")])
        exp = row[Kw("expected-id")]
        status = "ok " if got == exp else "BAD"
        if got != exp:
            bad += 1
        print(f"{status} {row[Kw('class')]}/{row[Kw('kind')]} {got[:16]}…")
    print(f"{len(rows)} rows, {bad} mismatches")
    sys.exit(1 if bad else 0)


if __name__ == "__main__":
    main(*sys.argv[1:])
