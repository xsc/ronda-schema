(ns ronda.schema.data.response
  (:require [ronda.schema.data
             [common :refer :all]
             [coercer :refer [Coercer CoercerFactory]]
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
  {:status-schema (ring/status-schema (keys rs))
   :schemas       (->> (for [[status schema] rs]
                         [status (compile-response-schema schema coercer)])
                       (into {}))})
