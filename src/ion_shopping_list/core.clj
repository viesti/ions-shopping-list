(ns ion-shopping-list.core
  (:require [datomic.ion.lambda.api-gateway :as api-gateway]))

(defn get-items-handler [{:keys [input context]}]
  {:status 200
   :body "hello"})

(def get-items
  (api-gateway/ionize get-items-handler))
