(ns ronda.schema.request
  (:require [ronda.schema.check :as c]
            [ronda.schema.data
             [common :refer :all]
             [coercer :refer [CoercerFactory]]
             [errors :as e]
             [ring :as ring]
             [request :refer :all]]
            [schema.core :as s]))

(defn possible-responses
  "Get possible responses for the given request, after they were
   stored in the request metdata by `check-single-request`."
  [request]
  (-> request meta :ronda/responses))

(defn set-possible-responses
  "Set the possible responses for the given request."
  [request responses]
  (vary-meta request assoc :ronda/responses responses))

(s/defn check-single-request
  "Check the given request against the given schema,storing possible responses
   in the metadata."
  [rschema :- RequestSchema
   request :- ring/Request]
  (let [{:keys [schema coercer constraint responses]} rschema
        request' (->> (ring/normalize-request request)
                      (c/check-request-with-coercion coercer schema))]
    (or (if (e/error? request') request')
        (c/check-request-constraint
          constraint
          request')
        (set-possible-responses request' responses))))

(s/defn check-request
  "Check request against the given schemas (based on the request method),
   coercing if desired."
  [requests :- Requests
   request  :- ring/Request]
  (let [{:keys [methods default schemas]} requests
        {:keys [request-method]} request]
    (or (c/check-method methods request)
        (check-single-request
          (get schemas request-method default)
          request))))

(s/defn request-validator
  "Create function, taking a request and validating (by method) against
   the given schemas."
  ([requests :- RawRequests]
   (request-validator requests nil))
  ([requests        :- RawRequests
    coercer-factory :- (s/maybe CoercerFactory)]
   (let [rs (compile-requests requests coercer-factory)]
     (fn validator
       [request]
       (check-request rs request)))))
