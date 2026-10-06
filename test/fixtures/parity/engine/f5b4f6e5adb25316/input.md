---
id: cmd-widget-run
system: widget
command_path: run
synopsis: Run a widget.
tags:
- topic:authored
provenance: parser:selfdoc_test_factory:build#run; contract-sha256:b3e3fbfa3dae7b60558ea881f0fc5bd9aeb9021e7f1b7944474827cbddb56665
---

# run

## Synopsis

Run a widget.

## Purpose

An authored purpose.

## How It Works

An authored explanation.

## Arguments

```json
{
  "arguments": [
    {
      "names": [
        "--limit"
      ],
      "required": false,
      "default": "3",
      "choices": null,
      "nargs": null,
      "value_type": "builtins.int",
      "action": "argparse._StoreAction",
      "const": "null",
      "help": ""
    }
  ],
  "exclusive_groups": []
}
```

## Usage

widget run --limit 1
