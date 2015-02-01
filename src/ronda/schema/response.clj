(ns ronda.schema.response
  (:require [ronda.schema.check :as c]
            [ronda.schema.data
             [common :refer :all]
             [errors :as e]
             [ring :as ring]
             [response :refer :all]]
            [schema.core :as s]))

(s/defn check-single-response
  "Check the given request/response pair against the given schema."
  ([rschema  :- ResponseSchema
    response :- (s/maybe ring/Response)]
   (check-single-response rschema response nil))
  ([rschema  :- ResponseSchema
    response :- (s/maybe ring/Response)
    request  :- (s/maybe ring/Request)]
   (let [{:keys [schema coercer constraint semantics]} rschema
         response' (->> (if response
                          (ring/normalize-response response)
                          {:status 404})
                        (c/check-response-with-coercion coercer schema))]
     (or (if (e/error? response') response')
         (c/check-response-constraint
           constraint
           response')
         (c/check-semantics
           semantics
           request
           response')
         (if response response')))))

(s/defn check-response
  "Check response against the given schemas (based on the response status),
   coercing if desired."
  ([responses :- Responses
    response  :- (s/maybe ring/Response)]
   (check-response responses response nil))
  ([responses :- Responses
    response  :- (s/maybe ring/Response)
    request   :- (s/maybe ring/Request)]
   (let [{:keys [statuses default schemas]} responses
         {:keys [status] :as r} (or response {:status 404})]
     (or (c/check-status statuses r)
         (check-single-response
           (get schemas status default)
           response
           request)))))
