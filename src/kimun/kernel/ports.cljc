(ns kimun.kernel.ports
  "Ports of the kernel (docs/v2/03 §1): the capabilities the kernel needs from
   its host, declared as protocols and received as a `host` value
   `{:hasher Hasher :text TextNormalizer :ids IdMinter}`. The kernel never
   requires `kimun.host.*`; adapters implement these and `kimun.host.default`
   composes the standard host.")

(defprotocol Hasher
  "Content hashing (docs/v2/02 §2.1, §8.1)."
  (algorithm [this] "Keyword naming the algorithm recorded in the store descriptor, e.g. :sha-256.")
  (digest-str [this s] "Lower-case hex digest of the UTF-8 encoding of string `s`."))

(defprotocol TextNormalizer
  "Unicode normalization (docs/v2/02 §2.1 step 1)."
  (nfc [this s] "NFC form of string `s`."))

(defprotocol IdMinter
  "Nominal ids for trees (docs/v2/02 §3.1)."
  (ulid [this] "A fresh 26-character Crockford-base32 ULID."))

(defprotocol TextSegmenter
  "Segmentation of text into the deterministic UAX #29 layers that stand-off
   addresses are counted in (docs/v2/02 §4, docs/v2/04 §2): graphemes are the
   unit of every offset; words and sentences are ranges over those units."
  (graphemes [this s] "Vector of grapheme-cluster strings of `s`, in order; (apply str v) == s.")
  (words [this s] "Vector of [start end] grapheme-offset ranges of the UAX #29 word segments of `s`, in order and covering it.")
  (sentences [this s] "Vector of [start end] grapheme-offset ranges of the UAX #29 sentence segments of `s`, in order and covering it."))

;; Optional port: a host without it gets eager plain maps (the kernel falls back).
(defprotocol LazyMaps
  "Map values computed on demand, so a store value can describe a whole store
   without loading it."
  (lazy-map [this lookup ks overlay]
    "A persistent map (map?, get, contains?, assoc, dissoc, merge, update, seq,
     count, keys, vals, = all work) whose entries are computed on demand:
     `(lookup k)` -> the value, or nil when `k` is absent; called at most once
     per key for this map and every map derived from it (the adapter memoises,
     per process, for as long as such a map is alive). `(ks)` -> every key
     `lookup` answers; called only by seq/count/keys/vals/=. `overlay` is a
     plain map of entries that win over `lookup`; assoc/merge add to it and
     dissoc hides a key, none of them calling `lookup` or `ks`."))

(defn lazy-maps
  "The LazyMaps of a host value, or nil when the host has none (optional port)."
  [host] (:lazy-maps host))

(defn lazy-maps?
  "True when `host` carries the optional LazyMaps port."
  [host] (some? (lazy-maps host)))

(defn segmenter
  "The TextSegmenter of a host value."
  [host] (:segmenter host))

(defn hasher
  "The Hasher of a host value."
  [host] (:hasher host))

(defn text
  "The TextNormalizer of a host value."
  [host] (:text host))

(defn ids
  "The IdMinter of a host value."
  [host] (:ids host))
