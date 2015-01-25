(ns ronda.schema.data.common
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
  "Make schema flexible by allowing any additional keys/values in maps."
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

(defn allow-any
  "Allow any additional keys in the given schema."
  [s]
  (merge {s/Any s/Any} s))

(defn constrain-schema
  "Add constraint to schema if given."
  [s constraint]
  (if constraint
    (s/both s constraint)
    s))

;; ## Helper

(defn update-in-existing
  "Apply function to map value only if the map contains the respective
   key."
  [m k f & args]
  (if (contains? m k)
    (update-in m [k] #(apply f % args))
    m))
