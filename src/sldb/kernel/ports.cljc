(ns sldb.kernel.ports
  "Ports of the kernel (docs/v2/03 §1): the capabilities the kernel needs from
   its host, declared as protocols and received as a `host` value
   `{:hasher Hasher :text TextNormalizer :ids IdMinter}`. The kernel never
   requires `sldb.host.*`; adapters implement these and `sldb.host.default`
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

(defn host?
  "True when `host` carries the four ports."
  [host]
  (and (map? host)
       (satisfies? Hasher (:hasher host))
       (satisfies? TextNormalizer (:text host))
       (satisfies? IdMinter (:ids host))
       (satisfies? TextSegmenter (:segmenter host))))
