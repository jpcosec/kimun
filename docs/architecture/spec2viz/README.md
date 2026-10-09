# spec2viz — diagramas de dirección (v2)

Diagramas del kernel v2 (ya en `kimun.*`). Se editan los YAML y se vuelven a renderizar los
`.mmd`; nunca se edita el render a mano.

```bash
spec2viz diagram validate docs/architecture/spec2viz/v2-*.yml
spec2viz diagram render docs/architecture/spec2viz/v2-*.yml \
  --out docs/architecture/spec2viz/rendered/v2 --renderer mermaid
spec2viz diagram lint docs/architecture/spec2viz/rendered/v2/*.mmd
```

| spec (`id: kimun.v2.*`) | fuente de verdad | qué muestra |
|---|---|---|
| `v2-rings.yml` | `docs/v2/03 §1` | anillos `kimun.kernel.*` / `kimun.host.*` / `kimun.surface.markdown.*` / producto / tests |
| `v2-cas-objects.yml` | `docs/v2/02 §2.1–§5`, `10 §5/§10/§11` | objetos del CAS, Revision de 8 campos, mmaps, caché del formato 4 |
| `v2-transaction-flow.yml` | `docs/v2/02 §5–§8.1`, `10 §11` | plan → revisión → persistencia (incl. `:register-tree`) |
| `v2-store-layout.yml` | `docs/v2/10 §5–§11` | `open` (sin replay), `stores check` y `stores verify` |
| `v2-replace-reanchoring.yml` | `docs/v2/02 §5.1, §6` | `:replace` y re-anclaje bajo sucesión |
| `v2-markdown-surface.yml` | `docs/v2/04` | perfil SLDB-MD (CST → AST → render / store / report) |

El manifiesto histórico del pipeline pre-v2 (vistas/HTML) sigue en
`docs/archive/architecture/spec2viz/manifest.yml`.
