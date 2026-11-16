(ns kimun.surface.models.extract
  "Markdown → payload (docs/v2/07 §6): aligns the blocks of a document with
   the recipe of the model's template and captures each field. Pure, no store.
   The step interpreter lives in `kimun.surface.models.selector`."
  (:require [kimun.surface.models.descriptor :as descriptor]
            [kimun.surface.models.recipe :as recipe]
            [kimun.surface.models.selector :as selector]))

(defn extract-raw
  "The untyped values (`{field value}`, string keys) that the template text
   `template` captures from Markdown `text`."
  [template text]
  (selector/extract-selector (recipe/recipes template) text))

(defn extract
  "The typed payload of `descriptor` extracted from Markdown `text`: declared
   fields with defaults and coercion applied (`descriptor/finalize`). Raises
   `:validate/missing` when a required field is absent."
  [descriptor text]
  (descriptor/finalize descriptor (extract-raw (:template descriptor) text)))
