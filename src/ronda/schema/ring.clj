(ns ronda.schema.ring
  (:require [ronda.schema.common :refer :all]
            [schema.core :as s]))

(def Status
  "Schema for HTTP status codes."
  (s/both
    s/Int
    (s/pred #(<= 100 % 599) 'valid-status-code?)))

(def Request
  "Basic Schema for Ring Requests."
  (flexible-schema
    {:request-method s/Keyword}))

(def Response
  "Basic Schema for Ring Responses."
  (s/maybe
    (flexible-schema
      {:status Status})))

(def Handler
  "Schema for Ring Handlers."
  (s/=> Response Request))

(defn method-schema
  "Create schema to check whether a request has an allowed method."
  [requests]
  (flexible-schema
    {:request-method (apply s/enum (keys requests))}))

(defn status-schema
  "Create schema to check whether a response has an allowed status."
  [{:keys [responses]}]
  (flexible-schema
    {:status (apply s/enum (keys responses))}))
