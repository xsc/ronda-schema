(ns ronda.schema.data.params
  (:require [ronda.schema.data.ring :as ring]
            [schema.core :as s]
            [clojure.walk :as w]))

(def ^:private param-keys
  "Request keys that contain values to be merged into `:params`."
  [:route-params :query-params :form-params])

(s/defn merge-request-params :- ring/Request
  "Merge `:route-params`, `:query-params`, `:form-params` into `:params`."
  [request :- ring/Request]
  (reduce
    (fn [request k]
      (if (contains? request k)
        (update-in request [:params] merge (get request k))
        request))
    request param-keys))

(s/defn keywordize-request-params :- ring/Request
  "Keywordize all request parameter maps."
  [request :- ring/Request]
  (reduce
    (fn [request k]
      (if (contains? request k)
        (update-in request [k] w/keywordize-keys)
        request))
    request param-keys))
