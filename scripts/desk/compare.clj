#!/usr/bin/env bb
;; Compares two kimun stores (a rebuilt .kimun against a reference), using
;; kimun.cli.main/run with --store:
;;   - same models (name, version)
;;   - same documents (name, model, path)
;;   - same payload for every document (docs show NAME :data :payload)
;;
;; Usage: bb scripts/desk/compare.clj [NEW_STORE] [OLD_STORE]
;;   defaults: .kimun and /tmp/kimun-desk-old
;; Exit 0 when everything matches, 1 otherwise.
(ns desk-compare
  (:require [clojure.set :as set]
            [clojure.string :as str]
            [kimun.cli.main :as main]))

(def repo (System/getProperty "user.dir"))
(def env (System/getenv))
(def new-store (or (first *command-line-args*) (str repo "/.kimun")))
(def old-store (or (second *command-line-args*) "/tmp/kimun-desk-old"))

(defn run
  "Runs the CLI in-process against `store`; returns the envelope or throws on a
   command failure (a failure envelope carries the reason)."
  [store & argv]
  (let [e (:envelope (main/run (into ["--store" store] argv) env repo))]
    (when-not (:ok e)
      (throw (ex-info (str "command failed against " store ": " (pr-str argv))
                      {:error (:error e)})))
    e))

(defn- model-map [store]
  (into {} (map (fn [m] [(:name m) (:version m)]))
        (:data (run store "models" "list"))))

(defn- doc-list [store]
  (mapv (fn [d] (select-keys d [:name :model :path]))
        (:data (run store "docs" "list"))))

(defn- payloads [store docs]
  (into {} (map (fn [{:keys [name]}]
                  [name (get-in (run store "docs" "show" name) [:data :payload])]))
        docs))

(defn- print-diff [label a b]
  (when (seq a) (println (str label " only in new (" (count a) "):") (str/join ", " (sort a))))
  (when (seq b) (println (str label " only in old (" (count b) "):") (str/join ", " (sort b)))))

(defn -main []
  (let [mn (model-map new-store) mo (model-map old-store)
        dn (doc-list new-store) do* (doc-list old-store)
        sn (set (map (juxt :name :model :path) dn)) so (set (map (juxt :name :model :path) do*))
        names (sort (set/union (set (keys mn)) (set (keys mo))))
        model-diff (filter #(not= (get mn %) (get mo %)) names)
        missing (set/difference so sn) extra (set/difference sn so)
        common-names (sort (set/intersection (set (map :name dn)) (set (map :name do*))))
        common-new (filter #(contains? (set common-names) (:name %)) dn)
        pn (payloads new-store common-new) po (payloads old-store common-new)
        payload-diff (filter #(not= (get pn %) (get po %)) common-names)]
    (println "new store:" new-store)
    (println "old store:" old-store)
    (println (str "models: " (count mn) " in new, " (count mo) " in old, " (count model-diff) " different"))
    (when (seq model-diff)
      (doseq [n model-diff] (println "  ✗" n "new=" (get mn n) "old=" (get mo n))))
    (println (str "documents (name, model, path): " (count sn) " in new, " (count so) " in old, "
                  (count (set/intersection sn so)) " equal, " (count missing) " missing, " (count extra) " extra"))
    (print-diff "document" extra missing)
    (println (str "payloads: " (count common-names) " compared, " (count payload-diff) " different"))
    (doseq [n (take 20 payload-diff)] (println "  ✗ payload" n))
    (let [ok? (and (empty? model-diff) (empty? missing) (empty? extra) (empty? payload-diff))]
      (println (if ok? "MATCH" "DIFFERENT"))
      (System/exit (if ok? 0 1)))))

(-main)
