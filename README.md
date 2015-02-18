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

A handler can be wrapped using per-method [request/response schemas](#schemas)
and [`ronda.schema/wrap-schema`](https://xsc.github.io/ronda-schema/ronda.schema.html#var-wrap-schema):

```clojure
(def schema
 {:params {:name s/Str, :id s/Int}
  :constraint {:params {:name (s/pred seq 'name-not-empty?)}}
  :responses {200 {:body s/Str
                   :constraint {:body (s/pred seq 'body-not-empty?)}}}})

(def app
  ;; for testing purposes, we allow a key `:f` in the request
  ;; to optionally transform the response.
  (-> (fn [{:keys [params f] :or {f identity}}]
        (let [{:keys [name id]} params]
          (f {:status 200,
              :body (format "Hello, %s! (ID: %d)" name id)})))
      (r/wrap-schema {:get schema})))
```

The resulting function will perform a variety of operations.

### Request Validation + Coercion

__Method Validation__

```clojure
(app {:request-method :post})
;; => {:status 405,
;;     :headers {},
;;     :ronda/error {:error :method-not-allowed, ...}
;;     :body ":method-not-allowed\n{:request-method (not (#{:get} :post))}"}
```

If the `:request-method` does not match any of the allowed ones, a HTTP 405
(Method Not Allowed) response will be returned.

__Request Format Validation__

```clojure
(app {:request-method :get, :query-params {:name "you"}})
;; => {:status 400,
;;     :headers {},
;;     :ronda/error {:error :request-validation-failed, ...},
;;     :body ":request-validation-failed\n{:params {:id missing-required-key}}"}
```

If `:params`, `:headers`, `:query-string` or `:body` don't match the respective
schema, a HTTP 400 (Bad Request) response will be returned.

__Request Constraint Validation__

```clojure
(app {:request-method :get :query-params {:name "", :id 0}})
;; => {:status 422,
;;     :headers {},
;;     :ronda/error {:error :request-constraint-failed, ...}
;;     :body ":request-constraint-failed\n{:params {:name (not (name-not-empty? \"\"))}}"
```

If the request does not match the `:constraint` of the respective schema, a HTTP
422 (Unprocessable) response will be returned.

__Request Coercion__

```clojure
(app {:request-method :get, :query-params {:name "you", :id "1234"}})
;; => {:status 200,
;;     :headers {},
;;     :body "Hello, you! (ID: 1234)"}
```

`:id` was coerced from `String` to `Long` to match the `s/Int` schema.

### Response Validation + Coercion

__Status Validation__

```clojure
(app {:request-method :get,
      :query-params {:name "you", :id 1234}
      :f #(assoc % :status 201)})
;; => {:status 500,
;;     :headers {},
;;     :ronda/error {:error :status-not-allowed, ...},
;;     :body ":status-not-allowed\n{:status (not (#{200} 201))}"}
```

If `:status` does not match one of the allowed ones, a HTTP 500 response will
be returned.

__Response Format Validation__

```clojure
(app {:request-method :get,
      :query-params {:name "you", :id 1234},
      :f #(dissoc % :body)})
;; => {:status 500,
;;     :headers {},
;;     :ronda/error {:error :response-validation-failed, ...},
;;     :body ":response-validation-failed\n{:body missing-required-key}"}
```

If `:headers` or `:body` don't match the respective schemas, a HTTP 500 response
will be returned.

__Response Constraint Validation__

```clojure
(app {:request-method :get,
      :query-params {:name "you", :id 1234},
      :f #(assoc % :body "")})
;; => {:status 500,
;;     :headers {},
;;     :ronda/error {:error :response-constraint-failed, ...},
;;     :body ":response-constraint-failed\n{:body (not (body-not-empty? \"\"))}"}
```

If the response does not match the respective `:constraint` schema, a HTTP 500
response will be returned.

__Request/Response Semantics__

It is possible to model request/response semantics using the `:semantics` key of
the response schema ([see below](#schemas)). If validation fails, a HTTP 500
response will be returned.

### Custom Error Handling

[`ronda.schema/validation-error`](https://xsc.github.io/ronda-schema/ronda.schema.html#var-validation-error)
can be used to access the error value in the response emitted by the
`wrap-schema` middleware which will have the following format:

```clojure
{:error :request-validation-failed,
 :error-form {:params {:id missing-required-key}},
 :schema {:params {Keyword Any,
                   :name java.lang.String,
                   :id Int},
          Any Any},
 :value {:params {:name "you"},
         :headers {},
         :query-params {:name "you"},
         :request-method :get}}
```

Possible error values (and default HTTP status codes):

- `:method-not-allowed` (405)
- `:request-validation-failed` (400)
- `:request-constraint-failed` (422)
- `:status-not-allowed` (500)
- `:response-validation-failed` (500)
- `:response-constraint-failed` (500)
- `:semantics-failed` (500)

These can be processed by wrapping the handler in a custom middleware à la:

```clojure
(defn wrap-custom-errors
  [handler]
  (fn [request]
    (let [response (handler request)]
      (case (r/validation-error response)
        :status-not-allowed { ... }
        ...
        response))))
```

## Schemas

### Request

TODO

### Response

TODO

## Documentation

Auto-generated documentation can be found [here](https://xsc.github.io/ronda-schema/).

## Contributing

Contributions are very welcome.

1. Clone the repository.
2. Create a branch, make your changes.
3. Make sure tests are passing by running `lein test`.
4. Submit a Github pull request.

## License

Copyright &copy; 2015 Yannick Scherer

Distributed under the Eclipse Public License either version 1.0 or (at
your option) any later version.

[schema]: https://github.com/prismatic/schema
