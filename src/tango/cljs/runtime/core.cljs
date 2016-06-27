(ns tango.cljs.runtime.core
  (:require-macros
    [cljs.core.async.macros :as asyncm :refer (go go-loop)])

  (:require [goog.dom :as gdom]
            [om.next :as om :refer-macros [defui]]
            [om.dom :as dom]

            [cognitect.transit :as t]

            [cljs.core.async :as async :refer (<! >! put! chan timeout)]
            [cljs-http.client :as http]

            [taoensso.sente :as sente :refer (cb-success?)]
            [datascript.core :as d]
            [tango.ui-db :as uidb]
            [tango.domain :as domain]
            [tango.presentation :as presentation]
            [tango.cljs.runtime.mutation :as m]
            [tango.cljs.runtime.read :as r]
            [alandipert.storage-atom :as ls])

  (:import [goog.net XhrIo]))

(defn log [m]
  (.log js/console m))

(declare reconciler)
(declare app-state)
(declare update-ch)
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
;; Import

(defn on-file-read [e file-reader]
  (let [result (.-result file-reader)]
    (log "On file read : send :file/import")
    (chsk-send! [:file/import {:content result}])))

(defn on-click-import-file [e read-cb]
  (log "Import clicked")
  (let [file (.item (.. e -target -files) 0)
        r (js/FileReader.)]
    (set! (.-onload r) #(read-cb % r))
    (.readAsText r file)))

;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;
;; ScheduleRow

(defui ScheduleRow
  static om/IQuery
  (query [_]
    [:activity/comment :activity/number :activity/time :activity/name :activity/id :activity/position
     {:activity/source
      [:round/class-id {:round/type ['*]} :round/index {:round/status ['*]}
       {:round/starting [:participant/number :participant/id]}
       :round/number-of-heats :round/number-to-recall
       {:round/dances [:dance/name]}
       {:round/panel [:adjudicator-panel/name
                      :adjudicator-panel/id
                      {:adjudicator-panel/adjudicators
                       [:adjudicator/name
                        :adjudicator/id
                        :adjudicator/number]}]}
       {:class/_rounds
        [{:class/rounds
          [{:round/type ['*]} :round/index {:round/status ['*]}]}]}]}])
  Object
  (render
    [this]
    (let [p (om/props this)
          {:keys [time number name starting round heats recall panel type]}
          (presentation/make-time-schedule-activity-presenter
            p
            (first (:class/_rounds (:activity/source (om/props this)))))
          selected? (:is-selected p)
          completed? (= (:round/status (:activity/source (om/props this))) :status/completed)
          ;speaker-activies #{}                                 ;(:speaker-activites (om/props this))
          ;speaker? (seq (filter #(= (:activity/id (om/props this)) (:activity/id %))
          ;                      speaker-activies))
          speaker? (:is-speaker-selected p)
          ]
      (dom/tr #js {:className (if selected? "success" (if completed? "info" ""))}
        (dom/td nil time)
        (dom/td nil number)
        (dom/td nil name)
        (dom/td nil starting)
        (dom/td nil round)
        (dom/td nil heats)
        (dom/td nil recall)
        (dom/td nil panel)
        (dom/td nil type)
        (when-not (= name "")
          (dom/td nil
                  (dom/div #js {:className "control-group"}
                    (dom/button #js {:className (str "btn" (if selected? " btn-success" " btn-default"))
                                     :onClick   #(when-not (or completed? selected?)
                                                  (do
                                                    (log "Selected")
                                                    (om/transact!
                                                      this
                                                      `[(app/select-activity {:activity/id ~(:activity/id p)})
                                                        :app/selected-activities])))}
                                (dom/span #js {:className "glyphicon glyphicon-play"}))

                    (dom/button #js {:className "btn btn-default"
                                     :onClick #(log "Deselect")}
                                (dom/span #js {:className "glyphicon glyphicon-stop"}))

                    (dom/button #js {:className (str "btn" (if speaker? " btn-success" " btn-default"))
                                     :onClick #(when-not (or completed? speaker?)
                                                (do
                                                  (log "Speaker Select")
                                                  (om/transact!
                                                    this
                                                    `[(app/select-speaker-activity {:activity/id ~(:activity/id p)})
                                                      :app/speaker-activities])))}
                                (dom/span #js {:className "glyphicon glyphicon-volume-up"})))))))))

;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;
;; ScheduleView

(defui ScheduleView
  static om/IQuery
  (query [_]
    [{:competition/activities (om/get-query ScheduleRow)}])
  Object
  (render
    [this]
    (let [p (om/props this)
          activites (sort-by :activity/position (:competition/activities p))]
      (log "Schedule")
      ;(log "Selected")
      ;(log (:selected-activities p))
      (log "Speaker")
      (log (:speaker-activities p))
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
              (dom/th #js {:width "20"} "Type")
              (dom/th #js {:width "400"} "")))
          (apply dom/tbody nil (map #((om/factory ScheduleRow)
                                      (merge % {:is-selected
                                                (seq (filter (fn [act]
                                                               (= (:activity/id act) (:activity/id %)))
                                                             (:selected-activities p)))
                                                :is-speaker-selected
                                                (seq (filter (fn [act]
                                                               (= (:activity/id act) (:activity/id %)))
                                                             (:speaker-activities p)))
                                                })) activites))
          ;(apply dom/tbody nil (map #((om/factory ScheduleRow)
          ;                            (merge % {:selected-activity (:selected-activity (om/props this))
          ;                                      :speaker-activites (:speaker-activites (om/props this))}))
          ;                          activites))
          )))))

;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;
;; Import/Export Component
(defui ImportExportComponent
  static om/IQuery
  (query [_]
    [])
  Object
  (render
    [this]
    (let [import-status (:import-status (om/props this))
          read-cb on-file-read]
      (dom/div nil
        (dom/span #js {:className (str "btn btn-default btn-file"
                                       (when (= import-status :importing) " disabled"))}
          "Importera.."
          (dom/input #js {:type     "file"
                          :onChange #(do
                                      (om/transact! this `[(app/set-status {:status :importing})])
                                      (on-click-import-file % read-cb))}))

        (dom/button #js {:className "btn btn-default"
                         :onClick   #(om/transact!
                                      this
                                      `[(app/set-status {:status :requested})
                                        :app/selected-competition])}
                    "Exportera")))))

;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;
;; Admin view
(defui AdminViewComponent
  static om/IQuery
  (query [_]
    [])
  Object
  (render
    [this]
    (let [p (om/props this)
          status (:status p)]
      (dom/div nil
        (dom/h1 nil "Admin")
        ((om/factory ImportExportComponent) {:import-status status})))))

;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;
;; MenuComponent
(defui MenuComponent
  static om/IQuery
  (query [_]
    [])
  Object
  (render
    [this]
    (let [p (om/props this)]
      (dom/div nil
        (dom/button #js {:onClick #(om/transact! this `[(app/select-page {:selected-page :home})
                                                        :app/selected-page])} "Home")
        (dom/button #js {:onClick #(om/transact! this `[(app/select-page {:selected-page :time-schedule})
                                                        :app/selected-page])} "Time Schedule")
        (dom/button #js {:onClick #(om/transact! this `[(app/select-page {:selected-page :clients})
                                                        :app/selected-page])} "Clients")))))

;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;
;; Client Row
(defui ClientRow
  static om/IQuery
  (query [_]
    [:client/id :client/name {:client/user [:adjudicator/id :adjudicator/name]}])
  Object
  (render
    [this]
    (let [client (:client (om/props this))
          panels (sort-by :adjudicator-panel/name (:adjudicator-panels (om/props this)))
          client-id (:client/id client)
          client-name (:client/name client)
          adjudicator-id (:adjudicator/id (:client/user client))
          adjudicator-name (:adjudicator/name (:client/user client))]
      (dom/tr nil
        (dom/td nil (str client-id))
        (dom/td nil client-name)
        (dom/td nil
          (dom/div #js {:className "dropdown"}
            (dom/button #js {:className   "btn btn-default dropdown-toggle"
                             :data-toggle "dropdown"} ""
                        (dom/span #js {:className "selection"}
                          (if adjudicator-name adjudicator-name "Select"))
                        (dom/span #js {:className "caret"}))
            ;(dom/ul #js {:className "dropdown-menu"
            ;             :role "menu"}
            ;  (dom/li #js {:className "dropdown-header"} (str "Panel - "
            ;                                                  (:adjudicator-panel/name (first panels))))
            ;  (dom/li #js {:role "presentation"}
            ;    (dom/a #js {:role "menuitem"} "AA"))
            ;  (dom/li #js {:role "presentation"}
            ;    (dom/a #js {:role "menuitem"} "BB")))

            (apply dom/ul #js {:className "dropdown-menu" :role "menu"}
                   (map (fn [panel]
                          [(dom/li #js {:className "dropdown-header"}
                             (str "Panel - " (:adjudicator-panel/name panel)))
                           (map (fn [adjudicator]
                                  (dom/li #js {:role "presentation"
                                               :onClick     ;#(log (str "Change ajd to " (:adjudicator/name adjudicator)))
                                                     #(om/transact! reconciler `[(app/set-client-info
                                                                             {:client/id   ~client-id
                                                                              :client/name ~client-name
                                                                              :client/user {:adjudicator/id   ~(:adjudicator/id adjudicator)
                                                                                            :adjudicator/name ~(:adjudicator/name adjudicator)}})
                                                                           :app/clients])
                                               }
                                    (dom/a #js {:role "menuitem"} (:adjudicator/name adjudicator))))
                                (:adjudicator-panel/adjudicators panel))])
                        panels))
            ))
        ))))

;<div class="dropdown">
;<button class="btn btn-default dropdown-toggle" type="button" id="menu1" data-toggle="dropdown">Tutorials
;<span class="caret"></span></button>
;<ul class="dropdown-menu" role="menu" aria-labelledby="menu1">
;<li role="presentation"><a role="menuitem" tabindex="-1" href="#">HTML</a></li>
;<li role="presentation"><a role="menuitem" tabindex="-1" href="#">CSS</a></li>
;<li role="presentation"><a role="menuitem" tabindex="-1" href="#">JavaScript</a></li>
;<li role="presentation" class="divider"></li>
;<li role="presentation"><a role="menuitem" tabindex="-1" href="#">About Us</a></li>
;</ul>
;</div>

;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;
;; Clients View
(defui ClientsView
  static om/IQuery
  (query [_]
    (into [] (om/get-query ClientRow)))
  Object
  (render
    [this]
    (let [clients (sort-by (juxt :client/name :client/id) (:clients (om/props this)))
          panels (:adjudicator-panels (om/props this))]
      (log "clients")
      (log clients)
      (dom/div nil
        (dom/h2 nil "Clients")
        (dom/table
          #js {:className "table table-hover table-condensed"}
          (dom/thead nil
            (dom/tr nil
              (dom/th #js {:width "50"} "Id")
              (dom/th #js {:width "50"} "Name")
              (dom/th #js {:width "50"} "Assigned to Adjudicator")))
          (apply dom/tbody nil (map #((om/factory ClientRow {:key-fn :client/id}) {:client       %
                                                              :adjudicator-panels panels}) clients)))))))

;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;
;; MainComponent

(defui MainComponent
  static om/IQuery
  (query [_]
    [{:app/selected-competition (into [:competition/name :competition/location
                                       {:competition/panels [:adjudicator-panel/name
                                                             {:adjudicator-panel/adjudicators
                                                              [:adjudicator/id
                                                               :adjudicator/name]}]}]
                                      (concat (om/get-query ScheduleView)))}
     :app/status
     :app/selected-page
     {:app/selected-activities [:activity/id] }
     {:app/speaker-activities [:activity/id]}
     {:app/clients (om/get-query ClientsView)}
     ])
  Object
  (render
    [this]
    (let [p (om/props this)
          selected-competition (:app/selected-competition p)
          status (:app/status p)
          selected-page (:app/selected-page p)]
      (log "Main Clients :")
      (log (:app/client p))
      (dom/div nil
        ((om/factory MenuComponent))
        (condp = selected-page
          :home (dom/div nil
                  (dom/h1 nil (str "Runtime of " (:competition/name selected-competition)))
                  ((om/factory AdminViewComponent) {:status status}))

          :time-schedule ((om/factory ScheduleView) {:competition/activities
                                                     (:competition/activities selected-competition)
                                                     :selected-activities
                                                     (:app/selected-activities p)
                                                     :speaker-activities
                                                     (:app/speaker-activities p)})
          :clients ((om/factory ClientsView) {:clients      (:app/clients p)
                                              :adjudicator-panels (:competition/panels selected-competition)}))
        ))))

;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;
;; Remote com
(defn transit-post [url edn cb]
  (log edn)
  (.send XhrIo url
         #()                                                ;log
         ;(this-as this
         ;  (log (t/read (t/reader :json)
         ;               (.getResponseText this)))
         ;  (cb (t/read (t/reader :json) (.getResponseText this))))

         "POST" (t/write (t/writer :json) edn)
         #js {"Content-Type" "application/transit+json"}))

(defn remote-send []
  (fn [edn cb]
    (cond
      (:command edn)
      ;(log "a")
      (transit-post "/commands" edn cb)
      (:query edn)
      (go
        ;(log "Query")
        ;(log edn)
        (let [remote-query (:query edn)
              ;remote-query (if (map? (first (:query edn)))
              ;               (:query edn)
              ;               ;; TODO - why do we not get a good query
              ;               (conj [] (first (om/get-query MainComponent))))
              response (async/<! (http/get "/query" {:query-params
                                                     {:query (pr-str remote-query)}}))
              edn-response (second (cljs.reader/read-string (:body response)))]

          ;(log remote-query)
          ;(log edn-response)
          ;; TODO - why is the response a vec?
          ;(cb (cljs.reader/read-string (:body response)))
          (cb edn-response)
          )))))

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
  (log (str "Unhandled socket event: " ev-msg)))

(defmethod event-msg-handler :chsk/state
  [{:as ev-msg :keys [?data]}]
  (do
    (log (str "Channel socket state change: " ?data))
    (when (:first-open? ?data)
      (log "Channel socket successfully established!"))))

;; TODO - Cleaning when respons type can be separated
(defmethod event-msg-handler :chsk/recv
  [{:as ev-msg :keys [?data]}]
  (let [[topic payload] ?data]
    (log (str "Socket Event from server: " topic))
    (when (= topic :event-manager/transaction-result)
      (do (log "Time to re-query")
          (om/transact! reconciler
                        `[(app/set-status {:status :requested})
                          ;; TODO - consolidate with main query
                          {:app/selected-competition
                           ~(into [:competition/name :competition/location
                                  {:competition/panels [:adjudicator-panel/name
                                                        {:adjudicator-panel/adjudicators
                                                         [:adjudicator/id
                                                          :adjudicator/name]}]}]
                                 (concat (om/get-query ScheduleView)))}])))
    (when (= topic :tx/accepted)
      (log (str "Socket Event from server: " topic))
      ;(log (str "Socket Payload: " payload))
      (cond
        (= payload 'app/set-speaker-activity)
        (do
          ;(log "select")
          (om/transact! reconciler `[:app/speaker-activites]))
        (= payload 'app/set-client-info)
        (do
          (log (str "Client info changed " payload))
          (om/transact! reconciler `[{:app/clients ~(om/get-query ClientsView)}]))))))

(defmethod event-msg-handler :chsk/handshake
  [{:as ev-msg :keys [?data]}]
  (let [[?uid ?csrf-token ?handshake-data] ?data]
    (log (str "Socket Handshake: " ?data))))

(defonce chsk-router
         (sente/start-chsk-router-loop! event-msg-handler* ch-chsk))

;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;
;; Application
(defonce app-state (atom {:app/selected-competition nil
                          :app/status :loaded
                          :app/selected-page :home
                          :app/selected-activities #{}
                          :app/speaker-activities #{}}))

(def reconciler
  (om/reconciler
    {:state   app-state
     :remotes [:command :query]
     :parser  (om/parser {:read r/read :mutate m/mutate})
     :send    (remote-send)}))

(om/add-root! reconciler
              MainComponent (gdom/getElement "app"))
