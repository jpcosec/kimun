use tree_sitter::{Parser, Tree};

/// A simple adapter for tree-sitter.
/// This parses raw markdown strings into tree-sitter ASTs for downstream
/// code-oriented querying and projection.
/// It operates purely as an adapter, downstream of the canonical Rowan AST.
pub struct TreeSitterAdapter {
    parser: Parser,
}

impl TreeSitterAdapter {
    pub fn new() -> Self {
        let mut parser = Parser::new();
        // tree-sitter-md exposes language() for block and inline, we'll use block for general parsing
        parser.set_language(&tree_sitter_md::LANGUAGE.into()).expect("Error loading Markdown grammar");
        Self { parser }
    }

    /// Parses a raw string into a TreeSitter tree.
    pub fn parse_markdown(&mut self, source_code: &str) -> Option<Tree> {
        self.parser.parse(source_code, None)
    }
}

#[cfg(test)]
mod tests {
    use super::*;

    #[test]
    fn test_parse_markdown() {
        let mut adapter = TreeSitterAdapter::new();
        let tree = adapter.parse_markdown("# Heading 1\n\nThis is a paragraph.");
        assert!(tree.is_some());
        
        let root = tree.unwrap().root_node();
        assert_eq!(root.kind(), "document");
    }
}
