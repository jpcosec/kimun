use rowan::Language;

#[derive(Debug, Clone, Copy, PartialEq, Eq, PartialOrd, Ord, Hash)]
#[allow(non_camel_case_types)]
pub enum SyntaxKind {
    ROOT,
    HEADING,
    PARAGRAPH,
    TEXT,
    WHITESPACE,
    // Add more markdown syntax kinds as needed
    ERROR,
}

impl From<SyntaxKind> for rowan::SyntaxKind {
    fn from(kind: SyntaxKind) -> Self {
        Self(kind as u16)
    }
}

#[derive(Debug, Clone, Copy, PartialEq, Eq, PartialOrd, Ord, Hash)]
pub struct SldbLanguage;

impl Language for SldbLanguage {
    type Kind = SyntaxKind;

    fn kind_from_raw(raw: rowan::SyntaxKind) -> Self::Kind {
        let kind = raw.0;
        match kind {
            0 => SyntaxKind::ROOT,
            1 => SyntaxKind::HEADING,
            2 => SyntaxKind::PARAGRAPH,
            3 => SyntaxKind::TEXT,
            4 => SyntaxKind::WHITESPACE,
            _ => SyntaxKind::ERROR,
        }
    }

    fn kind_to_raw(kind: Self::Kind) -> rowan::SyntaxKind {
        kind.into()
    }
}

pub type SyntaxNode = rowan::SyntaxNode<SldbLanguage>;
pub type SyntaxToken = rowan::SyntaxToken<SldbLanguage>;
pub type SyntaxElement = rowan::SyntaxElement<SldbLanguage>;

#[cfg(test)]
mod tests {
    use super::*;
    use rowan::GreenNodeBuilder;

    #[test]
    fn test_build_simple_tree() {
        let mut builder = GreenNodeBuilder::new();
        
        builder.start_node(SyntaxKind::ROOT.into());
        builder.start_node(SyntaxKind::PARAGRAPH.into());
        builder.token(SyntaxKind::TEXT.into(), "Hello, AST!");
        builder.finish_node(); // finish PARAGRAPH
        builder.finish_node(); // finish ROOT

        let green = builder.finish();
        let root = SyntaxNode::new_root(green);

        assert_eq!(root.kind(), SyntaxKind::ROOT);
        assert_eq!(root.text().to_string(), "Hello, AST!");
    }
}
