use blake3::Hasher;

/// Generates a Blake3 hash for a given byte payload.
/// This hash is used as the cryptographic identity for nodes and edges.
pub fn hash_payload(payload: &[u8]) -> String {
    let mut hasher = Hasher::new();
    hasher.update(payload);
    hasher.finalize().to_hex().to_string()
}

#[cfg(test)]
mod tests {
    use super::*;

    #[test]
    fn test_hash_determinism() {
        let hash1 = hash_payload(b"hello world");
        let hash2 = hash_payload(b"hello world");
        assert_eq!(hash1, hash2);
    }
}
