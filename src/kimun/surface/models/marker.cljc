(ns kimun.surface.models.marker
  "Markers of a model template (docs/v2/07 §4): `⸢kind[,trait…]•field[|trait…]⸥`.
   Both spellings are accepted — v1's `⸢rev,list•items⸥` and 07's
   `⸢rev•items|list⸥` — and parse to the same `{:kind :traits :name}`."
  (:require [clojure.string :as str]))

(def pattern
  "Regex matching one marker; group 1 is its inner text."
  #"⸢([^⸥]+)⸥")

(def token-pattern
  "Regex matching a marker or a v1 inline render expression `{{ … }}`."
  #"⸢([^⸥]+)⸥|\{\{\s*.*?\s*\}\}")

(defn- split-head [head]
  (loop [cs (seq head) depth 0 cur [] parts []]
    (if-let [c (first cs)]
      (let [depth' (cond (= c \[) (inc depth) (and (= c \]) (pos? depth)) (dec depth) :else depth)]
        (if (and (= c \,) (zero? depth'))
          (recur (rest cs) depth' [] (conj parts (apply str cur)))
          (recur (rest cs) depth' (conj cur c) parts)))
      (->> (conj parts (apply str cur)) (map str/trim) (remove str/blank?) vec))))

(defn parse
  "`{:kind \"rev\"|\"optrev\"|\"render\"|\"py\" :traits [..] :name field}` from
   the inner text of a marker (what sits between `⸢` and `⸥`)."
  [inner]
  (let [[head prop] (str/split inner #"•" 2)
        [name & tail] (str/split (or prop "") #"\|")
        parts (split-head head)]
    {:kind (or (first parts) "rev")
     :traits (vec (concat (rest parts) (remove str/blank? (map str/trim tail))))
     :name (str/trim (or name ""))}))

(defn data?
  "True for a marker that carries a field in both directions (`rev`/`optrev`)."
  [m] (contains? #{"rev" "optrev"} (:kind m)))

(defn optional?
  "True for an `optrev` marker."
  [m] (= "optrev" (:kind m)))

(defn trait?
  "True when marker `m` has trait `t` (`table[a,b]` counts as `table`)."
  [m t] (boolean (some #(or (= t %) (str/starts-with? % (str t "["))) (:traits m))))

(defn table-columns
  "Columns named by a `table[a,b]` trait, `[]` for a bare `table`, nil otherwise."
  [m]
  (some (fn [t] (cond (= t "table") []
                      (and (str/starts-with? t "table[") (str/ends-with? t "]"))
                      (vec (remove str/blank? (map str/trim (str/split (subs t 6 (dec (count t))) #","))))))
        (:traits m)))

(defn- quote-lit [s] (if (seq s) (java.util.regex.Pattern/quote s) ""))

(defn text-pattern
  "Compiles template text into `{:props [marker…] :regex str :dynamic? bool}`:
   literal text escaped, one `(.*?)` group per data marker, `.*?` for
   render-only tokens."
  [content]
  (let [m (re-matcher token-pattern content)]
    (loop [cursor 0 parts [] props [] dynamic? false]
      (if (.find m)
        (let [tok (.group m) lit (subs content cursor (.start m))
              mk (when-not (str/starts-with? tok "{{") (parse (.group m 1)))
              data (and mk (data? mk))]
          (recur (.end m)
                 (conj parts (quote-lit lit) (if data "(.*?)" ".*?"))
                 (if data (conj props mk) props) true))
        {:props props :dynamic? dynamic?
         :regex (str "^" (apply str (conj parts (quote-lit (subs content cursor)))) "$")}))))
