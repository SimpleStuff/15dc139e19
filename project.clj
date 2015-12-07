(defproject tango "0.1.0-SNAPSHOT"
  :description "Tango"
  :url "http://example.com/FIXME"
  :license {:name "Eclipse Public License"
            :url "http://www.eclipse.org/legal/epl-v10.html"}
  :dependencies [;; Core
                 [org.clojure/clojure "1.7.0"]
                 [org.clojure/core.async "0.1.346.0-17112a-alpha"]                 
                 [org.clojure/tools.namespace "0.2.10"]
                 [org.clojure/clojurescript "1.7.48"]
                 [org.clojure/core.match "0.3.0-alpha4"]

                 ;; Utils
                 [com.stuartsierra/component "0.2.3"]
                 [org.clojure/data.xml "0.0.8"]
                 [org.clojure/data.zip "0.1.1"]
                 [clj-time "0.8.0"]

                 ;; Web UI
                 [com.taoensso/sente "1.5.0"]
                 [com.taoensso/timbre "3.4.0"]

                 [http-kit "2.1.19"]

                 [ring                      "1.3.2"]
                 [ring/ring-defaults        "0.1.5"]

                 [compojure "1.3.4"]
                                          
                 [reagent "0.5.0"]
                                  
                 ; Code cleaness tools
                 [repetition-hunter "1.0.0"]

                 ;; Cljs
                 [com.andrewmcveigh/cljs-time "0.3.14"]
                 ]

  
  :plugins [[lein-cljsbuild "1.0.6"]
            [lein-hiera "0.9.0"]
            [lein-kibit "0.1.2"]
            [jonase/eastwood "0.2.1"]
            [michaelblume/lein-marginalia "0.9.0"]]

  :source-paths ["src"]
  :hiera {:path "specs/tango-hierarchy.png"
          :vertical true
          :show-external false
          :cluster-depth 0
          :trim-ns-prefix true
          :ignore-ns #{}}

  :cljsbuild {:builds [{:id "dev"
                        :source-paths ["src/tango/cljs" "src"]
                        :compiler {:output-to     "resources/public/js/app.js"
                                   :output-dir    "resources/public/js/out"
                                   :source-map    "resources/public/js/out.js.map"
                                   :externs       ["react/externs/react.js"]
                                   :optimizations :none
                                   :pretty-print  true}}]}
  
  :main ^:skip-aot tango.core
  :target-path "target/%s"
  :profiles {:uberjar {:aot :all}})

;; fixa simple check
;; https://github.com/jakemcc/lein-test-refresh
;; https://github.com/asciidoctor/asciidoctor-lein-plugin
