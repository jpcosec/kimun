//! pod-kimun-treesitter: a babashka pod exposing tree-sitter parsing.
//!
//! Protocol: bencode messages on stdin/stdout; payloads (args, value) are JSON.
//! Vars under `pod.kimun.treesitter`:
//!   (parse text)                 -> combined Markdown CST (block + inline)
//!   (parse-block text)           -> Markdown block CST only
//!   (parse-inline text)          -> Markdown inline CST
//!   (parse-lang text language)   -> CST with grammar "markdown"|"markdown-inline"|"python"
//!   (query text query-string lang) -> capture list
//!   (version)                    -> plugin version and grammar ABI info
//!
//! Node objects: {type, start_byte, end_byte, start_point[row,col],
//!                end_point[row,col], named, inline, field?, missing?, error?,
//!                text? (leaves), children?}

mod bencode;

use std::collections::BTreeMap;
use std::io::{self, BufWriter, Write};

use bencode::B;
use serde_json::{json, Map, Value};
use tree_sitter::{Language, Node, Query, QueryCursor, StreamingIterator, Tree};

use tree_sitter_md::{MarkdownCursor, MarkdownParser};

const VERSION: &str = env!("CARGO_PKG_VERSION");
const MAX_DEPTH: usize = 4096;

// --------------------------------------------------------------------- JSON CST

fn point(p: tree_sitter::Point) -> Value {
    json!([p.row, p.column])
}

fn node_json(node: &Node, src: &[u8], depth: usize) -> Value {
    let mut obj = Map::new();
    obj.insert("type".into(), json!(node.kind()));
    obj.insert("start_byte".into(), json!(node.start_byte()));
    obj.insert("end_byte".into(), json!(node.end_byte()));
    obj.insert("start_point".into(), point(node.start_position()));
    obj.insert("end_point".into(), point(node.end_position()));
    obj.insert("named".into(), json!(node.is_named()));
    if node.is_missing() {
        obj.insert("missing".into(), json!(true));
    }
    if node.is_error() {
        obj.insert("error".into(), json!(true));
    }
    if node.has_error() {
        obj.insert("has_error".into(), json!(true));
    }
    if node.child_count() == 0 && node.is_named() {
        if let Ok(t) = node.utf8_text(src) {
            obj.insert("text".into(), json!(t));
        }
    }
    if depth < MAX_DEPTH {
        let n = node.child_count();
        let mut kids = Vec::with_capacity(n);
        for i in 0..n {
            if let Some(child) = node.child(i as u32) {
                let mut c = node_json(&child, src, depth + 1);
                if let Some(f) = node.field_name_for_child(i as u32) {
                    c.as_object_mut()
                        .unwrap()
                        .insert("field".into(), json!(f));
                }
                kids.push(c);
            }
        }
        if !kids.is_empty() {
            obj.insert("children".into(), Value::Array(kids));
        }
    }
    Value::Object(obj)
}

fn tree_json(tree: &Tree, src: &[u8]) -> Value {
    Value::Array(vec![node_json(&tree.root_node(), src, 0)])
}

// -------------------------------------------------- combined markdown (block+inline)

fn walk_md(cursor: &mut MarkdownCursor, src: &[u8], depth: usize, include_inline: bool) -> Value {
    let node = cursor.node();
    let mut obj = Map::new();
    obj.insert("type".into(), json!(node.kind()));
    obj.insert("start_byte".into(), json!(node.start_byte()));
    obj.insert("end_byte".into(), json!(node.end_byte()));
    obj.insert("start_point".into(), point(node.start_position()));
    obj.insert("end_point".into(), point(node.end_position()));
    obj.insert("named".into(), json!(node.is_named()));
    obj.insert("inline".into(), json!(cursor.is_inline()));
    if let Some(f) = cursor.field_name() {
        obj.insert("field".into(), json!(f));
    }
    if node.is_missing() {
        obj.insert("missing".into(), json!(true));
    }
    if node.is_error() {
        obj.insert("error".into(), json!(true));
    }
    if node.has_error() {
        obj.insert("has_error".into(), json!(true));
    }
    if node.child_count() == 0 && node.is_named() {
        if let Ok(t) = node.utf8_text(src) {
            obj.insert("text".into(), json!(t));
        }
    }
    let descend = depth < MAX_DEPTH && (include_inline || !cursor.is_inline());
    if descend && cursor.goto_first_child() {
        let mut kids = Vec::new();
        loop {
            kids.push(walk_md(cursor, src, depth + 1, include_inline));
            if !cursor.goto_next_sibling() {
                break;
            }
        }
        cursor.goto_parent();
        obj.insert("children".into(), Value::Array(kids));
    }
    Value::Object(obj)
}

// ------------------------------------------------------------------- languages

fn language(name: &str) -> Result<Language, String> {
    match name {
        "markdown" => Ok(tree_sitter_md::LANGUAGE.into()),
        "markdown-inline" | "markdown_inline" => Ok(tree_sitter_md::INLINE_LANGUAGE.into()),
        "python" | "py" => Ok(tree_sitter_python::LANGUAGE.into()),
        other => Err(format!("unknown language: {other}")),
    }
}

// ---------------------------------------------------------------------- queries

fn run_query(src: &[u8], query_src: &str, lang_name: &str) -> Result<Value, String> {
    let lang = language(lang_name)?;
    let query = Query::new(&lang, query_src).map_err(|e| format!("query error: {e}"))?;
    let mut parser = tree_sitter::Parser::new();
    parser
        .set_language(&lang)
        .map_err(|e| format!("set_language: {e}"))?;
    let tree = parser
        .parse(src, None)
        .ok_or_else(|| "parse returned None".to_string())?;
    let names = query.capture_names();
    let mut cursor = QueryCursor::new();
    let mut matches = cursor.matches(&query, tree.root_node(), src);
    let mut out = Vec::new();
    while let Some(m) = matches.next() {
        let mut caps = Vec::new();
        for c in m.captures {
            let node = c.node;
            let name = names
                .get(c.index as usize)
                .copied()
                .unwrap_or("")
                .to_string();
            caps.push(json!({
                "name": name,
                "type": node.kind(),
                "start_byte": node.start_byte(),
                "end_byte": node.end_byte(),
                "start_point": [node.start_position().row, node.start_position().column],
                "end_point": [node.end_position().row, node.end_position().column],
                "text": node.utf8_text(src).unwrap_or(""),
            }));
        }
        out.push(Value::Array(caps));
    }
    Ok(Value::Array(out))
}

// --------------------------------------------------------------------- dispatch

fn arg_str(args: &[Value], i: usize) -> Result<String, String> {
    args.get(i)
        .and_then(|v| v.as_str())
        .map(|s| s.to_string())
        .ok_or_else(|| format!("argument {i} must be a string"))
}

fn dispatch(var: &str, args: &[Value]) -> Result<Value, String> {
    let name = var.rsplit('/').next().unwrap_or(var);
    match name {
        "version" => Ok(json!({
            "pod": "pod-kimun-treesitter",
            "version": VERSION,
            "tree_sitter": "0.26",
            "languages": ["markdown", "markdown-inline", "python"],
        })),
        "parse" => {
            let text = arg_str(args, 0)?;
            let mut p = MarkdownParser::default();
            let tree = p
                .parse(text.as_bytes(), None)
                .ok_or_else(|| "parse returned None".to_string())?;
            let mut cursor = tree.walk();
            Ok(json!([walk_md(&mut cursor, text.as_bytes(), 0, true)]))
        }
        "parse-block" => {
            let text = arg_str(args, 0)?;
            let lang = tree_sitter_md::LANGUAGE.into();
            let mut p = tree_sitter::Parser::new();
            p.set_language(&lang).map_err(|e| e.to_string())?;
            let tree = p
                .parse(text.as_bytes(), None)
                .ok_or_else(|| "parse returned None".to_string())?;
            Ok(tree_json(&tree, text.as_bytes()))
        }
        "blocks" => {
            let text = arg_str(args, 0)?;
            let lang = tree_sitter_md::LANGUAGE.into();
            let mut p = tree_sitter::Parser::new();
            p.set_language(&lang).map_err(|e| e.to_string())?;
            let tree = p
                .parse(text.as_bytes(), None)
                .ok_or_else(|| "parse returned None".to_string())?;
            Ok(Value::Array(flat_blocks(&tree.root_node())))
        }
        "parse-inline" => {
            let text = arg_str(args, 0)?;
            let lang = tree_sitter_md::INLINE_LANGUAGE.into();
            let mut p = tree_sitter::Parser::new();
            p.set_language(&lang).map_err(|e| e.to_string())?;
            let tree = p
                .parse(text.as_bytes(), None)
                .ok_or_else(|| "parse returned None".to_string())?;
            Ok(tree_json(&tree, text.as_bytes()))
        }
        "parse-lang" => {
            let text = arg_str(args, 0)?;
            let lang_name = arg_str(args, 1)?;
            let lang = language(&lang_name)?;
            let mut p = tree_sitter::Parser::new();
            p.set_language(&lang).map_err(|e| e.to_string())?;
            let tree = p
                .parse(text.as_bytes(), None)
                .ok_or_else(|| "parse returned None".to_string())?;
            Ok(tree_json(&tree, text.as_bytes()))
        }
        "bench" => {
            use std::time::Instant;
            let text = arg_str(args, 0)?;
            let iters: usize = args
                .get(1)
                .and_then(|v| v.as_u64())
                .unwrap_or(10) as usize;
            let lang: Language = tree_sitter_md::LANGUAGE.into();
            let mut p = tree_sitter::Parser::new();
            p.set_language(&lang).map_err(|e| e.to_string())?;
            let src = text.as_bytes();
            // parse only
            p.parse(src, None).ok_or("parse failed")?;
            let t0 = Instant::now();
            let mut nodes = 0usize;
            for _ in 0..iters {
                let tree = p.parse(src, None).ok_or("parse failed")?;
                nodes = tree.root_node().descendant_count();
            }
            let parse_ms = t0.elapsed().as_secs_f64() * 1000.0 / iters as f64;
            // parse + full CST serialization
            let tree = p.parse(src, None).ok_or("parse failed")?;
            let t1 = Instant::now();
            for _ in 0..iters {
                let _ = tree_json(&tree, src);
            }
            let cst_ms = t1.elapsed().as_secs_f64() * 1000.0 / iters as f64;
            // flat blocks
            let t2 = Instant::now();
            let mut nb = 0usize;
            for _ in 0..iters {
                nb = flat_blocks(&tree.root_node()).len();
            }
            let blocks_ms = t2.elapsed().as_secs_f64() * 1000.0 / iters as f64;
            Ok(json!({
                "parse_ms": parse_ms,
                "parse_plus_cst_ms": cst_ms,
                "parse_plus_blocks_ms": blocks_ms,
                "nodes": nodes,
                "blocks": nb,
                "iters": iters,
            }))
        }
        "query" => {
            let text = arg_str(args, 0)?;
            let query_src = arg_str(args, 1)?;
            let lang_name = args
                .get(2)
                .and_then(|v| v.as_str())
                .unwrap_or("markdown");
            run_query(text.as_bytes(), &query_src, lang_name)
        }
        other => Err(format!("unknown var: {other}")),
    }
}

fn describe() -> B {
    let mut ns = BTreeMap::new();
    ns.insert(b"name".to_vec(), B::str("pod.kimun.treesitter"));
    let vars: Vec<B> = [
        "parse",
        "parse-block",
        "blocks",
        "bench",
        "parse-inline",
        "parse-lang",
        "query",
        "version",
    ]
    .iter()
    .map(|v| {
        let mut m = BTreeMap::new();
        m.insert(b"name".to_vec(), B::str(v));
        B::Dict(m)
    })
    .collect();
    ns.insert(b"vars".to_vec(), B::List(vars));

    let mut ops_inner = BTreeMap::new();
    ops_inner.insert(b"shutdown".to_vec(), B::Dict(BTreeMap::new()));

    let mut resp = BTreeMap::new();
    resp.insert(b"format".to_vec(), B::str("json"));
    resp.insert(b"namespaces".to_vec(), B::List(vec![B::Dict(ns)]));
    resp.insert(b"ops".to_vec(), B::Dict(ops_inner));
    B::Dict(resp)
}

fn respond(out: &mut impl Write, msg: B) -> io::Result<()> {
    bencode::write_value(out, &msg)?;
    out.flush()
}

fn invoke_reply(
    out: &mut impl Write,
    id: &B,
    result: Result<Value, String>,
) -> io::Result<()> {
    let mut m = BTreeMap::new();
    if let B::Bytes(b) = id {
        m.insert(b"id".to_vec(), B::Bytes(b.clone()));
    }
    match result {
        Ok(v) => {
            m.insert(
                b"value".to_vec(),
                B::Bytes(serde_json::to_vec(&v).unwrap_or_else(|_| b"null".to_vec())),
            );
            m.insert(b"status".to_vec(), B::List(vec![B::str("done")]));
        }
        Err(e) => {
            m.insert(b"ex-message".to_vec(), B::Bytes(e.into_bytes()));
            m.insert(b"ex-data".to_vec(), B::Bytes(b"{}".to_vec()));
            m.insert(b"status".to_vec(), B::List(vec![B::str("done"), B::str("error")]));
        }
    }
    respond(out, B::Dict(m))
}

// Flat top-level block list: the block grammar's named blocks with `section`
// wrappers removed, without children or text. This is the minimal surface a
// consumer needs to mirror a line/byte block segmentation.
fn flat_blocks(node: &Node) -> Vec<Value> {
    let mut out = Vec::new();
    fn walk(node: &Node, out: &mut Vec<Value>) {
        let n = node.child_count();
        for i in 0..n {
            if let Some(c) = node.child(i as u32) {
                if c.is_named() {
                    if c.kind() == "section" {
                        walk(&c, out);
                    } else {
                        out.push(json!({
                            "type": c.kind(),
                            "start_byte": c.start_byte(),
                            "end_byte": c.end_byte(),
                            "start_point": [c.start_position().row, c.start_position().column],
                            "end_point": [c.end_position().row, c.end_position().column],
                        }));
                    }
                }
            }
        }
    }
    walk(node, &mut out);
    out
}

fn main() {
    let stdin = io::stdin();
    let stdout = io::stdout();
    let mut out = BufWriter::new(stdout.lock());
    let mut reader = io::BufReader::new(stdin.lock());

    loop {
        let msg = match bencode::read_message(&mut reader) {
            Ok(Some(m)) => m,
            Ok(None) => break,
            Err(e) => {
                eprintln!("pod-kimun-treesitter: read error: {e}");
                break;
            }
        };
        let op = msg.get("op").and_then(B::as_str).map(str::to_string);
        match op.as_deref() {
            Some("describe") => {
                if let Err(e) = respond(&mut out, describe()) {
                    eprintln!("pod-kimun-treesitter: write error: {e}");
                    break;
                }
            }
            Some("shutdown") => break,
            Some("invoke") | None => {
                let id = msg.get("id").cloned().unwrap_or(B::Int(0));
                let var = msg
                    .get("var")
                    .and_then(B::as_str)
                    .unwrap_or("")
                    .to_string();
                let args: Vec<Value> = msg
                    .get("args")
                    .and_then(B::as_str)
                    .and_then(|s| serde_json::from_str(s).ok())
                    .unwrap_or_default();
                let result = dispatch(&var, &args);
                if let Err(e) = invoke_reply(&mut out, &id, result) {
                    eprintln!("pod-kimun-treesitter: write error: {e}");
                    break;
                }
            }
            _ => {}
        }
    }
}
