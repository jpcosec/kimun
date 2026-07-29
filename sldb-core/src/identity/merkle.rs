use super::hash_payload;

/// Represents a node in the Merkle Tree, containing its hash and optional children.
#[derive(Debug, Clone, PartialEq)]
pub struct MerkleNode {
    pub hash: String,
    pub left: Option<Box<MerkleNode>>,
    pub right: Option<Box<MerkleNode>>,
}

impl MerkleNode {
    /// Creates a leaf node with the hash of the given data.
    pub fn new_leaf(data: &[u8]) -> Self {
        Self {
            hash: hash_payload(data),
            left: None,
            right: None,
        }
    }

    /// Combines two nodes into a parent node.
    pub fn combine(left: MerkleNode, right: MerkleNode) -> Self {
        let combined_data = format!("{}{}", left.hash, right.hash);
        Self {
            hash: hash_payload(combined_data.as_bytes()),
            left: Some(Box::new(left)),
            right: Some(Box::new(right)),
        }
    }
}

/// Builds a Merkle Tree from a list of data chunks and returns the root node.
pub fn build_merkle_tree(chunks: &[&[u8]]) -> Option<MerkleNode> {
    if chunks.is_empty() {
        return None;
    }

    let mut nodes: Vec<MerkleNode> = chunks.iter().map(|c| MerkleNode::new_leaf(c)).collect();

    while nodes.len() > 1 {
        let mut next_level = Vec::new();
        for i in (0..nodes.len()).step_by(2) {
            if i + 1 < nodes.len() {
                next_level.push(MerkleNode::combine(nodes[i].clone(), nodes[i + 1].clone()));
            } else {
                next_level.push(nodes[i].clone());
            }
        }
        nodes = next_level;
    }

    nodes.into_iter().next()
}

#[cfg(test)]
mod tests {
    use super::*;

    #[test]
    fn test_merkle_tree() {
        let chunks: Vec<&[u8]> = vec![b"chunk1", b"chunk2", b"chunk3"];
        let root = build_merkle_tree(&chunks);
        assert!(root.is_some());
        
        let root_node = root.unwrap();
        assert!(root_node.left.is_some());
        assert!(root_node.right.is_some());
    }
}
