(ns ronda.schema.response
  (:require [ronda.schema
             [common :refer :all]
             [checkers :as checkers]
             [errors :as e]
             [ring :as ring]]
            [schema.core :as s]))

;; ## Data
;;
;; A Ring response can contain status, headers and a body. Additionally,
;; there may be constraints on the response (i.e. 'Content-Length' header
;; has to match the body's length), as well as semantics based on the
;; request that prompted the response. Thus, a schema has to represent:
;;
;; - a map schema for headers (string -> value).
;; - a map schema for the whole-response constraint.
;; - a schema for a map with the keys `:request`/`:response` representing
;;   the request/response cycle constraints.
;; - a schema for the response body.
;;
;; Response validation will validate headers and body first, then the
;; constraint, then the request/response semantics.

;; ## Schema

(def RawResponseSchema
  "Structure of HTTP response schemas."
  (optional-keys
    {:headers    MapSchemaValue ;; headers (string - value)
     :body       SchemaValue    ;; body
     :constraint SchemaValue    ;; whole-response constraint
     :semantics  SchemaValue})) ;; request/response schema (:request/:response)

(def ResponseSchema
  "A compiled HTTP response schema."
  {:schema     MapSchemaValue
   :coercer    (s/maybe Coercer)
   :constraint SchemaValue
   :semantics  SchemaValue})

(def RawResponses
  "A map of possible responses and their schemas."
  {ring/Status RawResponseSchema})

(def Responses
  "A map of possible responses and their compiled schemas."
  {:status-schema SchemaValue
   :schemas {ring/Status ResponseSchema}})

;; ## Compile

(s/defn compile-response-schema :- ResponseSchema
  "Prepare the different parts of a raw response schema for actual
   validation."
  [schema          :- RawResponseSchema
   coercer-factory :- (s/maybe CoercerFactory)]
  (let [base (-> schema
                 (select-keys [:headers :body])
                 (update-in-existing :headers flexible-schema s/Str)
                 (allow-any))]
    {:schema base
     :coercer (if coercer-factory
                (coercer-factory base))
     :constraint (or (:constraint schema) s/Any)
     :semantics (or (:semantics schema) s/Any)}))

(s/defn compile-responses :- Responses
  "Prepare responses for actual validation."
  [rs :- RawResponses
   coercer :- (s/maybe CoercerFactory)]
  {:status-schema {:status (apply s/enum (keys rs))}
   :schemas (->> (for [[status schema] rs]
                   [status (compile-response-schema schema coercer)])
                 (into {}))})

;; ## Check

(s/defn check-response
  "Check the given request/response pair against the given schema."
  [rschema  :- ResponseSchema
   response :- (s/maybe ring/Response)
   request  :- (s/maybe ring/Request)]
  (let [{:keys [schema coercer constraint semantics]} rschema
        response' (checkers/check-response-with-coercion
                    coercer
                    schema
                    (or response {:status 404}))]
    (or (if (e/error? response') response')
        (checkers/check-constraint
          constraint
          response'
          :response-constraint-failed)
        (checkers/check-semantics
          semantics
          request
          response')
        (if response response'))))

(s/defn check
  "Check response against the given schemas (based on the response status),
   coercing if desired."
  [responses :- Responses
   response  :- (s/maybe ring/Response)
   request   :- (s/maybe ring/Request)]
  (let [{:keys [status-schema schemas]} responses
        {:keys [status] :as r} (or response {:status 404})]
    (or (checkers/check-status status-schema r)
        (check-response
          (get responses status)
          response
          request))))
