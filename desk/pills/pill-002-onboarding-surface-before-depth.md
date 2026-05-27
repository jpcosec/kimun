---
pill_type: pattern
scope: domain
nature: implementation
bound_to: CLI help, FAQ, docs explore, desk onboarding
created: "2026-05-20"
lifecycle: current
---

# Put a small onboarding surface before the deep system

When a tool has many concepts, the first-use layer should be a small stack:

1. portada help
2. curated help by topic
3. FAQ by question
4. deep exploration over docs and docstrings

That stack prevents users from needing full architectural context just to understand the first command they should run.

In this repo, that means `sldb --help`, `sldb help`, `sldb faq`, and `sldb explore` should work together as one layered onboarding system.
