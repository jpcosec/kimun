(ns kimun.surface.models.compile
  "The v1 `:template` as compilable sugar (docs/v2/16 §3.2): a derived spec 16
   view of a v1 descriptor — its recipe as the `:anchoring :selector` and its
   template as the `:markdown/v1` generation projection. A view, never persisted and never merged into
   the stored descriptor (that would change its address and supersede the
   model). Pure: it does not run the selector."
  (:require [kimun.surface.models.descriptor :as descriptor]
            [kimun.surface.models.recipe :as recipe]))

(defn template->spec16
  "The spec 16 view of v1 descriptor `d`: `{:model/name :model/version :base
   :document-role :source :substrate :anchoring {:fields :selector}
   :generation {:projections {:markdown/v1 …}}}`. The selector is the recipe of
   the template, step for step (docs/v2/16 §3.2); the projection keeps the whole
   template text, so `render` markers live there and not in the selector (they
   carry no field in the anchoring direction). Deterministic; `d` is not
   modified. Raises `:models/invalid` for an invalid v1 descriptor (including
   `py•` markers)."
  [d]
  (let [d (descriptor/validate! d)]
    {:model/name (:model/name d)
     :model/version (get d :model/version 1)
     :base (vec (:base d))
     :document-role :source
     :substrate {:parser :sldb-md/blocks}
     :anchoring {:fields (vec (:fields d))
                 :selector (vec (recipe/recipes (:template d)))}
     :generation {:projections {:markdown/v1 {:format :markdown
                                              :template (:template d)}}}}))
