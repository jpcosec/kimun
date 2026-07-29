use pulldown_cmark::{Parser, Event, Tag};
use crate::ast::nodes;
// Simulated rowan AST integration
pub fn parse_markdown_to_ast(markdown: &str) -> String {
    let parser = Parser::new(markdown);
    let mut ast_json = String::from("[");
    
    for event in parser {
        match event {
            Event::Start(Tag::Heading { level, .. }) => {
                ast_json.push_str(&format!(r#"{{"type":"heading","level":{}}},"#, level as usize));
            },
            Event::Start(Tag::Paragraph) => {
                ast_json.push_str(r#"{"type":"paragraph"},"#);
            },
            Event::Text(text) => {
                let escaped = text.replace("\"", "\\\"");
                ast_json.push_str(&format!(r#"{{"type":"text","value":"{escaped}"}},"#));
            },
            _ => {}
        }
    }
    ast_json.push_str("]");
    ast_json
}
