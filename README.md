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

- `:headers`: a schema for a map of strings to be matched against the response's
  `:headers` key.
- `:body`: a schema for the response body (no type restrictions).
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

A full request schema could look like the following:

```clojure
{:headers      {"content-type" #"^application/json"}
 :params       {:length s/Int}
 :query-string s/Str
 :body         {:action (s/enum :start :stop)}
 :constraint   {:length (s/pred pos? 'positive?)}
 :responses    {200 ok-schema, 409 conflict-schema}}
```

The following keys are possible, none are required:

- `:headers`: a schema for a map of strings to be matched against the request's
  `:headers` key.
- `:params`: a schema for a map of keywords that will be matched against the
  merged result of `:query-params`, `:route-params` and `:form-params`.
- `:query-string`:  a schema that will be matched against the request's query
  string.
- `body`: a schema for the request body (no type restrictions).
- `:constraint`: a schema hat will be applied to the whole request.
- `:responses`: a map of response status/schema pairs (the status is allowed to
  be the wildcard `:*` if a default schema shall be provided).

A raw request schems can be prepared for validation using
[`compile-request-schema`](https://xsc.github.io/ronda-schema/ronda.schema.html#var-compile-request-schema)
which can then be used with
[`check-single-request`](https://xsc.github.io/ronda-schema/ronda.schema.html#var-check-single-request)
for direct validation:

```clojure
(r/check-single-request
  (r/compile-request-schema {:params {:x s/Int}})
  {:request-method :get, :query-params {:x "0"}, :headers {}})
;; => {:request-method :get,
;;     :headers {},
;;     :query-params {:x "0"}
;;     :params {:x 0}}
```

As mentioned above, `:query-params`/`:route-params`/`:form-params` will always
be merged into `:params` which is also the only place that parameter coercion
will happen. Keep that in mind when writing your handlers!

Multiple requests can be compiled using a map of method/schema pairs (method is
allowed to be the wildcard `:*`) via
[`compile-requests`](https://xsc.github.io/ronda-schema/ronda.schema.html#var-compile-requests)
and subsequently checked by method in
[`check-request`](https://xsc.github.io/ronda-schema/ronda.schema.html#var-check-request).

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

Make sure that data decoding/encoding happens before and after schema validation
respectively, and validate the unencoded data, probably using the `:body` key.

[ring-json](https://github.com/ring-clojure/ring-json), for example, stores the
decoded JSON either in `:params` (`wrap-json-params`) or `:body`
(`wrap-json-body`), generating the response JSON again from `:body`. Your
middleware stack should thus look similar to the following one:

```clojure
(-> handler
    (r/wrap-schema {...})
    (ring.middleware.json/wrap-json-response {...})
    (ring.middleware.json/wrap-json-body {...}))
```

The `wrap-schema` middleware has to be closer to the bottom than any codec.

__Q: Does it play well with [Liberator](https://github.com/clojure-liberator/liberator)?__

Generally, yes. Problems arise mostly from content negotiation which causes a
Liberator resource to usually return the body as a string matching the
negotiated format:

```clojure
(require '[liberator.core :refer [resource]])

((r/wrap-schema
   (resource {:available-media-types ["application/json"]
              :handle-ok (constantly {:data "Hello!"})})
   {:get {:responses {200 {:body {:data s/Str}}}}})
 {:request-method :get, :headers {"accept" "application/json"}})
;; => {:status 500,
;;     :ronda/error {:error-form {:body (not (map? "{\"data\":\"Hello!\"}"))}, ...}
;;     ...}
```

A workaround for this would be to use `liberator.representation/ring-response`
to ensure the response is returned as-is, performing content-negotiation later
on. An abstraction over this should not be too hard to produce an would be much
appreciated.

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
