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

(defn dec-count [db id]
  (if-let [[id count] (first (d/q '[:find ?e ?count
                                    :in $ ?e
                                    :where
                                    [?e :item/count ?count]]
                                  db
                                  id))]
    [[:db/add id :item/count (max (dec count) 0)]]
    (throw (ex-info (str "Entity " id " does not exist")
                    {:id id}))))
