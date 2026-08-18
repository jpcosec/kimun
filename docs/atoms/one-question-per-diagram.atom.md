# One Question Per Diagram

## Related Concepts

- Structured Text
- Text Layer vs Graph Layer
- Semantic vs Physical Search

## What It Is

A useful architecture diagram should answer one main question at one level of abstraction. When a single diagram mixes system boundaries, module inventory, classes, and execution flow, it stops being an explanatory view and becomes a visual dump.

## Why It Matters

Oversized mixed-abstraction diagrams create reading noise. They hide the real structure, make onboarding harder, and prevent the viewer from understanding where to look for design decisions, dependency boundaries, or runtime flow.

## How It Works

Split architecture communication into separate views with clear intent. Use one view for boundaries and major subsystems, another for a pipeline or lifecycle, another for module dependencies, and another for deep implementation detail when needed. Each view should state what question it answers and what detail level it excludes.

## When It Shows Up

This matters when an architecture artifact starts to look like a complete repository inventory instead of a decision surface. It is especially visible when a diagram contains too many node kinds at once, such as systems, modules, classes, helpers, and external libraries.

## Where It Lives

This principle should guide generated architecture pages, Mermaid or PlantUML specs, and any documentation process that turns AST or import graphs into human-facing diagrams.

## Who Uses It

Contributors use it to design clearer diagrams. Reviewers use it to reject unreadable architecture surfaces. Tooling uses it to decide when a generated graph needs to be split into multiple curated views instead of rendered as one giant image.
