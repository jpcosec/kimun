(ns kimun.surface.adapters-test
  "Pure planner of the surface adapters: contract validation, v1-exact payload
   fields (goldens computed by v1's own Python over the real adapters' output),
   merge, sync plan and drift report. The store/CLI halves of the v1 selfdoc
   tests live in kimun.cli.adapters-test."
  (:require [clojure.edn :as edn]
            [clojure.string :as str]
            [clojure.test :refer [deftest is testing]]
            [kimun.surface.adapters :as a]
            [kimun.surface.docs :as kdocs]
            [kimun.surface.models.store :as mstore]
            [kimun.host.default :as host]
            [kimun.kernel.ports :as ports]
            [kimun.kernel.revision :as revision]))

(defn- sha256 [s] (ports/digest-str (ports/hasher host/host) s))

;; ---- goldens: generated with v1 (sldb.selfdoc) and the built-in adapters by
;; ---- /tmp/kimun-adapters/C/scripts/gen.py; do not edit by hand.

(def symbols-output
  "python-symbols adapter output over a 1-module tree (generated, real adapter)."
  {"adapter" "python-symbols" "language" "python" "profile" "symbols" "protocol" 1
   "facts"
  [{"attrs" {"imports" ["." "os,sys" "zlib"] "module" "pkg.mod" "native_kind" "Module"} "doc" "Módulo." "id" "pkg.mod" "kind" "module" "name" "mod" "path" "pkg/mod.py" "qualified_name" "pkg.mod" "relations" [{"target" "." "type" "imports"} {"external" true "target" "os" "type" "imports"} {"target" "os,sys" "type" "imports"} {"external" true "target" "pkg.x" "type" "imports"} {"external" true "target" "sys" "type" "imports"} {"target" "zlib" "type" "imports"} {"external" true "target" "zlib" "type" "imports"}] "signature" nil "source_hash" "sha256:31a6c70fe5615dd178027d2c7fff976f1c0a7e5e6c38bc525f1d0ef02adeebf9" "span" {"end_line" 14 "start_line" 1} "synopsis" nil}
  {"attrs" {"module" "pkg.mod" "native_kind" "FunctionDef"} "doc" nil "id" "pkg.mod.sin_doc" "kind" "function" "name" "sin_doc" "path" "pkg/mod.py" "qualified_name" "sin_doc" "relations" [{"target" "pkg.mod" "type" "contained-in"}] "signature" "sin_doc(a, b=('é', 1))" "source_hash" "sha256:31a6c70fe5615dd178027d2c7fff976f1c0a7e5e6c38bc525f1d0ef02adeebf9" "span" {"end_line" 14 "start_line" 14} "synopsis" nil}
  {"attrs" {"module" "pkg.mod" "native_kind" "ClassDef"} "doc" "Doc con emoji 😀, \"comillas\" y \\ barra." "id" "pkg.mod.Ñandú" "kind" "class" "name" "Ñandú" "path" "pkg/mod.py" "qualified_name" "Ñandú" "relations" [{"target" "pkg.mod" "type" "contained-in"}] "signature" nil "source_hash" "sha256:31a6c70fe5615dd178027d2c7fff976f1c0a7e5e6c38bc525f1d0ef02adeebf9" "span" {"end_line" 11 "start_line" 7} "synopsis" nil}
  {"attrs" {"module" "pkg.mod" "native_kind" "AsyncFunctionDef"} "doc" nil "id" "pkg.mod.Ñandú.corre" "kind" "method" "name" "corre" "path" "pkg/mod.py" "qualified_name" "Ñandú.corre" "relations" [{"target" "pkg.mod.Ñandú" "type" "contained-in"}] "signature" "corre(self, *args, k: int=3)" "source_hash" "sha256:31a6c70fe5615dd178027d2c7fff976f1c0a7e5e6c38bc525f1d0ef02adeebf9" "span" {"end_line" 11 "start_line" 10} "synopsis" nil}]})

(def v1-symbol-fields
  "v1 python_materialize._fields over the same tree (system sldb), v1 scan order."
  [{"id" "python-sldb-pkg-mod-and" "system" "sldb" "module" "pkg.mod" "qualname" "Ñandú" "kind" "ClassDef" "source_path" "pkg/mod.py" "source_span" "7:11" "source_sha256" "31a6c70fe5615dd178027d2c7fff976f1c0a7e5e6c38bc525f1d0ef02adeebf9" "signature" "Not applicable." "docstring" "Doc con emoji 😀, \"comillas\" y \\ barra." "imports" "[\".\", \"os,sys\", \"zlib\"]" "architecture_spec" "Not declared." "provenance" "python-ast:python:pkg.mod:Ñandú; contract-sha256:b7a7742268db0885c91c2461d9e967bf1c977fff1b1ddbc790063ba4d2f6b7cd"}
  {"id" "python-sldb-pkg-mod-and-corre" "system" "sldb" "module" "pkg.mod" "qualname" "Ñandú.corre" "kind" "AsyncFunctionDef" "source_path" "pkg/mod.py" "source_span" "10:11" "source_sha256" "31a6c70fe5615dd178027d2c7fff976f1c0a7e5e6c38bc525f1d0ef02adeebf9" "signature" "corre(self, *args, k: int=3)" "docstring" "Not documented." "imports" "[\".\", \"os,sys\", \"zlib\"]" "architecture_spec" "Not declared." "provenance" "python-ast:python:pkg.mod:Ñandú.corre; contract-sha256:63be34f3c177ba64235ec98f4709d7d70b64cba8c5e7972f7243fad613f90c2b"}
  {"id" "python-sldb-pkg-mod-sin_doc" "system" "sldb" "module" "pkg.mod" "qualname" "sin_doc" "kind" "FunctionDef" "source_path" "pkg/mod.py" "source_span" "14:14" "source_sha256" "31a6c70fe5615dd178027d2c7fff976f1c0a7e5e6c38bc525f1d0ef02adeebf9" "signature" "sin_doc(a, b=('é', 1))" "docstring" "Not documented." "imports" "[\".\", \"os,sys\", \"zlib\"]" "architecture_spec" "Not declared." "provenance" "python-ast:python:pkg.mod:sin_doc; contract-sha256:e7e9227e37f3a8da7c59651221770cc28d8227878179236631e3949f590f7867"}])

(def cli-output
  "argparse-cli adapter output for a small parser (generated, real adapter)."
  {"adapter" "argparse-cli" "language" "python" "profile" "cli" "protocol" 1
   "facts"
  [{"attrs" {"arguments" [] "exclusive_groups" [] "hidden" false "path" ["db"]} "doc" nil "id" "db" "kind" "group" "name" "db" "path" nil "qualified_name" "db" "relations" [] "signature" "usage: widget db [-h] {migrate} ..." "source_hash" nil "span" nil "synopsis" "Database"}
  {"attrs" {"arguments" [] "exclusive_groups" [] "hidden" false "path" ["db" "migrate"]} "doc" nil "id" "db migrate" "kind" "command" "name" "migrate" "path" nil "qualified_name" "db migrate" "relations" [] "signature" "usage: widget db migrate [-h]" "source_hash" nil "span" nil "synopsis" nil}
  {"attrs" {"arguments" [{"action" "argparse._StoreAction" "choices" nil "const" "null" "default" "5" "help" "Límite 😀" "names" ["--limit"] "nargs" nil "required" false "value_type" "builtins.int"} {"action" "argparse._StoreTrueAction" "choices" nil "const" "true" "default" "false" "help" "" "names" ["--fast"] "nargs" 0 "required" false "value_type" nil}] "exclusive_groups" [{"arguments" ["fast"] "required" false}] "hidden" false "path" ["run"]} "doc" "Runs the widget." "id" "run" "kind" "command" "name" "run" "path" nil "qualified_name" "run" "relations" [] "signature" "usage: widget run [-h] [--limit LIMIT] [--fast]" "source_hash" nil "span" nil "synopsis" "Run it"}
  {"attrs" {"action" "argparse._StoreTrueAction" "choices" nil "const" "true" "default" "false" "dest" "fast" "help" "" "names" ["--fast"] "nargs" 0 "required" false "value_type" nil} "doc" nil "id" "run#fast" "kind" "argument" "name" "--fast" "path" nil "qualified_name" "run#fast" "relations" [{"target" "run" "type" "contained-in"}] "signature" nil "source_hash" nil "span" nil "synopsis" nil}
  {"attrs" {"action" "argparse._StoreAction" "choices" nil "const" "null" "default" "5" "dest" "limit" "help" "Límite 😀" "names" ["--limit"] "nargs" nil "required" false "value_type" "builtins.int"} "doc" "Límite 😀" "id" "run#limit" "kind" "argument" "name" "--limit" "path" nil "qualified_name" "run#limit" "relations" [{"target" "run" "type" "contained-in"}] "signature" nil "source_hash" nil "span" nil "synopsis" nil}]})

(def v1-cli-fields
  "v1 command_fields/surface_fields (+ usage default) for the same parser, v1 plan order."
  [{"id" "surface-widget-db" "system" "widget" "surface" "db" "commands" "- widget db migrate" "provenance" "parser:fx:build#db; contract-sha256:698c6bc3fed8bc4b83abac4d93fbc51650e242f23701c157a95ffb4914c9b024"}
  {"id" "cmd-widget-db-migrate" "system" "widget" "command_path" "db migrate" "synopsis" "No synopsis declared." "arguments" "```json\n{\n  \"arguments\": [],\n  \"exclusive_groups\": []\n}\n```" "provenance" "parser:fx:build#db migrate; contract-sha256:718ba80bd4e6b7058c8ee5d00c808be84c68e065bbfcc569fbfaf21d3cc504e9" "usage" "usage: widget db migrate [-h]"}
  {"id" "cmd-widget-run" "system" "widget" "command_path" "run" "synopsis" "Run it" "arguments" "```json\n{\n  \"arguments\": [\n    {\n      \"names\": [\n        \"--limit\"\n      ],\n      \"required\": false,\n      \"default\": \"5\",\n      \"choices\": null,\n      \"nargs\": null,\n      \"value_type\": \"builtins.int\",\n      \"action\": \"argparse._StoreAction\",\n      \"const\": \"null\",\n      \"help\": \"Límite 😀\"\n    },\n    {\n      \"names\": [\n        \"--fast\"\n      ],\n      \"required\": false,\n      \"default\": \"false\",\n      \"choices\": null,\n      \"nargs\": 0,\n      \"value_type\": null,\n      \"action\": \"argparse._StoreTrueAction\",\n      \"const\": \"true\",\n      \"help\": \"\"\n    }\n  ],\n  \"exclusive_groups\": [\n    {\n      \"required\": false,\n      \"arguments\": [\n        \"fast\"\n      ]\n    }\n  ]\n}\n```" "provenance" "parser:fx:build#run; contract-sha256:5c1095b511edb5af4c477fa63a8131f7ae674095855eba7d7edda28b0b744fed" "usage" "usage: widget run [-h] [--limit LIMIT] [--fast]"}
  {"id" "surface-widget-run" "system" "widget" "surface" "run" "commands" "- widget run" "provenance" "parser:fx:build#run; contract-sha256:5c1095b511edb5af4c477fa63a8131f7ae674095855eba7d7edda28b0b744fed"}])

(def v1-cli-fields-default-7
  "Same parser after --limit's default changed from 5 to 7."
  [{"id" "surface-widget-db" "system" "widget" "surface" "db" "commands" "- widget db migrate" "provenance" "parser:fx:build#db; contract-sha256:698c6bc3fed8bc4b83abac4d93fbc51650e242f23701c157a95ffb4914c9b024"}
  {"id" "cmd-widget-db-migrate" "system" "widget" "command_path" "db migrate" "synopsis" "No synopsis declared." "arguments" "```json\n{\n  \"arguments\": [],\n  \"exclusive_groups\": []\n}\n```" "provenance" "parser:fx:build#db migrate; contract-sha256:718ba80bd4e6b7058c8ee5d00c808be84c68e065bbfcc569fbfaf21d3cc504e9" "usage" "usage: widget db migrate [-h]"}
  {"id" "cmd-widget-run" "system" "widget" "command_path" "run" "synopsis" "Run it" "arguments" "```json\n{\n  \"arguments\": [\n    {\n      \"names\": [\n        \"--limit\"\n      ],\n      \"required\": false,\n      \"default\": \"7\",\n      \"choices\": null,\n      \"nargs\": null,\n      \"value_type\": \"builtins.int\",\n      \"action\": \"argparse._StoreAction\",\n      \"const\": \"null\",\n      \"help\": \"Límite 😀\"\n    },\n    {\n      \"names\": [\n        \"--fast\"\n      ],\n      \"required\": false,\n      \"default\": \"false\",\n      \"choices\": null,\n      \"nargs\": 0,\n      \"value_type\": null,\n      \"action\": \"argparse._StoreTrueAction\",\n      \"const\": \"true\",\n      \"help\": \"\"\n    }\n  ],\n  \"exclusive_groups\": [\n    {\n      \"required\": false,\n      \"arguments\": [\n        \"fast\"\n      ]\n    }\n  ]\n}\n```" "provenance" "parser:fx:build#run; contract-sha256:8f9029d00c9116deba02474f996f20d2d18d29c0a7c62a29dc8e74e3cc428f45" "usage" "usage: widget run [-h] [--limit LIMIT] [--fast]"}
  {"id" "surface-widget-run" "system" "widget" "surface" "run" "commands" "- widget run" "provenance" "parser:fx:build#run; contract-sha256:8f9029d00c9116deba02474f996f20d2d18d29c0a7c62a29dc8e74e3cc428f45"}])

;; ---------------------------------------------------------------- helpers

(def sym-opts "Planner options of the symbols goldens." {:system "sldb" :sha256 sha256})
(def cli-opts "Planner options of the cli goldens." {:system "widget" :factory "fx:build" :sha256 sha256})

(defn- plan [output opts] (a/plan-documents output opts))
(defn- docs [output opts] (let [r (plan output opts)] (is (= [] (:problems r))) (:documents r)))
(defn- codes [problems] (set (map :code problems)))
(defn- update-fact [output id f] (update output "facts" (fn [fs] (mapv #(if (= id (% "id")) (f %) %) fs))))
(defn- tracked-of [entries] (mapv #(select-keys % [:name :model :path :payload]) entries))
(defn- synced [output opts] (tracked-of (:create (a/sync-plan (docs output opts) []))))

(def fact "A function fact of `symbols-output`." (first (filter #(= "pkg.mod.sin_doc" (% "id")) (symbols-output "facts"))))

;; --------------------------------------------------------------- validate

(deftest validate-accepts-real-adapter-output
  (is (= [] (a/validate symbols-output)))
  (is (= [] (a/validate cli-output))))

(deftest validate-reports-each-violation
  (let [with (fn [f] (codes (a/validate (assoc symbols-output "facts" [(f fact)]))))]
    (is (= #{:adapters/protocol} (codes (a/validate (assoc symbols-output "protocol" 2)))))
    (is (= #{:adapters/protocol} (codes (a/validate "nope"))))
    (is (= #{:adapters/profile} (codes (a/validate (assoc symbols-output "profile" "x")))))
    (is (= #{:adapters/facts} (codes (a/validate (assoc symbols-output "facts" {})))))
    (is (= #{:adapters/fact} (codes (a/validate (assoc symbols-output "facts" ["x"])))))
    (is (= #{:adapters/missing-key} (with #(dissoc % "doc"))))
    (is (= #{:adapters/empty-id} (with #(assoc % "id" ""))))
    (is (= #{:adapters/fact} (with #(assoc % "kind" nil))))
    (is (= #{:adapters/path} (with #(assoc % "path" "/abs/mod.py"))))
    (is (= #{:adapters/path} (with #(assoc % "path" "pkg/../mod.py"))))
    (is (= #{:adapters/path} (with #(assoc % "path" "pkg\\mod.py"))))
    (is (= #{:adapters/span} (with #(assoc % "span" {"start_line" 3}))))
    (is (= #{:adapters/span} (with #(assoc % "span" {"start_line" 9 "end_line" 3}))))
    (is (= #{:adapters/source-hash} (with #(assoc % "source_hash" "md5:abc"))))
    (is (= #{:adapters/relations} (with #(assoc % "relations" [{"type" "imports"}]))))
    (is (= #{:adapters/attrs} (with #(assoc % "attrs" nil))))
    (is (= [] (a/validate (assoc symbols-output "facts" [(assoc fact "path" nil "span" nil "source_hash" nil)]))))
    (testing "duplicate ids name the fact"
      (is (= [{:code :adapters/duplicate-id :message "duplicate fact id: pkg.mod.sin_doc" :fact "pkg.mod.sin_doc"}]
             (a/validate (assoc symbols-output "facts" [fact fact])))))))

;; ---------------------------------------------------------------- symbols

(deftest symbols-fields-are-v1-exact
  (let [ds (docs symbols-output sym-opts)]
    (testing "every parser-owned field, provenance included, equals v1's _fields"
      (is (= (set v1-symbol-fields) (set (map :fields ds))))
      (is (= 3 (count ds)) "module facts get no document: v1's scanner only emits classes and functions"))
    (testing "planned shape, defaults of a new document"
      (let [d (first (filter #(= "pkg.mod.sin_doc" (:fact %)) ds))]
        (is (= {:name "python-sldb-pkg-mod-sin_doc" :model "PythonSymbolDoc"
                :path "knowledge/symbols/python-sldb-pkg-mod-sin_doc.md" :fact "pkg.mod.sin_doc"
                :defaults {"purpose" "Not documented." "architecture" "Not documented." "tags" []}}
               (dissoc d :fields)))
        (is (= "Not documented." (get-in d [:fields "docstring"])))
        (is (= "FunctionDef" (get-in d [:fields "kind"])))
        (is (= "[\".\", \"os,sys\", \"zlib\"]" (get-in d [:fields "imports"])))
        (is (= "Not declared." (get-in d [:fields "architecture_spec"])))
        (is (not (str/starts-with? (get-in d [:fields "source_sha256"]) "sha256:")))))
    (testing "a class has no signature"
      (is (= "Not applicable." (get-in (first (filter #(= "ClassDef" (get-in % [:fields "kind"])) ds)) [:fields "signature"]))))
    (testing "options: output dir and architecture spec"
      (let [d (first (docs symbols-output (assoc sym-opts :output "docs/k" :architecture-spec "spec.md; sha256=ab")))]
        (is (str/starts-with? (:path d) "docs/k/symbols/"))
        (is (= "spec.md; sha256=ab" (get-in d [:fields "architecture_spec"])))))))

(deftest symbols-imports-fallback-to-relations
  (let [out (update-fact symbols-output "pkg.mod" #(update % "attrs" dissoc "imports"))
        rels (mapv #(% "target") (filter #(= "imports" (% "type")) ((first (out "facts")) "relations")))]
    (is (= (a/py-json rels {:ascii? true}) (get-in (first (docs out sym-opts)) [:fields "imports"])))))

(deftest v1-provenance-is-byte-exact
  (testing "golden from v1 python_materialize._fields over a hand-built PythonSymbol (signature None)"
    (is (= "python-ast:python:m:f; contract-sha256:ab00874f9466cabec174096784ab3846138f1e2ae503343c8d1b1c0c46f2fcf1"
           (a/v1-provenance "python-ast"
                            {"id" "python:m:f" "kind" "FunctionDef" "module" "m" "qualname" "f" "path" "m.py" "line_start" 1
                             "line_end" 2 "signature" nil "docstring" "ñ 😀" "source_sha256" "ab" "imports" ["os"]}
                            sha256))))
  (testing "ensure_ascii escapes (surrogate pairs, lowercase hex), sorted keys, None → null"
    (is (= "{\"a\": [1, null, true], \"b\": \"\\u00f1 \\ud83d\\ude00 \\\" \\\\ \\t\\u007f\"}"
           (a/py-json {"b" "ñ 😀 \" \\ \t\u007f" "a" [1 nil true]} {:ascii? true :sort? true})))
    (is (= "{\"b\": \"ñ 😀\"}" (a/py-json {"b" "ñ 😀"} {:sort? true})))
    (is (= "{\n  \"a\": [],\n  \"b\": [\n    {\n      \"c\": null\n    }\n  ]\n}"
           (a/py-json (array-map "a" [] "b" [{"c" nil}]) {:indent 2})))))

(deftest non-python-language-prefix
  (let [out (assoc symbols-output "adapter" "other-symbols" "language" "javascript")
        d (first (filter #(= "pkg.mod.sin_doc" (:fact %)) (docs out sym-opts)))]
    (is (= "javascript-sldb-pkg-mod-sin_doc" (:name d)))
    (is (= "PythonSymbolDoc" (:model d)))
    (is (str/starts-with? (get-in d [:fields "provenance"]) "other-symbols:javascript:pkg.mod:sin_doc; contract-sha256:"))
    (is (= "go-sldb-pkg-mod-sin_doc" (:name (first (filter #(= "pkg.mod.sin_doc" (:fact %)) (docs out (assoc sym-opts :language "go")))))))
    (testing "python-symbols keeps v1's python-ast tag"
      (is (every? #(str/starts-with? (get-in % [:fields "provenance"]) "python-ast:python:pkg.mod:") (docs symbols-output sym-opts))))))

(deftest symbols-problems
  (testing "invalid system (v1 _validate_system)"
    (doseq [s ["Sldb" "a/b" "" nil "1a"]]
      (is (= {:documents [] :problems [{:code :adapters/invalid-system :fact nil
                                         :message "system must be a lowercase identifier without path separators"}]}
             (plan symbols-output (assoc sym-opts :system s))))))
  (testing "document-id collisions (v1 _validate_paths)"
    (let [twin (assoc fact "id" "pkg.mod.Sin_Doc" "qualified_name" "Sin_Doc")
          r (plan (update symbols-output "facts" conj twin) sym-opts)]
      (is (= [:adapters/collision] (map :code (:problems r))))
      (is (str/includes? (:message (first (:problems r))) "Python symbols collide when converted to document IDs"))
      (is (= ["pkg.mod.sin_doc" "pkg.mod.Sin_Doc"] (:facts (first (:problems r)))))))
  (testing "contract violations, orphan symbols, missing source, missing digest"
    (is (= #{:adapters/protocol} (codes (:problems (plan (assoc symbols-output "protocol" 9) sym-opts)))))
    (is (= #{:adapters/no-module} (codes (:problems (plan (assoc symbols-output "facts" [fact]) sym-opts)))))
    (is (= #{:adapters/missing-source} (codes (:problems (plan (update-fact symbols-output "pkg.mod.sin_doc" #(assoc % "span" nil)) sym-opts)))))
    (is (= #{:adapters/no-digest} (codes (:problems (plan symbols-output {:system "sldb"})))))))

;; v1: tests/selfdoc/test_preflight.py::test_output_outside_store_is_rejected (pure half)
(deftest invalid-output-is-a-problem
  (doseq [o ["../outside" "/abs" "knowledge/" "a/../b"]]
    (is (= #{:adapters/invalid-output} (codes (:problems (plan symbols-output (assoc sym-opts :output o))))))))

;; -------------------------------------------------------------------- cli

(deftest cli-fields-are-v1-exact
  (let [ds (docs cli-output cli-opts)
        mine (map #(cond-> (:fields %) (= "CliCommandDoc" (:model %)) (assoc "usage" (get-in % [:defaults "usage"]))) ds)]
    (is (= v1-cli-fields (vec mine)) "same documents, same order, same bytes as v1 (arguments JSON, provenance, usage)")
    (is (= [["surface-widget-db" "SurfaceDoc" "knowledge/surfaces/surface-widget-db.md" "db"]
            ["cmd-widget-db-migrate" "CliCommandDoc" "knowledge/commands/cmd-widget-db-migrate.md" "db migrate"]
            ["cmd-widget-run" "CliCommandDoc" "knowledge/commands/cmd-widget-run.md" "run"]
            ["surface-widget-run" "SurfaceDoc" "knowledge/surfaces/surface-widget-run.md" "run"]]
           (mapv (juxt :name :model :path :fact) ds)))
    (is (= {"purpose" "Not documented." "how_it_works" "Not documented." "tags" [] "usage" "usage: widget run [-h] [--limit LIMIT] [--fast]"}
           (:defaults (nth ds 2))))
    (is (= {"purpose" "Not documented." "how_it_works" "Not documented." "tags" []} (:defaults (nth ds 0))))
    (testing "a surface without leaf commands, a command without synopsis"
      (let [out (update cli-output "facts" (fn [fs] (vec (remove #(str/starts-with? (% "id") "db ") fs))))
            ds (docs out cli-opts)]
        (is (= "No leaf commands." (get-in (first ds) [:fields "commands"])))
        (is (= "No synopsis declared." (get-in (second (docs cli-output cli-opts)) [:fields "synopsis"])))))))

;; v1: tests/selfdoc/test_preflight.py::test_filename_collision_is_rejected_before_writing
(deftest cli-document-id-collision
  (let [cmd (fn [id kind] {"id" id "kind" kind "name" (last (str/split id #" ")) "qualified_name" id "path" nil "span" nil
                           "source_hash" nil "signature" "usage: x" "synopsis" nil "doc" nil "relations" []
                           "attrs" {"path" (str/split id #" ") "hidden" false "arguments" [] "exclusive_groups" []}})
        out {"protocol" 1 "adapter" "argparse-cli" "profile" "cli" "language" nil
             "facts" [(cmd "a" "group") (cmd "a b" "command") (cmd "a-b" "command")]}
        r (plan out cli-opts)]
    (is (= [:adapters/collision] (map :code (:problems r))))
    (is (str/includes? (:message (first (:problems r))) "collide"))
    (is (= ["a b" "a-b"] (:facts (first (:problems r)))))))

(defn- with-default-7 [output]
  (let [bump (fn [arg] (if (= ["--limit"] (arg "names")) (assoc arg "default" "7") arg))]
    (-> output
        (update-fact "run" #(update-in % ["attrs" "arguments"] (partial mapv bump)))
        (update-fact "run#limit" #(update % "attrs" bump)))))

;; v1: tests/selfdoc/test_sync.py::test_parser_drift_preserves_authored_explanations_and_examples
(deftest cli-drift-preserves-authored-fields
  (let [authored {"purpose" "An authored purpose." "how_it_works" "An authored explanation."
                  "usage" "widget run --limit 1" "tags" ["topic:authored"]}
        tracked (mapv #(if (= "cmd-widget-run" (:name %)) (update % :payload merge authored) %) (synced cli-output cli-opts))
        before (:payload (first (filter #(= "cmd-widget-run" (:name %)) tracked)))
        sp (a/sync-plan (docs (with-default-7 cli-output) cli-opts) tracked)
        updated (first (:update sp))]
    (is (= v1-cli-fields-default-7 (mapv #(cond-> (:fields %) (= "CliCommandDoc" (:model %)) (assoc "usage" (get-in % [:defaults "usage"])))
                                         (docs (with-default-7 cli-output) cli-opts))))
    (testing "the surface of `run` drifts too: its provenance hashes the whole command record, as in v1"
      (is (= [["cmd-widget-run" ["arguments" "provenance"]] ["surface-widget-run" ["provenance"]]]
             (map (juxt :name :changed) (:update sp)))))
    (is (= authored (select-keys (:payload updated) (keys authored))))
    (is (str/includes? (get-in updated [:payload "arguments"]) "\"default\": \"7\""))
    (is (not= (before "provenance") (get-in updated [:payload "provenance"])))
    (is (= ["cmd-widget-db-migrate" "surface-widget-db"] (map :name (:unchanged sp))))
    (is (= {:drift? true :documents [{:name "cmd-widget-run" :path "knowledge/commands/cmd-widget-run.md" :model "CliCommandDoc"
                                      :status "changed" :fields ["arguments" "provenance"]}
                                     {:name "surface-widget-run" :path "knowledge/surfaces/surface-widget-run.md" :model "SurfaceDoc"
                                      :status "changed" :fields ["provenance"]}]}
           (a/drift-report sp)))
    (is (= {"documents" 4 "missing" [] "changed" ["knowledge/commands/cmd-widget-run.md" "knowledge/surfaces/surface-widget-run.md"] "removed" [] "ok" false} (a/v1-report sp)))))

;; ------------------------------------------------------ merge / sync / drift

(deftest merge-payload-is-v1-dict-merge
  (let [p (first (docs symbols-output sym-opts))]
    (is (= (merge (:defaults p) (:fields p)) (a/merge-payload p nil)))
    (let [existing (assoc (a/merge-payload p nil) "purpose" "Mine." "tags" ["t:x"] "docstring" "stale" "extra" "kept")
          merged (a/merge-payload p existing)]
      (is (= ["Mine." ["t:x"] "kept"] (map merged ["purpose" "tags" "extra"])))
      (is (= (get-in p [:fields "docstring"]) (merged "docstring"))))))

;; v1: tests/selfdoc/test_sync.py::test_sync_from_subdirectory_tracks_documents_and_then_is_noop (pure half)
(deftest sync-plan-noop-after-create
  (doseq [[output opts n] [[symbols-output sym-opts 3] [cli-output cli-opts 4]]]
    (let [ds (docs output opts) first-run (a/sync-plan ds []) again (a/sync-plan ds (tracked-of (:create first-run)))]
      (is (= n (count (:create first-run))))
      (is (= (sort (map :name ds)) (map :name (:create first-run))))
      (is (= [[] [] n []] [(:create again) (:update again) (count (:unchanged again)) (:untrack again)]))
      (is (= {:drift? false :documents []} (a/drift-report again))))))

;; v1: tests/selfdoc/test_sync.py::test_missing_generated_file_is_recreated_and_tracking_repaired (pure half: :create / "missing")
(deftest sync-plan-partitions
  (let [ds (docs symbols-output sym-opts)
        [t1 t2 t3] (synced symbols-output sym-opts)
        orphan (assoc t3 :name "python-sldb-gone" :path "knowledge/symbols/python-sldb-gone.md")
        sp (a/sync-plan ds [(assoc-in t1 [:payload "source_span"] "1:1") (assoc-in t2 [:payload "purpose"] "Mine.") orphan])]
    (is (= [(:name t3)] (map :name (:create sp))))
    (is (= [[(:name t1) ["source_span"]]] (map (juxt :name :changed) (:update sp))))
    (is (= [[(:name t2) "Mine."]] (map (juxt :name #(get-in % [:payload "purpose"])) (:unchanged sp))))
    (is (= [orphan] (:untrack sp)))
    (is (every? #(= (:payload %) (a/merge-payload % nil)) (:create sp)))
    (is (= [["python-sldb-gone" "orphan" []] [(:name t1) "changed" ["source_span"]] [(:name t3) "missing" []]]
           (map (juxt :name :status :fields) (:documents (a/drift-report sp)))))
    (is (:drift? (a/drift-report sp)))))

;; v1: tests/selfdoc/test_sync.py::test_removed_commands_are_reported_and_retained (pure half)
(deftest removed-commands-are-untrack-candidates
  (let [tracked (synced cli-output cli-opts)
        gone (update cli-output "facts" (fn [fs] (vec (remove #(str/starts-with? (% "id") "run") fs))))
        sp (a/sync-plan (docs gone cli-opts) tracked)]
    (is (= ["cmd-widget-run" "surface-widget-run"] (map :name (:untrack sp))))
    (is (= 2 (count ((a/v1-report sp) "removed"))))
    (is (= ["orphan" "orphan"] (map :status (:documents (a/drift-report sp)))))))

(deftest owned-rule
  (let [scope {:adapter "python-symbols" :profile "symbols" :language "python" :system "sldb" :output "knowledge"}
        t (first (synced symbols-output sym-opts))
        c (first (synced cli-output cli-opts))
        cli-scope {:adapter "argparse-cli" :profile "cli" :language nil :system "widget" :output "knowledge"}]
    (is (a/owned? scope t))
    (is (a/owned? cli-scope c))
    (is (every? #(a/owned? cli-scope %) (synced cli-output cli-opts)))
    (is (not (a/owned? scope c)))
    (is (not (a/owned? (assoc scope :system "other") t)))
    (is (not (a/owned? (assoc scope :output "docs") t)))
    (is (not (a/owned? (assoc scope :adapter "other-symbols") t)))
    (is (not (a/owned? (assoc scope :language "javascript") t)))
    (is (not (a/owned? scope (assoc t :model "NoteDoc"))))
    (is (not (a/owned? scope (assoc-in t [:payload "provenance"] "hand-written"))))
    (is (not (a/owned? scope (assoc t :path "knowledge/notes/x.md"))))))

;; ----------------------------------------------------------------- models

;; v1: tests/selfdoc/test_model_refs.py::test_public_model_exports_keep_identity (kimun form: frozen descriptors)
;; v1: tests/selfdoc/test_model_refs.py::test_old_registry_path_follows_public_reexports_to_editable_definition (template half)
(deftest frozen-model-descriptors
  (let [load (fn [n] (edn/read-string (slurp (str "resources/kimun/adapters/models/" n ".edn"))))
        planned (concat (docs symbols-output sym-opts) (docs cli-output cli-opts))
        opts {:actor "jp" :timestamp "2026-10-06T00:00:00.000Z" :tree-id "01ARZ3NDEKTSV4RRFFQ69G5FAV"}]
    (doseq [n (mapcat val a/profile-models) :let [d (load n) declared (set (map :name (:fields d)))]]
      (is (= n (:model/name d)))
      (is (contains? a/model-dirs n))
      (testing "the models store accepts the frozen descriptor as-is"
        (is (:plan (mstore/define-plan host/host (revision/empty-store host/host {"jp" :all}) d opts))))
      (testing "every planned or defaulted field is declared, every required field is produced"
        (doseq [p planned :when (= n (:model p)) :let [payload (a/merge-payload p nil)]]
          (is (every? declared (keys payload)))
          (is (every? #(contains? payload (:name %)) (filter :required (:fields d)))))))
    (is (str/includes? (:template (load "CliCommandDoc")) "⸢rev•command_path⸥"))))

;; v1 materializers' "Documentation roundtrip failed" guard, against kimun's own write path.
(deftest planned-payloads-roundtrip-through-docs-create
  (let [opts {:actor "jp" :timestamp "2026-10-06T00:00:00.000Z" :tree-id "01ARZ3NDEKTSV4RRFFQ69G5FAV"}
        define (fn [s n] (let [d (edn/read-string (slurp (str "resources/kimun/adapters/models/" n ".edn")))]
                           (:store (revision/apply-plan s (:plan (mstore/define-plan host/host s d (assoc opts :base (:head s))))))))
        store (reduce define (revision/empty-store host/host {"jp" :all}) (mapcat val a/profile-models))]
    (doseq [p (concat (docs symbols-output sym-opts) (docs cli-output cli-opts))
            payload [(a/merge-payload p nil) (assoc (a/merge-payload p nil) "tags" ["topic:x" "k:v"] "purpose" "Authored.\n\nTwo paragraphs.")]]
      (is (= payload (:payload (kdocs/create-plan host/host store (:model p) (:name p) payload
                                                  (assoc opts :base (:head store) :doc "01ARZ3NDEKTSV4RRFFQ69G5FDC"
                                                         :tree-id "01ARZ3NDEKTSV4RRFFQ69G5FTR" :path (:path p)))))
          (:name p)))))

(deftest cites-spec-references
  (is (= ["07" "12a"] (a/cites "See spec 12a and Spec 07; spec 07 again, not spec 7.")))
  (is (= [] (a/cites nil))))

;; v1: tests/selfdoc/test_python_scanner.py::test_kgdb_graph_check_rejects_missing_or_stale_source_nodes
;; v1: tests/selfdoc/test_python_scanner.py::test_source_graph_check_rejects_missing_or_stale_source_nodes
(deftest source-graph-check-rejects-missing-or-stale-source-nodes
  (testing "v1 snapshot contract: missing target, stale sha256/ast, current match, extra stale node, malformed input"
    (let [snapshot {"nodes" [{"identity" {"node_id" "node" "node_type" "python_symbol"}
                              "source" {"sha256" "a"}
                              "ast" {"line" 1}}]}]
      (is (false? (a/source-graph-current? snapshot nil)))
      (is (= {:current? false :status :missing :missing ["node"] :stale [] :orphan []}
             (a/source-graph-check snapshot nil)))
      (is (false? (a/source-graph-current?
                   snapshot
                   {"nodes" [{"id" "node" "schema" {"source" {"sha256" "b"} "ast" {"line" 1}}}]})))
      (is (= {:current? false :status :stale :missing [] :stale ["node"] :orphan []}
             (a/source-graph-check
              snapshot
              {"nodes" [{"id" "node" "schema" {"source" {"sha256" "b"} "ast" {"line" 1}}}]})))
      (is (true? (a/source-graph-current?
                  snapshot
                  {"nodes" [{"id" "node" "schema" {"source" {"sha256" "a"} "ast" {"line" 1}}}]})))
      (is (false? (a/source-graph-current?
                   snapshot
                   {"nodes" [{"id" "node" "schema" {"source" {"sha256" "a"} "ast" {"line" 1}}}
                             {"id" "stale" "schema" {}}]})))
      (is (= {:current? false :status :stale :missing [] :stale [] :orphan ["stale"]}
             (a/source-graph-check
              snapshot
              {"nodes" [{"id" "node" "schema" {"source" {"sha256" "a"} "ast" {"line" 1}}}
                        {"id" "stale" "schema" {}}]})))
      (is (= {:current? false :status :unreadable :missing ["node"] :stale [] :orphan []}
             (a/source-graph-check snapshot "not json")))))
  (testing "kimun planned vs tracked PythonSymbolDoc nodes"
    (let [planned (docs symbols-output sym-opts)
          names (vec (sort (map :name planned)))
          tracked (mapv (fn [d] {:name (:name d) :model (:model d) :path (:path d) :payload (:fields d)}) planned)]
      (is (false? (a/source-graph-current? planned nil)))
      (is (= {:current? false :status :missing :missing names :stale [] :orphan []}
             (a/source-graph-check planned nil)))
      (is (true? (a/source-graph-current? planned tracked)))
      (let [first-name (:name (first tracked))
            stale-sha (update tracked 0 assoc-in [:payload "source_sha256"] "0000")
            stale-span (update tracked 0 assoc-in [:payload "source_span"] "1:999")
            with-orphan (conj tracked {:name "stale-sym" :model "PythonSymbolDoc"
                                       :path "knowledge/symbols/stale-sym.md"
                                       :payload {"id" "stale-sym"}})]
        (is (= {:current? false :status :stale :missing [] :stale [first-name] :orphan []}
               (a/source-graph-check planned stale-sha)))
        (is (= {:current? false :status :stale :missing [] :stale [first-name] :orphan []}
               (a/source-graph-check planned stale-span)))
        (is (= {:current? false :status :stale :missing [] :stale [] :orphan ["stale-sym"]}
               (a/source-graph-check planned with-orphan)))))))
