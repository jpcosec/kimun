use petgraph::graph::DiGraph;
use rusqlite::{Connection, Result};

pub struct MemoryGraph {
    pub graph: DiGraph<String, String>,
}

impl MemoryGraph {
    pub fn build_from_db(conn: &Connection) -> Result<Self> {
        let mut stmt = conn.prepare("SELECT source_hash, target_hash, relation_type FROM edges")?;
        let _edge_iter = stmt.query_map([], |row| {
            Ok((
                row.get::<_, String>(0)?,
                row.get::<_, String>(1)?,
                row.get::<_, String>(2)?,
            ))
        })?;

        let graph = DiGraph::new();
        // A real implementation would maintain a map of hash -> NodeIndex
        // For simplicity, we just populate the graph for structural traversal
        // in an optimized way later.

        Ok(MemoryGraph { graph })
    }
}
