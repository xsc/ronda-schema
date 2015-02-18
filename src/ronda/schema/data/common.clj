(ns ^:no-doc ronda.schema.data.common
  (:require [schema.core :as s]
            [clojure.walk :as w]))

;; ## Basic

(def SchemaValue
  (s/protocol s/Schema))

(def MapSchemaValue
  {s/Any SchemaValue})

(s/defn optional-keys
  "Make all schema keys optional."
  [m :- {s/Any s/Any}]
  (->> (for [[k v] m]
         [(s/optional-key k) v])
       (into {})))

(defn- plain-map?
  "Check whether the value is a plain Clojure map."
  [v]
  (or (instance? clojure.lang.PersistentArrayMap v)
      (instance? clojure.lang.PersistentHashMap v)))

(s/defn flexible-schema
  "Make schema flexible by allowing any additional keys/values in maps,
   even nested ones."
  ([s :- SchemaValue]
   (flexible-schema s s/Any))
  ([s :- SchemaValue
    key-schema :- SchemaValue]
   (w/postwalk
     (fn [m]
       (if (plain-map? m)
         (->> (for [[k v] m]
                (if (string? k)
                  [(s/required-key k) v]
                  [k v]))
              (into {key-schema s/Any}))
         m))
     s)))

(s/defn constraint-schema
  "Make schema flexible if it is a map."
  [s :- (s/maybe SchemaValue)]
  (cond (nil? s) s/Any
        (and (plain-map? s) (not (contains? s s/Any))) (assoc s s/Any s/Any)
        :else s))

(defn allow-any
  "Allow any additional keys in the given schema."
  [s]
  (merge {s/Any s/Any} s))

(defn seq-or-single
  "Allow either a seq with values matching the given schema
   or a single value."
  [schema]
  (s/either schema [schema]))

(defn as-seq
  "Convert value to seq if it's not already."
  [v]
  (if (or (nil? v) (sequential? v))
    v
    [v]))

;; ## Helper

(defn update-in-existing
  "Apply function to map value only if the map contains the respective
   key."
  [m k f & args]
  (if (contains? m k)
    (update-in m [k] #(apply f % args))
    m))

;; ## Wildcard

(def Wildcard
  "The wildcard value."
  :*)

(defn allow-wildcard
  "Allow wildcard value in addition to the given schema."
  [schema]
  (s/either
    (s/eq Wildcard)
    schema))

(defn wildcarded-seq
  "Schema that allows a seq of values matching the given schema or
   the wildcard value."
  [schema]
  [(allow-wildcard schema)])

(defn wildcard?
  "Does the given value (either a literal or a seq) represent
   the wildcard?"
  [v]
  (some #{Wildcard} (as-seq v)))

(defn normalize-wildcard
  "Convert to seq. If empty or wildcard is contained,
   return a single-element seq with just the wildcard."
  [v]
  (let [vs (as-seq v)]
    (if (or (empty? vs) (wildcard? vs))
      [Wildcard]
      vs)))
