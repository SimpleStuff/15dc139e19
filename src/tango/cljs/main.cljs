(ns tango.client
  (:require-macros
   [cljs.core.async.macros :as asyncm :refer (go go-loop)])
  (:require [reagent.core :as reagent :refer [atom]]
            [cljs.core.async :as async :refer (<! >! put! chan)]
            [cljs.core.match :refer-macros [match]]
            [taoensso.sente :as sente :refer (cb-success?)]
            [clojure.string]
            [cljs-time.core :as time]
            [cljs-time.coerce :as tc]
            [cljs-time.format :as tf]
            ))

;; Sente: https://github.com/ptaoussanis/sente
;http://markusslima.github.io/bootstrap-filestyle/
;http://getbootstrap.com/components/
;;; Utils
(enable-console-print!)

(defn log [m]
  (.log js/console m))

(log "ClojureScript appears to have loaded correctly.")

;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;
;;; Sente Socket setup
(let [{:keys [chsk ch-recv send-fn state]}
      (sente/make-channel-socket! "/chsk" ; Note the same path as before
       {:type :auto ; e/o #{:auto :ajax :ws}
       })]
  (def chsk       chsk)
  (def ch-chsk    ch-recv) ; ChannelSocket's receive channel
  (def chsk-send! send-fn) ; ChannelSocket's send API fn
  (def chsk-state state)   ; Watchable, read-only atom
  )

;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;
;; (defonce app-state
;;   (atom {:competitions []}))

(defonce app-state
  (atom {:selected-page :classes
         :competition {}}))

(defn on-file-read [e file-reader]
  (let [result (.-result file-reader)]
    (log "On file read : send :file/import")
    (chsk-send! [:file/import {:content result}])))

(defn on-click-import-file [e]
  (log "Import clicked")
  (let [file (.item (.. e -target -files) 0)
        r (js/FileReader.)]
    (set! (.-onload r) #(on-file-read % r))
    (.readAsText r file)))

(defn dispatch [props]
  (let [id (first props)
        data (vec (rest props))]
    (log (str "Dispatch of " id " with data " data))
    (match [id data]
           [:file/import [file]]
           (inc 1)
           [:select-page [new-page]]
           (swap! app-state merge {:selected-page new-page})
           )))

(defn on-export-click [e competition]
  (log "Export clicked")
  (log (str competition))
  (chsk-send! [:file/export {:file/format :dance-perfect
                             :file/content competition}]))

;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;
;; UI utils

;; (defn- to-number [s]
;;   {:pre [(string? s)]}
;;   (let [prepared-string (clojure.string/replace s #" " "")]
;;     (cond (re-seq #"^[-+]?\d*[\.,]\d*$" prepared-string)
;;           (js/parseDouble (clojure.string/replace prepared-string #"," "."))
;;           (re-seq #"^[-+]?\d+$" prepared-string)
;;           (js/parseInt (clojure.string/replace prepared-string #"\+" ""))
;;           :else s)))

(defn make-dance-type-presentation [dances]
  ;; Dances are presented as a list of the first letters of each dance
  (clojure.string/join (map #(first (:dance/name %)) dances)))

(def round-map
  {:none ""
   :normal-x "Normal" :semifinal-x "Semifinal" :final-x "Final"
   :b-final-x "B-Final" :retry-x "Retry" :second-try-x "2nd try"

   :normal-1-5 "Normal 1-5" :semifinal-1-5 "Semifinal 1-5" :retry-1-5 "Retry 1-5" :second-try-1-5 "2nd try 1-5"

   :normal-3d "Normal 3D" :semifinal-3d "Semifinal 3D" :retry-3d "Retry 3D" :second-try-3d "2nd try 3D"

   :normal-a+b "Normal A+B" :semifinal-a+b "Semifinal A+B" :final-a+b "Final A+B"
   :b-final-a+b "B-Final A+B" :retry-a+b "Retry A+B" :second-try-a+b "2nd try A+B"

   :presentation "Presentation"})

(defn make-event-round-presentation [event-round]
  (get round-map event-round))

(defn get-completed-rounds [rounds]
  (filter #(and (= (:round/status %) :completed)
                (not= (:round/type %) :presentation)) rounds))

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

;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;
;; Components

;; TODO - id is used as entity id and not as presentation, is name enough or should we keep
;; a adj/number or adj/position ?
(defn adjudictors-component []
  [:div
   [:h3 "Domare"]
   [:table.table
    [:thead
     [:tr
      ;[:th {:with "20"} "#"]
      [:th {:with "200"} "Name"]
      [:th {:with "20"} "Country"]]]
    [:tbody
     (for [adjudicator
           (sort-by :adjudicator/name (:competition/adjudicators (:competition @app-state)))]
       ^{:key adjudicator}
       [:tr
        ;[:td (:adjudicator/id adjudicator)]
        [:td (:adjudicator/name adjudicator)]
        [:td (:adjudicator/country adjudicator)]])]]])

(defn adjudictor-panels-component []
  [:div
   [:h3 "Domarpaneler"]
   [:table.table
    [:thead
     [:tr
      [:th {:with "20"} "#"]
      [:th {:with "200"} "Domare"]]]
    [:tbody
     (for [adjudicator-panel (sort-by :adjudicator-panel/name (:competition/panels (:competition @app-state)))]
       ^{:key adjudicator-panel}
       [:tr
        [:td (:adjudicator-panel/name adjudicator-panel)]
        [:td (clojure.string/join
              ", "
              (map :adjudicator/name (:adjudicator-panel/adjudicators adjudicator-panel)))]])]]])

(defn classes-component []
  [:div
   [:h3 "Klasser"]
   [:table.table
    [:thead
     [:tr
      [:th {:with "20"} "#"]
      [:th {:with "200"} "Dansdisciplin"]
      [:th {:with "20"} "Panel"]
      [:th {:with "20"} "Typ"]
      [:th {:with "20"} "Startande"]
      [:th {:with "20"} "Status"]]]
    [:tbody
     (for [class (sort-by :class/position (:competition/classes (:competition @app-state)))]
       ^{:key class}
       [:tr
        [:td (:class/position class)]
        [:td (:class/name class)]
        [:td (if-let [panel (:class/adjudicator-panel class)]
               (:adjudicator-panel/name panel)
               "0")]
        [:td (make-dance-type-presentation (:class/dances class))]
        
        [:td (str (count (:class/remaining class)) "/" (count (:class/starting class)))]

        ;; Present how far into the competition this class is.
        ;; The round status is given by the last completed round, if there are none
        ;;  the class is not started
        [:td (make-round-presentation (:class/rounds class))]])]]])

;; TODO - make the on-click event run thoughe dispatch
(defn import-component []
  [:div
   [:h2 "Importera en ny tävling : "]
   [:input.btn.btn-primary.btn-lg {:type "file" :value "Import file"
                                   :onChange #(on-click-import-file %)}]])

(defn navigation-component []
  [:div
   [:input.btn.btn-default {:type "button" :value "Classes"
                            :on-click #(dispatch [:select-page :classes])}]
   [:input.btn.btn-default {:type "button" :value "Time Schedule"
                            :on-click #(dispatch [:select-page :events])}]
   [:input.btn.btn-default {:type "button" :value "Adjudicators"
                            :on-click #(dispatch [:select-page :adjudicators])}]
   [:input.btn.btn-default {:type "button" :value "Adjudicator panels"
                            :on-click #(dispatch [:select-page :adjudicator-panels])}]])

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

     :recall (if (or (zero? (:round/recall round)) comment?)
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

(defn events-component []
  [:div
   [:h3 "Time Schedule"]
   [:table.table
    [:thead
     [:tr
      [:th {:with "20"} "Time"]
      [:th {:with "20"} "#"]
      [:th {:with "200"} "Dansdisciplin"]
      [:th {:with "20"} "Startande"]
      [:th {:with "20"} "Rond"]
      [:th {:with "20"} "Heats"]
      [:th {:with "20"} "Recall"]
      [:th {:with "20"} "Panel"]
      [:th {:with "20"} "Type"]]]
    [:tbody
     (doall
      (for [activity (sort-by :activity/position (:competition/activities (:competition @app-state)))]
        (let [{:keys [time number name starting round heats recall panel type]}
              (time-schedule-activity-presenter activity (:competition/classes
                                                          (:competition @app-state)))]
        ^{:key activity}
        [:tr
           [:td time]
           [:td number]
           [:td name]
           [:td starting]
           [:td round]
           [:td heats]
           [:td recall]
           [:td panel]
           [:td type]])))]]])

(defn menu-component []
  (fn []
    [:div
     [import-component]
     [navigation-component]
     (condp = (:selected-page @app-state)
       :classes [classes-component]
       :events [events-component]
       :adjudicators [adjudictors-component]
       :adjudicator-panels [adjudictor-panels-component])]))

;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;
;; Application

(defn ^:export run []
  (reagent/render-component [menu-component] (.-body js/document)))

;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;
;;; Socket handling

(defn- event-handler [[id data :as ev] _]
  (log (str "Event: " id))
  (match [id data]
         ;; TODO Match your events here <...>
         [:chsk/recv [:file/imported content]]
         (do
           (swap! app-state #(merge % {:competition content}))
           (log (str @app-state)))
         ;; [:chsk/recv [:file/export content]]
         ;; (handle-export)
         [:chsk/state [:first-open _]]
         (log "Channel socket successfully established!")
                                        ;[:chsk/state new-state] (log (str "Chsk state change: " new-state))
                                        ;[:chsk/recv payload] (log (str "Push event from server: " payload))
         :else (log (str "Unmatched event: " ev))))

(defonce chsk-router
  (sente/start-chsk-router-loop! event-handler ch-chsk))

;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;
;; Purgatory

;; Exempel till "Export"

;; (defn handle-export []
;;   (let [export-link (. js/document (getElementById "export-download-link"))]
;;     (log "Exporting competition")
;;     (.click export-link)))

;; <a id="document-download-link" download="project.goy" href=""></a>

;; (defn save-document []
;; (let [download-link (. js/document (getElementById "document-download-link"))
;; app-state-to-save (get-in @app/app-state [:main-app])
;; document-content (pr-str app-state-to-save)
;; compressed-content (.compressToBase64 js/LZString document-content)
;; href-content (str "data:application/octet-stream;base64," compressed-content)]
;; (set! (.-href download-link) href-content)
;; (.click download-link)))

