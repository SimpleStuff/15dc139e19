(defproject tango "0.1.0-SNAPSHOT"
  :description "Tango"
  :url "http://example.com/FIXME"
  :license {:name "Eclipse Public License"
            :url "http://www.eclipse.org/legal/epl-v10.html"}

  :dependencies [;; Core
                 [org.clojure/clojure "1.7.0"]
                 [org.clojure/core.async "0.2.374"]                 
                 [org.clojure/tools.namespace "0.2.10"]
                 [org.clojure/clojurescript "1.7.228"]
                 [org.clojure/core.match "0.3.0-alpha4"]

                 [datascript "0.14.0"]

                 ;; Utils
                 [com.stuartsierra/component "0.3.1"]
                 [org.clojure/data.xml "0.0.8"]
                 [org.clojure/data.zip "0.1.1"]
                 [clj-time "0.11.0"]
                 
                 ;; Logging
                 [com.taoensso/timbre "4.2.0"]

                 ;; Web UI
                 [org.omcljs/om "1.0.0-alpha22"]
                 [com.taoensso/sente "1.7.0"]

                 [http-kit "2.1.19"]

                 [ring                      "1.4.0"]
                 [ring/ring-defaults        "0.1.5"]

                 [compojure "1.4.0"]
                                          
                 ;[reagent "0.5.1"]
                                  
                 ; Code cleaness tools
                 [repetition-hunter "1.0.0"]

                 ;; Cljs
                 [com.andrewmcveigh/cljs-time "0.3.14"]]

  :plugins [[lein-figwheel "0.5.0-2"]
            [lein-cljsbuild "1.1.2"]
            [lein-ancient "0.6.8"]
            [lein-hiera "0.9.0"]
            [lein-kibit "0.1.2"]
            [jonase/eastwood "0.2.1"]
            [michaelblume/lein-marginalia "0.9.0"]]

  :source-paths ["src"]

  :clean-targets ^{:protect false} ["resources/public/js/out" "target"]

  :test-paths ["test" "test/services"]

  :hiera {:path "specs/tango-hierarchy.png"
          :vertical true
          :show-external false
          :cluster-depth 0
          :trim-ns-prefix true
          :ignore-ns #{}}

  :cljsbuild {:builds
              [{:id "dev"
                :source-paths ["src/tango/cljs" "src"]

                :figwheel {:on-jsload "tango.cljs.client/on-js-reload"}

                :compiler {:main tango.cljs.client
                           :asset-path "js/out"
                           :output-to     "resources/public/js/app.js"
                           :output-dir    "resources/public/js/out"
                           ;:source-map    "resources/public/js/out.js.map"
                           :source-map true
                           :optimizations :none
                           :pretty-print  true}}

               ;; This next build is an compressed minified build for
               ;; production. You can build this with:
               ;; lein cljsbuild once min
               {:id "min"
                :source-paths ["src"]
                :compiler {:output-to "resources/public/js/app.js"
                           :main tango.cljs.client
                           :optimizations :advanced
                           :pretty-print false}}]}
  
  :figwheel {
             :css-dirs ["resources/public/css"]}

  :main ^:skip-aot tango.core
  :target-path "target/%s"
  :profiles {:uberjar {:aot :all}}

  :aliases {"quality" ["do"
                       ;; excluding constant test due to not working well with logging
                       ["eastwood" "{:exclude-linters [:constant-test]}"]
                       ["kibit"]
                       ["ancient"]]

            "doc" ["do"
                   ["hiera"]
                   ["marg" "src" "test" "specs" "--dir" "./doc"]]

            "pre-commit" ["do"
                          ["test"]
                          ["quality"]]})

;; fixa simple check
;; https://github.com/jakemcc/lein-test-refresh
;; https://github.com/asciidoctor/asciidoctor-lein-plugin
