(ns ronda.schema.coercer
  (:require [ronda.schema.data
             [coercer :refer [Coercer CoercerResult]]
             [common :refer :all]]
            [schema
             [core :as s]
             [coerce :as coerce]
             [utils :as utils]]
            [clojure.walk :as w]
            [clojure.tools.reader.edn :as edn])
  (:import [java.net URI URL]
           [java.math BigDecimal BigInteger]))

;; ## Custom Coercer

(defn- coerce-if
  [pred f]
  (coerce/safe
    (fn [v]
      (if (pred v)
        (f v)
        v))))

(defn- coerce-with-read
  [f]
  (coerce/safe
    (fn [v]
      (if (string? v)
        (f (edn/read-string v))
        (f v)))))

(def +custom-coercer+
  "Custom coercion matcher."
  (merge
    coerce/+string-coercions+
    {URI                 (coerce-if string? #(URI. %))
     URL                 (coerce-if string? #(URL. %))
     s/Num               (coerce-with-read identity)
     Long                (coerce-with-read coerce/safe-long-cast)
     s/Int               (coerce-with-read coerce/safe-long-cast)
     Double              (coerce-with-read double)
     BigDecimal          (coerce-with-read bigdec)
     clojure.lang.BigInt (coerce-with-read bigint)
     BigInteger          (coerce-with-read biginteger)}))

;; ## Coercer Function

(s/defn ^:private coerce-value :- CoercerResult
  "Coerce the given value using the prepared coercion function."
  [f value]
  (let [r (f value)]
    (if (utils/error? r)
      {:error-form (utils/error-val r)}
      {:value r})))

(defn default-coercer-factory
  "Create custom coercer from the given Schema."
  ([]
   (default-coercer-factory {}))
  ([additional-coercions]
   (s/fn :- Coercer
     [schema :- SchemaValue]
     (let [f (coerce/coercer schema (merge +custom-coercer+
                                           additional-coercions))]
       (fn ronda-coercer
         [value]
         (coerce-value f value))))))
