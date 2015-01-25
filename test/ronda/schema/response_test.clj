(ns ronda.schema.response-test
  (:require [midje.sweet :refer :all]
            [ronda.schema.response :refer :all]
            [ronda.schema.data.response
             :refer [compile-responses
                     compile-response-schema]]
            [ronda.coerce :as c]
            [schema.core :as s]))

(s/set-fn-validation! true)

;; ## Fixtures

(def response-schemas
  (compile-responses
    {200 {:headers {"x-length" s/Int}
          :body s/Str
          :constraint
          (s/pred
            #(= (-> % :headers (get "x-length"))
                (-> % :body count))
            'length-consistent?)
          :semantics
          (s/pred
            (fn [{:keys [request response]}]
              (= (-> request :query-params :length)
                 (-> response :body count)))
            'requested-length-returned?)}}
    nil))

;; ## Tests

(fact "about response status failure."
      (let [r (check response-schemas {:status 403})]
        (:error r) => :status-not-allowed))

(tabular
  (fact "about response validation failure."
        (let [r (check response-schemas (assoc ?response :status 200))]
          (:error r) => :response-validation-failed))
  ?response
  {}
  {:headers {"x-length" "8"}}
  {:headers {"x-length" 8}}
  {:headers {"x-length" 8}, :body nil})

(fact "about response constraint failure."
      (let [r (check
                response-schemas
                {:status 200
                 :headers {"x-length" 8}
                 :body "1234567"})]
        (:error r) => :response-constraint-failed))

(fact "about response semantics validation failure."
      (let [r (check
                response-schemas
                {:status 200
                 :headers {"x-length" 7}
                 :body "1234567"}
                {:request-method :get
                 :query-params {:length 8}})]
        (:error r) => :semantics-failed))

(fact "about successful response validation."
      (let [resp {:status 200
                  :headers {"x-length" 8}
                  :body "12345678"}
            r (check
                response-schemas
                resp
                {:request-method :get
                 :query-params {:length 8}})]
        r => resp))
