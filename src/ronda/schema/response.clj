(ns ronda.schema.response
  (:require [ronda.schema.check :as c]
            [ronda.schema.data
             [errors :as e]
             [ring :as ring]
             [response :refer :all]]
            [schema.core :as s]))

(s/defn check-response
  "Check the given request/response pair against the given schema."
  ([responses :- Responses
    response  :- (s/maybe ring/Response)]
   (check-response responses response nil))
  ([rschema  :- ResponseSchema
    response :- (s/maybe ring/Response)
    request  :- (s/maybe ring/Request)]
   (let [{:keys [schema coercer constraint semantics]} rschema
         response' (c/check-response-with-coercion
                     coercer
                     schema
                     (or response {:status 404}))]
     (or (if (e/error? response') response')
         (c/check-response-constraint
           constraint
           response')
         (c/check-semantics
           semantics
           request
           response')
         (if response response')))))

(s/defn check
  "Check response against the given schemas (based on the response status),
   coercing if desired."
  ([responses :- Responses
    response  :- (s/maybe ring/Response)]
   (check responses response nil))
  ([responses :- Responses
    response  :- (s/maybe ring/Response)
    request   :- (s/maybe ring/Request)]
   (let [{:keys [status-schema schemas]} responses
         {:keys [status] :as r} (or response {:status 404})]
     (or (c/check-status status-schema r)
         (check-response
           (get schemas status)
           response
           request)))))
