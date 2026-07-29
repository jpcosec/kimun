use pyo3::prelude::*;
use sldb_core::identity::hash_payload;
use sldb_core::store::connection::open_store;
use sldb_core::store::queries::{get_node, get_edge, get_outgoing_edges, get_incoming_edges};
use sldb_core::store::search::search_nodes_by_payload;

/// A simple PyO3 function to expose hashing to Python
#[pyfunction]
fn hash_data(payload: &str) -> PyResult<String> {
    Ok(hash_payload(payload.as_bytes()))
}

#[pyclass(name = "Node")]
#[derive(Clone)]
pub struct PyNode {
    #[pyo3(get, set)]
    pub hash: String,
    #[pyo3(get, set)]
    pub node_type: String,
    #[pyo3(get, set)]
    pub payload: Option<String>,
}

impl From<sldb_core::store::repository::Node> for PyNode {
    fn from(node: sldb_core::store::repository::Node) -> Self {
        PyNode {
            hash: node.hash,
            node_type: node.node_type,
            payload: node.payload,
        }
    }
}

#[pyclass(name = "Edge")]
#[derive(Clone)]
pub struct PyEdge {
    #[pyo3(get, set)]
    pub hash: String,
    #[pyo3(get, set)]
    pub source_hash: String,
    #[pyo3(get, set)]
    pub target_hash: String,
    #[pyo3(get, set)]
    pub relation_type: String,
}

impl From<sldb_core::store::repository::Edge> for PyEdge {
    fn from(edge: sldb_core::store::repository::Edge) -> Self {
        PyEdge {
            hash: edge.hash,
            source_hash: edge.source_hash,
            target_hash: edge.target_hash,
            relation_type: edge.relation_type,
        }
    }
}

#[pyclass]
pub struct SldbStore {
    conn: rusqlite::Connection,
}

#[pymethods]
impl SldbStore {
    #[new]
    fn new(path: &str) -> PyResult<Self> {
        let conn = open_store(path)
            .map_err(|e| pyo3::exceptions::PyRuntimeError::new_err(e.to_string()))?;
        Ok(SldbStore { conn })
    }

    fn get_node(&self, hash: &str) -> PyResult<Option<PyNode>> {
        let node = get_node(&self.conn, hash)
            .map_err(|e| pyo3::exceptions::PyRuntimeError::new_err(e.to_string()))?;
        Ok(node.map(PyNode::from))
    }

    fn get_edge(&self, hash: &str) -> PyResult<Option<PyEdge>> {
        let edge = get_edge(&self.conn, hash)
            .map_err(|e| pyo3::exceptions::PyRuntimeError::new_err(e.to_string()))?;
        Ok(edge.map(PyEdge::from))
    }

    fn get_outgoing_edges(&self, source_hash: &str) -> PyResult<Vec<PyEdge>> {
        let edges = get_outgoing_edges(&self.conn, source_hash)
            .map_err(|e| pyo3::exceptions::PyRuntimeError::new_err(e.to_string()))?;
        Ok(edges.into_iter().map(PyEdge::from).collect())
    }

    fn get_incoming_edges(&self, target_hash: &str) -> PyResult<Vec<PyEdge>> {
        let edges = get_incoming_edges(&self.conn, target_hash)
            .map_err(|e| pyo3::exceptions::PyRuntimeError::new_err(e.to_string()))?;
        Ok(edges.into_iter().map(PyEdge::from).collect())
    }

    fn search_nodes_by_payload(&self, query: &str) -> PyResult<Vec<PyNode>> {
        let nodes = search_nodes_by_payload(&self.conn, query)
            .map_err(|e| pyo3::exceptions::PyRuntimeError::new_err(e.to_string()))?;
        Ok(nodes.into_iter().map(PyNode::from).collect())
    }
}

/// A simple Python module wrapping SLDB Core capabilities
#[pymodule]
fn sldb_ffi(m: &Bound<'_, pyo3::types::PyModule>) -> PyResult<()> {
    m.add_function(wrap_pyfunction!(hash_data, m)?)?;
    m.add_class::<PyNode>()?;
    m.add_class::<PyEdge>()?;
    m.add_class::<SldbStore>()?;
    Ok(())
}
