(ns ion-shopping-list.core
  (:require [datomic.ion.lambda.api-gateway :as api-gateway]
            [datomic.client.api :as d]
            [cognitect.transit :as transit]
            [datomic.ion.cast :as cast]
            [clojure.data.json :as json])
  (:import [java.text Collator]))

(def db-name "shopping-list")

(def db-spec
  {:server-type :ion
   :region "eu-west-1"
   :system "ions-demo"
   :endpoint "http://entry.ions-demo.eu-west-1.datomic.net:8182"
   :proxy-port 8182})

(def get-client
  (memoize #(d/client db-spec)))

(def items-query
  '[:find ?e ?name ?count
    :in $
    :where
    [?e :item/name ?name]
    [?e :item/count ?count]])

(defn transit-string [data]
  (let [out (java.io.ByteArrayOutputStream.)
        writer (transit/writer out :json)]
    (transit/write writer data)
    (.toString out "UTF-8")))

(def transit?
  (fnil #(.contains % "transit") ""))

(defn serialize-as-accepted [data accept]
  (if (transit? accept)
    [(transit-string data) "application/transit+json"]
    [(json/write-str data) "application/json"]))

(defn get-items-handler [{:as request :keys [headers]}]
  (cast/event {:msg "Getting items"
               :db db-name})
  (let [connection (d/connect (get-client) {:db-name db-name})
        db (d/db connection)
        items (d/q items-query db)
        [body content-type] (serialize-as-accepted items (get headers "accept"))]
    {:status 200
     :headers {"content-type" content-type}
     :body body}))

(def get-items
  (api-gateway/ionize get-items-handler))

(defn deserialize [body content-type]
  (if (transit? content-type)
    (-> (transit/reader body :json)
        (transit/read))
    (json/read-str (slurp body) :key-fn keyword)))

(defn add-item-handler [{:as request :keys [body headers]}]
  (let [{:keys [item-name]} (deserialize body (get headers "content-type"))
        _ (cast/event {:msg (str "Adding " item-name)
                       :event-type :add-item
                       :item-name item-name})
        connection (d/connect (get-client) {:db-name db-name})
        {:keys [db-after] :as tx-result} (d/transact connection
                                                     {:tx-data [['ion-shopping-list.txfn/inc-count item-name]]})
        items (d/q items-query db-after)
        [body content-type] (serialize-as-accepted items (get headers "accept"))]
    {:status 200
     :headers {"content-type" content-type}
     :body body}))

(def add-item
  (api-gateway/ionize add-item-handler))

(defn buy-item-handler [{:as request :keys [body headers]}]
  (let [{:keys [item-id]} (deserialize body (get headers "content-type"))
        _ (cast/event {:msg "Mark item as bought"
                       :item-id item-id})
        connection (d/connect (get-client) {:db-name db-name})
        {:keys [db-after] :as tx-result} (d/transact connection
                                                     {:tx-data [['ion-shopping-list.txfn/dec-count item-id]]})
        items (d/q items-query db-after)
        [body content-type] (serialize-as-accepted items (get headers "accept"))]
    {:status 200
     :headers {"content-type" content-type}
     :body body}))

(def buy-item
  (api-gateway/ionize buy-item-handler))
