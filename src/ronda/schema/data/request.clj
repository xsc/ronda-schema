(ns ronda.schema.data.request
  (:require [ronda.schema.data
             [common :refer :all]
             [coercer :refer [Coercer CoercerFactory]]
             [ring :as ring]
             [response :as r]]
            [schema.core :as s]))

;; ## Data
;;
;; A Ring request can contain a method, headers, different kinds of
;; parameters (query, route, form) and a body. Additionally, there
;; may be constraints on the request. Thus, a schema has to  represent:
;;
;; - a map schema for headers (string -> value).
;; - a map schema for all params (keyword -> value), not differentiating
;;   between where they come from.
;; - a schema for the query string.
;; - a schema for whole-request constraints.
;; - a schema for the request body.
;; - a series of allowed responses.
;;
;; Request validation will validate method/headers/params/query-string and
;; body first, then the constraint.

;; ## Schema

(def RawRequestSchema
  "Structure of HTTP request schemas."
  (optional-keys
    {:method         (seq-or-single
                       (allow-wildcard
                         ring/Method)) ;; request method (default: ANY)
     :params         MapSchemaValue    ;; route/query/form params (keyword -> value)
     :headers        MapSchemaValue    ;; headers (string -> value)
     :query-string   SchemaValue       ;; query string constraint (string)
     :body           SchemaValue       ;; body constraint
     :constraint     SchemaValue       ;; whole-request constraint
     :responses      r/RawResponses})) ;; allowed responses (default: ANY)

(def RequestSchema
  "Compiled request schema."
  {:methods    SchemaValue
   :schema     SchemaValue
   :coercer    (s/maybe Coercer)
   :constraint SchemaValue
   :responses  r/Responses})

(def RawRequests
  "Schema for representation of multiple allowed requests."
  [RawRequestSchema])

(def Requests
  "Compiled allowed requests."
  {:default (s/maybe RequestSchema)
   :schemas {ring/Method RequestSchema}})

;; ## Compile

(s/defn ^:private compile-methods :- SchemaValue
  "Create schema that will match methods"
  [schema :- RawRequestSchema]
  (let [methods (as-seq (or (:method schema) :*))]
    (if (wildcard? methods)
      s/Any
      {:request-method (apply s/enum methods)})))

(s/defn compile-request-schema :- RequestSchema
  "Prepare the different pars of a raw request schema for actual
   validation."
  [schema          :- RawRequestSchema
   coercer-factory :- (s/maybe CoercerFactory)]
  (let [methods (compile-methods schema)
        base (-> schema
                 (select-keys [:params :headers :query-string :body])
                 (update-in-existing :headers flexible-schema s/Str)
                 (update-in-existing :params flexible-schema s/Keyword)
                 (allow-any))]
    {:methods methods
     :schema  base
     :coercer (if coercer-factory
                (coercer-factory base))
     :constraint (or (:constraint schema) s/Any)
     :responses (r/compile-responses
                  (or (:responses schema) {})
                  coercer-factory)}))

(s/defn ^:private compile-all-requests
  [schemas         :- RawRequests
   coercer-factory :- (s/maybe CoercerFactory)]
  (->> (for [{:keys [methods] :as schema} schemas
             :let [methods' (or methods [:*])
                   r (compile-request-schema schema coercer-factory)]
             method (if (wildcard? methods')
                      [:*]
                      (as-seq methods'))]
         [method r])
       (into {})))

(s/defn compile-requests :- Requests
  "Prepare requests for actual validation."
  [schemas         :- RawRequests
   coercer-factory :- (s/maybe CoercerFactory)]
  (if (empty? schemas)
    (recur [{}] coercer-factory)
    (let [requests (compile-all-requests schemas coercer-factory)]
      {:default (get requests Wildcard)
       :schemas (dissoc requests Wildcard)})))
