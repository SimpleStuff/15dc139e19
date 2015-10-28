(ns tango.import-new
  (:require [clj-time.coerce :as tcr]
            [clj-time.format :as tf]
            [clojure.data.xml :as xml]
            [clojure.xml :as cxml]
            [clojure.zip :as zip]
            [clojure.data.zip.xml :as zx]))

;; todo - prefix
(defn make-competition
  [name date location panels adjudicators activites classes]
  {:competition/name name
   :competition/date date ;(tcr/to-date (tc/date-time 2014 11 22))
   :competition/location location
   :competition/panels panels ;[]
   :competition/adjudicators adjudicators ;[example-adjudicator-1]
   :competitor/activities activites ;[example-round-1]
   :competition/classes classes ;[example-class-1]
   })

(defn- get-name-attr [loc]
  (zx/attr loc :Name))

(defn adjudicators->map [xml-loc]
  (for [adjudicator (zx/xml-> xml-loc :AdjList :Adjudicator)]
    {:adjudicator/name (zx/attr adjudicator :Name)}))

(defn competition->map [xml-loc]
  (make-competition
   (get-name-attr xml-loc)
   (tcr/to-date
    (tf/parse (tf/formatter "yyyy-MM-dd")
              (zx/attr xml-loc :Date)))
   (zx/attr xml-loc :Place)
   []
   []
   []
   []))

(defn competition-xml->map [xml]
  (first (zx/xml-> xml :CompData)))

;; (defn competition->map [loc]
;;   (let [competition-data (first (zx/xml-> loc :CompData))
;;         classes (vec (class-list->map (zx/xml-> loc :ClassList :Class)))]
;;     {:competition/name (get-name-attr competition-data)
;;      :competition/date (tcr/to-date
;;                         (tf/parse (tf/formatter "yyyy-MM-dd")
;;                                   (zx/attr competition-data :Date)))
;;      :competition/location (zx/attr competition-data :Place)
;;      :competition/classes classes
;;      :competition/events (event-list-post-process
;;                           (vec (event-list->map (zx/xml-> loc :EventList :Event)))
;;                           classes)
;;      :competition/adjudicators
;;      (if-let [adjudicator-list-loc (zx/xml-> loc :AdjPanelList :AdjList :Adjudicator)]
;;        (into [] (adjudicator-list2->map adjudicator-list-loc))
;;        [])
;;     :competition/adjudicator-panels
;;      (into [] (adjudicator-panel-list->map (zx/xml-> loc :AdjPanelList :PanelList :Panel)))
;;      }))


