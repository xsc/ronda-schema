(ns ronda.schema.data.ring
  (:require [ronda.schema.data.common :refer :all]
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

(def Method
  "Schema for Ring Methods."
  (s/enum
    :get :head :options
    :put :post :delete))
