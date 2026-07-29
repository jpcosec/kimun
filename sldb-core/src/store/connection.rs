use rusqlite::{Connection, Result};
use std::path::Path;
use super::schema::initialize_schema;

/// Opens an SQLite connection (or creates a new database) and initializes the schema.
pub fn open_store<P: AsRef<Path>>(path: P) -> Result<Connection> {
    let conn = Connection::open(path)?;
    initialize_schema(&conn)?;
    Ok(conn)
}

/// Opens an in-memory database for testing.
pub fn open_memory_store() -> Result<Connection> {
    let conn = Connection::open_in_memory()?;
    initialize_schema(&conn)?;
    Ok(conn)
}

#[cfg(test)]
mod tests {
    use super::*;

    #[test]
    fn test_memory_store_initializes() {
        let conn = open_memory_store().expect("Failed to open memory store");
        // Verify nodes table exists
        let mut stmt = conn.prepare("SELECT name FROM sqlite_master WHERE type='table' AND name='nodes'").unwrap();
        let exists = stmt.exists([]).unwrap();
        assert!(exists);
    }
}
