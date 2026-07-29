use rusqlite::{Connection, Result, params};
use super::repository::Node;

/// Performs a basic text search over the payloads of all Nodes.
/// In a real implementation, this would integrate with a semantic search engine or inverted index.
pub fn search_nodes_by_payload(conn: &Connection, query: &str) -> Result<Vec<Node>> {
    let mut stmt = conn.prepare("SELECT hash, type, payload FROM nodes WHERE payload LIKE ?1")?;
    let search_pattern = format!("%{}%", query);
    
    let node_iter = stmt.query_map(params![search_pattern], |row| {
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
