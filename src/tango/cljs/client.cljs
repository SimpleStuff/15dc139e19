(ns tango.cljs.client
  (:require-macros
   [cljs.core.async.macros :as asyncm :refer (go go-loop)])
  (:require [reagent.core :as reagent :refer [atom]]
            [cljs.core.async :as async :refer (<! >! put! chan)]
            [cljs.core.match :refer-macros [match]]
            [taoensso.sente :as sente :refer (cb-success?)]
            [clojure.string]
            [tango.presentation :as presentation]))

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
  (atom {:selected-page :start-page
         :import-status :import-not-started
         :competitions []
         ;; TODO - rename, this represents the current selected competition
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
    (.readAsText r file)
    (swap! app-state merge {:import-status :import-started})))

(defn dispatch [props]
  (let [id (first props)
        data (vec (rest props))]
    (log (str "Dispatch of " id " with data " data))
    (match [id data]
           ;[:query ['[*] [:competition/name (:competition/name competition)]]]
           [:query [q]]
           (do
             (log (str "Query for " q))
             (chsk-send! [:event-manager/query q]))
           [:file/import [file]]
           (inc 1)
           [:select-page [new-page]]
           (swap! app-state merge {:selected-page new-page})

           [:new-competition-name-changed [new-name]]
           (swap! app-state (fn [current] (merge current
                                                 {:competition
                                                  (merge (:competition current) {:competition/name new-name})})))
           )))

(defn on-export-click [e competition]
  (log "Export clicked")
  (log (str competition))
  (chsk-send! [:file/export {:file/format :dance-perfect
                             :file/content competition}]))

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
   [:h3 (str "Klasser")]
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
     (for [class (map presentation/make-class-presenter
                      (sort-by :class/position (:competition/classes (:competition @app-state))))]
       (let [{:keys [position name panel type starting status]} class]
         ^{:key class}
         [:tr
          [:td position]
          [:td name]
          [:td panel]
          [:td type]
          [:td starting]       
          [:td status]]))]]])

(defn navigation-component []
  [:div
   [:input.btn.btn-default {:type "button" :value "Tävlingar"
                            :on-click #(dispatch [:select-page :start-page])}]
   (when (not= (:competition @app-state) {})
     [:div
      [:h3 (:competition/name (:competition @app-state))]
      [:input.btn.btn-default {:type "button" :value "Properties"
                               :on-click #(dispatch [:select-page :properties])}]
      [:input.btn.btn-default {:type "button" :value "Classes"
                               :on-click #(dispatch [:select-page :classes])}]
      [:input.btn.btn-default {:type "button" :value "Time Schedule"
                               :on-click #(dispatch [:select-page :events])}]
      [:input.btn.btn-default {:type "button" :value "Adjudicators"
                               :on-click #(dispatch [:select-page :adjudicators])}]
      [:input.btn.btn-default {:type "button" :value "Adjudicator panels"
                               :on-click #(dispatch [:select-page :adjudicator-panels])}]])])

(defn time-schedule-component []
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
              (presentation/make-time-schedule-activity-presenter
               activity
               (:competition/classes
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

(defn start-page-component []
  (fn []
    [:div
     [:div
      [:h2 "Mina tävlingar"]
      [:table.table
       [:thead
        [:tr
         [:th "Namn"]
         [:th "Plats"]]]
       [:tbody
        (for [competition (:competitions @app-state)]
          ^{:key competition}
          [:tr {:on-click #(dispatch [:query ['[*] [:competition/name (:competition/name competition)]]])}
           [:td (:competition/name competition)]
           [:td (:competition/location competition)]])]]]
     [:div
      [:input.btn.btn-default {:type "button" :value "Ny tävling"
                               :on-click #(dispatch [:select-page :new-competition])
                               }]
      [:span.btn.btn-default.btn-file
       "Importera.."
       [:input {:type "file" :onChange #(on-click-import-file %)}]]
      (condp = (:import-status @app-state)
        :import-not-started ""
        :import-started [:h4 "Importerar.."]
        :import-done [:h4 "Import färdig!"])]]))

;; TODO - make the on-click event run thoughe dispatch
;; http://www.abeautifulsite.net/whipping-file-inputs-into-shape-with-bootstrap-3/
(defn import-component []
  [:div
   ;[:h2 "Importera en ny tävling : "]
   [:span.btn.btn-default.btn-file
    "Importera"
    [:input {:type "file" :onChange #(on-click-import-file %)}]]
   (condp = (:import-status @app-state)
     :import-not-started ""
     :import-started [:h4 "Importerar.."]
     :import-done [:h4 "Import färdig!"])])

(defn make-competition-properties-presenter [competition]
  {:name (:competition/name competition)
   :location (:competition/location competition)})

(defn new-competition []
  (fn []
    (let [{:keys [name location]} (make-competition-properties-presenter (:competition @app-state))]
      [:div
       [:h3 "Skapa ny tävling"]

       [:form
        [:div.row
         [:div.col-sm-4
          [:div.form-group
           [:label.control-label {:for "inputName"} "Namn"]
           [:input.form-control
            {:id "inputName"
             :type "text"
             :placeholder "Tävlingens namn"
             :value name
             :on-change #(dispatch [:new-competition-name-changed (-> % .-target .-value)])}]]]]

        [:div.row
         [:div.col-sm-4
          [:div.form-group
           [:label.control-label {:for "inputPlace"} "Plats"]
           [:input.form-control
            {:id "inputPlace"
             :type "text"
             :placeholder "Plats"
             :value location
             :on-change #(dispatch [:new-competition-place-changed (-> % .-target .-value)])}]]]]

        [:div.row
         [:div.col-sm-4
          [:div.form-group
           [:label.control-label {:for "inputDate"} "Datum"]
           [:input.form-control
            {:id "inputDate"
             :type "text"
             :placeholder "Datum"
             :value "TODO"
             :on-change #(dispatch [:new-competition-date-changed (-> % .-target .-value)])}]]]]

        [:div.row
         [:div.col-sm-4
          [:div.form-group
           [:label.control-label {:for "inputOrg"} "Organisatör"]
           [:input.form-control
            {:id "inputOrg"
             :type "text"
             :placeholder "Organistation"
             :value "TODO"
             :on-change #(dispatch [:new-competition-organisation-changed (-> % .-target .-value)])}]]]
         ]

        ;; Options checkboxes
        [:div.row
         [:div.col-sm-12
          [:div.form-group
           [:label {:for "Options"} "Inställningar"]
           [:div.checkbox
            [:label
             [:input
              {:type "checkbox" :checked true}]  "Same heat in all dances"]
            ]

           [:div.checkbox
            [:label
             [:input
              {:type "checkbox"}]  "Random order in heats"]]

           [:div.checkbox
            [:label
             [:input
              {:type "checkbox"}]  "Heat text on Adjudicator sheets"]]

           [:div.checkbox
            [:label
             [:input
              {:type "checkbox"}]  "Names on Number signs"]]

           [:div.checkbox
            [:label
             [:input
              {:type "checkbox"}]  "Clubs on Number signs"]]

           [:div.checkbox
            [:label
             [:input
              {:type "checkbox"}]  "Enter marks by Adjudicators, Qual/Semi"]]

           [:div.checkbox
            [:label
             [:input
              {:type "checkbox"}]  "Enter marks by Adjudicators, Final"]]

           ;; NON EXISTING IN DP?
           [:div.checkbox
            [:label
             [:input
              {:type "checkbox"}]  "Reversed Final entry (NZ)"]]

           [:div.checkbox
            [:label
             [:input
              {:type "checkbox"}]  "Preview Printouts"]]

           [:div.checkbox
            [:label
             [:input
              {:type "checkbox"}]  "Select paper size before each printout"]]

           [:div.checkbox
            [:label
             [:input
              {:type "checkbox"}]  "Do not print Adjudicators letters (A-ZZ)"]]

           [:div.checkbox
            [:label
             [:input
              {:type "checkbox"}]  "Print with Chinese character set"]]
           ]]]

        [:button.btn.btn-primary {:type "button"} "Spara"]]
       ])))

(defn menu-component []
  (fn []
    [:div
     [navigation-component]
     (condp = (:selected-page @app-state)
       :start-page [start-page-component]
       :new-competition [new-competition]
       :properties [new-competition]
       :classes [classes-component]
       :events [time-schedule-component]
       :adjudicators [adjudictors-component]
       :adjudicator-panels [adjudictor-panels-component])]))



;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;
;; Application

(reagent/render-component [menu-component]
                          (.getElementById js/document "app"))

;; (defn ^:export run []
;;   (reagent/render-component [menu-component] (.-body js/document)))

(defn on-js-reload []
  ;; optionally touch your app-state to force rerendering depending on
  ;; your application
  ;; (swap! app-state update-in [:__figwheel_counter] inc)
)


;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;
;;; Socket handling

(defn- event-handler [[id data :as ev] _]
  (log (str "Event: " id))
  (match [id data]
         ;; TODO Match your events here <...>
         [:chsk/recv [:file/imported content]]
         (do
           (swap! app-state #(merge % {:competition content
                                       :import-status :import-done}))
           (log (str @app-state)))
         [:chsk/recv [:event-manager/transaction-result _]]
         (do
           (log "Server transacted - refresh to get latest")
           (swap! app-state #(merge % {:import-status :import-done}))
           (chsk-send! [:event-manager/query [[:competition/name :competition/location]]]))
         [:chsk/recv [:event-manager/query-result payload]]
         (do
           (log (str "Query result " data))
           ;; VERY TEMPORARY (KILL ME IF I DO NOT FIX IT)
           ;; Need to make difference between query for all comps. vs query for details for a comp.
           (if (vector? payload)
             (do
               (log "Init Q-res ")
               (swap! app-state #(merge % {:competitions payload})))
             (do
               (log "Details Q-res")
               (swap! app-state #(merge % {:competition payload
                                           :selected-page :classes})))))
         [:chsk/state d]
         (if (:first-open? d)
           (do
             (log "Channel socket successfully established!")
             (chsk-send! [:event-manager/query [[:competition/name :competition/location]]]))
           (log (str "Channel socket state changed: " d)))
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
