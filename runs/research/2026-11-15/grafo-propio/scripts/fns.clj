(require '[clojure.tools.reader :as r] '[clojure.tools.reader.reader-types :as rt] '[clojure.string :as str])
(defn run [file]
  (let [rdr (rt/indexing-push-back-reader (slurp file))]
    (println "##" file)
    (loop []
      (let [f (r/read {:read-cond :allow :features #{:clj} :eof ::eof} rdr)]
        (when (not= f ::eof)
          (when (and (seq? f) ('#{defn defmacro def} (first f)) (symbol? (second f)) (not (:private (meta (second f)))))
            (let [d (nth f 2 nil) d (if (string? d) d (when (and (seq? f) (string? (nth f 2 nil))) d))
                  doc (some #(when (string? %) %) (take 2 (drop 2 f)))]
              (println (:line (meta f)) (second f) "|" (some-> doc (str/replace #"\s+" " ") (as-> s (subs s 0 (min 95 (count s))))))))
          (recur))))))
(doseq [f *command-line-args*] (run (str "/home/jp/proyectos/kimun/src/kimun/" f)))
