(ns ion-shopping-list.core
  (:require [datomic.ion.lambda.api-gateway :as api-gateway]
            [datomic.client.api :as d]
            [cognitect.transit :as transit]
            [datomic.ion.cast :as cast])
  (:import [java.text Collator]))

(def db-name "testing")

(def db-spec
  {:server-type :ion
   :region "eu-west-1"
   :system "tiuhti"
   :endpoint "http://entry.tiuhti.eu-west-1.datomic.net:8182"
   :proxy-port 8182})

(def get-client
  (memoize #(d/client db-spec)))

(defn query-items [db]
  (d/q '[:find ?name ?count
         :in $
         :where
         [?e :item/name ?name]
         [?e :item/count ?count]]
       db))

(defn transit-string [data]
  (let [out (java.io.ByteArrayOutputStream.)
        writer (transit/writer out :json)]
    (transit/write writer data)
    (.toString out "UTF-8")))

(defn get-items-handler [_]
  {:status 200
   :headers {"content-type" "application/transit+json"
             "x-lol" "foo"
             "x-moi" "bar"}
   :body (transit-string (query-items (d/db (d/connect (get-client) {:db-name db-name}))))})

(def get-items
  (api-gateway/ionize get-items-handler))

(defn add-item-handler [{:keys [body]}]
  (let [reader (transit/reader body :json)
        {:keys [item-name]} (transit/read reader)
        _ (cast/event {:msg (str "Adding " item-name)})
        {:keys [db-after] :as tx-result} (d/transact (d/connect (get-client) {:db-name db-name})
                                                     {:tx-data [['ion-shopping-list.txfn/inc-count item-name]]})]
    {:status 200
     :headers {"content-type" "application/transit+json"}
     :body (transit-string (query-items db-after))}))

(def add-item
  (api-gateway/ionize add-item-handler))
