(ns kimun.surface.index.where
  "The `--where` filter (docs/v2/06 §3): `parse` turns an expression into a
   closed AST and `matches?` evaluates that AST against one document, both
   pure. Two surface syntaxes are accepted. The v1 string syntax, one
   predicate per expression: `has(f)`, `\"x\" in f`, `f ~ \"re\"`,
   `model <= Base`, `f OP lit` with OP in `= != >= <=` and lit a string
   literal or an unsigned number. And the EDN vector form written as a
   string, which also carries `:and`/`:or`/`:not`. Anything else raises
   `:query/bad-where` with the position of the offending token; v1's silent
   `False` is not reproduced.

   AST: `[:has f]` `[:in x f]` `[:re f pat]` `[:model<= Base]`
   `[:cmp op f v]` (op one of `:=` `:!=` `:>=` `:<=`) `[:and q+]` `[:or q+]`
   `[:not q]`; fields and strings are strings, v a string or a number."
  (:require [clojure.string :as str]
            [kimun.kernel.err :as err]))

;; ---------------------------------------------------------------- errors

(defn- bad
  "Raises `:query/bad-where` at character offset `pos` of `expr`."
  [expr pos msg]
  (err/raise :query/bad-where
             (str "bad --where at position " pos ": " msg " in " (pr-str expr))
             {:expression expr :position pos :reason msg}))

;; ------------------------------------------------------- literals (shared)

(def ^:private literal-re #"^\"(?:[^\"\\]|\\.)*\"")

(defn- unquote-literal
  "The literal's value: quotes off, `\\\"` and `\\\\` resolved, any other
   backslash kept verbatim (v1's `_unquote`)."
  [raw]
  (let [body (subs raw 1 (dec (count raw)))
        n (count body)]
    (loop [i 0 out (transient [])]
      (cond
        (>= i n) (apply str (persistent! out))
        (and (= \\ (nth body i)) (< (inc i) n) (#{\" \\} (nth body (inc i))))
        (recur (+ i 2) (conj! out (nth body (inc i))))
        :else (recur (inc i) (conj! out (nth body i)))))))

(def ^:private number-re #"[+-]?\d+(?:\.\d+)?")

(defn- parse-number [s] (if (str/includes? s ".") (parse-double s) (parse-long (str/replace s "+" ""))))

(def ^:private ops {"=" :=, "!=" :!=, ">=" :>=, "<=" :<=})

(defn- check-regex!
  "Raises at `pos` when `pat` is not a valid regular expression."
  [expr pos pat]
  (try (re-pattern pat)
       (catch Throwable _ (bad expr pos (str "invalid regular expression " (pr-str pat))))))

;; --------------------------------------------------------- v1 string syntax

(defn- skip-ws [s i]
  (loop [i i] (if (and (< i (count s)) (Character/isWhitespace ^char (nth s i))) (recur (inc i)) i)))

(defn- scan
  "Matches `re` (written without anchor) at offset `i`; `[match end]` or nil."
  [s i re]
  (when-let [m (re-find re (subs s i))]
    (let [m (if (vector? m) (first m) m)] [m (+ i (count m))])))

(def ^:private ident-re #"^[A-Za-z_]\w*")
(def ^:private op-re #"^(?:!=|>=|<=|=)")
(def ^:private num-lit-re #"^\d+(?:\.\d+)?")

(defn- expect-end [expr i]
  (let [j (skip-ws expr i)]
    (when (< j (count expr)) (bad expr j "unexpected trailing text"))))

(defn- literal-at
  "Reads a string literal at `i` (after whitespace); `[value end start]`."
  [expr i what]
  (let [i (skip-ws expr i)
        [raw end] (or (scan expr i literal-re)
                      (bad expr i (if (= \" (get expr i)) "unterminated string literal" (str "expected " what))))]
    [(unquote-literal raw) end i]))

(defn- nonempty-literal [expr i what]
  (let [[v end at] (literal-at expr i what)]
    (when (empty? v) (bad expr at "empty string literal not allowed here"))
    [v end]))

(defn- parse-has [expr i]
  (let [open (+ i 4)
        close (str/index-of expr ")" open)]
    (when-not close (bad expr (count expr) "missing ')' after has("))
    (let [field (str/trim (subs expr open close))]
      (when (empty? field) (bad expr open "has() needs a field"))
      (expect-end expr (inc close))
      [:has field])))

(defn- parse-in [expr i]
  (let [[needle end] (nonempty-literal expr i "string literal")
        j (skip-ws expr end)]
    (when-not (and (str/starts-with? (subs expr j) "in") (> j end))
      (bad expr j "expected 'in' after string literal"))
    (let [k (skip-ws expr (+ j 2))]
      (when (= k (+ j 2)) (bad expr k "expected whitespace after 'in'"))
      (let [[field end2] (or (scan expr k ident-re) (bad expr k "expected a field name after 'in'"))]
        (expect-end expr end2)
        [:in needle field]))))

(defn- parse-after-ident [expr field end]
  (let [j (skip-ws expr end)
        c (get expr j)]
    (if (= \~ c)
      (let [[pat e] (nonempty-literal expr (inc j) "string literal after '~'")]
        (expect-end expr e)
        (check-regex! expr (skip-ws expr (inc j)) pat)
        [:re field pat])
      (let [[op oend] (or (scan expr j op-re) (bad expr j "expected one of ~ = != >= <= after field name"))
            k (skip-ws expr oend)
            model (when (and (= field "model") (= op "<="))
                    (when-let [[base e] (scan expr k ident-re)]
                      (when (>= (skip-ws expr e) (count expr)) base)))]
        (if model
          [:model<= model]
          (if (= \" (get expr k))
            (let [[v e] (literal-at expr k "a literal")]
              (expect-end expr e)
              [:cmp (ops op) field v])
            (let [[n e] (or (scan expr k num-lit-re) (bad expr k "expected a string or unsigned number literal"))]
              (expect-end expr e)
              [:cmp (ops op) field (parse-number n)])))))))

(defn- parse-v1 [expr]
  (let [i (skip-ws expr 0)
        c (get expr i)]
    (cond
      (nil? c) (bad expr 0 "empty expression")
      (str/starts-with? (subs expr i) "has(") (parse-has expr i)
      (= \" c) (parse-in expr i)
      :else (if-let [[field end] (scan expr i ident-re)]
              (parse-after-ident expr field end)
              (bad expr i "unrecognised predicate")))))

;; ------------------------------------------------------ EDN vector syntax

(defn- edn-ws? [c] (or (Character/isWhitespace ^char c) (= \, c)))

(defn- skip-edn-ws [s i]
  (loop [i i] (if (and (< i (count s)) (edn-ws? (nth s i))) (recur (inc i)) i)))

(declare read-node)

(defn- read-vec [expr i]
  (loop [j (inc i) kids []]
    (let [j (skip-edn-ws expr j)]
      (cond
        (>= j (count expr)) (bad expr i "unterminated vector")
        (= \] (nth expr j)) [{:kind :vec :v kids :pos i} (inc j)]
        :else (let [[node e] (read-node expr j)] (recur e (conj kids node)))))))

(defn- classify [tok pos]
  (cond
    (str/starts-with? tok ":") {:kind :kw :v (subs tok 1) :pos pos}
    (re-matches number-re tok) {:kind :num :v (parse-number tok) :pos pos}
    (#{"true" "false" "nil"} tok) {:kind :lit :v tok :pos pos}
    :else {:kind :sym :v tok :pos pos}))

(defn- read-node [expr i]
  (let [c (nth expr i)]
    (cond
      (= \[ c) (read-vec expr i)
      (= \" c) (if-let [[raw e] (scan expr i literal-re)]
                 [{:kind :str :v (unquote-literal raw) :pos i} e]
                 (bad expr i "unterminated string literal"))
      (contains? #{\] \) \( \{ \} \# \' \; \^ \@ \~ \` \\} c) (bad expr i (str "unexpected " (pr-str (str c))))
      :else (let [e (or (first (keep (fn [j] (when (let [d (nth expr j)] (or (edn-ws? d) (#{\[ \] \"} d))) j))
                                     (range i (count expr))))
                        (count expr))]
              [(classify (subs expr i e) i) e]))))

(defn- name-node
  "The text of a field/symbol-like node."
  [expr node what]
  (if (and (#{:sym :str :kw} (:kind node)) (seq (:v node)))
    (:v node)
    (bad expr (:pos node) (str "expected " what))))

(defn- str-node [expr node what]
  (if (= :str (:kind node)) (:v node) (bad expr (:pos node) (str "expected " what))))

(defn- arity! [expr node args n head]
  (when (not= n (count args))
    (bad expr (:pos node) (str head " takes " n " argument" (when (not= n 1) "s")))))

(defn- build [expr node]
  (when-not (= :vec (:kind node)) (bad expr (:pos node) "expected a [:form ...] vector"))
  (let [[h & args] (:v node)]
    (when-not h (bad expr (:pos node) "empty vector"))
    (when-not (= :kw (:kind h)) (bad expr (:pos h) "expected a keyword head"))
    (case (:v h)
      "has" (do (arity! expr node args 1 ":has") [:has (name-node expr (first args) "a field name")])
      "in" (do (arity! expr node args 2 ":in")
               [:in (str-node expr (first args) "a string")
                (name-node expr (second args) "a field name")])
      "re" (do (arity! expr node args 2 ":re")
               (let [pn (second args) pat (str-node expr pn "a regex string")]
                 (check-regex! expr (:pos pn) pat)
                 [:re (name-node expr (first args) "a field name") pat]))
      "model<=" (do (arity! expr node args 1 ":model<=") [:model<= (name-node expr (first args) "a model name")])
      "cmp" (do (arity! expr node args 3 ":cmp")
                (let [[o f v] args
                      op (ops (when (#{:sym :str :kw} (:kind o)) (:v o)))]
                  (when-not op (bad expr (:pos o) "expected one of = != >= <="))
                  (when-not (#{:str :num} (:kind v)) (bad expr (:pos v) "expected a string or number"))
                  [:cmp op (name-node expr f "a field name") (:v v)]))
      ("and" "or") (do (when (empty? args) (bad expr (:pos node) (str (:v h) " needs at least one argument")))
                       (into [(keyword (:v h))] (map #(build expr %)) args))
      "not" (do (arity! expr node args 1 ":not") [:not (build expr (first args))])
      (bad expr (:pos h) (str "unknown form :" (:v h))))))

(defn- parse-edn [expr]
  (let [i (skip-edn-ws expr 0)
        [node end] (read-node expr i)
        j (skip-edn-ws expr end)]
    (when (< j (count expr)) (bad expr j "unexpected trailing text"))
    (build expr node)))

(defn parse
  "Parses a where expression (v1 string syntax or an EDN vector as a string)
   into the closed AST. Raises `:query/bad-where` carrying `:position` (a
   0-based character offset into `expr`) and `:expression`."
  [expr]
  (when-not (string? expr) (bad (str expr) 0 "a where expression must be a string"))
  (let [i (skip-edn-ws expr 0)]
    (if (= \[ (get expr i)) (parse-edn expr) (parse-v1 expr))))

;; -------------------------------------------------------------- evaluation

(defn- lookup
  "`[found? value]` of field `f` in `payload`: the literal key first, then a
   dotted path through maps and list indexes (`tasks.0.title`)."
  [payload f]
  (let [direct (fn [m k] (cond (contains? m k) [true (get m k)]
                               (contains? m (keyword k)) [true (get m (keyword k))]))]
    (or (direct payload f)
        (when (str/includes? f ".")
          (loop [v payload [p & ps] (str/split f #"\.")]
            (let [step (cond (map? v) (direct v p)
                             (and (sequential? v) (re-matches #"\d+" p) (< (parse-long p) (count v)))
                             [true (nth v (parse-long p))])]
              (cond (nil? step) nil
                    (nil? ps) step
                    :else (recur (second step) ps)))))
        [false nil])))

(defn- present? [payload f]
  (let [[found v] (lookup payload f)] (and found (not (nil? v)) (not= "" v))))

(defn- py-str
  "`str(value)` as v1 saw it for the regex target."
  [v]
  (cond (nil? v) "None" (true? v) "True" (false? v) "False" :else (str v)))

(defn- same? [a b] (if (and (number? a) (number? b)) (== a b) (= a b)))

(defn- ordered
  "`(op a b)` for `>=`/`<=` when both are numbers or both strings, else false
   (v1 raised TypeError there)."
  [op a b]
  (and (or (and (number? a) (number? b)) (and (string? a) (string? b)))
       (let [c (compare a b)] (if (= op :>=) (>= c 0) (<= c 0)))))

(defn- cmp [payload op f v]
  (let [[found x] (lookup payload f)]
    (if (and (string? v) (not found))
      false
      (case op
        := (same? x v)
        :!= (not (same? x v))
        (ordered op x v)))))

(defn matches?
  "True when `doc` `{:name :model :bases :payload :tags}` satisfies `ast`
   (as returned by `parse`). An absent field matches neither `=` nor `!=`
   against a string literal; `has` is false for absent, nil and \"\"."
  [ast doc]
  (let [payload (or (:payload doc) {})
        [tag a b c] ast]
    (case tag
      :has (boolean (present? payload a))
      :in (let [[_ v] (lookup payload b)]
            (cond (string? v) (and (string? a) (str/includes? v a))
                  (sequential? v) (boolean (some #(same? a %) v))
                  :else false))
      :re (let [target (if (= a "doc")
                         (:name doc)
                         (let [[found v] (lookup payload a)] (if found (py-str v) "")))]
            (some? (re-find (re-pattern b) (or target ""))))
      :model<= (boolean (or (= a (:model doc)) (some #{a} (:bases doc))))
      :cmp (cmp payload a b c)
      :and (every? #(matches? % doc) (rest ast))
      :or (boolean (some #(matches? % doc) (rest ast)))
      :not (not (matches? a doc)))))
