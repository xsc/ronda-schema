(ns ronda.schema.ring-test
  (:require [midje.sweet :refer :all]
            [ronda.schema.ring :as r]
            [schema.core :as s]))

(s/set-fn-validation! true)

;; ## tests

(fact "about HTTP status codes."
      (s/check r/Status 200) => nil?
      (s/check r/Status 500) => nil?
      (s/check r/Status :x)  =not=> nil?
      (s/check r/Status 10)  =not=> nil?
      (s/check r/Status 777) =not=> nil?)
