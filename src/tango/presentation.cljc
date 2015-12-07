(ns tango.presentation
  (:require #?@(:cljs [[cljs-time.coerce :as tc]
                       [cljs-time.format :as tf]]
                      :clj  [[clj-time.coerce :as tc]
                             [clj-time.format :as tf]])))

;; (defn to-number [s]
;;   {:pre [(string? s)]}
;;   (let [prepared-string (clojure.string/replace s #" " "")]
;;     (cond (re-seq #"^[-+]?\d*[\.,]\d*$" prepared-string)
;;           (js/parseDouble (clojure.string/replace prepared-string #"," "."))
;;           (re-seq #"^[-+]?\d+$" prepared-string)
;;           (js/parseInt (clojure.string/replace prepared-string #"\+" ""))
;;           :else s)))

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
;; Presenters

(def round-map
  {:none ""
   :normal-x "Normal" :semifinal-x "Semifinal" :final-x "Final"
   :b-final-x "B-Final" :retry-x "Retry" :second-try-x "2nd try"

   :normal-1-5 "Normal 1-5" :semifinal-1-5 "Semifinal 1-5" :retry-1-5 "Retry 1-5" :second-try-1-5 "2nd try 1-5"

   :normal-3d "Normal 3D" :semifinal-3d "Semifinal 3D" :retry-3d "Retry 3D" :second-try-3d "2nd try 3D"

   :normal-a+b "Normal A+B" :semifinal-a+b "Semifinal A+B" :final-a+b "Final A+B"
   :b-final-a+b "B-Final A+B" :retry-a+b "Retry A+B" :second-try-a+b "2nd try A+B"

   :presentation "Presentation"})

(defn- make-event-round-presentation [event-round]
  (get round-map event-round))

;; TODO move class to presenters
(defn make-dance-type-presentation [dances]
  ;; Dances are presented as a list of the first letters of each dance
  (clojure.string/join (map #(first (:dance/name %)) dances)))

(defn- get-completed-rounds [rounds]
  (filter #(and (= (:round/status %) :completed)
                (not= (:round/type %) :presentation)) rounds))

;; TODO move class to presenters
(defn make-round-presentation [rounds]
  ;; Only count rounds that are completed and that are not presentation rounds
  (let [completed-rounds (get-completed-rounds rounds)]
    (str
     (count completed-rounds) " - "
     (if (seq completed-rounds)
       (let [round-type (:round/type (last completed-rounds))]
         (if (= round-type :normal-x)
           (str "Round " (count completed-rounds))
           (get round-map round-type)))
       "Not Started"))))

(defn time-schedule-activity-presenter [activity classes]
  (let [round (:activity/source activity)

        class (first (filter #(= (:class/id %) (:round/class-id round)) classes))

        ;; Comments have no activity number or if they do the round will not belong to any class
        comment? (or (not= (:activity/comment activity) "") (= (:activity/number activity) -1))
            
        ;; When a class have only one round that round is a direct final.
        ;; Note that a presentation round should not be considered and a
        ;; presentation round is never a direct final.
        direct-final? (and (= (count (filter #(not= (:round/type %) :presentation) (:class/rounds class))) 1)
                           (not= (:round/type round) :presentation))
                
        last-completed-round-index (:round/index (last (get-completed-rounds (:class/rounds class))))]
    {:time (str (if (:activity/time activity)
                  (let [t (tc/from-long (.getTime (:activity/time activity)))
                        formatter (tf/formatter "HH:mm")]
                    ;(str (time/hour t) ":" (time/minute t))
                    (tf/unparse formatter t)
                    ))
                (if (= (:round/status round) :completed) "*" ""))

     :number (if (= (:activity/number activity) -1) "" (:activity/number activity))

     :name (if comment? (:activity/comment activity) (:activity/name activity))

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

     :round (if comment?
              ""
              (if direct-final?
                "Direct Final"
                (make-event-round-presentation (:round/type round))))

     :heats (if (or comment? direct-final? (= (:round/type round) :final-x))
              ""
              (let [heats (:round/heats round)
                    suffix (if (= 1 heats) "" "s")]
                (str  heats " heat" suffix)))

     :recall (if (or (zero? (get round :round/recall 0)) comment?)
               ""
               (str "Recall " (:round/recall round)))

     :panel (if (or comment? (= (:round/type round) :presentation))
              ""
              (let [panel (:adjudicator-panel/name (:round/panel round))]
                (if (= panel "0")
                  "All adj"
                  (str "Panel " panel))))

     :type (if comment?
              ""
              (make-dance-type-presentation (:round/dances round)))}))

