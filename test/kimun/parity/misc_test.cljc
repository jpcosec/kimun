(ns kimun.parity.misc-test
  "Address reads through the CLI: port of sldb v1 `test_address_surface.py`
   `test_singular_alias_routes_to_the_same_handler` (v1 `get` / `glob` aliases are
   retired by docs/v2/06 §8; `find` with an address answers the same questions)."
  (:require [clojure.test :refer [deftest is]]
            [babashka.fs :as fs]
            [kimun.cli.main :as main]))

(def env {"USER" "tester"})
(def ticket (str (fs/absolutize "test/fixtures/models/ticket.edn")))

(defn- run [cwd & argv] (:envelope (main/run argv env cwd)))

(defn- project []
  (let [p (str (fs/create-temp-dir {:prefix "kimun-parity-misc-"}))]
    (run p "stores" "init" "--name" "p")
    (run p "models" "define" "-f" ticket)
    (doseq [[n t s] [["memo1" "Memo1" "open"] ["memo2" "Memo2" "closed"]]]
      (run p "docs" "create" "TicketDoc" n "--set" (str "title=" t) "--set" (str "status=" s) "--set" "body=b"))
    p))

;; v1: test_address_surface.py::test_singular_alias_routes_to_the_same_handler
(deftest an-address-reads-one-value-and-a-glob-reads-many
  (let [p (project)
        one (run p "find" "st.{TicketDoc}.memo2.title")
        many (run p "find" "st.{TicketDoc}.memo*.status")]
    (is (true? (:ok one)) (pr-str one))
    (is (= ["Memo2"] (mapv :value (:data one))))
    (is (= ["Memo1" "Memo2"] (mapv :value (:data (run p "find" "st.{TicketDoc}.memo*.title")))))
    (is (= ["st.{TicketDoc}.memo1.status" "st.{TicketDoc}.memo2.status"] (mapv :address (:data many))))
    (is (= ["open" "closed"] (mapv :value (:data many))))))

;; v1: test_address_surface.py::test_field_update_moves_the_model_hash_and_keeps_integrity
;; (v1's per-model hash_b is the Merkle identity of the model's documents; v2's is the tree of
;; each document, `docs show :content-hash`, plus the store head)
(deftest a-field-write-moves-the-document-identity-and-the-store-stays-sound
  (let [p (project)
        tree #(get-in (run p "docs" "show" "memo1") [:data :content-hash])
        other #(get-in (run p "docs" "show" "memo2") [:data :content-hash])
        before (tree) other-before (other)
        e (run p "fields" "set" "memo1" "status" "\"closed\"")]
    (is (true? (:ok e)) (pr-str e))
    (is (string? before))
    (is (not= before (tree)) "the document's identity moved with its field")
    (is (= other-before (other)) "a sibling document did not move")
    (is (true? (:ok (run p "stores" "check"))))
    (is (true? (:ok (run p "stores" "verify"))))
    (is (= ["closed"] (mapv :value (:data (run p "find" "st.{TicketDoc}.memo1.status")))))))
