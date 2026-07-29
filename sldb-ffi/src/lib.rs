use pyo3::prelude::*;
use sldb_core::identity::hash_payload;

/// A simple PyO3 function to expose hashing to Python
#[pyfunction]
fn hash_data(payload: &str) -> PyResult<String> {
    Ok(hash_payload(payload.as_bytes()))
}

/// A simple Python module wrapping SLDB Core capabilities
#[pymodule]
fn sldb_ffi(m: &pyo3::Bound<'_, pyo3::types::PyModule>) -> PyResult<()> {
    m.add_function(wrap_pyfunction!(hash_data, m)?)?;
    Ok(())
}
