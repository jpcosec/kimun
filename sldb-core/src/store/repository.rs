use rusqlite::{Connection, Result, params};

/// Represents a Node in the Graph.
#[derive(Debug, PartialEq, Clone)]
pub struct Node {
    pub hash: String,
    pub node_type: String,
    pub payload: Option<String>,
}

/// Represents an Edge in the Graph.
#[derive(Debug, PartialEq, Clone)]
pub struct Edge {
    pub hash: String,
    pub source_hash: String,
    pub target_hash: String,
    pub relation_type: String,
}

/// Appends a new Node to the immutable store. 
/// Returns an error if the node already exists (since hashes dictate identity, 
/// duplicates are theoretically identical, but we rely on INSERT OR IGNORE or similar 
/// depending on architectural strictness. For now, strict append).
pub fn append_node(conn: &Connection, node: &Node) -> Result<()> {
    conn.execute(
        "INSERT INTO nodes (hash, type, payload) VALUES (?1, ?2, ?3)",
        params![node.hash, node.node_type, node.payload],
    )?;
    Ok(())
}

/// Appends a new Edge to the store.
pub fn append_edge(conn: &Connection, edge: &Edge) -> Result<()> {
    conn.execute(
        "INSERT INTO edges (hash, source_hash, target_hash, relation_type) VALUES (?1, ?2, ?3, ?4)",
        params![edge.hash, edge.source_hash, edge.target_hash, edge.relation_type],
    )?;
    Ok(())
}

#[cfg(test)]
mod tests {
    use super::*;
    use crate::store::connection::open_memory_store;
    use crate::identity::hash_payload;

    #[test]
    fn test_append_node_and_edge() {
        let conn = open_memory_store().unwrap();

        let n1_payload = b"Node 1 Data";
        let n1_hash = hash_payload(n1_payload);
        let n1 = Node {
            hash: n1_hash.clone(),
            node_type: "Doc".to_string(),
            payload: Some("Node 1 Data".to_string()),
        };

        let n2_payload = b"Node 2 Data";
        let n2_hash = hash_payload(n2_payload);
        let n2 = Node {
            hash: n2_hash.clone(),
            node_type: "Doc".to_string(),
            payload: Some("Node 2 Data".to_string()),
        };

        append_node(&conn, &n1).unwrap();
        append_node(&conn, &n2).unwrap();

        let edge_payload = format!("{}-{}-{}", n1_hash, n2_hash, "LinksTo");
        let edge_hash = hash_payload(edge_payload.as_bytes());

        let edge = Edge {
            hash: edge_hash,
            source_hash: n1_hash,
            target_hash: n2_hash,
            relation_type: "LinksTo".to_string(),
        };

        append_edge(&conn, &edge).unwrap();

        // Query back
        let count: i32 = conn.query_row("SELECT COUNT(*) FROM nodes", [], |row| row.get(0)).unwrap();
        assert_eq!(count, 2);

        let edge_count: i32 = conn.query_row("SELECT COUNT(*) FROM edges", [], |row| row.get(0)).unwrap();
        assert_eq!(edge_count, 1);
    }
}
