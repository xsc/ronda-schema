# ronda-schema

__ronda-schema__ aims to offer HTTP request/response validation and coercion
using [prismatic/schema][schema].

## Usage

Don't. But if you insist:

__Leiningen__ ([via Clojars](http://clojars.org/ronda/schema))

[![Clojars Project](http://clojars.org/ronda/schema/latest-version.svg)](http://clojars.org/ronda/schema)

__REPL__

```clojure
(require '[ronda.schema :as r]
         '[schema.core :as s])
```

## Middleware

A handler can be wrapped using per-method request/response schemas:

```clojure
(def app
  (-> (fn [{:keys [params]}]
        (let [{:keys [name id]} params]
          {:status 200,
           :body (format "Hello, %s! (ID: %d)" name id)}))
      (r/wrap-schema
        {:get {:params {:name s/Str, :id s/Int}
               :constraint {:params {:name (s/pred seq 'name-not-empty?)}}
               :responses {200 {:body s/Str}}}})))
```

The resulting function will perform a variety of operations.

### Request Validation + Coercion

__Method Validation__

```clojure
(app {:request-method :post})
;; => {:status 405,
;;     :headers {},
;;     :ronda/error {:error :method-not-allowed, ...}
;;     :body ":method-not-allowed\n{:request-method (not (#{:get} :post))}""}
```

__Request Format Validation__

```clojure
(app {:request-method :get,
      :query-params {:name "you"}})
;; => {:status 400,
;;     :headers {},
;;     :ronda/error {:error :request-validation-failed, ...},
;;     :body ":request-validation-failed\n{:params {:id missing-required-key}}"}
```

__Request Semantics Validation__

```clojure
(app {:request-method :get
      :query-params {:name "", :id 0}})
;; => {:status 422,
;;     :headers {},
;;     :ronda/error {:error :request-constraint-failed, ...}
;;     :body ":request-constraint-failed\n{:params {:name (not (name-not-empty?  \"\"))}}"
```

__Request Coercion__

```clojure
(app {:request-method :get,
      :query-params {:name "you", :id "1234"}})
;; => {:status 200,
;;     :headers {},
;;     :body "Hello, you! (ID: 1234)"}
```

## License

Copyright &copy; 2015 Yannick Scherer

Distributed under the Eclipse Public License either version 1.0 or (at
your option) any later version.

[schema]: https://github.com/prismatic/schema
