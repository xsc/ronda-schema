(ns ronda.schema.data.errors
  (:require [ronda.schema.data.common :refer :all]
            [schema.core :as s]))

;; ## Generic Error Schema

(defn error-schema
  "Create schema for error values."
  [ks]
  (merge
    {:error (apply s/enum ks)
     :value s/Any
     :schema SchemaValue
     :error-form s/Any}
    (optional-keys
      {:throwable Throwable})))

(defn ->error
  "Create error value map."
  [k schema value error-form & [throwable]]
  (with-meta
    (merge
      {:error k
       :error-form error-form
       :schema schema
       :value value}
      (if throwable
        {:throwable throwable}))
    {::error? true}))

(defn error?
  "Check whether a value represents an error."
  [m]
  (-> m meta ::error?))

;; ## Request/Response Validation Errors

(def ResponseValidationError
  "Schema for failed response validation."
  (error-schema
    [:status-not-allowed
     :response-validation-failed
     :response-constraint-failed
     :semantics-failed]))

(def RequestValidationError
  "Schema for failed request validation."
  (error-schema
    [:method-not-allowed
     :request-validation-failed
     :request-constraint-failed]))

(def ValidationError
  "Possible validation errors."
  (s/either
    RequestValidationError
    ResponseValidationError))

;; ## Checks

(defn check-for-error
  "Check the given value against several schemas and produce an error
   map with a given error key if necessary. Ex.:

   (check-for-error value :x schema-x :y schema-y)
   "
  [value & checks]
  (->> (partition 2 checks)
       (some
         (fn [[k schema]]
           (if schema
             (if-let [error-form (s/check schema value)]
               (->error k schema value error-form)))))))
