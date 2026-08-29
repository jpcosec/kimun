(ns sldb.host.default
  "The standard host for Babashka/JVM (and, once host parity lands, Node):
   composes the port implementations of `sldb.host.*` into the `host` value
   the kernel receives (docs/v2/03 §1)."
  (:require [sldb.host.hash :as hash]
            [sldb.host.text :as text]
            [sldb.host.ulid :as ulid]))

(def host
  "`{:hasher :text :ids}` — SHA-256, NFC via the platform normalizer, ULIDs."
  {:hasher hash/sha-256
   :text   text/normalizer
   :ids    ulid/minter})
