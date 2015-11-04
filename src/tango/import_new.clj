(ns tango.import-new
  (:require [clj-time.coerce :as tcr]
            [clj-time.format :as tf]
            [clojure.data.xml :as xml]
            [clojure.xml :as cxml]
            [clojure.zip :as zip]
            [clojure.data.zip.xml :as zx]))

;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;
;; Utils
(defn- to-number [s]
  {:pre [(string? s)]}
  (let [prepared-string (clojure.string/replace s #" " "")]
    (cond (re-seq #"^[-+]?\d*[\.,]\d*$" prepared-string)
          (Double/parseDouble (clojure.string/replace prepared-string #"," "."))
          (re-seq #"^[-+]?\d+$" prepared-string)
          (Integer/parseInt (clojure.string/replace prepared-string #"\+" ""))
          :else s)))

;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;
;; Makers

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

(defn make-competition-data
  [name date location]
  {:competition/name name
   :competition/date date ;(tcr/to-date (tc/date-time 2014 11 22))
   :competition/location location})

(defn make-adjudicator
  [id name country]
  {:adjudicator/id id
   :adjudicator/name name
   :adjudicator/country country})

;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;
;; Xml import

(defn- get-name-attr [loc]
  (zx/attr loc :Name))

(defn- adjudicators->map [adjudicator-loc id-generator-fn]
  (assoc
      (make-adjudicator
       (id-generator-fn)
       (zx/attr adjudicator-loc :Name)
       (zx/attr adjudicator-loc :Country))
    :dp/temp-id (to-number (zx/attr adjudicator-loc :Seq))))

(defn- competition-data->map [xml-loc]
  (make-competition-data
   (get-name-attr xml-loc)
   (tcr/to-date
    (tf/parse (tf/formatter "yyyy-MM-dd")
              (zx/attr xml-loc :Date)))
   (zx/attr xml-loc :Place)))

(defn- get-adj-number-attr [loc]
  (to-number (zx/attr loc :AdjNumber)))

(defn- panel->map [panel-loc id-generator-fn adjudicators]
  (for [panel panel-loc]
    (dissoc
     (first (filter #(= (get-adj-number-attr panel) (:dp/temp-id %))
                    adjudicators))
     :dp/temp-id)))

(defn- get-seq-attr
  [loc]
  (to-number (zx/attr loc :Seq)))

(defn- get-number-attr [loc]
  (to-number (zx/attr loc :Number)))

(defn- adjudicator-panel-list->map
  [xml-loc id-generator-fn adjudicators]
  (filter
   ;; Adjudicators can be empty and if so its an empty panel and should not
   ;; be used
   (fn [adjudicators] (seq (:adjudicator-panel/adjudicators adjudicators)))
   (for [panel xml-loc]
     {:adjudicator-panel/name (str (inc (get-seq-attr panel)))
      :adjudicator-panel/id (id-generator-fn)          ;(get-seq-attr-no-inc panel)
      :adjudicator-panel/adjudicators
      (into [] (panel->map (zx/xml-> panel :PanelAdj) id-generator-fn adjudicators))})))

(defn- couple->map [couples-loc id-generator-fn]
  (for [couple couples-loc]
    {:participant/name (get-name-attr couple)
     :participant/club (zx/attr couple :Club)
     :participant/number (get-number-attr couple)
     :participant/id (id-generator-fn)
     ;:competitor/position (get-seq-attr couple)
     }))

(defn- dance-list->map [dances-loc]
  (for [dance dances-loc]
    {:dance/name (get-name-attr dance)}))

(defn- class-list->map [classes-loc id-generator-fn]
  (for [class classes-loc]
    {:class/name (zx/attr class :Name)
     :class/position (inc (get-seq-attr class))
     ;:class/adjudicator-panel (to-number (zx/attr class :AdjPanel))
     :class/starting (into [] (couple->map (zx/xml-> class :StartList :Couple) id-generator-fn))
     :class/dances (into [] (dance-list->map (zx/xml-> class :DanceList :Dance)))
     :class/remaining []
     :class/rounds []
     ;:class/results [] ;(into [] (result-list->map (zx/xml-> class :Results :Result)))
     }))

;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;
;; Import API

(defn adjudicators-xml->map [xml id-generator-fn]
  (mapv
   #(adjudicators->map % id-generator-fn)
   (zx/xml-> xml :AdjPanelList :AdjList :Adjudicator)))

(defn adjudicator-panels-xml->map [xml id-generator-fn adjudicators]
  (adjudicator-panel-list->map (zx/xml-> xml :AdjPanelList :PanelList :Panel) id-generator-fn adjudicators))

(defn classes-xml->map [xml id-generator-fn]
  (class-list->map (zx/xml-> xml :ClassList :Class) id-generator-fn))

(defn competition-data-xml->map [xml]
  (competition-data->map (first (zx/xml-> xml :CompData))))

(defn competition-xml->map [xml id-generator-fn]
  (let [comp-data (competition-data-xml->map xml)
        dp-adjudicators (adjudicators-xml->map xml id-generator-fn)]
    (make-competition
     (:competition/name comp-data)
     (:competition/date comp-data)
     (:competition/location comp-data)
     (adjudicator-panels-xml->map xml id-generator-fn dp-adjudicators)
     (mapv #(dissoc % :dp/temp-id) dp-adjudicators)
     []
     [])))

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


