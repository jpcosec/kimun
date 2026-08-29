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
  "True when `host` carries the three ports."
  [host]
  (and (map? host)
       (satisfies? Hasher (:hasher host))
       (satisfies? TextNormalizer (:text host))
       (satisfies? IdMinter (:ids host))))
