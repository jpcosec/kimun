# StructuredNLDoc Model

## Related Concepts

- [Model Reference](model-reference.atom.md)
- [Payload Inputs](payload-inputs.atom.md)
- [Tracked Document](tracked-document.atom.md)

## What It Is

A `StructuredNLDoc` model is the Pydantic-based contract that tells SLDB how to render Markdown, extract typed payload data from Markdown, and validate roundtrip behavior.

## Why It Matters

It is the contract boundary that turns free-form Markdown into a typed document workflow instead of an unstructured text file.

## How It Works

The model declares fields with descriptions and a `__template__`. SLDB uses the template markers to map between Markdown structure and typed field values. Optional semantics attach higher-level concepts to the model.

## When It Shows Up

You encounter it when creating a new model, registering one, validating idempotency, rendering docs, extracting payloads, or querying tracked docs.

## Where It Lives

It lives in Python code. Common examples are app-local docs modules and this repo's `docs/models.py`.

## Who Uses It

Model authors, CLI users, and automation all rely on the `StructuredNLDoc` contract whenever Markdown must remain human-readable while still being machine-structured.
