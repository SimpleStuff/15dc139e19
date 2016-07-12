(set-env!
  :dependencies '[;; Core
                 [org.clojure/clojure "1.8.0"]
                 [org.clojure/core.async "0.2.374"]
                 [org.clojure/tools.namespace "0.2.10"]
                 [org.clojure/clojurescript "1.8.40"]
                 [org.clojure/core.match "0.3.0-alpha4"]

                 [com.datomic/datomic-free "0.9.5350"]
                 [datascript "0.15.0"]

                 [com.cognitect/transit-clj "0.8.285"]

                 ;; Utils
                 [com.stuartsierra/component "0.3.1"]
                 [org.clojure/data.xml "0.0.8"]
                 [org.clojure/data.zip "0.1.1"]
                 [clj-time "0.11.0"]
                 [prismatic/schema "1.1.1"]

                 ;; Logging
                 [com.taoensso/timbre "4.3.1"]

                 ;; Web UI
                 [org.omcljs/om "1.0.0-alpha32"]
                 [com.taoensso/sente "1.8.1"]

                 [http-kit "2.1.19"]

                 [ring                      "1.4.0"]
                 [ring/ring-defaults        "0.2.0"]
                 [ring-transit "0.1.4"]

                 [hiccup "1.0.5"]

                 [compojure "1.5.0"]

                 ; Code cleaness tools
                 [repetition-hunter "1.0.0"]

                 ;; Cljs
                 [com.andrewmcveigh/cljs-time "0.4.0"]
                 [cljs-http "0.1.40"]
                 [com.cognitect/transit-cljs "0.8.237"]
                 [alandipert/storage-atom "1.2.4"]]
  :source-paths #{"src/"})

(task-options!
 pom {:project 'tango
      :version "1.0.0"}
 jar {:main 'tango.core}
 aot {:all true}
 )

(deftask build "Create a standalone tango server jar." []
  (comp (aot)
        (pom)
        (uber)
        (jar)
        ))
