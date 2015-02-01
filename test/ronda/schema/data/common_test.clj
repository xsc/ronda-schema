(ns ronda.schema.data.common-test
  (:require [midje.sweet :refer :all]
            [ronda.schema.data.common :as r]
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

(tabular
  (fact "about custom 'update-in'."
        (r/update-in-existing
          {:a 0, :b 1}
          ?k + 3) => ?r)
  ?k           ?r
  :a           {:a 3, :b 1}
  :b           {:a 0, :b 4}
  :c           {:a 0, :b 1})

(fact "about wildcard."
      (let [s (r/allow-wildcard s/Num)]
        (s/check s 0) => nil?
        (s/check s :*) => nil?
        (s/check s 'x) =not=> nil?)
      :* => r/wildcard?
      [:get :*] => r/wildcard?
      :get =not=> r/wildcard?)

(fact "about seq/single schema."
      (let [s (r/seq-or-single s/Keyword)]
        (s/check s :x) => nil?
        (s/check s [:x]) => nil?
        (s/check s 0) =not=> nil?
        (s/check s [0]) =not=> nil?))
