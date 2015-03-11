(ns ronda.schema.middleware
  (:require [ronda.schema.data
             [common :refer :all]
             [coercer :refer [CoercerFactory]]
             [errors :as e]
             [ring :as ring]
             [request :as rq]
             [response :as rs]]
            [ronda.schema
             [coercer :as rc]
             [request :refer [check-request possible-responses]]
             [response :refer [check-response]]]
            [schema.core :as s]))

;; ## Options

(def Options
  "Options for `wrap-schema`."
  (optional-keys
    {:response? s/Bool
     :coercer   CoercerFactory}))

(def ^:private default-options
  "Default values for `wrap-schema`'s options."
  {:response? true
   :coercer rc/default-coercer-factory})

;; ## Error Response

(s/defn error->status :- ring/Status
  "Convert error result to response status."
  [{:keys [error]} :- e/ValidationError]
  (case error
    :method-not-allowed        405
    :request-validation-failed 400
    :request-constraint-failed 422
    500))

(s/defn error->response-body :- s/Str
  "Convert error result to response body."
  [{:keys [error error-form]} :- e/ValidationError]
  (str error "\n" (pr-str error-form)))

(s/defn error->response
  "Convert error result to response map."
  [error :- e/ValidationError]
  {:status (error->status error)
   :headers {}
   :ronda/error error
   :body (error->response-body error)})

(s/defn validation-error :- (s/maybe e/ValidationError)
  "Get the validation error from the response (or `nil`)."
  [response :- ring/Response]
  (:ronda/error response))

;; ## Steps

(s/defn ^:private ensure-response :- (s/maybe ring/Response)
  "Make sure to return either `nil` or a response map."
  [value]
  (if value
    (let [r (if (map? value)
              value
              {:status 200,
               :headers {},
               :body value})]
      (update-in r [:status] #(or % 200)))))

(s/defn ^:private handle-response
  "Validate response if desired."
  [options   :- Options
   request   :- ring/Request
   response  :- ring/Response]
  (if (:response? options)
    (check-response
      (possible-responses request)
      response
      request)
    response))

(s/defn ^:private handle-request
  "Handle request, validate response if desired."
  [options :- Options
   handler :- ring/Handler
   request :- ring/Request]
  (e/unless-error->>
    (handler request)
    (ensure-response)
    (handle-response options request)))

;; ## Handler

(s/defn as-response :- (s/maybe ring/Response)
  "If `result` is an error, return default error response, the response
   otherwise."
  [result :- (s/either ring/Response e/ValidationError)]
  (if (e/error? result)
    (error->response result)
    result))

(s/defn handle-with-validation :- (s/maybe ring/Response)
  "Process the given request:

   - validate request against schema,
   - handle coerced request,
   - validate response,
   - return coerced response.

   If a validation error is encountered, an error response will be generated
   that contains the raw error value, accessible using `validation-error`.

   Note that this function takes a compiled request schema attainable using
   `ronda.schema/compile-requests`."
  ([handler :- ring/Handler
    schema  :- rq/Requests
    request :- ring/Request]
   (handle-with-validation handler schema request {}))
  ([handler :- ring/Handler
    schema  :- rq/Requests
    request :- ring/Request
    options :- Options]
   (let [options (merge default-options options)]
     (as-response
       (e/unless-error->>
         (check-request schema request)
         (handle-request options handler))))))

;; ## Middleware

(s/defn wrap-schema :- ring/Handler
  "Wrap the given handler with request/response validation against the given
   request schema."
  ([handler        :- ring/Handler
    request-schema :- rq/RawRequests]
   (wrap-schema handler request-schema {}))
  ([handler        :- ring/Handler
    request-schema :- rq/RawRequests
    {:keys [response? coercer]
     :or {response? true
          coercer   rc/default-coercer-factory}
     :as options}
    :- Options]
   (let [schema (rq/compile-requests request-schema coercer)]
     (fn handler-with-schema
       [request]
       (handle-with-validation
         handler
         schema
         request
         options)))))
