(ns ronda.schema.check
  (:require [ronda.schema.data
             [common :refer :all]
             [coercer :refer [CoercerResult Coercer]]
             [errors :as e]
             [params :as p]
             [ring :as ring]]
            [schema.core :as s]))

;; ## General

(s/defn check-constraint
  :- (s/maybe
       (s/either
         e/ResponseValidationError
         e/RequestValidationError))
  "Check constraint schema against value."
  [constraint :- (s/maybe SchemaValue)
   value      :- s/Any
   error-key  :- s/Keyword]
  (if constraint
    (e/check-for-error value error-key constraint)))

(s/defn ^:private safe-coerce :- CoercerResult
  "Apply coercer to schema and value; catch Throwables and create error value."
  [coercer schema value]
  (try
    (coercer value)
    (catch Throwable t
      {:error-form (list 'not (list 'coerceable-as? (s/explain schema) value))})))

(s/defn check-with-coercion
  "Validate/Coerce the given value and produce an error value
   on Exception or validation/coercion failure. Otherwise, return
   the coerced value."
  [coercer :- (s/maybe Coercer)
   schema :- SchemaValue
   value :- s/Any
   error-key :- s/Keyword]
  (if coercer
    (let [r (safe-coerce coercer schema value)]
      (if-let [e (:error-form r)]
        (e/->error error-key schema value e)
        (:value r)))
    (or (e/check-for-error value error-key schema)
        value)))

;; ## Response

(s/defn check-semantics :- (s/maybe e/ResponseValidationError)
  "Check semantics schema against request/response."
  [semantics :- (s/maybe SchemaValue)
   request   :- (s/maybe ring/Request)
   response  :- ring/Response]
  (if (and semantics request)
    (e/check-for-error
      {:request request, :response response}
      :semantics-failed semantics)))

(s/defn check-status :- (s/maybe e/ResponseValidationError)
  "Check status schema against response."
  [status-schema :- SchemaValue
   response      :- ring/Response]
  (e/check-for-error
    (select-keys response [:status])
    :status-not-allowed status-schema))

(s/defn check-response-with-coercion
  :- (s/either e/ResponseValidationError ring/Response)
  "Coerce/Validate response. Returns either an error value or the
   coerced response."
  [coercer  :- (s/maybe Coercer)
   schema   :- SchemaValue
   response :- ring/Response]
  (check-with-coercion
    coercer
    schema
    response
    :response-validation-failed))

(s/defn check-response-constraint :- (s/maybe e/ResponseValidationError)
  "Check constraint on the given response."
  [constraint :- (s/maybe SchemaValue)
   value      :- s/Any]
  (check-constraint
    constraint
    value
    :response-constraint-failed))

;; ## Request

(s/defn check-method :- (s/maybe e/RequestValidationError)
  "Check method schema against request."
  [method-schema :- SchemaValue
   request       :- ring/Request]
  (e/check-for-error request :method-not-allowed method-schema))

(s/defn check-params :- (s/maybe e/RequestValidationError)
  "Check params schema against request."
  [params :- (s/maybe SchemaValue)
   request :- ring/Request]
  (if params
    (e/check-for-error
      (update-in request [:params] #(or % {}))
      :request-validation-failed
      (flexible-schema
        {:params params}))))

(s/defn check-request-with-coercion
  :- (s/either e/RequestValidationError ring/Request)
  "Coerce/Validate request. Returns either an error value or the
   coerced request."
  [coercer :- (s/maybe Coercer)
   schema  :- SchemaValue
   request :- ring/Request]
  (check-with-coercion
    coercer
    schema
    request
    :request-validation-failed))

(s/defn check-request-constraint :- (s/maybe e/RequestValidationError)
  "Check constraint on the given response."
  [constraint :- (s/maybe SchemaValue)
   value      :- s/Any]
  (check-constraint
    constraint
    value
    :request-constraint-failed))
