(ns ronda.schema.data.response
  (:require [ronda.schema.data
             [common :refer :all]
             [coercer :as c]
             [ring :as ring]]
            [ronda.schema.coercer :refer [default-coercer-factory]]
            [schema.core :as s]))

;; ## Data
;;
;; A Ring response can contain status, headers and a body. Additionally,
;; there may be constraints on the response (i.e. 'Content-Length' header
;; has to match the body's length), as well as semantics based on the
;; request that prompted the response. Thus, a schema has to represent:
;;
;; - a map schema for headers (string -> value).
;; - a schema for the whole-response constraint.
;; - a schema for a map with the keys `:request`/`:response` representing
;;   the request/response cycle constraints.
;; - a schema for the response body.
;;
;; Response validation will validate headers and body first, then the
;; constraint, then the request/response semantics.

;; ## Schema

(def ^:private schema-structure
  {:headers    MapSchemaValue ;; headers (string -> value)
   :body       SchemaValue    ;; body
   :constraint SchemaValue    ;; whole-response constraint
   :semantics  SchemaValue})  ;; request/response schema (:request/:response)

(def ^:private structural-keys
  (keys schema-structure))

(def RawResponseSchema
  "Structure of HTTP response schemas."
  (allow-any
    (optional-keys
      schema-structure)))

(def ResponseSchema
  "A compiled HTTP response schema."
  {:schema     MapSchemaValue
   :coercer    (s/maybe c/Coercer)
   :constraint SchemaChecker
   :semantics  SchemaChecker
   :metadata   {s/Any s/Any}})

(def RawResponses
  "A map of possible responses and their schemas."
  {(seq-or-single
     (allow-wildcard
       ring/Status)) RawResponseSchema})

(def Responses
  "A map of possible responses and their compiled schemas."
  {:statuses SchemaChecker
   :default  (s/maybe SchemaValue)
   :schemas  {ring/Status ResponseSchema}})

;; ## Compile

(s/defn ^:private collect-metadata :- {s/Any s/Any}
  "Collect metadata by removing all the schema-specific keys."
  [schema :- RawResponseSchema]
  (apply dissoc schema structural-keys))

(s/defn compile-response-schema :- ResponseSchema
  "Prepare the different parts of a raw response schema for actual
   validation."
  ([schema :- RawResponseSchema]
   (compile-response-schema schema default-coercer-factory))
  ([schema          :- RawResponseSchema
    coercer-factory :- (s/maybe c/CoercerFactory)]
   (let [base (-> schema
                  (select-keys [:headers :body])
                  (update-in-existing :headers flexible-schema s/Str)
                  (allow-any))]
     {:schema     base
      :coercer    (c/apply-factory coercer-factory base)
      :constraint (constraint-schema (:constraint schema))
      :semantics  (or (some-> schema :semantics ->checker) noop-checker)
      :metadata   (collect-metadata schema)})))

(def ^:private default-statuses
  [500])

(s/defn ^:private compile-statuses :- SchemaChecker
  "Create schema that will match statuses."
  [responses :- RawResponses]
  (let [statuses (normalize-wildcard
                   (mapcat as-seq (keys responses)))]
    (if (wildcard? statuses)
      noop-checker
      (->> (concat default-statuses statuses)
           (distinct)
           (apply s/enum)
           (hash-map :status)
           (->checker)))))

(s/defn ^:private attach-default-schemas
  "Attach default schemas if necessary."
  [coercer-factory schemas]
  (if (and (not (contains? schemas Wildcard))
           (not-any? #(contains? schemas %) default-statuses))
    (let [default-schema (compile-response-schema {} coercer-factory)]
      (reduce
        (fn [schemas status]
          (assoc schemas status default-schema))
        schemas default-statuses))
    schemas))

(s/defn ^:private compile-all-responses
  :- {(allow-wildcard ring/Status) ResponseSchema}
  "Create map associating statuses"
  [responses :- RawResponses
   coercer-factory :- (s/maybe c/CoercerFactory)]
  (->> (for [[statuses schema] responses
             :let [r (compile-response-schema
                       schema
                       coercer-factory)]
             status (if (wildcard? statuses)
                      [Wildcard]
                      (as-seq statuses))]
         [status r])
       (into {})
       (attach-default-schemas coercer-factory)))

(s/defn compile-responses :- Responses
  "Prepare responses for actual validation."
  ([rs :- RawResponses]
   (compile-responses rs default-coercer-factory))
  ([rs :- RawResponses
    coercer-factory :- (s/maybe c/CoercerFactory)]
   (if (empty? rs)
     (recur {Wildcard {}} coercer-factory)
     (let [responses (compile-all-responses rs coercer-factory)]
       {:statuses (compile-statuses rs)
        :default  (get responses Wildcard)
        :schemas  (dissoc responses Wildcard)}))))
