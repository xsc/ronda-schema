(ns ronda.schema.middleware-test
  (:require [midje.sweet :refer :all]
            [ronda.schema.middleware :refer :all]
            [schema.core :as s]))

(s/set-fn-validation! true)

;; ## Fixtures

(def wrap-ok-handler
  (partial
    wrap-schema
    (constantly {:status 200, :body "hello"})))

(defn req
  [m]
  (merge
    {:request-method :get}
    m))

(def ^:private test-schema
  {:get {:params {:length s/Int}
         :constraint
         {:request-method s/Keyword
          s/Any s/Any}
         :responses {200 {}}}})

;; ## Tests

(tabular
  (let [handler ?handler]
    (tabular
      (fact "about successful validation in the middleware."
            (handler (req ?req))
            => {:status 200,
                :headers {},
                :body "hello"})
      ?req
      {:query-params {:length 10}}
      {:route-params {:length 10}}
      {:query-params {:length "10"}}
      {:route-params {:length "10"}}))
  ?handler
  (wrap-ok-handler test-schema)
  (wrap-schema (constantly {:body "hello"}) test-schema)
  (wrap-schema (constantly "hello") test-schema))

(let [handler (wrap-ok-handler {:get {}})]
  (fact "about method validation failure."
        (let [{:keys [status] :as r} (handler (req {:request-method :post}))
              {:keys [error] :as e} (validation-error r)]
          status => 405
          error => :method-not-allowed)))

(let [handler (wrap-ok-handler {:get {:params {:length s/Int}}})]
  (fact "about validation failure."
        (let [{:keys [status] :as r} (handler (req {}))
              {:keys [error] :as e} (validation-error r)]
          status => 400
          error => :request-validation-failed)))

(let [handler (wrap-ok-handler
                {:get {:constraint (s/pred (constantly false))}})]
  (fact "about constraint validation failure."
        (let [{:keys [status] :as r} (handler (req {}))
              {:keys [error] :as e} (validation-error r)]
          status => 422
          error => :request-constraint-failed)))

(let [handler (wrap-ok-handler {:get {:responses {201 {}}}})]
  (fact "about response status validation failure."
        (let [{:keys [status] :as r} (handler (req {}))
              {:keys [error] :as e} (validation-error r)]
          status => 500
          error => :status-not-allowed)))

(let [handler (wrap-ok-handler
                {:get {:responses {200 {:headers {"x-length" s/Int}}}}})]
  (fact "about response validation failure."
        (let [{:keys [status] :as r} (handler (req {}))
              {:keys [error] :as e} (validation-error r)]
          status => 500
          error => :response-validation-failed)))

(let [handler (wrap-ok-handler
                {:get {:responses {200 {:constraint (s/pred (constantly false))}}}})]
  (fact "about response constraint failure."
        (let [{:keys [status] :as r} (handler (req {}))
              {:keys [error] :as e} (validation-error r)]
          status => 500
          error => :response-constraint-failed)))

(let [handler (wrap-ok-handler
                {:get {:responses {200 {:semantics (s/pred (constantly false))}}}})]
  (fact "about semantic failure."
        (let [{:keys [status] :as r} (handler (req {}))
              {:keys [error] :as e} (validation-error r)]
          status => 500
          error => :semantics-failed)))
