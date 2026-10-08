(ns kimun.host.lazy
  "The LazyMaps port (kimun.kernel.ports) for Babashka/JVM: a persistent map
   whose values come from a loader on first use. Each map lineage (a map and
   everything derived from it through assoc/dissoc) memoises what its loader
   returned. This memo is NOT the object cache of a store: that one lives in
   the Backend adapter (kimun.host.fs-store, the kernel's `ObjectCache` port),
   so the :objects loader hands out the adapter's already-parsed values."
  (:require [kimun.kernel.ports :as ports]))

#?(:clj
   (defn- lazy-map*
     [lookup ks memo overlay removed mta]
     (let [base (fn [k]
                  (when-not (contains? removed k)
                    (let [m @memo]
                      (if (contains? m k)
                        (get m k)
                        (let [v (lookup k)] (swap! memo assoc k v) v)))))
           value (fn [k not-found]
                   (if (contains? overlay k)
                     (get overlay k)
                     (let [v (base k)] (if (nil? v) not-found v))))
           all-keys (fn [] (concat (keys overlay)
                                   (remove #(or (contains? overlay %) (contains? removed %)) (distinct (seq (ks))))))
           entries (fn [] (seq (map (fn [k] (clojure.lang.MapEntry. k (value k nil))) (all-keys))))
           with (fn [k v] (lazy-map* lookup ks memo (assoc overlay k v) removed mta))]
       (proxy [clojure.lang.APersistentMap clojure.lang.IMeta clojure.lang.IObj] []
         (valAt ([k] (value k nil)) ([k not-found] (value k not-found)))
         (containsKey [k] (or (contains? overlay k) (some? (base k))))
         (entryAt [k] (let [v (value k ::absent)] (when-not (= ::absent v) (clojure.lang.MapEntry. k v))))
         (assoc [k v] (with k v))
         (assocEx [k v] (with k v))
         (without [k] (lazy-map* lookup ks memo (dissoc overlay k) (conj removed k) mta))
         (count [] (count (all-keys)))
         (seq [] (entries))
         (iterator [] (.iterator ^java.lang.Iterable (vec (entries))))
         (empty [] {})
         (cons [o] (if (map? o) (reduce (fn [m [k v]] (assoc m k v)) this o) (with (nth o 0) (nth o 1))))
         (meta [] mta)
         (withMeta [m] (lazy-map* lookup ks memo overlay removed m))))))

(def maps
  "The LazyMaps of this platform; nil where there is none (the kernel then
   builds eager maps)."
  #?(:clj (reify ports/LazyMaps
            (lazy-map [_ lookup ks overlay] (lazy-map* lookup ks (atom {}) (or overlay {}) #{} nil)))
     :default nil))
