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

(defn marks->map [marks-loc adjudicators]
  (for [mark marks-loc]
    {:judging/adjudicator
     (first (filter
             #(= (:adjudicator/position %)
                 (get-seq-attr mark))
             adjudicators))
     :juding/marks [{:mark/x (= (zx/attr mark :X) "X")}]}))

(defn mark-list->map [result-couple-loc adjudicators]
  (for [couple result-couple-loc]
    {:result/participant-number (get-number-attr couple)
     :result/recalled
     (condp = (zx/attr couple :Recalled)
       "X" :x
       "R" :r
       " " ""
       (str "unexpected recalled value"))
     :result/judgings (into [] (marks->map (zx/xml-> couple :MarkList :Mark) adjudicators))}))

(defn- result-adjudicator [adjudicator-panels-loc]
  (filter
   (fn [rec] (seq (rec :panel/adjudicators)))
   (for [panel adjudicator-panels-loc]
     {:panel/id (get-seq-attr panel)
      :panel/adjudicators
      [:todo]                ;(panel->map (zx/xml-> panel :PanelAdj))
      })))

(defn- result-list->map [result-loc]
  (for [result result-loc]
    (let [adjudicators (result-adjudicator (zx/xml-> result :AdjList :Adjudicator))]
      {;; TODO beh;ver sparas n[gon stans
       ;:result/round (zx/attr result :Round)
       ;:result/dance (first (dance-list->map (zx/xml-> result :DanceList :Dance)))
       :result/adjudicators (into [] adjudicators)
       :result/results (into [] (mark-list->map (zx/xml-> result :ResultArray :Couple) adjudicators))})))

(defn- class-list->map [classes-loc id-generator-fn]
  (for [class classes-loc]
    {:class/name (zx/attr class :Name)
     :class/position (inc (get-seq-attr class))
     ;:class/adjudicator-panel (to-number (zx/attr class :AdjPanel))
     :class/starting (into [] (couple->map (zx/xml-> class :StartList :Couple) id-generator-fn))
     :class/dances (into [] (dance-list->map (zx/xml-> class :DanceList :Dance)))
     :class/remaining []
     :class/rounds []
     :class/results (into [] (result-list->map (zx/xml-> class :Results :Result)))
     ;:dp/temp-class-id (inc (get-seq-attr class))
     }))

;; TODO - each event need to get a class position i.e. witch number of event it is
;; for the specific class.
;; TODO - events with class number 0 is used as comments in DP, would be better to have a comment entity
;; (defn event-list->map [events-loc]
;;   (for [event events-loc]
;;     {:event/position (get-seq-attr event)
;;      :event/class-number (to-number (zx/attr event :ClassNumber))
;;      :event/number (if (= "" (zx/attr event :EventNumber)) -1 (to-number (zx/attr event :EventNumber)))
;;      :event/time (zx/attr event :Time)
;;      :event/comment (zx/attr event :Comment)
;;      :event/adjudicator-panel (to-number (zx/attr event :AdjPanel))
;;      :event/heats (to-number (zx/attr event :Heats))
;;      :event/round (round-value->key (to-number (zx/attr event :Round)))
;;      :event/status (to-number (zx/attr event :Status))
;;      :event/start-order (to-number (zx/attr event :Startorder))
;;      :event/recall (to-number (zx/attr (first (zx/xml-> event :RecallList :Recall)) :Recall))
;;      :event/dances (vec (dance-list->map (zx/xml-> event :DanceList :Dance)))}))

(defn round-value->key [val]
  (get
   [:none
    :normal-x :semifinal-x :final-x :b-final-x :retry-x :second-try-x
    :normal-1-5 :semifinal-1-5 :retry-1-5 :second-try-1-5
    :normal-3d :semifinal-3d :retry-3d :second-try-3d
    :normal-a+b :semifinal-a+b :final-a+b :b-final-a+b :retry-a+b :second-try-a+b
    :presentation]
   val
   :unknown-round-value))

;; TODO - Activity stuff need to be parsed
(defn- round-list->map [rounds-loc id-generator-fn]
  (for [round rounds-loc]
    {:dp/temp-class-id (to-number (zx/attr round :ClassNumber))
                                        ;:round/activity nil ;[example-activity-1]
     ;; TODO - fix activity
     :activity/number (if (= "" (zx/attr round :EventNumber)) -1 (to-number (zx/attr round :EventNumber)))

     ;; TODO - Post process, need to get this from the previous round
     ;:round/starting [] ;[example-participant-1]

     ;; TODO - parsa time and plus with compdate
     ;:round/start-time (zx/attr round :Time) ;(tcr/to-date)
 
     ;; TODO - PP
     ;; Save the id of the adjudicator panel to be able to look it up in post processing.
     ;; Subtract 3 since the 'index' in the file refer to a UI index witch is 3 of from
     ;; the Adjudicator index in this file beeing parsed.
     :round/panel {:dp/panel-id (- (to-number (zx/attr round :AdjPanel)) 3)}

     ;; TODO - PP need to get this from class
     ;:round/results [] ;[example-result-1]
     :round/recall (to-number (zx/attr (first (zx/xml-> round :RecallList :Recall)) :Recall))
     
     ;; Index is set in PP
     :round/index -1 ;; the rounds number in its class

     :round/heats (to-number (zx/attr round :Heats))
     :round/status (if (= 1 (to-number (zx/attr round :Status))) :completed :not-started)
     :round/dances (vec (dance-list->map (zx/xml-> round :DanceList :Dance))) ;[example-dance-1]
     
     ;; CONSIDER - maybe this should be left as a number for DB and then up to any presenter to parse?
     :round/type (round-value->key (to-number (zx/attr round :Round)))}))

;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;
;; Post process
(defn round-list-post-process [rounds classes]
  (vec
   (flatten
    (for [[class-id round-group] (group-by :dp/temp-class-id rounds)]
      (let [rounds-class (first (filter #(= class-id (:class/position %)) classes))]
        (reduce-kv
         (fn [post-processed-rounds k round]
           (conj post-processed-rounds
                 (merge round {:round/index k})))
         []
         round-group))))))

(defn class-list-post-process [classes])

;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;
;; Import API

(defn adjudicators-xml->map [xml id-generator-fn]
  (mapv
   #(adjudicators->map % id-generator-fn)
   (zx/xml-> xml :AdjPanelList :AdjList :Adjudicator)))

(defn adjudicator-panels-xml->map [xml id-generator-fn adjudicators]
  (adjudicator-panel-list->map (zx/xml-> xml :AdjPanelList :PanelList :Panel) id-generator-fn adjudicators))

(defn rounds-xml->map [xml id-generator-fn classes]
  (round-list-post-process
   (round-list->map (zx/xml-> xml :EventList :Event) id-generator-fn)
   classes))

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


