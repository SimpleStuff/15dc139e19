(ns tango.client
  (:require-macros
   [cljs.core.async.macros :as asyncm :refer (go go-loop)])
  (:require [reagent.core :as reagent :refer [atom]]
            [cljs.core.async :as async :refer (<! >! put! chan)]
            [cljs.core.match :refer-macros [match]]
            [taoensso.sente :as sente :refer (cb-success?)]
            [clojure.string]))

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
  (atom {:selected-page :events ;:classes

         ;; :competition
         ;; {:competition/date #inst "2014-11-22T00:00:00.000-00:00",
         ;;  :competition/name "Elittävling",
         ;;  :dance-perfect/version "4.1",
         ;;  :competition/location "VÄSTERÅS",
         ;;  :competition/classes
         ;;  [{:class/name "Hiphop Singel Star B",
         ;;    :class/adjudicator-panel 1
         ;;    :class/dances
         ;;    [{:dance/name "Medium"} {:dance/name "Waltz"}]
         ;;    :class/competitors
         ;;    [{:competitor/name "Saga Boström-Fors", :competitor/number 10, :competitor/club "M&M"}
         ;;       {:competitor/name "Tyra Hedin", :competitor/number 11, :competitor/club "Uddans"}
         ;;       {:competitor/name "Elina Ahlberg", :competitor/number 12, :competitor/club "SDC"}
         ;;       {:competitor/name "Thyra Söderström", :competitor/number 13, :competitor/club "Uddans"}
         ;;       {:competitor/name "Wilma Lindström Åslund", :competitor/number 14, :competitor/club "MD"}]}
           
         ;;   {:class/name "Hiphop Singel Star J Fl",
         ;;    :class/adjudicator-panel 0
         ;;    :class/dances [{:dance/name "Quick"}]
         ;;    :class/competitors
         ;;    [{:competitor/name "Tilda Strandberg", :competitor/number 30, :competitor/club "Uddans"}
         ;;     {:competitor/name "Tove Gärdin", :competitor/number 31, :competitor/club "BF"}
         ;;     {:competitor/name "Esther Wallmo", :competitor/number 32, :competitor/club "Uddans"}
         ;;     {:competitor/name "Felicia Dackell", :competitor/number 33, :competitor/club "Uddans"}
         ;;     {:competitor/name "Emma Fredriksson", :competitor/number 34, :competitor/club "DVT"}]}
           
         ;;   {:class/name "Hiphop Singel Star J Po",
         ;;    :class/adjudicator-panel 2
         ;;    :class/dances []
         ;;    :class/competitors
         ;;    [{:competitor/name "Axel Carlsson", :competitor/number 60, :competitor/club "DTLH/DV"}
         ;;     {:competitor/name "Tom Matei", :competitor/number 61, :competitor/club "SDC"}
         ;;     {:competitor/name "Jacob Olsson", :competitor/number 62, :competitor/club "DTLH/DV"}]}]}

         :competition-new {}


         :competition {}
         ;; {:competition/name "TurboMegatävling"
         ;;  :competition/date #inst "2014-11-22T00:00:00.000-00:00"
         ;;  :competition/location "THUNDERDOME"
         ;;  :competition/events
         ;;  [{:event/position 0
         ;;    :event/class-number 0
         ;;    :event/number -1
         ;;    :event/time ""
         ;;    :event/comment ""
         ;;    :event/adjudicator-panel 0
         ;;    :event/heats 1
         ;;    :event/round :unknown-round-value
         ;;    :event/status 0
         ;;    :event/start-order 0
         ;;    :event/recall 0
         ;;    :event/dances
         ;;    []}
           
         ;;   {:event/position 1
         ;;    :event/class-number 1
         ;;    :event/number 1
         ;;    :event/time "10:00"
         ;;    :event/comment "A comment"
         ;;    :event/adjudicator-panel 4
         ;;    :event/heats 2
         ;;    :event/round :normal-x
         ;;    :event/status 1
         ;;    :event/start-order 0
         ;;    :event/recall 0
         ;;    :event/dances
         ;;    [{:dance/name "Medium"}
         ;;     {:dance/name "Waltz"}]}

         ;;   {:event/position 2
         ;;   :event/class-number 0
         ;;   :event/number 1
         ;;   :event/time "10:05"
         ;;   :event/comment "A comment"
         ;;   :event/adjudicator-panel 4
         ;;   :event/heats 2
         ;;   :event/round :normal-x
         ;;   :event/status 0
         ;;   :event/start-order 0
         ;;   :event/recall 6
         ;;   :event/dances
         ;;   [{:dance/name "Medium"}]}]

         ;;  :competition/classes
         ;;  [{:class/name "Hiphop Singel Star B"
         ;;    :class/position 1
         ;;    :class/adjudicator-panel 1
         ;;    :class/dances
         ;;    [{:dance/name "Medium"}
         ;;     {:dance/name "Tango"}
         ;;     {:dance/name "VienWaltz"}
         ;;     {:dance/name "Foxtrot"}
         ;;     {:dance/name "Quickstep"}
         ;;     {:dance/name "Samba"}
         ;;     {:dance/name "Cha-Cha"}
         ;;     {:dance/name "Rumba"}
         ;;     {:dance/name "Paso-Doble"}
         ;;     {:dance/name "Jive"}]
         ;;    :class/competitors
         ;;    [{:competitor/name "Rulle Trulle"
         ;;      :competitor/club "Sinclairs"
         ;;      :competitor/number 30}
         ;;     {:competitor/name "Milan Lund"
         ;;      :competitor/club "Wilson"
         ;;      :competitor/number 31}
         ;;     {:competitor/name "Douglas Junger"
         ;;      :competitor/club "RGDT"
         ;;      :competitor/number 32}]
         ;;    :class/results
         ;;    [{:result/round "S"
         ;;      :result/adjudicators
         ;;      [{:adjudicator/number 3 :adjudicator/position 0}
         ;;       {:adjudicator/number 4 :adjudicator/position 1}
         ;;       {:adjudicator/number 5 :adjudicator/position 2}]
         ;;      :result/dance {:dance/name "X-Quick Forward"}
         ;;      :result/results
         ;;      [{:competitor/number 30
         ;;        :competitor/recalled ""
         ;;        :competitor/results
         ;;        [{:result/adjudicator
         ;;          {:adjudicator/number 3, :adjudicator/position 0},
         ;;          :result/x-mark true}
         ;;         {:result/adjudicator
         ;;          {:adjudicator/number 4, :adjudicator/position 1},
         ;;          :result/x-mark false}
         ;;         {:result/adjudicator
         ;;          {:adjudicator/number 5, :adjudicator/position 2},
         ;;          :result/x-mark true}]}
         ;;       {:competitor/number 31,
         ;;        :competitor/recalled :r,
         ;;        :competitor/results
         ;;        [{:result/adjudicator
         ;;          {:adjudicator/number 3, :adjudicator/position 0},
         ;;          :result/x-mark false}
         ;;         {:result/adjudicator
         ;;          {:adjudicator/number 4, :adjudicator/position 1},
         ;;          :result/x-mark true}
         ;;         {:result/adjudicator
         ;;          {:adjudicator/number 5, :adjudicator/position 2},
         ;;          :result/x-mark false}]}
         ;;       {:competitor/number 32,
         ;;        :competitor/recalled :x,
         ;;        :competitor/results
         ;;        [{:result/adjudicator
         ;;          {:adjudicator/number 3, :adjudicator/position 0},
         ;;          :result/x-mark true}
         ;;         {:result/adjudicator
         ;;          {:adjudicator/number 4, :adjudicator/position 1},
         ;;          :result/x-mark false}
         ;;         {:result/adjudicator
         ;;          {:adjudicator/number 5, :adjudicator/position 2},
         ;;          :result/x-mark false}]}]
         ;;      }]}
           
           
         ;;   {:class/name "Hiphop Singel Star J Fl"
         ;;    :class/position 0
         ;;    :class/adjudicator-panel 0
         ;;    :class/dances
         ;;    []
         ;;    :class/competitors
         ;;    [{:competitor/name "Ringo Stingo"
         ;;      :competitor/club "Kapangg"
         ;;      :competitor/number 20}
         ;;     {:competitor/name "Greve Turbo"
         ;;      :competitor/club "OOoost"
         ;;      :competitor/number 21}]
         ;;    :class/results []}]}}
         }))




(defn on-file-read [e file-reader]
  (let [result (.-result file-reader)]
    (log "On file read: send :file/import")
    (chsk-send! [:file/import {:content result}])))

(defn on-file-read-new [e file-reader]
  (let [result (.-result file-reader)]
    (log "On file read new: send :file/import-new")
    (chsk-send! [:file/import-new {:content result}])))

(defn on-click-import-file-new [e]
  (log "Import new clicked")
  (let [file (.item (.. e -target -files) 0)
        r (js/FileReader.)]
    (set! (.-onload r) #(on-file-read-new % r))
    (.readAsText r file)))

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

;; :class/adjudicator-panel {:adjudicator-panel/adjudicators [{:adjudicator/name "Anders", :adjudicator/country "Sweden", :adjudicator/id #uuid "4938cfaa-3af6-40e0-ba9c-532efde47bdd"} {:adjudicator/name "Bertil", :adjudicator/country "", :adjudicator/id #uuid "2cbab2aa-9c42-42c1-8385-2e0ba2856afe"}], :adjudicator-panel/name "1"

(defn make-round-presentation-new-2 [round-status round-count]
;;  (log round-status)
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
     "Not Started"))
  )

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

(defn new-classes-component []
  [:div
   [:h3 "Klasser Ny"]
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
     (for [class (sort-by :class/position (:competition/classes (:competition-new @app-state)))]
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

(defn dp-classes-component []
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
        [:td (:class/adjudicator-panel class)]
        [:td (make-dance-type-presentation (:class/dances class))]
        
        [:td (str (count-class-recalled class) "/" (count (:class/competitors class)))]

        [:td (make-round-presentation (:result/round (last (:class/results class)))
                                      (count (:class/results class)))]])]]])

;; TODO - make the on-click event run thoughe dispatch
(defn import-component []
  [:div
   [:h2 "Importera en ny tävling : "]
   [:input.btn.btn-primary.btn-lg {:type "file" :value "Import file"
                                   :onChange #(on-click-import-file %)}]
   [:input.btn.btn-primary.btn-lg {:type "file" :value "Import file new"
                                   :onChange #(on-click-import-file-new %)}]])

(defn navigation-component []
  [:div
   [:input.btn.btn-default {:type "button" :value "Classes"
                            :on-click #(dispatch [:select-page :classes])}]
   [:input.btn.btn-default {:type "button" :value "Classes New"
                            :on-click #(dispatch [:select-page :new-classes])}]
   [:input.btn.btn-default {:type "button" :value "Time Schedule"
                            :on-click #(dispatch [:select-page :events])}]
   [:input.btn.btn-default {:type "button" :value "Time Schedule New"
                            :on-click #(dispatch [:select-page :events-new])}]
   [:input.btn.btn-default {:type "button" :value "NewAdjudicators"
                            :on-click #(dispatch [:select-page :new-adjudicators])}]
   [:input.btn.btn-default {:type "button" :value "NewAdjudicator panels"
                            :on-click #(dispatch [:select-page :new-adjudicator-panels])}]
   [:input.btn.btn-default {:type "button" :value "Adjudicators"
                            :on-click #(dispatch [:select-page :adjudicators])}]
   [:input.btn.btn-default {:type "button" :value "Adjudicator panels"
                            :on-click #(dispatch [:select-page :adjudicator-panels])}]
   ])

(defn make-time [time]
  (str time))

(defn time-schedule-activity-presenter [activity classes]
  ;(log (:activity/comment activity))
  (let [comment? (= (:activity/number activity) -1)
        round (:activity/source activity)

        class (first (filter #(= (:class/id %) (:round/class-id round)) classes))
        ;; When a class have only one round that round is a direct final
        ;; Note that a presentation round should not be considered
        direct-final? (and (= (count (:class/rounds class)) 1)
                           (not= (:round/type round) :presentation)
                           )
        
        last-completed-round-index (:round/index (last (get-completed-rounds (:class/rounds class))))
        ]
    ;(log (:round/starting round))
    {:time (if comment? ""
               (str "TODO" (if (= (:round/status round) :completed) "*") ;(:round/start-time round)
                    ))

     :number (if (= (:activity/number activity) -1) "" (:activity/number activity))

     :name (if comment? (:activity/comment activity) (:activity/name activity))

     ;; TODO - Need to understan 'Qual'..
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
                  :else "BROKEN"))

     :round (if comment-row?
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

;; (if (or comment-row?)
            ;;   ""
            ;;   (cond
            ;;    (zero? (:event/class-index event))
            ;;    (str "Start " (:event/starting event))
            ;;    direct-final?
            ;;    (str "Start " (count (:class/competitors referenced-class)))
            ;;    (= (:event/class-index event) (count (:class/results referenced-class)))
            ;;    (str "Qual " (:event/starting event))))

(defn events-component-new []
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
      (for [activity (sort-by :activity/position (:competition/activities (:competition-new @app-state)))]
        (let [referenced-class (first (filter #(= (:class/position %) (:event/class-number event))
                                              (:competition/classes (:competition @app-state))))

              comment-row? (zero? (:event/class-number event))
              direct-final? (and (= (:event/nrof-events-in-class event) 1)
                                 (not= (:event/round event) :presentation))
              completed? (= (:event/status event) 1)

              comment? (= (:activity/number activity) -1)

              
              time-schedule (time-schedule-activity-presenter activity (:competition/classes
                                                                        (:competition-new @app-state)))]
        ^{:key activity}
        
          [:tr
           ;; Time - TODO - convert..
           [:td (:time time-schedule)]

           ;; # 
           [:td (:number time-schedule)]

           ;; Dansdisciplin (name) - use comment if activity number is -1
           [:td (:name time-schedule)]

           ;; Startande
           ;; TODO - get ppl left from refed class
           ;; kan hända att vid importen så behöver jag lägga till någon typ av koppling
           ;; så att ett vist event kan kopplas ihop till rätt resultat

           ;; Started ska presentera hur manga som startade i det eventet och det baseras pa
           ;; resultatet pa det tidigare eventet
           [:td (:starting time-schedule)        
            
            ]

           ;; Round
           [:td (:round time-schedule)
            ]

           ;; Heats
           [:td (:heats time-schedule)]

           [:td (:recall time-schedule)]

           ;; TODO - adjust adj panel in back-end
           [:td (:panel time-schedule)
            ]

           [:td (:type time-schedule)
            ]])))]]])



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
      (for [event (sort-by :event/position (:competition/events (:competition @app-state)))]
        (let [referenced-class (first (filter #(= (:class/position %) (:event/class-number event))
                                              (:competition/classes (:competition @app-state))))
              comment-row? (zero? (:event/class-number event))
              direct-final? (and (= (:event/nrof-events-in-class event) 1)
                                 (not= (:event/round event) :presentation))
              completed? (= (:event/status event) 1)]
        ^{:key event}
        
          [:tr
           [:td (make-event-time-presentation (:event/time event) (:event/status event))
            ]
           [:td (if (or  (= (:event/number event) -1)) "" (:event/number event))]
           ;; use comment if class number is zero
           [:td
            (let [t (vec (:competition/classes (:competition @app-state)))]
              (if (zero? (:event/class-number event))
                (:event/comment event)
                (:class/name referenced-class)))]

           ;; Startande
           ;; TODO - get ppl left from refed class
           ;; kan hända att vid importen så behöver jag lägga till någon typ av koppling
           ;; så att ett vist event kan kopplas ihop till rätt resultat

           ;; Started ska presentera hur manga som startade i det eventet och det baseras pa
           ;; resultatet pa det tidigare eventet
           [:td         
            (if (or comment-row?)
              ""
              (cond
               (zero? (:event/class-index event))
               (str "Start " (:event/starting event))
               direct-final?
               (str "Start " (count (:class/competitors referenced-class)))
               (= (:event/class-index event) (count (:class/results referenced-class)))
               (str "Qual " (:event/starting event))))]

           ;; Round
           [:td
            (if comment-row?
              ""
              (if direct-final?
                "Direct Final"
                (make-event-round-presentation (:event/round event))))]

           ;; Heats
           [:td
            (if (or comment-row? direct-final? (= (:event/round event) :final-x))
              ""
              (let [heats (:event/heats event)
                    suffix (if (= 1 heats) "" "s")]
                (str  heats " heat" suffix)))]

           [:td
            (if (or (zero? (:event/recall event)) comment-row?)
              ""
              (str "Recall " (:event/recall event)))]

           ;; TODO - adjust adj panel in back-end
           [:td
            (if (or comment-row? (= (:event/round event) :presentation))
              ""
              (let [panel (- (:event/adjudicator-panel event) 2)]
                (if (zero? panel)
                  "All adj"
                  (str "Panel " panel))))]

           [:td
            (if comment-row?
              ""
              (make-dance-type-presentation (:event/dances event)))]])))]]])

; :selected-page :import
(defn menu-component []
 ;[:div]
  (fn []
    [:div
     [import-component]
     [navigation-component]
     (condp = (:selected-page @app-state)
       :classes [dp-classes-component]
       :new-classes [new-classes-component]
       :events [events-component]
       :events-new [events-component-new]
       :adjudicators [adjudictors-component]
       :new-adjudicators [new-adjudictors-component]
       :new-adjudicator-panels [new-adjudictor-panels-component]
       :adjudicator-panels [adjudictor-panels-component]
       )]))

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
         [:chsk/recv [:file/imported content]]
         (do
           (swap! app-state #(merge % {:selected-page :classes
                                       :competition (:file/content content)}))
           ;(swap! app-state #(hash-map :competitions (conj (:competitions %) content)))
           (log (str @app-state))
           )
         [:chsk/recv [:file/imported-new content]]
         (do
           (swap! app-state #(merge % {:competition-new content}))
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
