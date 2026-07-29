use rusqlite::{Connection, Result};

pub const SCHEMA_INIT: &str = r#"
PRAGMA journal_mode = WAL;
PRAGMA synchronous = NORMAL;
PRAGMA foreign_keys = ON;

-- Append-only nodes table
CREATE TABLE IF NOT EXISTS nodes (
    hash TEXT PRIMARY KEY,
    type TEXT NOT NULL,
    payload TEXT,
    created_at DATETIME DEFAULT CURRENT_TIMESTAMP
);

-- Append-only edges table
CREATE TABLE IF NOT EXISTS edges (
    hash TEXT PRIMARY KEY,
    source_hash TEXT NOT NULL,
    target_hash TEXT NOT NULL,
    relation_type TEXT NOT NULL,
    created_at DATETIME DEFAULT CURRENT_TIMESTAMP,
    FOREIGN KEY(source_hash) REFERENCES nodes(hash),
    FOREIGN KEY(target_hash) REFERENCES nodes(hash)
);

-- Index for fast graph traversal
CREATE INDEX IF NOT EXISTS idx_edges_source ON edges(source_hash);
CREATE INDEX IF NOT EXISTS idx_edges_target ON edges(target_hash);

-- FTS5 Search Index
CREATE VIRTUAL TABLE IF NOT EXISTS search_index USING fts5(
    payload,
    hash UNINDEXED
);

-- Trigger to keep search index synchronized
CREATE TRIGGER IF NOT EXISTS nodes_ai AFTER INSERT ON nodes BEGIN
    INSERT INTO search_index(hash, payload) VALUES (new.hash, new.payload);
END;
"#;

pub fn initialize_schema(conn: &Connection) -> Result<()> {
    conn.execute_batch(SCHEMA_INIT)
}
