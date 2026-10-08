//! Minimal streaming bencode for the babashka pod protocol.
//! Only needs integers, byte strings, lists and dicts (string keys), which is
//! all the pod protocol uses on the wire.

use std::collections::BTreeMap;
use std::io::{self, BufRead, Read, Write};

#[derive(Debug, Clone, PartialEq)]
pub enum B {
    Int(i64),
    Bytes(Vec<u8>),
    List(Vec<B>),
    Dict(BTreeMap<Vec<u8>, B>),
}

impl B {
    pub fn str(s: &str) -> B {
        B::Bytes(s.as_bytes().to_vec())
    }
    pub fn get<'a>(&'a self, key: &str) -> Option<&'a B> {
        match self {
            B::Dict(m) => m.get(key.as_bytes()),
            _ => None,
        }
    }
    pub fn as_str(&self) -> Option<&str> {
        match self {
            B::Bytes(b) => std::str::from_utf8(b).ok(),
            _ => None,
        }
    }
}

fn read_byte<R: Read>(r: &mut R) -> io::Result<Option<u8>> {
    let mut b = [0u8; 1];
    match r.read(&mut b)? {
        0 => Ok(None),
        _ => Ok(Some(b[0])),
    }
}

fn read_exact_vec<R: Read>(r: &mut R, n: usize) -> io::Result<Vec<u8>> {
    let mut v = vec![0u8; n];
    r.read_exact(&mut v)?;
    Ok(v)
}

/// Read exactly one bencode value; `Ok(None)` on clean EOF before any byte.
pub fn read_value<R: Read>(r: &mut R) -> io::Result<Option<B>> {
    let first = match read_byte(r)? {
        Some(b) => b,
        None => return Ok(None),
    };
    read_after(r, first).map(Some)
}

fn read_until<R: Read>(r: &mut R, stop: u8) -> io::Result<Vec<u8>> {
    let mut out = Vec::new();
    loop {
        match read_byte(r)? {
            Some(b) if b == stop => return Ok(out),
            Some(b) => out.push(b),
            None => {
                return Err(io::Error::new(
                    io::ErrorKind::UnexpectedEof,
                    "bencode: eof inside value",
                ))
            }
        }
    }
}

fn read_after<R: Read>(r: &mut R, first: u8) -> io::Result<B> {
    match first {
        b'i' => {
            let digits = read_until(r, b'e')?;
            let s = std::str::from_utf8(&digits)
                .map_err(|_| io::Error::new(io::ErrorKind::InvalidData, "bencode: bad int"))?;
            Ok(B::Int(s.parse::<i64>().map_err(|_| {
                io::Error::new(io::ErrorKind::InvalidData, "bencode: bad int")
            })?))
        }
        b'l' => {
            let mut items = Vec::new();
            loop {
                match read_byte(r)? {
                    Some(b'e') => return Ok(B::List(items)),
                    Some(b) => items.push(read_after(r, b)?),
                    None => {
                        return Err(io::Error::new(
                            io::ErrorKind::UnexpectedEof,
                            "bencode: eof in list",
                        ))
                    }
                }
            }
        }
        b'd' => {
            let mut map = BTreeMap::new();
            loop {
                match read_byte(r)? {
                    Some(b'e') => return Ok(B::Dict(map)),
                    Some(b) if b.is_ascii_digit() => {
                        let mut digits = vec![b];
                        digits.append(&mut read_until(r, b':')?);
                        let n = std::str::from_utf8(&digits)
                            .ok()
                            .and_then(|s| s.parse::<usize>().ok())
                            .ok_or_else(|| {
                                io::Error::new(io::ErrorKind::InvalidData, "bencode: bad key length")
                            })?;
                        let key = read_exact_vec(r, n)?;
                        let val = read_value(r)?.ok_or_else(|| {
                            io::Error::new(io::ErrorKind::UnexpectedEof, "bencode: eof in dict")
                        })?;
                        map.insert(key, val);
                    }
                    Some(_) => {
                        return Err(io::Error::new(
                            io::ErrorKind::InvalidData,
                            "bencode: dict key must be a byte string",
                        ))
                    }
                    None => {
                        return Err(io::Error::new(
                            io::ErrorKind::UnexpectedEof,
                            "bencode: eof in dict",
                        ))
                    }
                }
            }
        }
        b'0'..=b'9' => {
            // re-read the whole length: reconstruct from `first`
            let mut digits = vec![first];
            let mut rest = read_until(r, b':')?;
            digits.append(&mut rest);
            let n = std::str::from_utf8(&digits)
                .ok()
                .and_then(|s| s.parse::<usize>().ok())
                .ok_or_else(|| io::Error::new(io::ErrorKind::InvalidData, "bencode: bad length"))?;
            Ok(B::Bytes(read_exact_vec(r, n)?))
        }
        _ => Err(io::Error::new(
            io::ErrorKind::InvalidData,
            "bencode: unknown tag",
        )),
    }
}

pub fn write_value<W: Write>(w: &mut W, v: &B) -> io::Result<()> {
    match v {
        B::Int(i) => write!(w, "i{}e", i),
        B::Bytes(b) => {
            write!(w, "{}:", b.len())?;
            w.write_all(b)
        }
        B::List(xs) => {
            w.write_all(b"l")?;
            for x in xs {
                write_value(w, x)?;
            }
            w.write_all(b"e")
        }
        B::Dict(m) => {
            w.write_all(b"d")?;
            for (k, val) in m {
                write!(w, "{}:", k.len())?;
                w.write_all(k)?;
                write_value(w, val)?;
            }
            w.write_all(b"e")
        }
    }
}

pub fn read_message<R: BufRead>(r: &mut R) -> io::Result<Option<B>> {
    read_value(r)
}
