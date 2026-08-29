(ns sldb.kernel.generators
  "Shared test.check generators for the kernel (docs/v2/02 §8.1). Created in
   milestone 0 (`gen-node`), extended by later milestones."
  (:require [clojure.test.check.generators :as gen]
            [sldb.host.hash :as hash]
            [sldb.kernel.node :as node]))

(def hasher hash/sha-256)

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
