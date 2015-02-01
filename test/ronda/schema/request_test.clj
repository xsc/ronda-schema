(ns ronda.schema.request-test
  (:require [midje.sweet :refer :all]
            [ronda.schema.data
             [errors :as e]
             [request :refer [compile-requests]]]
            [ronda.schema.request :refer :all]
            [ronda.coerce :refer [coercer-factory]]
            [schema.core :as s]))

(s/set-fn-validation! true)

;; ## Fixtures

(def request
  {:method :get
   :params {:length s/Int}
   :constraint (s/pred
                 #(-> % :params :length pos?)
                 'length-positive?)
   :responses {200 {:headers {"x-length" s/Int}
                    :body s/Str
                    :constraint
                    (s/pred
                      #(= (-> % :headers (get "x-length"))
                          (-> % :body count))
                      'length-consistent?)
                    :semantics
                    (s/pred
                      (fn [{:keys [request response]}]
                        (= (-> request :params :length)
                           (-> response :body count)))
                      'requested-length-returned?)}}})

(def request-schema
  (compile-requests [request] nil))

(def coercing-schema
  (compile-requests [request] coercer-factory))

;; ## Tests

(fact "about method validation failure."
      (let [r (check-request
                request-schema
                {:request-method :post})]
        (:error r) => :method-not-allowed))

(tabular
  (fact "about request validation failure."
        (let [r (check-request
                  request-schema
                  (assoc ?request :request-method :get))]
          (:error r) => :request-validation-failed))
  ?request
  {}
  {:query-params {:length "x"}}
  {:query-params {}})

(fact "about request constraint failure."
      (let [r (check-request
                request-schema
                {:request-method :get
                 :query-params {:length -8}})]
        (:error r) => :request-constraint-failed))

(fact "about successful request validation."
      (let [req {:request-method :get
                 :query-params {:length 8}}
            r (check-request request-schema req)]
        (:error r) => nil?
        r => (assoc req :params {:length 8} :headers {})
        (-> r possible-responses :schemas keys) => [200]))

(let [validate-coerce #(check-request coercing-schema %)]
  (fact "about request coercion exception."
        (let [r (validate-coerce
                  {:request-method :get
                   :query-params {:length "x"}})]
          (:error r) => :request-validation-failed))
  (fact "about request validation failure after coercion."
        (let [r (validate-coerce
                  {:request-method :get
                   :query-params {}})]
          (:error r) => :request-validation-failed))
  (fact "about request constraint failure after coercion."
        (let [r (validate-coerce
                  {:request-method :get
                   :query-params {:length "-12"}})]
          (:error r) => :request-constraint-failed))
  (fact "about successful coercion and validation."
        (let [r (validate-coerce
                  {:request-method :get
                   :query-params {:length "12"}})]
          (:error r) => nil?
          r => {:request-method :get
                :headers {}
                :query-params {:length "12"}
                :params {:length 12}}
          (-> r possible-responses :schemas keys) => [200])))
