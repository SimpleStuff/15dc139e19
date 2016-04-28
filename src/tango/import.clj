(ns tango.import
  (:require [clj-time.coerce :as tcr]
            [clj-time.format :as tf]
            [clj-time.core :as t]
            [clojure.data.xml :as xml]
            [clojure.xml :as cxml]
            [clojure.zip :as zip]
            [clojure.data.zip.xml :as zx]
            [tango.domain :as d]))

;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;
;; Export demo
;; (print (xml/indent-str (xml/sexp-as-element
;;                                       [:AdjPanelList
;;                                        [:AdjList {:Qty "3"}
;;                                         [:Adjudicator {:Seq "0" :Name "Anders" :Countr ""}]]])))
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

(defn- to-boolean [s]
  {:pre [(string? s)]}
  (if (= "1" s)
    true
    false))

(defn- start-time-to-date [time-str date]
  (let [[hh mm] (map to-number (clojure.string/split time-str #":"))]
    (when (and (number? hh) (number? mm))
      (tcr/to-date (t/plus (tcr/to-date-time date) (t/hours hh) (t/minutes mm))))))

(defn- round-value->key [val]
  (get
   [:none
    :normal-x :semifinal-x :final-x :b-final-x :retry-x :second-try-x
    :normal-1-5 :semifinal-1-5 :retry-1-5 :second-try-1-5
    :normal-3d :semifinal-3d :retry-3d :second-try-3d
    :normal-a+b :semifinal-a+b :final-a+b :b-final-a+b :retry-a+b :second-try-a+b
    :presentation]
   val
   :unknown-round-value))

;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;
;; Attribute Utils

(defn- get-name-attr [loc]
  (zx/attr loc :Name))

(defn- get-time-attr [loc]
  (zx/attr loc :Time))

(defn- get-seq-attr [loc]
  (zx/attr loc :Seq))

(defn- get-seq-attr-as-number
  [loc]
  (to-number (get-seq-attr loc)))

(defn- increased-seq-attr [loc]
  (inc (get-seq-attr-as-number loc)))

(defn- get-adj-number-attr [loc]
  (to-number (zx/attr loc :AdjNumber)))

(defn- get-number-attr [loc]
  (to-number (zx/attr loc :Number)))

(defn- class-number-attr-as-number [loc]
  (to-number (zx/attr loc :ClassNumber)))

(defn- event-number-attr [loc]
  (zx/attr loc :EventNumber))

(defn- event-number-attr-as-number [loc]
  (to-number (event-number-attr loc)))

(defn- event-number-attr-empty? [loc]
  (= "" (event-number-attr loc)))

(defn- time-attr-to-date [loc base-date]
  (start-time-to-date (get-time-attr loc) base-date))

(defn- make-event-number [loc]
  (if (event-number-attr-empty? loc)
    -1
    (event-number-attr-as-number loc)))

(defn- get-boolean-attr [loc attr]
  (to-boolean (zx/attr loc attr)))

;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;
;; Xml import

(defn- get-options [competition-data]
  (let [get-flag (partial get-boolean-attr competition-data)
        dance-mappings [[:same-heat-all-dances :SameHeatAllDances]
                        [:random-order-in-heats :RadomHeats]
                        [:heat-text-on-adjudicator-sheet :HeatText]
                        [:name-on-number-sign :NameOnNumberSign]
                        [:club-on-number-sign :ClubOnNumberSign]
                        [:adjudicator-order-other :AdjOrderOther]
                        [:adjudicator-order-final :AdjOrderFinal]
                        [:skip-adjudicator-letter :SkipAdjLetter]]
        dance-nsp "dance-competition"
        dance-flags (mapv (fn [[k attr]]
                            (hash-map (keyword dance-nsp (name k)) (get-flag attr)))
                          dance-mappings)

        presentation-mappings [[:arial-font ]]]
    (merge
     (apply merge dance-flags)
     {:presentation/arial-font (zx/attr competition-data :ArialFont)
      :presentation/courier-font (zx/attr competition-data :CourierFont)
      :presentation/chinese-fonts (get-flag :ChineseFonts)

      :printer/preview (get-flag :PreView)
      :printer/printer-select-paper (get-flag :PrinterSelectPaper)})
    ))
    ;; {:dance-perfect/flags {;; Same heat in all dances
    ;;                        :same-heat-all-dances (get-flag :SameHeatAllDances)
    ;;                        ;; Random order in heats
    ;;                        :random-order-in-heats (get-flag :RadomHeats)
    ;;                        ;; Heat text on Adjudicator sheets
    ;;                        :heat-text (get-flag :HeatText)
    ;;                        ;; Names on Number signs
    ;;                        :name-on-number-sign (get-flag :NameOnNumberSign)
    ;;                        ;; Clubs on Number signs
    ;;                        :club-on-number-sign (get-flag :ClubOnNumberSign)
    ;;                        ;; Enter marks by Adjudicators, Qual/Semi
    ;;                        :adj-order-other (get-flag :AdjOrderOther)
    ;;                        ;; Enter marks by Adjudicators, Final
    ;;                        :adj-order-final (get-flag :AdjOrderFinal)         
    ;;                        ;; Preview Printouts
    ;;                        :preview (get-flag :PreView)
    ;;                        ;; Select paper size before each printout
    ;;                        :printer-select-paper (get-flag :PrinterSelectPaper)
    ;;                        ;; Do not print Adjudicators letters (A-ZZ)
    ;;                        :skip-adj-letter (get-flag :SkipAdjLetter)
    ;;                        ;; Print with Chinese character set
    ;;                        :chinese-fonts (get-flag :ChineseFonts)}

    ;;  :dance-perfect/fonts {:arial-font "SimSun"
    ;;                        :courier-font "NSimSun"}}))

(defn- adjudicators->map [adjudicator-loc id-generator-fn]
  (assoc
      (d/make-adjudicator
       (id-generator-fn)
       (get-name-attr adjudicator-loc)
       (zx/attr adjudicator-loc :Country)
       (get-seq-attr-as-number adjudicator-loc))
    :dp/temp-id (get-seq-attr-as-number adjudicator-loc)))

(defn- competition-data->map [xml-loc]
  (d/make-competition-data
   (get-name-attr xml-loc)
   (tcr/to-date
    (tf/parse (tf/formatter "yyyy-MM-dd")
              (zx/attr xml-loc :Date)))
   (zx/attr xml-loc :Place)
   (get-options xml-loc)))

(defn- panel->map [panel-loc id-generator-fn adjudicators]
  (for [panel panel-loc]
    (dissoc
     (first (filter #(= (get-adj-number-attr panel) (:dp/temp-id %))
                    adjudicators))
     :dp/temp-id)))

(defn- adjudicator-panel-list->map
  [xml-loc id-generator-fn adjudicators]
  (filter
   ;; Adjudicators can be empty and if so its an empty panel and should not
   ;; be used
   (fn [adjudicators] (seq (:adjudicator-panel/adjudicators adjudicators)))
   (for [panel xml-loc]
     {:dp/panel-id (get-seq-attr-as-number panel)
      :adjudicator-panel/name (str (increased-seq-attr panel))
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

(defn- marks->map [marks-loc adjudicators]
  (for [mark marks-loc]
    {:dp/temp-local-adjudicator (get-seq-attr-as-number mark)
     :judging/adjudicator :todo
     :juding/marks [{:mark/x (= (zx/attr mark :X) "X")}]}))

(defn- mark-list->map [result-couple-loc adjudicators]
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
     {:dp/result-local-panel-id (get-seq-attr-as-number panel)
      :dp/temp-id (get-number-attr panel)})))

(defn- result-list->map [result-loc]
  (for [result result-loc]
    (let [adjudicators (result-adjudicator (zx/xml-> result :AdjList :Adjudicator))]
      {:result/adjudicators (into [] adjudicators)
       :result/results (into [] (mark-list->map (zx/xml-> result :ResultArray :Couple) adjudicators))})))

(defn- class-list->map [classes-loc id-generator-fn]
  (for [class classes-loc]
    {:class/name (zx/attr class :Name)
     :class/position (inc (get-seq-attr-as-number class))
     :class/adjudicator-panel {:dp/temp-id (dec (to-number (zx/attr class :AdjPanel)))}
     :class/starting (into [] (couple->map (zx/xml-> class :StartList :Couple) id-generator-fn))
     :class/dances (into [] (dance-list->map (zx/xml-> class :DanceList :Dance)))
     :class/remaining []
     :class/rounds []
     :class/results (into [] (result-list->map (zx/xml-> class :Results :Result)))
     :class/id (id-generator-fn)}))

(defn- round-list->map [rounds-loc id-generator-fn base-time]
  (for [round rounds-loc]
    (let [round-id (id-generator-fn)
          start-date-time (time-attr-to-date round base-time)
          event-number (make-event-number round)]
      {:dp/temp-class-id (class-number-attr-as-number round)
       
       :round/id round-id
       
       :temp/activity (assoc (d/make-activity
                              ;; Post processed
                              ""
                              ;; Events that represent comments do not have an EventNumber and do now get -1
                              event-number
                              (zx/attr round :Comment)
                              (id-generator-fn)
                              (increased-seq-attr round)
                              ;; Put real source here, needs to be done in pp
                              round-id
                              start-date-time)
                        :dp/temp-class-id (class-number-attr-as-number round))

       :round/number event-number

       ;; Post process, need to get this from the previous round
       :round/starting []

       ;; Post process, parse time and plus with compdate
       :round/start-time start-date-time
       
       ;; Save the id of the adjudicator panel to be able to look it up in post processing.
       ;; Subtract 3 since the 'index' in the file refer to a UI index witch is 3 of from
       ;; the Adjudicator index in this file beeing parsed.
       ;; NOTE: A panel UI index of 2, (-1 when translated), has the meaning of "All adjudicators"
       ;; in the DP UI. This need to be handled in post-processing of this round.
       :round/panel {:dp/panel-id (- (to-number (zx/attr round :AdjPanel)) 3)}

       ;; Post process
       :round/results []
       :round/recall (to-number (zx/attr (first (zx/xml-> round :RecallList :Recall)) :Recall))
       
       ;; Index is set in Post Process
       :round/index -1 ;; the rounds number in its class

       ;; Class id is set in Post Process
       :round/class-id -1 

       :round/heats (to-number (zx/attr round :Heats))
       :round/status (if (= 1 (to-number (zx/attr round :Status))) :completed :not-started)
       :round/dances (vec (dance-list->map (zx/xml-> round :DanceList :Dance))) ;[example-dance-1]
       
       ;; CONSIDER - maybe this should be left as a number for DB and then up to any presenter to parse?
       :round/type (round-value->key (to-number (zx/attr round :Round)))})))

;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;
;; Post process

(defn- prep-class-result [results result-panel adjudicators]
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

(defn- round-recalled [results participants]
  (vec
   (remove
    nil?
    (for [result results]
      (if (contains? #{:r :x} (:result/recalled result))
        (first (filter
                #(= (:result/participant-number result)
                    (:participant/number %))
                participants)))))))


(defn- last-result [rounds]
  (:round/results (last rounds)))

(defn- last-round-starting [class rounds]
  (round-recalled (last-result rounds) (:class/starting class)))

;; TODO - list of starting should be refactored
(defn- class-list-post-process [classes rounds adjudicators panels competition-date]
  (for [class classes]
    (let [last-round-starting-for-class (partial last-round-starting class)
          processed-rounds
          (reduce
           (fn [res round]
             (conj
              res
              (dissoc
               (merge
                round
                {:round/class-id (:class/id class)
                 :round/index (count res)
                 :round/results (vec (prep-class-result
                                      (get (:class/results class) (count res))
                                      (:result/adjudicators
                                       (first (:class/results class)))
                                      adjudicators))
                 ;; TODO - Fix that id of -1(?) refers to an "All Ajd" panel, create such a panel and ref it
                 ;; NOTE: Id of -1 mean "All adj" panel in DP UI.
                 :round/panel (dissoc
                               (first (filter
                                       #(= (:dp/panel-id (:round/panel round)) (:dp/panel-id %))
                                       panels))
                               :dp/panel-id)
                 ;; Starters in this rounds is based on the result on the previous round.
                 ;; If the previous round is a presentation round it will not have a result
                 ;; and should be dissregarded
                 :round/starting (if (= (count (filter #(not= (:round/type %) :presentation) res)) 0)
                                   (:class/starting class)
                                   (last-round-starting-for-class res))
                 :round/start-time  (:round/start-time round)})
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
               
               ;; There can be many pre-configured rounds, the remaining participants are
               ;; calculated from the result of the last completed round.
               ;; If there are no rounds completed, than all participants are still remaining.
               :class/remaining (if-let [completed-rounds
                                         (seq 
                                          (filter #(= (:round/status %) :completed) processed-rounds))]
                                  (last-round-starting-for-class completed-rounds)
                                  (:class/starting class))})
       :class/results))))

(defn- adjudicators-xml->map [xml id-generator-fn]
  (mapv
   #(adjudicators->map % id-generator-fn)
   (zx/xml-> xml :AdjPanelList :AdjList :Adjudicator)))

(defn- adjudicator-panels-xml->map [xml id-generator-fn adjudicators]
  (adjudicator-panel-list->map (zx/xml-> xml :AdjPanelList :PanelList :Panel) id-generator-fn adjudicators))

(defn- rounds-xml->map [xml id-generator-fn base-time]
  (round-list->map (zx/xml-> xml :EventList :Event) id-generator-fn base-time))

(defn- classes-xml->map [xml id-generator-fn]
  (class-list->map (zx/xml-> xml :ClassList :Class) id-generator-fn))

(defn- competition-data-xml->map [xml]
  (competition-data->map (first (zx/xml-> xml :CompData))))

(defn- make-activities [raw-activities classes rounds]
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

               :activity/source (first
                                 (filter #(= (:activity/source-id activity) (:round/id %)) rounds))})
       :dp/temp-class-id
       :activity/source-id)))
   []
   raw-activities))

(defn- make-all-adjudicators-panel [id-generator-fn dp-adjudicators]
  {:adjudicator-panel/name         "All adjudicators"
   :adjudicator-panel/id           (id-generator-fn)
   :adjudicator-panel/adjudicators (mapv #(dissoc % :dp/temp-id) dp-adjudicators)
   :dp/panel-id                    -1})

;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;
;; Import API

(defn competition-xml->map [xml id-generator-fn]
  {:pre [(not (nil? xml))
         (not (nil? id-generator-fn))]}
  (let [comp-data (competition-data-xml->map xml)
        dp-adjudicators (adjudicators-xml->map xml id-generator-fn)
        dp-classes (classes-xml->map xml id-generator-fn)
        dp-rounds (rounds-xml->map xml id-generator-fn (:competition/date comp-data))
        dp-panels-from-xml (adjudicator-panels-xml->map xml id-generator-fn dp-adjudicators)
        dp-panels-extended (make-all-adjudicators-panel id-generator-fn dp-adjudicators)
        dp-panels (conj dp-panels-from-xml dp-panels-extended)
        classes (class-list-post-process
                 dp-classes
                 dp-rounds
                 dp-adjudicators
                 dp-panels
                 (:competition/date comp-data))]
    (d/make-competition
     (:competition/name comp-data)
     (:competition/date comp-data)
     (:competition/location comp-data)
     (:competition/options comp-data)
     (mapv #(dissoc % :dp/panel-id) dp-panels) ;(adjudicator-panels-xml->map xml id-generator-fn dp-adjudicators)
     (mapv #(dissoc % :dp/temp-id) dp-adjudicators)
     (make-activities (mapv :temp/activity dp-rounds) classes (mapcat :class/rounds classes))
     classes)))

(defn import-file-stream
  [file-stream id-generator-fn]
  {:pre [(not (nil? file-stream))
         (not (nil? id-generator-fn))]}
  (competition-xml->map
   (zip/xml-zip
    (clojure.xml/parse (java.io.ByteArrayInputStream. (.getBytes file-stream))))
   id-generator-fn))
