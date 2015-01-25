(ns ronda.schema.data.coercer
  (:require [ronda.schema.data.common :refer [SchemaValue]]
            [schema.core :as s]))

(def CoercerResult
  (s/either
    {:error-form s/Any}
    {:value s/Any}))

(def Coercer
  "Schema for coercer functions."
  (s/=> CoercerResult s/Any))

(def CoercerFactory
  "Schema for functions that create coercers."
  (s/=> Coercer SchemaValue))
