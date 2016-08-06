(set-env!
  :source-paths #{"src/"}
  :resource-paths #{"resources/"}
  :dependencies '[
                 ;; Core 
                 [org.clojure/clojure "1.8.0"] 
                 [org.clojure/core.async "0.2.385"] 
                 [org.clojure/tools.namespace "0.2.10"] 
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
                 [ring "1.5.0"] 
                 [ring/ring-defaults "0.2.1"] 
                 [ring-transit "0.1.6"] 
                 [hiccup "1.0.5"] 
                 [compojure "1.5.1"] 

                 ; Code cleaness tools 
                 [repetition-hunter "1.0.0"] 

                 ;; Cljs 
                 [adzerk/boot-cljs "1.7.228-1"]
                 [com.andrewmcveigh/cljs-time "0.4.0"] 
                 [cljs-http "0.1.41"] 
                 [com.cognitect/transit-cljs "0.8.239"] 
                 [alandipert/storage-atom "2.0.1"]]
  )

(require '[adzerk.boot-cljs :refer [cljs]])

(task-options!
 pom {:project 'tango
      :version "1.0.0"}
 jar {:main 'tango.core
      }
 aot {:all true}
 target {:dir #{"target"}}
 )

(deftask build "Create a standalone tango server jar." []
  (comp (cljs)
        (aot)
        (pom)
        (uber)
        (jar)
        (target)
        ))
