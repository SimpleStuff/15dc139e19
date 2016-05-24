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
          (om/transact! reconciler `[(app/set-status {:status :requested}) :app/selected-competition])))
    (when (= topic :tx/accepted)
      (log (str "Socket Event from server: " topic))
      ;(log (str "Socket Payload: " payload))
      (cond
        (= payload 'app/set-speaker-activity)
        (do
          ;(log "select")
          (om/transact! reconciler `[:app/speaker-activites]))))))

(defmethod event-msg-handler :chsk/handshake
  [{:as ev-msg :keys [?data]}]
  (let [[?uid ?csrf-token ?handshake-data] ?data]
    (log (str "Socket Handshake: " ?data))))

(defonce chsk-router
         (sente/start-chsk-router-loop! event-msg-handler* ch-chsk))

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
;; TimeScheduleComponent
(defui TimeScheduleComponent
  static om/IQuery
  (query [_]
    [])
  Object
  (render
    [this]
    (let [p (om/props this)]
      (dom/div nil "Time Schedule"))))

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
                                                        :app/selected-page])} "Time Schedule")))))

;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;
;; MainComponent

(defui MainComponent
  static om/IQuery
  (query [_]
    [{:app/selected-competition [:competition/name :competition/location]}
     :app/status
     :app/selected-page])
  Object
  (render
    [this]
    (let [p (om/props this)
          selected-competition (:app/selected-competition p)
          status (:app/status p)
          selected-page (:app/selected-page p)]
      (log (str selected-competition))
      (dom/div nil
        ((om/factory MenuComponent))
        (condp = selected-page
          :home (dom/div nil
                  (dom/h1 nil (str "Runtime of " (:competition/name selected-competition)))
                  ((om/factory AdminViewComponent) {:status status}))

          :time-schedule ((om/factory TimeScheduleComponent)))
        ))))

;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;
;; Remote com
(defn remote-send []
  (fn [edn cb]
    (cond
      (:query edn)
      (go
        (let [response (async/<! (http/get "/query" {:query-params
                                                     {:query (pr-str (if (map? (first (:query edn)))
                                                                       (:query edn)
                                                                       (om/get-query MainComponent)))}}))
              edn-response (second (cljs.reader/read-string (:body response)))]

          ;; TODO - why is the response a vec?
          ;(cb {:app/selected-competition (first (:app/selected-competition edn-response))})
          (cb edn-response)
          )))))

;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;
;; Application
(def update-ch (chan))

(defonce app-state (atom {:app/selected-competition nil
                          :app/status :loaded
                          :app/selected-page :home}))

(def reconciler
  (om/reconciler
    {:state   app-state
     :remotes [:command :query]
     :parser  (om/parser {:read r/read :mutate m/mutate})
     :send    (remote-send)}))

(om/add-root! reconciler
              MainComponent (gdom/getElement "app"))
