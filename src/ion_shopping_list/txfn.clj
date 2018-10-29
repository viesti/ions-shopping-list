(ns ion-shopping-list.txfn
  (:require [datomic.client.api :as d]))

(defn inc-count [db name]
  (if-let [[id count] (first (d/q '[:find ?e ?count
                                    :in $ ?name
                                    :where
                                    [?e :item/name ?name]
                                    [?e :item/count ?count]]
                                  db
                                  name))]
    [[:db/add id :item/count (inc count)]]
    [{:item/name name
      :item/count 1}]))
