(ns ronda.schema.data.ring-test
  (:require [midje.sweet :refer :all]
            [ronda.schema.data.ring :as r]
            [schema.core :as s]))

(s/set-fn-validation! true)

;; ## tests

(fact "about HTTP status codes."
      (s/check r/Status 200) => nil?
      (s/check r/Status 500) => nil?
      (s/check r/Status :x)  =not=> nil?
      (s/check r/Status 10)  =not=> nil?
      (s/check r/Status 777) =not=> nil?)

(let [r {:request-method :get
         :uri "/path"
         :route-params {:a 1, "b" 2}
         :query-params {:c "1", "d" 2}
         :form-params  {:e 1, :f 2}}]
  (fact "about keywordizing all request parameters."
        (let [r' (r/normalize-params r)]
          (keys (:route-params r')) => (has every? keyword?)
          (keys (:query-params r')) => (has every? keyword?)
          (keys (:form-params r')) => (has every? keyword?)))
  (fact "about merging all request params."
        (let [r' (r/merge-params r)]
          (:params r') => {:a 1, "b" 2, :c "1"
                           "d" 2, :e 1, :f 2})))

(fact "about normalizing headers."
      (let [r (r/normalize-headers
                {:request-method :get
                 :uri "/path"
                 :headers {"Content-Type" "text/plain"
                           :x-forwarded-for "127.0.0.1"}})]
        (:headers r) => {"content-type" "text/plain"
                         "x-forwarded-for" "127.0.0.1"}))
