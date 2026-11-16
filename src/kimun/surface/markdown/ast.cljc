(ns kimun.surface.markdown.ast
  "Markdown text → neutral structural AST of the SLDB-MD profile (docs/v2/04
   §3.2, §4, §7): the choice between the in-process parser
   (`kimun.surface.markdown.profile/parse`) and the pod view
   (`kimun.surface.markdown.cst-ast`). Total: anything outside the profile
   becomes an :opaque block."
  (:require [kimun.host.treesitter :as ts]
            [kimun.surface.markdown.profile :as profile]
            #?(:clj [kimun.surface.markdown.cst-ast :as cst-ast])))

(defn parse-native
  "The in-process parser of SLDB-MD (`kimun.surface.markdown.profile/parse`): the
   oracle of the pod view (R2 §5 fase 2) and the fallback when the pod is
   absent."
  [host s]
  (profile/parse host s))

(def ^:dynamic *parser*
  "Overrides `KIMUN_PARSER` for the dynamic scope (tests, embedding hosts):
   \"native\" or \"treesitter\"; nil reads the environment."
  nil)

(defn requested-parser
  "The parser that was asked for: `*parser*`, else `KIMUN_PARSER`, else
   \"native\". It may not be the one in force (`effective-parser`)."
  []
  #?(:clj (or *parser* (System/getenv "KIMUN_PARSER") "native")
     :cljs "native"))

(defn effective-parser
  "The parser `parse` uses right now: \"treesitter\" when it is selected
   (`*parser*`, else `KIMUN_PARSER`) and the pod is available, else \"native\".
   The parse contract of a document records it (`kimun.surface.parse-contract`)."
  []
  #?(:clj (if (and (= "treesitter" (requested-parser)) (ts/available?))
            "treesitter"
            "native")
     :cljs "native"))

(defn parse
  "Markdown text → neutral AST `{:type :document :attrs {} :children [...]}`.
   Defaults to the in-process parser (`parse-native`). `KIMUN_PARSER=treesitter`
   selects the pod view (`kimun.surface.markdown.cst-ast`): it matches the
   in-process parser on the parity corpus and the desk (1099/1099) and on the
   free-form samples of `runs/subagents/2026-11-15-S1/` (0 divergences), because
   it adopts a pod block only where the profile segments it the same way. It
   stays behind the flag for the pod blocks it still reconciles (R2 §5 fase 2;
   see `cst-ast-parity-test`)."
  [host s]
  #?(:clj (if (= "treesitter" (effective-parser))
            (cst-ast/parse host s)
            (parse-native host s))
     :cljs (parse-native host s)))
