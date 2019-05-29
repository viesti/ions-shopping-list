(ns user
  (:require [cider-nrepl.main :as nrepl]
            [clojure.java.io :as io]
            [clojure.edn :as edn]
            [cfn-yaml.core :as cfn]
            [cfn-yaml.tags.api :refer :all]
            [datomic.client.api :as d]
            [ion-shopping-list.core :as core]
            [ion-shopping-list.txfn :as txfn]
            [datomic.ion.dev :as ion-dev]
            [cognitect.aws.client.api :as aws]
            [datomic.client.api :as d]
            [clojure.tools.namespace.repl :refer [refresh]]
            [clj-http.client :as http]))

(def compute-group "ions-demo-compute")

(defn start-nrepl-server
  "Start Nrepl server for use with Cider"
  []
  (nrepl/init ["cider.nrepl/cider-middleware"]))

(defn api-lambdas
  "Returns Lambda keywords that with :api-gateway/proxy integration"
  [ion-config]
  (keep (fn [[k v]]
          (when (= :api-gateway/proxy (:integration v))
            k))
        (:lambdas ion-config)))

(defn make-paths
  "Creates API Gateway paths from Datomic Ions Config"
  [ion-config]
  (into {} (for [lambda (api-lambdas ion-config)]
             [(str "/" (name lambda))
              {:x-amazon-apigateway-any-method
               {:responses {}
                :x-amazon-apigateway-integration
                {:uri (!Sub (str "arn:aws:apigateway:${AWS::Region}:lambda:path/2015-03-31/functions/arn:aws:lambda:${AWS::Region}:${AWS::AccountId}:function:${ComputeGroup}-"
                                 (name lambda)
                                 "/invocations"))
                 :responses {:default {:statusCode "200"}}
                 :passthroughBehavior "when_no_match"
                 :httpMethod "POST"
                 :contentHandling "CONVERT_TO_TEXT"
                 :type "aws_proxy"}}}])))

(defn invoke-permissions [ion-config]
  (into {} (for [lambda (api-lambdas ion-config)]
             [(-> lambda name (clojure.string/replace "-" "") (str "InvokePermission") keyword)
              {:Type "AWS::Lambda::Permission"
               :Properties
               {:Action "lambda:InvokeFunction"
                :FunctionName (!Sub (str "${ComputeGroup}-" (name lambda)))
                :Principal "apigateway.amazonaws.com"
                :SourceArn (!Sub "arn:aws:execute-api:${AWS::Region}:${AWS::AccountId}:${Api}/*/*/*")}}])))

(defn create-api-template
  "Cloudformation template for making API Gateway for Datomic Ions Lambdas"
  []
  (let [ion-config (edn/read-string (slurp (io/resource "datomic/ion-config.edn")))
        resources {:Api
                   {:Type "AWS::ApiGateway::RestApi"
                    :Properties
                    {:Body
                     {:swagger "2.0"
                      :info {:title "ShoppingList"}
                      :basePath "/dev"
                      :schemes ["https"]
                      :paths (make-paths ion-config)
                      :x-amazon-apigateway-binary-media-types ["*/*"]}}}
                   :DevStage
                   {:Type "AWS::ApiGateway::Deployment"
                    :Properties
                    {:RestApiId (!Ref "Api")
                     :StageName "dev"}}}
        resources (merge resources (invoke-permissions ion-config))]
    {:AWSTemplateFormatVersion "2010-09-09"
     :Description "Shopping List API"
     :Parameters
     {:ComputeGroup {:Type "String"}}
     :Resources resources}))

(def system-ns
  "System namespaces, not used by clients"
  #{"db" "db.type" "db.install" "db.part"
    "db.lang" "fressian" "db.unique" "db.excise"
    "db.cardinality" "db.fn"
    "db.alter" "db.bootstrap" "db.sys"})

(defn get-schema
  "Find out schema of the database (excludes system names)"
  [db]
  (d/q '[:find ?e ?ident
         :in $ ?system-ns
         :where
         [?e :db/ident ?ident]
         [(namespace ?ident) ?ns]
         [((comp not contains?) ?system-ns ?ns)]]
       db system-ns))

(defn migrate
  "Transacts schema to a database"
  ([client]
   (migrate client "shopping-list"))
  ([client db-name]
   (d/create-database client {:db-name db-name})
   (d/transact (d/connect client {:db-name db-name})
               {:tx-data (-> (io/resource "schema.edn")
                             (slurp)
                             (edn/read-string))})))

(def cfn-client (aws/client {:api :cloudformation}))
(def apigw-client (aws/client {:api :apigateway}))

(defn push []
  (ion-dev/push {:uname "dev"}))

(defn deploy []
  (ion-dev/deploy {:app-name "ions-demo"
                   :uname "dev"
                   :group compute-group
                   :description "repl deploy"}))

(defn deploy-status [m]
  (println (ion-dev/deploy-status m))
  m)

(defn create-api-stack []
  (aws/invoke cfn-client
              {:op :CreateStack
               :request {:StackName "shopping-list-api"
                         :TemplateBody (cfn/generate-string (create-api-template))
                         :Parameters [{:ParameterKey "ComputeGroup"
                                       :ParameterValue compute-group}]}}))

(defn update-api-stack []
  (aws/invoke cfn-client
              {:op :UpdateStack
               :request {:StackName "shopping-list-api"
                         :TemplateBody (cfn/generate-string (create-api-template))
                         :Parameters [{:ParameterKey "ComputeGroup"
                                       :ParameterValue compute-group}]}}))

(def get-api-id
  (memoize (fn []
             (-> cfn-client
                 (aws/invoke {:op :DescribeStackResource
                              :request {:StackName "shopping-list-api"
                                        :LogicalResourceId "Api"}})
                 :StackResourceDetail
                 :PhysicalResourceId))))

(defn deploy-api []
  (aws/invoke apigw-client
              {:op :CreateDeployment
               :request {:restApiId (get-api-id)
                         :stageName "dev"}}))

(defn create-client []
  (d/client core/db-spec))

(defn base-url []
  (format "https://%s.execute-api.%s.amazonaws.com/dev"
          (get-api-id)
          (.toLowerCase (System/getenv "AWS_REGION"))))

(comment
  (def client (create-client))
  (def connection (d/connect client {:db-name core/db-name}))
  (def db (d/db connection))

  (-> (str (base-url) "/get-items")
      (http/get {:as :json})
      :body)

  (-> (str (base-url) "/add-item")
      (http/post {:content-type :transit+json
                  :form-params {:item-name "Maitoa"}})
      :body))
