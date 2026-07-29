import typer
# import sldb_ffi # FFI module

app = typer.Typer()

@app.command()
def status():
    """Check SLDB status"""
    typer.echo("SLDB Core is active.")

@app.command()
def hash_text(text: str):
    """Hash text using SLDB Core Blake3"""
    try:
        import sldb_ffi
        result = sldb_ffi.hash_data(text)
        typer.echo(f"Hash: {result}")
    except ImportError:
        typer.echo("Error: sldb_ffi not installed or compiled. Build it with maturin.")

@app.command()
def track(path: str, model: str = "Doc"):
    """Track a document into SLDB"""
    typer.echo(f"Tracking {path} as {model}...")
    # Integration logic will use importers/markdown.py
    
@app.command()
def build():
    """Build the SLDB Graph Snapshot"""
    typer.echo("Building graph snapshot...")
    # Integration logic will use emitters and sldb core

if __name__ == "__main__":
    app()
