(ns sldb.kernel.node
  "Nodes of the SMG pool (docs/v2/02 §2, §2.1).

   A node is {:class c :kind k :content m}. Its id is
   H(canonical-bytes {:class c :kind k :content m}); class and kind enter the
   hash, `address` does not (it is derived from class/kind). Provenance,
   timestamps and evidence never live in a node."
  (:require [sldb.kernel.canon :as canon]
            [sldb.kernel.err :as err]))

(def shapes
  "class → kind → predicate over content; one row per line of the §2.1 table."
  {:sign   {:text       (fn [c] (string? (:text c)))
            :block      (fn [c] (and (keyword? (:format c)) (keyword? (:type c)) (map? (:attrs c))))
            :opaque     (fn [c] (and (string? (:format c)) (string? (:blob c))))
            :external   (fn [c] (and (map? (:locator c)) (keyword? (:kind (:locator c)))
                                     (string? (:sample c)) (string? (:fingerprint c))))
            :span       (fn [c] (and (string? (:leaf c))
                                     (let [[a b] (:range c)]
                                       (and (vector? (:range c)) (= 2 (count (:range c)))
                                            (integer? a) (integer? b) (<= 0 a b)))))}
   :symbol {:term        (fn [c] (and (string? (:name c)) (string? (:lang c))))
            :proposition (fn [c] (and (vector? (:form c)) (seq (:form c)) (string? (first (:form c)))
                                      (every? #(or (string? %) (integer? %) (boolean? %) (nil? %) (keyword? %))
                                              (rest (:form c)))))}
   :fact   {:triple      (fn [c] (and (string? (:subject c)) (string? (:predicate c))
                                      (contains? c :object) (string? (:context c))))
            :context     (fn [c] (string? (:name c)))}})

(def classes
  "The three node classes: :sign (S), :symbol (M), :fact (G)."
  (set (keys shapes)))

(defn kinds
  "Admitted kinds of a class."
  [class] (set (keys (get shapes class))))

(defn address
  "Addressability class derived from kind (docs/v2/01 §4.7): :structural,
   :opaque or :external."
  [{:keys [kind]}]
  (case kind
    :opaque   :opaque
    :external :external
    :structural))

(defn- fail [node why]
  (err/raise :node/invalid (str "invalid node: " why) {:node node :why why}))

(defn validate
  "Returns the node when its class, kind and content shape are admitted and the
   content is canonical-admissible; raises :node/invalid otherwise."
  [host {:keys [class kind content] :as node}]
  (when-not (contains? shapes class) (fail node (str "unknown class " class)))
  (when-not (contains? (get shapes class) kind) (fail node (str "unknown kind " kind " for class " class)))
  (when-not (map? content) (fail node "content must be a map"))
  (when-not ((get-in shapes [class kind]) content) (fail node (str "content does not match shape " class "/" kind)))
  (when-not (canon/valid? host content) (fail node "content contains a value not admitted in canonical content"))
  node)

(defn identity-form
  "The exact value that is hashed: only class, kind and content."
  [{:keys [class kind content]}]
  {:class class :kind kind :content content})

(defn node-id
  "id(node) = H(canonical-bytes {:class :kind :content})."
  [host node]
  (canon/digest host (identity-form node)))

(defn make
  "Builds a validated, NFC-normalized node with its :id and derived :address."
  [host class kind content]
  (let [node (validate host {:class class :kind kind :content (canon/normalize host content)})]
    (assoc node :id (node-id host node) :address (address node))))
