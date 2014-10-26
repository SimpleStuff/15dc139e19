(defproject tango "0.1.0-SNAPSHOT"
  :description "FIXME: write description"
  :url "http://example.com/FIXME"
  :license {:name "Eclipse Public License"
            :url "http://www.eclipse.org/legal/epl-v10.html"}
  :dependencies [[org.clojure/clojure "1.6.0"]
                 [com.taoensso/sente "1.2.0"]
                 [compojure "1.2.0"]
                 [http-kit "2.1.18"]
                 [org.clojure/clojurescript "0.0-2371"]
                 [org.clojure/core.async "0.1.346.0-17112a-alpha"]
                 [om "0.8.0-alpha1"]]
  
  :plugins [[lein-cljsbuild "1.0.3"]]

  :cljsbuild {:builds [{:id "dev"
                        :source-paths ["src/tango/cljs"]
                        :compiler {:output-to "./resources/public/tango_client.js"
                                   :output-dir "./resources/public/out"
                                   :optimizations :none
                                   :source-map true}}]}
  
  :main ^:skip-aot tango.core
  :target-path "target/%s"
  :profiles {:uberjar {:aot :all}})
