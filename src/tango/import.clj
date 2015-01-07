(ns tango.import
  (:require [clojure.xml :as xml]
            [clj-time.coerce :as tcr]
            [clj-time.format :as tf]))

(defn- read-xml [path]
  (xml/parse (clojure.java.io/file path)))

(defn- dance-perfect-xml-competitors->competitors [dp-competitors-xml]
  (mapv #(hash-map :competitor/name (get-in % [:attrs :Name])
                   :competitor/club (get-in % [:attrs :Club])
                   :competitor/number (Integer/parseInt (get-in % [:attrs :Number])))
        dp-competitors-xml))

; Kan en klass bara ha en startlista? - make a test
(defn- dance-perfect-xml-classes->classes [dp-classes-xml]
  (mapv #(hash-map :class/name (get-in % [:attrs :Name])
                   :class/competitors
                   (dance-perfect-xml-competitors->competitors (:content (first (:content %)))))
        (:content dp-classes-xml))
  )

(defn- build-dance-perfect-xml-parts [dp-xml]
  (apply merge
         (for [node (xml-seq dp-xml)]
           (condp = (:tag node)
             :DancePerfect {:dance-perfect-xml node}
             :ClassList {:class-list-xml node}
             :CompData {:competition-data-xml node}
             nil))))

(defn dance-perfect-xml->data [dp-xml]
  (let [xml-parts (build-dance-perfect-xml-parts dp-xml)
        competition-data (:competition-data-xml xml-parts)
        classes-xml (:class-list-xml xml-parts)]
    {:dance-perfect/version (get-in (:dance-perfect-xml xml-parts) [:attrs :Version])
     :competition/name (get-in competition-data [:attrs :Name])
     :competition/location (get-in competition-data [:attrs :Place])
     :competition/date (tcr/to-date
                        (tf/parse (tf/formatter "yyyy-MM-dd")
                                  (get-in competition-data [:attrs :Date])))
     :competition/classes (dance-perfect-xml-classes->classes classes-xml)
     }))

(defn import-file [path]
  {:pre [(string? path)]}
  (let [xml-src (read-xml path)
        dance-perfect-data (dance-perfect-xml->data xml-src)]
    {:file/version (:dance-perfect/version dance-perfect-data)
     :file/content (dissoc dance-perfect-data :dance-perfect/version)
     :file/import-status :success
     :file/import-errors []}))

;http://blog.fogus.me/2011/09/08/10-technical-papers-every-programmer-should-read-at-least-twice/
