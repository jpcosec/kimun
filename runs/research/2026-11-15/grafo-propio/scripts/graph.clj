(ns graph
  (:require [babashka.fs :as fs] [clojure.string :as str] [clojure.edn :as edn]
            [clojure.tools.reader :as r] [clojure.tools.reader.reader-types :as rt]
            [clojure.pprint :as pp]))
(def root "/home/jp/proyectos/kimun/")
(defn read-forms [file]
  (let [rdr (rt/indexing-push-back-reader (slurp (str file)))]
    (loop [forms []]
      (let [f (r/read {:read-cond :allow :features #{:clj} :eof ::eof} rdr)]
        (if (= f ::eof) (vec (mapcat (fn flat [x] (if (and (seq? x) (= 'do (first x))) (mapcat flat (rest x)) [x])) forms)) (recur (conj forms f)))))))
(defn requires-of [ns-form]
  (for [clause (rest ns-form) :when (and (seq? clause) (= :require (first clause)))
        spec (rest clause) :let [lib (if (vector? spec) (first spec) spec)] :when (symbol? lib)] (str lib)))
(defn ring-of [n]
  (cond (str/starts-with? n "kimun.kernel.") "kernel"
        (str/starts-with? n "kimun.host.") "host"
        (str/starts-with? n "kimun.surface.markdown.") "surface.markdown"
        (str/starts-with? n "kimun.graph.") "graph"
        :else "kimun.*"))
(def files (sort (map str (fs/glob (str root "src") "**/*.cljc"))))
(def nodes
  (for [f files
        :let [forms (read-forms f) nsf (first (filter #(and (seq? %) (= 'ns (first %))) forms))
              n (str (second nsf))
              pubs (for [x forms :when (and (seq? x) (#{'defn 'defmacro 'def 'defprotocol 'defrecord 'defmulti} (first x)) (symbol? (second x))
                                            (not (:private (meta (second x)))))] (str (second x)))
              pubfn (count (filter #(and (seq? %) (= 'defn (first %)) (not (:private (meta (second %))))) forms))]]
    {:ns n :file (subs f (count root)) :lines (count (str/split-lines (slurp f))) :defn-public pubfn :public-vars (vec pubs)
     :dir (str (fs/parent (subs f (count (str root "src/kimun/"))))) :ring (ring-of n) :requires (vec (sort (set (requires-of nsf))))}))
(def names (set (map :ns nodes)))
(def edges (vec (for [n nodes r (:requires n) :when (names r)] [(:ns n) r])))
(spit "/tmp/kimun-planes/grafo-propio/grafo.edn" (with-out-str (pp/pprint {:nodes (vec nodes) :edges edges})))
(defn short [s] (str/replace s "kimun." ""))
(spit "/tmp/kimun-planes/grafo-propio/grafo.dot"
  (str "digraph kimun {\n rankdir=LR;\n"
       (apply str (for [[ring ns] (group-by :ring nodes)]
         (str " subgraph \"cluster_" ring "\" {label=\"" ring "\";\n"
              (apply str (for [n ns] (str "  \"" (short (:ns n)) "\" [label=\"" (short (:ns n)) "\\n" (:lines n) "L\"];\n"))) " }\n")))
       (apply str (for [[a b] edges] (str " \"" (short a) "\" -> \"" (short b) "\";\n"))) "}\n"))
;; analysis
(def adj (reduce (fn [m [a b]] (update m a (fnil conj #{}) b)) {} edges))
(def radj (reduce (fn [m [a b]] (update m b (fnil conj #{}) a)) {} edges))
;; tarjan
(defn scc []
  (let [idx (atom {}) low (atom {}) on (atom #{}) st (atom []) c (atom 0) out (atom [])]
    (letfn [(sc [v] (swap! idx assoc v @c) (swap! low assoc v @c) (swap! c inc) (swap! st conj v) (swap! on conj v)
              (doseq [w (adj v)]
                (cond (not (@idx w)) (do (sc w) (swap! low update v min (@low w)))
                      (@on w) (swap! low update v min (@idx w))))
              (when (= (@low v) (@idx v))
                (loop [comp []] (let [w (peek @st)] (swap! st pop) (swap! on disj w)
                                  (if (= w v) (swap! out conj (conj comp w)) (recur (conj comp w)))))))]
      (doseq [n names :when (not (@idx n))] (sc n)))
    @out))
(def sccs (scc))
(def big (filter #(> (count %) 1) sccs))
(def comp-of (into {} (for [c sccs v c] [v (set c)])))
(def depth (let [memo (atom {})]
  (letfn [(d [v seen] (or (@memo v)
            (let [r (if (seen v) 0 (let [ds (map #(d % (conj seen v)) (remove (comp-of v) (adj v)))] (if (seq ds) (inc (apply max ds)) 0)))] (swap! memo assoc v r) r)))]
    (into {} (for [n names] [n (d n #{})])))))
(def area (fn [n] (let [p (str/split (short n) #"\.")] (if (> (count p) 2) (str/join "." (take 2 p)) (first p)))))
(def rows (for [n (sort names)]
  {:ns n :in (count (radj n)) :out (count (adj n)) :depth (depth n)
   :areas-out (count (set (map area (adj n)))) :areas (sort (set (map area (adj n))))}))
(println "nodes" (count names) "edges" (count edges) "sccs>1" (map sort big))
(println "maxdepth" (apply max (vals depth)))
(println "--depth histogram" (sort (frequencies (vals depth))))
(println "--top in") (doseq [r (take 15 (sort-by (comp - :in) rows))] (println (:in r) (:ns r)))
(println "--top out") (doseq [r (take 15 (sort-by (comp - :out) rows))] (println (:out r) (:areas-out r) (:ns r)))
(println "--areas") (doseq [r (take 15 (sort-by (comp - :areas-out) rows))] (println (:areas-out r) (:ns r) (:areas r)))
(println "--big") (doseq [n (take 14 (sort-by (comp - :lines) nodes))] (println (:lines n) (:defn-public n) (:file n)))
(println "--isolated" (filter #(and (zero? (:in %)) (zero? (:out %))) rows))
(println "--no-in" (map :ns (filter #(zero? (:in %)) rows)))
(println "--ring crossing counts" (frequencies (for [[a b] edges] [(ring-of a) (ring-of b)])))
(println "--dir crossing" (sort-by (comp - val) (frequencies (for [[a b] edges] [(area a) (area b)]))))
(spit "/tmp/kimun-planes/grafo-propio/metricas.edn" (with-out-str (pp/pprint rows)))
(spit "/tmp/kimun-planes/grafo-propio/capas.txt" (apply str (for [[d ns] (sort (group-by depth names))] (str d ": " (str/join ", " (map short (sort ns))) "\n"))))
