# Payload Inputs

## Related Concepts

- [Model Reference](model-reference.atom.md)
- [StructuredNLDoc Model](structurednldoc-model.atom.md)
- [Tracked Document](tracked-document.atom.md)

## What It Is

Payload inputs are the YAML or JSON values that fill a `StructuredNLDoc` model when SLDB renders or updates a document.

## Why It Matters

Users need to know whether a command expects a Markdown file, a typed payload file, or inline data. That distinction is one of the biggest first-use pain points.

## How It Works

Commands such as `docs create`, `docs update`, and `render` accept either inline YAML/JSON or a file path to YAML/JSON data, depending on the command. The data must match the target model schema.

## When It Shows Up

You encounter payload inputs when creating a new tracked doc, updating a tracked doc, rendering Markdown from data, or validating a model from `--data` instead of `--input` Markdown.

## Where It Lives

Payload data can live inline on the command line or in files such as `data.yaml` or `payload.json`.

## Who Uses It

Anyone creating, updating, or rendering SLDB documents uses payload inputs.
