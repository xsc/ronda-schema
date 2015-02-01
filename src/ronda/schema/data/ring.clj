(ns ronda.schema.data.ring
  (:require [ronda.schema.data.common :refer :all]
            [schema.core :as s]
            [clojure
             [string :as string]
             [walk :as w]]))

;; ## Schemas

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

;; ## Normalization

(defn normalize-headers
  "Header names have to be lowercased strings."
  [data]
  (update-in data [:headers]
             (fn [headers]
               (->> (for [[header value] headers]
                      (vector
                        (string/lower-case
                          (if (keyword? header)
                            (name header)
                            (str header)))
                        value))
                    (into {})))))

(def ^:private param-keys
  "Request keys that contain values to be merged into `:params`."
  [:route-params :query-params :form-params])

(s/defn normalize-params :- Request
  "Keywordize all request parameter maps."
  [request :- Request]
  (reduce
    (fn [request k]
      (if (contains? request k)
        (update-in request [k] w/keywordize-keys)
        request))
    request param-keys))

(s/defn merge-params :- Request
  "Merge `:route-params`, `:query-params`, `:form-params` into `:params`."
  [request :- Request]
  (let [params (map #(get request %) param-keys)]
    (update-in request [:params] (fnil into {}) params)))

(s/defn normalize-request :- Request
  "Normalize request in preparation of validation."
  [request :- Request]
  (-> request
      (normalize-headers)
      (normalize-params)))

(s/defn normalize-response :- Response
  "Normalize response in preparation of validation."
  [response :- Response]
  (-> response
      (normalize-headers)))
