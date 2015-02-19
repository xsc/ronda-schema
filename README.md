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
 {:params     {:name s/Str, :id s/Int}
  :constraint {:params {:name (s/pred seq 'name-not-empty?)}}
  :responses  {200 {:body s/Str
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

### Response

A full response schema could look like this:

```clojure
{:headers    {"content-type" #"^text/plain"},
 :body       s/Str,
 :constraint (s/pred (comp seq :body) 'body-not-empty?)
 :semantics  (s/pred semantics? 'semantics-valid?)}
```

The following keys are possible, none are required:

- `:headers`: a schema for a map of strings.
- `:body`: a schema for the body (no type restrictions).
- `:constraint`: a schema that will be applied to the whole response.
- `:semantics`: a schema that will be applied to a map of `:request` and
  `:response`, e.g.:

        (s/pred
          (fn [{:keys [request response]}]
            (= (-> request :params :length)
               (-> response :body count)))
          'length-semantics?)

A raw response schema can be prepared for validation using
[`compile-response-schema`](https://xsc.github.io/ronda-schema/ronda.schema.html#var-compile-response-schema)
which can then be used with
[`check-single-response`](https://xsc.github.io/ronda-schema/ronda.schema.html#var-check-single-response)
for direct validation:

```clojure
(r/check-single-response
  (r/compile-response-schema {:body s/Str})
  {:status 200, :headers {}, :body ""})
;; => {:headers {}, :status 200, :body ""}
```

Error values can be distinguished using
[`error?`](https://xsc.github.io/ronda-schema/ronda.schema.html#var-error.3F):

```clojure
(r/error?
  (r/check-single-response
    (r/compile-response-schema {:body s/Str})
    {:status 200, :headers {}, :body nil}))
;; => true
```

Multiple responses can be given using a map of status/response pairs (where the
status is allowed to be the wildcard value `:*`) and compiled using
[`compile-responses`](https://xsc.github.io/ronda-schema/ronda.schema.html#var-compile-responses).
Checking is done by status inside of
[`check-response`](https://xsc.github.io/ronda-schema/ronda.schema.html#var-check-response).

### Request

TODO

## FAQs

__Q: How slow is this?__

You can clone the repository and run `lein perf` which will compare the
schema-based middleware to a custom one performing validation/coercion _on
foot_.

Runs of the benchmark show an approximate overhead of __21x__, i.e. validation
using schemas produces a _constant_ overhead that is 21 times higher than using
a direct approach.

You have to decide on a per-case basis if the actual timing values are
significant. Also, future performance improvements in the underlying [schema
library][schema] might reduce that significance.

__Q: How do I handle e.g. JSON data?__

TODO

__Q: Does it play well with [Liberator](https://github.com/clojure-liberator/liberator)?__

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
