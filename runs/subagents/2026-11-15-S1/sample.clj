;; S1 sampler — one free-form sample at a time (docs/v2/04 §11.2, 12 §1 S1).
;;
;;   memcap 3 bb -cp src:test:resources runs/subagents/2026-11-15-S1/sample.clj <start> <count> [seed]
;;   memcap 3 bb -cp src:test:resources runs/subagents/2026-11-15-S1/sample.clj fixture
;;
;; For each document it prints one line: the AST verdict (contract: pod view ==
;; parse-native) and, when the tree differs, the first differing path with both
;; node types. It also reports where the pod's own block span/kind disagrees with
;; the profile segmentation (`cell`), which is what a pod-driven view would have
;; to repair; those cells are the classes to close.

(require '[clojure.string :as str]
         '[kimun.host.default :as host]
         '[kimun.host.treesitter :as ts]
         '[kimun.surface.markdown.ast :as ast]
         '[kimun.surface.markdown.cst :as cst]
         '[kimun.surface.markdown.cst-ast :as cst-ast])

;; ---- generators (same alphabet as cst-ast-parity-test, plus the fixed classes)

(def lines
  ["a" "b" "" "   " "# h" "## h #" "---" "***" "> q" "> > q2" ">> q3"
   "- x" "* y" "+ z" "1. a" "9. a" "10. b" "1) paren"
   "  indented2" "   indented3" "    indented4"
   "```" "```clj" "````" "~~~" "~~~ `bad`" "  ```"
   "| a | b |" "|---|---|" "<div>" "</div>" "<!-- c -->" "=== " "--- "])

(defn gen-doc [seed i]
  (let [r (java.util.Random. (+ (* 7919 seed) i))
        n (.nextInt r 18)]
    (str/join "\n" (repeatedly n #(nth lines (.nextInt r (count lines)))))))

(defn snippet [s] (subs s 0 (min 120 (count s))))

;; ---- first differing path

(defn type-of [x] (if (map? x) [(:type x) (:format x)] x))

(defn diff-path
  "First differing node as `[path native pod]`; path is a vector of child indices."
  ([a b] (diff-path [] a b))
  ([p a b]
   (let [xa (:children a) xb (:children b)]
     (loop [i 0]
       (cond
         (and (nil? (get xa i)) (nil? (get xb i)))
         (if (= a b) nil [p (type-of a) (type-of b)])

         (not= (type-of (get xa i)) (type-of (get xb i)))
         [(conj p i) (type-of (get xa i)) (type-of (get xb i))]

         :else (or (diff-path (conj p i) (get xa i) (get xb i))
                   (recur (inc i))))))))

;; ---- pod cell vs profile cell (runs/2026-10-08/S2-report.md §Fase 2 classes)

(defn- expand [node]
  (mapcat (fn [c]
            (cond (not (:named c)) []
                  (= "section" (:type c)) (expand c)
                  :else [c]))
          (:children node)))

(defn- pod-kind [t]
  (case t
    ("minus_metadata" "plus_metadata") :frontmatter
    "atx_heading" :heading
    ("link_reference_definition" "paragraph") :paragraph
    "fenced_code_block" :code
    "block_quote" :quote
    "list" :list
    "thematic_break" :thematic-break
    "html_block" :html
    "pipe_table" :table
    nil))

(defn- rows [node]
  (let [[sr _] (:start_point node) [er ec] (:end_point node)]
    [(long sr) (long (if (zero? ec) er (inc er)))]))

(defn- line-spans
  "Same rule as `cst_ast/line-spans` (private): mid-line endings yield to the
   node that starts on that line."
  [nodes]
  (let [spans (mapv rows nodes)
        starts (mapv #(first (:start_point %)) nodes)]
    (mapv (fn [k [st e]]
            (let [[er ec] (:end_point (nth nodes k))
                  next-sr (get starts (inc k))]
              (if (and (pos? ec) (= next-sr er) (= e (inc er)))
                [st er]
                [st e])))
          (range) spans)))

(defn- blank? [line] (str/blank? (cst/strip-cr line)))

(defn- trim-blanks
  "Same rule as `cst_ast/trim-end-blanks`: a pod node absorbs the blank line
   after it unless it is code/frontmatter."
  [lines i j k]
  (if (contains? #{:code :frontmatter} k)
    j
    (loop [e j]
      (if (and (> e i) (blank? (nth lines (dec e))))
        (recur (dec e))
        e))))

(defn cells
  "Top-level pod block vs the profile block starting on the same line. A cell is
   a **rejected** pod block (what a pod-driven view would have to repair):
   `[line pod-type pod-kind profile-kind reason]`, reason in
   `:kind-mismatch` (unknown pod type is nil) / `:span-mismatch` / `:no-profile-block`."
  [text]
  (let [norm (if (str/ends-with? text "\n") text (str text "\n"))
        root (first (ts/parse-block norm))
        nodes (expand root)
        lines (let [raw (vec (str/split text #"\n" -1))
                    raw (if (str/ends-with? text "\n") (vec (butlast raw)) raw)]
                (if (= text "") [] raw))
        spans (line-spans nodes)
        prof (cst/parse text)]
    (for [[node [i j]] (map vector nodes spans)
          :let [pk (pod-kind (:type node))
                pb (some #(when (= (:start %) i) %) (:blocks prof))
                fk (:kind pb)
                pend (when pb (+ (:start pb) (count (:lines pb))))
                k (or pk :paragraph)
                j' (trim-blanks lines i j pk)]
          :let [reason (cond (nil? pb) :no-profile-block
                             (not= pk fk) :kind-mismatch
                             (not= j' pend) :span-mismatch)]
          :when reason]
      [i (:type node) pk fk reason
       (mapv cst/strip-cr (subvec lines i (max (inc i) (min j (count lines)))))
       (when-let [cov (some #(when (and (<= (:start %) i)
                                        (< i (+ (:start %) (count (:lines %))))) %)
                            (:blocks prof))]
         [(str (:kind cov) " " (:start cov) "-" (+ (:start cov) (count (:lines cov))))
          (mapv cst/strip-cr (:lines cov))])])))

;; ---- one sample

(defn step [i text]
  (let [n (ast/parse-native host/host text)
        p (cst-ast/parse host/host text)
        cs (cells text)
        verdict (cond (= n p) "same" :else "DIFF")
        d (when (not= n p) (diff-path n p))]
    (println (format "%-6d %s cells=%d %s" i verdict (count cs)
                     (if d (str "path=" (pr-str (first d)) " native=" (pr-str (second d))
                                " pod=" (pr-str (nth d 2)))
                         "")))
    (when (seq cs)
      (println "        cells:" (pr-str (sort-by (comp pr-str first) (frequencies (map rest cs))))))
    (when (= "DIFF" verdict)
      (println "        text:" (pr-str (snippet text))))
    (count cs)))

(defn drill [start n]
  (doseq [i (range start (+ start n))
          :let [t (gen-doc 1 i)
                cs (cells t)]
          :when (seq cs)]
    (println "== doc" i "cells" (count cs) "text:" (pr-str t))
    (doseq [[line pt pk fk reason pod-lines cov] cs]
      (println (format "   line %d %s %s->%s %s pod=%s profile=%s"
                       line pt pk fk reason (pr-str pod-lines) (pr-str cov))))))

(defn -main [& args]
  (cond
    (not (ts/available?)) (println "pod absent: run `bb pod` (skipped)")
    (= "fixture" (first args))
    (let [text (slurp "test/fixtures/markdown/freeform_divergences.md")]
      (step 0 text)
      (println "fixture cells:" (pr-str (cells text))))
    (= "cells" (first args))
    (drill (Long/parseLong (second args)) (Long/parseLong (nth args 2)))
    :else
    (let [[start n seed] (mapv #(Long/parseLong %) args)
          cs (doall (for [i (range start (+ start n))]
                      (let [t (gen-doc (or seed 1) i)]
                        [i t (step i t)])))]
      (println "samples" (count cs) "with-cells"
               (count (filter (fn [[_ _ c]] (pos? (long c))) cs))))))

(apply -main *command-line-args*)
