(ns ronda.schema.data.params-test
  (:require [midje.sweet :refer :all]
            [ronda.schema.data.params :refer :all]
            [schema.core :as s]))

(s/set-fn-validation! true)

;; ## Tests

(let [r {:request-method :get
         :uri "/path"
         :route-params {:a 1, "b" 2}
         :query-params {:c "1", "d" 2}
         :form-params  {:e 1, :f 2}}]
  (fact "about keywordizing all request parameters."
        (let [r' (keywordize-request-params r)]
          (keys (:route-params r')) => (has every? keyword?)
          (keys (:query-params r')) => (has every? keyword?)
          (keys (:form-params r')) => (has every? keyword?)))
  (fact "about merging all request params."
        (let [r' (merge-request-params r)]
          (:params r') => {:a 1, "b" 2, :c "1"
                           "d" 2, :e 1, :f 2})))
