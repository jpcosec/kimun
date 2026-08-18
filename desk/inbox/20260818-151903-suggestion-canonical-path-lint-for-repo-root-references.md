---
kind: suggestion
sender_project: AntonIA
created_at: 2026-08-18T15:19:03
status: open
---

# canonical path lint for repo-root references

Propuesta para SLDB: agregar un lint/regla de validación de referencias canónicas en artefactos de conocimiento. Reglas sugeridas: (1) permitir solo paths canónicos desde raíz del repo, por ejemplo source_docs/, projects/, software/, docs/, desk/; (2) rechazar rutas frágiles relativas como ../, ../../, ../../../ cuando se usan como referencias de conocimiento; (3) rechazar prefijos legacy como Lab_Chile-Teva/ y otros_proyectos/ en referencias canónicas; (4) validar que la ruta canónica referenciada exista. Caso motivador: reorganización taxonómica de AntonIA donde necesitamos paths estables e idempotentes para README, project.yaml, software.yaml, atoms, docs y provenance textual.
