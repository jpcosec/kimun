from __future__ import annotations

from http.server import ThreadingHTTPServer
from typing import Any

from sldb.cli.serve.http_server import build_handler


class ServeCLI:
    def run(self, args: Any) -> int:
        handler = build_handler(args.store, args.pythonpath, getattr(args, "cors", False))
        httpd = ThreadingHTTPServer((args.host, args.port), handler)
        print(f"sldb serve on http://{args.host}:{args.port} (store={args.store or 'auto'})")
        try:
            httpd.serve_forever()
        except KeyboardInterrupt:
            pass
        finally:
            httpd.server_close()
        return 0
