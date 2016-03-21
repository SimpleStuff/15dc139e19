(ns tango.cljs.client
  (:require-macros
    [cljs.core.async.macros :as asyncm :refer (go go-loop)])
  (:require [goog.dom :as gdom]
            [om.next :as om :refer-macros [defui]]
            [om.dom :as dom]
            [cljs.core.async :as async :refer (<! >! put! chan)]
            [taoensso.sente :as sente :refer (cb-success?)]
            [datascript.core :as d]
            [tango.ui-db :as uidb]
            [tango.domain :as domain]
            [tango.presentation :as presentation]
            [tango.cljs.client-mutation :as m]
            [tango.cljs.client-read :as r]
            [cognitect.transit :as t])
  (:import [goog.net XhrIo]))

;; TODO - check performance issue
; https://github.com/omcljs/om/issues/556

;; TODO - IntelliJ KeyPromoter
;; TODO - IntelliJ, emacs ctrl+u
;; TODO - IntelliJ, emacs send to repl

;https://github.com/omcljs/om/wiki/Quick-Start-%28om.next%29
;https://blog.juxt.pro/posts/course-notes-2.html
;https://github.com/awkay/om-tutorial
;http://juxt.pro/

(enable-console-print!)

(defn log [m]
  (.log js/console m))

;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;
;; Sente Socket setup

(let [{:keys [chsk ch-recv send-fn state]}
      (sente/make-channel-socket! "/chsk"
                                  {:type :auto})]
  (def chsk chsk)
  (def ch-chsk ch-recv)                                     ; ChannelSocket's receive channel
  (def chsk-send! send-fn)                                  ; ChannelSocket's send API fn
  (def chsk-state state)                                    ; Watchable, read-only atom
  )

;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;
;; Init DB
(defonce conn (d/create-conn uidb/schema))

(defn init-app []
  (d/transact! conn [{:db/id -1 :app/id 1}
                     {:db/id -1 :app/online? false}
                     {:db/id -1 :selected-page :competitions}
                     {:db/id -1 :app/import-status :none}
                     {:db/id -1 :app/status :running}
                     {:db/id -1 :app/selected-competition {}}
                     {:db/id -1 :app/new-competition {}
                      ;(domain/make-competition "New" "" "" {} {} {} {} {})
                      }
                     {:db/id -1 :app/selected-activity {}}]))

(defn app-started? [conn]
  (seq (d/q '[:find ?e
              :where
              [?e :app/id 1]] (d/db conn))))

(defn app-online? [conn]
  (d/q '[:find ?online .
         :where
         [_ :app/online? ?online]] (d/db conn)))

;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;
;; Server message handling

(declare reconciler)

;; VERY TEMPORARY (KILL ME IF I DO NOT FIX IT)
;; Need to make difference between query for all comps. vs query for details for a comp.
(defn handle-query-result [d]
  (do
    (log "Handle Q R")
    (if (vector? d)
      (let [clean-data {:competitions d}]
        (om/transact! reconciler `[(app/add-competition ~clean-data) :app/competitions]))
      (let [clean-data (uidb/sanitize d)]
        (om/transact! reconciler `[(app/add-competition ~clean-data) :app/competitions])))
    (om/transact! reconciler `[(app/set-import-status {:status :none})])
    (om/transact! reconciler `[(app/status {:status :running})])))

;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;
;; Sente message handling

; Dispatch on event-id
(defmulti event-msg-handler :id)

;; Wrap for logging, catching, etc.:
(defn event-msg-handler* [{:keys [id ?data event] :as ev-msg}]
  (event-msg-handler {:id    (first ev-msg)
                      :?data (second ev-msg)}))

(defmethod event-msg-handler :default
  [ev-msg]
  (log (str "Unhandled event: " ev-msg)))

(defmethod event-msg-handler :chsk/state
  [{:as ev-msg :keys [?data]}]
  (do
    (log (str "Channel socket state change: " ?data))
    (if (:first-open? ?data)
      (do
        (log "Channel socket successfully established!")
        (log "Fetch initilize data from Tango server")
        (om/transact! reconciler `[(app/online? {:online? true})])
        (chsk-send! [:event-manager/query [[:competition/name :competition/location]]]))
      (let [open-state (:open? ?data)]
        (om/transact! reconciler `[(app/online? {:online? ~open-state})])))))

;; TODO - Cleaning when respons type can be separated
(defmethod event-msg-handler :chsk/recv
  [{:as ev-msg :keys [?data]}]
  (let [[topic payload] ?data]
    (log (str "Push event from server: " topic))
    (when (= topic :event-manager/query-result)
      (if (vector? payload)
        (handle-query-result payload)
        (handle-query-result (second ?data))))
    (when (= topic :event-manager/transaction-result)
      (chsk-send! [:event-manager/query [[:competition/name :competition/location]]]))
    ;(log "Exit event-msg-handler")
    ))

(defmethod event-msg-handler :chsk/handshake
  [{:as ev-msg :keys [?data]}]
  (let [[?uid ?csrf-token ?handshake-data] ?data]
    (log (str "Handshake: " ?data))))

(defonce chsk-router
         (sente/start-chsk-router-loop! event-msg-handler* ch-chsk))

;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;
;; Import

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



;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;
;; Components

;;;;;;;;;;;;;;;;;;;;
;; Properties

;; TODO - Not completed yet..
;(defui PropertiesView
;  static om/IQuery
;  (query [_]
;    [{:competition/options
;      [:dance-competition/adjudicator-order-final]}
;     :competition/name])
;  Object
;  (render
;    [this]
;    (let [options (:competition/options (om/props this))]
;      (log "Properties")
;      (log options)
;      (dom/h3 nil "Properties")
;      (dom/h2 nil (:competition/name (om/props this))))))

;;;;;;;;;;;;;;;;;;;;
;; Adjudicators

(defui AdjudicatorRow
  static om/IQuery
  (query [_]
    [:adjudicator/name :adjudicator/country])
  Object
  (render
    [this]
    (let [adjudicator (om/props this)]
      (dom/tr nil
        (dom/td nil (:adjudicator/name adjudicator))
        (dom/td nil (:adjudicator/country adjudicator))))))

(defui AdjudicatorsView
  static om/IQuery
  (query [_]
    [{:competition/adjudicators (om/get-query AdjudicatorRow)}])
  Object
  (render
    [this]
    (let [adjudicators (sort-by :adjudicator/name (:competition/adjudicators (om/props this)))]
      (dom/div nil
        (dom/h2 {:className "sub-header"} "Domare")
        (dom/table
          #js {:className "table"}
          (dom/thead nil
            (dom/tr nil
              (dom/th #js {:width "20"} "Name")
              (dom/th #js {:width "200"} "Country")))
          (apply dom/tbody nil (map (om/factory AdjudicatorRow) adjudicators)))))))

;;;;;;;;;;;;;;;;;;;;
;; Adjudicator Panels

(defui AdjudicatorPanelRow
  static om/IQuery
  (query [_]
    [:adjudicator-panel/name {:adjudicator-panel/adjudicators [:adjudicator/name]}])
  Object
  (render
    [this]
    (let [panel (om/props this)]
      (dom/tr nil
        (dom/td nil (:adjudicator-panel/name panel))
        (dom/td nil (clojure.string/join
                      ", "
                      (map :adjudicator/name (:adjudicator-panel/adjudicators panel))))))))

(defui AdjudicatorPanelsView
  static om/IQuery
  (query [_]
    [{:competition/panels (om/get-query AdjudicatorPanelRow)}])
  Object
  (render
    [this]
    (let [panels (sort-by :adjudicator-panel/name (:competition/panels (om/props this)))]
      (log panels)
      (dom/div nil
        (dom/h2 {:className "sub-header"} "Domarpaneler")
        (dom/table
          #js {:className "table"}
          (dom/thead nil
            (dom/tr nil
              (dom/th #js {:width "20"} "#")
              (dom/th #js {:width "200"} "Domare")))
          (apply dom/tbody nil (map (om/factory AdjudicatorPanelRow) panels)))))))

;;;;;;;;;;;;;;;;;;;;
;; Competition

(defui Competition
  static om/IQuery
  (query [_]
    [:competition/name :competition/location])
  Object
  (render
    [this]
    (let [competition (om/props this)
          name (:competition/name competition)]
      (dom/tr
        ;; TODO - if the comp is already selected, do nothing
        ;; TODO - this should be handle by some remote mechanism
        #js {:onClick #(if (app-online? conn)
                        (do
                          (chsk-send! [:event-manager/query ['[*] [:competition/name name]]])
                          (om/transact! this `[(app/select-competition {:name ~name})])
                          (om/transact! this `[(app/status {:status :querying})]))
                        (om/transact! this `[(app/select-competition {:name ~name})]))}
        (dom/td nil name)
        (dom/td nil (:competition/location competition))))))

(def competition (om/factory Competition))

(defui CompetitionsView
  Object
  (render
    [this]
    (let [competitions (:competitions (om/props this))
          import-status (:import-status (om/props this))
          status (:status (om/props this))]
      (cond
        (= status :querying) (dom/div nil (dom/h3 nil (str "Laddar tävlingen, vänligen vänta..")))
        (= import-status :none)
        (dom/div nil
          (dom/h2 nil
            "Mina tävlingar")
          (dom/span nil "Välj en tävling att arbete med.")

          (dom/table
            #js {:className "table table-hover"}
            (dom/thead nil
              (dom/tr nil
                (dom/th nil "Namn")
                (dom/th nil "Plats")))
            (apply dom/tbody nil (map competition competitions)))

          (dom/div nil
            (dom/span #js {:className (str "btn btn-default btn-file"
                                           (when (= import-status :importing) " disabled"))}
                      "Importera.."
                      (dom/input #js {:type     "file"
                                      :onChange #(do
                                                  (om/transact! reconciler `[(app/set-import-status
                                                                               {:status :importing})])
                                                  (on-click-import-file %))}))

            ;(dom/li
            ;  #js {:className (if (= active-page-key page-key) "active" "")
            ;       :onClick   #(om/transact! component `[(app/select-page {:page ~page-key})])}
            ;  (dom/a nil button-name))
            (dom/button #js {:className "btn btn-default"
                             :onClick   #(om/transact! reconciler
                                                       `[(app/create-competition
                                                           ~(merge {:db/id -1 :competition/id (om/tempid)}
                                                                   (domain/make-competition "New" "" "" {} {} {} {} {})))
                                                         (app/select-page {:page :create-new-competition})
                                                         (app/select-competition {:name "New"})])}
                        "Skapa ny..")))
        (= import-status :importing) (dom/h3 nil "Importerar, vänligen vänta..")))))

;;;;;;;;;;;;;;;;;;;;
;; Classes

(defui ClassRow
  static om/IQuery
  (query [_]
    [:class/position :class/name :class/remaining :class/starting
     {:class/rounds
      [:round/status :round/type]}
     {:class/adjudicator-panel
      [:adjudicator-panel/name]}
     {:class/dances
      [:dance/name]}])
  Object
  (render [this]
    (let [{:keys [position name panel type starting status]}
          (presentation/make-class-presenter (om/props this))]
      (dom/tr nil
        (dom/td nil position)
        (dom/td nil name)
        (dom/td nil panel)
        (dom/td nil type)
        (dom/td nil starting)
        (dom/td nil status)))))

(defui ClassesView
  static om/IQuery
  (query [_]
    [{:competition/classes (om/get-query ClassRow)}])
  Object
  (render
    [this]
    (let [classes (sort-by :class/position
                           (:competition/classes (om/props this)))]
      (dom/div nil
        (dom/h2 {:className "sub-header"} "Klasser")
        (dom/table
          #js {:className "table"}
          (dom/thead nil
            (dom/tr nil
              (dom/th #js {:width "20"} "#")
              (dom/th #js {:width "200"} "Dansdisciplin")
              (dom/th #js {:width "20"} "Panel")
              (dom/th #js {:width "20"} "Typ")
              (dom/th #js {:width "20"} "Startande")
              (dom/th #js {:width "20"} "Status")))
          (apply dom/tbody nil (map (om/factory ClassRow) classes)))))))

;;;;;;;;;;;;;;;;;;;;
;; Schedule

(defui ScheduleRow
  static om/IQuery
  (query [_]
    [:activity/comment :activity/number :activity/time :activity/name :activity/id
     {:activity/source
      [:round/class-id :round/type :round/index :round/status
       {:round/starting [:participant/number :participant/id]}
       :round/heats :round/recall
       {:round/dances [:dance/name]}
       {:round/panel [:adjudicator-panel/name]}
       {:class/_rounds
        [{:class/rounds
          [:round/type :round/index :round/status]}]}]}])
  Object
  (render
    [this]
    (let [{:keys [time number name starting round heats recall panel type]}
          (presentation/make-time-schedule-activity-presenter
            (om/props this)
            (first (:class/_rounds (:activity/source (om/props this)))))]
      (dom/tr #js {:onClick #(om/transact! this `[(app/select-activity
                                                    {:activity/id   ~(:activity/id (om/props this))
                                                     :activity/name ~name
                                                     :round/recall  ~(:round/recall (:activity/source
                                                                                      (om/props this)))
                                                     :round/name    ~round
                                                     :round/heats   ~(:round/heats (:activity/source
                                                                                     (om/props this)))
                                                     :round/starting ~(:round/starting (:activity/source
                                                                                         (om/props this)))})])}
        (dom/td nil time)
        (dom/td nil number)
        (dom/td nil name)
        (dom/td nil starting)
        (dom/td nil round)
        (dom/td nil heats)
        (dom/td nil recall)
        (dom/td nil panel)
        (dom/td nil type)))))

(defui ScheduleView
  static om/IQuery
  (query [_]
    [{:competition/activities (om/get-query ScheduleRow)}])
  Object
  (render
    [this]
    (let [activites (sort-by :activity/position
                             (:competition/activities (om/props this)))]
      (dom/div nil
        (dom/h2 nil "Time Schedule")
        (dom/table
          #js {:className "table table-hover table-condensed"}
          (dom/thead nil
            (dom/tr nil
              (dom/th #js {:width "20"} "Time")
              (dom/th #js {:width "20"} "#")
              (dom/th #js {:width "200"} "Dansdisciplin")
              (dom/th #js {:width "20"} "Startande")
              (dom/th #js {:width "20"} "Rond")
              (dom/th #js {:width "20"} "Heats")
              (dom/th #js {:width "20"} "Recall")
              (dom/th #js {:width "20"} "Panel")
              (dom/th #js {:width "20"} "Type")))
          (apply dom/tbody nil (map (om/factory ScheduleRow) activites)))))))

;;;;;;;;;;;;;;;;;;;;
;; Create Competition
(defui PropertiesView
  static om/IQuery
  (query [_]
    [:db/id :competition/name :competition/location :competition/date
     :competition/options :competition/id])
  Object
  (render [this]
    (let [entity (om/props this)
          options (:competition/options entity)
          update-fn (fn [ent value-fn]
                      (fn [attribute e]
                        (om/transact! this `[(app/update-competition
                                               ~{:db/id (:db/id ent)
                                                 :attribute attribute
                                                 :value (value-fn e)}) ])))
          update-competition-fn (update-fn entity #(.. % -target -value))
          update-competition-options-fn (update-fn options #(.. % -target -checked))]
      (log "Render Properties")
      (log "Entity >")
      (log (:db/id entity))
      (log (:competition/id entity))
      (dom/div nil
        (dom/h3 nil "Competition Information")
        (dom/form nil
          (dom/div #js {:className "form-group"}
            (dom/label nil "Name")
            (dom/input #js {:type "text" :className "form-control" :value (:competition/name entity)
                            :onChange #(update-competition-fn :competition/name %)}))

          (dom/div #js {:className "form-group"}
            (dom/label nil "Place")
            (dom/input #js {:type "text" :className "form-control" :value (:competition/location entity)
                            :onChange #(update-competition-fn :competition/location %)}))

          (dom/div #js {:className "form-group"}
            (dom/label nil "Date")
            (dom/input #js {:type "text" :className "form-control" :value (:competition/date entity)
                            :onChange #(update-competition-fn :competition/date %)}))

          ;; TODO - is not included in the import, why?
          ;(dom/div #js {:className "form-group"}
          ;  (dom/label nil "Organizer")
          ;  (dom/input #js {:type "text" :className "form-control"
          ;                  :onChange #(update-competition-fn :competition/or %)}))
          )

        (let [make-property-check
              (fn [name k]
                (dom/div #js {:className "checkbox"}
                  (dom/label nil
                    (dom/input #js {:type     "checkbox"
                                    :checked  (get options k false)
                                    :onChange #(update-competition-options-fn k %)}) name)))]
          (dom/div nil
            (dom/h3 nil "Options")

            (dom/h4 nil "Competition")
            (apply dom/form nil
                   (map (fn [[name k]]
                          (make-property-check name k))
                        [["Same heat in all dances" :dance-competition/same-heat-all-dances]
                         ["Random order in heats" :dance-competition/random-order-in-heats]
                         ["Heat text on Adjudicator sheets" :dance-competition/heat-text-on-adjudicator-sheet]
                         ["Names on Number signs" :dance-competition/name-on-number-sign]
                         ["Clubs on Number signs" :dance-competition/club-on-number-sign]
                         ["Enter marks by Adjudicators, Qual/Semi" :dance-competition/adjudicator-order-other]
                         ["Enter marks by Adjudicators, Final" :dance-competition/adjudicator-order-final]
                         ["Do not print Adjudicators letters (A-ZZ)" :dance-competition/skip-adjudicator-letter]]))
            (dom/h4 nil "Printing")
            (apply dom/form nil
                   (map (fn [[name k]] (make-property-check name k))
                        [["Preview Printouts" :printer/preview]
                         ["Select paper size before each printout" :printer/printer-select-paper]]))
            ))
        ;(dom/button nil "Spara")
        ))))

;;;;;;;;;;;;;;;;;;;;
;; Menu

(defn make-menu-button
  [component active-page-key button-name page-key]
  (dom/li
    #js {:className (if (= active-page-key page-key) "active" "")
         :onClick   #(om/transact! component `[(app/select-page {:page ~page-key})])}
    (dom/a nil button-name)))

(defui MenuComponent
  static om/IQuery
  (query [_]
    [:app/selected-page
     :app/import-status
     :app/status
     :app/online?
     {:app/competitions (om/get-query Competition)}
     {:app/selected-competition
      (concat (om/get-query ClassesView)
              (om/get-query ScheduleView)
              (om/get-query AdjudicatorPanelsView)
              (om/get-query AdjudicatorsView)
              (om/get-query PropertiesView))}
     {:app/new-competition (om/get-query PropertiesView)}])
  Object
  (render
    [this]
    (log (:app/online? (om/props this)))
    (let [competitions (:app/competitions (om/props this))
          spage (:app/selected-page (om/props this))
          selected-competition (:app/selected-competition (om/props this))
          make-button (partial make-menu-button this spage)]
      (dom/div #js {:className "navbar-wrapper"}
        (dom/div #js {:className "container"}

          (dom/nav #js {:className "navbar navbar-inverse navbar-static-top"}
                   (dom/div #js {:className "container"}

                     ;; Header
                     (dom/div #js {:className "navbar-header"}
                       (dom/button #js {:type "button" :className "navbar-toggle collapsed"
                                        :data-toggle "collapse" :data-target "#navbar"
                                        :aria-expanded "false" :aria-controls "navbar"}
                                   (dom/span #js {:className "icon-bar"})
                                   (dom/span #js {:className "icon-bar"})
                                   (dom/span #js {:className "icon-bar"}))

                       ;; Brand name
                       (dom/a #js {:className "navbar-brand" :href "#"}
                              (str "Tango! - "
                                   (if (:app/online? (om/props this))
                                     "online"
                                     "offline"))))

                     ;; Navigation items
                     (dom/div #js {:id "navbar" :className "navbar-collapse collapse"}
                       (apply dom/ul #js {:className "nav navbar-nav"}
                              (map (fn [[name key]] (make-button name key))
                                   [["Home" :competitions]
                                    ["Properties" :properties]
                                    ["Classes" :classes]
                                    ["Time Schedule" :schedule]
                                    ["Adjudicators" :adjudicators]
                                    ["Adjudicator Panels" :adjudicator-panels]]))))))

        (dom/div #js {:className "container"}
          (dom/div #js {:className "row"}
            (dom/div #js {:className "col-lg-4"}
              (condp = spage
                :create-new-competition ((om/factory PropertiesView) selected-competition)
                :properties ((om/factory PropertiesView) selected-competition)
                :classes ((om/factory ClassesView) selected-competition)
                :competitions ((om/factory CompetitionsView)
                                {:competitions  competitions
                                 :import-status (:app/import-status (om/props this))
                                 :status        (:app/status (om/props this))})
                :schedule ((om/factory ScheduleView) selected-competition)
                :adjudicators ((om/factory AdjudicatorsView) selected-competition)
                :adjudicator-panels ((om/factory AdjudicatorPanelsView) selected-competition)))))))))

;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;
;; Remote Posts

(defn transit-post [url]
  (fn [edn cb]
    (log edn)
    (.send XhrIo url
           (fn [e]
             (log e)
             ;(this-as this
             ;  (log (t/read (t/reader :json)
             ;               (.getResponseText this)))
             ;  (cb (t/read (t/reader :json) (.getResponseText this))))
             )
           "POST" (t/write (t/writer :json) edn)
           #js {"Content-Type" "application/transit+json"})))

(defn sente-post []
  (fn [{:keys [remote command] :as env} cb]
    (if remote
      (do
        (log "Env > ")
        (log env)
        (log (str "Sent to Tango Backend => " remote))
        (chsk-send! [:event-manager/query [[:competition/name :competition/location]]]))
      (do
        ((transit-post "http://localhost:1337/commands") env cb)))))



;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;
;; Application

;; Init db etc if it has not been done
(when-not (app-started? conn)
  (init-app))

(def reconciler
  (om/reconciler
    {:state   conn
     :remotes [:remote :command]
     :parser  (om/parser {:read r/read :mutate m/mutate})
     :send    (sente-post)                                  ;(transit-post "http://localhost:1337/commands")
     }))

(om/add-root! reconciler
              MenuComponent (gdom/getElement "app"))


