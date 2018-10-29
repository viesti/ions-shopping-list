(ns user
  (:require [cider-nrepl.main :as nrepl]
            [clojure.java.io :as io]
            [clojure.edn :as edn]
            [cfn]
            [amazonica.aws.cloudformation :as aws-cfn]
            [amazonica.aws.lambda :as aws-lambda]
            [amazonica.aws.apigateway :as aws-apigw]
            [datomic.client.api :as d]
            [ion-shopping-list.core :as core]
            [ion-shopping-list.txfn :as txfn]
            [datomic.ion.dev :as dev]))

(def compute-group "tiuhti-Compute-CS1UK5K6HXWP")

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
                {:uri (cfn/->Sub (str "arn:aws:apigateway:${AWS::Region}:lambda:path/2015-03-31/functions/arn:aws:lambda:${AWS::Region}:${AWS::AccountId}:function:${ComputeGroup}-"
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
                :FunctionName (cfn/->Sub (str "${ComputeGroup}-" (name lambda)))
                :Principal "apigateway.amazonaws.com"
                :SourceArn #cfn.Sub{:value "arn:aws:execute-api:${AWS::Region}:${AWS::AccountId}:${Api}/*/*/*"}}}])))

(def template
  "Cloudformation template for making API Gateway for Datomic Ions Lambdas"
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
                    {:RestApiId #cfn.Ref{:value "Api"}
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
  [db-name]
  (let [client (d/client core/db-spec)]
    (d/create-database client {:db-name db-name})
    (d/transact (d/connect client {:db-name db-name})
                {:tx-data (-> (io/resource "schema.edn")
                              (slurp)
                              (edn/read-string))})))

(defn update-stack []
  (aws-cfn/update-stack :stack-name "shopping-list-api"
                        :template-body (cfn/generate-string template)
                        :parameters [{:parameter-key "ComputeGroup"
                                      :parameter-value compute-group}]))

(defn deploy-api []
  (aws-apigw/create-deployment :rest-api-id (-> (aws-cfn/describe-stack-resource :stack-name "shopping-list-api" :logical-resource-id "Api")
                                                :stack-resource-detail
                                                :physical-resource-id)
                               :stage-name "dev"))

(comment
  #_(aws-cfn/create-stack :stack-name "shopping-list-api"
                        :template-body (cfn/generate-string template)
                        :parameters [{:parameter-key "ComputeGroup"
                                      :parameter-value compute-group}])

  (aws-cfn/update-stack :stack-name "shopping-list-api"
                        :template-body (cfn/generate-string template)
                        :parameters [{:parameter-key "ComputeGroup"
                                      :parameter-value compute-group}]))
