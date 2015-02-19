(ns benchmark
  (:require [clojure.test :refer :all]
            [criterium.core :as criterium]
            [ronda.schema :as r]
            [schema.core :as s]))

;; ## App

(defn app
  [{:keys [params body]}]
  (let [n (:length params)]
    {:status 200
     :headers {"x-len" n}
     :body (apply str body (repeat n \0))}))

;; ## App + Schema Middleware

(def app-with-schema
  (r/wrap-schema
    app
    {:get {:params     {:length s/Int}
           :body       s/Str
           :constraint (s/pred (comp seq :body) 'not-empty?)
           :responses  {200 {:headers {"x-len" s/Int}
                             :body    s/Str
                             :constraint (s/pred (comp seq :body) 'not-empty?)
                             :semantics
                             (s/both
                               (s/pred
                                 (fn [{:keys [request response]}]
                                   (= (-> request :params :length)
                                      (get-in response [:headers "x-len"])))
                                 'length-header?)
                               (s/pred
                                 (fn [{:keys [request response]}]
                                   (= (+ (count (:body request))
                                         (-> request :params :length))
                                      (-> response :body count)))))}}}}))

;; ## App + Custom Middleware

(defn wrap-custom
  [handler]
  (fn [{:keys [request-method query-params body] :as request}]
    (if (not= request-method :get)
      {:status 405, :headers {}, :body ":method-not-allowed"}
      (let [{:keys [length]} query-params
            n (try (Long/parseLong length) (catch Throwable t))]
        (cond (not (and n (string? body)))
              {:status 400, :headers {}, :body ":request-validation-failed"}

              (not (seq body))
              {:status 422, :headers {}, :body ":request-constraint-failed"}

              :else
              (let [{:keys [status headers] body' :body :as response}
                    (handler (assoc request :params {:length n}))]
                (cond (not= status 200)
                      {:status 500, :headers {}, :body ":status-not-allowed"}

                      (or (not (number? (headers "x-len")))
                          (not (string? body')))
                      {:status 500, :headers {}, :body ":response-validation-failed"}

                      (not (seq body'))
                      {:status 500, :headers {}, :body ":response-constraint-failed"}

                      (or (not= n (headers "x-len"))
                          (not= (+ n (count body))
                                (count body')))
                      {:status 500, :headers {}, :body ":semantics-failed"}

                      :else response)))))))

(def app-with-custom
  (wrap-custom app))

;; ## Tests

(def ^:private test-requests
  [{:request-method :get
    :headers {}
    :query-params {:length "10"}
    :body "random"}
   {:request-method :post
    :headers {}
    :body nil}
   {:request-method :get
    :headers {}
    :query-params {}
    :body "random"}
   {:request-method :get
    :headers {}
    :query-params {:length "10"}
    :body ""}])

(deftest t-precondition
  (testing "status equality."
    (is (= (map (comp :status app-with-custom) test-requests)
           (map (comp :status app-with-schema) test-requests)))))

(deftest t-schema-middleware
  (testing "schema middleware performance."
    (printf "%n-- schema middleware%n")
    (criterium/bench
      (doseq [req test-requests]
        (app-with-schema req)))))

(deftest t-custom-middleware
  (testing "explicit check/conversion performance."
    (printf "%n-- custom middleware%n")
    (criterium/bench
      (doseq [req test-requests]
        (app-with-custom req)))))
