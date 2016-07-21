(ns tango.presentation
  (:require #?@(:cljs [[cljs-time.coerce :as tc]
                       [cljs-time.format :as tf]]
                :clj  [[clj-time.coerce :as tc]
                       [clj-time.format :as tf]])))

;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;
;; Move to utils

(defn to-number [s]
  {:pre [(string? s)]}
  (let [prepared-string (clojure.string/replace s #" " "")]
    (cond (re-seq #"^[-+]?\d*[\.,]\d*$" prepared-string)
          #?(:clj (Double/parseDouble (clojure.string/replace prepared-string #"," "."))
             :cljs (js/parseFloat (clojure.string/replace prepared-string #"," ".")))
          
          (re-seq #"^[-+]?\d+$" prepared-string)
          #?(:clj (Integer/parseInt (clojure.string/replace prepared-string #"\+" ""))
             :cljs (js/parseInt (clojure.string/replace prepared-string #"\+" "")))
          :else s)))

;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;
;; Presenter utils

(def round-map
  {:round-type/none ""
   :round-type/normal-x "Normal" :round-type/semifinal-x "Semifinal" :round-type/final-x "Final"
   :round-type/b-final-x "B-Final" :round-type/retry-x "Retry" :round-type/second-try-x "2nd try"

   :round-type/normal-1-5 "Normal 1-5" :round-type/semifinal-1-5 "Semifinal 1-5"
   :round-type/retry-1-5 "Retry 1-5" :round-type/second-try-1-5 "2nd try 1-5"

   :round-type/normal-3d "Normal 3D" :round-type/semifinal-3d "Semifinal 3D"
   :round-type/retry-3d "Retry 3D" :round-type/second-try-3d "2nd try 3D"

   :round-type/normal-a+b "Normal A+B" :round-type/semifinal-a+b "Semifinal A+B"
   :round-type/final-a+b "Final A+B" :round-type/b-final-a+b "B-Final A+B"
   :round-type/retry-a+b "Retry A+B" :round-type/second-try-a+b "2nd try A+B"

   :round-type/presentation "Presentation"})

(defn- make-event-round-presentation [event-round]
  (get round-map event-round))

(defn- make-dance-type-presentation [dances]
  ;; Dances are presented as a list of the first letters of each dance
  (clojure.string/join (map #(first (:dance/name %)) dances)))

(defn- get-completed-rounds [rounds]
  (filter #(and (= (:round/status %) :status/completed)
                (not= (:round/type %) :round-type/presentation)) rounds))

(defn- make-round-presentation [rounds]
  ;; Only count rounds that are completed and that are not presentation rounds
  (let [completed-rounds (get-completed-rounds rounds)]
    (str
     (count completed-rounds) " - "
     (if (seq completed-rounds)
       (let [round-type (:round/type (last completed-rounds))]
         (if (= round-type :round-type/normal-x)
           (str "Round " (count completed-rounds))
           (get round-map round-type)))
       "Not Started"))))

;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;
;; Presenters

(defn make-class-presenter [class]
  (let [last-round (last (:class/rounds class))
        remaining (if (:round/starting last-round)
                    (:round/starting last-round)
                    (:class/starting class))
        remaining-count (if (= :status/completed (:round/status last-round))
                          0
                          (count remaining))]
    {:position (:class/position class)

     :name     (:class/name class)

     :panel    (if-let [panel (:class/adjudicator-panel class)]
                 (:adjudicator-panel/name panel)
                 "0")

     :type     (make-dance-type-presentation (:class/dances class))

     :starting (str remaining-count "/" (count (:class/starting class)))

     ;; Present how far into the competition this class is.
     ;; The round status is given by the last completed round, if there are none
     ;;  the class is not started
     :status   (make-round-presentation (:class/rounds class))}))

(defn make-time-schedule-activity-presenter [activity class]
  (let [round (:activity/source activity)
        
        ;; Comments have no activity number or if they do the round will not belong to any class
        comment? (or (not= (:activity/comment activity) "") (= (:activity/number activity) "-1"))
            
        ;; When a class have only one round that round is a direct final.
        ;; Note that a presentation round should not be considered and a
        ;; presentation round is never a direct final.
        direct-final? (and (= (count (filter #(not= (:round/type %) :round-type/presentation) (:class/rounds class))) 1)
                           (not= (:round/type round) :round-type/presentation))
                
        last-completed-round-index (:round/index (last (get-completed-rounds (:class/rounds class))))]
    {:time     (str (if (:activity/time activity)
                      (let [t (tc/from-long (.getTime (:activity/time activity)))
                            formatter (tf/formatter "HH:mm")]
                        ;(str (time/hour t) ":" (time/minute t))
                        (tf/unparse formatter t)
                        ))
                    (if (= (:round/status round) :status/completed) "*" ""))

     :number   (str (if (= (:activity/number activity) "-1") "" (:activity/number activity)))

     :name     (if comment? (:activity/comment activity) (:activity/name activity))

     ;; 'Qual' is when a greater number of participants where recalled than asked for,
     ;; then a 'Qual' round will be done to eliminate down to the requested recall number

     ;; If the class has not been started yet, than all rounds except the first will not have
     ;; a list of starters since this is not decided yet, there fore those rounds will have an
     ;; empty string in the starting field
     :starting (if comment?
                 ""
                 (cond
                   ;; First round will show the number of starters
                   (zero? (:round/index round)) (str "Start " (count (:round/starting round)))

                   ;; Direct finals will show starters
                   direct-final? (str "Start " (count (:round/starting round)))

                   ;; No starters yet
                   (zero? (count (:round/starting round))) ""

                   ;; if the last completed round, was the round before this, this is 'Qual'
                   (= (dec (:round/index round)) last-completed-round-index)
                   (str "Qual " (count (:round/starting round)))
                   :else ""))

     :round    (if comment?
                 ""
                 (if direct-final?
                   "Direct Final"
                   (make-event-round-presentation (:round/type round))))

     :heats    (if (or comment? direct-final? (= (:round/type round) :round-type/final-x))
                 ""
                 (let [heats (:round/number-of-heats round)
                       suffix (if (= 1 heats) "" "s")]
                   (str heats " heat" suffix)))

     :recall   (if (or (zero? (get round :round/number-to-recall 0)) comment?)
                 ""
                 (str "Recall " (:round/number-to-recall round)))

     :panel    (if (or comment? (= (:round/type round) :round-type/presentation))
                 ""
                 (let [panel (:adjudicator-panel/name (:round/panel round))]
                   (if (= panel "All adjudicators")
                     "All adj"
                     (str "Panel " panel))))

     :type     (if comment?
                 ""
                 (make-dance-type-presentation (:round/dances round)))}))

