use rusqlite::{Connection, Result, params};
use super::repository::Node;

/// Performs a full-text search over the payloads using SQLite FTS5.
pub fn search_nodes_by_payload(conn: &Connection, query: &str) -> Result<Vec<Node>> {
    let mut stmt = conn.prepare(
        "SELECT n.hash, n.type, n.payload 
         FROM search_index s 
         JOIN nodes n ON s.hash = n.hash 
         WHERE s.payload MATCH ?1 
         ORDER BY rank"
    )?;
    
    let node_iter = stmt.query_map(params![query], |row| {
        Ok(Node {
            hash: row.get(0)?,
            node_type: row.get(1)?,
            payload: row.get(2)?,
        })
    })?;

    let mut nodes = Vec::new();
    for node in node_iter {
        nodes.push(node?);
    }
    Ok(nodes)
}
