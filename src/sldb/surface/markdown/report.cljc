(ns sldb.surface.markdown.report
  "Addressability report of a document AST (docs/v2/04 §9, docs/v2/01 §4.7):
   how much of the document is operable and exactly which regions are opaque."
  (:require [sldb.kernel.ports :as ports]))

(defn- gcount [host s] (count (ports/graphemes (ports/segmenter host) s)))

(defn report
  "{:blocks :structural :opaque :coverage :opaque-regions [{:path :format :graphemes}]}."
  [host ast]
  (let [acc (atom {:blocks 0 :structural 0 :opaque 0 :text-g 0 :opaque-g 0 :regions []})
        walk (fn walk [b path]
               (swap! acc update :blocks inc)
               (if (= :opaque (:type b))
                 (let [g (gcount host (:blob b))]
                   (swap! acc #(-> % (update :opaque inc) (update :opaque-g + g)
                                   (update :regions conj {:path path :format (:format b) :graphemes g}))))
                 (do (swap! acc update :structural inc)
                     (when (:text b) (swap! acc update :text-g + (gcount host (:text b))))
                     (doseq [[i c] (map-indexed vector (:children b))] (walk c (conj path i))))))]
    (doseq [[i c] (map-indexed vector (:children ast))] (walk c [i]))
    (let [{:keys [blocks structural opaque text-g opaque-g regions]} @acc
          total (+ text-g opaque-g)]
      {:blocks blocks :structural structural :opaque opaque
       :coverage (if (zero? total) 1.0 (double (/ text-g total)))
       :opaque-regions regions})))
