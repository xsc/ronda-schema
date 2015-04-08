(defproject ronda/schema "0.1.1"
  :description "Request/Response Schemas"
  :url "https://github.com/xsc/ronda-schema"
  :license {:name "MIT License"
            :url "http://xsc.mit-license.org"}
  :dependencies [[org.clojure/clojure "1.7.0-alpha5"]
                 [org.clojure/tools.reader "0.8.16"]
                 [prismatic/schema "0.4.0"]
                 [potemkin "0.3.13"]]
  :profiles {:dev {:dependencies [[midje "1.7.0-SNAPSHOT"]
                                  [org.clojure/math.combinatorics "0.1.1"]
                                  [joda-time "2.7"]]
                   :plugins [[lein-midje "3.1.3"]
                             [codox "0.8.10"]]
                   :codox {:project {:name "ronda/schema"}
                           :src-dir-uri "https://github.com/xsc/ronda-schema/blob/master/"
                           :src-linenum-anchor-prefix "L"
                           :defaults {:doc/format :markdown}}}
             :perf {:dependencies [[criterium "0.4.3"]]
                    :test-paths ^:replace ["perf"]
                    :jvm-opts ^:replace []}}
  :aliases {"test" ["midje"]
            "perf" ["with-profile" "+perf" "test" ":only" "benchmark"]}
  :pedantic? :abort)
