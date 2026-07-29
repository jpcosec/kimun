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

if __name__ == "__main__":
    app()
