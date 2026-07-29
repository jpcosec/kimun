use rusqlite::{Connection, Result, params, OptionalExtension};
use super::repository::{Node, Edge};

/// Retrieves a Node by its hash.
pub fn get_node(conn: &Connection, hash: &str) -> Result<Option<Node>> {
    conn.query_row(
        "SELECT hash, type, payload FROM nodes WHERE hash = ?1",
        params![hash],
        |row| {
            Ok(Node {
                hash: row.get(0)?,
                node_type: row.get(1)?,
                payload: row.get(2)?,
            })
        },
    ).optional()
}

/// Retrieves an Edge by its hash.
pub fn get_edge(conn: &Connection, hash: &str) -> Result<Option<Edge>> {
    conn.query_row(
        "SELECT hash, source_hash, target_hash, relation_type FROM edges WHERE hash = ?1",
        params![hash],
        |row| {
            Ok(Edge {
                hash: row.get(0)?,
                source_hash: row.get(1)?,
                target_hash: row.get(2)?,
                relation_type: row.get(3)?,
            })
        },
    ).optional()
}

/// Retrieves all outgoing edges from a given node hash.
pub fn get_outgoing_edges(conn: &Connection, source_hash: &str) -> Result<Vec<Edge>> {
    let mut stmt = conn.prepare("SELECT hash, source_hash, target_hash, relation_type FROM edges WHERE source_hash = ?1")?;
    let edge_iter = stmt.query_map(params![source_hash], |row| {
        Ok(Edge {
            hash: row.get(0)?,
            source_hash: row.get(1)?,
            target_hash: row.get(2)?,
            relation_type: row.get(3)?,
        })
    })?;

    let mut edges = Vec::new();
    for edge in edge_iter {
        edges.push(edge?);
    }
    Ok(edges)
}

/// Retrieves all incoming edges to a given node hash.
pub fn get_incoming_edges(conn: &Connection, target_hash: &str) -> Result<Vec<Edge>> {
    let mut stmt = conn.prepare("SELECT hash, source_hash, target_hash, relation_type FROM edges WHERE target_hash = ?1")?;
    let edge_iter = stmt.query_map(params![target_hash], |row| {
        Ok(Edge {
            hash: row.get(0)?,
            source_hash: row.get(1)?,
            target_hash: row.get(2)?,
            relation_type: row.get(3)?,
        })
    })?;

    let mut edges = Vec::new();
    for edge in edge_iter {
        edges.push(edge?);
    }
    Ok(edges)
}
