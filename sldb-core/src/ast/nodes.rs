use rowan::ast::AstNode;
use crate::ast::language::{SyntaxKind, SyntaxNode, SldbLanguage};

// Define concrete AST nodes that wrap the generic SyntaxNode.

#[derive(Debug, Clone, PartialEq, Eq, Hash)]
pub struct Document(SyntaxNode);
impl AstNode for Document {
    type Language = SldbLanguage;
    fn can_cast(kind: SyntaxKind) -> bool {
        kind == SyntaxKind::ROOT
    }
    fn cast(node: SyntaxNode) -> Option<Self> {
        if Self::can_cast(node.kind()) {
            Some(Self(node))
        } else {
            None
        }
    }
    fn syntax(&self) -> &SyntaxNode {
        &self.0
    }
}

#[derive(Debug, Clone, PartialEq, Eq, Hash)]
pub struct Paragraph(SyntaxNode);
impl AstNode for Paragraph {
    type Language = SldbLanguage;
    fn can_cast(kind: SyntaxKind) -> bool {
        kind == SyntaxKind::PARAGRAPH
    }
    fn cast(node: SyntaxNode) -> Option<Self> {
        if Self::can_cast(node.kind()) {
            Some(Self(node))
        } else {
            None
        }
    }
    fn syntax(&self) -> &SyntaxNode {
        &self.0
    }
}

#[derive(Debug, Clone, PartialEq, Eq, Hash)]
pub struct Heading(SyntaxNode);
impl AstNode for Heading {
    type Language = SldbLanguage;
    fn can_cast(kind: SyntaxKind) -> bool {
        kind == SyntaxKind::HEADING
    }
    fn cast(node: SyntaxNode) -> Option<Self> {
        if Self::can_cast(node.kind()) {
            Some(Self(node))
        } else {
            None
        }
    }
    fn syntax(&self) -> &SyntaxNode {
        &self.0
    }
}
