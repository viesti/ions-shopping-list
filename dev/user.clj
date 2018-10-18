(ns user
  (:require [cider-nrepl.main :as nrepl]
            [clojure.java.io :as io]
            [cfn]
            [amazonica.aws.cloudformation :as aws-cfn]))

(defn start-nrepl-server []
  (nrepl/init ["cider.nrepl/cider-middleware"]))

(def template
  {:AWSTemplateFormatVersion "2010-09-09"
   :Description "Shopping List API"
   :Parameters
   {:ComputeGroup {:Type "String"}}
   :Resources
   {:Api
    {:Type "AWS::ApiGateway::RestApi"
     :Properties
     {:Body
      {:swagger "2.0"
       :info {:title "ShoppingList"}
       :basePath "/dev"
       :schemes ["https"]
       :paths
       {"/get-items"
        {:get
         {:produces ["application/json"]
          :responses {}
          :x-amazon-apigateway-integration
          {:uri #cfn.Sub{:value "arn:aws:apigateway:${AWS::Region}:lambda:path/2015-03-31/functions/arn:aws:lambda:${AWS::Region}:${AWS::AccountId}:function:${ComputeGroup}-get-items/invocations"}
           :responses {:default {:statusCode "200"}}
           :passthroughBehavior "when_no_match"
           :httpMethod "POST"
           :contentHandling "CONVERT_TO_TEXT"
           :type "aws_proxy"}}}}
       :x-amazon-apigateway-binary-media-types ["*/*"]}}}
    :ApiInvokePermission
    {:Type "AWS::Lambda::Permission"
     :Properties
     {:Action "lambda:InvokeFunction"
      :FunctionName #cfn.Sub{:value "${ComputeGroup}-get-items"}
      :Principal "apigateway.amazonaws.com"
      :SourceArn #cfn.Sub{:value "arn:aws:execute-api:${AWS::Region}:${AWS::AccountId}:${Api}/*/*/*"}}}
    :DevStage
    {:Type "AWS::ApiGateway::Deployment"
     :Properties
     {:RestApiId #cfn.Ref{:value "Api"}
      :StageName "dev"}}}})

(comment
  (aws-cfn/create-stack :stack-name "shopping-list-api"
                        :template-body (cfn/generate-string template)
                        :parameters [{:parameter-key "ComputeGroup"
                                      :parameter-value "tiuhti-Compute-CS1UK5K6HXWP"}]))
