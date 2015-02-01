(ns ronda.schema.data.request-test
  (:require [midje.sweet :refer :all]
            [ronda.schema.data
             [request :as r]]
            [schema
             [core :as s]
             [utils :as u]]))

(s/set-fn-validation! true)

;; ## Fixtures

(def request
  {:params {:id s/Int, :length s/Int}
   :query-string (s/pred
                   #(>= (count %) 8)
                   'query-string-long?)
   :headers {"accept" #"text/plain"}
   :constraint (s/pred
                 #(= (-> % :route-params :id)
                     (-> % :query-params :length))
                 'weird-constraint?)
   :responses {200 {}}})

;; ## Tests

(let [{:keys [schema coercer constraint responses]}
      (r/compile-request-schema request nil)]
  (fact "about request schema compiling."
        coercer => nil?
        constraint => some?
        responses => map?
        (-> responses :schemas keys) => [200]
        (:default responses) => nil?)
  (tabular
    (fact "about request schema matches."
          (s/check schema ?request) => nil?
          (s/check constraint ?request) => nil?)
    ?request
    {:request-method :get
     :headers {"accept" "text/plain;text/xml"}
     :params {:id 7, :length 7}
     :query-string "length=7"}
    {:request-method :put
     :headers {"accept" "text/plain;text/xml"}
     :params {:id 7, :length 7}
     :query-string "length=7"}
    {:request-method :get
     :headers {"accept" "text/plain;text/xml"
               "additional-header" "value"}
     :params {:id 7, :length 7}
     :query-string "length=7"
     :additional-key :x})

  (tabular
    (fact "about request schema mismatches."
          (let [r (s/check schema ?request)]
            r =not=> nil?
            (get-in r ?failure-at) =not=> nil?))
    ?request, ?failure-at
    {:request-method :get
     :headers {"accept" "text/xml"}
     :params {:id 7, :length 7}
     :query-string "length=7"}
    [:headers "accept"]

    {:request-method :get
     :headers {"accept" "text/plain;text/xml"}
     :params {:id "7", :length 7}
     :query-string "length=7"}
    [:params]

    {:request-method :get
     :headers {"accept" "text/plain;text/xml"}
     :params {:id 7, :length 7}
     :query-string "len=7"}
    [:query-string])

  (fact "about request constraint mismatches."
        (->> (s/check
               constraint
               {:request-method :get
                :route-params {:id 7}
                :query-params {:length 8}
                :query-string "length=7"})
          (u/validation-error-explain)
          second first)
        => 'weird-constraint?))

(fact "about compiling multiple schemas (no default)."
      (let [{:keys [methods default schemas]}
            (r/compile-requests
              {:get {}
               :post {}}
              nil)]
        (s/check methods {:request-method :get}) => nil?
        (s/check methods {:request-method :post}) => nil?
        (s/check methods {:request-method :delete}) =not=> nil?
        default => nil?
        (count schemas) => 2
        (set (keys schemas)) => #{:get :post}))

(fact "about compiling multiple schemas (with default)."
      (let [{:keys [methods default schemas]}
            (r/compile-requests
              {[:get :post] {}
               :* {}}
              nil)]
        (s/check methods {:request-method :get}) => nil?
        (s/check methods {:request-method :post}) => nil?
        (s/check methods {:request-method :delete}) => nil?
        default =not=> nil?
        (count schemas) => 2
        (set (keys schemas)) => #{:get :post}))
