(ns sldb.kernel.generators
  "Shared test.check generators for the kernel (docs/v2/02 §8.1). Created in
   milestone 0 (`gen-node`), extended by later milestones."
  (:require [clojure.test.check.generators :as gen]
            [sldb.host.default :as host]
            [sldb.kernel.node :as node]
            [sldb.kernel.edge]
            [sldb.kernel.tree]
            [sldb.kernel.ports]
            [sldb.surface.markdown.inline]
            [clojure.string]))

(def hasher host/host)

(def gen-text
  "Strings including combining characters, so NFC matters."
  (gen/one-of [gen/string
               (gen/fmap #(str "\u00e9" %) gen/string-alphanumeric)        ; NFC
               (gen/fmap #(str "e\u0301" %) gen/string-alphanumeric)]))    ; NFD

(def gen-atomic
  (gen/one-of [gen-text gen/large-integer gen/boolean (gen/return nil) gen/keyword]))

(def gen-content-value
  "Arbitrary canonical-admissible content (for canon tests)."
  (gen/recursive-gen
   (fn [inner]
     (gen/one-of [(gen/vector inner 0 4)
                  (gen/map gen/keyword inner {:max-elements 4})
                  (gen/set inner {:max-elements 4})]))
   gen-atomic))

(def gen-hex (gen/fmap #(apply str %) (gen/vector (gen/elements "0123456789abcdef") 64)))

(def gen-content-by-kind
  {[:sign :text]           (gen/fmap (fn [t] {:text t}) gen-text)
   [:sign :block]          (gen/let [type (gen/elements [:heading :paragraph :list :code])
                                     level gen/nat]
                             {:format :markdown :type type :attrs {:level level}})
   [:sign :opaque]         (gen/let [b gen-text] {:format "html" :blob b})
   [:sign :external]       (gen/let [p gen/string-alphanumeric s gen-text f gen-hex]
                             {:locator {:kind :file :path p :page 3} :sample s :fingerprint f})
   [:sign :span]           (gen/let [leaf gen-hex a gen/nat d gen/nat] {:leaf leaf :range [a (+ a d)]})
   [:symbol :term]         (gen/let [n gen-text l (gen/elements ["es" "en" "de"])] {:name n :lang l})
   [:symbol :proposition]  (gen/let [p gen-hex args (gen/vector gen-atomic 0 3)] {:form (into [p] args)})
   [:fact :triple]         (gen/let [s gen-hex p gen-hex o (gen/one-of [gen-hex gen-atomic]) c gen-hex]
                             {:subject s :predicate p :object o :context c})
   [:fact :context]        (gen/fmap (fn [n] {:name n}) gen-text)})

(def all-kinds (vec (keys gen-content-by-kind)))

(def gen-node
  "A validated node covering every class/kind row of docs/v2/02 §2.1."
  (gen/let [[class kind] (gen/elements all-kinds)
            content (get gen-content-by-kind [class kind])]
    (node/make hasher class kind content)))

(defn gen-node-of [class kind]
  (gen/fmap #(node/make hasher class kind %) (get gen-content-by-kind [class kind])))

;; ---------------------------------------------------------------- milestone 1

(def gen-hex64 gen-hex)

(def gen-origin
  (gen/one-of [(gen/fmap (fn [a] {:actor a}) gen/string-alphanumeric)
               (gen/let [e gen/string-alphanumeric v (gen/elements ["1" "2.0" "3"])] {:engine e :version v})]))

(def gen-edge
  "A validated non-ownership or ownership edge with the evidence its type requires."
  (gen/let [type (gen/elements [:ownership :supersedes :reference :binding :projection :semantic :derived])
            from gen-hex64 to gen-hex64 ctx gen-hex64 origin gen-origin
            status (gen/elements [:sinnvoll :sinnlos :unsinnig])
            order gen/nat]
    (let [ev (case type
               :ownership  nil
               :supersedes {:actor (or (:actor origin) "system")}
               :reference  (merge {:ref-hash to} origin)
               :binding    (merge {:ref-hash to :context ctx} origin)
               :projection (merge {:ref-hash to :context ctx :status status} origin)
               :semantic   (merge {:ref-hash to :context ctx} origin)
               :derived    {:ref-hash to :engine "parser" :version "1"})
          e (cond-> {:type type :from from :to to}
              (= type :ownership) (-> (assoc :tree "01ARZ3NDEKTSV4RRFFQ69G5FAV" :order order :parent []) (dissoc :from))
              ev (assoc :evidence ev))]
      (sldb.kernel.edge/make hasher e))))

(defn- text-id [s] (:id (node/make hasher :sign :text {:text s})))

(def gen-tree
  "A random valid :document tree of positions with 1..12 text nodes (some repeated),
   uncommitted."
  (gen/let [n (gen/choose 1 12)
            seed gen/nat]
    (let [ids (mapv #(text-id (str "n" seed "-" (mod % 5))) (range n))   ; mod 5 ⇒ repeated nodes
          root (first ids)]
      (loop [t (sldb.kernel.tree/create hasher :document "gen" root "01ARZ3NDEKTSV4RRFFQ69G5FAV")
             i 1
             rng seed]
        (if (>= i n)
          t
          (let [paths (mapv first (sldb.kernel.tree/positions t))
                parent (nth paths (mod rng (count paths)))
                siblings (sldb.kernel.tree/children-at t parent)
                order (mod (quot rng 7) (inc (count siblings)))]
            (recur (sldb.kernel.tree/add-child t parent (nth ids i) order)
                   (inc i)
                   (mod (+ (* (mod rng 65536) 1103515245) 12345 i) 4294967296))))))))

;; ---------------------------------------------------------------- milestone 2

(def gen-ulid
  (gen/fmap (fn [n] (str "01ARZ3NDEKTSV4RRFFQ69" (apply str (map #(nth "0123456789ABCDEFGHJKMNPQRSTVWXYZ" (mod (+ n %) 32)) (range 5)))))
            gen/nat))

(def gen-valid-plan
  "A valid first plan on an empty store: one document tree with 1..8 text
   children under the root and a binding to a term in a context."
  (gen/let [n (gen/choose 1 8)
            tid gen-ulid
            texts (gen/vector gen-text n)
            actor (gen/elements ["jp" "ana"])]
    (let [node-ops (map-indexed (fn [i t] {:op :add-node :node {:class :sign :kind :text :content {:text (str i "-" t)}} :as (keyword (str "p" i))}) texts)
          edge-ops (map (fn [i] {:op :add-edge :edge {:type :ownership :tree :t :parent [] :to (keyword (str "p" i)) :order i}}) (range n))]
      {:plan/version 1 :base nil :actor (if (= actor "ana") "jp" actor) :engines {} :timestamp "2026-08-29T12:00:00.000Z"
       :ops (-> [{:op :add-node :node {:class :sign :kind :block :content {:format :markdown :type :document :attrs {}}} :as :root}
                 {:op :add-node :node {:class :symbol :kind :term :content {:name "x" :lang "es"}} :as :sym}
                 {:op :add-node :node {:class :fact :kind :context :content {:name "w"}} :as :w}]
                (into node-ops)
                (conj {:op :new-tree :tree {:kind :document :name "g"} :id tid :root :root :as :t})
                (into edge-ops)
                (conj {:op :add-edge :edge {:type :binding :from :p0 :to :sym :evidence {:ref-hash :sym :actor "jp" :context :w}}}))})))

(def gen-plan-sequence
  "1..4 plans: a first valid plan followed by plans that add a text node under the
   root of the same tree. :base is filled in by the consumer."
  (gen/let [first-plan gen-valid-plan
            extra (gen/vector gen-text 0 3)]
    (let [tid (some :id (filter #(= :new-tree (:op %)) (:ops first-plan)))]
      (into [first-plan]
            (map-indexed (fn [i t] {:plan/version 1 :base nil :actor "jp" :engines {} :timestamp "2026-08-29T12:00:01.000Z"
                          :ops [{:op :add-node :node {:class :sign :kind :text :content {:text (str "extra-" i "-" t)}} :as :x}
                                {:op :add-edge :edge {:type :ownership :tree tid :parent [] :to :x :order 0}}]})
                 extra)))))

;; ---------------------------------------------------------------- milestone 4: canonical Markdown ASTs (docs/v2/04 §4.1)

(def ^:private md-alphabet
  ;; grapheme strings: letters, digits and every escapable / block-start character
  ["a" "b" "c" "x" "y" "z" "é" "0" "1" "9" "\\" "*" "_" "[" "]" "`" "<" "|" "#" ">" "-" "+" "~" "." "!" "(" ")" "=" ":" "{" "}" "👨‍👩‍👧"])

(def gen-md-word
  (gen/fmap #(apply str %) (gen/vector (gen/elements md-alphabet) 1 6)))

(def gen-md-text
  "Canonical paragraph/heading text: NFC, no leading/trailing space, no \\n/\\t, non-empty."
  (gen/fmap #(clojure.string/join " " %) (gen/vector gen-md-word 1 6)))

(defn- graphemes [s] (sldb.kernel.ports/graphemes (sldb.kernel.ports/segmenter hasher) s))

(defn- nonspace-at? [gs i] (and (< -1 i (count gs)) (not= " " (nth gs i))))

(defn- gen-marks-for
  "Marks over text with n graphemes: up to 3 disjoint outer marks separated by a
   gap, each optionally with one nested inner mark of another kind."
  [gs]
  (let [n (count gs)
        kinds [:emphasis :strong :code :link]
        mk (fn [k s e] (if (= k :link) [:link s e "u"] [k s e]))]
    (if (< n 1)
      (gen/return [])
      (gen/let [count-outer (gen/choose 0 3)
                spec (gen/vector (gen/tuple (gen/choose 0 (dec n)) (gen/choose 1 4) (gen/elements kinds)
                                            gen/boolean (gen/elements kinds) (gen/choose 0 3) (gen/choose 1 3))
                                 count-outer)]
        (let [marks (loop [items (sort-by first spec) cursor 0 acc []]
                      (if (empty? items)
                        acc
                        (let [[s len k inner? ik ioff ilen] (first items)
                              s (max s cursor) e (min n (+ s len))]
                          (if (or (>= s e) (not (nonspace-at? gs s)) (not (nonspace-at? gs (dec e)))
                                  (and (= k :code) (or (= "`" (nth gs s)) (= "`" (nth gs (dec e))))))
                            (recur (rest items) cursor acc)
                            (let [outer (mk k s e)
                                  is (+ s ioff) ie (min e (+ is ilen))
                                  inner (when (and inner? (not= k :code) (not= ik k)
                                                   (not (and (= k :link) (= ik :link)))
                                                   (< is ie) (nonspace-at? gs is) (nonspace-at? gs (dec ie))
                                                   (not (and (= is s) (= ie e) (= ik :code)))
                                                   (not (and (= k :emphasis) (= ik :strong) (or (= is s) (= ie e))))
                                                   (not (and (= ik :code) (or (= "`" (nth gs is)) (= "`" (nth gs (dec ie)))))))
                                          (mk ik is ie))]
                              (recur (rest items) (inc e) (cond-> (conj acc outer) inner (conj inner))))))))]
          (gen/return (vec (sort-by sldb.surface.markdown.inline/mark-key marks))))))))

(def gen-md-inline
  (gen/let [text gen-md-text
            marks (gen-marks-for (graphemes text))]
    {:text text :marks marks}))

(def gen-md-heading
  (gen/let [level (gen/choose 1 6) inl (gen/one-of [gen-md-inline (gen/return {:text "" :marks []})])]
    {:type :heading :attrs {:level level :marks (:marks inl)} :text (:text inl)}))

(def gen-md-paragraph
  (gen/fmap (fn [{:keys [text marks]}] {:type :paragraph :attrs {:marks marks} :text text}) gen-md-inline))

(def gen-md-code
  (gen/let [lines (gen/vector (gen/elements ["x = 1" "```" "````" "" "  indented" "~~~" "print(\"a\")"]) 0 4)
            lang (gen/elements [nil "python" "clj"])]
    {:type :code :attrs (if lang {:lang lang} {}) :text (clojure.string/join "\n" lines)}))

(def gen-md-opaque
  (gen/elements [{:type :opaque :format "markdown/html" :blob "<div class=\"w\">\n**x**\n</div>"}
                 {:type :opaque :format "markdown/table" :blob "| a | b |\n|---|---|"}
                 {:type :opaque :format "markdown/unparsed" :blob "see <b>bold</b> here"}
                 {:type :opaque :format "markdown/unparsed" :blob "ref [x][1] link"}]))

(def gen-md-break (gen/return {:type :thematic-break :attrs {}}))

(defn- no-adjacent-same-lists [blocks]
  (reduce (fn [acc b]
            (let [prev (peek acc)]
              (if (and prev (= :list (:type prev)) (= :list (:type b)) (= (get-in prev [:attrs :ordered]) (get-in b [:attrs :ordered])))
                acc
                (conj acc b))))
          [] blocks))

(def gen-md-block
  (gen/recursive-gen
   (fn [inner]
     (gen/one-of
      [(gen/fmap (fn [kids] {:type :quote :attrs {} :children (no-adjacent-same-lists kids)})
                 (gen/vector inner 1 3))
       (gen/let [ordered? gen/boolean start (gen/choose 1 12)
                 items (gen/vector (gen/tuple (gen/one-of [gen-md-paragraph gen-md-heading gen-md-code gen-md-opaque])
                                              (gen/vector inner 0 2))
                                   1 3)]
         {:type :list
          :attrs (if ordered? {:ordered true :start start} {:ordered false})
          :children (mapv (fn [[first-block more]] {:type :item :attrs {} :children (no-adjacent-same-lists (into [first-block] more))}) items)})]))
   (gen/one-of [gen-md-heading gen-md-paragraph gen-md-code gen-md-opaque gen-md-break])))

(def gen-ast
  "A canonical SLDB-MD document AST (docs/v2/04 §4.1)."
  (gen/fmap (fn [blocks] {:type :document :attrs {} :children (no-adjacent-same-lists blocks)})
            (gen/vector gen-md-block 0 6)))
