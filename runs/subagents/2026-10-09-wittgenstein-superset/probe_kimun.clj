(require '[kimun.host.default :as host]
         '[kimun.kernel.node :as node]
         '[kimun.kernel.revision :as rev])
(def h host/host)
(def ts "2026-10-09T12:00:00.000Z")
(def base
  {:plan/version 1 :base nil :actor "review" :engines {} :timestamp ts
   :ops [{:op :add-node :node {:class :sign :kind :block :content {:format :markdown :type :document :attrs {}}} :as :root}
         {:op :add-node :node {:class :sign :kind :text :content {:text "igual"}} :as :old}
         {:op :add-node :node {:class :sign :kind :text :content {:text "destino"}} :as :target}
         {:op :new-tree :tree {:kind :document :name "A"} :id "01ARZ3NDEKTSV4RRFFQ69G5FAV" :root :root :as :a}
         {:op :new-tree :tree {:kind :document :name "B"} :id "01ARZ3NDEKTSV4RRFFQ69G5FAW" :root :root :as :b}
         {:op :add-edge :edge {:type :ownership :tree :a :parent [] :to :old :order 0}}
         {:op :add-edge :edge {:type :ownership :tree :b :parent [] :to :old :order 0}}
         {:op :add-edge :edge {:type :reference :from :target :to :old :evidence {:actor "review" :ref-hash :old}}}]} )
(let [{:keys [store aliases]} (rev/apply-plan (rev/empty-store h {"review" :all}) base)
      result (rev/apply-plan store {:plan/version 1 :base (:head store) :actor "review" :engines {} :timestamp ts
                                   :ops [{:op :add-node :node {:class :sign :kind :text :content {:text "editado"}} :as :new}
                                         {:op :replace :tree (:a aliases) :at [0] :new :new}]})
      s (:store result)
      refs (filter #(= :reference (:type %)) (map #(get-in s [:objects %]) (rev/active-edges s)))]
  (prn {:probe :replace-shared-node :old (:old aliases) :new (get-in result [:aliases :new])
        :references (mapv #(select-keys % [:from :to]) refs)
        :note "Reference successor created without any occurrence qualifier."}))
(try
  (node/make h :symbol :proposition {:form ["if" ["r" "a" "b"] ["r" "a" "c"]]})
  (prn {:probe :nested-proposition :accepted true})
  (catch Exception e (prn {:probe :nested-proposition :accepted false :error (:type (ex-data e))})))
(prn {:probe :context-free-term
      :same-id (= (:id (node/make h :symbol :term {:name "she" :lang "en"}))
                  (:id (node/make h :symbol :term {:name "she" :lang "en"})))
      :note "Default shape requires name/lang, no declaration context."})
