(ns ronda.schema.data.response-test
  (:require [midje.sweet :refer :all]
            [ronda.schema.data
             [response :as r]
             [common :refer :all]]
            [schema
             [core :as s]
             [utils :as u]]))

(s/set-fn-validation! true)

;; ## Fixtures

(def response
  {:headers {"content-type" (s/eq "text/plain")}
   :body s/Str
   :constraint (s/pred #(= (count (:body %)) 8) 'valid-body?)
   :description "Response"
   :semantics s/Any})

;; ## Compile Tests

(fact "about HTTP response schemas."
      (s/check r/RawResponseSchema response) => nil?)

(fact "about response schema compilation."
      (let [r (r/compile-response-schema response nil)]
        (:constraint r) =not=> nil?
        (:schema r) =not=> nil?
        (:semantics r) =not=> nil?
        (:metadata r) => {:description "Response"})
      (let [r (r/compile-response-schema {} nil)]
        ((:constraint r) :not-valid) => nil?
        ((:semantics r) :not-valid) => nil?
        (:schema r) => {s/Any s/Any}))

(let [compiled-response (r/compile-response-schema response nil)]
  (tabular
    (fact "about response schema matches."
          (s/check (:schema compiled-response) ?response) => nil?)
    ?response
    {:headers {"content-type" "text/plain"}
     :body "12345678"}
    {:headers {"content-type" "text/plain"}
     :body "1234567"}
    {:headers {"content-type" "text/plain"
               "additional-header" "value"}
     :body "12345678"
     :additional-key :x})

  (tabular
    (fact "about response schema mismatches."
          (let [r (s/check (:schema compiled-response) ?response)]
            r =not=> nil?
            ?failure-at => (contains (set (keys r)))))
    ?response, ?failure-at
    {}
    [:headers :body]

    {:headers {"content-type" "text/plain"}}
    [:body]

    {:headers {"content-type" "text/xml"}
     :body "12345678"}
    [:headers]

    {:headers {"content-type" "text/plain"}
     :body (.getBytes "12345678")}
    [:body])

  (let [response {:headers {"content-type" "text/plain"}
                  :body "1234567"}]
    (fact "about constraint mismatches."
          (s/check (:schema compiled-response) response) => nil?
          (let [r ((:constraint compiled-response) response)]
            (-> (u/validation-error-explain r)
                second first) => 'valid-body?))))

(fact "about compiling multiple responses (no default)."
      (let [{:keys [statuses default schemas]} (r/compile-responses
                                                 {[200 201 202] response
                                                  400 {}}
                                                 nil)]
        (set (keys schemas)) => #{200 201 202 400 500}
        (map
          #(statuses {:status %})
          [200 201 202 400 500]) => (has every? nil?)
        (map
          #(statuses {:status %})
          [503 404]) => (has not-any? nil?)
        default => nil?))

(fact "about compiling multiple responses (with default)."
      (let [{:keys [statuses default schemas]} (r/compile-responses
                                                 {200 response
                                                  [:*] {}}
                                                 nil)]
        (set (keys schemas)) => #{200}
        (statuses {:status :crazy-status}) => nil
        default =not=> nil?))
