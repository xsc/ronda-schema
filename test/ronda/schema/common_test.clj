(ns ronda.schema.common-test
  (:require [midje.sweet :refer :all]
            [ronda.schema.common :as r]
            [schema.core :as s]))

(s/set-fn-validation! true)

;; ## Tests

(fact "about optional keys."
      (let [s (merge
                (r/optional-keys
                  {:a s/Int
                   :b s/Int})
                {:c s/Int})]
        (s/check s {:c 0}) => nil?
        (s/check s {}) =not=> nil?
        (s/check s {:a 0}) =not=> nil?
        (s/check s {:a 0, :b 0, :c 0}) => nil?))

(fact "about flexible schemas."
      (let [s (r/flexible-schema
                (s/both
                  {:a s/Int, :b s/Int}
                  (s/pred #(< (apply + (vals %)) 10)))
                s/Keyword)]
        (s/check s {:a 0, :b 0})        => nil?
        (s/check s {:a 0, :b 0, :c 0})  => nil?
        (s/check s {:a 0, :b 0, "c" 0}) =not=> nil?
        (s/check s {:a 0, :b 10})       =not=> nil?
        (s/check s {:a 5, :b 3, :c 2})  =not=> nil?))
