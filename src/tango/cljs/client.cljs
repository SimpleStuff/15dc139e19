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
            [tango.presentation :as presentation]))

;; TODO - All Adjudicators 'r valbart i ui m[ste fixa, kolla hur de behandlas i importen
;; TODO sortera p[ position ;aven klasser

;https://github.com/omcljs/om/wiki/Quick-Start-%28om.next%29

(enable-console-print!)

(defn log [m]
  (.log js/console m))

;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;
;; Init DB
(defonce conn (d/create-conn uidb/schema))

(defn init-app []
  (d/transact! conn [{:db/id -1 :app/id 1}
                     {:db/id -1 :selected-page :competitions}
                     ;{:db/id -1 :app/selected-competition {}}
                     ]))

(defn app-started? [conn]
  (not
   (empty? (d/q '[:find ?e
                  :where
                  [?e :app/id 1]] (d/db conn)))))

(log conn)
;;  (d/q '[:find [(pull ?c [:class/name]) ...] 
;;         :where
;;         [?e :competition/name "A"]
;;         [?e :competition/classes ?c]]
;;       (d/db conn)))

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

;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;
;; Server message handling

(declare reconciler)
(defn handle-query-result [d]
  (let [clean-data  (uidb/sanitize d)]
    ;;{:competition/name "TestNamn" :competition/location "Location"}
    (log "Handle Q R")
     ;(log clean-data)   
    ;(log (apply str (map :competition/name clean-data)))
                                        ;(d/transact! (d/db conn) )
    ;;(d/transact! conn [clean-data])
    ;; (log (d/q '[:find [(pull ?c [:class/name]) ...]
    ;;             :where
    ;;             [?e :competition/name "Rikstävling disco"]
    ;;             [?e :competition/classes ?c]]
    ;;           (d/db conn)))
    ;(d/transact! conn [{:db/id 1 :competition/name (apply str (map :competition/name clean-data))}])
    ;(om/transact! reconciler `[(app/add-competition `{:X "X"}) :app/competitions])
    ;(log clean-data)
    ;; (om/transact! reconciler `[(app/add-competition ~clean-data) :app/competitions
    ;;                            ])
    (om/transact! reconciler `[(app/add-competition ~clean-data) :app/competitions])
    ))

;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;
;; Sente message handling

(defmulti event-msg-handler :id) ; Dispatch on event-id
;; Wrap for logging, catching, etc.:
(defn event-msg-handler* [{:keys [id ?data event] :as ev-msg}]
  (event-msg-handler {:id (first ev-msg)
                      :?data (second ev-msg)}))

(defmethod event-msg-handler :default ; Fallback
  [{:keys [event] :as ev-msg}]
  (log (str "Unhandled event: " ev-msg)))

(defmethod event-msg-handler :chsk/state
  [{:as ev-msg :keys [?data]}]
  (if (= ?data {:first-open? true})
    (log "Channel socket successfully established!")
    (log (str "Channel socket state change: " ?data))))

(defmethod event-msg-handler :chsk/recv
  [{:as ev-msg :keys [?data]}]
  (let [[topic payload] ?data]
    ;(log (str "Push event from server: " ev-msg))
    (log (str "Push event from server: " topic))
    (when (= topic :event-manager/query-result)
      (handle-query-result (second ?data)))))

(defmethod event-msg-handler :chsk/handshake
  [{:as ev-msg :keys [?data]}]
  (let [[?uid ?csrf-token ?handshake-data] ?data]
    (log (str "Handshake: " ?data))))

(defmethod event-msg-handler :event-manager/query-result
  [{:as ev-msg :keys [?data]}]
  (log (str "Event Manager Query Result " ?data)))

(defonce chsk-router
  (sente/start-chsk-router-loop! event-msg-handler* ch-chsk))


;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;
;; Read

(defmulti read om/dispatch)

;; (defmethod read :app/counter
;;   [{:keys [state query]} _ _]
;;   {:value (d/q '[:find [(pull ?e ?selector) ...]
;;                  :in $ ?selector
;;                  :where [?e :app/title]]
;;             (d/db state) query)})

;; The signature of a read function is [env key params]. env is a hash map containing any context 
;; necessary to accomplish reads. key is the key that is being requested to be read. 
;; Finally params is a hash map of parameters that can be used to customize the read. 
;; In many cases params will be empty.
(defmethod read :app/competitions
  [{:keys [state query] :as env} key params]
  {:value (do
            ;(log (str "Env: " env " --- Key : " key " --- Params" params))
            (log (str "Read app/competitions with query " query))
            (log (str "Key " key))
            ;(log (str "Env " env))
            (if query
              (d/q '[:find [(pull ?e ?selector) ...]
                     :in $ ?selector
                     :where [?e :competition/name]]
                   (d/db state) query))
            ) ;(log (str "Read Comp, state " state " , query" query))
   :remote true})

(defmethod read :app/selected-page
  [{:keys [state query]} key params]
  {:value (do
            (log "Read Selected Page")
            (let [q (d/q '[:find ?page . :where [[:app/id 1] :selected-page ?page]] (d/db state))]
              (log q)
              q))})

(defmethod read :app/selected-competition
  [{:keys [state query]} key params]
  {:value (do
            (log "Read Selected Comp.")
            (let [q (d/q '[:find (pull ?comp ?selector) .
                           :in $ ?selector
                           :where [[:app/id 1] :app/selected-competition ?comp]]
                         (d/db state) query)]
              ;(log q)
              q))})

;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;
;; Mutate

(defmulti mutate om/dispatch)

(defmethod mutate 'app/add-competition
  [{:keys [state]} key params]
  (do
    {:value {:keys [:app/competitions]}
     :action (fn []
               (do
                 (log "Add Competition")
                 (d/transact! state [params])
                 ;(log conn)
                 ))}))

(defmethod mutate 'app/select-page
  [{:keys [state]} key {:keys [page] :as params}]
  {:value {:keys [:app/selected-page]}
   :action (fn []
             (do (log (str "Select Page "))
                 ;(log page)
                 (d/transact! state [{:app/id 1 :selected-page page}])
                 ;(log state)
                 ))})

(defmethod mutate 'app/select-competition
  [{:keys [state]} key {:keys [name]}]
  {:value {:keys [:app/selected-competition]}
   :action (fn []
             (d/transact! state [{:app/id 1 :app/selected-competition {:competition/name name}}]))})

;; Mutations should return a map for :value. This map can contain two keys - 
;; :keys and/or :tempids. The :keys vector is a convenience that communicates what 
;; read operations should follow a mutation. :tempids will be discussed later. 
;; Mutations can easily change multiple aspects of the application (think Facebook "Add Friend"). 
;; Adding :value with a :keys vector helps users identify stale keys which should be re-read.
;; (defmethod mutate 'app/name
;;   [{:keys [state]} _ _]
;;   {:value {:keys [:competition/name]}
;;    :action (fn [] (d/transact! state [{:db/id 1 :competition/name "B"}]))})
(defmethod mutate 'app/name
  [{:keys [state]} _ _]
  {:value {:keys [:app/competitions]}
   :action ;(fn [] (chsk-send! [:event-manager/query [[:competition/name :competition/location]]]))
   (fn [] (chsk-send! [:event-manager/query ['[*] [:competition/name "Rikstävling disco"]]]))
   })

(defn test-query-click [t]
  (do
    (log "Test Click")
    (om/transact! t '[(app/name)])))

;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;
;; Components

;;;;;;;;;;;;;;;;;;;;
;; Properties

(defui PropertiesView
  static om/IQuery
  (query [this]
         [{:competition/options [:dance-competition/adjudicator-order-final]}
          :competition/name])
  Object
  (render
   [this]
   (let [options (:competition/options (om/props this))]
     (log "Properties")
     (log options)
     (dom/h3 nil "Properties")
     (dom/h2 nil (:competition/name (om/props this))))))

;;;;;;;;;;;;;;;;;;;;
;; Adjudicators

(defui AdjudicatorRow
  static om/IQuery
  (query [this]
         [:adjudicator/name :adjudicator/country])
  Object
  (render
   [this]
   (let [adjudicator (om/props this)]
     ;(log "ClassRowRender")
     (dom/tr
      nil
      (dom/td nil (:adjudicator/name adjudicator))
      (dom/td nil (:adjudicator/country adjudicator))))))

(defui AdjudicatorsView
  static om/IQuery
  (query [this]
         [{:competition/adjudicators (om/get-query AdjudicatorRow)}])
  Object
  (render
   [this]
   (let [adjudicators (:competition/adjudicators (om/props this))]
     (log adjudicators)
     (dom/div
      nil
      (dom/h3 nil "Domare")
      (dom/table
       #js {:className "table"}
       (dom/thead
        nil
        (dom/tr
         nil
         (dom/th #js {:width "20"} "Name")
         (dom/th #js {:width "200"} "Country")))
       (apply dom/tbody nil (map (om/factory AdjudicatorRow) adjudicators)))))))

;;;;;;;;;;;;;;;;;;;;
;; Adjudicator Panels

(defui AdjudicatorPanelRow
  static om/IQuery
  (query [this]
         [:adjudicator-panel/name {:adjudicator-panel/adjudicators [:adjudicator/name]}])
  Object
  (render
   [this]
   (let [panel (om/props this)]
     ;(log "ClassRowRender")
     (dom/tr
      nil
      (dom/td nil (:adjudicator-panel/name panel))
      (dom/td nil (clojure.string/join
                   ", "
                   (map :adjudicator/name (:adjudicator-panel/adjudicators panel))))))))

(defui AdjudicatorPanelsView
  static om/IQuery
  (query [this]
         [{:competition/panels (om/get-query AdjudicatorPanelRow)}])
  Object
  (render
   [this]
   (let [panels (:competition/panels (om/props this))]
     (log panels)
     (dom/div
      nil
      (dom/h3 nil "Domarpaneler")
      (dom/table
       #js {:className "table"}
       (dom/thead
        nil
        (dom/tr
         nil
         (dom/th #js {:width "20"} "#")
         (dom/th #js {:width "200"} "Domare")))
       (apply dom/tbody nil (map (om/factory AdjudicatorPanelRow) panels))
       )))))

;;;;;;;;;;;;;;;;;;;;
;; Competition

(defui Competition
  static om/IQuery
  (query [this]
         [:competition/name :competition/location])
  Object
  (render
   [this]
   (let [competition (om/props this)
         name (:competition/name competition)]
     (dom/tr
      #js {:onClick #(om/transact! this `[(app/select-competition {:name ~name})])}
      (dom/td nil name)
      (dom/td nil (:competition/location competition))))))

(def competition (om/factory Competition))

(defui CompetitionsView
  Object
  (render
   [this]
   (let [competitions (om/props this)]
     (log "Render CompetitionView")
     (log (om/props this))
     (dom/div
      nil
      (dom/h2
       nil
       "Mina tävlingar")
      (dom/table
       nil
       (dom/thead
        nil
        (dom/tr
         nil
         (dom/th nil "Namn")
         (dom/th nil "Plats")))
       (apply dom/tbody nil (map competition competitions)))))))

;;;;;;;;;;;;;;;;;;;;
;; Classes

(defui ClassRow
  static om/IQuery
  (query [this]
         [:class/position :class/name :class/remaining :class/starting
          {:class/rounds [:round/status :round/type]}
          {:class/adjudicator-panel
           [:adjudicator-panel/name]}
          {:class/dances
           [:dance/name]}])
  Object
  (render
   [this]
   (let [{:keys [position name panel type starting status]} (presentation/make-class-presenter (om/props this))]
     ;(log "ClassRowRender")
     (dom/tr
      nil
      (dom/td nil position)
      (dom/td nil name)
      (dom/td nil panel)
      (dom/td nil type)
      (dom/td nil starting)
      (dom/td nil status)))))

(defui ClassesView
  static om/IQuery
  (query [this]
         [{:competition/classes (om/get-query ClassRow)}])
  Object
  (render
   [this]
   (let [classes (:competition/classes (om/props this))]
     (dom/div
      nil
      (dom/h3 nil "Klasser")
      (dom/table
       #js {:className "table"}
       (dom/thead
        nil
        (dom/tr
         nil
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
  (query [this]
         [:activity/comment :activity/number :activity/time :activity/name
          {:activity/source
           [:round/class-id :round/type :round/index :round/status
            :round/starting :round/heats :round/recall
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
     (log "ScheduleRowRender")
     ;(log (first (:class/_rounds (:activity/source (om/props this)))))
     ;(log (om/props this))
     (dom/tr
      nil
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
  (query [this]
         [{:competition/activities (om/get-query ScheduleRow)}])
  Object
  (render
   [this]
   (let [activites (:competition/activities (om/props this))]
     ;(log "ScheduleView Render")
     ;(log activites)
     (dom/div
      nil
      (dom/h3 nil "Time Schedule")
      (dom/table
       #js {:className "table"}
       (dom/thead
        nil
        (dom/tr
         nil
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
;; Menu

(defn make-menu-button
  [component button-name page-key]
  (dom/button
   #js {:className "btn btn-default"
        :onClick #(om/transact! component `[(app/select-page {:page ~page-key})])}
   button-name))

(defui MenuComponent
  static om/IQuery
  (query [this]
         [:app/selected-page
          {:app/competitions (om/get-query Competition)}
          {:app/selected-competition
           (concat (om/get-query ClassesView)
                   (om/get-query ScheduleView)
                   (om/get-query AdjudicatorPanelsView)
                   (om/get-query AdjudicatorsView)
                   (om/get-query PropertiesView))}])
  Object
  (render
   [this]
   (let [competitions (:app/competitions (om/props this))
         spage (:app/selected-page (om/props this))
         selected-competition (:app/selected-competition (om/props this))
         make-button (partial make-menu-button this)]
     (log "Render MenuComponent")
;     (dom/div #js {:className "container"})
     (dom/div
      nil
      (dom/nav #js {:className "navbar navbar-inverse navbar-fixed-top"}
               (dom/div #js {:className "container-fluid"}
                        (dom/div #js {:className "navbar-header"}
                                 (dom/a #js {:className "navbar-brand" :href  "#"} "Tango!"))
                        (dom/div #js {:id "navbar" :className "navbar-collapse collapse"}
                                 (dom/ul #js {:className "nav navbar-nav navbar-right"}
                                         (dom/li nil (dom/a #js {:href "#"} "Tävlingar")))
                                 (dom/form #js {:className "navbar-form navbar-right"}
                                           (dom/input #js {:type "text"
                                                           :className "form-control"
                                                           :placeholder "Search..."}))
                                 ;; (dom/div nil (make-button "Tävlingar" :competitions)   
                                 ;;          (dom/button
                                 ;;           #js {:onClick
                                 ;;                (fn [e]
                                 ;;                  (test-query-click this))}
                                 ;;           "Query")
                                 ;;          (when selected-competition
                                 ;;            (apply dom/div nil
                                 ;;                   (map (fn [[name key]] (make-button name key))
                                 ;;                        [["Properties" :properties]
                                 ;;                         ["Classes" :classes]
                                 ;;                         ["Time Schedule" :schedule]
                                 ;;                         ["Adjudicators" :adjudicators]
                                 ;;                         ["Adjudicator Panels" :adjudicator-panels]]))))
                                 )))

      (dom/div #js {:className "container-fluid"}
               (dom/div #js {:className "row"}
                        (dom/div #js {:className "col-sm-3 col-md-2 sidebar"}
                                 (dom/ul #js {:className "nav nav-sidebar"}
                                         (dom/li #js {:className "active"
                                                      :onClick  #(log "click")}
                                                 (dom/a {:href "#"} "Classer"))
                                         (dom/li nil (dom/a {:href "#"} "Time Schedule"))
                                         )

                                 (apply dom/ul #js {:className "nav nav-sidebar"}
                                        (map (fn [[name key]] (make-button name key))
                                             [["Properties" :properties]
                                              ["Classes" :classes]
                                              ["Time Schedule" :schedule]
                                              ["Adjudicators" :adjudicators]
                                              ["Adjudicator Panels" :adjudicator-panels]]))
                                         ;; (dom/li #js {:className "active"
                                         ;;              :onClick  #(log "click")}
                                         ;;         (dom/a {:href "#"} "Classer"))
                                         ;; (dom/li nil (dom/a {:href "#"} "Time Schedule"))
                                         )
                        
                        (dom/div #js {:className "col-sm-9 col-sm-offset-3 col-md-10 col-md-offset-2 main"}
                                 (dom/h1 #js {:className "page-header"} "Rikstävling yada yada")
                                 (dom/h2 #js {:className "sub-header"} "Klasser")
                                 (condp = spage
                                   :properties ((om/factory PropertiesView) selected-competition)
                                   :classes ((om/factory ClassesView) selected-competition)
                                   :competitions ((om/factory CompetitionsView) competitions)
                                   :schedule ((om/factory ScheduleView) selected-competition)
                                   :adjudicators ((om/factory AdjudicatorsView) selected-competition)
                                   :adjudicator-panels ((om/factory AdjudicatorPanelsView) selected-competition)))))))))

;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;
;; Remote Posts

(defn sente-post []
  (fn [{:keys [remote] :as env} cb]
    (do
      (log "Env > ")
      (log env)
      (log (str "Sent to Tango Backend => " remote))
      (chsk-send! [:event-manager/query [[:competition/name :competition/location]]]))))

;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;
;; Application

;; Init db etc if it has not been done
(when (not (app-started? conn))
  (init-app))

(def reconciler
  (om/reconciler
    {:state conn
     :remotes [:remote]
     :parser (om/parser {:read read :mutate mutate})
     :send sente-post}))

(om/add-root! reconciler
  MenuComponent (gdom/getElement "app"))


