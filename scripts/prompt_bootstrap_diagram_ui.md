Usa `scripts/bootstrap_diagram_ui_from_git.sh` para inicializar la UI de diagramas en otro proyecto.

Comando:

```bash
./scripts/bootstrap_diagram_ui_from_git.sh ~/overclock
```

Eso debe:
- clonar `deskops`, `sldb` (branch `refactor-target`) y `spec2viz` en `~/overclock/.diagram-ui/vendor/`
- copiar `docs/architecture/sldb_Kernel_Vistas_UML.html`
- copiar `docs/architecture/spec2viz/`
- copiar `docs/architecture/vistas/`
- instalar `scripts/annotate_server.py`
- crear `.diagram-ui/annotations.json`
- instalar `scripts/serve_diagram_ui.sh`

Validación mínima:

```bash
cd ~/overclock
nohup ./scripts/serve_diagram_ui.sh >/tmp/overclock-diagram-ui.log 2>&1 &
curl -I http://127.0.0.1:8765/docs/architecture/sldb_Kernel_Vistas_UML.html
curl http://127.0.0.1:8765/annotations
```
