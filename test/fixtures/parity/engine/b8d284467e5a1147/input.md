---
id: cmd-widget-run
system: widget
command_path: run
synopsis: Run a widget.
tags:
- topic:authored
provenance: parser:selfdoc_test_factory:build#run; contract-sha256:53db353eb08395d5117d5c8f9b016bf68f16f41b8abccd6db9e0273b19bf34a7
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
      "default": "7",
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