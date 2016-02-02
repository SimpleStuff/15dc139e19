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
;; Init DB
(defonce conn (d/create-conn uidb/schema))

(defn init-app []
  (d/transact! conn [{:db/id -1 :app/id 1}
                     {:db/id -1 :selected-page :competitions}
                     {:db/id -1 :app/import-status :none}
                     {:db/id -1 :app/selected-competition {}}
                     ]))

(defn app-started? [conn]
  (not
   (empty? (d/q '[:find ?e
                  :where
                  [?e :app/id 1]] (d/db conn)))))

;(log conn)

;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;
;; Server message handling

(declare reconciler)
(defn handle-query-result [d]
  (do
    (log "Handle Q R")
    ;; VERY TEMPORARY (KILL ME IF I DO NOT FIX IT)
    ;; Need to make difference between query for all comps. vs query for details for a comp.
    
    (if (vector? d)
      (let [clean-data  {:competitions d}      ;(uidb/sanitize (first d))
            ]
        (log "Compsss")
        (om/transact! reconciler `[(app/add-competition ~clean-data) :app/competitions])
        )
      (let [clean-data (uidb/sanitize d)]
        (log "Crazzzy")
        (om/transact! reconciler `[(app/add-competition ~clean-data) :app/competitions])))
    (om/transact! reconciler `[(app/set-import-status {:status :none})]))
  )

;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;
;; Sente message handling

(defmulti event-msg-handler :id) ; Dispatch on event-id
;; Wrap for logging, catching, etc.:
(defn event-msg-handler* [{:keys [id ?data event] :as ev-msg}]
  (do
    (log "Enter event-msg-handler*")
    (event-msg-handler {:id (first ev-msg)
                        :?data (second ev-msg)})
    (log "Exit event-msg-handler*")))

(defmethod event-msg-handler :default ; Fallback
  [{:keys [event] :as ev-msg}]
  (log (str "Unhandled event: " ev-msg)))

(defmethod event-msg-handler :chsk/state
  [{:as ev-msg :keys [?data]}]
  (if (:first-open? ?data)
    (do
      (log "Channel socket successfully established!")
      (log "Fetch initilize data from Tango server")
      (chsk-send! [:event-manager/query [[:competition/name :competition/location]]]))
    (log (str "Channel socket state change: " ?data))))

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
    (log "Exit event-msg-handler")))

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
    (.readAsText r file)
    ;(swap! app-state merge {:import-status :import-started})
    ))

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

(defmethod read :app/import-status
  [{:keys [state query]} key params]
  {:value (do
            (log "Read Import status")
            (let [q (d/q '[:find ?status .
                           :where [[:app/id 1] :app/import-status ?status]]
                         (d/db state))]
              (log q)
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
                 (if params
                   (if (:competitions params)
                     (do
                       (log "Compsss")
                       (d/transact! state (:competitions params)))
                     (d/transact! state [params])))))}))

(defmethod mutate 'app/select-page
  [{:keys [state]} key {:keys [page] :as params}]
  {:value {:keys [:app/selected-page]}
   :action (fn []
             (do (log (str "Select Page "))
                 ;(log page)
                 (d/transact! state [{:app/id 1 :selected-page page}])
                 ;(log state)
                 ))})

(defmethod mutate 'app/set-import-status
  [{:keys [state]} key {:keys [status] :as params}]
  {:value {:keys [:app/import-status]}
   :action (fn []
             (d/transact! state [{:app/id 1 :app/import-status status}]))})

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
      (dom/h2 {:className "sub-header"} "Domare")
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
      (dom/h2 {:className "sub-header"} "Domarpaneler")
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
      ;; TODO - this should be handle be some remote mechanism
      ;; TODO - set app status to remote query then reset at query done
      #js {:onClick #(do
                       (chsk-send! [:event-manager/query ['[*] [:competition/name name]]])
                       (om/transact! this `[(app/select-competition {:name ~name})])
                       ;(om/transact! this `[(app/select-competition {:name {}})])
                       )}
      (dom/td nil name)
      (dom/td nil (:competition/location competition))))))

(def competition (om/factory Competition))

(defui CompetitionsView
  Object
  (render
   [this]
   (let [competitions (:competitions (om/props this))
         status (:status (om/props this))]
     (log "Render CompetitionView")
     (log (om/props this))
     (log "Import Status")
      (log status)
     (dom/div
      nil
      (dom/h2
       nil
       "Mina tävlingar")
      (dom/span nil "Välj en tävling att arbete med.")
      (dom/table
       #js {:className "table table-hover"}
       (dom/thead
        nil
        (dom/tr
         nil
         (dom/th nil "Namn")
         (dom/th nil "Plats")))
       (apply dom/tbody nil (map competition competitions)))
      
      (dom/div
       nil
       (dom/span #js {:className (str "btn btn-default btn-file"
                                      (when (= status :importing) " disabled"))} "Importera.."
                 (dom/input #js {:type "file"
                                 :onChange #(do
                                              (om/transact! reconciler `[(app/set-import-status
                                                                          {:status :importing})])
                                              (on-click-import-file %))}))
       (condp = status
         :none (dom/h3 nil "")
         :importing (dom/h3 nil "Importerar, vänligen vänta..")))))))

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
      (dom/h2 {:className "sub-header"} "Klasser")
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
      (dom/h2 nil "Time Schedule")
      (dom/table
       #js {:className "table table-hover table-condensed"}
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

;; (dom/li #js {:className "active"
;;              :onClick  #(log "click")}
;;         (dom/a {:href "#"} "Classer"))
;; (dom/li nil (dom/a {:href "#"} "Time Schedule"))

(defn make-menu-button
  [component active-page-key button-name page-key ]
  (dom/li
   #js {:className (if (= active-page-key page-key) "active" "")
        :onClick #(om/transact! component `[(app/select-page {:page ~page-key})])}
   (dom/a nil button-name)))

;; (defn make-menu-button
;;   [component button-name page-key]
;;   (dom/button
;;    #js {:className "btn btn-default"
;;         :onClick #(om/transact! component `[(app/select-page {:page ~page-key})])}
;;    button-name))

(defui MenuComponent
  static om/IQuery
  (query [this]
         [:app/selected-page
          :app/import-status
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
   (log "Renderz")
   (let [competitions (:app/competitions (om/props this))
         spage (:app/selected-page (om/props this))
         selected-competition (:app/selected-competition (om/props this))
         make-button (partial make-menu-button this spage)]
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
                                         (dom/li
                                          #js {:onClick #(om/transact!
                                                          this
                                                          `[(app/select-page {:page :competitions})])}
                                          (dom/a #js {:href "#"} "Tävlingar"))
                                         (dom/li
                                          #js {:onClick (fn [e] (test-query-click this))}
                                          (dom/a #js {:href "#"} "Query")))
                                 (dom/form #js {:className "navbar-form navbar-right"}
                                           (dom/input #js {:type "text"
                                                           :className "form-control"
                                                           :placeholder "Search..."})))))

      (dom/div #js {:className "container-fluid"}
               (dom/div #js {:className "row"}
                        (when (not (empty? selected-competition))
                          (dom/div #js {:className "col-sm-2 col-md-2 sidebar"}
                                   (dom/div nil (dom/u nil (:competition/name selected-competition)) )
                                   (apply dom/ul #js {:className "nav nav-sidebar"}
                                          (map (fn [[name key]] (make-button name key))
                                               [["Properties" :properties]
                                                ["Classes" :classes]
                                                ["Time Schedule" :schedule]
                                                ["Adjudicators" :adjudicators]
                                                ["Adjudicator Panels" :adjudicator-panels]]))))
                        
                        (dom/div #js {:className "col-sm-10 col-sm-offset-2 col-md-10 col-md-offset-2 main"}
                                 ;(dom/h1 #js {:className "page-header"} "Rikstävling yada yada")
                                 (condp = spage
                                   :properties ((om/factory PropertiesView) selected-competition)
                                   :classes ((om/factory ClassesView) selected-competition)
                                   :competitions ((om/factory CompetitionsView)
                                                  {:competitions competitions
                                                   :status (:app/import-status (om/props this))
                                                   })
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


