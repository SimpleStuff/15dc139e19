(defproject tango "0.1.0-SNAPSHOT"
  :description "Tango"
  :url "http://example.com/FIXME"
  :license {:name "Eclipse Public License"
            :url "http://www.eclipse.org/legal/epl-v10.html"}
  :dependencies [[org.clojure/clojure "1.6.0"]
                 [com.taoensso/sente "1.3.0"]
                 [compojure "1.2.0"]
                 [http-kit "2.1.18"]
                 [org.clojure/clojurescript "0.0-2371"]
                 [org.clojure/core.async "0.1.346.0-17112a-alpha"]
                 [reagent "0.4.3"]
                 [clj-time "0.8.0"]
                 [org.clojure/core.match "0.3.0-alpha4"]
                 [ring/ring-defaults "0.1.3"]
                 [com.stuartsierra/component "0.2.2"]
                 [org.clojure/tools.namespace "0.2.9"]
                 [org.clojure/data.xml "0.0.8"]]
  
  :plugins [[lein-cljsbuild "1.0.3"]]

  :cljsbuild {:builds [{:id "dev"
                        :source-paths ["src/tango/cljs"]
                        :compiler {:output-to     "resources/public/js/app.js"
                                   :output-dir    "resources/public/js/out"
                                   :source-map    "resources/public/js/out.js.map"
                                   :externs       ["react/externs/react.js"]
                                   :optimizations :none
                                   :pretty-print  true}}]}
  
  :main ^:skip-aot tango.core
  :target-path "target/%s"
  :profiles {:uberjar {:aot :all}})
