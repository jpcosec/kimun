#!/usr/bin/env bb
;; Builds this repository's desk store (.kimun) from desk/models/*.edn and
;; desk/kimun-manifest.edn, in ONE Babashka process (kimun.cli.main/run).
;;
;; Usage: bb desk [--rebuild]
;;   Refuses to touch an existing .kimun unless --rebuild is given, in which
;;   case the old store is moved aside to .kimun.bak-<timestamp>.
;;
;; Exit code 0 when every model was defined and every document tracked.
(ns desk-build
  (:require [babashka.fs :as fs]
            [clojure.edn :as edn]
            [clojure.string :as str]
            [kimun.cli.main :as main]))

(def repo (System/getProperty "user.dir"))
(def env (System/getenv))
(def store-dir (str (fs/path repo ".kimun")))
(def models-dir (str (fs/path repo "desk" "models")))
(def manifest-file (str (fs/path repo "desk" "kimun-manifest.edn")))

(defn run
  "Calls the CLI in-process; returns the envelope."
  [& argv]
  (:envelope (main/run (vec argv) env repo)))

(defn- fail! [& msg]
  (binding [*out* *err*] (println (str/join " " msg)))
  (System/exit 1))

;; ---------------------------------------------------------------- model order

(defn- topo-order
  "Descriptors of `desk/models` ordered so a model's bases (when the base is
   itself one of the descriptors) come before it. Ties break alphabetically."
  [descs]
  (let [by-name (into {} (map (fn [d] [(:model/name d) d]) descs))
        names (set (keys by-name))
        deps (fn [n] (set (filter names (:base (by-name n)))))
        loop* (fn loop* [done pending]
                (if (empty? pending)
                  done
                  (let [ready (filter #(every? (set done) (deps %)) pending)]
                    (when (empty? ready)
                      (fail! "model cycle among:" (str/join ", " (sort pending))))
                    (recur (into done ready)
                           (remove (set ready) pending)))))]
    (map by-name (loop* [] (sort names)))))

;; ---------------------------------------------------------------- steps

(defn- resolve-rebuild? [args]
  (cond
    (empty? args) false
    (= args ["--rebuild"]) true
    :else (fail! "unknown argument(s):" (str/join " " args) "\nusage: bb desk [--rebuild]")))

(defn- prepare-store! [rebuild?]
  (when (fs/exists? store-dir)
    (if rebuild?
      (let [stamp (-> (java.time.LocalDateTime/now)
                      (.format (java.time.format.DateTimeFormatter/ofPattern "yyyyMMdd-HHmmss")))
            bak (str store-dir ".bak-" stamp)]
        (fs/move store-dir bak)
        (println (str "moved existing store to " (fs/file-name bak))))
      (fail! (str ".kimun already exists at " store-dir
                  "; pass --rebuild to move it aside and rebuild"))))
  (let [e (run "stores" "init")]
    (when-not (:ok e) (fail! "stores init failed:" (pr-str (:error e))))))

(defn- define-models! []
  (let [descs (->> (fs/glob models-dir "*.edn")
                   (map #(edn/read-string (slurp (str %))))
                   (sort-by :model/name))]
    (reduce (fn [acc d]
              (let [name (:model/name d)
                    rel (str/replace (str (fs/relativize repo (fs/path models-dir (str name ".edn")))) fs/file-separator "/")
                    e (run "models" "define" "-f" rel)]
                (if (:ok e)
                  (update acc :ok conj name)
                  (update acc :failed conj {:model name :error (get-in e [:error :type]) :message (get-in e [:error :message])}))))
            {:ok [] :failed []}
            (topo-order descs))))

(defn- track-docs! []
  (let [entries (edn/read-string (slurp manifest-file))]
    (reduce (fn [acc {:keys [name model path force]}]
              (let [argv (cond-> ["docs" "track" model path "--name" name]
                           force (conj "--force"))
                    e (apply run argv)]
                (if (:ok e)
                  (update acc :ok conj name)
                  (update acc :failed conj {:name name :model model :path path :force (boolean force)
                                            :error (get-in e [:error :type]) :message (get-in e [:error :message])}))))
            {:ok [] :failed []}
            entries)))

(defn- report! [models docs]
  (println "")
  (println "desk store:" store-dir)
  (println (str "models: " (count (:ok models)) " defined, " (count (:failed models)) " failed"))
  (doseq [f (:failed models)] (println "  ✗" (:model f) (str "(" (:error f) ")") (:message f)))
  (println (str "documents: " (count (:ok docs)) " tracked, " (count (:failed docs)) " failed"))
  (doseq [f (:failed docs)] (println "  ✗" (:name f) (str "(" (:model f) ", " (:error f) ")") (:message f)))
  (let [failed (+ (count (:failed models)) (count (:failed docs)))]
    (if (zero? failed)
      (println "OK")
      (do (println (str failed " failure(s)")) (System/exit 1)))))

(defn -main [& args]
  (let [rebuild? (resolve-rebuild? args)]
    (prepare-store! rebuild?)
    (let [models (define-models!)
          docs (track-docs!)]
      (report! models docs))))

(apply -main *command-line-args*)
