(ns kimun.surface.links
  "Document links, the pure core: parse `[[links]]`, resolve them through a
   caller-supplied lookup, recover a document's links and compose its
   transclusions (docs/v2 parity with sldb v1 `sldb.links`). The store and the
   file system stay with the caller, who passes `lookup` and `content`.
   Lookup: `(fn [{:keys [target from]}]) -> [{:path p :source :store|:path} …]`.
   Content: `(fn [path]) -> markdown text`."
  (:require [kimun.surface.links.composer :as composer]
            [kimun.surface.links.parser :as parser]
            [kimun.surface.links.recovery :as recovery]))

(defn parse-links
  "See `kimun.surface.links.parser/parse-links`."
  ([markdown] (parser/parse-links markdown))
  ([markdown predicate-types] (parser/parse-links markdown predicate-types)))

(defn recover-links
  "See `kimun.surface.links.recovery/recover-links`."
  [path opts]
  (recovery/recover-links path opts))

(defn compose-document
  "See `kimun.surface.links.composer/compose-document`."
  [path opts]
  (composer/compose-document path opts))
