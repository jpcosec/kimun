(ns kimun.surface.models.extract
  "Markdown → payload (docs/v2/07 §6): aligns the blocks of a document with
   the recipe of the model's template and captures each field. Pure, no store.
   The alignment reproduces sldb v1's `DataExtractor`; the step interpreter
   lives in `kimun.surface.models.selector`."
  (:require [kimun.surface.models.descriptor :as descriptor]
            [kimun.surface.models.recipe :as recipe]
            [kimun.surface.models.selector :as selector]))

(defn extract-raw
  "The untyped values (`{field value}`, string keys) that the template text
   `template` captures from Markdown `text`. `mode` is a selector mode
   (`selector/modes`); the default is `:legacy-v1-template`."
  ([template text] (extract-raw template text :legacy-v1-template))
  ([template text mode] (selector/extract-selector (recipe/recipes template) text mode)))

(defn extract
  "The typed payload of `descriptor` extracted from Markdown `text`: declared
   fields with defaults and coercion applied (`descriptor/finalize`). Raises
   `:validate/missing` when a required field is absent."
  ([descriptor text] (extract descriptor text :legacy-v1-template))
  ([descriptor text mode]
   (descriptor/finalize descriptor (extract-raw (:template descriptor) text mode))))
