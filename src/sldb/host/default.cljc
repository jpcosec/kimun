(ns sldb.host.default
  "The standard host for Babashka/JVM (and, once host parity lands, Node):
   composes the port implementations of `sldb.host.*` into the `host` value
   the kernel receives (docs/v2/03 §1)."
  (:require [sldb.host.hash :as hash]
            [sldb.host.text :as text]
            [sldb.host.ulid :as ulid]))

(def host
  "`{:hasher :text :ids :segmenter}` — SHA-256, NFC and graphemes via the platform, ULIDs."
  {:hasher    hash/sha-256
   :text      text/normalizer
   :ids       ulid/minter
   :segmenter text/segmenter})

(defn layer-cache
  "A fresh memo for `sldb.kernel.standoff/layers`: an atom holding a map keyed
   by leaf id. The host composes the memo and hands it to the kernel as an
   argument, so the kernel keeps no global mutable state (docs/v2/03 §1)."
  [] (atom {}))
