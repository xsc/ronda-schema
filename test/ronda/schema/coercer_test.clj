(ns ronda.schema.coercer-test
  (:require [midje.sweet :refer :all]
            [schema.core :as s]
            [ronda.schema.coercer :refer :all])
  (:import [java.net URL URI]
           [java.math BigInteger BigDecimal]
           [clojure.lang BigInt]))

;; ## Fixtures

(def test-url
  "http://abc.de/x y")

(def test-uri
  "http://abc.de/x%20y")

(def test-schema
  {:int    s/Int
   :long   Long
   :num    s/Num
   :double Double
   :bigdec BigDecimal
   :bigint BigInteger
   :bint   BigInt
   :key    s/Keyword
   :url    URL
   :uri    URI})

(def test-value
  {:int    (int 12)
   :long   12345678
   :num    -43.4
   :double 3.14
   :bigdec 23.4M
   :bigint (BigInteger. "23")
   :bint   43N
   :key    :keyword
   :url    (URL. test-url)
   :uri    (URI. test-uri)})

(def test-coercer
  ((default-coercer-factory) test-schema))

;; ## Tests

(tabular
  (fact "about request coercion."
        (let [r (test-coercer (merge test-value ?v))]
          (:error-form r) => nil?
          (:value r) => test-value))
  ?v
  {}
  {:key "keyword"}

  {:int    "12"}
  {:int    12N}
  {:int    12.0}
  {:int    12.0M}
  {:long   "12345678"}
  {:long   12345678N}
  {:long   12345678.0}
  {:long   12345678.0M}
  {:num    "-43.4"}
  {:double "3.14"}
  {:double 3.14M}
  {:bigdec "23.4"}
  {:bigdec 23.4}
  {:bigint "23"}
  {:bigint 23}
  {:bigint 23.0}
  {:bigint 23.0M}
  {:bint   "43"}
  {:bint   43}
  {:bint   43.0}
  {:bint   43.0M}
  {:url    test-url}
  {:uri    test-uri})

(tabular
  (fact "about coercion errors."
        (let [r (test-coercer (assoc test-value ?k ?v))]
          (:value r)      => nil?
          (:error-form r) => map?
          (:error-form r) => #(contains? % ?k)))
  ?k               ?v
  :key             123
  :int             "abc"
  :int             12.3
  :long            "abc"
  :long            12.3
  :long            (* (bigint Long/MAX_VALUE) 2)
  :num             "abc"
  :bigdec          "abc"
  :bigint          "abc"
  :bint            "abc"
  :url             "abc"
  :uri             "http://"
  :uri             test-url)
