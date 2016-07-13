(defproject tango "0.1.5-SNAPSHOT"
  :description "Tango"
  :url "http://example.com/FIXME"
  :license {:name "Eclipse Public License"
            :url "http://www.eclipse.org/legal/epl-v10.html"}

  :dependencies [;; Core
                 [org.clojure/clojure "1.8.0"]
                 [org.clojure/core.async "0.2.385"]
                 [org.clojure/tools.namespace "0.2.10"]
                 ;[org.clojure/clojurescript "1.8.51"]
                 [org.clojure/clojurescript "1.9.93"]
                 [org.clojure/core.match "0.3.0-alpha4"]

                 [com.datomic/datomic-free "0.9.5385"]
                 [datascript "0.15.1"]

                 [com.cognitect/transit-clj "0.8.285"]

                 ;; Utils
                 [com.stuartsierra/component "0.3.1"]
                 [org.clojure/data.xml "0.0.8"]
                 [org.clojure/data.zip "0.1.2"]
                 [clj-time "0.12.0"]
                 [prismatic/schema "1.1.2"]
                 
                 ;; Logging
                 [com.taoensso/timbre "4.6.0"]

                 ;; Web UI
                 [org.omcljs/om "1.0.0-alpha40"]
                 [com.taoensso/sente "1.9.0"]

                 [http-kit "2.2.0"]

                 [ring                      "1.5.0"]
                 [ring/ring-defaults        "0.2.1"]
                 [ring-transit "0.1.6"]

                 [hiccup "1.0.5"]

                 [compojure "1.5.1"]

                 ; Code cleaness tools
                 [repetition-hunter "1.0.0"]

                 ;; Cljs
                 [com.andrewmcveigh/cljs-time "0.4.0"]
                 [cljs-http "0.1.41"]
                 [com.cognitect/transit-cljs "0.8.239"]
                 [alandipert/storage-atom "2.0.1"]]

  :plugins [[lein-figwheel "0.5.4-7"]
            [lein-cljsbuild "1.1.3"]
            [lein-ancient "0.6.10"]
            [lein-hiera "0.9.5"]
            [lein-kibit "0.1.2"]
            [jonase/eastwood "0.2.3"]
            [lein-externs "0.1.5"]
            [michaelblume/lein-marginalia "0.9.0"]]

  :source-paths ["src"]

  :clean-targets ^{:protect false} ["resources/public/js/out"
                                    "resources/public/js/runtime.js"
                                    "resources/public/js/app.js"
                                    "resources/public/js/adj.js"
                                    "resources/public/js/speaker.js"
                                    "target"]

  :test-paths ["test" "test/services"]

  :hiera {:path "specs/tango-hierarchy.png"
          :vertical true
          :show-external false
          :cluster-depth 0
          :trim-ns-prefix true
          :ignore-ns #{}}

  ;; On externs
  ;http://www.lispcast.com/clojurescript-externs
  :cljsbuild {:builds
              [
               ;{:id           "dev"
               ; :source-paths ["src/tango/cljs" "src"]
               ;
               ; :figwheel     {:on-jsload "tango.cljs.client/on-js-reload"}
               ;
               ; :compiler     {:main          tango.cljs.client
               ;                :asset-path    "js/out"
               ;                :output-to     "resources/public/js/app.js"
               ;                :output-dir    "resources/public/js/out"
               ;                ;:source-map    "resources/public/js/out.js.map"
               ;                ;; PROD
               ;                :optimizations :advanced
               ;                :pretty-print  false
               ;
               ;                ;; DEV
               ;                ;:source-map true
               ;                ;:pretty-print  true
               ;                ;:optimizations :none
               ;                }}

               {:id           "adj"
                :source-paths ["src/tango/cljs/adjudicator" "src"]
                :figwheel     {:on-jsload "tango.cljs.adjudicator.core/on-js-reload"}
                :compiler     {:main       tango.cljs.adjudicator.core
                               :asset-path "js/out/adj"
                               :output-to  "resources/public/js/adj.js"
                               :output-dir "resources/public/js/out/adj"
                               ;:source-map    "resources/public/js/out.js.map"
                               ;; PROD
                               ;:optimizations :advanced
                               :optimizations :none
                               :pretty-print  false

                               ;; DEV
                               ;:source-map true
                               ;:pretty-print  true
                               ;:optimizations :none
                               }}

               {:id           "runtime"
                :source-paths ["src/tango/cljs/runtime" "src"]
                :figwheel     {:on-jsload "tango.cljs.adjudicator.core/on-js-reload"}
                :compiler     {:main       tango.cljs.runtime.core
                               :asset-path "js/out/runtime"
                               :output-to  "resources/public/js/app.js"
                               :output-dir "resources/public/js/out/runtime"
                               ;:source-map    "resources/public/js/out.js.map"
                               ;; PROD
                               :optimizations :none
                               :pretty-print  false

                               ;; DEV
                               ;:source-map true
                               ;:pretty-print  true
                               ;:optimizations :none
                               }}

               ;; Not building this until updated to new Compose iteration
               ;{:id           "speaker"
               ; :source-paths ["src/tango/cljs/speaker" "src"]
               ; :figwheel     {:on-jsload "tango.cljs.speaker.core/on-js-reload"}
               ; :compiler     {:main       tango.cljs.speaker.core
               ;                :asset-path "js/out/speaker"
               ;                :output-to  "resources/public/js/speaker.js"
               ;                :output-dir "resources/public/js/out/speaker"
               ;
               ;                ;; PROD
               ;                :optimizations :advanced
               ;                :pretty-print  false
               ;
               ;                ;; DEV
               ;                ;:source-map true
               ;                ;:pretty-print  true
               ;                ;:optimizations :none
               ;                }}

               ;; This next build is an compressed minified build for
               ;; production. You can build this with:
               ;; lein cljsbuild once min
               {:id           "min"
                :source-paths ["src"]
                :compiler     {:main          tango.cljs.runtime.core
                               :output-to     "resources/public/js/app.js"
                               :asset-path    "js/out"
                               :optimizations :advanced
                               :pretty-print  false}}]}
  
  :figwheel {
             :css-dirs ["resources/public/css"]}

  :main ^:skip-aot tango.core
  :target-path "target/%s"
  :profiles {:uberjar {:aot :all
                       :hooks     [leiningen.cljsbuild]}}

  :aliases {"quality"    ["do"
                          ;; excluding constant test due to not working well with logging
                          ["eastwood" "{:exclude-linters [:constant-test] :exclude-namespaces [tango.datomic-storage]}"]
                          ["kibit"]
                          ["ancient"]]

            "doc"        ["do"
                          ["hiera"]
                          ["marg" "src" "test" "specs" "--dir" "./doc"]]

            "pre-commit" ["do"
                          ["test"]
                          ["quality"]]})

;; fixa simple check
;; https://github.com/jakemcc/lein-test-refresh
;; https://github.com/asciidoctor/asciidoctor-lein-plugin
