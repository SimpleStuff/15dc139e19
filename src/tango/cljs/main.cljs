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

(defn- to-number [s]
  {:pre [(string? s)]}
  (let [prepared-string (clojure.string/replace s #" " "")]
    (cond (re-seq #"^[-+]?\d*[\.,]\d*$" prepared-string)
          (js/parseDouble (clojure.string/replace prepared-string #"," "."))
          (re-seq #"^[-+]?\d+$" prepared-string)
          (js/parseInt (clojure.string/replace prepared-string #"\+" ""))
          :else s)))

(defn number-string? [s]
  (if s
    (re-seq #"\d+" s)))

(defn make-dance-type-presentation [dances]
  ;; Dances are presented as a list of the first letters of each dance
  (clojure.string/join (map #(first (:dance/name %)) dances)))

(defn make-event-time-presentation [time status]
  (str time
       (when (= status 1)
         "*")))

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

(defn make-round-presentation [round-status round-count]
  (str
   round-count " - "
   (if round-status
     (if (number-string? round-status)
       (str "Round " round-status)
       (condp = round-status
         "S" (str "Semifinal" )
         "E" "2nd Try"
         "F" "Final"
         "O" "Retry"
         (str "Unknown status : " round-status)))
     "Not Started")))

;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;
;; Components

(defn count-class-recalled [class]
  (let [results (:result/results (last (:class/results class)))
        started (count (:class/competitors class))]
    (if (empty? results)
      started
      (reduce
       (fn [x y]
         (if (contains?
              #{:r :x}
              (:competitor/recalled y))
           (inc x)
           x))
       0
       results))))

(defn count-result-recalled [class-result]
  (reduce
   (fn [x y]
     (if (contains?
          #{:r :x}
          (:competitor/recalled y))
       (inc x)
       x))
   0
   class-result))

(def new-adj-data
  {:competition/name "TurboMegatävling"
   :competition/date "2014 11 22"
   :competition/location "THUNDERDOME"
   :competition/panels
   [{:adjudicator-panel/name "1"
     :adjudicator-panel/adjudicators
     [{:adjudicator/name "Anders"
       :adjudicator/id 1
       :adjudicator/country "Sweden"}
      {:adjudicator/name "Bertil"
       :adjudicator/id 2
       :adjudicator/country ""}]
     :adjudicator-panel/id 4}
    {:adjudicator-panel/name "2"
     :adjudicator-panel/adjudicators
     [{:adjudicator/name "Bertil"
       :adjudicator/id 2
       :adjudicator/country ""}
      {:adjudicator/name "Cesar"
       :adjudicator/id 3
       :adjudicator/country ""}]
     :adjudicator-panel/id 5}]
   :competition/adjudicators
   [{:adjudicator/name "Anders"
     :adjudicator/id 1
     :adjudicator/country "Sweden"}
    {:adjudicator/name "Bertil"
     :adjudicator/id 2
     :adjudicator/country "Uganda"}
    {:adjudicator/name "Cesar"
     :adjudicator/id 3
     :adjudicator/country "Tibet"}]
   :competitor/activities []
   :competition/classes []})

(defn new-adjudictors-component []
  [:div
   [:h3 "Domare"]
   [:table.table
    [:thead
     [:tr
      [:th {:with "20"} "#"]
      [:th {:with "200"} "Name"]
      [:th {:with "20"} "Country"]]]
    [:tbody
     (for [adjudicator (sort-by :adjudicator/id (:competition/adjudicators new-adj-data))]
       ^{:key adjudicator}
       [:tr
        [:td (:adjudicator/id adjudicator)]
        [:td (:adjudicator/name adjudicator)]
        [:td (:adjudicator/country adjudicator)]])]]])

(defn adjudictors-component []
  [:div
   [:h3 "Domare"]
   [:table.table
    [:thead
     [:tr
      [:th {:with "20"} "#"]
      [:th {:with "200"} "Name"]
      [:th {:with "20"} "Country"]]]
    [:tbody
     (for [adjudicator (sort-by :adjudicator/id (:competition/adjudicators (:competition @app-state)))]
       ^{:key adjudicator}
       [:tr
        [:td (:adjudicator/id adjudicator)]
        [:td (:adjudicator/name adjudicator)]
        [:td (:adjudicator/country adjudicator)]])]]])

(defn new-adjudictor-panels-component []
  [:div
   [:h3 "Domarpaneler"]
   [:table.table
    [:thead
     [:tr
      [:th {:with "20"} "#"]
      [:th {:with "200"} "Domare"]]]
    [:tbody
     (for [adjudicator-panel (sort-by :adjudicator-panel/id (:competition/panels new-adj-data))]
       ^{:key adjudicator-panel}
       [:tr
        [:td (:adjudicator-panel/id adjudicator-panel)]
        [:td (clojure.string/join ", " (map :adjudicator/name (:adjudicator-panel/adjudicators adjudicator-panel)))]])]]])

(defn adjudictor-panels-component []
  [:div
   [:h3 "Domarpaneler"]
   [:table.table
    [:thead
     [:tr
      [:th {:with "20"} "#"]
      [:th {:with "200"} "Domare"]]]
    [:tbody
     (for [adjudicator-panel (sort-by :panel/id (:competition/adjudicator-panels (:competition @app-state)))]
       ^{:key adjudicator-panel}
       [:tr
        [:td (:panel/id adjudicator-panel)]
        [:td (clojure.string/join ", " (mapcat vals (:panel/adjudicators adjudicator-panel)))]])]]])

(defn get-completed-rounds [rounds]
  (filter #(and (= (:round/status %) :completed)
                (not= (:round/type %) :presentation)) rounds))

(defn make-round-presentation-new [rounds]
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

(defn classes-component []
  [:div
   [:h3 "Klasser Nyyy"]
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
        [:td (make-round-presentation-new (:class/rounds class))
         ]])]]])

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
   [:input.btn.btn-default {:type "button" :value "NewAdjudicators"
                            :on-click #(dispatch [:select-page :new-adjudicators])}]
   [:input.btn.btn-default {:type "button" :value "NewAdjudicator panels"
                            :on-click #(dispatch [:select-page :new-adjudicator-panels])}]
   [:input.btn.btn-default {:type "button" :value "Adjudicators"
                            :on-click #(dispatch [:select-page :adjudicators])}]
   [:input.btn.btn-default {:type "button" :value "Adjudicator panels"
                            :on-click #(dispatch [:select-page :adjudicator-panels])}]
   ])

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
              (make-dance-type-presentation (:round/dances round)))})
  )

(defn events-component []
  [:div
   [:h3 "Time Schedule Ny"]
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
       :new-adjudicators [new-adjudictors-component]
       :new-adjudicator-panels [new-adjudictor-panels-component]
       :adjudicator-panels [adjudictor-panels-component])]))

;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;
;; Application

(defn ^:export run []
  (reagent/render-component [menu-component] (.-body js/document)))

;; (defn handle-export []
;;   (let [export-link (. js/document (getElementById "export-download-link"))]
;;     (log "Exporting competition")
;;     (.click export-link)))

;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;
;;; Socket handling
(defn- event-handler [[id data :as ev] _]
  (log (str "Event: " id))
  (match [id data]
    ;; TODO Match your events here <...>
         ;; [:chsk/recv [:file/imported content]]
         ;; (do
         ;;   (swap! app-state #(merge % {:selected-page :classes
         ;;                               :competition (:file/content content)}))
         ;;   ;(swap! app-state #(hash-map :competitions (conj (:competitions %) content)))
         ;;   (log (str @app-state))
         ;;   )
         [:chsk/recv [:file/imported content]]
         (do
           (swap! app-state #(merge % {:competition content}))
           (log (str @app-state)))
         ;; [:chsk/recv [:file/export content]]
         ;; (handle-export)
    [:chsk/state [:first-open _]] (log "Channel socket successfully established!")
    ;[:chsk/state new-state] (log (str "Chsk state change: " new-state))
    ;[:chsk/recv payload] (log (str "Push event from server: " payload))
    :else (log (str "Unmatched event: " ev)))
  )

(defonce chsk-router
  (sente/start-chsk-router-loop! event-handler ch-chsk))

;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;
;; Purgatory

;; Exempel till "Export"
;; <a id="document-download-link" download="project.goy" href=""></a>

;; (defn save-document []
;; (let [download-link (. js/document (getElementById "document-download-link"))
;; app-state-to-save (get-in @app/app-state [:main-app])
;; document-content (pr-str app-state-to-save)
;; compressed-content (.compressToBase64 js/LZString document-content)
;; href-content (str "data:application/octet-stream;base64," compressed-content)]
;; (set! (.-href download-link) href-content)
;; (.click download-link)))


;; TODO - Let the server return dancers in a UI-normalized way
;; (defn get-dancers [competitions]
;;   (into #{}
;;         (map #(dissoc % :competitor/number) 
;;              (for [competition competitions
;;                    classes (:competition/classes competition)
;;                    competitors (:class/competitors classes)]
;;                competitors))))

;; (defonce component-state
;;   (atom {:competitions-visible false}))

;; (defn competitor-component [competitors include-number?]
;;   [:div ""
;;    [:ul
;;     (for [competitor competitors]
;;       ^{:key competitor}
;;       [:li 
;;        (str (if include-number?
;;               (str "Tävlande nummer : " (:competitor/number competitor) " - "))
;;             (str (:competitor/name competitor)
;;                  " från "
;;                  (:competitor/club competitor)))])]])

;; (defn class-component [classes]
;;   [:h3 "Classes"]
;;   [:div
;;    (for [cls classes]
;;      ^{:key cls}
;;      [:div
;;       (:class/name cls)
;;       [competitor-component (:class/competitors cls) true]])])

;; (defn competition-item []
;;   (let [open (atom false)]
;;     (fn [competition]
;;       [:li 
;;        [:div.view
;;         [:label {:on-click #(log "Klickz")} (str (:competition/name competition) " i "
;;                       (:competition/location competition) " den "
;;                       (:competition/date competition))
;;          ]
;;         [:input.btn.btn-default
;;          {:type "button"
;;           :value (if @open "Stäng" "Öppna")
;;           :on-click #(swap! open not)}]]
;;        (if @open
;;          [:div
;;           [:input.btn.btn-default
;;            {:type "button"
;;             :value "Exportera"
;;             ;; TODO - send competition id when we got back-end storage
;;             :on-click #(on-export-click % competition)}]
;;           [:a {:id "export-download-link"
;;                :href (str "/exported-files/" (:competition/name competition) ".xml")
;;                :download (str (:competition/name competition) ".xml")}]
;;           [:h4 "Klasser :"]
;;           [class-component (:competition/classes competition)]])])))

;; (defn competitors-component []
;;   [:div
;;    [:h2 "Tillgängliga dansare"]
;;    [competitor-component (get-dancers (:competitions @app-state))]])

;; (defn import-component []
;;   [:div
   
;;    [:h2 "Tillgängliga tävlingar"]
;;    [:ul
;;     (for [competition (:competitions @app-state)]
;;       ^{:key competition} [competition-item competition])]
;;    [:h2 "Importera en ny tävling : "]
;;    [:input.btn.btn-default {:type "file" :value "Import file"
;;                             :onChange ;#(dispatch [:file/import (.item (.. % -target -files) 0)])
;;                             #(on-click-import-file %)
;;                             }]])

;; (defn menu-component []
;;   (let [visible-component (atom :none)]
;;     (fn []
;;       [:div.container
;;        [:div.header
;;         [:h2 "Välkommen till Tango!"]
;;         [:input.btn.btn-primary.btn-lg.navbar-btn
;;          {:type "button" :value "Tävlingar" :on-click #(reset! visible-component :competitions)}]
;;         [:input.btn.btn-primary.btn-lg
;;          {:type "button" :value "Dansare" :on-click #(reset! visible-component :competitors)}]
;;         [:input.btn.btn-primary.btn-lg
;;          {:type "button" :value "Domare" :on-click #(reset! visible-component :adjudicators)}]

;;         [:input.btn.btn-default
;;          {:type "button" :value "Debug Button" :on-click #(chsk-send! [:debug/test {:test "Test"}])}]
;;         ;[:h2 (str "visible-component " @visible-component)]
;;         ]
;;        (condp = @visible-component
;;          :competitions [import-component]
;;          :competitors [competitors-component]
;;          :adjudicators [:div]
;;          :none [:div])
;;        ])))
