(ns tango.import-new
  (:require [clj-time.coerce :as tcr]
            [clj-time.format :as tf]
            [clj-time.core :as t]
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
   :competition/activities activites ;[example-round-1]
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
     {:dp/panel-id (get-seq-attr panel)
      :adjudicator-panel/name (str (inc (get-seq-attr panel)))
      :adjudicator-panel/id (id-generator-fn)          ;(get-seq-attr-no-inc panel)
      :adjudicator-panel/adjudicators
      (into [] (panel->map (zx/xml-> panel :PanelAdj) id-generator-fn adjudicators))})))

(defn- couple->map [couples-loc id-generator-fn]
  (for [couple couples-loc]
    {:participant/name (get-name-attr couple)
     :participant/club (zx/attr couple :Club)
     :participant/number (get-number-attr couple)
     :participant/id (id-generator-fn)}))

(defn- dance-list->map [dances-loc]
  (for [dance dances-loc]
    {:dance/name (get-name-attr dance)}))

(defn marks->map [marks-loc adjudicators]
  (for [mark marks-loc]
    {:dp/temp-local-adjudicator (get-seq-attr mark)
     :judging/adjudicator :todo
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
   :dp/temp-id
   (for [panel adjudicator-panels-loc]
     {:dp/result-local-panel-id (get-seq-attr panel)
      :dp/temp-id (get-number-attr panel)})))

(defn- result-list->map [result-loc]
  (for [result result-loc]
    (let [adjudicators (result-adjudicator (zx/xml-> result :AdjList :Adjudicator))]
      {:result/adjudicators (into [] adjudicators)
       :result/results (into [] (mark-list->map (zx/xml-> result :ResultArray :Couple) adjudicators))})))

(defn- class-list->map [classes-loc id-generator-fn]
  (for [class classes-loc]
    {:class/name (zx/attr class :Name)
     :class/position (inc (get-seq-attr class))
     :class/adjudicator-panel {:dp/temp-id (dec (to-number (zx/attr class :AdjPanel)))}
     :class/starting (into [] (couple->map (zx/xml-> class :StartList :Couple) id-generator-fn))
     :class/dances (into [] (dance-list->map (zx/xml-> class :DanceList :Dance)))
     :class/remaining []
     :class/rounds []
     :class/results (into [] (result-list->map (zx/xml-> class :Results :Result)))}))

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

(defn make-activity [name number comment id position source-id]
  {:activity/name name ;"Round 1"
   :activity/number number ;"1A"
   :activity/comment comment ;"Comment"
   :activity/id id ;"1"
   :activity/position position ;"1"
   :activity/source-id source-id})

;; comment:
 ;; <Event Seq="0" ClassNumber="0" DanceQty="0" EventNumber="" Time="" 
;; Comment="FREESTYLE" AdjPanel="0" Heats="1" Round="0" Status="0" Startorder="0">
(defn- round-list->map [rounds-loc id-generator-fn]
  (for [round rounds-loc]
    (let [round-id (id-generator-fn)]
      {:dp/temp-class-id (to-number (zx/attr round :ClassNumber))
       
       :round/id round-id
       
       :temp/activity (assoc (make-activity
                              ;; Post processed
                              ""
                              ;; Events that represent comments do not have an EventNumber and do now get -1
                              (if (= "" (zx/attr round :EventNumber)) -1 (to-number (zx/attr round :EventNumber)))
                              (zx/attr round :Comment)
                              (id-generator-fn)
                              (inc (get-seq-attr round))
                              ;; Put real source here, needs to be done in pp
                              round-id
                              )
                        :dp/temp-class-id (to-number (zx/attr round :ClassNumber)))

       :round/number (if (= "" (zx/attr round :EventNumber)) -1 (to-number (zx/attr round :EventNumber)))

       ;; Post process, need to get this from the previous round
       :round/starting []

       ;; Post process, parse time and plus with compdate
       :round/start-time (zx/attr round :Time)
       
       ;; Save the id of the adjudicator panel to be able to look it up in post processing.
       ;; Subtract 3 since the 'index' in the file refer to a UI index witch is 3 of from
       ;; the Adjudicator index in this file beeing parsed.
       :round/panel {:dp/panel-id (- (to-number (zx/attr round :AdjPanel)) 3)}

       ;; Post process
       :round/results []
       :round/recall (to-number (zx/attr (first (zx/xml-> round :RecallList :Recall)) :Recall))
       
       ;; Index is set in Post Process
       :round/index -1 ;; the rounds number in its class

       :round/heats (to-number (zx/attr round :Heats))
       :round/status (if (= 1 (to-number (zx/attr round :Status))) :completed :not-started)
       :round/dances (vec (dance-list->map (zx/xml-> round :DanceList :Dance))) ;[example-dance-1]
       
       ;; CONSIDER - maybe this should be left as a number for DB and then up to any presenter to parse?
       :round/type (round-value->key (to-number (zx/attr round :Round)))})))

;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;
;; Post process

(defn prep-class-result [results result-panel adjudicators]
  (for [result (:result/results results)]
    (merge
     result
     {:result/judgings
      (for [judging (:result/judgings result)]
        (dissoc
         (merge judging
                {:judging/adjudicator
                 (let [temp-id (:dp/temp-id
                                (first
                                 (filter #(= (:dp/temp-local-adjudicator judging)
                                             (:dp/result-local-panel-id %))
                                         result-panel)))]
                   (:adjudicator/id (first (filter #(= temp-id (:dp/temp-id %)) adjudicators))))})
         :dp/temp-local-adjudicator))})))

(defn prep-round-starting [prev-results participants]
  (vec
   (remove
    nil?
    (for [prev-result prev-results]
      (if (contains? #{:r :x} (:result/recalled prev-result))
        (first (filter
                #(= (:result/participant-number prev-result)
                    (:participant/number %))
                participants)))))))

(defn start-time-to-date [time-str date]
  (let [[hh mm] (map to-number (clojure.string/split time-str #":"))]
    (if (and (number? hh) (number? mm))
      (tcr/to-date (t/plus (tcr/to-date-time date) (t/hours hh) (t/minutes mm)))
      nil)))

;; TODO - add :class/remaining
(defn class-list-post-process [classes rounds adjudicators panels competition-date]
  (for [class classes]
    (let [processed-rounds
          (reduce
           (fn [res round]
             (conj
              res
              (dissoc
               (merge
                round
                {:round/index (count res)
                 :round/results (vec (prep-class-result
                                      (get (:class/results class) (count res))
                                      (:result/adjudicators
                                       (first (:class/results class)))
                                      adjudicators))
                 :round/panel (dissoc
                               (first (filter
                                       #(= (:dp/panel-id (:round/panel round)) (:dp/panel-id %))
                                       panels))
                               :dp/panel-id)
                 :round/starting (if (= (count res) 0)
                                   (:class/starting class)
                                   (prep-round-starting
                                    (:round/results (last res))
                                    (:class/starting class)))
                 :round/start-time (start-time-to-date (:round/start-time round) competition-date)})
               :dp/temp-class-id
               :temp/activity)))
           []
           (filter #(= (:class/position class) (:dp/temp-class-id %)) rounds))]
      (dissoc
       (merge class
              {:class/rounds processed-rounds
               
               :class/adjudicator-panel (dissoc
                                         (first (filter #(= (:dp/temp-id (:class/adjudicator-panel class))
                                                            (:dp/panel-id %))
                                                        panels))
                                         :dp/panel-id)

               ;; Remaining participants with a result of X or R has already been calculated
               :class/remaining (:round/starting (last processed-rounds))})
       :class/results))))

;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;
;; Import API

(defn adjudicators-xml->map [xml id-generator-fn]
  (mapv
   #(adjudicators->map % id-generator-fn)
   (zx/xml-> xml :AdjPanelList :AdjList :Adjudicator)))

(defn adjudicator-panels-xml->map [xml id-generator-fn adjudicators]
  (adjudicator-panel-list->map (zx/xml-> xml :AdjPanelList :PanelList :Panel) id-generator-fn adjudicators))

(defn rounds-xml->map [xml id-generator-fn]
  (round-list->map (zx/xml-> xml :EventList :Event) id-generator-fn))

(defn classes-xml->map [xml id-generator-fn]
  (class-list->map (zx/xml-> xml :ClassList :Class) id-generator-fn))

(defn competition-data-xml->map [xml]
  (competition-data->map (first (zx/xml-> xml :CompData))))

(defn make-activities [raw-activities classes rounds]
  (reduce
   (fn [result activity]
     (conj
      result
      (dissoc
       (merge activity
              {:activity/name
               (if-let [name (:class/name
                              (first
                               (filter #(= (:class/position %) (:dp/temp-class-id activity)) classes)))]
                 name
                 "")

               :activity/source 
               (first
                (filter #(= (:activity/source-id activity) (:round/id %)) rounds))
                                 })
       :dp/temp-class-id
       :activity/source-id)))
   []
   raw-activities))

(defn competition-xml->map [xml id-generator-fn]
  (let [comp-data (competition-data-xml->map xml)
        dp-adjudicators (adjudicators-xml->map xml id-generator-fn)
        dp-classes (classes-xml->map xml id-generator-fn)
        dp-rounds (rounds-xml->map xml id-generator-fn)
        dp-panels (adjudicator-panels-xml->map xml id-generator-fn dp-adjudicators)
        classes (class-list-post-process
                 dp-classes
                 dp-rounds
                 dp-adjudicators
                 dp-panels
                 (:competition/date comp-data))]
    (make-competition
     (:competition/name comp-data)
     (:competition/date comp-data)
     (:competition/location comp-data)
     (mapv #(dissoc % :dp/panel-id) dp-panels) ;(adjudicator-panels-xml->map xml id-generator-fn dp-adjudicators)
     (mapv #(dissoc % :dp/temp-id) dp-adjudicators)
     (make-activities (mapv :temp/activity dp-rounds) classes (mapcat :class/rounds classes))
     classes)))


