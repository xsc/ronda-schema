(ns benchmark
  (:require [clojure.test :refer :all]
            [criterium.core :as criterium]
            [ronda.schema :as r]
            [schema.core :as s]))

;; ## App

(defn gen
  [n]
  (->> (fn []
         {:type (rand-nth ["a" "b" "c"])
          :value {:x (rand-int 1000)}})
       (repeatedly n)
       (hash-map :data)))

(let [g (gen 100)]
  (defn app
    [{:keys [params body]}]
    (let [n (:length params)]
      (assert (= n 100))
      {:status 200
       :headers {"x-len" n}
       :body g})))

;; ## App + Schema Middleware

(def app-with-schema
  (r/wrap-schema
    app
    {:get {:params     {:length s/Int}
           :body       s/Str
           :constraint (s/pred (comp seq :body) 'not-empty?)
           :responses  {200 {:headers {"x-len" s/Int}
                             :body {:data [{:type s/Str
                                            :value {s/Keyword s/Int}}]}
                             :constraint (s/pred (comp seq :data :body) 'not-empty?)
                             :semantics
                             (s/pred
                               (fn [{:keys [request response]}]
                                 (= (-> request :params :length)
                                    (count (get-in response [:body :data]))))
                               'length-header?)}}}}))

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
                          (not (sequential? (:data body')))
                          (not (every?
                                 (fn [{:keys [type value]}]
                                   (and (string? type)
                                        (every? keyword? (keys value))
                                        (every? integer? (vals value))))
                                 (:data body'))))
                      {:status 500, :headers {}, :body ":response-validation-failed"}

                      (not (seq body'))
                      {:status 500, :headers {}, :body ":response-constraint-failed"}

                      (or (not= n (headers "x-len")))
                      {:status 500, :headers {}, :body ":semantics-failed"}

                      :else response)))))))

(def app-with-custom
  (wrap-custom app))

;; ## Tests

(def ^:private test-requests
  [{:request-method :get
    :headers {}
    :query-params {:length "100"}
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
    :query-params {:length "100"}
    :body ""}])

(deftest t-precondition
  (testing "status equality."
    (let [mc (mapv app-with-custom test-requests)
          ms (mapv app-with-custom test-requests)]
      (is (= (map :status mc) (map :status ms)))
      (is (= (-> mc first :body) (-> ms first :body)))
      (is (= (map :headers mc) (map :headers ms))))))

(deftest t-schema-middleware
  (testing "schema middleware performance."
    (printf "%n-- schema middleware%n")
    (criterium/with-progress-reporting
      (criterium/bench
        (doseq [req test-requests]
          (app-with-schema req))))
    (flush)))

(deftest t-custom-middleware
  (testing "explicit check/conversion performance."
    (printf "%n-- custom middleware%n")
    (criterium/with-progress-reporting
      (criterium/bench
        (doseq [req test-requests]
          (app-with-custom req))))
    (flush)))
